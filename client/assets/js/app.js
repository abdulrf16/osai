(function () {
  'use strict';

  const els = {};

  // Third-party MCP servers won't share exact tool names, so the panels try
  // a short list of common ones rather than assuming any single schema.
  const PANEL_TOOL_CANDIDATES = {
    mail: ['list_messages', 'search_messages', 'list_emails', 'get_messages', 'list_inbox'],
    inventory: ['list_records', 'search_records', 'get_records', 'list_items', 'list_inventory']
  };

  function cacheEls() {
    els.mailList = document.getElementById('mailList');
    els.inventoryList = document.getElementById('inventoryList');
    els.refreshMailBtn = document.getElementById('refreshMailBtn');
    els.refreshInventoryBtn = document.getElementById('refreshInventoryBtn');
    els.mailStatusPill = document.getElementById('mailStatusPill');
    els.inventoryStatusPill = document.getElementById('inventoryStatusPill');
  }

  function pick(obj, keys) {
    for (const k of keys) {
      if (obj && obj[k] !== undefined && obj[k] !== null && obj[k] !== '') return obj[k];
    }
    return null;
  }

  /** Best-effort: turn whatever shape a tool returned into a flat list of records. */
  function extractRecords(raw) {
    if (!raw) return [];
    if (Array.isArray(raw)) return raw;
    if (Array.isArray(raw.content)) {
      // MCP tool results often wrap output as content blocks with text/json
      const textBlock = raw.content.find((b) => b.type === 'text');
      if (textBlock) {
        try {
          return extractRecords(JSON.parse(textBlock.text));
        } catch {
          return [{ __raw: textBlock.text }];
        }
      }
    }
    for (const key of ['messages', 'records', 'items', 'emails', 'data', 'results']) {
      if (Array.isArray(raw[key])) return raw[key];
    }
    return [raw];
  }

  function renderEmptyState(container, text) {
    container.innerHTML = `<div class="empty-state">${text}</div>`;
  }

  async function loadPanelData(role) {
    const mcp = window.Settings.getMcpsByRole(role)[0];
    if (!mcp) return { connected: false, records: [] };

    const mcpConfig = { url: mcp.url, accessToken: mcp.accessToken, tokenType: 'Bearer' };
    const tools = await window.Api.listMcpTools(mcpConfig);
    const candidates = PANEL_TOOL_CANDIDATES[role] || [];
    const match = tools.find((t) => candidates.includes(t.name));
    if (!match) return { connected: true, records: [], toolNames: tools.map((t) => t.name) };

    const result = await window.Api.executeTool(mcpConfig, match.name, { limit: 10 });
    return { connected: true, records: extractRecords(result) };
  }

  async function loadMailPanel() {
    const configured = window.Settings.getMcpsByRole('mail').length > 0;
    els.mailStatusPill.dataset.state = configured ? 'on' : 'off';
    if (!configured) {
      renderEmptyState(els.mailList, 'Add a Mail-role MCP server in settings to see your inbox here.');
      return;
    }

    try {
      const data = await loadPanelData('mail');
      if (!data.records.length) {
        renderEmptyState(
          els.mailList,
          data.toolNames
            ? `Connected, but no recognized listing tool found. Available tools: ${data.toolNames.join(', ')}`
            : 'No messages found.'
        );
        return;
      }
      els.mailList.innerHTML = '';
      data.records.slice(0, 20).forEach((r) => {
        const from = pick(r, ['from', 'sender', 'fromAddress', 'author']) || 'Unknown sender';
        const subject = pick(r, ['subject', 'title', 'name']) || (r.__raw ? String(r.__raw).slice(0, 80) : '(no subject)');
        const time = pick(r, ['date', 'receivedTime', 'sentTime', 'time']) || '';
        const item = document.createElement('div');
        item.className = 'mail-item';
        item.innerHTML = `<span class="mail-time">${time}</span><div class="mail-from">${from}</div><div class="mail-subject">${subject}</div>`;
        els.mailList.appendChild(item);
      });
    } catch (err) {
      renderEmptyState(els.mailList, `Could not load mail: ${err.message}`);
    }
  }

  async function loadInventoryPanel() {
    const configured = window.Settings.getMcpsByRole('inventory').length > 0;
    els.inventoryStatusPill.dataset.state = configured ? 'on' : 'off';
    if (!configured) {
      renderEmptyState(els.inventoryList, 'Add an Inventory-role MCP server (e.g. Airtable) in settings to see stock here.');
      return;
    }

    try {
      const data = await loadPanelData('inventory');
      if (!data.records.length) {
        renderEmptyState(
          els.inventoryList,
          data.toolNames
            ? `Connected, but no recognized listing tool found. Available tools: ${data.toolNames.join(', ')}`
            : 'No inventory records found.'
        );
        return;
      }
      els.inventoryList.innerHTML = '';
      data.records.slice(0, 20).forEach((r) => {
        const fields = r.fields || r; // Airtable-style records nest under `fields`
        const name = pick(fields, ['name', 'Name', 'title', 'Title', 'sku', 'SKU']) || (r.__raw ? String(r.__raw).slice(0, 80) : 'Item');
        const qty = pick(fields, ['quantity', 'Quantity', 'stock', 'Stock', 'count']);
        const location = pick(fields, ['location', 'Location', 'warehouse']);
        const qtyNum = Number(qty);
        const pct = Number.isFinite(qtyNum) ? Math.max(0, Math.min(100, qtyNum)) : null;

        const card = document.createElement('div');
        card.className = 'inv-card';
        card.innerHTML = `
          <div class="inv-name">${name}</div>
          <div class="inv-meta"><span>${location || ''}</span><span>${qty !== null ? `Qty: ${qty}` : ''}</span></div>
          ${pct !== null ? `<div class="inv-qty-bar"><div class="inv-qty-fill" style="width:${pct}%"></div></div>` : ''}
        `;
        els.inventoryList.appendChild(card);
      });
    } catch (err) {
      renderEmptyState(els.inventoryList, `Could not load inventory: ${err.message}`);
    }
  }

  async function init() {
    cacheEls();
    window.Settings.init();
    window.Chat.init();

    await Promise.all([loadMailPanel(), loadInventoryPanel()]);

    els.refreshMailBtn.addEventListener('click', loadMailPanel);
    els.refreshInventoryBtn.addEventListener('click', loadInventoryPanel);
    window.addEventListener('osai:mcp-list-changed', async () => {
      await Promise.all([loadMailPanel(), loadInventoryPanel()]);
    });
  }

  document.addEventListener('DOMContentLoaded', init);
})();
