(function () {
  'use strict';

  const STORAGE_KEY = 'inventorybot.settings.v1';

  function loadSettings() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return defaultSettings();
      return Object.assign(defaultSettings(), JSON.parse(raw));
    } catch {
      return defaultSettings();
    }
  }

  function defaultSettings() {
    return {
      provider: 'anthropic',
      modelName: '',
      apiKey: '',
      apiBase: '', // advanced: override the default /server/inventory_bot_api path
      mcps: [] // { mcpId, name, url, role, accessToken, refreshToken, expiresAt, tokenEndpoint, clientId, clientSecret, resource }
    };
  }

  function persist(settings) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  }

  function newId() {
    return (crypto.randomUUID && crypto.randomUUID()) || `mcp-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  let state = loadSettings();

  const els = {};

  function cacheEls() {
    els.overlay = document.getElementById('settingsOverlay');
    els.settingsBtn = document.getElementById('settingsBtn');
    els.closeBtn = document.getElementById('closeSettingsBtn');
    els.saveBtn = document.getElementById('saveSettingsBtn');
    els.providerSelect = document.getElementById('providerSelect');
    els.modelNameInput = document.getElementById('modelNameInput');
    els.apiKeyInput = document.getElementById('apiKeyInput');
    els.apiBaseInput = document.getElementById('apiBaseInput');

    els.zohoMcpUrlInput = document.getElementById('zohoMcpUrlInput');
    els.connectZohoBtn = document.getElementById('connectZohoBtn');
    els.zohoConnectStatus = document.getElementById('zohoConnectStatus');

    els.mcpServerList = document.getElementById('mcpServerList');
    els.mcpNameInput = document.getElementById('mcpNameInput');
    els.mcpUrlInput = document.getElementById('mcpUrlInput');
    els.mcpTokenInput = document.getElementById('mcpTokenInput');
    els.mcpClientIdInput = document.getElementById('mcpClientIdInput');
    els.mcpClientSecretInput = document.getElementById('mcpClientSecretInput');
    els.mcpConnectStatus = document.getElementById('mcpConnectStatus');
    els.connectMcpBtn = document.getElementById('connectMcpBtn');
    els.addMcpBtn = document.getElementById('addMcpBtn');

    els.mailStatusPill = document.getElementById('mailStatusPill');
    els.inventoryStatusPill = document.getElementById('inventoryStatusPill');
    els.modelStatusPill = document.getElementById('modelStatusPill');
    els.chatModelTag = document.getElementById('chatModelTag');
  }

  function getZohoMcp() {
    return state.mcps.find((m) => m.role === 'mail');
  }

  function renderForm() {
    els.providerSelect.value = state.provider;
    els.modelNameInput.value = state.modelName || '';
    els.apiKeyInput.value = state.apiKey || '';
    els.apiBaseInput.value = state.apiBase || '';

    const zohoMcp = getZohoMcp();
    if (!els.zohoMcpUrlInput.matches(':focus')) {
      els.zohoMcpUrlInput.value = zohoMcp ? zohoMcp.url : els.zohoMcpUrlInput.value;
    }
    if (zohoMcp) {
      els.zohoConnectStatus.textContent = 'Connected';
      els.zohoConnectStatus.dataset.state = 'on';
      els.connectZohoBtn.textContent = 'Reconnect';
    } else {
      els.zohoConnectStatus.textContent = 'Not connected';
      els.zohoConnectStatus.dataset.state = 'off';
      els.connectZohoBtn.textContent = 'Connect via OAuth';
    }

    const otherMcps = state.mcps.filter((m) => m.role !== 'mail');
    els.mcpServerList.innerHTML = '';
    if (otherMcps.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'empty-state';
      empty.textContent = 'No additional MCP servers configured yet.';
      els.mcpServerList.appendChild(empty);
    }
    otherMcps.forEach((mcp) => {
      const row = document.createElement('div');
      row.className = 'mcp-item';
      row.innerHTML = `<span>${mcp.name}</span><span class="mcp-remove" data-mcp-id="${mcp.mcpId}">Remove</span>`;
      els.mcpServerList.appendChild(row);
    });

    els.mcpConnectStatus.textContent = 'Not connected';
    els.mcpConnectStatus.dataset.state = 'off';
  }

  function renderTopStatus() {
    els.mailStatusPill.dataset.state = state.mcps.some((m) => m.role === 'mail') ? 'on' : 'off';
    els.inventoryStatusPill.dataset.state = state.mcps.some((m) => m.role === 'inventory') ? 'on' : 'off';
    const hasModel = !!(state.apiKey && state.modelName);
    els.modelStatusPill.dataset.state = hasModel ? 'on' : 'off';
    els.chatModelTag.textContent = hasModel ? `${state.provider} · ${state.modelName}` : 'No model configured';
  }

  function openModal() {
    renderForm();
    els.overlay.classList.add('open');
  }

  function closeModal() {
    els.overlay.classList.remove('open');
  }

  function upsertMcp(mcp) {
    const idx = state.mcps.findIndex((m) => m.mcpId === mcp.mcpId);
    if (idx >= 0) state.mcps[idx] = mcp;
    else state.mcps.push(mcp);
  }

  /**
   * Connecting an MCP navigates the whole page away and back (see oauth.js).
   * Anything only held in the form fields - the model provider/name/API key
   * the user just typed in, but hasn't clicked Save yet - would otherwise be
   * lost on that reload. Save it to state first so it survives.
   */
  function persistModelFields() {
    state.provider = els.providerSelect.value;
    state.modelName = els.modelNameInput.value.trim();
    state.apiKey = els.apiKeyInput.value.trim();
    state.apiBase = els.apiBaseInput.value.trim();
    persist(state);
  }

  /**
   * Both "Connect via OAuth" buttons navigate the whole page away to the
   * provider's consent screen and back (see oauth.js) - the same approach
   * the voice assistant app uses, rather than a popup. That means nothing
   * after `OAuthFlow.begin()` resolves runs in the normal case; the result
   * is picked up on the next page load by resumeOAuthIfReturning() below,
   * tagged with `kind` so it knows which of these two flows it belongs to.
   */
  async function handleConnectZoho() {
    const url = els.zohoMcpUrlInput.value.trim();
    if (!url) {
      alert('Enter the Zoho Mail MCP URL first, then connect.');
      return;
    }

    els.connectZohoBtn.disabled = true;
    els.connectZohoBtn.textContent = 'Connecting...';
    persistModelFields();
    try {
      await window.OAuthFlow.begin(url, undefined, undefined, 'Inventory Bot for Zoho Mail', 'zoho', { url });
    } catch (err) {
      alert(`Zoho Mail connection failed: ${err.message}`);
      els.connectZohoBtn.disabled = false;
      renderForm();
    }
  }

  async function handleConnectMcp() {
    const name = els.mcpNameInput.value.trim();
    const url = els.mcpUrlInput.value.trim();
    if (!name || !url) {
      alert('Please provide both a name and a server URL, then connect via OAuth.');
      return;
    }
    const clientId = els.mcpClientIdInput.value.trim() || undefined;
    const clientSecret = els.mcpClientSecretInput.value.trim() || undefined;

    els.connectMcpBtn.disabled = true;
    els.connectMcpBtn.textContent = 'Connecting...';
    persistModelFields();
    try {
      await window.OAuthFlow.begin(url, clientId, clientSecret, undefined, 'other', { name, url });
    } catch (err) {
      alert(`MCP connection failed: ${err.message}`);
      els.connectMcpBtn.disabled = false;
      els.connectMcpBtn.textContent = 'Connect via OAuth';
    }
  }

  /** Paste-a-token path: no redirect involved, so this finishes immediately. */
  async function handleAddMcp() {
    const name = els.mcpNameInput.value.trim();
    const url = els.mcpUrlInput.value.trim();
    const manualToken = els.mcpTokenInput.value.trim();

    if (!name || !url) {
      alert('Please provide both a name and a server URL for the MCP.');
      return;
    }
    if (!manualToken) {
      alert('Paste an access token, or use "Connect via OAuth" instead.');
      return;
    }

    els.addMcpBtn.disabled = true;
    try {
      // Validate the connection now, rather than after the first chat message.
      await window.Api.listMcpTools({ url, accessToken: manualToken, tokenType: 'Bearer' });

      const mcp = { mcpId: newId(), name, url, role: 'inventory', accessToken: manualToken };
      state.mcps.push(mcp);
      persist(state);
      els.mcpNameInput.value = '';
      els.mcpUrlInput.value = '';
      els.mcpTokenInput.value = '';
      els.mcpClientIdInput.value = '';
      els.mcpClientSecretInput.value = '';
      renderForm();
      renderTopStatus();
      window.dispatchEvent(new CustomEvent('inventorybot:mcp-list-changed'));
    } catch (err) {
      alert(`Could not add MCP server: ${err.message}`);
    } finally {
      els.addMcpBtn.disabled = false;
    }
  }

  /**
   * On load, finish an OAuth round trip if we came back with a code. Must
   * run before anything else touches settings state.
   */
  async function resumeOAuthIfReturning() {
    if (!window.OAuthFlow.hasCallbackParams()) return;

    try {
      const result = await window.OAuthFlow.complete();
      if (!result) return;

      const { kind, extra, token } = result;
      if (kind === 'zoho') {
        const existing = getZohoMcp();
        upsertMcp({
          mcpId: existing ? existing.mcpId : newId(),
          name: 'Zoho Mail',
          url: extra.url,
          role: 'mail',
          accessToken: token.accessToken,
          refreshToken: token.refreshToken,
          expiresAt: token.expiresAt,
          tokenEndpoint: token.tokenEndpoint,
          clientId: token.clientId,
          clientSecret: token.clientSecret,
          resource: token.resource
        });
      } else if (kind === 'other') {
        // Validate the connection now, rather than after the first chat message.
        await window.Api.listMcpTools({ url: extra.url, accessToken: token.accessToken, tokenType: 'Bearer' });
        state.mcps.push({
          mcpId: newId(),
          name: extra.name,
          url: extra.url,
          role: 'inventory',
          accessToken: token.accessToken,
          refreshToken: token.refreshToken,
          expiresAt: token.expiresAt,
          tokenEndpoint: token.tokenEndpoint,
          clientId: token.clientId,
          clientSecret: token.clientSecret,
          resource: token.resource
        });
      }
      persist(state);
      renderTopStatus();
      window.dispatchEvent(new CustomEvent('inventorybot:mcp-list-changed'));
      openModal();
    } catch (err) {
      alert(`Authorization failed: ${err.message}`);
      openModal();
    }
  }

  function handleRemoveMcp(mcpId) {
    state.mcps = state.mcps.filter((m) => m.mcpId !== mcpId);
    persist(state);
    renderForm();
    renderTopStatus();
    window.dispatchEvent(new CustomEvent('inventorybot:mcp-list-changed'));
  }

  function handleSave() {
    persistModelFields();
    renderTopStatus();
    closeModal();
  }

  async function init() {
    cacheEls();
    renderTopStatus();
    await resumeOAuthIfReturning();

    els.settingsBtn.addEventListener('click', openModal);
    els.closeBtn.addEventListener('click', closeModal);
    els.overlay.addEventListener('click', (e) => {
      if (e.target === els.overlay) closeModal();
    });
    els.saveBtn.addEventListener('click', handleSave);
    els.connectZohoBtn.addEventListener('click', handleConnectZoho);
    els.connectMcpBtn.addEventListener('click', handleConnectMcp);
    els.addMcpBtn.addEventListener('click', handleAddMcp);
    els.mcpServerList.addEventListener('click', (e) => {
      const target = e.target.closest('[data-mcp-id]');
      if (target) handleRemoveMcp(target.dataset.mcpId);
    });
  }

  /**
   * Refresh an expired access token using its refresh token.
   * Returns true if the token was refreshed and state updated, false otherwise.
   */
  async function maybeRefreshToken(mcp) {
    if (!mcp.expiresAt || !mcp.refreshToken || !mcp.tokenEndpoint) return false;

    // Token is still valid for at least 5 minutes, don't refresh yet
    if (Date.now() < mcp.expiresAt - 5 * 60 * 1000) return false;

    console.log(`[Token] Refreshing token for MCP "${mcp.name}"`);
    try {
      const result = await window.Api.refreshOAuthToken({
        tokenEndpoint: mcp.tokenEndpoint,
        clientId: mcp.clientId,
        clientSecret: mcp.clientSecret,
        refreshToken: mcp.refreshToken,
        resource: mcp.resource
      });
      if (result.ok) {
        mcp.accessToken = result.accessToken;
        if (result.refreshToken) mcp.refreshToken = result.refreshToken;
        if (result.expiresAt) mcp.expiresAt = result.expiresAt;
        persist(state);
        console.log(`[Token] Successfully refreshed token for "${mcp.name}"`);
        return true;
      }
    } catch (err) {
      console.error(`[Token] Failed to refresh token for "${mcp.name}":`, err.message);
    }
    return false;
  }

  /**
   * Ensure all MCPs have valid, non-expired access tokens before they're used.
   * Silently refreshes any tokens that are expired or about to expire.
   */
  async function ensureValidTokens() {
    for (const mcp of state.mcps) {
      if (mcp.accessToken) {
        await maybeRefreshToken(mcp);
      }
    }
  }

  const Settings = {
    init,
    get: () => state,
    getApiBase: () => state.apiBase,
    getModelConfig: () => ({ provider: state.provider, modelName: state.modelName, apiKey: state.apiKey }),
    getMcps: () => state.mcps,
    getMcpsByRole: (role) => state.mcps.filter((m) => m.role === role),
    getMcpConfigsForChat: async () => {
      await ensureValidTokens();
      return state.mcps.map((m) => ({ mcpId: m.mcpId, name: m.name, url: m.url, accessToken: m.accessToken, tokenType: 'Bearer' }));
    }
  };

  window.Settings = Settings;
})();
