# US-0402 — Semantic search filters by type/subtype (BL-0402)

## Goal
Allow semantic search results to be **filtered by vector-store attributes** so the user can narrow queries to specific document types (e.g., specs).

## Scope
- Extend API: `GET /api/search/semantic` supports optional `type` and `subtype` query parameters.
- Extend Search UI (`/search?mode=semantic`) with filter controls:
  - Filter Type: `code | documentation | observation` (MVP values)
  - Filter Subtype: free text (MVP; controlled vocab later)
- Apply filters against vector-store file attributes already produced by indexing (`type`, `subtype`, `path`).

## Implementation plan
1. Core
   - Reuse existing `SemanticSearchPort.search(query, k, filters)` (added in BL-0401).
2. Adapter
   - Update `LocalSemanticSearchAdapter` to skip documents that do not match provided filters (`type`, `subtype` when present).
3. Delivery
   - API: accept `type`/`subtype`, build `filters` map, pass into `SemanticSearchPort`.
   - UI: add two inputs (Type select + Subtype input) visible only in semantic mode; preserve values on submit.
4. Tests (Spring profile `test`)
   - API test: after indexing a repo containing a spec file under `/spec` and a code file, query with `type=documentation&subtype=spec` returns only the spec path.
   - HTML/controller test: `/search?mode=semantic&type=documentation&subtype=spec&query=...` renders only the spec result.

## Acceptance
- UI E2E: “Semantic search filtered to specification documents” passes.

