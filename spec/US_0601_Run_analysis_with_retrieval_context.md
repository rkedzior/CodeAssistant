# US-0601 — Run analysis with retrieval context (BL-0601)

## Goal
Provide an Analysis workspace where the user can enter a prompt and receive:
- A free-form **answer**
- A non-empty list of **retrieved context items** (RAG-style) when the repository is indexed

## Scope
- Add Analysis UI screen `/analysis` with:
  - Prompt input
  - “Code scope” toggle (when enabled, retrieve from `type=code`)
  - Render retrieved context list + answer
- Add REST API `POST /api/analysis` returning `answer` and `retrievedContext[]`.
- Implement retrieval using existing `SemanticSearchPort` (MVP: local deterministic semantic scoring).
- Implement LLM answering as a **local stub** (no network calls) that produces a non-empty answer (OpenAI integration later).

## Implementation plan
1. Core (new `app.core.analysis` slice):
   - DTOs: `AnalysisRequest(prompt, codeScope)`, `RetrievedContextItem(path, preview)`, `AnalysisResponse(prompt, answer, retrievedContext, error)`.
   - Ports: `LlmPort` (generate answer from prompt + context) and `AnalysisPort` (or use-case) that orchestrates retrieval + LLM call.
2. Platform adapters
   - `LocalLlmAdapter` (non-test): builds a deterministic answer from prompt + context (no OpenAI).
   - `AnalysisService` (component) that uses `SemanticSearchPort` for retrieval and `LlmPort` for answering.
3. Delivery
   - UI controller `/analysis` (GET renders form; POST executes analysis and shows results).
   - API controller `POST /api/analysis` consuming/producing JSON.
4. Tests (Spring profile `test`)
   - API: after indexing a temp repo with a code file, `POST /api/analysis` with `codeScope=true` returns 200 with non-empty `answer` and `retrievedContext.length() > 0`.
   - UI: POST `/analysis` renders the answer + at least one retrieved context item.

## Acceptance
- UI E2E: “Run analysis with code scope enabled” passes.
- API: analysis result includes answer + non-empty retrieved context list.

