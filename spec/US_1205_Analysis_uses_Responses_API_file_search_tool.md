# US-1205 — Analysis uses Responses API + File Search tool (BL-1205)
## Goal
Replace the deterministic local LLM stub with a real OpenAI Responses API call that performs retrieval via File Search tool against the configured vector store id.

This directly closes the gap: `/analysis` currently does not use Responses API.

## Scope
- Implement `LlmPort` as `OpenAIResponsesLlmAdapter`:
  - Uses configured model and vector store id
  - Uses File Search tool so the model retrieves relevant context itself
  - Supports “code scope” by applying a filter (attribute `type=code`) when enabled
- Keep the current UI shape: show “Retrieved Context” + “Answer”
  - The adapter should return:
    - answer text
    - a list of retrieved sources (paths/snippets) if the SDK response includes them; otherwise return at least the answer and a best-effort “sources unknown”

## Implementation samples (from `spec/classes_backend_ai.txt`)

### Wrapper method that enables File Search when a vector store id is present
```java
Optional<FileSearchTool> fileSearch = Optional.of(
    FileSearchTool.builder().vectorStoreIds(List.of(vectorStoreId)).build()
);

AIResponseResult result = responsesApi.callText(
    model,
    instructions,
    prompt,
    fileSearch,
    Optional.empty() // Optional<ComparisonFilter> when codeScope=true
);
```

### Responses API wrapper aggregating output text
Use the same aggregation pattern as in the reference:
```java
String text = response.output().stream()
    .flatMap(item -> item.message().stream())
    .flatMap(message -> message.content().stream())
    .flatMap(content -> content.outputText().stream())
    .map(ResponseOutputText::text)
    .collect(Collectors.joining(\"\\n\"));
```

## Acceptance
- `/analysis` calls OpenAI Responses API when `vectorStoreId` is configured.
- “Code scope” restricts retrieval to indexed code content (via File Search filter on `type=code`).
- Errors are surfaced consistently (same UX as BL-0602): API 400 + UI banner for config/validation issues.

