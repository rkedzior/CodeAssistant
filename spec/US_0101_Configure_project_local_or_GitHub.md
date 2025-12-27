# US-0101 — Configure project (local or GitHub) (BL-0101)

## Goal
Provide a Project Setup UI that lets the user configure:
- Local git repository path OR GitHub repository identifier (+ token)
- OpenAI API key
and persist the configuration locally so the Dashboard can show “Configured”.

## Scope
- New MVC page: Project Setup (e.g., `GET /setup`).
- Form submission: `POST /setup` with server-side validation and friendly errors.
- Local configuration persistence (no DB): store under a gitignored runtime folder.
- Dashboard shows a simple configuration status (Configured / Not configured).
- Automated acceptance tests for both local and GitHub setup flows.

## Implementation plan
1. Configuration model + persistence:
   - Introduce a small `ProjectConfig` model and a `ProjectConfigPort` (read/write).
   - Implement file-backed adapter (e.g., JSON under `./.codeassistant/config.json`), ensure directory is gitignored.
   - Provide a test-profile adapter using a temporary directory or in-memory store.
2. Web UI:
   - Add `ProjectSetupController`:
     - `GET /setup` renders the form.
     - `POST /setup` validates and saves; shows success message and/or redirects to `/`.
   - Add Thymeleaf template with mode selection:
     - Local mode: `localRepoPath`
     - GitHub mode: `githubRepo`, `githubToken`
     - Always: `openaiApiKey`
   - Add a small status indicator on the Dashboard.
3. Validation rules (MVP):
   - OpenAI API key: non-empty.
   - Local repo path: exists on disk and contains `.git` directory.
   - GitHub repo identifier: matches `owner/name` pattern.
   - GitHub token: non-empty when GitHub mode selected.
4. Tests (Spring profile `test`):
   - UI integration tests with MockMvc covering:
     - Local setup happy path → success + dashboard shows “Configured”.
     - GitHub setup happy path → success + dashboard shows “Configured”.
   - Negative validation test (e.g., invalid GitHub repo id or missing OpenAI key) → shows error and does not mark configured.

## Acceptance
- UI E2E: “Configure project with local git repository path” passes.
- UI E2E: “Configure project with GitHub repository” passes.

## Notes / follow-ups
- Later stories will integrate vector-store metadata bootstrap; keep config model flexible (e.g., allow adding `vectorStoreId` later).

