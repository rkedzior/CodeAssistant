# US-0001 — App boots and renders Dashboard (BL-0001)

## Goal
Create the initial Spring Boot application that boots locally and serves:
- `GET /` (HTML) showing “Dashboard”
- `GET /health` (JSON) returning 200

## Scope
- Add Maven-based Spring Boot + Thymeleaf skeleton.
- Add a basic MVC controller + template for Dashboard.
- Add a minimal REST endpoint for health.
- Add automated acceptance tests that verify both endpoints.

## Implementation plan
1. Create Java project structure:
   - `pom.xml` (Spring Boot, web, thymeleaf, test)
   - `src/main/java/...` application bootstrap
   - `src/main/resources/templates/dashboard.html`
   - `src/main/resources/application.yml`
2. Implement web layer:
   - `DashboardController` (MVC): `GET /` returns template with title “Dashboard”
   - `HealthController` (REST): `GET /health` returns `{ "status": "ok" }`
3. Add smoke tests (Spring profile `test`):
   - `DashboardSmokeTest`: asserts 200 + body contains “Dashboard”
   - `HealthSmokeTest`: asserts 200 + JSON contains `"status":"ok"`
4. Provide dev run docs:
   - `README.md` with `mvn spring-boot:run` and test command.

## Acceptance
- UI “E2E” (integration): `GET /` returns 200 and contains “Dashboard”.
- API: `GET /health` returns 200.

## Notes / follow-ups
- Later stories will add configuration pages, vector-store bootstrap, indexing jobs, etc.; keep this slice minimal and extensible.

