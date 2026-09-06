'use strict';

/**
 * MCP OAuth 2.1 client.
 *
 * Implements the pieces of the MCP authorization spec that real MCP servers
 * (Zoho Mail's included) rely on:
 *
 *   1. RFC 9728  - Protected Resource Metadata discovery (/.well-known/oauth-protected-resource)
 *   2. RFC 8414  - Authorization Server Metadata discovery (/.well-known/oauth-authorization-server)
 *   3. RFC 7591  - Dynamic Client Registration (so the user never copies a client ID)
 *   4. RFC 7636  - Authorization Code + PKCE (S256)
 *   5. RFC 8707  - Resource Indicators (the `resource` parameter binds the token to the MCP server)
 *
 * Every one of these steps - discovery, PKCE generation, and authorize-URL
 * construction - happens server-side, in this module and the routes that
 * call it. That is deliberate: building the authorize URL by hand in the
 * browser (as an earlier version of this app did) drifts from what a given
 * authorization server actually expects (e.g. Zoho requires the `resource`
 * parameter to be present and byte-identical across the authorize and token
 * requests), and one mismatched parameter is enough to fail with an opaque
 * error after the user has already granted consent. Doing it all here, once,
 * keeps every MCP connection - Zoho Mail, Airtable, or anything else - going
 * through the exact same, previously-verified code path.
 *
 * Nothing is persisted server-side. Tokens are handed back to the browser,
 * which stores them locally and replays them on each request. The function
 * stays stateless so it can be deployed as a plain Catalyst Advanced I/O zip
 * with no datastore. The PKCE code_verifier is generated here but only ever
 * leaves this module inside a JSON response body (never a URL), and the
 * browser sends it back verbatim on the token exchange - it never appears in
 * the OAuth `state` param, which would defeat the point of PKCE.
 */

const crypto = require('crypto');

const HTTP_TIMEOUT_MS = 10000;

/* ------------------------------------------------------------------ helpers */

function base64url(buffer) {
	return Buffer.from(buffer)
		.toString('base64')
		.replace(/\+/g, '-')
		.replace(/\//g, '_')
		.replace(/=+$/, '');
}

/** Generate a PKCE verifier/challenge pair (S256, RFC 7636). */
function createPkcePair() {
	const verifier = base64url(crypto.randomBytes(32));
	const challenge = base64url(crypto.createHash('sha256').update(verifier).digest());
	return { verifier, challenge, method: 'S256' };
}

function randomState() {
	return base64url(crypto.randomBytes(16));
}

/**
 * fetch() with a hard timeout. Catalyst Advanced I/O functions are killed at 30s,
 * so every outbound call needs its own ceiling or one slow discovery hop can
 * consume the whole request budget.
 */
async function fetchWithTimeout(url, options = {}, timeoutMs = HTTP_TIMEOUT_MS) {
	const controller = new AbortController();
	const timer = setTimeout(() => controller.abort(), timeoutMs);
	try {
		return await fetch(url, { ...options, signal: controller.signal });
	} catch (err) {
		if (err && err.name === 'AbortError') {
			throw new Error(`Request to ${url} timed out after ${timeoutMs}ms`);
		}
		throw err;
	} finally {
		clearTimeout(timer);
	}
}

async function fetchJson(url, options, timeoutMs) {
	const res = await fetchWithTimeout(url, options, timeoutMs);
	const text = await res.text();
	let body = null;
	if (text) {
		try {
			body = JSON.parse(text);
		} catch (_) {
			body = null;
		}
	}
	return { ok: res.ok, status: res.status, body, text, headers: res.headers };
}

/**
 * The `resource` value for RFC 8707 must be the MCP endpoint without a fragment,
 * and the spec asks for the canonical form (no trailing slash on a bare origin).
 */
function canonicalResource(mcpUrl) {
	const u = new URL(mcpUrl);
	u.hash = '';
	return u.toString();
}

/* --------------------------------------------------------------- discovery */

/**
 * Build the ordered list of protected-resource metadata URLs to try.
 *
 * RFC 9728 inserts the well-known segment *before* the resource path, and the MCP
 * spec also allows the path-less form. MCP URLs commonly carry a path
 * (e.g. /mcp/<id>), so the path-aware variant is tried first.
 */
function protectedResourceMetadataUrls(mcpUrl) {
	const u = new URL(mcpUrl);
	const path = u.pathname.replace(/\/+$/, '');
	const urls = [];
	if (path && path !== '') {
		urls.push(`${u.origin}/.well-known/oauth-protected-resource${path}`);
	}
	urls.push(`${u.origin}/.well-known/oauth-protected-resource`);
	return urls;
}

/**
 * Build the ordered list of authorization-server metadata URLs for an issuer.
 * Tries RFC 8414 (path-aware and path-less) then OpenID Connect discovery.
 */
function authServerMetadataUrls(issuer) {
	const u = new URL(issuer);
	const path = u.pathname.replace(/\/+$/, '');
	const urls = [];
	if (path && path !== '') {
		urls.push(`${u.origin}/.well-known/oauth-authorization-server${path}`);
		urls.push(`${u.origin}${path}/.well-known/oauth-authorization-server`);
		urls.push(`${u.origin}${path}/.well-known/openid-configuration`);
	}
	urls.push(`${u.origin}/.well-known/oauth-authorization-server`);
	urls.push(`${u.origin}/.well-known/openid-configuration`);
	return urls;
}

/**
 * Parse the `WWW-Authenticate` header of a 401 from the MCP server. Per the MCP
 * spec the server SHOULD advertise its resource metadata document there, which is
 * the most reliable discovery hint available.
 */
function parseResourceMetadataHint(wwwAuthenticate) {
	if (!wwwAuthenticate) return null;
	const match = /resource_metadata\s*=\s*"([^"]+)"/i.exec(wwwAuthenticate);
	return match ? match[1] : null;
}

