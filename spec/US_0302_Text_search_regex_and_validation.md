# US-0302 — Text search (regex) + validation (BL-0302)

## Goal
Extend text search with an optional **regex** mode:
- When regex is enabled, query is treated as a regex and matching files/previews are returned.
- When regex is invalid, user sees a clear validation error and no results are shown.

## Scope
- Add regex toggle to Search UI (`/search`).
- Extend API to support regex mode (e.g., `GET /api/search/text?query=...&regex=true`).
- Implement regex search using `rg` when available, with Java fallback.
- Validate regex input and surface errors consistently (UI + API).
- Automated tests for regex search and invalid regex error.

## Implementation plan
1. Extend `TextSearchPort`:
   - Add a method or parameter to support `regex=true|false`.
   - Define an error/exception model for invalid regex.
2. Adapter updates:
   - Ripgrep: use `rg --regexp` mode and parse matches same as exact mode.
   - Java fallback: compile `Pattern` and match line-by-line; handle `PatternSyntaxException`.
3. Delivery:
   - UI: add checkbox “Regex”; preserve query/regex in form; display error banner when invalid.
   - API: return 400 with a JSON error (or 200 with `error` field) for invalid regex; ensure no results returned.
4. Tests (Spring profile `test`):
   - Regex happy path: `foo\\s+bar` matches a tracked file.
   - Invalid regex: `foo(` returns validation error and empty results.

## Acceptance
- UI E2E: “Simple regex search returns matching files” passes.
- UI E2E: “Invalid regex shows a validation error” passes.

