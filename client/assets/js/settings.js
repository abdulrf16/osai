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
  // Captured from the last "Connect via OAuth" click, awaiting the matching
  // "Connect"/"Add MCP server" action. Kept separate for Zoho Mail (a single,
  // predefined connection) and for the generic "+ Add more MCP servers" form.
  let pendingZohoToken = null;
  let pendingOAuthToken = null;

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
    } else if (pendingZohoToken) {
      els.zohoConnectStatus.textContent = 'Token ready — click Connect again to save';
      els.zohoConnectStatus.dataset.state = 'on';
      els.connectZohoBtn.textContent = 'Connect via OAuth';
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

    els.mcpConnectStatus.textContent = pendingOAuthToken ? 'Token ready — click "Add MCP server"' : 'Not connected';
    els.mcpConnectStatus.dataset.state = pendingOAuthToken ? 'on' : 'off';
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

  async function handleConnectZoho() {
    const url = els.zohoMcpUrlInput.value.trim();
    if (!url) {
      alert('Enter the Zoho Mail MCP URL first, then connect.');
      return;
    }

    els.connectZohoBtn.disabled = true;
    els.connectZohoBtn.textContent = 'Connecting...';
    try {
      const result = await window.OAuthFlow.connectMcp(url, undefined, undefined, 'Inventory Bot for Zoho Mail');
      const existing = getZohoMcp();
      const mcp = {
        mcpId: existing ? existing.mcpId : newId(),
        name: 'Zoho Mail',
        url,
        role: 'mail',
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
        expiresAt: result.expiresAt,
        tokenEndpoint: result.tokenEndpoint,
        clientId: result.clientId,
        clientSecret: result.clientSecret,
        resource: result.resource
      };
      upsertMcp(mcp);
      persist(state);
      pendingZohoToken = null;
      renderForm();
      renderTopStatus();
      window.dispatchEvent(new CustomEvent('inventorybot:mcp-list-changed'));
    } catch (err) {
      alert(`Zoho Mail connection failed: ${err.message}`);
    } finally {
      els.connectZohoBtn.disabled = false;
      renderForm();
    }
  }

  async function handleConnectMcp() {
    const url = els.mcpUrlInput.value.trim();
    if (!url) {
      alert('Enter the MCP server URL first, then connect via OAuth.');
      return;
    }
    const clientId = els.mcpClientIdInput.value.trim() || undefined;
    const clientSecret = els.mcpClientSecretInput.value.trim() || undefined;

    els.connectMcpBtn.disabled = true;
    els.connectMcpBtn.textContent = 'Connecting...';
    try {
      const result = await window.OAuthFlow.connectMcp(url, clientId, clientSecret);
      pendingOAuthToken = {
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
        expiresAt: result.expiresAt,
        tokenEndpoint: result.tokenEndpoint,
        clientId: result.clientId,
        clientSecret: result.clientSecret,
        resource: result.resource
      };
      renderForm();
    } catch (err) {
      alert(`MCP connection failed: ${err.message}`);
    } finally {
      els.connectMcpBtn.disabled = false;
      els.connectMcpBtn.textContent = 'Connect via OAuth';
    }
  }

  async function handleAddMcp() {
    const name = els.mcpNameInput.value.trim();
    const url = els.mcpUrlInput.value.trim();
    const manualToken = els.mcpTokenInput.value.trim();

    if (!name || !url) {
      alert('Please provide both a name and a server URL for the MCP.');
      return;
    }

    const tokenInfo = pendingOAuthToken || (manualToken ? { accessToken: manualToken } : {});

    els.addMcpBtn.disabled = true;
    try {
      // Validate the connection now, rather than after the first chat message.
      await window.Api.listMcpTools({ url, accessToken: tokenInfo.accessToken, tokenType: 'Bearer' });

      const mcp = { mcpId: newId(), name, url, role: 'inventory', ...tokenInfo };
      state.mcps.push(mcp);
      persist(state);
      pendingOAuthToken = null;
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

  function handleRemoveMcp(mcpId) {
    state.mcps = state.mcps.filter((m) => m.mcpId !== mcpId);
    persist(state);
    renderForm();
    renderTopStatus();
    window.dispatchEvent(new CustomEvent('inventorybot:mcp-list-changed'));
  }

  function handleSave() {
    state.provider = els.providerSelect.value;
    state.modelName = els.modelNameInput.value.trim();
    state.apiKey = els.apiKeyInput.value.trim();
    state.apiBase = els.apiBaseInput.value.trim();
    persist(state);
    renderTopStatus();
    closeModal();
  }

  function init() {
    cacheEls();
    renderTopStatus();

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

  const Settings = {
    init,
    get: () => state,
    getApiBase: () => state.apiBase,
    getModelConfig: () => ({ provider: state.provider, modelName: state.modelName, apiKey: state.apiKey }),
    getMcps: () => state.mcps,
    getMcpsByRole: (role) => state.mcps.filter((m) => m.role === role),
    getMcpConfigsForChat: () =>
      state.mcps.map((m) => ({ mcpId: m.mcpId, name: m.name, url: m.url, accessToken: m.accessToken, tokenType: 'Bearer' }))
  };

  window.Settings = Settings;
})();
