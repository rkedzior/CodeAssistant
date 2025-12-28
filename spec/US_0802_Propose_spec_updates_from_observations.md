# US-0802 — Propose spec updates from observations (BL-0802)
## Goal
Generate suggested `/spec/*.md` updates based on saved observations and show them grouped by spec file.

## Scope
- UI (Spec Manager)
  - Add a “Propose updates” action on `/spec`.
  - Show suggested changes grouped by spec file (at minimum: show file path + proposed content/patch).
- REST API
  - `POST /api/spec/propose-updates` returns proposals grouped by file.

## Implementation plan
1. Core
   - Add a small `app.core.specupdates` (or `app.core.spec`) model:
     - `SpecUpdateProposal(path, proposedContent, rationale?)`
   - Add a use case/service to build proposals from:
     - `ObservationsPort.list()`
     - `SpecStoragePort.listSpecFiles()` + `readSpecFile(path)`
2. Proposal algorithm (MVP, deterministic)
   - For each spec file:
     - Append a section (e.g., `## Proposed updates from observations`) containing bullet points from observations (or only the most recent N).
     - Avoid complex diffing; return full proposed content as a string.
3. Delivery
   - API: `POST /api/spec/propose-updates` returns JSON list of proposals.
   - UI:
     - Add a POST handler (e.g., `/spec/propose-updates`) that calls the use case and renders the proposals on `spec.html`.
     - Keep proposals grouped by file path.

## Tests (Spring profile `test`)
1. API
   - Create a temp repo with `spec/a.md`, `spec/b.md`.
   - Save at least one observation (use `ObservationsPort.save(...)` or `POST /api/observations`).
   - `POST /api/spec/propose-updates` returns proposals for spec files and includes the observation text in proposedContent.
2. UI
   - With the same setup, `POST /spec/propose-updates` renders HTML containing:
     - both file paths
     - proposed content containing the observation text.

## Acceptance
- UI E2E: proposal shows suggested changes grouped by spec file.

