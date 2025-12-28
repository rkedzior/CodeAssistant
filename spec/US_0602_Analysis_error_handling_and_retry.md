# US-0602 — Analysis error handling and retry (BL-0602)

## Goal
When the LLM/analysis execution fails, the user sees a **clear error message** and can **retry** without leaving the page.

## Scope
- Ensure consistent failure behavior for:
  - UI `/analysis` (shows error banner, keeps prompt + options for retry)
  - API `POST /api/analysis` (returns 400 with error and preserves retrieved context when available)
- Add fault-injection for automated tests (no real OpenAI calls):
  - “Fail once” mode to simulate a transient LLM failure, then succeed on retry.

## Implementation plan
1. LLM adapter fault injection
   - Extend the current local `LlmPort` implementation to support:
     - `codeassistant.llm.failOnce=true` → throw on first call, succeed thereafter.
     - (Optional) `codeassistant.llm.failAlways=true` → always throw.
2. Error surfacing
   - Confirm `RunAnalysisUseCase` returns `error` when `LlmPort` throws, and preserves retrieved context.
   - UI keeps prompt + `codeScope` checked state so user can click Analyze again.
3. Tests (Spring profile `test`)
   - API: with `codeassistant.llm.failOnce=true`, first `POST /api/analysis` returns 400 with `error`, second returns 200 with non-empty `answer`.
   - UI: with the same fault injection, first `POST /analysis` renders error, second renders answer.

## Acceptance
- UI E2E: “Analysis shows error when OpenAI request fails” passes (fault injection).

