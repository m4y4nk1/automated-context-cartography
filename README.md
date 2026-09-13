# Context Cartography — AI-Powered Enterprise Architecture Context Diagram Generator

Spring Boot 3 (Java 21) service that ingests an Enterprise Architecture (EA) dataset,
builds a dependency graph, detects data-quality and architectural issues, and exposes
everything as JSON for a frontend (e.g. the bundled Cytoscape.js prototype).

## Features

- **Ingestion** — pluggable parsers for **JSON**, **Excel (.xlsx)** and **CSV (zip of files)**,
  driven by an externalized, config-based column mapping (`application.yml`).
- **Canonical model** — technology-agnostic domain: Applications, Interfaces,
  Business Processes, Domains, Information Objects.
- **Validation** — required fields, unique IDs, referential integrity (never throws;
  returns a `ValidationReport`).
- **Graph engine** — JGraphT `DirectedPseudograph` (apps as nodes, interfaces as edges).
- **Observation frames** — four node/edge projections: application, business process,
  domain (aggregated), information flow.
- **Impact analysis** — upstream + downstream blast radius for any application.
- **Insights** — ownership gaps, orphan interfaces, missing process mappings,
  lifecycle risks (EOL/Deprecated), dependency hotspots (single points of failure).
- **AI summary** — deterministic template generator by default; optional Azure OpenAI
  (Spring `WebClient`) behind the `ai` profile with graceful fallback.
- **Export** — PNG, PDF (PDFBox) and PPTX (POI) summaries.
- **Session store** — the uploaded model and its derived graph/findings/stats are cached
  in memory (no database). On startup the **bundled sample dataset is auto-loaded**, so
  the API works immediately without an upload.

## Requirements

- Java 21
- Maven (or the bundled `mvnw` / `mvnw.cmd` wrapper)

## Run

```bash
# from the project root
./mvnw spring-boot:run
# Windows PowerShell
.\mvnw.cmd spring-boot:run
```

The app starts on <http://localhost:8080> with the sample dataset already loaded.

To disable auto-loading the sample:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--ea.sample.autoload=false
```

### Run tests

```bash
./mvnw test
```

### Enable the Azure OpenAI summary (optional)

```powershell
$env:AZURE_OPENAI_ENDPOINT="https://<resource>.openai.azure.com"
$env:AZURE_OPENAI_API_KEY="<key>"
$env:AZURE_OPENAI_DEPLOYMENT="<deployment>"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=ai"
```

If the endpoint is unreachable or errors/times out, the service falls back to the
deterministic template summary.

## API Endpoints

Base path: `/api`

| Method | Path | Description | Success | Errors |
| ------ | ---- | ----------- | ------- | ------ |
| `POST` | `/api/upload` | Multipart `file` (`.json`, `.xlsx`, or `.zip` of CSVs). Parses, validates, and caches the model. Returns a `ValidationReport`. | `200` | `400` bad/empty/unsupported file |
| `GET` | `/api/graph/{frame}` | Node/edge projection. `frame` = `application` \| `process` \| `domain` \| `infoflow`. Returns a `GraphDto`. | `200` | `400` invalid frame, `409` no model |
| `GET` | `/api/node/{id}/impact` | Blast radius (upstream + downstream) for an application. Returns an `ImpactAnalysisResult`. | `200` | `404` unknown app, `409` no model |
| `GET` | `/api/insights` | All insight findings. Returns `List<Finding>`. | `200` | `409` no model |
| `GET` | `/api/summary` | Natural-language landscape summary. Returns `{ "summary": "..." }`. | `200` | `409` no model |
| `GET` | `/api/export?type=png\|pdf\|pptx` | Downloadable export of the current landscape. | `200` | `400` invalid type, `409` no model |

All errors are returned as a structured JSON body:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "details": [ ] }
```

### CORS

The React dev server origin `http://localhost:5173` is allowed by default
(configurable via `ea.cors.allowed-origins`).

### Quick examples

```bash
# Application graph (auto-loaded sample)
curl http://localhost:8080/api/graph/application

# Insights
curl http://localhost:8080/api/insights

# Impact of an application
curl http://localhost:8080/api/node/APP-ERP/impact

# Upload your own dataset
curl -F "file=@src/main/resources/sample_ea_dataset.json" http://localhost:8080/api/upload

# Export a PPTX
curl -OJ "http://localhost:8080/api/export?type=pptx"
```

## Configuration

Key properties (see `src/main/resources/application.yml`):

| Property | Default | Description |
| -------- | ------- | ----------- |
| `ea.ingestion.fields.*` | see yml | Column mapping per entity (shared by all parsers) |
| `ea.ingestion.json.roots.*` | see yml | Top-level JSON array names |
| `ea.ingestion.excel.sheets.*` | see yml | Worksheet name per entity |
| `ea.ingestion.csv.files.*` | see yml | CSV file name per entity (inside the zip) |
| `ea.insight.hotspot-degree-threshold` | `5` | Degree above which an app is a hotspot |
| `ea.sample.autoload` | `true` | Auto-load the bundled sample on startup |
| `ea.cors.allowed-origins` | `http://localhost:5173` | Allowed CORS origins |
| `ea.ai.azure.*` (profile `ai`) | env vars | Azure OpenAI endpoint/key/deployment/etc. |

## Project structure

```
com.vw.eacontext
├── model        canonical domain (records)
├── ingestion    JSON/CSV/Excel parsers
├── graph        JGraphT builder, projections, impact analysis
├── insight      detectors -> findings
├── ai           summary generators (template default, Azure optional)
├── validation   data-quality validation
├── api          REST controllers + in-memory session store + export
├── dto          request/response DTOs
├── config       @ConfigurationProperties, CORS, AI wiring
└── exception    custom exceptions + global handler
```

