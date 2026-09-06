'use strict';

const { getAllTools, executeTool } = require('./mcp-manager');

const MAX_TOOL_ITERATIONS = 10;

const ANTHROPIC_API_URL = process.env.OSAI_ANTHROPIC_URL || 'https://api.anthropic.com/v1/messages';
const ANTHROPIC_VERSION = '2023-06-01';
const GEMINI_API_BASE = process.env.OSAI_GEMINI_BASE || 'https://generativelanguage.googleapis.com/v1beta/models';
const OPENROUTER_API_URL = process.env.OSAI_OPENROUTER_URL || 'https://openrouter.ai/api/v1/chat/completions';
const NVIDIA_API_URL = process.env.OSAI_NVIDIA_URL || 'https://integrate.api.nvidia.com/v1/chat/completions';

/**
 * Without this, the model has no reason to look up an id it could fetch
 * itself, and no reason to call dependent tools in the order they actually
 * depend on each other (account before folders, folders before messages,
 * base before table before records) - it just asks the user, or guesses.
 */
const SYSTEM_PROMPT = `
You manage a user's connected services through MCP tools - Zoho Mail and any
other MCP server they have added (for example, an Airtable inventory base).
Use the connected tools for anything about their actual data: mail messages,
folders, contacts, inventory records, tables, or anything else a tool exposes.
Never invent an id, record, message, table name, or base id - if a tool did
not return it, you do not know it.

Most tools need an identifier first - an account id, a folder id, a base id,
a table name - before they can do what was actually asked. Resolve every one
of these yourself with whichever tool lists or fetches them; never ask the
user for something a tool can look up. Call tools in the order they depend on
each other (get the account before listing folders, list folders before
listing messages in one, list bases before listing tables, list tables before
querying records) before ever answering or asking the user something a tool
could have supplied. Reuse an id you already fetched earlier in this same
request rather than re-fetching it.

When a lookup returns exactly one result - one account, one base, one
matching folder - use it directly without asking, since there is nothing to
disambiguate. Only ask the user to choose when a lookup genuinely returns
more than one plausible candidate and the request did not already say which.

If a tool call fails, read the error before reacting: a generic or
validation-shaped error (missing field, invalid input, not found) usually
means a required identifier or argument is missing - get it from another
tool or the error itself and retry once with it filled in, rather than
giving up or asking the user. Only surface the failure if the retry also fails.

Sending, deleting, and any other irreversible action needs the user's
explicit confirmation first - state exactly what you are about to do and wait
for a clear yes before calling that tool. Reading, listing, and searching
need no confirmation.

If asked to respond in a specific structured format (for example, JSON
matching an exact shape), follow that format exactly: no prose, headings, or
markdown code fences around it, and nothing before or after it.
`.trim();

/**
 * OpenAI-style tool calls carry their arguments as a JSON-encoded string.
 * Cheaper/smaller models (common on OpenRouter and NVIDIA NIM) sometimes emit
 * that string slightly malformed or with extra trailing content glued on -
 * a raw JSON.parse on it throws and, uncaught, used to crash the entire
 * request (chat and both dashboard panels alike, since they share this same
 * code path). This recovers the leading balanced {...} object instead of
 * failing outright; worst case it returns {}, which surfaces as a normal
 * tool-call error the model can see and retry, not a hard crash.
 */
function parseToolArguments(raw) {
  if (raw == null || raw === '') return {};
  if (typeof raw === 'object') return raw;
  try {
    return JSON.parse(raw);
  } catch (_) {
    const start = raw.indexOf('{');
    if (start === -1) return {};
    let depth = 0;
    for (let i = start; i < raw.length; i++) {
      if (raw[i] === '{') depth++;
      else if (raw[i] === '}') {
        depth--;
        if (depth === 0) {
          try {
            return JSON.parse(raw.slice(start, i + 1));
          } catch (_) {
            return {};
          }
        }
      }
    }
    return {};
  }
}

function sanitizeToolName(mcpName, originalName) {
  const safe = `${mcpName}__${originalName}`.replace(/[^a-zA-Z0-9_-]/g, '_');
  return safe.slice(0, 128);
}

/**
 * Pulls a JSON value out of a structured-query response even when the model
 * ignored the "output nothing else" instruction and added a lead-in like
 * "Now I'll check..." or "Perfect! Here's the list:" before the JSON -
 * weaker/cheaper models do this fairly often. Tries, in order: the raw text
 * as-is, a ```json fenced block, then the widest [...]/{...} substring found
 * anywhere in the text.
 */
