# US-0801 — Browse /spec files (BL-0801)
## Goal
Allow the user to browse the configured project repository’s `/spec/` Markdown files and view a selected file’s content.

## Scope
- REST API
  - `GET /api/spec/files` lists spec files (repo-relative paths) under `/spec/` only.
  - `GET /api/spec/file?path=...` returns the content of a single spec file.
  - Prevent path traversal; reject paths outside `/spec/`.
- UI
  - Add a Spec Manager page (e.g., `GET /spec`) that lists files and shows selected file content.
  - Add a link from Dashboard to Spec Manager.

## Implementation plan
1. Core
   - Add `app.core.spec` slice with a `SpecStoragePort` (list + read).
   - Define simple DTO for returning file content (e.g., `SpecFile(path, content)`).
2. Adapter
   - Implement `RepoSpecFolderAdapter` (or similarly named) using `ProjectConfigPort` to resolve `localRepoPath`.
   - Enforce security:
     - Only allow repo-relative paths that start with `spec/` (or `spec\\` on Windows), normalize, and verify resolved path remains under `<repoRoot>/spec`.
3. Delivery (API + UI)
   - Add `SpecApiController` with endpoints listed above; return 400 for invalid path, 404 for missing file.
   - Add `SpecController` and `templates/spec.html`:
     - List files on the left, selected file content on the right (or stacked).
     - Use query param `path` to select a file (e.g., `/spec?path=spec/foo.md`).
4. Tests (Spring profile `test`)
   - Use a temp git repo directory with:
     - `spec/a.md` and `spec/b.md`
     - `not-spec.txt` (must not appear in list)
   - Save `ProjectConfig(mode=LOCAL, localRepoPath=<temp>)` into `ProjectConfigPort`.
   - API tests:
     - `/api/spec/files` returns only `spec/*.md` (or at least only under `spec/`).
     - `/api/spec/file?path=spec/a.md` returns expected content.
     - Path traversal (`../pom.xml`) returns 400.
   - UI test:
     - `/spec?path=spec/a.md` returns 200 and includes file content.

## Acceptance
- API: list spec files returns only under `/spec/`.
- UI E2E: opening a spec file shows content.

