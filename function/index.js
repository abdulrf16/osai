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
const oauth = require('./lib/oauth');
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
//
// Discovery, PKCE, dynamic client registration and authorize-URL construction
// all happen here rather than in the browser: authorization servers (Zoho's
// included) validate parameters like the RFC 8707 `resource` indicator
// strictly, and building that URL by hand in JS drifted from what the
// server actually expected. Keeping every step server-side means Zoho Mail,
// Airtable, and any other MCP server all go through one verified code path.

app.post('/api/oauth/start', async (req, res) => {
  try {
    const { mcpUrl, redirectUri, clientId, clientSecret, clientName } = req.body;
    if (!mcpUrl || !redirectUri) throw new Error('mcpUrl and redirectUri are required');

    // Best-effort unauthenticated probe: a 401 here often carries a
    // WWW-Authenticate header pointing straight at the resource metadata,
    // which is the most reliable discovery hint an MCP server can give.
    let wwwAuthenticate = null;
    try {
      const probeRes = await oauth.fetchWithTimeout(
        mcpUrl,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json, text/event-stream',
            'MCP-Protocol-Version': '2025-06-18'
          },
          body: JSON.stringify({
            jsonrpc: '2.0',
            id: 0,
            method: 'initialize',
            params: { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'inventory-bot', version: '1.0.0' } }
          })
        },
        8000
      );
      wwwAuthenticate = probeRes.headers.get('www-authenticate');
    } catch (_) {
      // Non-fatal - discovery still tries the standard well-known URLs.
    }

    const discovered = await oauth.discover(mcpUrl, wwwAuthenticate);
    if (!discovered.ok) return fail(res, 502, discovered.error);

    const resource = oauth.canonicalResource(mcpUrl);
    const scope =
      Array.isArray(discovered.scopesSupported) && discovered.scopesSupported.length
        ? discovered.scopesSupported.join(' ')
        : undefined;

    let clientInfo;
    if (clientId) {
      clientInfo = { clientId, clientSecret: clientSecret || null };
    } else {
      const registration = await oauth.registerClient(discovered.registrationEndpoint, redirectUri, { clientName, scope });
      if (!registration.ok) {
        return fail(
          res,
          400,
          registration.unsupported
            ? 'This MCP server has no dynamic client registration endpoint. Provide a client ID manually, or paste an access token instead.'
            : registration.error
        );
      }
      clientInfo = registration;
    }

    const pkce = oauth.createPkcePair();
    const state = oauth.randomState();

    const authorizeUrl = oauth.buildAuthorizeUrl({
      authorizationEndpoint: discovered.authorizationEndpoint,
      clientId: clientInfo.clientId,
      redirectUri,
      codeChallenge: pkce.challenge,
      state,
      scope,
      resource
    });

    res.json({
      ok: true,
      authorizeUrl,
      pending: {
        state,
        codeVerifier: pkce.verifier,
        resource,
        redirectUri,
        tokenEndpoint: discovered.tokenEndpoint,
        clientId: clientInfo.clientId,
        clientSecret: clientInfo.clientSecret || null
      }
    });
  } catch (err) {
    fail(res, 400, err.message);
  }
});

app.post('/api/oauth/callback', async (req, res) => {
  try {
    const { pending, code } = req.body;
    if (!pending || !code) throw new Error('pending and code are required');

    const result = await oauth.exchangeCode({
      tokenEndpoint: pending.tokenEndpoint,
      clientId: pending.clientId,
      clientSecret: pending.clientSecret,
      code,
      codeVerifier: pending.codeVerifier,
      redirectUri: pending.redirectUri,
      resource: pending.resource
    });
    if (!result.ok) return fail(res, 400, result.error);

    res.json({
      ok: true,
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
      expiresAt: result.expiresAt,
      tokenType: result.tokenType,
      // Echoed back so the browser can refresh later without re-running discovery.
      tokenEndpoint: pending.tokenEndpoint,
      clientId: pending.clientId,
      clientSecret: pending.clientSecret,
      resource: pending.resource
    });
  } catch (err) {
    fail(res, 400, err.message);
  }
});

app.post('/api/oauth/refresh', async (req, res) => {
  try {
    const { tokenEndpoint, clientId, clientSecret, refreshToken, resource } = req.body;
    const result = await oauth.refreshToken({ tokenEndpoint, clientId, clientSecret, refreshToken, resource });
    if (!result.ok) return fail(res, 400, result.error);
    res.json({ ok: true, accessToken: result.accessToken, refreshToken: result.refreshToken, expiresAt: result.expiresAt });
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
