# US-0803 — Apply accepted spec updates to working tree (BL-0803)
## Goal
Allow the user to accept proposed spec updates and apply them into the configured repo’s `/spec/*.md` working tree files, ready to commit.

## Scope
- REST API
  - `POST /api/spec/apply-updates` applies accepted proposals to selected spec files and writes them to disk.
  - Response includes which files were updated.
- UI (Spec Manager)
  - In the proposals view, allow accepting proposals (checkboxes) and an “Apply updates” action.
  - After apply, show a success message indicating changes are ready to be committed.

## Implementation plan
1. Core
   - Extend `SpecStoragePort` with `writeSpecFile(path, content)` (or similar).
   - Add an `ApplySpecUpdatesUseCase` that:
     - Recomputes proposals via `ProposeSpecUpdatesUseCase` (deterministic) and applies only selected paths.
2. Adapter
   - Implement write support in `RepoSpecFolderAdapter` with the same path normalization + traversal protection as read.
   - Write UTF-8 and ensure parent directories exist.
3. Delivery
   - API: `POST /api/spec/apply-updates` accepts JSON body like `{ "paths": ["spec/a.md"] }` and applies updates.
   - UI:
     - Render checkboxes per proposal (default checked).
     - POST `/spec/apply-updates` submits selected paths, runs apply use case, and renders/redirects with success banner.
4. Tests (Spring profile `test`)
   - Setup temp repo with `spec/a.md` and at least one saved observation.
   - Apply updates for `spec/a.md` (API and UI variants) and assert:
     - the file content on disk changes and contains the appended section + observation marker
     - UI shows a success message after applying.

## Acceptance
- UI E2E: “Propose and apply spec updates from observations” passes.
- API: after apply, selected `/spec/*.md` files changed.

