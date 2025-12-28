# US-1102 — Resilience: metadata missing in vector store (BL-1102)
## Goal
If `metadata.json` is missing in the vector store, the app recreates it and indicates “Not indexed”; reindexing works after recovery.

## Scope
- Tests + small hardening if needed.

## Implementation plan
1. Test setup
   - Use `test` profile with `InMemoryVectorStoreAdapter`.
   - Configure a temp git repo via `/setup`.
2. Simulate missing metadata
   - Trigger metadata creation (e.g., call `/api/metadata` or load Dashboard once).
   - Remove `metadata.json` from the in-memory vector store map (via reflection in test only).
3. Assertions
   - `GET /` shows “Not indexed” after metadata removal (app recreates metadata).
   - Running `POST /api/index/initial` after recovery succeeds and updates `lastIndexedCommit` (Dashboard shows commit).

## Acceptance
- API: if metadata.json is missing, app recreates it and shows “Not indexed”.
- UI E2E: reindex succeeds after recovery.

