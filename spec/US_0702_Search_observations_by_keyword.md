# US-0702 — Search observations by keyword (BL-0702)
## Goal
Allow the user to filter observations by a simple keyword query.

## Scope
- Observations UI (`/observations`)
  - Add a search input (keyword) and submit button.
  - When a query is present, list shows only matching observations (case-insensitive substring match on text; optionally include subtype/id).
  - Preserve the query value after submit.
- REST API
  - Extend `GET /api/observations` with optional query param (e.g., `q`) to return only matching observations.

## Implementation plan
1. Core
   - Keep `ObservationsPort` as-is (returns full list) and implement filtering in delivery layer for MVP simplicity.
2. Delivery
   - Update `ObservationsController`:
     - Accept optional `q` in `GET /observations`.
     - Add `q` to model and filter observations list when non-blank.
   - Update `observations.html`:
     - Add a small search form (GET) with `q` input above the list.
     - Show a “no matches” message when the list is empty while `q` is present.
   - Update `ObservationsApiController`:
     - Accept optional `q` and filter the returned list when non-blank.
3. Tests (Spring profile `test`)
   - API: create 2 observations, then `GET /api/observations?q=<term>` returns only matching items.
   - UI: create 2 observations (POST), then `GET /observations?q=<term>` shows only the matching text.

## Acceptance
- UI E2E: “Search observations by keyword” passes.

