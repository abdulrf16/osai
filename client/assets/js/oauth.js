(function () {
  'use strict';

  const PENDING_KEY = 'inventorybot.oauth.pending.v1';

  /** This page's URL, stripped of query and hash, used as the redirect URI. */
  function redirectUri() {
    return window.location.origin + window.location.pathname;
  }

  function setPending(record) {
    sessionStorage.setItem(PENDING_KEY, JSON.stringify(record));
  }

  function takePending() {
    const raw = sessionStorage.getItem(PENDING_KEY);
    sessionStorage.removeItem(PENDING_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  const OAuthFlow = {
    redirectUri,

    /**
     * Starts the MCP OAuth 2.1 + PKCE + Dynamic Client Registration flow with
     * a full-page redirect to the provider's consent screen - the same
     * approach the voice assistant app uses, and for the same reason: it
     * survives pop-up blockers and works on mobile. An earlier version of
     * this used a popup instead, which consistently failed right after the
     * user accepted on Zoho's real consent page; rather than keep debugging
     * that blind, this removes the popup from the picture entirely.
     *
     * Navigates away immediately, so nothing after this call runs. The PKCE
     * verifier and everything else needed to finish the exchange is stashed
     * in sessionStorage under `kind`/`extra` so the calling code can tell,
     * once the browser comes back, which settings-UI flow (Zoho's predefined
     * slot, or the generic "+ Add more" form) this connection belongs to -
     * the full-page reload wipes all in-memory JS state.
     *
     * @param {string} mcpUrl
     * @param {string} [clientId]
     * @param {string} [clientSecret]
     * @param {string} [clientName]
     * @param {string} kind 'zoho' or 'other'
     * @param {object} [extra]
     */
    async begin(mcpUrl, clientId, clientSecret, clientName, kind, extra) {
      const redirect = redirectUri();
      const started = await window.Api.oauthStart(mcpUrl, redirect, clientId, clientSecret, clientName);
      setPending({ pending: started.pending, kind, extra: extra || {} });
      window.location.href = started.authorizeUrl;
    },

    /** True when we have come back from a consent page with a code (or an error). */
    hasCallbackParams() {
      const params = new URLSearchParams(window.location.search);
      return params.has('code') || params.has('error');
    },

    /**
     * Completes an OAuth round trip after the provider redirects back here.
     * Scrubs the code/state out of the address bar before doing anything
     * else with them, regardless of outcome.
     *
     * @returns {Promise<null|{kind: string, extra: object, token: object}>}
     */
    async complete() {
      const params = new URLSearchParams(window.location.search);
      const code = params.get('code');
      const state = params.get('state');
      const oauthError = params.get('error');
      const description = params.get('error_description');

      window.history.replaceState({}, document.title, redirectUri());

      if (oauthError) {
        throw new Error(description || `Authorisation was declined: ${oauthError}`);
      }
      if (!code) return null;

      const record = takePending();
      if (!record) {
        throw new Error('The authorisation session expired. Open settings and connect again.');
      }
      if (record.pending.state && state && record.pending.state !== state) {
        throw new Error('OAuth state mismatch - possible CSRF, connection aborted.');
      }

      const token = await window.Api.oauthCallback(record.pending, code);
      return { kind: record.kind, extra: record.extra, token };
    }
  };

  window.OAuthFlow = OAuthFlow;
})();
