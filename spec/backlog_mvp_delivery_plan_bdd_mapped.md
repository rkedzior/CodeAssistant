# Backlog — MVP Delivery Plan (BDD mapped)

This is the iterative delivery backlog that leads to the **MVP described in the HLD**.
- **Behavior-oriented** (user-observable outcomes)
- **Verifiable** (automated UI E2E / API / MCP contract tests)
- **No implementation plan** yet (only minimal technical highlights)

---

## 0. BDD readiness map (scenario → earliest user story)
This answers: **“After which user story can the scenario be executed?”**

### Project setup
- Configure project with local git repository path → **BL-0101**
- Configure project with GitHub repository → **BL-0101**

### Indexing
- Run initial index using main HEAD → **BL-0201**
- Update index to a target commit hash → **BL-0901**
- Full reload index at a target commit hash → **BL-0902**
- Indexing excludes gitignored files → **BL-0301** *(needs ignore handling + text search UI)*

### Text search
- Exact text search returns matching files → **BL-0301**
- Simple regex search returns matching files → **BL-0302**
- Invalid regex shows a validation error → **BL-0302**

### Semantic search
- Semantic search returns relevant results → **BL-0401**
- Semantic search filtered to specification documents → **BL-0402**

### File viewer
- Open file from search results → **BL-0501**

### Analysis
- Run analysis with code scope enabled → **BL-0601**
- Analysis shows error when OpenAI request fails → **BL-0602**

### Observations and specs
- Add a plain text observation → **BL-0701**
- Search observations by keyword → **BL-0702**
- Propose and apply spec updates from observations → **BL-0803**

### MCP
- View MCP server status and available tools → **BL-1001**
- Write an observation via MCP and see it in the UI → **BL-1003**

### End-to-end
- Index, search, analyze, capture observation, update spec → **BL-1101**

---

## Conventions
- **Verification**: each story must have ≥1 automated acceptance test.
- **DoD**: acceptance tests pass; UI shows meaningful errors; logs capture failures.

---

## Iteration 0 — Skeleton & smoke
### BL-0001 — App boots and renders Dashboard
**Acceptance (automated)**
- UI E2E: GET `/` returns 200 and shows “Dashboard”.
- API: GET `/health` returns 200.

**Enables BDD scenarios:** none directly (foundation for all UI scenarios)

---

## Iteration 1 — Configuration + vector-store state (no DB)
### BL-0101 — Configure project (local or GitHub)
**Acceptance (automated)**
- UI E2E: “Configure project with local git repository path” passes.
- UI E2E: “Configure project with GitHub repository” passes.

**Enables BDD scenarios:**
- Configure project with local git repository path
- Configure project with GitHub repository

**Technical highlights**: config via `application.yml` + `.env`; UI validation & feedback.

### BL-0102 — Vector-store state bootstrap (metadata.json inside vector store)
**Acceptance (automated)**
- API: if metadata file not present, create `metadata.json` with attributes `type=documentation`, `subtype=metadata`.
- API: Dashboard can read metadata and display `lastIndexedCommit` (empty if never indexed).

**Enables BDD scenarios:** none directly (prerequisite for indexing/stateful flows)

**Technical highlights**: vector store file discovery by attributes; schema versioning.

---

## Iteration 2 — Repo access & initial indexing
### BL-0201 — Initial index job starts and shows progress
**Acceptance (automated)**
- UI E2E: “Run initial index using main HEAD” passes.
- UI E2E: progress text changes during job.

**Enables BDD scenarios:**
- Run initial index using main HEAD

**Technical highlights**: job orchestration + progress reporting.

### BL-0202 — Indexing respects `.gitignore` and only tracks git files
**Acceptance (automated)**
- API: indexing enumerates only tracked files and excludes gitignored files.

**Enables BDD scenarios:** supports “Indexing excludes gitignored files” (scenario runnable after BL-0301)

**Technical highlights**: tracked-file enumeration + ignore application.

### BL-0203 — Upload indexed files to vector store with type/subtype attributes
**Acceptance (automated)**
- API: sample uploaded files include:
  - code files with `type=code` + valid subtype
  - `/spec/*.md` with `type=documentation`, `subtype=spec`
- API: metadata.json updated with lastIndexedCommit.

**Enables BDD scenarios:** supports semantic filter scenario (runnable after BL-0402)

**Technical highlights**: path-based classification rules stored in metadata.

---

## Iteration 3 — Lexical search (ripgrep)
### BL-0301 — Text search (exact) returns matching files
**Acceptance (automated)**
- UI E2E: “Exact text search returns matching files” passes.
- UI E2E: “Indexing excludes gitignored files” passes.

**Enables BDD scenarios:**
- Exact text search returns matching files
- Indexing excludes gitignored files

**Technical highlights**: ripgrep against checked-out repo state; file-level results + previews.

### BL-0302 — Text search (regex) + validation
**Acceptance (automated)**
- UI E2E: “Simple regex search returns matching files” passes.
- UI E2E: “Invalid regex shows a validation error” passes.

**Enables BDD scenarios:**
- Simple regex search returns matching files
- Invalid regex shows a validation error

