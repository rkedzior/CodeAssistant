# US-1003 — MCP write_observation works and is visible in UI (BL-1003)
## Goal
Allow writing an observation via the MCP adapter and confirm it appears in the Observations UI.

## Scope
- MCP API
  - `POST /api/mcp/write_observation` (or similar) accepts `{ text, subtype }` and stores the observation via `ObservationsPort`.
  - Returns the saved observation (id, subtype, createdAt, text).
- MCP Status UI (`/mcp`)
  - Add a small test form to submit observation text (and subtype).
  - Show the tool call result (or at least a success message).
- Integration
  - After writing via MCP, the observation appears on `/observations`.

## Implementation plan
1. Delivery (MCP API)
   - Extend `McpApiController` with `POST /api/mcp/write_observation`.
   - Validate non-blank text and valid subtype (default to `note` if omitted).
2. Delivery (UI test action)
   - Update `mcp.html` to include a “write_observation” test form.
   - Add `POST /mcp/write-observation` handler in `McpStatusController` that calls `ObservationsPort.save(...)` (or calls the MCP API internally) and redirects back with flash success/error.
3. Tests (Spring profile `test`)
   - Flow test: POST an observation via MCP (API or `/mcp/write-observation`), then GET `/observations` contains the text.

## Acceptance
- UI E2E: “Write an observation via MCP and see it in the UI” passes.

