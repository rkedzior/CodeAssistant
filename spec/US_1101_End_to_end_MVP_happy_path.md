# US-1101 — End-to-end MVP happy path (BL-1101)
## Goal
Provide an automated end-to-end “happy path” test covering the key MVP workflow: configure → index → search → analyze → observation → spec update apply.

## Scope
- Tests only (primary deliverable for this story)
  - A single integration test using MockMvc under `test` profile that exercises the flow against a temp repo.

## Implementation plan
1. Test repo setup
   - Create a temp git repo with:
     - `src/main/java/app/core/PaymentService.java` containing a unique token (for text search).
     - `spec/US_9997_Payment.md` for spec browsing/update apply.
   - Commit the repo.
2. Flow steps (MockMvc)
   - Configure project via `POST /setup` (LOCAL).
   - Run initial indexing `POST /api/index/initial` and poll `/api/index/status` until SUCCESS.
   - Text search `GET /api/search/text?query=PaymentService&regex=false` returns results.
   - Analysis `POST /api/analysis` with `{"prompt":"Explain payment capture flow","codeScope":true}` returns answer and non-empty retrieved context.
   - Save observation `POST /api/observations` with unique marker text.
   - Propose updates `POST /spec/propose-updates` (HTML) contains proposed section and marker.
   - Apply updates `POST /spec/apply-updates` selecting `spec/US_9997_Payment.md`; then read file on disk and assert it contains the marker.
3. Assertions
   - Each step returns expected status and key content is present.

## Acceptance
- UI E2E: “Index, search, analyze, capture observation, update spec” passes.

