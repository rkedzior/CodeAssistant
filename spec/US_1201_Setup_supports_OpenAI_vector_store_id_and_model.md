# US-1201 — Setup supports OpenAI vector store id + model (BL-1201)
## Goal
Allow configuring (and later validating) the OpenAI integration parameters needed for real retrieval:
- `vectorStoreId` (OpenAI vector store id, e.g. `vs_...`)
- `model` (Responses API model name, e.g. `gpt-4.1-mini`)

## Scope
- Extend Project Setup UI (`GET/POST /setup`) to include:
  - Optional `vectorStoreId`
  - Optional `model` (with a sensible default if blank)
  - Help text explaining: semantic search + analysis will use Responses API File Search tool when `vectorStoreId` is set
- Extend persisted project config:
  - Add fields to `ProjectConfig` and to `.codeassistant/config.json`
  - Backward compatible load: old config without new fields must still load
- Dashboard should display (read-only):
  - “Vector store: Not configured” or the configured id
  - “Model: …”

## Implementation notes
- Current config is persisted locally under `./.codeassistant/config.json` (non-test profiles). Keep this for now; later stories can migrate secrets away from disk.
- Validation should be light but helpful:
  - If `vectorStoreId` is non-empty, validate format `^vs_[A-Za-z0-9]+$` (or a relaxed check if you want to support future formats).
  - If `model` is non-empty, just trim; don’t hard-fail on unknown models (the OpenAI call will validate).

## Implementation samples

### Data model
```java
// app.core.projectconfig.ProjectConfig
public record ProjectConfig(
    ProjectConfigMode mode,
    String openaiApiKey,
    String localRepoPath,
    String githubRepo,
    String githubToken,
    String openaiModel,
    String openaiVectorStoreId
) {}
```

### Setup form fields (Thymeleaf)
```html
<div class="row">
  <label for="openaiModel">OpenAI model</label>
  <input id="openaiModel" type="text" th:field="*{openaiModel}" placeholder="e.g. gpt-4.1-mini" />
  <div class="help">Used for Responses API calls (analysis + semantic search).</div>
</div>

<div class="row">
  <label for="openaiVectorStoreId">OpenAI vector store id</label>
  <input id="openaiVectorStoreId" type="text" th:field="*{openaiVectorStoreId}" placeholder="e.g. vs_abc123" />
  <div class="help">When set, retrieval uses File Search tool against this vector store.</div>
</div>
```

### Backward-compatible JSON load
If your `FileProjectConfigAdapter` uses Jackson to read/write `ProjectConfig`, ensure it:
- tolerates missing fields (Jackson default behavior)
- writes the new fields when present

## Acceptance
- UI: `/setup` includes fields for `OpenAI model` and `OpenAI vector store id`.
- Persistence: saving config writes/reads the new fields; previously saved config (without them) still loads.
- Dashboard: shows the configured `vectorStoreId` (or “Not configured”) and model.

