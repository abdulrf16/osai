'use strict';

/**
 * Stateless helpers for talking to MCP servers over the Streamable HTTP
 * transport (JSON-RPC 2.0 POST requests). Nothing here is held in memory
 * between calls - every function takes the full connection details it needs
 * (url, accessToken, tokenType) as arguments, because this module runs
 * inside a Catalyst Advanced I/O function: a serverless handler that has no
 * guaranteed memory across invocations. The browser is the only place
 * MCP configuration persists (localStorage); it resends what it needs on
 * every request.
 *
 * The Streamable HTTP transport requires an `initialize` call (and, when the
 * server hands back an `Mcp-Session-Id`, a `notifications/initialized`
 * follow-up) before any other method - skipping straight to `tools/list`
 * against a server that enforces this handshake gets rejected, typically as
 * a 401, even with a perfectly valid access token. That is what made
 * Airtable's MCP server reject every call here: this module used to call
 * `tools/list`/`tools/call` directly with no handshake at all.
 *
 * @typedef {{ mcpId: string, name: string, url: string, accessToken?: string, tokenType?: string }} McpConfig
 */

const PROTOCOL_VERSION = '2025-06-18';
const REQUEST_TIMEOUT_MS = 15000;

let requestId = 0;

async function fetchWithTimeout(url, options, timeoutMs) {
	const controller = new AbortController();
	const timer = setTimeout(() => controller.abort(), timeoutMs);
	try {
		return await fetch(url, { ...options, signal: controller.signal });
	} catch (err) {
		if (err && err.name === 'AbortError') {
			throw new Error(`Request to the MCP server timed out after ${timeoutMs}ms`);
		}
		throw err;
	} finally {
		clearTimeout(timer);
	}
}

/**
 * Streamable HTTP servers may answer a POST with either `application/json` or an
 * SSE stream. Parse both so this works against either transport.
 */
async function readRpcResponse(res) {
	const contentType = (res.headers.get('content-type') || '').toLowerCase();
	const text = await res.text();

	if (contentType.includes('text/event-stream')) {
		for (const line of text.split(/\r?\n/)) {
			const trimmed = line.trim();
			if (!trimmed.startsWith('data:')) continue;
			const payload = trimmed.slice(5).trim();
			if (!payload || payload === '[DONE]') continue;
			try {
				return JSON.parse(payload);
			} catch (_) {
				// keep scanning
			}
		}
		return null;
	}

	if (!text) return null;
	try {
		return JSON.parse(text);
	} catch (_) {
		return null;
	}
}

function buildHeaders(mcp, sessionId) {
	const headers = {
		'Content-Type': 'application/json',
		Accept: 'application/json, text/event-stream',
		'MCP-Protocol-Version': PROTOCOL_VERSION
	};
	if (mcp.accessToken) headers.Authorization = `${mcp.tokenType || 'Bearer'} ${mcp.accessToken}`;
	if (sessionId) headers['Mcp-Session-Id'] = sessionId;
	return headers;
}

function rpc(method, params, id) {
	return JSON.stringify({ jsonrpc: '2.0', id, method, params: params || {} });
}

/**
 * Perform the `initialize` handshake and return the session id (if the
 * server issued one - some servers are sessionless and that is fine too).
 *
 * @param {McpConfig} mcp
 * @returns {Promise<string|null>}
 */
async function openSession(mcp) {
	const initRes = await fetchWithTimeout(
		mcp.url,
		{
			method: 'POST',
			headers: buildHeaders(mcp, null),
			body: rpc(
				'initialize',
				{
					protocolVersion: PROTOCOL_VERSION,
					capabilities: {},
					clientInfo: { name: 'inventory-bot-mcp-client', version: '1.0.0' }
				},
				++requestId
			)
		},
		REQUEST_TIMEOUT_MS
	);

	if (initRes.status === 401 || initRes.status === 403) {
		throw new Error('Not authorised. The access token for this MCP server is missing, invalid, or expired.');
	}
	if (!initRes.ok) {
		const body = await initRes.text().catch(() => '');
		throw new Error(`MCP initialize failed with HTTP ${initRes.status}${body ? `: ${body.slice(0, 200)}` : ''}`);
	}

	const sessionId = initRes.headers.get('mcp-session-id') || null;
	await readRpcResponse(initRes);

	if (sessionId) {
		try {
			await fetchWithTimeout(
				mcp.url,
				{
					method: 'POST',
					headers: buildHeaders(mcp, sessionId),
					body: JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' })
				},
				REQUEST_TIMEOUT_MS
			);
		} catch (_) {
			// Optional on many servers - not fatal if it's rejected or times out.
		}
	}

	return sessionId;
}

