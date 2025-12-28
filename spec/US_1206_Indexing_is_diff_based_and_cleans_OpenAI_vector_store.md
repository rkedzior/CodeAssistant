# US-1206 — Indexing is diff-based and cleans OpenAI vector store (BL-1206)
## Goal
Align indexing with the HLD: incremental update from `lastIndexedCommit` → target commit must:
- Upload added/modified files
- Delete removed files
- Handle renames/moves
- Keep `metadata.json` mapping path → OpenAI file ids for cleanup

This closes the gap where the current implementation “reuploads everything and never deletes”.

## Scope
- Extend indexing update logic to compute diff:
  - Use `git diff --name-status <from>..<to>` (or equivalent via `GitPort`)
  - Classify changes into Added/Modified/Deleted/Renamed
- For Added/Modified:
  - Upload to OpenAI (files API + attach to vector store) with attributes `type/subtype/path`
  - Update metadata mapping for that path
- For Deleted:
  - Lookup old OpenAI file ids for path in metadata
  - Delete from vector store (and optionally delete underlying OpenAI file)
  - Remove mapping entry
- For Renamed:
  - Treat as delete old path + upload new path (or update attributes if the API supports it reliably)

## Implementation samples

### Diff parsing (name-status)
Example output:
```
A\tnew/file.md
M\tsrc/main/java/app/core/Foo.java
D\told/file.md
R100\told/name.md\tnew/name.md
```

Pseudo-parse:
```java
sealed interface DiffItem { }
record Added(String path) implements DiffItem {}
record Modified(String path) implements DiffItem {}
record Deleted(String path) implements DiffItem {}
record Renamed(String from, String to) implements DiffItem {}
```

### Cleanup call (based on reference delete pattern)
```java
client.vectorStores().files().delete(
    FileDeleteParams.builder().vectorStoreId(vectorStoreId).fileId(openAiFileId).build());
client.files().delete(com.openai.models.files.FileDeleteParams.builder().fileId(openAiFileId).build());
```

## Acceptance
- Update index from commit A → X removes deleted paths from the vector store (no stale search hits).
- Rename/move results in only the new path being present (or old path removed).
- Metadata mapping is updated so future deletes work.

