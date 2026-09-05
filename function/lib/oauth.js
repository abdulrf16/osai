'use strict';

/**
 * Stateless OAuth 2.1 helpers for MCP servers, following the MCP
 * authorization spec: protected-resource metadata (RFC 9728) locates the
 * authorization server, authorization-server metadata (RFC 8414) gives its
 * endpoints, and dynamic client registration (RFC 7591) gets a client id
 * without any per-provider setup. This is what lets a user paste in *any*
 * MCP server URL - Zoho Mail's, an Airtable inventory server's, or anything
 * else - and connect it without us having pre-registered an OAuth app for it.
 *
 * Nothing here is held in memory between calls (this runs inside a
 * stateless Catalyst function). The PKCE code_verifier in particular never
 * passes through this module more than once, and never round-trips through
 * the authorization redirect - it is generated in the browser, kept there,
 * and sent directly to `exchangeToken` alongside the code once the user has
 * authorized. Putting it in the OAuth `state` param instead (state gets
 * echoed back through the browser's address bar) would defeat the entire
 * point of PKCE, so callers must not do that.
 */

async function fetchJson(url) {
	try {
		const res = await fetch(url, { headers: { Accept: 'application/json' } });
		if (!res.ok) return null;
		return await res.json();
	} catch {
		return null;
	}
}

/**
 * @param {string} mcpUrl
 * @returns {Promise<{authorizationEndpoint: string, tokenEndpoint: string, registrationEndpoint: string|null}>}
 */
async function discover(mcpUrl) {
	const origin = new URL(mcpUrl).origin;

	let asIssuer = origin;
	const prm = await fetchJson(`${origin}/.well-known/oauth-protected-resource`);
	if (prm && Array.isArray(prm.authorization_servers) && prm.authorization_servers[0]) {
		asIssuer = prm.authorization_servers[0];
	}

	const base = asIssuer.replace(/\/$/, '');
	const meta =
		(await fetchJson(`${base}/.well-known/oauth-authorization-server`)) ||
		(await fetchJson(`${base}/.well-known/openid-configuration`));

	if (!meta || !meta.authorization_endpoint || !meta.token_endpoint) {
		throw new Error(
			'Could not discover an OAuth authorization server for this MCP server. ' +
				'It may not support OAuth - try pasting an access token instead.'
		);
	}

	return {
		authorizationEndpoint: meta.authorization_endpoint,
		tokenEndpoint: meta.token_endpoint,
		registrationEndpoint: meta.registration_endpoint || null
	};
}

/**
 * Dynamically registers a public (PKCE, no secret) OAuth client with the
 * authorization server. Falls back to a caller-supplied client id when the
 * server has no registration endpoint.
 */
async function register(registrationEndpoint, redirectUri) {
	const res = await fetch(registrationEndpoint, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({
			client_name: 'Inventory Bot MCP Control Center',
			redirect_uris: [redirectUri],
			grant_types: ['authorization_code', 'refresh_token'],
			response_types: ['code'],
			token_endpoint_auth_method: 'none'
		})
	});
	const data = await res.json().catch(() => ({}));
	if (!res.ok || !data.client_id) {
		throw new Error(`Dynamic client registration failed: ${data.error_description || data.error || res.status}`);
	}
	return { clientId: data.client_id, clientSecret: data.client_secret || null };
}

/**
 * Combines discovery + (dynamic or manual) client registration into the
 * endpoint/client info a browser needs to build its own authorization URL
 * and run the PKCE challenge locally.
 */
async function prepareAuth(mcpUrl, redirectUri, manualClient) {
	const { authorizationEndpoint, tokenEndpoint, registrationEndpoint } = await discover(mcpUrl);

	let clientId, clientSecret;
	if (manualClient && manualClient.clientId) {
		clientId = manualClient.clientId;
		clientSecret = manualClient.clientSecret || null;
	} else if (registrationEndpoint) {
		const reg = await register(registrationEndpoint, redirectUri);
		clientId = reg.clientId;
		clientSecret = reg.clientSecret;
	} else {
		throw new Error(
			'This MCP server has no dynamic client registration endpoint. Provide a client ID manually, ' +
				'or paste an access token instead.'
		);
	}

	return { authorizationEndpoint, tokenEndpoint, clientId, clientSecret };
}

/**
 * Exchanges an authorization code (plus the PKCE verifier the browser has
 * been holding since it started the flow) for tokens.
 */
async function exchangeToken({ tokenEndpoint, clientId, clientSecret, code, codeVerifier, redirectUri }) {
	const body = new URLSearchParams({
		grant_type: 'authorization_code',
		code,
		redirect_uri: redirectUri,
		client_id: clientId,
		code_verifier: codeVerifier
	});
	if (clientSecret) body.set('client_secret', clientSecret);

	const res = await fetch(tokenEndpoint, {
		method: 'POST',
		headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
		body
	});
	const data = await res.json().catch(() => ({}));
	if (!res.ok || data.error) {
		throw new Error(`Token exchange failed: ${data.error_description || data.error || res.status}`);
	}

	return {
		accessToken: data.access_token,
		refreshToken: data.refresh_token || null,
		expiresIn: data.expires_in || 3600
	};
}

async function refreshAccessToken({ tokenEndpoint, clientId, clientSecret, refreshToken }) {
	if (!tokenEndpoint || !clientId || !refreshToken) {
		throw new Error('Missing token endpoint, client id, or refresh token for this connection.');
	}
	const body = new URLSearchParams({ grant_type: 'refresh_token', refresh_token: refreshToken, client_id: clientId });
	if (clientSecret) body.set('client_secret', clientSecret);

	const res = await fetch(tokenEndpoint, {
		method: 'POST',
		headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
		body
	});
	const data = await res.json().catch(() => ({}));
	if (!res.ok || data.error) {
		throw new Error(`Token refresh failed: ${data.error_description || data.error || res.status}`);
	}
	return {
		accessToken: data.access_token,
		refreshToken: data.refresh_token || refreshToken,
		expiresIn: data.expires_in || 3600
	};
}

module.exports = { discover, register, prepareAuth, exchangeToken, refreshAccessToken };
