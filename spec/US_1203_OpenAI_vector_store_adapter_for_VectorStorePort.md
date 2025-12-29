# US-1203 — OpenAI vector store adapter for `VectorStorePort` (BL-1203)
## Goal
Replace the local filesystem “vector store” with a real OpenAI Vector Store adapter behind `VectorStorePort`, enabling:
- Uploading repo/spec/observation content to an OpenAI vector store with attributes
- Listing vector store files and their attributes/status
- Reading `metadata.json` content back (for durable state)

## Scope
- Add `OpenAIVectorStoreAdapter` implementing `VectorStorePort`
- Extend `VectorStorePort` so it can work with OpenAI semantics:
  - IDs are server-generated for uploaded files
  - Attach uploaded files to a vector store
  - Delete a file from a vector store (needed for diff-based indexing)
  - Read file content (needed to read `metadata.json`)
- Ensure `VectorStoreProjectStateAdapter` no longer assumes deterministic `"metadata.json"` == file id; instead it should:
  - find metadata file by attributes (e.g. `type=documentation`, `subtype=metadata`, `path=metadata.json`)
  - read its content via OpenAI “files content” endpoint

## Implementation plan (detailed)
1. Adapter selection (non-test profiles)
   - When OpenAI is configured and `openaiVectorStoreId` is present, use an OpenAI-backed `VectorStorePort`.
   - Otherwise, keep using the local filesystem `VectorStorePort` implementation.
2. OpenAI adapter behavior
   - Upload bytes as OpenAI File (`client.files().create(...)`, purpose `ASSISTANTS`).
   - Attach the uploaded file to the configured vector store (`client.beta().vectorStores().files().create(...)`).
   - Persist `attributes` (including `path`, `type`, `subtype`) on the vector-store file so filtering remains possible.
   - Implement upsert-by-`path` to prevent duplicates when reindexing (delete existing `path` then upload new).
   - Add delete support (delete from vector store and delete underlying file).
3. Metadata uniqueness
   - Store metadata with `path=metadata.json` so it is uniquely discoverable and can be upserted safely.
4. Tests
   - Add Mockito unit tests for the OpenAI adapter (no network) verifying upload/attach/delete and content download.

## Implementation samples (based on `spec/classes_backend_ai.txt`)

### Upload file with metadata/attributes
```java
FileObject uploadedFile = client.files().create(com.openai.models.files.FileCreateParams.builder()
    .file(MultipartField.<InputStream>builder()
        .value(fileContent)
        .contentType("text/plain")
        .filename(fileName)
        .build())
    .purpose(FilePurpose.ASSISTANTS)
    .build());

FileCreateParams.Attributes.Builder attrs = FileCreateParams.Attributes.builder();
attrs.putAdditionalProperty("type", JsonString.of("code"));
attrs.putAdditionalProperty("subtype", JsonString.of("business_logic"));
attrs.putAdditionalProperty("path", JsonString.of("src/main/java/..."));

client.vectorStores().files().create(FileCreateParams.builder()
    .vectorStoreId(vectorStoreId)
    .fileId(uploadedFile.id())
    .attributes(attrs.build())
    .build());
```

### List vector store files and surface status
```java
FileListPage files = client.vectorStores().files().list(
    FileListParams.builder().vectorStoreId(vectorStoreId).build());

for (VectorStoreFile f : files.data()) {
  String openAiFileId = f.id();
  String status = f.status() != null ? f.status().toString() : "unknown";
  // f.attributes() may contain additionalProperties → map to plain strings
}
```

### Delete from vector store (and optionally delete underlying file)
```java
client.vectorStores().files().delete(
    FileDeleteParams.builder().vectorStoreId(vectorStoreId).fileId(openAiFileId).build());
client.files().delete(com.openai.models.files.FileDeleteParams.builder().fileId(openAiFileId).build());
```

## Acceptance
- Indexing uploads real files to the configured OpenAI vector store id and they appear in `GET /api/vectorstore/files` with attributes.
- `metadata.json` can be found by attributes and read back as bytes (no reliance on deterministic file ids).
- Delete operation is available for later stories (diff-based indexing cleanup).

