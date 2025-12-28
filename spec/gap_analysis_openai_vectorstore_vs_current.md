# Gap Analysis — OpenAI Vector Store & Responses API vs Current Implementation

This document compares:
- The intended design in `spec/hld_code_docs_intelligence_platform_mvp_final.md`
- The actually implemented behavior described in `spec/progress.txt` and visible in the codebase
- Observations: no configurable `vectorstore_id` in `/setup`, and `/search` + `/analysis` not using OpenAI Vector Store / Responses API

## Executive summary

The current app intentionally implements an **offline, local “vector store”** and a **deterministic local LLM stub**:
- Vector store: `FileSystemVectorStoreAdapter` writing into `./.codeassistant/vectorstore` (`src/main/resources/application-default.yml:4`)
- Semantic search: `LocalSemanticSearchAdapter` token-hash/cosine over stored file contents (`src/main/java/app/platform/adapters/search/LocalSemanticSearchAdapter.java:21`)
- Analysis: `LocalLlmAdapter` (“Local analysis (deterministic stub)”) (`src/main/java/app/platform/adapters/analysis/LocalLlmAdapter.java:34`)

Therefore, the OpenAI-based architecture described in the HLD (Vector Store + Responses API) is **not implemented** yet, and the UI lacks the configuration surface for a real OpenAI `vectorstore_id`.

## What the HLD expects (relevant excerpts)

From the HLD:
- **Semantic search**: “Semantic search (OpenAI Vector Store)”
- **LLM analysis**: “LLM analysis (OpenAI Responses API…)”
- **Durable runtime state**: “Durable runtime state stored only in the OpenAI Vector Store”
- **Config**: `openai.vectorStoreId` optional; if empty, app can create a new one and display it
- **Indexing algorithm**: diff-based incremental updates + delete/rename support, driven by metadata stored in the vector store
- **Classification rules**: stored in `metadata.json` inside the vector store

## Current implementation behavior (confirmed)

### `/setup` does not allow configuring a vector store id
- Setup form only collects repo source + OpenAI API key; there is no field for a vector store id (`src/main/resources/templates/setup.html:120`).
- Persisted runtime config is local file-backed (`./.codeassistant/config.json`), not `.env` (`src/main/resources/application-default.yml:2`).
- `ProjectConfig` has no `vectorStoreId` field (`src/main/java/app/core/projectconfig/ProjectConfig.java:7`).

### Vector store is local filesystem/in-memory, not OpenAI
- Vector store path is configured as `codeassistant.vectorstore.path: ./.codeassistant/vectorstore` (`src/main/resources/application-default.yml:4`).
- `VectorStorePort` is a minimal local abstraction (find by attributes, create with caller-chosen id, read, list) (`src/main/java/app/core/vectorstore/VectorStorePort.java:7`).
- `VectorStoreProjectStateAdapter` assumes a deterministic “file id” of `"metadata.json"` (`src/main/java/app/platform/adapters/projectstate/VectorStoreProjectStateAdapter.java:17`).

This does not match OpenAI Vector Store semantics, where file ids are **server-generated** and ingestion is **asynchronous**.

### Semantic search (`/search?mode=semantic`) is local scoring, not OpenAI retrieval
- Semantic search iterates over `vectorStorePort.listFiles()`, reads content bytes, and ranks using deterministic token hashing (`src/main/java/app/platform/adapters/search/LocalSemanticSearchAdapter.java:21`).
- Filter support is implemented as simple attribute key equality checks (works locally), but it is not mapped to OpenAI vector store filters.

### Analysis (`/analysis`) does not use OpenAI Responses API
- Analysis retrieves “context” using `SemanticSearchPort` and then calls `LlmPort`.
- `LlmPort` implementation is `LocalLlmAdapter`, which explicitly states no network calls (`src/main/java/app/platform/adapters/analysis/LocalLlmAdapter.java:34`).
- There is no OpenAI client dependency in `pom.xml`; no OpenAI SDK is present.

### Indexing is “re-upload everything” and does not delete stale docs
HLD expects a diff-based update. Current behavior:
- `startUpdateIndex(targetCommit)` enumerates **all** tracked files at the target commit and uploads them all again (`src/main/java/app/core/indexing/StartInitialIndexUseCase.java:72`).
- Uploaded file id is a deterministic hash of the path (`repo_<sha256(path)>`) (`src/main/java/app/core/indexing/StartInitialIndexUseCase.java:215`).

Key gap:
- Deleted/renamed paths are **never removed** from the vector store. With a local store this leaves stale entries; with an OpenAI store it would accumulate cost and reduce relevance.

### Project metadata is minimal and does not represent HLD-required state
- `ProjectMetadata` currently only stores `{schemaVersion, lastIndexedCommit}` (`src/main/java/app/core/projectstate/ProjectMetadata.java:6`).
- HLD requires metadata to also store: vector store identity, classification rules, indexing settings used, and a mapping from repo path → OpenAI file ids (for delete/update).