async function tryFetchMetadata(urls) {
	const attempts = [];
	for (const url of urls) {
		try {
			const { ok, status, body } = await fetchJson(url, {
				method: 'GET',
				headers: { Accept: 'application/json', 'MCP-Protocol-Version': '2025-06-18' }
			});
			if (ok && body && typeof body === 'object') {
				return { metadata: body, source: url, attempts };
			}
			attempts.push({ url, status });
		} catch (err) {
			attempts.push({ url, error: err.message });
		}
	}
	return { metadata: null, source: null, attempts };
}

/**
 * Discover the authorization server for an MCP endpoint and return its metadata.
 *
 * @param {string} mcpUrl        The MCP server URL.
 * @param {string} [hintHeader]  A WWW-Authenticate header from a prior 401, if any.
 */
async function discover(mcpUrl, hintHeader) {
	const attempts = [];

	// Step 1: protected resource metadata -> tells us which AS to talk to.
	const prUrls = [];
	const hint = parseResourceMetadataHint(hintHeader);
	if (hint) prUrls.push(hint);
	prUrls.push(...protectedResourceMetadataUrls(mcpUrl));

	const pr = await tryFetchMetadata(prUrls);
	attempts.push(...pr.attempts);

	let issuers = [];
	let scopesSupported = null;
	if (pr.metadata) {
		if (Array.isArray(pr.metadata.authorization_servers)) {
			issuers = pr.metadata.authorization_servers.filter((s) => typeof s === 'string');
		}
		if (Array.isArray(pr.metadata.scopes_supported)) {
			scopesSupported = pr.metadata.scopes_supported;
		}
	}

	// Fall back to treating the MCP origin itself as the issuer, which is what
	// servers that predate RFC 9728 expect.
	if (issuers.length === 0) {
		issuers = [new URL(mcpUrl).origin];
	}

	// Step 2: authorization server metadata -> endpoints we actually call.
	for (const issuer of issuers) {
		const as = await tryFetchMetadata(authServerMetadataUrls(issuer));
		attempts.push(...as.attempts);
		if (!as.metadata) continue;

		const md = as.metadata;
		if (!md.authorization_endpoint || !md.token_endpoint) {
			attempts.push({ url: as.source, error: 'metadata missing authorization_endpoint/token_endpoint' });
			continue;
		}

		return {
			ok: true,
			issuer: md.issuer || issuer,
			authorizationEndpoint: md.authorization_endpoint,
			tokenEndpoint: md.token_endpoint,
			registrationEndpoint: md.registration_endpoint || null,
			revocationEndpoint: md.revocation_endpoint || null,
			scopesSupported: md.scopes_supported || scopesSupported || null,
			codeChallengeMethodsSupported: md.code_challenge_methods_supported || null,
			tokenEndpointAuthMethodsSupported: md.token_endpoint_auth_methods_supported || null,
			resourceMetadata: pr.metadata || null,
			metadataSource: as.source,
			attempts
		};
	}

	return {
		ok: false,
		error:
			'Could not discover an OAuth authorization server for this MCP URL. ' +
			'The server may not require OAuth (try connecting without auth), or it may ' +
			'issue a static token you can paste directly.',
		attempts
	};
}

/* ---------------------------------------------------- dynamic registration */

/**
 * Register this app as an OAuth client (RFC 7591) so the user never has to create
 * a client ID by hand. Many MCP servers require DCR; those that don't support it
 * return 404/405, in which case the caller falls back to a user-supplied client ID.
 */
