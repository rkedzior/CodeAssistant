# US-0203 — Upload indexed files to vector store with type/subtype attributes (BL-0203)

## Goal
During indexing, upload tracked repo files to the vector store with correct attributes:
- Code files: `type=code` and a valid `subtype`
- `/spec/*.md`: `type=documentation`, `subtype=spec`
and update `metadata.json.lastIndexedCommit`.

## Scope
- Extend indexing job to iterate tracked files and upload content to `VectorStorePort` with attributes.
- Add simple path-based classification for `type` + `subtype`.
- Expose a minimal API to list vector-store files (for acceptance verification).
- Add automated API tests proving sample uploads and metadata update.

## Implementation plan
1. Vector store capabilities:
   - Extend `VectorStorePort` with a read/list method suitable for tests and later search (e.g., `listFiles()` returning summaries).
2. Classification (MVP rules):
   - `spec/**/*.md` → `type=documentation`, `subtype=spec`
   - `README.md` → `type=documentation`, `subtype=readme`
   - `src/main/java/app/platform/delivery/**` → `type=code`, `subtype=ui`
   - `src/main/java/app/core/**` → `type=code`, `subtype=business_logic`
   - `src/main/java/app/platform/config/**` → `type=code`, `subtype=configuration`
   - `src/main/java/app/platform/adapters/**` → `type=code`, `subtype=infrastructure`
   - `src/test/**` → `type=code`, `subtype=test`
   - fallback: `type=code`, `subtype=other` for known code extensions; otherwise skip.
3. Index job integration:
   - In `StartInitialIndexUseCase`, after listing tracked files:
     - Read file bytes from repo working tree.
     - Compute safe `fileId` (e.g., hash of repo-relative path).
     - Upload via `VectorStorePort.createFile(fileId, content, attributes)` where attributes include `type`, `subtype`, and `path`.
   - Update metadata lastIndexedCommit (already present).
4. API:
   - Add `GET /api/vectorstore/files` returning list of file summaries (fileId + attributes + size/path).
5. Tests (Spring profile `test`):
   - Build a temp git repo containing:
     - a Java file under `src/main/java/app/core/...`
     - a Markdown spec under `spec/...`
   - Run initial index; assert `/api/vectorstore/files` includes:
     - at least one `type=code` with a valid subtype
     - at least one `type=documentation`, `subtype=spec`
   - Assert `/api/metadata` shows lastIndexedCommit equals repo HEAD.

## Acceptance
- API: sample uploaded files include code files with `type=code` + valid subtype and `/spec/*.md` with `type=documentation`, `subtype=spec`.
- API: metadata.json updated with lastIndexedCommit.

