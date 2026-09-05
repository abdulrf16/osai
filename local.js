'use strict';

/**
 * Local development server only - not part of either Catalyst deployable.
 *
 * In production, Catalyst hosts the client (client/) and the function
 * (function/) as two separate components; when they're in the same
 * project, Catalyst itself proxies /server/<function-name> to the function,
 * same-origin. This script reproduces that locally in one process: it
 * serves client/ as static files and mounts the function's Express app at
 * the same /server/inventory_bot_api path the client's api.js expects by default.
 */

const path = require('path');
const express = require('express');
const functionApp = require('./function/index.js');

const PORT = process.env.PORT || 8090;

const app = express();
app.use(express.static(path.join(__dirname, 'client')));
app.use('/server/inventory_bot_api', functionApp);

app.listen(PORT, () => {
  console.log(`Inventory Bot MCP Control Center (local dev) running on port ${PORT}`);
});
