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
 * @typedef {{ mcpId: string, name: string, url: string, accessToken?: string, tokenType?: string }} McpConfig
 */

let requestId = 0;

async function parseSse(response) {
	const text = await response.text();
	for (const line of text.split('\n')) {
		if (line.startsWith('data:')) {
			const jsonStr = line.slice(5).trim();
			if (jsonStr) {
				try {
					return JSON.parse(jsonStr);
				} catch {
					// keep scanning
				}
			}
		}
	}
	throw new Error('No parsable data in SSE response from MCP server');
}

async function rpcCall(mcp, method, params) {
	const id = ++requestId;
	const headers = {
		'Content-Type': 'application/json',
		Accept: 'application/json, text/event-stream'
	};
	if (mcp.accessToken) {
		headers.Authorization = `${mcp.tokenType || 'Bearer'} ${mcp.accessToken}`;
	}

	const response = await fetch(mcp.url, {
		method: 'POST',
		headers,
		body: JSON.stringify({ jsonrpc: '2.0', id, method, params: params || {} })
	});

	if (!response.ok) {
		const text = await response.text().catch(() => '');
		throw new Error(`MCP server responded ${response.status}: ${text.slice(0, 300)}`);
	}

	const contentType = response.headers.get('content-type') || '';
	const payload = contentType.includes('text/event-stream') ? await parseSse(response) : await response.json();

	if (payload.error) {
		throw new Error(payload.error.message || 'MCP server returned an error');
	}
	return payload.result;
}

/** @param {McpConfig} mcp */
async function listTools(mcp) {
	const result = await rpcCall(mcp, 'tools/list', {});
	return (result && result.tools) || [];
}

/**
 * @param {McpConfig[]} mcps
 * @returns {Promise<Array<object & {__mcpId: string, __mcpName: string}>>}
 */
async function getAllTools(mcps) {
	const out = [];
	for (const mcp of mcps) {
		try {
			const tools = await listTools(mcp);
			for (const tool of tools) {
				out.push({ ...tool, __mcpId: mcp.mcpId, __mcpName: mcp.name });
			}
		} catch (err) {
			console.error(`Failed to list tools for MCP "${mcp.name}":`, err.message);
		}
	}
	return out;
}

/** @param {McpConfig} mcp */
async function executeTool(mcp, toolName, toolInput) {
	return rpcCall(mcp, 'tools/call', { name: toolName, arguments: toolInput || {} });
}

module.exports = { listTools, getAllTools, executeTool };
