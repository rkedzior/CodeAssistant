# US-1208 — Setup can create + validate an OpenAI vector store (BL-1208)
## Goal
Make OpenAI Vector Store onboarding smooth:
- User can **create** a new vector store from the app (optional)
- User can **validate** an existing `vectorStoreId` (check it exists and is accessible)
- App displays the chosen id and stores it in config + `metadata.json`

## Scope
- Add REST endpoints:
  - `POST /api/openai/vectorstore` with `{name}` → `{vectorStoreId}`
  - `GET /api/openai/vectorstore/{id}` → `{exists, name?, fileCount?, createdAt?}`
- Extend `/setup` UI:
  - Button “Create new vector store” (opens name input, calls API, fills the id field)
  - Button “Validate” (calls API and renders result inline)
- Persist the created/selected id to project config and also into `metadata.json.openai.vectorStoreId`

## Implementation samples (from `spec/classes_backend_ai.txt`)

### Create vector store
```java
VectorStoreCreateParams params = VectorStoreCreateParams.builder()
    .name(name)
    .build();

VectorStore vs = client.vectorStores().create(params);
String vectorStoreId = vs.id();
```

### Validate/retrieve vector store info
```java
VectorStore vectorStore = client.vectorStores().retrieve(
    VectorStoreRetrieveParams.builder().vectorStoreId(vectorStoreId.trim()).build()
);

FileListPage files = client.vectorStores().files().list(
    FileListParams.builder().vectorStoreId(vectorStore.id()).build()
);
```

## Acceptance
- UI: user can create a vector store from `/setup` and the created id is saved.
- UI: validation clearly indicates whether the id exists and is accessible (and shows basic info).
- Persistence: `vectorStoreId` is stored in project config and in `metadata.json`.

