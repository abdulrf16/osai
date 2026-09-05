(function () {
  'use strict';

  // When the client and the function live in the same Catalyst project, the
  // function is reachable at this path on the same origin, so the user never
  // has to paste an invocation URL.
  var DEFAULT_PATH = '/server/inventory_bot_api';

  function base() {
    var configured = (window.Settings && window.Settings.getApiBase && window.Settings.getApiBase()) || '';
    configured = configured.trim();
    if (configured) return configured.replace(/\/+$/, '');
    return DEFAULT_PATH;
  }

  async function postJson(path, body) {
    const res = await fetch(base() + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body || {})
    });
    const data = await res.json().catch(() => ({ ok: false, error: 'Invalid server response' }));
    if (!res.ok || data.ok === false) {
      throw new Error(data.error || `Request failed (${res.status})`);
    }
    return data;
  }

  const Api = {
    async chat(message, modelConfig, conversationHistory, mcpConfigs) {
      const data = await postJson('/api/chat', { message, modelConfig, conversationHistory, mcpConfigs });
      return data.response;
    },

    async listMcpTools(mcp) {
      const data = await postJson('/api/mcp/tools', { mcp });
      return data.tools;
    },

    async executeTool(mcp, toolName, toolInput) {
      const data = await postJson('/api/mcp/execute', { mcp, toolName, toolInput });
      return data.result;
    },

    async discoverOAuth(mcpUrl, redirectUri, clientId, clientSecret) {
      return postJson('/api/oauth/discover', { mcpUrl, redirectUri, clientId, clientSecret });
    },

    async exchangeOAuthToken({ tokenEndpoint, clientId, clientSecret, code, codeVerifier, redirectUri }) {
      return postJson('/api/oauth/token', { tokenEndpoint, clientId, clientSecret, code, codeVerifier, redirectUri });
    },

    async refreshOAuthToken({ tokenEndpoint, clientId, clientSecret, refreshToken }) {
      return postJson('/api/oauth/refresh', { tokenEndpoint, clientId, clientSecret, refreshToken });
    }
  };

  window.Api = Api;
})();
