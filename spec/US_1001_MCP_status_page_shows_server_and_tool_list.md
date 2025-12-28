# US-1001 — MCP status page shows server and tool list (BL-1001)
## Goal
Expose a simple “MCP Status” UI page that shows whether the local MCP adapter is available and lists supported tools.

## Scope
- UI
  - Add `/mcp` page showing:
    - server status (running/available)
    - tool list including `search` and `write_observation`
  - Add link from Dashboard to MCP Status.
- API (optional but useful)
  - `GET /api/mcp/status` returns `{ running: true, tools: [...] }`.

## Implementation plan
1. Platform
   - Add a small `McpToolRegistry` (or similar) that returns a static list of MVP tools:
     - `search`
     - `write_observation`
2. Delivery
   - Add `McpStatusController` for `/mcp` + `templates/mcp.html`.
   - Add `McpApiController` for `/api/mcp/status`.
3. Tests (Spring profile `test`)
   - UI test: `GET /mcp` contains the tool names `search` and `write_observation`.
   - API test: `/api/mcp/status` returns tools list including those names.

## Acceptance
- UI E2E: “View MCP server status and available tools” passes.

