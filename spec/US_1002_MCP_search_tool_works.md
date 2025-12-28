# US-1002 — MCP search tool works (BL-1002)
## Goal
Expose an MCP “search” tool endpoint that supports both text and semantic search modes.

## Scope
- API (MCP adapter)
  - `POST /api/mcp/search` accepts a request with:
    - `mode`: `text | semantic`
    - `query`: string
    - text mode: `regex` boolean
    - semantic mode: `k` int, optional `type`/`subtype` filters
  - Returns search results (mode-specific payload) and surfaces validation errors as 400.

## Implementation plan
1. Delivery
   - Extend `McpApiController` with `POST /api/mcp/search`.
   - Route to existing ports:
     - text: `TextSearchPort.search(query, regex)`
     - semantic: `SemanticSearchPort.search(query, k, filters)`
   - Define request/response DTOs under MCP package.
2. Tests (Spring profile `test`)
   - MCP contract tests (MockMvc):
     - Text mode: create temp repo with a tracked file containing a known token, configure project, call `/api/mcp/search` and assert results include that path.
     - Semantic mode: create temp repo with semantic content, configure + run initial index, call `/api/mcp/search` with `mode=semantic` and assert non-empty ranked results.

## Acceptance
- MCP contract: calling `search` (text mode) returns results.
- MCP contract: calling `search` (semantic mode) returns results.

