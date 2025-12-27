# BL-0001 (US-0001): App boots and renders Dashboard

## Run locally
```bash
./mvnw spring-boot:run
```

Then open:
- `http://localhost:8080/` (HTML contains `Dashboard`)
- `http://localhost:8080/health` (JSON `{ "status": "ok" }`)

## Tests
```bash
./mvnw test
```

