# US-1210 — Indexing progress tracks OpenAI ingestion status (BL-1210)
## Goal
Make indexing accurate with OpenAI Vector Store behavior:
- Uploading a file is not the end; OpenAI must process it for retrieval
- UI/API should reflect: uploaded → processing → ready/failed

## Scope
- Extend index job status model to include per-file ingestion state (bounded list or counters)
- During indexing:
  - After attaching files to the vector store, poll vector store file status until:
    - all are “completed/ready”, or
    - a timeout is reached (then surface partial readiness)
- UI `/index` and API `/api/index/status` should show:
  - counts: uploaded, processing, ready, failed
  - last error message if any

## Implementation samples (status surfacing pattern from `spec/classes_backend_ai.txt`)
Vector store file listing provides a status:
```java
FileListPage files = client.vectorStores().files().list(
    FileListParams.builder().vectorStoreId(vectorStoreId).build()
);

for (VectorStoreFile f : files.data()) {
  String status = f.status() != null ? f.status().toString() : \"unknown\";
}
```

## Acceptance
- After indexing, the app reports readiness accurately (not “done” while files are still processing).
- Failures are visible (at least file id + path + status) and do not silently disappear.

