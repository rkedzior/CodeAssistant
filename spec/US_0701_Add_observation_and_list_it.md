# US-0701 — Add observation and list it (BL-0701)

## Goal
Allow the user to save a plain-text observation and view the list of saved observations.

## Scope
- Add Observations UI page `/observations`:
  - Textarea to enter observation text
  - Subtype selector (MVP: `note | decision | risk | other`)
  - List of saved observations (newest first)
- Add REST API:
  - `POST /api/observations` to save an observation
  - `GET /api/observations` to list observations
- Persist observations in the vector store as files with attributes:
  - `type=observation`
  - `subtype=<selected>`

## Implementation plan
1. Core
   - Add a small `app.core.observations` slice with DTOs and an `ObservationsPort` (save + list).
2. Platform adapter
   - Implement `ObservationsPort` using `VectorStorePort`:
     - Create fileId `obs_<timestamp>_<random>` (filesystem-safe).
     - Store observation text as UTF-8 bytes.
     - Attributes include `type`, `subtype`, and optional `createdAt` for sorting.
     - List by scanning vector store and filtering `type=observation`, then reading file contents.
3. Delivery
   - UI controller `/observations` (GET page; POST save with validation).
   - API controller `/api/observations` for JSON list/create.
   - Add link from Dashboard to Observations.
4. Tests (Spring profile `test`)
   - UI flow: POST observation then GET page shows it in list.
   - API: POST then GET returns it; vector-store file summaries include `type=observation` + selected subtype.

## Acceptance
- UI E2E: “Add a plain text observation” passes.
- API: observation stored as vector store file with `type=observation` + valid subtype.

