# US-0202 — Indexing respects `.gitignore` and only tracks git files (BL-0202)

## Goal
Indexing enumerates only **tracked** repository files and does not include `.gitignore`d/untracked files.

## Scope
- Extend `GitPort` to enumerate tracked files at the configured repo state.
- Use that enumeration in indexing flows (at least to report counts / drive later uploads).
- Add an API-level test proving ignored/untracked files are excluded.

## Implementation plan
1. Extend `GitPort`:
   - Add `listTrackedFiles()` returning repo-relative paths (strings).
   - Implement in `LocalGitAdapter` via `git ls-files`.
2. Indexing integration:
   - Expose a lightweight API endpoint for inspection (MVP): `GET /api/index/tracked-files` (or include count in `/api/index/status`).
   - Ensure any indexing enumeration logic uses `GitPort.listTrackedFiles()` (not filesystem walking).
3. Tests (Spring profile `test`):
   - Create a temp git repo with:
     - One tracked file committed.
     - One file present on disk but ignored by `.gitignore` and not tracked.
   - Assert API output contains the tracked file and does not contain the ignored file.

## Acceptance
- API: indexing enumerates only tracked files and excludes gitignored files.

