# US-0901 — Update index to commit hash X (BL-0901)
## Goal
Allow the user to update indexing to a specified commit hash `X` and persist `lastIndexedCommit = X`.

## Scope
- UI (Indexing page)
  - Input for commit hash `X`.
  - Action “Update index to commit” (with confirmation).
  - Progress indicates update job and the source/target commits when available.
- REST API
  - `POST /api/index/update` with target commit hash; returns job state.
  - Metadata `lastIndexedCommit` updated to `X` on success.

## Implementation plan
1. Git support (safe, no working-tree checkout)
   - Extend `GitPort` with methods to work against a target commit:
     - `List<String> listTrackedFilesAtCommit(String commit)`
     - `byte[] readFileAtCommit(String commit, String repoRelativePath)`
   - Implement in `LocalGitAdapter` using `git` commands (`ls-tree`, `show`).
2. Index job use case
   - Extend the indexing job runner to support:
     - update job that targets commit `X` and re-uploads tracked files at `X` (MVP: full re-upload is acceptable; deletions can be ignored if vector store lacks delete).
   - Read current `lastIndexedCommit` from metadata to display “from A to X” in progress.
   - Update metadata lastIndexedCommit to `X` when successful.
3. Delivery
   - API: add `POST /api/index/update` accepting `{ "commit": "..." }` (or query param) and starting the job.
   - UI: extend `index.html` + `IndexController` to render commit input + buttons and call the new API endpoint.
4. Tests (Spring profile `test`)
   - Create a temp git repo with two commits `A` and `X`:
     - initial index at `A`
     - update to `X` via API/UI
   - Assert `/api/metadata` returns `lastIndexedCommit == X` after update.
   - Assert UI `/index` reflects progress and completion for the update action.

## Acceptance
- UI E2E: “Update index to a target commit hash X” passes.
- API: metadata.json lastIndexedCommit equals X.

