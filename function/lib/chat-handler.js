'use strict';

const { getAllTools, executeTool } = require('./mcp-manager');

const MAX_TOOL_ITERATIONS = 6;

const ANTHROPIC_API_URL = process.env.OSAI_ANTHROPIC_URL || 'https://api.anthropic.com/v1/messages';
const ANTHROPIC_VERSION = '2023-06-01';
const GEMINI_API_BASE = process.env.OSAI_GEMINI_BASE || 'https://generativelanguage.googleapis.com/v1beta/models';
const OPENROUTER_API_URL = process.env.OSAI_OPENROUTER_URL || 'https://openrouter.ai/api/v1/chat/completions';

function sanitizeToolName(mcpName, originalName) {
  const safe = `${mcpName}__${originalName}`.replace(/[^a-zA-Z0-9_-]/g, '_');
  return safe.slice(0, 128);
}

/**
 * Routes a chat turn to the selected model provider, exposing the tools of
 * the caller-supplied MCP servers, and loops through any tool calls the
 * model makes until it produces a final text answer. Stateless: every MCP's
 * full connection info (url, token) is passed in per call rather than
 * looked up from a server-side registry, since this runs inside a
 * serverless function with no memory between invocations.
 */
class ChatHandler {
  /**
   * @param {string} userMessage
   * @param {{provider: string, apiKey: string, modelName?: string}} modelConfig
   * @param {Array} conversationHistory
   * @param {Array<{mcpId: string, name: string, url: string, accessToken?: string, tokenType?: string}>} mcpConfigs
   */
  async processMessage(userMessage, modelConfig, conversationHistory = [], mcpConfigs = []) {
    if (!modelConfig || !modelConfig.provider || !modelConfig.apiKey) {
      throw new Error('Model provider and API key are required');
    }

    const rawTools = await getAllTools(mcpConfigs);
    const nameMap = new Map(); // sanitized name -> { mcpConfig, originalName }
    const mcpById = new Map(mcpConfigs.map((m) => [m.mcpId, m]));
    for (const tool of rawTools) {
      const sanitized = sanitizeToolName(tool.__mcpName, tool.name);
      nameMap.set(sanitized, { mcpConfig: mcpById.get(tool.__mcpId), originalName: tool.name });
    }

    const messages = [...conversationHistory, { role: 'user', content: userMessage }];

    let iterations = 0;
    while (iterations < MAX_TOOL_ITERATIONS) {
      iterations++;
      const result = await this._callProvider(modelConfig, messages, rawTools);

      if (result.toolCalls && result.toolCalls.length > 0) {
        messages.push(result.assistantMessage);
        const toolResults = [];
        for (const call of result.toolCalls) {
          const mapping = nameMap.get(call.name);
          if (!mapping) {
            toolResults.push({ id: call.id, name: call.name, output: { error: `Unknown tool: ${call.name}` } });
            continue;
          }
          try {
            const toolOutput = await executeTool(mapping.mcpConfig, mapping.originalName, call.input);
            toolResults.push({ id: call.id, name: call.name, output: toolOutput });
          } catch (err) {
            toolResults.push({ id: call.id, name: call.name, output: { error: err.message } });
          }
        }
        messages.push(this._formatToolResultsMessage(modelConfig.provider, toolResults));
        continue;
      }

      return { text: result.text, conversationHistory: [...messages, { role: 'assistant', content: result.text }] };
    }

    throw new Error('Tool call loop exceeded maximum iterations without a final answer');
  }

  _formatToolResultsMessage(provider, toolResults) {
    if (provider === 'anthropic') {
      return {
        role: 'user',
        content: toolResults.map((r) => ({
          type: 'tool_result',
          tool_use_id: r.id,
          content: JSON.stringify(r.output)
        }))
      };
    }
    if (provider === 'gemini') {
      return {
        role: 'function',
        content: toolResults.map((r) => ({ name: r.name, response: r.output }))
      };
    }
    // openrouter (OpenAI-style)
    return {
      role: 'tool',
      content: toolResults.map((r) => ({ tool_call_id: r.id, name: r.name, content: JSON.stringify(r.output) }))
    };
  }