function parseJsonLoose(text) {
  if (!text) throw new Error('The model returned no data.');
  const cleaned = text.trim();

  const candidates = [cleaned];
  const fenced = /```(?:json)?\s*([\s\S]*?)```/i.exec(cleaned);
  if (fenced) candidates.push(fenced[1].trim());

  const firstBracket = cleaned.search(/[[{]/);
  if (firstBracket !== -1) {
    const closeChar = cleaned[firstBracket] === '[' ? ']' : '}';
    const lastClose = cleaned.lastIndexOf(closeChar);
    if (lastClose > firstBracket) candidates.push(cleaned.slice(firstBracket, lastClose + 1));
  }

  for (const candidate of candidates) {
    try {
      return JSON.parse(candidate);
    } catch (_) {
      // try the next candidate
    }
  }
  throw new Error(`Could not parse a structured response. The model said: "${cleaned.slice(0, 160)}"`);
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
    const messages = [...conversationHistory, { role: 'user', content: userMessage }];
    const { text, messages: finalMessages } = await this._runToolLoop(modelConfig, messages, mcpConfigs);
    return { text, conversationHistory: finalMessages };
  }

  /**
   * One-shot, historyless query used by the dashboard panels: same tool loop
   * as chat, but the instruction asks for an exact JSON shape and the result
   * is parsed rather than shown as a conversational reply.
   */
  async processStructuredQuery(instruction, modelConfig, mcpConfigs = []) {
    if (!modelConfig || !modelConfig.provider || !modelConfig.apiKey) {
      throw new Error('Model provider and API key are required');
    }
    const strictInstruction =
      `${instruction}\n\nCRITICAL: your entire final response must be nothing but that JSON - ` +
      'no lead-in sentence, no acknowledgement, no explanation, no markdown fences. ' +
      'It must start with [ or { and end with the matching ] or } and contain nothing else.';
    const { text } = await this._runToolLoop(modelConfig, [{ role: 'user', content: strictInstruction }], mcpConfigs);
    return parseJsonLoose(text);
  }

  async _runToolLoop(modelConfig, messages, mcpConfigs) {
    const { tools: rawTools, sessions } = await getAllTools(mcpConfigs);
    console.log(`[DEBUG] _runToolLoop: received ${mcpConfigs.length} MCPs, got ${rawTools.length} tools`);
    const nameMap = new Map(); // sanitized name -> { mcpConfig, originalName }
    const mcpById = new Map(mcpConfigs.map((m) => [m.mcpId, m]));
    for (const tool of rawTools) {
      const sanitized = sanitizeToolName(tool.__mcpName, tool.name);
      nameMap.set(sanitized, { mcpConfig: mcpById.get(tool.__mcpId), originalName: tool.name });
    }

    let iterations = 0;
    while (iterations < MAX_TOOL_ITERATIONS) {
      iterations++;
      console.log(`[DEBUG] _runToolLoop iteration ${iterations}: calling provider with ${rawTools.length} tools`);
      const result = await this._callProvider(modelConfig, messages, rawTools);

      if (result.toolCalls && result.toolCalls.length > 0) {
        console.log(`[DEBUG] _runToolLoop: model made ${result.toolCalls.length} tool calls`);
        messages.push(result.assistantMessage);
        const toolResults = [];
        for (const call of result.toolCalls) {
          console.log(`[DEBUG] _runToolLoop: executing tool "${call.name}"`);
          const mapping = nameMap.get(call.name);
          if (!mapping) {
            console.log(`[DEBUG] _runToolLoop: tool "${call.name}" not found in nameMap`);
            toolResults.push({ id: call.id, name: call.name, output: { error: `Unknown tool: ${call.name}` } });
            continue;
          }
          try {
            const sessionId = sessions.get(mapping.mcpConfig.mcpId);
            const toolOutput = await executeTool(mapping.mcpConfig, mapping.originalName, call.input, sessionId);
            console.log(`[DEBUG] _runToolLoop: tool "${call.name}" executed successfully`);
            toolResults.push({ id: call.id, name: call.name, output: toolOutput });
          } catch (err) {
            console.error(`[DEBUG] _runToolLoop: tool "${call.name}" failed:`, err.message);
            toolResults.push({ id: call.id, name: call.name, output: { error: err.message } });
          }
        }
        messages.push(this._formatToolResultsMessage(modelConfig.provider, toolResults));
        continue;
      }

      console.log(`[DEBUG] _runToolLoop: final response: ${result.text.slice(0, 100)}`);
      return { text: result.text, messages: [...messages, { role: 'assistant', content: result.text }] };
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
      return this._callOpenAiCompatible(modelConfig, messages, rawTools, OPENROUTER_API_URL, 'anthropic/claude-sonnet-4.5');
    }
    if (modelConfig.provider === 'nvidia') {
      return this._callOpenAiCompatible(modelConfig, messages, rawTools, NVIDIA_API_URL, 'meta/llama-3.1-70b-instruct');
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
      system: SYSTEM_PROMPT,
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
      body: JSON.stringify({
        contents,
        systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
        ...(tools ? { tools } : {})
      })
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

  /**
   * OpenAI-compatible chat completions - OpenRouter and NVIDIA NIM
   * (integrate.api.nvidia.com) both speak this exact schema, differing only
   * in base URL and which models they host. Not every model NIM hosts
   * supports tool calling; ones that don't will just answer in prose and the
   * dashboard panels' JSON parsing will report that plainly.
   */
  async _callOpenAiCompatible(modelConfig, messages, rawTools, apiUrl, defaultModel) {
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

    const response = await fetch(apiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${modelConfig.apiKey}`
      },
      body: JSON.stringify({
        model: modelConfig.modelName || defaultModel,
        messages: [{ role: 'system', content: SYSTEM_PROMPT }, ...orMessages],
        ...(tools.length ? { tools } : {})
      })
    });

    const data = await response.json();
    if (data.error) throw new Error(data.error.message || 'API error');

    const choice = data.choices && data.choices[0];
    const message = (choice && choice.message) || {};
    const toolCalls = (message.tool_calls || []).map((tc) => ({
      id: tc.id,
      name: tc.function.name,
      input: parseToolArguments(tc.function.arguments)
    }));

    return {
      text: message.content || '',
      toolCalls,
      assistantMessage: { role: 'assistant', content: message.content || '', tool_calls: message.tool_calls }
    };
  }
}

module.exports = { ChatHandler };