### Classification rules are hard-coded, not stored in vector store metadata
- `TrackedFileClassifier` is code-defined path rules and extension allowlist (`src/main/java/app/core/indexing/TrackedFileClassifier.java:8`).
- HLD expects these rules to be stored/edited via vector-store `metadata.json` so indexing behavior is portable and user-adjustable.

## What’s missing to meet the HLD (OpenAI Vector Store + Responses API)

This section describes the **design adjustments** needed to align implementation with the HLD, without prescribing an implementation plan.

### 1) Real OpenAI Vector Store adapter (and a richer port)

Missing capabilities in the current `VectorStorePort` abstraction:
- Server-generated ids (can’t require caller-provided `fileId`)
- Delete/detach operations (for diff-based indexing and cleanup)
- Search/query operation (semantic retrieval should not require reading entire file contents client-side)
- Ingestion status (OpenAI vector store file processing is asynchronous; indexing must reflect “processing/ready/failed”)
- Optional: list/search with metadata filters as first-class, not purely client-side

Adjustment needed:
- Introduce an OpenAI-backed adapter (HLD: `OpenAIVectorStoreAdapter`) and evolve the port so it can represent OpenAI’s capabilities (create store, attach file, delete, search, status).
- Keep the local adapter only if “offline mode” remains a deliberate product requirement; otherwise remove it to avoid divergence.

### 2) Vector store id lifecycle + UI configuration surface

Missing in UI (`/setup`) and config model:
- A persisted `vectorStoreId` (and optionally a “create new store” flow)
- Display of the currently used vector store id, plus validation/health feedback

Adjustment needed:
- Extend `ProjectConfig`/`ProjectSetupForm` to include `vectorStoreId` and update `setup.html` to allow:
  - Paste an existing id
  - Or “Create new vector store” (if supported by the chosen OpenAI integration)
- Ensure the chosen `vectorStoreId` is also stored in the durable `metadata.json` (as HLD indicates) so the store is discoverable/recoverable.

### 3) Replace local semantic search with OpenAI Vector Store retrieval

Missing behavior:
- `/api/search/semantic` should call OpenAI vector store search (or an equivalent retrieval mechanism), not rank by locally hashing content.
- Results should be based on vector-store chunks, then aggregated to file-level results for the UI (path, score, preview).

Adjustment needed:
- Implement `SemanticSearchPort` via OpenAI vector store search:
  - Use metadata filters (`type`, `subtype`, `path`) as OpenAI filter predicates (or emulate where not supported).
  - Provide stable previews: prefer using the returned chunk text/highlights; avoid reading the entire file list.

### 4) Replace local analysis with OpenAI Responses API + retrieval (RAG)

Missing behavior:
- `/analysis` should call OpenAI Responses API and incorporate retrieved context from the vector store.

Adjustment needed:
- Implement `LlmPort` using OpenAI Responses API:
  - Option A: Use Responses with a retrieval tool (e.g., file_search) pointing at the vector store id, letting OpenAI both retrieve and answer.
  - Option B: Perform explicit vector store search first, then send the retrieved excerpts as context to the Responses API.
- Return useful “retrieved context” items to the UI:
  - file path(s)
  - snippet(s)
  - optional citations/attributions if available from the Responses API response shape

### 5) Update indexing to match HLD: diff-based + deletes/renames

Missing behavior:
- Diff between `lastIndexedCommit` → `targetCommit`
- Delete/detach of removed paths
- Correct handling of renames/moves

Adjustment needed:
- Extend metadata schema to track path → OpenAI file ids (and possibly chunk ids) so deletes and updates can be performed.
- Align indexing job progress with OpenAI ingestion: show “uploaded”, “processing”, “ready/failed”.

### 6) Expand `metadata.json` schema to become the “durable state”

Missing metadata fields (HLD-aligned):
- `vectorStoreId`
- project identity (name/id), commit tracking, indexing settings
- classification rules (path-based subtype rules)
- path → OpenAI file ids mapping (for maintenance and cleanup)

Adjustment needed:
- Bump schema version and add migration behavior for existing stores.
- Ensure metadata discovery does not depend on a local deterministic file id (`metadata.json` can be the filename, but not the OpenAI file id).

### 7) Dependency and configuration gaps

Missing:
- OpenAI SDK or HTTP client integration in `pom.xml`
- Provider selection and environment-based key management consistent with HLD (`.env`/secrets)

Adjustment needed:
- Add the OpenAI integration layer (SDK or explicit REST client) and configuration knobs:
  - API key source (prefer env/secret store; avoid persisting the raw key in local `config.json` unless explicitly accepted)
  - vector store id and/or creation strategy
  - model selection for Responses API

## Conclusion

Your observations are accurate: the current implementation delivers an offline MVP with a local “vector store” and stub LLM, while the HLD specifies a real OpenAI Vector Store + Responses API architecture. To align with the HLD, the biggest missing pieces are:
- OpenAI vector store adapter + port capabilities (search, async ingestion, delete)
- `vectorStoreId` lifecycle + `/setup` UI support
- Responses API integration for `/analysis`
- HLD-grade metadata schema + diff-based indexing with deletes/renames

