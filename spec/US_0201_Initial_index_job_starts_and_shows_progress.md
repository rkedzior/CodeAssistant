# US-0201 — Initial index job starts and shows progress (BL-0201)

## Goal
From the UI, the user can run an **initial index** at `main` HEAD and see:
- A job starts after confirmation.
- Progress text changes while running.
- When finished, the Dashboard shows the last indexed commit hash.

## Scope
- Add Indexing page (`GET /index`) with “Initial index” action and confirmation.
- Add a lightweight job runner (single-job, in-memory state is OK for MVP).
- Add REST endpoint(s) to start a job and to poll job status/progress.
- Use local git (from configured local repo path) to compute HEAD commit hash.
- Update `metadata.json.lastIndexedCommit` when the job completes.
- Automated tests proving job start/progress and lastIndexedCommit display.

## Implementation plan
1. Git access port:
   - Add `GitPort` with minimal operations: `getHeadCommit()` (and later `checkout`, `diff`, `listTrackedFiles`).
   - Implement `LocalGitAdapter` using the `git` CLI against the configured local repo path.
   - In `test` profile, provide a fake/in-memory adapter or create a temp git repo via CLI in tests.
2. Indexing slice (MVP):
   - `IndexJobState` (status: IDLE/RUNNING/SUCCESS/FAILED, progress message, timestamps).
   - `StartInitialIndexUseCase`: kicks off async work; updates progress at least twice; writes `lastIndexedCommit` to metadata on success.
3. Delivery:
   - MVC: `GET /index` renders page with current job state and buttons.
   - REST: `POST /api/index/initial` starts job (returns 202 + job state).
   - REST: `GET /api/index/status` returns job state for polling.
   - UI uses small JS polling to update progress text.
4. Tests (Spring profile `test`):
   - Start job via `POST /api/index/initial`, then poll `GET /api/index/status` until status SUCCESS; assert progress message changed from initial value.
   - After job completes, `GET /` includes last indexed commit (not “Not indexed”).

## Acceptance
- UI E2E: “Run initial index using main HEAD” passes.
- UI E2E: progress text changes during job.
- UI: last indexed commit is displayed after completion.

## Notes / follow-ups
- BL-0203 will upload tracked files into vector store; this story only establishes job orchestration + metadata update.

