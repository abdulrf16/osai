(function () {
  'use strict';

  const els = {};
  let latestEmails = [];

  const MAIL_LIST_INSTRUCTION =
    'List the 10 most recently received emails in the inbox, most recent first. ' +
    'Respond with ONLY a JSON array, no prose, no markdown code fences, each item exactly: ' +
    '{"id": "<message id>", "from": "<sender name or address>", "subject": "<subject>", ' +
    '"date": "<date/time as a readable string>", "snippet": "<first line or short preview of the body>"}.';

  const INVENTORY_LIST_INSTRUCTION =
    'List all products or items you can find, with their current stock quantity, across all connected ' +
    'inventory tables/bases. Respond with ONLY a JSON array, no prose, no markdown code fences, each item exactly: ' +
    '{"name": "<product name>", "sku": "<sku or code, or null>", "quantity": <number or null>, "location": "<location/warehouse, or null>"}.';

  function mailDetailInstruction(id) {
    return (
      `Get the full sender, subject, date, and complete plain-text body of the email with id "${id}". ` +
      'Respond with ONLY JSON, no prose, no markdown code fences, exactly: ' +
      '{"from": "<sender>", "subject": "<subject>", "date": "<date/time>", "body": "<full plain text body>"}.'
    );
  }

  function cacheEls() {
    els.mailList = document.getElementById('mailList');
    els.inventoryList = document.getElementById('inventoryList');
    els.fetchMailBtn = document.getElementById('fetchMailBtn');
    els.refreshMailBtn = document.getElementById('refreshMailBtn');
    els.fetchInventoryBtn = document.getElementById('fetchInventoryBtn');
    els.refreshInventoryBtn = document.getElementById('refreshInventoryBtn');
    els.mailStatusPill = document.getElementById('mailStatusPill');
    els.inventoryStatusPill = document.getElementById('inventoryStatusPill');

    els.mailDetailOverlay = document.getElementById('mailDetailOverlay');
    els.mailDetailSubject = document.getElementById('mailDetailSubject');
    els.mailDetailMeta = document.getElementById('mailDetailMeta');
    els.mailDetailBody = document.getElementById('mailDetailBody');
    els.closeMailDetailBtn = document.getElementById('closeMailDetailBtn');
    els.replyMailBtn = document.getElementById('replyMailBtn');
  }

  function escapeHtml(str) {
    return String(str == null ? '' : str).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }

  function renderEmptyState(container, text) {
    container.innerHTML = `<div class="empty-state">${escapeHtml(text)}</div>`;
  }

  function toMcpConfig(m) {
    return { mcpId: m.mcpId, name: m.name, url: m.url, accessToken: m.accessToken, tokenType: 'Bearer' };
  }

  function requireModel() {
    const modelConfig = window.Settings.getModelConfig();
    if (!modelConfig.apiKey || !modelConfig.modelName) return null;
    return modelConfig;
  }

  async function loadMailPanel() {
    const mcps = window.Settings.getMcpsByRole('mail');
    els.mailStatusPill.dataset.state = mcps.length ? 'on' : 'off';
    if (!mcps.length) {
      renderEmptyState(els.mailList, 'Connect Zoho Mail in settings to see your inbox here.');
      return;
    }
    const modelConfig = requireModel();
    if (!modelConfig) {
      renderEmptyState(els.mailList, 'Configure a model provider and API key in settings to load your inbox.');
      return;
    }

    renderEmptyState(els.mailList, 'Loading...');
    try {
      const refreshedMcps = await window.Settings.getMcpConfigsForChat();
      const mailMcps = refreshedMcps.filter(m => mcps.some(mc => mc.mcpId === m.mcpId));
      const emails = await window.Api.dashboardQuery(MAIL_LIST_INSTRUCTION, modelConfig, mailMcps);
      if (!Array.isArray(emails) || !emails.length) {
        renderEmptyState(els.mailList, 'No messages found.');
        return;
      }
      latestEmails = emails.slice(0, 10);
      els.mailList.innerHTML = '';
      latestEmails.forEach((email, idx) => {
        const item = document.createElement('div');
        item.className = 'mail-item';
        item.dataset.emailIdx = String(idx);
        item.innerHTML =
          `<span class="mail-time">${escapeHtml(email.date)}</span>` +
          `<div class="mail-from">${escapeHtml(email.from || 'Unknown sender')}</div>` +
          `<div class="mail-subject">${escapeHtml(email.subject || '(no subject)')}</div>`;
        els.mailList.appendChild(item);
      });
    } catch (err) {
      renderEmptyState(els.mailList, `Could not load mail: ${err.message}`);
    }
  }

  async function openMailDetail(email) {
    els.mailDetailSubject.textContent = email.subject || '(no subject)';
    els.mailDetailMeta.textContent = `From ${email.from || 'Unknown sender'} · ${email.date || ''}`;
    els.mailDetailBody.textContent = 'Loading...';
    els.mailDetailOverlay.classList.add('open');
    els.replyMailBtn.dataset.from = email.from || '';
    els.replyMailBtn.dataset.subject = email.subject || '';

    if (!email.id) {
      els.mailDetailBody.textContent = email.snippet || '';
      return;
    }
    const mcps = window.Settings.getMcpsByRole('mail');
    const modelConfig = requireModel();
    if (!modelConfig) {
      els.mailDetailBody.textContent = email.snippet || '';
      return;
    }
    try {
      const detail = await window.Api.dashboardQuery(mailDetailInstruction(email.id), modelConfig, mcps.map(toMcpConfig));
      els.mailDetailBody.textContent = (detail && detail.body) || email.snippet || '';
    } catch (err) {
      els.mailDetailBody.textContent = `Could not load the full message: ${err.message}`;
    }
  }

  async function loadInventoryPanel() {
    const mcps = window.Settings.getMcpsByRole('inventory');
    els.inventoryStatusPill.dataset.state = mcps.length ? 'on' : 'off';
    if (!mcps.length) {
      renderEmptyState(els.inventoryList, 'Add an Inventory-role MCP server (e.g. Airtable) in settings to see stock here.');
      return;
    }
    const modelConfig = requireModel();
    if (!modelConfig) {
      renderEmptyState(els.inventoryList, 'Configure a model provider and API key in settings to load inventory.');
      return;
    }

    renderEmptyState(els.inventoryList, 'Loading...');
    try {
      const refreshedMcps = await window.Settings.getMcpConfigsForChat();
      const invMcps = refreshedMcps.filter(m => mcps.some(mc => mc.mcpId === m.mcpId));
      const records = await window.Api.dashboardQuery(INVENTORY_LIST_INSTRUCTION, modelConfig, invMcps);
      if (!Array.isArray(records) || !records.length) {
        renderEmptyState(els.inventoryList, 'No inventory records found.');
        return;
      }
      els.inventoryList.innerHTML = '';
      records.forEach((r) => {
        const qtyNum = Number(r.quantity);
        const pct = Number.isFinite(qtyNum) ? Math.max(0, Math.min(100, qtyNum)) : null;
        const card = document.createElement('div');
        card.className = 'inv-card';
        card.innerHTML = `
          <div class="inv-name">${escapeHtml(r.name || 'Item')}</div>
          <div class="inv-meta"><span>${escapeHtml(r.location || '')}</span><span>${r.quantity != null ? `Qty: ${escapeHtml(r.quantity)}` : ''}</span></div>
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
    await window.Settings.init();
    window.Chat.init();

    // Loading either panel spends model API credits (it runs the full tool
    // loop), so nothing fetches automatically - only an explicit click does,
    // whether that's on connect, on later reconnects, or on page load.
    els.fetchMailBtn.addEventListener('click', loadMailPanel);
    els.refreshMailBtn.addEventListener('click', loadMailPanel);
    els.fetchInventoryBtn.addEventListener('click', loadInventoryPanel);
    els.refreshInventoryBtn.addEventListener('click', loadInventoryPanel);

    els.mailList.addEventListener('click', (e) => {
      const item = e.target.closest('[data-email-idx]');
      if (!item) return;
      const email = latestEmails[Number(item.dataset.emailIdx)];
      if (email) openMailDetail(email);
    });
    els.closeMailDetailBtn.addEventListener('click', () => els.mailDetailOverlay.classList.remove('open'));
    els.mailDetailOverlay.addEventListener('click', (e) => {
      if (e.target === els.mailDetailOverlay) els.mailDetailOverlay.classList.remove('open');
    });
    els.replyMailBtn.addEventListener('click', () => {
      const from = els.replyMailBtn.dataset.from || '';
      const subject = els.replyMailBtn.dataset.subject || '';
      els.mailDetailOverlay.classList.remove('open');
      window.Chat.prefill(`Reply to the email from ${from} about "${subject}" saying: `);
    });
  }

  document.addEventListener('DOMContentLoaded', init);
})();
