(function () {
  'use strict';

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
     * setup needed. Discovery, PKCE generation and authorize-URL construction
     * all happen server-side (POST /api/oauth/start) so every MCP server goes
     * through the exact same, already-verified request shape; this just opens
     * the popup the server hands back and relays the resulting code.
     */
    async connectMcp(mcpUrl, manualClientId, manualClientSecret, clientName) {
      const redirect = redirectUri();
      const started = await window.Api.oauthStart(mcpUrl, redirect, manualClientId, manualClientSecret, clientName);

      const { code } = await openPopupAndWait(started.authorizeUrl, started.pending.state);
      const result = await window.Api.oauthCallback(started.pending, code);

      return {
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
        expiresAt: result.expiresAt,
        tokenEndpoint: result.tokenEndpoint,
        clientId: result.clientId,
        clientSecret: result.clientSecret,
        resource: result.resource
      };
    }
  };

  window.OAuthFlow = OAuthFlow;
})();
