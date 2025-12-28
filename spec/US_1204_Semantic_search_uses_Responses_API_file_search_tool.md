# US-1204 — Semantic search uses Responses API + File Search tool (BL-1204)
## Goal
Implement semantic search without local “embedding” or local scoring by using:
- OpenAI Responses API
- File Search tool bound to `vectorStoreId`

This directly replaces the current `LocalSemanticSearchAdapter`.

## Scope
- Replace semantic search implementation behind `SemanticSearchPort`:
  - When `vectorStoreId` is configured: call Responses API with `FileSearchTool`
  - When not configured: keep a helpful error (or fallback to local mode if you explicitly want “offline mode”)
- Support existing UI filters `type` and `subtype` by mapping them to File Search tool filters
- Produce stable, parseable results:
  - Use Responses API in “JSON output” mode (schema) so the UI can render results deterministically

## Implementation samples (from `spec/classes_backend_ai.txt`)

### Responses call with optional File Search tool
```java
ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
    .model(model)
    .input(input);

if (fileSearch.isPresent()) {
  builder.addTool(fileSearch.get());
}

Response response = client.responses().create(builder.build());
String json = response.output().stream()
    .flatMap(item -> item.message().stream())
    .flatMap(message -> message.content().stream())
    .flatMap(content -> content.outputText().stream())
    .map(ResponseOutputText::text)
    .collect(Collectors.joining());
```

### File Search tool bound to a vector store
```java
FileSearchTool fst = FileSearchTool.builder()
    .vectorStoreIds(List.of(vectorStoreId))
    .build();
```

### JSON schema prompt pattern (file-level results)
In the prompt/instructions, require a strict JSON response:
```json
{
  "results": [
    { "path": "spec/US_0401_...", "score": 0.72, "preview": "..." }
  ]
}
```

Then parse into your existing `SemanticSearchResponse`.

## Notes on filtering
- Current app stores `type`, `subtype`, `path` as attributes.
- Map UI filters to File Search filters, e.g.:
  - `type=code` → filter on attribute `type`
  - `subtype=spec` → filter on attribute `subtype`

## Acceptance
- `GET /api/search/semantic` no longer reads all vector store files and scores them locally.
- With a configured `vectorStoreId`, semantic search returns ranked results from OpenAI retrieval and honors `type/subtype` filters.
- With missing OpenAI config, API returns a clear error indicating what’s missing (API key / vectorStoreId).

