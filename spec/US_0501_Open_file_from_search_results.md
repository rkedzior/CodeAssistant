# US-0501 — Open file from search results (BL-0501)

## Goal
Enable opening a file from search results to view its content **at the currently indexed commit**, and display the indexed commit hash in the viewer.

## Scope
- Add a File Viewer page (e.g., `GET /file?path=...`) that:
  - Shows the repo-relative file path
  - Renders file content (text files only; show an error for binary/unreadable)
  - Displays the current `lastIndexedCommit`
- Update Search UI results so file paths are clickable links to the File Viewer (both text and semantic results).

## Implementation plan
1. Delivery
   - Add a controller `FileViewerController` handling `GET /file`.
   - Read `lastIndexedCommit` from `ProjectStatePort` (metadata) and show it in the model.
   - Use `GitPort.readWorkingTreeFile(path)` to load content; validate requested path is tracked (or at least does not escape repo root).
   - Add a new Thymeleaf template `file.html` for rendering.
2. Search UI integration
   - Wrap result paths in `<a>` links to `/file?path=...` for both text and semantic result sections.
3. Tests (Spring profile `test`)
   - Controller HTML test: after creating a temp repo and indexing, `GET /file?path=...` returns 200, contains file content and contains the indexed commit hash.
   - Search page test: `/search?...` renders an `<a href="/file?path=...">` link for a returned result.

## Acceptance
- UI E2E: “Open file from search results” passes.

