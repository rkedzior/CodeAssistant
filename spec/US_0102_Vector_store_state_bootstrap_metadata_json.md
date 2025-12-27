# US-0102 — Vector-store state bootstrap (metadata.json inside vector store) (BL-0102)

## Goal
Ensure the app can bootstrap and read `metadata.json` as the only durable state (no DB):
- If metadata file is missing, create it in the “vector store” with attributes `type=documentation`, `subtype=metadata`.
- Dashboard displays `lastIndexedCommit` (empty / “Not indexed” when never indexed).

## Scope
- Introduce `VectorStorePort` and `ProjectStatePort` for `metadata.json` lifecycle.
- Provide a non-OpenAI implementation for now (local file-backed) and an in-memory `test` profile implementation.
- Add a small REST API to read metadata (and trigger bootstrap).
- Update Dashboard UI to display `lastIndexedCommit`.
- Automated acceptance tests for API + Dashboard display.

## Implementation plan
1. Define metadata model (MVP):
   - `ProjectMetadata` with `schemaVersion`, `lastIndexedCommit` (nullable/empty), and room for future fields.
2. Ports:
   - `VectorStorePort`: minimal operations needed now (`findByAttributes`, `createFile`, `readFile`).
   - `ProjectStatePort`: `getOrCreateMetadata()` and `readMetadata()`.
3. Adapters:
   - Default adapter: local filesystem “vector store” under `./.codeassistant/vectorstore/` storing file content + attributes.
   - Test adapter (`test` profile): in-memory vector store (no disk writes).
4. API + UI:
   - `GET /api/metadata` returns metadata (and creates it if missing) including attributes.
   - Dashboard loads metadata via port and displays `lastIndexedCommit` or “Not indexed”.
5. Tests (Spring profile `test`):
   - API test: first call to `GET /api/metadata` returns 200 and includes `type=documentation`, `subtype=metadata`.
   - UI test: dashboard HTML includes “Not indexed” when metadata has no `lastIndexedCommit`.

## Acceptance
- API: if metadata file not present, create `metadata.json` with attributes `type=documentation`, `subtype=metadata`.
- UI: Dashboard can read metadata and display `lastIndexedCommit` (empty if never indexed).

## Notes / follow-ups
- Later stories will replace the default “local vector store” adapter with `OpenAIVectorStoreAdapter` and store real OpenAI file IDs.