  async _callProvider(modelConfig, messages, rawTools) {
    if (modelConfig.provider === 'anthropic') {
      return this._callAnthropic(modelConfig, messages, rawTools);
    }
    if (modelConfig.provider === 'gemini') {
      return this._callGemini(modelConfig, messages, rawTools);
    }
    if (modelConfig.provider === 'openrouter') {
      return this._callOpenRouter(modelConfig, messages, rawTools);
    }
    throw new Error(`Unsupported provider: ${modelConfig.provider}`);
  }

  async _callAnthropic(modelConfig, messages, rawTools) {
    const tools = rawTools.map((t) => ({
      name: sanitizeToolName(t.__mcpName, t.name),
      description: t.description || '',
      input_schema: t.inputSchema || { type: 'object', properties: {} }
    }));

    const anthropicMessages = messages.map((m) => {
      if (typeof m.content === 'string') return { role: m.role, content: m.content };
      return m;
    });

    const body = {
      model: modelConfig.modelName || 'claude-sonnet-4-5-20250929',
      max_tokens: 2048,
      messages: anthropicMessages,
      ...(tools.length ? { tools } : {})
    };

    const response = await fetch(ANTHROPIC_API_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': modelConfig.apiKey,
        'anthropic-version': ANTHROPIC_VERSION
      },
      body: JSON.stringify(body)
    });

    const data = await response.json();
    if (data.error) throw new Error(data.error.message || 'Anthropic API error');

    const toolCalls = [];
    let text = '';
    for (const block of data.content || []) {
      if (block.type === 'text') text += block.text;
      if (block.type === 'tool_use') toolCalls.push({ id: block.id, name: block.name, input: block.input });
    }

    return {
      text,
      toolCalls,
      assistantMessage: { role: 'assistant', content: data.content }
    };
  }

  async _callGemini(modelConfig, messages, rawTools) {
    const modelName = modelConfig.modelName || 'gemini-2.0-flash';
    const url = `${GEMINI_API_BASE}/${modelName}:generateContent?key=${modelConfig.apiKey}`;

    const tools = rawTools.length
      ? [
          {
            functionDeclarations: rawTools.map((t) => ({
              name: sanitizeToolName(t.__mcpName, t.name),
              description: t.description || '',
              parameters: t.inputSchema || { type: 'object', properties: {} }
            }))
          }
        ]
      : undefined;

    const contents = messages.map((m) => {
      if (typeof m.content === 'string') {
        return { role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: m.content }] };
      }
      return m; // already-formatted function role message
    });

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents, ...(tools ? { tools } : {}) })
    });

    const data = await response.json();
    if (data.error) throw new Error(data.error.message || 'Gemini API error');

    const candidate = data.candidates && data.candidates[0];
    const parts = (candidate && candidate.content && candidate.content.parts) || [];
    let text = '';
    const toolCalls = [];
    for (const part of parts) {
      if (part.text) text += part.text;
      if (part.functionCall) {
        toolCalls.push({ id: part.functionCall.name, name: part.functionCall.name, input: part.functionCall.args || {} });
      }
    }

    return {
      text,
      toolCalls,
      assistantMessage: { role: 'assistant', content: text || JSON.stringify(parts) }
    };
  }

  async _callOpenRouter(modelConfig, messages, rawTools) {
    const tools = rawTools.map((t) => ({
      type: 'function',
      function: {
        name: sanitizeToolName(t.__mcpName, t.name),
        description: t.description || '',
        parameters: t.inputSchema || { type: 'object', properties: {} }
      }
    }));

    const orMessages = messages.map((m) => {
      if (typeof m.content === 'string') return { role: m.role, content: m.content };
      return m;
    });

    const response = await fetch(OPENROUTER_API_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${modelConfig.apiKey}`
      },
      body: JSON.stringify({
        model: modelConfig.modelName || 'anthropic/claude-sonnet-4.5',
        messages: orMessages,
        ...(tools.length ? { tools } : {})
      })
    });

    const data = await response.json();
    if (data.error) throw new Error(data.error.message || 'OpenRouter API error');

    const choice = data.choices && data.choices[0];
    const message = (choice && choice.message) || {};
    const toolCalls = (message.tool_calls || []).map((tc) => ({
      id: tc.id,
      name: tc.function.name,
      input: JSON.parse(tc.function.arguments || '{}')
    }));

    return {
      text: message.content || '',
      toolCalls,
      assistantMessage: { role: 'assistant', content: message.content || '', tool_calls: message.tool_calls }
    };
  }
}

module.exports = { ChatHandler };
