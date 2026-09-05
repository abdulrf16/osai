'use strict';

/**
 * Inventory Bot MCP Control Center - a Catalyst Advanced I/O function.
 *
 * An Advanced I/O function is handed the raw Node (req, res) pair, and an
 * Express app *is* a (req, res) handler, so `module.exports = app` is all
 * Catalyst needs - no app.listen() here; see local.js at the repo root for
 * local dev, which mounts this app under an Express server that also serves
 * the client build (Catalyst hosts those as two separate components: this
 * function, and a static web client - see client/client-package.json).
 *
 * This service is deliberately stateless: it stores no API keys, no OAuth
 * tokens, no MCP registry and no conversation history. The browser holds all
 * of that in localStorage and resends what's needed on every call. That is
 * what lets an end user bring nothing but their own model API key and
 * whichever MCP server URLs they choose to add.
 */

const express = require('express');
const cors = require('cors');
const { listTools, executeTool } = require('./lib/mcp-manager');
const { prepareAuth, exchangeToken, refreshAccessToken } = require('./lib/oauth');
const { ChatHandler } = require('./lib/chat-handler');

const app = express();
const chatHandler = new ChatHandler();

/**
 * The web client is served from the Catalyst app URL, which is a different
 * origin from the function URL unless both are in the same Catalyst project
 * (in which case Catalyst itself proxies /server/<name> same-origin). Set
 * ALLOWED_ORIGINS in the function's env_variables to a comma-separated
 * allowlist once you know your app URL; the default of "*" keeps first
 * deploys working. The function holds no secrets of its own - credentials
 * arrive per request - so a permissive default leaks nothing.
 */
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || '*')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

app.use(cors({ origin: ALLOWED_ORIGINS.includes('*') ? true : ALLOWED_ORIGINS }));
app.use(express.json({ limit: '2mb' }));

function fail(res, status, error) {
  res.status(status).json({ ok: false, error });
}

app.get('/health', (req, res) => {
  res.json({ ok: true });
});

// --- OAuth: generic MCP OAuth 2.1 + Dynamic Client Registration ---------

app.post('/api/oauth/discover', async (req, res) => {
  try {
    const { mcpUrl, redirectUri, clientId, clientSecret } = req.body;
    if (!mcpUrl || !redirectUri) throw new Error('mcpUrl and redirectUri are required');
    const manualClient = clientId ? { clientId, clientSecret } : undefined;
    const result = await prepareAuth(mcpUrl, redirectUri, manualClient);
    res.json({ ok: true, ...result });
  } catch (err) {
    fail(res, 400, err.message);
  }
});

app.post('/api/oauth/token', async (req, res) => {
  try {
    const { tokenEndpoint, clientId, clientSecret, code, codeVerifier, redirectUri } = req.body;
    const result = await exchangeToken({ tokenEndpoint, clientId, clientSecret, code, codeVerifier, redirectUri });
    res.json({ ok: true, ...result });
  } catch (err) {
    fail(res, 400, err.message);
  }
});

app.post('/api/oauth/refresh', async (req, res) => {
  try {
    const { tokenEndpoint, clientId, clientSecret, refreshToken } = req.body;
    const result = await refreshAccessToken({ tokenEndpoint, clientId, clientSecret, refreshToken });
    res.json({ ok: true, ...result });
  } catch (err) {
    fail(res, 400, err.message);
  }
});

// --- MCP: stateless proxy to whatever server the browser tells us about ---

app.post('/api/mcp/tools', async (req, res) => {
  try {
    const { mcp } = req.body;
    if (!mcp || !mcp.url) throw new Error('mcp.url is required');
    const tools = await listTools(mcp);
    res.json({ ok: true, tools });
  } catch (err) {
    fail(res, 400, err.message);
  }
});

app.post('/api/mcp/execute', async (req, res) => {
  try {
    const { mcp, toolName, toolInput } = req.body;
    if (!mcp || !mcp.url) throw new Error('mcp.url is required');
    const result = await executeTool(mcp, toolName, toolInput);
    res.json({ ok: true, result });
  } catch (err) {
    fail(res, 500, err.message);
  }
});

// --- Chat ------------------------------------------------------------------

app.post('/api/chat', async (req, res) => {
  try {
    const { message, modelConfig, conversationHistory, mcpConfigs } = req.body;
    const response = await chatHandler.processMessage(message, modelConfig, conversationHistory, mcpConfigs);
    res.json({ ok: true, response });
  } catch (err) {
    console.error('Chat error:', err);
    fail(res, 500, err.message);
  }
});

app.use((req, res) => fail(res, 404, `No such endpoint: ${req.method} ${req.path}`));

module.exports = app;
