# US-0401 — Semantic search returns ranked results (BL-0401)

## Goal
Provide a **Semantic search** mode that returns **top results ranked by relevance** against the already-indexed repository content.

## Scope
- Extend Search UI (`/search`) to support selecting **Text search** vs **Semantic search**.
- Add API endpoint `GET /api/search/semantic?query=...&k=...` returning ranked results.
- Implement semantic retrieval over the existing vector-store-backed indexed files:
  - Compute a lightweight “embedding” for query + files (MVP: local deterministic vectorization; no network calls).
  - Return top-k results with a relevance score and minimal preview/snippet.
- Ensure the feature is disabled/blocked with a meaningful message when project is not configured.

## Implementation plan
1. Core model + port (in `app.core.search`):
   - Add `SemanticSearchPort` with `search(String query, int k, Map<String,String> filters)` (filters used in BL-0402).
   - Add response DTOs: `SemanticSearchResult(path, score, preview?)`, `SemanticSearchResponse(query, results)`.
2. Adapter / implementation:
   - Implement semantic search by scanning current vector store files (excluding `metadata.json`) and ranking by similarity.
   - Use a deterministic local vectorizer (e.g., token bag + hashing to fixed dims + cosine similarity) so tests don’t require OpenAI.
   - Keep it small and fast (whole-file scoring; optional short preview from the file content).
3. Delivery:
   - API controller: `GET /api/search/semantic` returning JSON.
   - Thymeleaf: extend `search.html` with a mode selector and render semantic results (path + score).
   - Keep existing text search UX intact.
4. Test plan (Spring profile `test`):
   - API test: after indexing a temp repo with two files, semantic query ranks the more relevant file first.
   - UI/controller test (MockMvc): selecting semantic mode renders the results list.

## Acceptance
- UI E2E: “Semantic search returns relevant results” passes.

