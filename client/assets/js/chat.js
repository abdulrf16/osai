(function () {
  'use strict';

  let conversationHistory = [];
  let sending = false;

  const els = {};

  function cacheEls() {
    els.form = document.getElementById('chatForm');
    els.input = document.getElementById('chatInput');
    els.sendBtn = document.getElementById('chatSendBtn');
    els.messages = document.getElementById('chatMessages');
  }

  function appendBubble(role, text) {
    const wrap = document.createElement('div');
    wrap.className = `chat-msg chat-msg-${role}`;
    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = text;
    wrap.appendChild(bubble);
    els.messages.appendChild(wrap);
    els.messages.scrollTop = els.messages.scrollHeight;
    return wrap;
  }

  function appendThinking() {
    const wrap = document.createElement('div');
    wrap.className = 'chat-msg chat-msg-assistant chat-msg-thinking';
    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = 'Thinking...';
    wrap.appendChild(bubble);
    els.messages.appendChild(wrap);
    els.messages.scrollTop = els.messages.scrollHeight;
    return wrap;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (sending) return;

    const text = els.input.value.trim();
    if (!text) return;

    const modelConfig = window.Settings.getModelConfig();
    const missingCustomUrl = modelConfig.provider === 'custom' && !modelConfig.apiUrl;
    const missingApiKey = modelConfig.provider !== 'custom' && !modelConfig.apiKey;
    if (!modelConfig.modelName || missingCustomUrl || missingApiKey) {
      appendBubble('assistant', 'Please configure a model provider, model name, and API key (or custom API URL) in settings first.');
      return;
    }

    sending = true;
    els.sendBtn.disabled = true;
    els.input.value = '';
    appendBubble('user', text);
    const thinkingEl = appendThinking();

    try {
      const mcpConfigs = window.Settings.getMcpConfigsForChat();
      const response = await window.Api.chat(text, modelConfig, conversationHistory, mcpConfigs);
      conversationHistory = response.conversationHistory;
      thinkingEl.remove();
      appendBubble('assistant', response.text || '(no response)');
    } catch (err) {
      thinkingEl.remove();
      appendBubble('assistant', `Error: ${err.message}`);
    } finally {
      sending = false;
      els.sendBtn.disabled = false;
      els.input.focus();
    }
  }

  function resetConversation() {
    conversationHistory = [];
  }

  /** Drops drafted text into the chat box and focuses it - used by "Reply". */
  function prefill(text) {
    els.input.value = text;
    els.input.focus();
  }

  function init() {
    cacheEls();
    els.form.addEventListener('submit', handleSubmit);
  }

  window.Chat = { init, resetConversation, prefill };
})();
