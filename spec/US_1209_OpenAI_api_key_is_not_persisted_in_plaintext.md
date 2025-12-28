# US-1209 — OpenAI API key is not persisted in plaintext (BL-1209)
## Goal
Align with the HLD `.env` approach and reduce risk:
- The OpenAI API key should come from environment/secrets, not `config.json`
- `/setup` should not require entering the raw key (optional “dev mode” may allow it)

## Scope
- Default behavior:
  - Resolve API key only from `OPENAI_API_KEY` or `openai.api.key`
  - `ProjectConfig.openaiApiKey` becomes optional/empty in production usage
- `/setup` UI:
  - Replace “OpenAI API key” input with a status panel:
    - “OPENAI_API_KEY detected” or “Missing; set env var and restart”
  - Optional checkbox “Allow dev-only key entry” (guarded by property like `codeassistant.allowKeyInUi=false`)
- Ensure logs never print the raw key; status endpoints must not echo secrets

## Implementation samples

### Property-guarded dev-only UI field
```java
@Value(\"${codeassistant.allowKeyInUi:false}\")
boolean allowKeyInUi;
```

### Status response (no secrets)
```json
{ \"configured\": true, \"source\": \"env\", \"model\": \"gpt-4.1-mini\" }
```

## Acceptance
- Without `codeassistant.allowKeyInUi=true`, the API key is never saved to `config.json`.
- App provides actionable setup guidance when the key is missing.
- No endpoint or log line exposes the raw API key.

