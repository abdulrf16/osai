(function () {
  'use strict';

  function base64url(buffer) {
    const bytes = new Uint8Array(buffer);
    let str = '';
    for (let i = 0; i < bytes.length; i++) str += String.fromCharCode(bytes[i]);
    return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  function randomBytes(len) {
    const arr = new Uint8Array(len);
    crypto.getRandomValues(arr);
    return arr;
  }

  async function sha256(str) {
    const data = new TextEncoder().encode(str);
    return crypto.subtle.digest('SHA-256', data);
  }

  function redirectUri() {
    return `${window.location.origin}/oauth-callback.html`;
  }

  function openPopupAndWait(url, expectedState) {
    return new Promise((resolve, reject) => {
      const popup = window.open(url, 'inventorybot-oauth', 'width=520,height=680');
      if (!popup) {
        reject(new Error('Popup blocked. Please allow popups for this site.'));
        return;
      }

      let settled = false;
      const onMessage = (event) => {
        if (event.origin !== window.location.origin) return;
        const data = event.data;
        if (!data || data.type !== 'inventorybot-oauth-callback') return;
        settled = true;
        window.removeEventListener('message', onMessage);
        clearInterval(closeWatcher);
        if (data.error) {
          reject(new Error(data.error));
        } else if (data.state !== expectedState) {
          reject(new Error('OAuth state mismatch - possible CSRF, connection aborted.'));
        } else {
          resolve({ code: data.code });
        }
      };
      window.addEventListener('message', onMessage);

      const closeWatcher = setInterval(() => {
        if (popup.closed) {
          clearInterval(closeWatcher);
          window.removeEventListener('message', onMessage);
          if (!settled) reject(new Error('Connection window closed before completing.'));
        }
      }, 500);
    });
  }

  const OAuthFlow = {
    /**
     * Runs the full MCP OAuth 2.1 + PKCE + Dynamic Client Registration flow
     * against whatever MCP server URL the user supplied - no per-provider
     * setup needed. The PKCE code_verifier is generated and kept here in the
     * browser and never sent anywhere except the final token exchange, so it
     * never appears in a redirect URL (which would defeat PKCE).
     */
    async connectMcp(mcpUrl, manualClientId, manualClientSecret) {
      const redirect = redirectUri();
      const { authorizationEndpoint, tokenEndpoint, clientId, clientSecret } = await window.Api.discoverOAuth(
        mcpUrl,
        redirect,
        manualClientId,
        manualClientSecret
      );

      const codeVerifier = base64url(randomBytes(32));
      const codeChallenge = base64url(await sha256(codeVerifier));
      const state = base64url(randomBytes(16));

      const params = new URLSearchParams({
        response_type: 'code',
        client_id: clientId,
        redirect_uri: redirect,
        state,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        resource: mcpUrl
      });
      const authUrl = `${authorizationEndpoint}?${params.toString()}`;

      const { code } = await openPopupAndWait(authUrl, state);
      const tokenResult = await window.Api.exchangeOAuthToken({
        tokenEndpoint,
        clientId,
        clientSecret,
        code,
        codeVerifier,
        redirectUri: redirect
      });

      return { ...tokenResult, tokenEndpoint, clientId, clientSecret };
    }
  };

  window.OAuthFlow = OAuthFlow;
})();