async function registerClient(registrationEndpoint, redirectUri, opts = {}) {
	if (!registrationEndpoint) {
		return { ok: false, unsupported: true, error: 'Server does not advertise a registration_endpoint.' };
	}

	const payload = {
		client_name: opts.clientName || 'Inventory Bot MCP Control Center',
		redirect_uris: [redirectUri],
		grant_types: ['authorization_code', 'refresh_token'],
		response_types: ['code'],
		token_endpoint_auth_method: 'none', // public client; PKCE is the protection
		application_type: 'web'
	};
	if (opts.scope) payload.scope = opts.scope;
	if (opts.clientUri) payload.client_uri = opts.clientUri;

	try {
		const { ok, status, body, text } = await fetchJson(registrationEndpoint, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
			body: JSON.stringify(payload)
		});

		if (!ok || !body || !body.client_id) {
			return {
				ok: false,
				unsupported: status === 404 || status === 405 || status === 501,
				status,
				error:
					(body && (body.error_description || body.error)) ||
					`Dynamic client registration failed with HTTP ${status}.`,
				raw: text ? String(text).slice(0, 500) : null
			};
		}

		return {
			ok: true,
			clientId: body.client_id,
			clientSecret: body.client_secret || null,
			registrationAccessToken: body.registration_access_token || null,
			tokenEndpointAuthMethod: body.token_endpoint_auth_method || 'none'
		};
	} catch (err) {
		return { ok: false, error: `Dynamic client registration request failed: ${err.message}` };
	}
}

/* -------------------------------------------------------- authorization URL */

/**
 * Build the authorization URL the user's browser is sent to. This is the page that
 * shows the provider's consent screen.
 */
function buildAuthorizeUrl(params) {
	const { authorizationEndpoint, clientId, redirectUri, codeChallenge, state, scope, resource } = params;

	const url = new URL(authorizationEndpoint);
	url.searchParams.set('response_type', 'code');
	url.searchParams.set('client_id', clientId);
	url.searchParams.set('redirect_uri', redirectUri);
	url.searchParams.set('code_challenge', codeChallenge);
	url.searchParams.set('code_challenge_method', 'S256');
	url.searchParams.set('state', state);
	if (scope) url.searchParams.set('scope', scope);
	// RFC 8707: bind the issued token to this specific MCP server.
	if (resource) url.searchParams.set('resource', resource);
	return url.toString();
}

/* ---------------------------------------------------------- token endpoint */

function buildTokenAuthHeaders(clientId, clientSecret) {
	const headers = {
		'Content-Type': 'application/x-www-form-urlencoded',
		Accept: 'application/json'
	};
	if (clientSecret) {
		// Confidential client: HTTP Basic per RFC 6749 §2.3.1.
		headers.Authorization =
			'Basic ' + Buffer.from(`${encodeURIComponent(clientId)}:${encodeURIComponent(clientSecret)}`).toString('base64');
	}
	return headers;
}

function normaliseTokenResponse(body) {
	const expiresIn = Number(body.expires_in);
	return {
		accessToken: body.access_token,
		refreshToken: body.refresh_token || null,
		tokenType: body.token_type || 'Bearer',
		scope: body.scope || null,
		// Absolute expiry so the browser can decide when to refresh without
		// depending on how long the response sat in flight.
		expiresAt: Number.isFinite(expiresIn) ? Date.now() + expiresIn * 1000 : null
	};
}

/** Exchange an authorization code for tokens. */
async function exchangeCode(params) {
	const { tokenEndpoint, clientId, clientSecret, code, codeVerifier, redirectUri, resource } = params;

	const form = new URLSearchParams();
	form.set('grant_type', 'authorization_code');
	form.set('code', code);
	form.set('redirect_uri', redirectUri);
	form.set('code_verifier', codeVerifier);
	form.set('client_id', clientId); // required for public clients
	if (resource) form.set('resource', resource);

	const { ok, status, body, text } = await fetchJson(tokenEndpoint, {
		method: 'POST',
		headers: buildTokenAuthHeaders(clientId, clientSecret),
		body: form.toString()
	});

	if (!ok || !body || !body.access_token) {
		return {
			ok: false,
			status,
			error: (body && (body.error_description || body.error)) || `Token exchange failed with HTTP ${status}.`,
			raw: text ? String(text).slice(0, 500) : null
		};
	}

	return { ok: true, ...normaliseTokenResponse(body) };
}

/** Trade a refresh token for a fresh access token. */
async function refreshToken(params) {
	const { tokenEndpoint, clientId, clientSecret, refreshToken: token, resource, scope } = params;

	const form = new URLSearchParams();
	form.set('grant_type', 'refresh_token');
	form.set('refresh_token', token);
	form.set('client_id', clientId);
	if (resource) form.set('resource', resource);
	if (scope) form.set('scope', scope);

	const { ok, status, body, text } = await fetchJson(tokenEndpoint, {
		method: 'POST',
		headers: buildTokenAuthHeaders(clientId, clientSecret),
		body: form.toString()
	});

	if (!ok || !body || !body.access_token) {
		return {
			ok: false,
			status,
			error: (body && (body.error_description || body.error)) || `Token refresh failed with HTTP ${status}.`,
			raw: text ? String(text).slice(0, 500) : null
		};
	}

	// Some servers rotate the refresh token and some omit it; keep the old one
	// when the response has none, or the session silently becomes un-refreshable.
	const next = normaliseTokenResponse(body);
	if (!next.refreshToken) next.refreshToken = token;
	return { ok: true, ...next };
}

module.exports = {
	createPkcePair,
	randomState,
	canonicalResource,
	discover,
	registerClient,
	buildAuthorizeUrl,
	exchangeCode,
	refreshToken,
	fetchWithTimeout,
	fetchJson
};