async function rpcCall(mcp, method, params, sessionId) {
	const res = await fetchWithTimeout(
		mcp.url,
		{
			method: 'POST',
			headers: buildHeaders(mcp, sessionId),
			body: rpc(method, params, ++requestId)
		},
		REQUEST_TIMEOUT_MS
	);

	if (res.status === 401 || res.status === 403) {
		throw new Error('Not authorised. The access token for this MCP server is missing, invalid, or expired.');
	}
	if (!res.ok) {
		const text = await res.text().catch(() => '');
		throw new Error(`MCP server responded ${res.status}${text ? `: ${text.slice(0, 200)}` : ''}`);
	}

	const body = await readRpcResponse(res);
	if (!body) throw new Error(`No readable response from the MCP server for ${method}.`);
	if (body.error) throw new Error(body.error.message ? String(body.error.message) : `MCP call to ${method} failed.`);
	return body.result;
}

/** @param {McpConfig} mcp */
async function listTools(mcp) {
	const sessionId = await openSession(mcp);
	const result = await rpcCall(mcp, 'tools/list', {}, sessionId);
	return (result && result.tools) || [];
}

/**
 * @param {McpConfig[]} mcps
 * @returns {Promise<{tools: Array<object & {__mcpId: string, __mcpName: string}>, sessions: Map<string, string|null>}>}
 */
async function getAllTools(mcps) {
	const tools = [];
	const sessions = new Map();
	console.log(`[DEBUG] getAllTools: starting with ${mcps.length} MCPs`);
	for (const mcp of mcps) {
		console.log(`[DEBUG] getAllTools: processing MCP "${mcp.name}" (${mcp.mcpId}) at ${mcp.url}, hasToken=${!!mcp.accessToken}`);
		try {
			const sessionId = await openSession(mcp);
			console.log(`[DEBUG] getAllTools: opened session for "${mcp.name}", sessionId=${sessionId}`);
			sessions.set(mcp.mcpId, sessionId);
			const result = await rpcCall(mcp, 'tools/list', {}, sessionId);
			const toolCount = (result && result.tools && result.tools.length) || 0;
			console.log(`[DEBUG] getAllTools: got ${toolCount} tools from "${mcp.name}"`);
			for (const tool of (result && result.tools) || []) {
				tools.push({ ...tool, __mcpId: mcp.mcpId, __mcpName: mcp.name });
			}
		} catch (err) {
			console.error(`[DEBUG] getAllTools: Failed for MCP "${mcp.name}": ${err.message}`);
		}
	}
	console.log(`[DEBUG] getAllTools: returning ${tools.length} total tools from ${sessions.size} sessions`);
	return { tools, sessions };
}

/**
 * @param {McpConfig} mcp
 * @param {string|null} [sessionId] Reuse a session opened earlier this turn
 *   (via getAllTools) instead of paying a fresh initialize handshake per call.
 */
async function executeTool(mcp, toolName, toolInput, sessionId) {
	const sid = sessionId !== undefined ? sessionId : await openSession(mcp);
	const result = await rpcCall(mcp, 'tools/call', { name: toolName, arguments: toolInput || {} }, sid);
	if (result && result.isError) {
		throw new Error(flattenContent(result.content) || `Tool ${toolName} reported an error.`);
	}
	return result;
}

/** Reduce MCP content blocks to plain text/JSON a caller can use. */
function flattenContent(content) {
	if (!content) return '';
	if (typeof content === 'string') return content;
	if (!Array.isArray(content)) return '';

	return content
		.map((block) => {
			if (!block) return '';
			if (block.type === 'text' && typeof block.text === 'string') return block.text;
			if (block.type === 'resource' && block.resource && block.resource.text) return block.resource.text;
			if (block.type === 'image') return '[image omitted]';
			return '';
		})
		.filter(Boolean)
		.join('\n')
		.trim();
}

module.exports = { listTools, getAllTools, executeTool };
