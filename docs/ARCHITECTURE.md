# Context Cartography — Architecture

A plain-language tour of how the system fits together: what each piece does, how a file
turns into a diagram on screen, and where everything lives in the codebase.


---

## 1. The big picture

Two independent apps talk over a REST API. The backend does all the real work (parsing,
validation, graph building, insight detection); the frontend is a thin, interactive viewer.
There's no database — whatever dataset was last uploaded (or the bundled sample, loaded
automatically on startup) lives in server memory for the lifetime of the process.

```
   ┌──────────────┐
   │  👤 User      │
   │  (Browser)    │
   └──────┬────────┘
          │ uploads a file, clicks around
          ▼
   ┌──────────────────────────┐
   │  React frontend            │
   │  Vite, Cytoscape.js        │
   │  localhost:5173            │
   └──────┬───────────▲─────────┘
          │            │
    REST calls    JSON responses
    over HTTP          │
          ▼            │
   ┌──────────────────────────┐        only if the "ai"       ┌─────────────────────┐
   │  Spring Boot backend       │  ─ ─ ─ profile is enabled ─▶ │  Azure OpenAI         │
   │  Java 21                   │                               │  (optional, off        │
   │  localhost:8080             │                               │   by default)          │
   └──────┬───────────▲─────────┘                               └─────────────────────┘
          │            │
   caches the parsed   read back on
   model + derived      every request
   graph + findings          │
          ▼            │
   ┌──────────────────────────┐
   │  In-memory session store   │
   │  (one model at a time,     │
   │   no database)             │
   └──────────────────────────┘

   Frontend then renders diagrams, panels, and popups back to the user.
```

### All the blocks, one map

