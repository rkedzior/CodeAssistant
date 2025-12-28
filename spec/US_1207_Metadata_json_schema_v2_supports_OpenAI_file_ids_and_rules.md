# US-1207 — `metadata.json` schema v2 for OpenAI Vector Store (BL-1207)
## Goal
Expand `metadata.json` to become the durable runtime state described in the HLD, enabling OpenAI-backed indexing + retrieval:
- Store `vectorStoreId` and indexing settings
- Store classification rules (path → subtype rules)
- Store path → OpenAI file id mapping (for delete/update)
- Support schema migrations from current v1

## Scope
- Bump schema version to `2`
- Extend metadata structure (example below)
- Add migration behavior:
  - If v1 is found: keep `lastIndexedCommit`, initialize new fields with defaults
  - If metadata is missing: create schema v2 defaults
- Update `TrackedFileClassifier` to read rules from metadata (with safe defaults when missing)

## Proposed schema (example)
```json
{
  "schemaVersion": 2,
  "project": { "name": "CodeAssistant", "repoRoot": "C:/dev/repo" },
  "openai": { "vectorStoreId": "vs_abc123", "model": "gpt-4.1-mini" },
  "indexing": {
    "lastIndexedCommit": "abcdef...",
    "maxChunkChars": 12000,
    "chunkOverlapChars": 800
  },
  "classificationRules": [
    { "pathPrefix": "spec/", "type": "documentation", "subtype": "spec" },
    { "pathPrefix": "src/main/java/app/core/", "type": "code", "subtype": "business_logic" }
  ],
  "pathToOpenAiFileIds": {
    "spec/hld.md": ["file_123"],
    "src/main/java/.../Foo.java": ["file_456", "file_789"]
  }
}
```

## Implementation samples

### Finding metadata in OpenAI vector store
Store metadata itself as a vector store file with attributes:
```json
{ "type": "documentation", "subtype": "metadata", "path": "metadata.json" }
```
Then locate it via list/search-by-attributes, not by assuming a stable OpenAI file id.

## Acceptance
- Metadata schema v2 is created on first run and migrated from v1 when present.
- Metadata includes `vectorStoreId`, `model`, rules, and path→fileId mapping.
- Index update/delete operations can be driven entirely from metadata (no local DB).

