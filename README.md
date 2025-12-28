# CodeAssistant

## Requirements
- Java 17 (see `pom.xml`)

## Run locally (default profile)
```powershell
.\mvnw.cmd spring-boot:run
```

Then open:
- `http://localhost:8080/` (Dashboard UI)
- `http://localhost:8080/health` (JSON `{ "status": "ok" }`)
- `http://localhost:8080/setup` (Project Setup)

## Project Setup (`/setup`)
The setup form persists a `ProjectConfig` with:
- `mode`: `LOCAL` or `GITHUB`
- `openaiApiKey`: reserved for future OpenAI-backed providers (current MVP uses deterministic local implementations)
- `localRepoPath`: local git repository path (must be an existing directory containing `.git`) when `mode=LOCAL` (required for indexing/search in the current MVP)
- `githubRepo`: `owner/name` when `mode=GITHUB`
- `githubToken`: GitHub token when `mode=GITHUB`

With the implicit `default` profile, config is persisted to:
- `codeassistant.config.path` (default: `./.codeassistant/config.json`)

## Vector store persistence (default profile)
With the implicit `default` profile, vector store files are persisted under:
- `codeassistant.vectorstore.path` (default: `./.codeassistant/vectorstore/`)

## Profiles
- `default` (implicit): enabled when no profile is specified; uses filesystem-backed adapters and persists under `./.codeassistant/`.
- `e2etest` (explicit): intended for future BDD scenario automation (`spec/bdd_mvp_ui_scenarios.md`); uses in-memory adapters and defaults to isolated paths under `./.codeassistant-e2e/` and `server.port: 0`.
  - Run: `.\mvnw.cmd -Dspring-boot.run.profiles=e2etest spring-boot:run`
  - Tests (when BDD-style tests are added): `.\mvnw.cmd -Dspring.profiles.active=e2etest test`
- `test`: used for unit/integration tests; uses in-memory adapters.

## Tests
```powershell
.\mvnw.cmd test
```