**Technical highlights**: regex toggle; errors surfaced to UI.

---

## Iteration 4 — Semantic search (vector)
### BL-0401 — Semantic search returns ranked results
**Acceptance (automated)**
- UI E2E: “Semantic search returns relevant results” passes.

**Enables BDD scenarios:**
- Semantic search returns relevant results

**Technical highlights**: vector query + top-k results.

### BL-0402 — Semantic search filters by type/subtype
**Acceptance (automated)**
- UI E2E: “Semantic search filtered to specification documents” passes.

**Enables BDD scenarios:**
- Semantic search filtered to specification documents

**Technical highlights**: attribute filters.

---

## Iteration 5 — File viewer
### BL-0501 — Open file from search results
**Acceptance (automated)**
- UI E2E: “Open file from search results” passes.

**Enables BDD scenarios:**
- Open file from search results

**Technical highlights**: read file at indexed commit; display commit hash.

---

## Iteration 6 — Analysis workspace
### BL-0601 — Run analysis with retrieval context
**Acceptance (automated)**
- UI E2E: “Run analysis with code scope enabled” passes.
- API: analysis result includes answer + non-empty retrieved context list.

**Enables BDD scenarios:**
- Run analysis with code scope enabled

**Technical highlights**: retrieval + Responses API.

### BL-0602 — Analysis error handling and retry
**Acceptance (automated)**
- UI E2E: “Analysis shows error when OpenAI request fails” passes (fault injection).

**Enables BDD scenarios:**
- Analysis shows error when OpenAI request fails

**Technical highlights**: consistent error surfacing + retry.

---

## Iteration 7 — Observations
### BL-0701 — Add observation and list it
**Acceptance (automated)**
- UI E2E: “Add a plain text observation” passes.
- API: observation stored as vector store file with `type=observation` + valid subtype.

**Enables BDD scenarios:**
- Add a plain text observation

**Technical highlights**: observations stored/retrieved from vector store.

### BL-0702 — Search observations by keyword
**Acceptance (automated)**
- UI E2E: “Search observations by keyword” passes.

**Enables BDD scenarios:**
- Search observations by keyword

---

## Iteration 8 — Spec Manager (/spec) + suggested updates
### BL-0801 — Browse /spec files
**Acceptance (automated)**
- API: list spec files returns only under `/spec/`.
- UI E2E: opening a spec file shows content.

**Enables BDD scenarios:** supports spec-update scenario

### BL-0802 — Propose spec updates from observations
**Acceptance (automated)**
- UI E2E: proposal shows suggested changes grouped by spec file.

**Enables BDD scenarios:** supports spec-update scenario

### BL-0803 — Apply accepted spec updates to working tree
**Acceptance (automated)**
- UI E2E: “Propose and apply spec updates from observations” passes.
- API: after apply, selected `/spec/*.md` files changed.

**Enables BDD scenarios:**
- Propose and apply spec updates from observations

---

## Iteration 9 — Incremental indexing to commit X
### BL-0901 — Update index to commit hash X
**Acceptance (automated)**
- UI E2E: “Update index to a target commit hash” passes.
- API: metadata.json lastIndexedCommit equals X.

**Enables BDD scenarios:**
- Update index to a target commit hash

### BL-0902 — Full reload index to commit hash X
**Acceptance (automated)**
- UI E2E: “Full reload index at a target commit hash” passes.

**Enables BDD scenarios:**
- Full reload index at a target commit hash

---

## Iteration 10 — MCP (local)
### BL-1001 — MCP status page shows server and tool list
**Acceptance (automated)**
- UI E2E: “View MCP server status and available tools” passes.

**Enables BDD scenarios:**
- View MCP server status and available tools

### BL-1002 — MCP search tool works
**Acceptance (automated)**
- MCP contract: calling `search` (text mode) returns results.
- MCP contract: calling `search` (semantic mode) returns results.

**Enables BDD scenarios:** (validated by MCP contract tests; no dedicated UI scenario)

### BL-1003 — MCP write_observation works and is visible in UI
**Acceptance (automated)**
- UI E2E: “Write an observation via MCP and see it in the UI” passes.

**Enables BDD scenarios:**
- Write an observation via MCP and see it in the UI

---

## Iteration 11 — End-to-end + hardening
### BL-1101 — End-to-end MVP happy path
**Acceptance (automated)**
- UI E2E: “Index, search, analyze, capture observation, update spec” passes.

**Enables BDD scenarios:**
- Index, search, analyze, capture observation, update spec

### BL-1102 — Resilience: metadata missing in vector store
**Acceptance (automated)**
- API: if metadata.json is missing, app recreates it and shows "Not indexed".
- UI E2E: reindex succeeds after recovery.

**Enables BDD scenarios:** (hardening; candidate for a new BDD scenario)

---

## Post-MVP (not in MVP)
- AST-based chunking and symbol navigation
- Hybrid retrieval + reranking
- More MCP tools: get_file, analyze, index_to_commit, get_spec
- Multi-branch
- Integrations: ADO/Jira/Teams

