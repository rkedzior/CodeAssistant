# HLD — Code & Docs Intelligence Platform (MVP Final)

## 1. Summary
A local-first **developer application** for a **single monorepo** (Java/Python + Markdown), providing:
- **Lexical search** (ripgrep-based, file-level results, optional simple regex)
- **Semantic search** (OpenAI Vector Store)
- **LLM analysis** (OpenAI Responses API, free-form explanations)
- **On-demand indexing** (initial + incremental update to a user-provided commit hash on `main`, plus full reload)
- **Memory Bank** (plain-text observations)
- **Specification management** (Markdown files in repo `/spec/`, updates suggested from observations and accepted by user)
- **UI** (Java + Thymeleaf) + **REST API** + **MCP server** (local), MVP tools: `search`, `write_observation`

MVP constraints:
- Single user, single project, monorepo
- Main branch only
- No database
- Durable runtime state stored only in the OpenAI Vector Store

---

## 2. Goals
- Replace “agentic tool file search” with fast local lexical search.
- Enable semantic retrieval across code, docs, spec, and observations.
- Keep the knowledge base up to date via commit-driven indexing.
- Maintain living specs in `/spec/` updated from development observations.

Non-goals (MVP): multi-user, multi-project, multi-branch, historical snapshots/time travel, AST chunking.

---

## 3. Users and scope
- Primary user: Developer (single user in MVP)
- Content types:
  - Code: Java, Python
  - Documentation: Markdown
  - Specs: Markdown under `/spec/`
  - Observations: plain-text

---

## 4. Core workflows
### 4.1 Indexing
- Initial index at `main` HEAD (or given commit)
- Incremental update: lastIndexedCommit → commit X
- Full reload to commit X
- Index only tracked files; respect `.gitignore`

### 4.2 Search
- Lexical search (ripgrep) → file-level matches + previews
- Semantic search (OpenAI Vector Store) with filters by file attributes

### 4.3 Analysis
- RAG-style: retrieve relevant vector store files → call OpenAI Responses API → free-form explanation (usually with citations)

### 4.4 Memory and specs
- Save observations (plain text) into vector store
- Propose spec updates (LLM) from observations + current `/spec/` docs
- User accepts changes → app writes updates into `/spec/` working tree (user commits via normal git workflow)

---

## 5. Architecture
### 5.1 Deployment shape
- **Modular monolith** (single Spring Boot app)
- Local Docker Compose

### 5.2 Internal structure
- **Vertical slices** with ports & adapters inside each slice.
- MCP is treated as **delivery/inbound adapters** (not a separate business slice).

Recommended top packages:
- `app.platform` (config, MCP host/registry, shared utilities)
- `app.indexing`, `app.search`, `app.analysis`, `app.memory`, `app.spec`

Slice layout convention:
- `delivery.web` (Thymeleaf MVC)
- `delivery.api` (REST)
- `delivery.mcp` (MCP tools)
- `application` (use cases)
- `ports` (capability interfaces + DTOs)
- `adapters.*` (provider implementations)

### 5.3 Ports (capability-based)
- `GitPort` (clone/checkout/diff/list tracked files)
- `TextSearchPort` (lexical search)
- `VectorStorePort` (OpenAI vector store operations)
- `LlmPort` (OpenAI Responses)
- `SpecStoragePort` (read/write `/spec/`)
- `ProjectStatePort` (read/write metadata.json within vector store)

### 5.4 Adapters (MVP)
- Git: `LocalGitAdapter` (and/or GitHub for repo discovery)
- Lexical: `RipgrepTextSearchAdapter`
- Vector store: `OpenAIVectorStoreAdapter`
- LLM: `OpenAIResponsesAdapter`
- Spec: `RepoSpecFolderAdapter`
- State: `OpenAIProjectStateAdapter` (metadata.json stored inside vector store)

---

## 6. OpenAI Vector Store data model
### 6.1 File attributes
Each vector-store file has attributes:
- `type`: `code | documentation | observation`
- `subtype`: controlled vocabulary

MVP subtype examples:
- code: `ui | business_logic | configuration | infrastructure | test | other`
- documentation: `spec | metadata | readme | adr | runbook | other`
- observation: `agent_log | note | decision | risk | other`

### 6.2 Classification rules
- Subtype is assigned via simple path-based rules stored in `metadata.json` (vector store).
- Default subtype used when no rule matches.

### 6.3 Embedding granularity
- Whole-file embedding for small files
- Windowed chunks for large files (maxChars + overlap)

---

## 7. Incremental indexing algorithm
Inputs:
- `lastIndexedCommit` from vector-store metadata.json
- target commit X from UI

Algorithm:
1. Checkout commit X
2. Compute diff from lastIndexedCommit → X
3. For each changed path:
   - Added/Modified: re-upload content as vector store files (whole or windowed parts)
   - Deleted/Renamed: delete previous vector store file(s) using stored OpenAI file ids
4. Update `lastIndexedCommit` in vector-store metadata.json

Fallback:
- Full reload at commit X

---

## 8. Persistence and configuration (no DB)
### 8.1 `.env` (secrets + machine-specific)
- `OPENAI_API_KEY`
- `GITHUB_TOKEN` (optional)
- `REPO_LOCAL_PATH`
- `APP_PORT`, `LOG_LEVEL`

### 8.2 `application.yml` (non-secret config)
- project id/name
- provider selection
- repo defaults (branch main, include patterns)
- indexing thresholds
- `openai.vectorStoreId` (optional; if empty, app can create a new one and display it)

### 8.3 Vector store `metadata.json` (only durable state)
Stored as a vector-store file with attributes:
- `type=documentation`, `subtype=metadata`

Schema (summary):
- project identity
- vectorStoreId
- lastIndexedCommit + settings used
- classification rules
- mapping path → openaiFileIds (to support delete/update)

If vector store is lost: observations lost; repo can be reindexed.

---

## 9. UI (MVP screens)
- Dashboard
- Project Setup
- Indexing (initial/update/full reload)
- Search (Text + Semantic)
- File Viewer
- Analysis Workspace
- Observations
- Spec Manager (`/spec/`)
- MCP Status

---

## 10. REST API (MVP outline)
- `POST /index/initial`
- `POST /index/update` (to commit)
- `POST /index/reload`
- `GET /search/text`
- `GET /search/semantic`
- `POST /analysis`
- `POST /observations`
- `GET /observations`
- `GET /spec/files`
- `GET /spec/file?path=...`
- `POST /spec/propose-updates`
- `POST /spec/apply-updates`

---

## 11. MCP (MVP)
- Tools:
  - `search` (text or semantic mode + filters)
  - `write_observation`

---

## 12. MVP deliverables
- Working Docker Compose app
- Indexing to commit X with incremental updates + full reload
- Text and semantic search
- Analysis with retrieval
- Observations storage in vector store
- Spec proposal + apply into `/spec/`
- MCP local server with tools

---

## 13. Future versions (high-level)
- v1: AST chunking, hybrid retrieval + reranking, better regex/search UX, more MCP tools, webhooks/scheduling
- v2: multi-user, multi-project, multi-branch, on-prem/private providers, integrations (Teams + ADO/Jira)

---

## 14. BDD scenarios
BDD scenarios are maintained in a separate document: **“BDD — MVP UI Scenarios”**.