The rest of this document walks each of these blocks one at a time (§2 onward). This is
the whole stack at once, grouped by layer — useful as a map to come back to.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  👤 User / Browser                                                                │
└───────────────────────────────────┬──────────────────────────────────────────────┘
                                     │ clicks, uploads
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│ FRONTEND — React + Vite · localhost:5173                                          │
│                                                                                    │
│  [Upload flow]           [Workspace shell]           [Graph canvas]               │
│  UploadPage         ──▶  FrameTabs · FilterPanel ──▶  GraphCanvas                  │
│  ValidationSummary       AnchorPicker · ExportButton  (Cytoscape.js)               │
│  LoadingScreen                    │                        │                       │
│                                    ▼                        ▼                       │
│                          [Panels & popups]         [Data access]                   │
│                          DashboardCards            hooks/*                         │
│                          InsightsPanel        ◀──▶ services/api.js                 │
│                          NodePopupDialog            graphAdapter.js                │
│                          EdgePopupDialog                                           │
└───────────────────────────────────┬──────────────────────────────────────────────┘
                                     │ REST / JSON
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│ BACKEND — Spring Boot 3 · Java 21 · localhost:8080                                │
│                                                                                    │
│  ┌─ API layer ───────────────────────────────┐                                    │
│  │  EaController (REST endpoints)              │                                    │
│  │  SessionModelStore (one active model)       │                                    │
│  └──────────────┬───────────────────────────┘                                    │
│                  │ dispatches to                                                   │
│                  ▼                                                                 │
│  ┌─ Core services ───────────────────────────────────────────────────┐            │
│  │  ingestion   validation   graph               insight    ai       │            │
│  │  (Excel/     (Validation  (Builder ·           (18        (Summary │            │
│  │  CSV/JSON     Service)     Projections ·        detectors) Generator│           │
│  │  parsers)                  Impact)                          )      │            │
│  └──────────────┬───────────────────────────────────────────────────┘            │
│                  │ all read/write                                                  │
│                  ▼                                                                 │
│  ┌─ Domain model ─────────────────────────────┐                                    │
│  │  CanonicalModel                              │                                    │
│  │  Applications · Relationships · Interfaces ·  │                                   │
│  │  InformationObjects · BusinessProcesses · ... │                                   │
│  └───────────────────────────────────────────┘                                    │
│                                                                                    │
│  export (PNG · PDF · PPTX · draw.io) hangs off EaController                        │
└───────────────────────────────────┬──────────────────────────────────────────────┘
                                     │ if configured
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│  Azure OpenAI — optional, "ai" profile only                                       │
└──────────────────────────────────────────────────────────────────────────────────┘
```

| Layer | Block | What lives there |
|---|---|---|
| Frontend | UI | Everything under `frontend/src/` — screens, canvas, panels, popups, and the thin data-access layer that talks to the API. |
| API layer | Entry point | The single REST entry point (`EaController`) and the one-model-at-a-time in-memory session (`SessionModelStore`). |
| Core services | Business logic | The five backend packages that do the actual work: turn files into data, check it, graph it, find problems in it, and summarize it. |
| Domain model | Data shape | `CanonicalModel` — the one shape every other block reads from or writes to. Nothing talks to a database; this *is* the data layer. |
| External | Optional | The only thing outside the process boundary — Azure OpenAI, and only when explicitly enabled. Everything else runs locally, in memory. |

---

## 2. Backend: layered architecture

Each package has one job. Data flows top to bottom — a raw file becomes a typed model,
gets checked, gets turned into graphs and findings, and finally gets shaped into JSON.

```
 1. INGESTION                             2. MODEL
┌───────────────────────┐        ┌───────────────────────────────┐
│ ExcelEaDataParser      │        │ CanonicalModel                 │
│ CsvEaDataParser        │──────▶ │ (Applications, Relationships,   │
│ JsonEaDataParser       │        │  Interfaces, InformationObjects,│
│        │                │        │  BusinessProcesses,              │
│        ▼                │        │  ProcessMappings, Ownerships,    │
│ IngestionSupport        │        │  DataQualityGaps)                │
│ (shared parsing helpers)│        └───────────────┬─────────────────┘
└───────────────────────┘                          │
                                                     │
                 ┌───────────────────────────────────┼───────────────────────────────┐
                 ▼                                    ▼                                ▼
 3. VALIDATION                       4. GRAPH + INSIGHT                        5. AI
┌───────────────────────┐   ┌─────────────────────────────────────┐   ┌────────────────────────┐
│ ValidationService       │   │ GraphBuilderService                    │   │ SummaryGenerator          │
│        │                │   │ (the one analytical dependency graph)  │   │ (template by default,     │
│        ▼                │   │        │                                │   │  or Azure OpenAI)         │
│ ValidationReport         │   │        ├─▶ GraphProjectionService       │   └────────────┬───────────┘
│ (issues + severity +     │   │        │   (4 observation frames)       │                │
│  summary)                │   │        ├─▶ InsightService               │                │
└───────────┬─────────────┘   │        │   (18 detectors)                │                │
             │                 │        └─▶ ImpactAnalysisService         │                │
             │                 │            (blast radius)                │                │
             │                 └─────────────────────────────────────┘                │
             │                                    │                                     │
             └────────────────────┬───────────────┴─────────────────────────────────────┘
                                   ▼
                        6. API
             ┌──────────────────────────────────────────────┐
             │ SessionModelStore — holds the current model,   │
             │   graph, and findings                          │
             │        │                                        │
             │        ▼                                        │
             │ EaController — every /api/* endpoint            │
             │        │                                        │
             │        ▼                                        │
             │ ExportService — PNG / PDF / PPTX / draw.io      │
             └──────────────────────────────────────────────┘
```

**What each layer means, in one line each:**

| Layer | Job |
|---|---|
| `ingestion` | Turn a `.xlsx`, `.csv` (zip), or `.json` file into rows, tolerating messy headers/columns via config-driven matching — never guessing at data it can't confidently map. |
| `model` | The one shared shape every downstream layer works with (`CanonicalModel`), regardless of which file format it came from. |
| `validation` | Check the model for missing fields, duplicate IDs, broken cross-references, bad enum values — collects everything into a report instead of throwing. |
| `graph` | Build the real dependency graph (JGraphT), then project it into 4 different "views" for the UI, plus answer "what breaks if I remove this app?" |
| `insight` | Run 18 independent detectors over the model + graph to surface architectural problems (orphans, hubs, cycles, lifecycle risk, ownership gaps, ...). |
| `ai` | Turn the numbers into a paragraph — a deterministic template by default, or a real LLM call if configured. |
| `api` | The REST surface: holds the one active session in memory, dispatches requests to the layers above, and handles exports. |

---

## 3. What happens when you upload a file

```
User          Frontend        EaController      Parser          ValidationService   SessionModelStore
 │                │                 │              │                    │                   │
 │ picks a file   │                 │              │                    │                   │
 │───────────────▶│                 │              │                    │                   │
 │                │ POST /api/upload│              │                    │                   │
 │                │ (multipart)     │              │                    │                   │
 │                │────────────────▶│              │                    │                   │
 │                │                 │  parse(file) │                    │                   │
 │                │                 │─────────────▶│                    │                   │
 │                │                 │◀─────────────│ CanonicalModel      │                   │
 │                │                 │  validate(model)                  │                   │
 │                │                 │───────────────────────────────────▶│                   │
 │                │                 │◀───────────────────────────────────│ ValidationReport   │
 │                │                 │     (issues, never throws)         │                   │
 │                │                 │  store model                       │                   │
 │                │                 │─────────────────────────────────────────────────────────▶│
 │                │                 │                                                          │ build(model) → graph
 │                │                 │                                                          │ analyze(model, graph)
 │                │                 │                                                          │  → findings (cached)
 │                │◀────────────────│ 200 + ValidationReport JSON                              │
 │◀───────────────│                 │                                                          │
```

Note: a genuinely broken file (wrong format, unreadable) never reaches this point — it fails
fast as a 400, before a `CanonicalModel` even exists.

The validation report is never a reason to reject the upload — even a file full of problems
gets parsed, cached, and handed back with a full list of what's wrong. The graph and
findings are computed once, right after upload, and reused for every later request until
the next upload replaces them.

---

## 4. One model, four ways to look at it

`GraphProjectionService` turns the same `CanonicalModel` into four different node/edge
shapes, one per "observation frame" — each answering a different question.

```
                          ┌───────────────────────┐
                          │  CanonicalModel          │
                          └───────────┬─────────────┘
                                      ▼
                          ┌───────────────────────┐
                          │  GraphProjectionService  │
                          └───────────┬─────────────┘
             ┌───────────────┬─────────┴───────────┬────────────────┐
             ▼               ▼                     ▼                 ▼
     ┌───────────────┐ ┌──────────────┐  ┌─────────────────┐ ┌───────────────────┐
     │ application     │ │ domain        │  │ process           │ │ infoflow             │
     │ apps as nodes;  │ │ apps          │  │ business          │ │ source app →         │
     │ relationships + │ │ aggregated    │  │ processes +       │ │ data object →        │
     │ interfaces +    │ │ by business   │  │ the apps that     │ │ target app           │
     │ flows each their│ │ domain        │  │ support them      │ │                      │
     │ own edge        │ │               │  │                   │ │                      │
     └───────────────┘ └──────────────┘  └─────────────────┘ └───────────────────┘
```

A broken reference (an id that's pointed at but doesn't exist as a real record) is never
silently dropped — it shows up as a dashed "ghost" placeholder node in whichever frame
references it, so the diagram tells the truth about what's actually wrong with the data.

---

## 5. How a problem gets found and shown to the user

```
┌───────────────────┐   ┌───────────────────┐
│  CanonicalModel      │   │  Dependency graph    │
└──────────┬─────────┘   └──────────┬─────────┘
            └────────────┬──────────┘
                          ▼
     18 independent detectors (insight/detector/*)
     ┌────────────────────────────────────────────────────┐
     │ OrphanApplicationDetector      HubDetector             │
     │ CircularDependencyDetector     MissingOwnerFieldDetector│
     │ LifecycleRisk...Detector ×2    ...13 more                │
     └──────────────────────┬───────────────────────────────┘
                              ▼
                   List<Finding>
                   type + severity + message + relatedEntityIds
                              │
                              ▼
                   GET /api/insights
                              │
          ┌───────────────────┼───────────────────┐
          ▼                    ▼                    ▼
  DashboardCards        InsightsPanel        Node/Edge popups
                                              (badges + full message text)
```

Two separate things can look similar but aren't mixed together: **findings** (what the 18
detectors compute fresh every time) vs. **declared gaps** (the dataset's own
`KnownDataQualityGaps` sheet — a pre-existing, partial list). `GapComparisonService`
compares the two ("5 declared, 12 more detected") so the AI summary can say something
meaningful about both.

---

## 6. Frontend: the screens a user moves through

```
                         ┌──────────────┐
                    ┌───▶│ UploadPage     │◀────────────────────┐
                    │    └──────┬───────┘                        │
        pick a genuinely       │ file parses                    │
        broken file            │ (even with issues)              │ "Upload a
        (error shown           ▼                                  │  different file"
        inline, stays   ┌──────────────────┐                     │
        here) ──────────│ ValidationSummary  │─────────────────────┘
                         └──────┬───────────┘
                                │ "Continue" / "Continue anyway"
                                ▼
                         ┌──────────────┐
                         │ LoadingScreen  │   ~3.5s cosmetic transition
                         └──────┬───────┘
                                ▼
                         ┌──────────────┐
                         │  Workspace     │
                         └──────────────┘
```

- **UploadPage** — the only place a truly unusable file (wrong format, corrupt) is ever
  surfaced; it's caught before a model even exists.
- **ValidationSummary** — always shown once a file parses, even if there are zero issues.
  Splits problems into "structural — needs a new file" vs. "data-quality — already
  captured, browsable in the workspace."
- **LoadingScreen** — purely cosmetic, not gated on real data-readiness.
- **Workspace** — the actual app: graph canvas, filters, insights panel, popups, export.

---

## 7. Frontend: component & data flow inside the Workspace

```
┌──────────────────────────┐
│  App.jsx                   │  owns top-level state
│  (top-level state)         │
└──────────────┬─────────────┘
                ▼
┌──────────────────────────────────────────────────────────────────┐
│  Workspace                                                          │
│                                                                     │
│   refreshKey, frame, filters ─▶ useGraphData ─▶ GET /api/graph/:frame │
│                                  useInsights   ─▶ GET /api/insights    │
│                                  useSummary    ─▶ GET /api/summary     │
│                                  useFilterOptions ─▶ GET /api/filters  │
│                                  useGapComparison ─▶ GET /api/insights/gaps
│                                                                     │
│   FrameTabs (application / domain / process / infoflow)             │
│   FilterPanel     ExportButton                                      │
└──────────────┬───────────────────────────────────────────────────┘
                ▼
        services/api.js  (one function per endpoint)
                │
                ▼
        graphAdapter.js — GraphDto → Cytoscape elements
                │
      ┌─────────┼──────────────────┐
      ▼         ▼                  ▼
 GraphCanvas  DashboardCards   InsightsPanel
      │
      ├─ tap node  ─▶ NodePopupDialog → NodeDetail
      ├─ tap edge  ─▶ EdgePopupDialog
      └─ anchor picked ─▶ AnchorPicker (scoped context diagram)
```

Every data-fetching hook follows the same shape: `loading` / `error` / `data`, keyed off a
`refreshKey` so a re-upload can force everything to refetch. `graphAdapter.js` is the one
place backend `GraphDto` JSON turns into Cytoscape's `{ data: {...} }` element format —
nothing else in the frontend talks to the raw API shape directly.

---

## 8. Full end-to-end: from click to diagram

```
User                         Frontend                          Backend
 │                               │                                  │
 │ uploads dataset               │                                  │
 │──────────────────────────────▶│                                  │
 │                               │  POST /api/upload                │
 │                               │─────────────────────────────────▶│
 │                               │◀─────────────────────────────────│ ValidationReport
 │◀──────────────────────────────│ ValidationSummary screen         │
 │ clicks Continue               │                                  │
 │──────────────────────────────▶│                                  │
 │◀──────────────────────────────│ LoadingScreen (~3.5s)             │
 │                               │  GET /api/graph/application       │
 │                               │─────────────────────────────────▶│
 │                               │  GET /api/insights                │
 │                               │─────────────────────────────────▶│
 │                               │  GET /api/summary                 │
 │                               │─────────────────────────────────▶│
 │                               │  GET /api/filters                 │
 │                               │─────────────────────────────────▶│
 │                               │◀─────────────────────────────────│ GraphDto + findings
 │◀──────────────────────────────│ Workspace renders                │  + summary + filter options
 │                               │ (graph, dashboard, insights panel)│
 │                               │                                  │
 │ clicks an application node    │                                  │
 │──────────────────────────────▶│  GET /api/node/:id/impact        │
 │                               │─────────────────────────────────▶│
 │                               │◀─────────────────────────────────│ upstream/downstream
 │◀──────────────────────────────│ highlight + NodePopupDialog      │  blast radius
 │                               │ (fields, ownership, declared     │
 │                               │  gaps, connections, findings)     │
 │                               │                                  │
 │ clicks "Simulate retiring     │                                  │
 │  this app"                    │  GET /api/node/:id/simulate-removal│
 │──────────────────────────────▶│─────────────────────────────────▶│
 │                               │◀─────────────────────────────────│ new problems this
 │◀──────────────────────────────│ shown inline in the popup        │  would create/resolve
 │                               │                                  │
 │ clicks Export                 │  GET /api/export?type=pptx       │
 │──────────────────────────────▶│─────────────────────────────────▶│
 │                               │◀─────────────────────────────────│ file download
 │◀──────────────────────────────│                                  │
```

---

## 9. Backend folder structure

```
backend/src/main/java/com/vw/eacontext/
├── EaContextApplication.java     Spring Boot entry point
│
├── ingestion/                    File → CanonicalModel
│   ├── ExcelEaDataParser.java    .xlsx via Apache POI
│   ├── CsvEaDataParser.java      zip-of-CSVs via Commons CSV
│   ├── JsonEaDataParser.java     .json via Jackson
│   └── IngestionSupport.java     shared header-matching, enum parsing, notes
│
├── model/                        The canonical domain (Java records)
│   ├── CanonicalModel.java       the 8 entity lists + convenience accessors
│   ├── Application.java, Relationship.java, Interface.java,
│   │   InformationObject.java, BusinessProcess.java, ProcessMapping.java,
│   │   ApplicationOwnership.java, DataQualityGap.java
│   └── *.java                    enums (BusinessCriticality, LifecycleStatus, ...)
│
├── validation/                   Data-quality checks (never throws)
│   ├── ValidationService.java
│   ├── ValidationReport.java / ValidationIssue.java / Severity.java
│
├── graph/                        Dependency graph + 4 observation frames
│   ├── GraphBuilderService.java  the one analytical JGraphT graph
│   ├── GraphProjectionService.java   application/domain/process/infoflow views
│   ├── ApplicationEdgeAssembler.java  relationships+interfaces+flows → edges
│   ├── GhostReferenceResolver.java    tracks ids referenced but never defined
│   ├── GraphScopeService.java    "scoped context diagram" (anchor + depth)
│   ├── ImpactAnalysisService.java     blast radius
│   └── CycleDetectionService.java
│
├── insight/                      Architectural-pattern detection
│   ├── InsightService.java       runs every Detector, collects Findings
│   ├── Detector.java             the pluggable interface
│   ├── detector/                 18 independent implementations
│   └── GapComparisonService.java declared vs. detected gaps
│
├── ai/                           Natural-language summary
│   ├── SummaryGenerator.java     interface
│   ├── TemplateSummaryGenerator.java   default, deterministic
│   └── AzureOpenAiSummaryGenerator.java   optional, "ai" profile
│
├── api/                          REST layer
│   ├── EaController.java         every /api/* endpoint
│   ├── EaContextService.java     orchestrates upload → parse → validate → cache
│   ├── SessionModelStore.java    the one in-memory session (model + graph + findings)
│   ├── ExportService.java + DrawioExportService.java + PlantUmlExportService.java
│   ├── FilterOptionsService.java
│   └── SampleDataInitializer.java     auto-loads the bundled sample on startup
│
├── dto/                          Frontend-facing response shapes
│   └── GraphDto, GraphNode, GraphEdge, FilterOptions, ImpactAnalysisResult, ...
│
├── config/                       @ConfigurationProperties + wiring
│   └── EaIngestionProperties.java (the big one — every column-mapping default)
│
└── exception/                    Custom exceptions + @RestControllerAdvice
    └── GlobalExceptionHandler.java   exception type → HTTP status, one place
```

---

## 10. Frontend folder structure

```
frontend/src/
├── main.jsx                      Vite entry point
├── App.jsx                       Top-level state machine (see §6) + Workspace layout
│
├── components/
│   ├── UploadPage.jsx / UploadButton.jsx      file picker, upload-flow shell
│   ├── ValidationSummary.jsx     post-upload structural-vs-data-quality screen
│   ├── LoadingScreen.jsx         cosmetic transition into the workspace
│   ├── GraphCanvas.jsx           Cytoscape.js wrapper — layouts, tap handlers,
│   │                             highlight/blast-radius logic
│   ├── GraphLegend.jsx, FrameTabs.jsx, AnchorPicker.jsx, FilterPanel.jsx
│   ├── NodePopupDialog.jsx → NodeDetail.jsx    application/domain/process/
│   │                             informationObject node detail popup
│   ├── EdgePopupDialog.jsx       relationship/interface/flow edge detail popup
│   ├── DashboardCards.jsx        the 18-finding-type severity dashboard
│   ├── InsightsPanel.jsx, ArchitectureHealth.jsx   right-side AI summary panel
│   ├── ExportButton.jsx          PNG/PDF/PPTX download
│   └── Toast.jsx, HorizontalScroller.jsx   small shared UI primitives
│
├── hooks/                        One hook per backend endpoint
│   ├── useGraphData.js, useInsights.js, useSummary.js,
│   │   useFilterOptions.js, useGapComparison.js
│   └── (each: { data, loading, error }, keyed off a refreshKey)
│
└── services/                     Pure functions, no React
    ├── api.js                    axios wrapper, one function per endpoint
    ├── graphAdapter.js           GraphDto → Cytoscape elements
    ├── insightAdapter.js         Finding[] → per-node issue classes / badges
    ├── filterState.js            client-side filter shape + server-filter mapping
    └── graphViewport.js          camera fit/zoom helpers
```

---

## 11. A few concepts worth knowing before reading the code

- **Canonical model** — every parser (Excel/CSV/JSON), no matter how different the source
  file looks, produces the exact same `CanonicalModel` shape. Nothing downstream of
  `ingestion/` ever needs to know which file format was uploaded.
- **Ghost reference** — an id that's pointed at (a relationship's target, an interface's
  consumer, ...) but has no row of its own anywhere in the dataset. Never dropped — always
  rendered as a placeholder node, both because it's real information and because hiding it
  would be exactly the kind of "silently invent nothing's wrong" behavior the project
  intentionally avoids.
- **Observation frame** — one of the four "ways to look at the same data" (§4). Frame-
  specific ids (a domain node's id, say) never carry meaning outside their own frame.
- **Finding vs. declared gap** — a *finding* is computed fresh, every time, by one of the 18
  detectors. A *declared gap* is a row from the source dataset's own
  `KnownDataQualityGaps` sheet — a static, partial, human-curated list the dataset ships
  with. They're deliberately never merged into one list; `GapComparisonService` is the one
  place that talks about both at once.
- **Session store** — there is exactly one active dataset at a time, held in memory
  (`SessionModelStore`). Uploading a new file replaces it entirely; nothing is persisted to
  disk or a database.