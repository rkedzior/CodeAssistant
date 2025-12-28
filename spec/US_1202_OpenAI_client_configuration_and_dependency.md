# US-1202 — OpenAI client configuration + dependency (BL-1202)
## Goal
Provide a production-grade OpenAI client wiring for:
- OpenAI Java SDK client initialization
- API key sourcing and validation
- Model selection defaults
 
This story is the foundation for replacing local stub behavior in `/search` and `/analysis`.

## Scope
- Add an OpenAI Java SDK dependency and a thin adapter layer
- Resolve API key in this order:
  1) `OPENAI_API_KEY` env var
  2) Spring property `openai.api.key` (optional)
  3) Project config `openaiApiKey` (current behavior, least preferred but supports UI)
- Centralize OpenAI client creation (single bean), with structured “not configured” errors rather than NPEs
- Add “OpenAI configuration status” endpoint for debugging:
  - `GET /api/openai/status` → `{configured: boolean, model: string, vectorStoreId: string?}`

## Implementation plan (detailed)
1. Dependencies
   - Add the OpenAI Java SDK dependency to `pom.xml` (pin a specific version).
2. Configuration resolution (centralized)
   - Add a small resolver component (or utilities) that resolves:
     - API key: `OPENAI_API_KEY` → `openai.api.key` → `ProjectConfig.openaiApiKey`
     - Model: `ProjectConfig.openaiModel` → fallback default (same default as BL-1201 dashboard)
     - Vector store id: `ProjectConfig.openaiVectorStoreId` (may be null/blank)
   - Ensure trimming/blank handling is consistent across web/UI and adapter layers.
3. Client bean wiring
   - Add a Spring `@Configuration` (e.g., `app.platform.config.OpenAIConfig`) that:
     - Creates `OpenAIClient` only when an API key is resolvable (otherwise exposes no bean).
     - Avoids test profile side effects: in `test` profile either do not create the client bean or create a lightweight stub.
4. Status endpoint
   - Add `GET /api/openai/status` that reports:
     - `configured` (true if API key present)
     - `apiKeySource` (optional but useful: `env|property|projectConfig|missing`, without exposing the key)
     - `model` (effective model string)
     - `vectorStoreId` (nullable/blank → null)
5. Tests
   - Add a `test`-profile MockMvc test verifying `/api/openai/status`:
     - returns configured=false when no key present (common in tests)
     - returns model derived from project config when provided
     - never includes the raw API key

## Implementation samples (based on `spec/classes_backend_ai.txt`)

### Maven dependency (example)
```xml
<dependency>
  <groupId>com.openai</groupId>
  <artifactId>openai-java</artifactId>
  <version><!-- pin a known good version --></version>
</dependency>
```

### Client factory (SDK pattern used in the reference app)
```java
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

OpenAIClient buildClient(String apiKey) {
  return new OpenAIOkHttpClient.Builder()
      .apiKey(apiKey)
      .build();
}
```

### API key resolution
```java
String resolveApiKey(Environment env, Optional<ProjectConfig> cfg) {
  String apiKey = env.getProperty("OPENAI_API_KEY");
  if (apiKey == null || apiKey.isBlank()) apiKey = env.getProperty("openai.api.key");
  if ((apiKey == null || apiKey.isBlank()) && cfg.isPresent()) apiKey = cfg.get().openaiApiKey();
  return apiKey == null ? null : apiKey.trim();
}
```

## Acceptance
- App starts when OpenAI is not configured, but OpenAI-backed features are gracefully blocked with a clear message.
- `GET /api/openai/status` reports whether an API key is available and which model/vectorStoreId would be used.
- OpenAI client initialization is centralized and testable (no “new client” scattered across adapters).
