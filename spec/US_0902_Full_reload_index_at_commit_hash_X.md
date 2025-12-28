# US-0902 — Full reload index at commit hash X (BL-0902)
## Goal
Allow the user to perform a full reload of the index at a specified commit hash `X`.

## Scope
- UI (Indexing page)
  - Action “Full reload” with confirmation and commit hash input.
  - Progress indicates reload job and target commit.
- REST API
  - `POST /api/index/reload` with target commit hash.
  - Metadata `lastIndexedCommit` updated to `X` on success.

## Implementation plan
1. Index job use case
   - Add `startFullReloadIndex(targetCommit)` alongside existing initial/update jobs.
   - For MVP, full reload can re-upload all tracked files at commit `X` (same mechanics as update), but labeled as reload in progress messages and success state.
2. Delivery
   - API: add `POST /api/index/reload` accepting `{ "commit": "..." }`.
   - UI: add “Full reload” button on `/index` using the same commit input and a confirmation dialog.
3. Tests (Spring profile `test`)
   - Temp git repo with commits A and X.
   - Index at A, then call reload to X and assert `/api/metadata` lastIndexedCommit == X.
   - Verify `/index` HTML includes the “Full reload” control.

## Acceptance
- UI E2E: “Full reload index at a target commit hash” passes.

