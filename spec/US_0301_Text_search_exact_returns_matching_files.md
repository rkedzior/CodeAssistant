# US-0301 — Text search (exact) returns matching files (BL-0301)

## Goal
Provide fast lexical (exact) text search over the checked-out repo state:
- User can open Search page, select “Text search”, enter query, and see matching files with previews.
- Ignored files must not appear in search results.

## Scope
- Introduce `TextSearchPort` + `RipgrepTextSearchAdapter` (fallback to simple Java search if `rg` is unavailable).
- Add Search UI page (`GET /search`) with text search form and results list.
- Add REST endpoint `GET /api/search/text` returning JSON results (used by UI or tests).
- Ensure search runs against configured local repo path and respects `.gitignore` / tracked files.
- Automated tests for exact search and ignored-file exclusion.

## Implementation plan
1. Ports/models:
   - `TextSearchPort.searchExact(query)` returning file-level results with previews (file path + list of match lines/snippets).
2. Ripgrep adapter:
   - Execute `rg` in repo root with fixed-string mode.
   - Capture matches with line numbers and line previews; group by file.
   - Ensure `.gitignore` is respected (default) and/or restrict search to `GitPort.listTrackedFiles()` for consistency.
3. Delivery:
   - MVC controller + Thymeleaf template for Search page.
   - REST endpoint `GET /api/search/text?query=...` returning results.
4. Tests (Spring profile `test`):
   - Create temp git repo with:
     - tracked file containing `MyClass`
     - ignored file containing `MyClass` and listed in `.gitignore`
   - Configure project with local repo path, run initial index.
   - Call text search and assert tracked file appears and ignored file does not.

## Acceptance
- UI E2E: “Exact text search returns matching files” passes.
- UI E2E: “Indexing excludes gitignored files” passes (ignored file not in results).

