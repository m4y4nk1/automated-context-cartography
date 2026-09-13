# Task: Redesign the Context Cartography backend to fully support the Auriga Motors synthetic EA dataset

## Context

This is a Spring Boot 3 (Java 21) + React 19 application ("Context Cartography") that ingests
enterprise-architecture datasets, builds a dependency graph, detects architectural risk patterns,
and renders interactive context diagrams. Full current architecture is documented in
`ARCHITECTURE.md` at the repo root — read it first.

We're adapting the backend to fully and correctly support a hackathon dataset: a synthetic,
LeanIX-style, 7-sheet Excel workbook for a fictional company "Auriga Motors"
(`Auriga_Motors_Synthetic_EA_Dataset.xlsx`), documented in full in
`Synthetic_Dataset_Report_Auriga_Motors.docx` (schema, keys, join logic, full anomaly catalogue)
and `Participant_Guide_Context_Diagram_Hackathon.docx` (functional requirements / Definition of
Done). Both docs are in `/mnt/user-data/uploads/` — read them in full, twice, before writing any
code. Do not skim — the anomaly catalogue in particular (section 4 of the dataset report) has
precise join logic per scenario that must be implemented exactly, not approximated.

**Read these three files first, in this order, before touching any code:**
1. `Synthetic_Dataset_Report_Auriga_Motors.docx` — the data contract (schema, keys, anomaly catalogue)
2. `Participant_Guide_Context_Diagram_Hackathon.docx` — functional requirements (F1–F6, Definition of Done)
3. `ARCHITECTURE.md` — current system design

## Scope of this task

**This is a full backend redesign, not a patch.** The current canonical model (5 simplified
entities, Interface-as-edge graph, 5 insight detectors) does not match the Auriga schema closely
enough to bolt fields onto. Go through every backend package listed below and rebuild the pieces
that need rebuilding, while reusing every part of the existing design that still fits: the
layered architecture, the config-driven column mapping in `application.yml`, the parser adapter
pattern, the facade pattern in `EaContextService`, the non-blocking validation philosophy, the
immutable-record model style, and the four-frame projection concept. **Reuse aggressively where
the existing abstraction is right; replace aggressively where the existing model is just too thin
for the real dataset.** Don't leave dead code behind — if a class's responsibility is fully
superseded by a new one, delete it and update every caller, don't leave two parallel paths.

## Hard constraints — read carefully

- **Do not hardcode to any specific record ID, name, or count from the sample dataset.** Judges
  re-run the solution against a *modified* copy of the data with different IDs and additional
  scenarios. Every detector must work by logic/pattern, not by matching known IDs or magic numbers
  copied from the sample workbook (e.g. no `if (appId.equals("APP-0001"))`, no `if (count == 46)`).
- **Never fail/throw on bad data.** Broken references, ghost IDs, and missing fields must become
  findings/validation issues, not exceptions. A ghost ID (e.g. `APP-9001`) referenced by another
  sheet but absent from `Applications` is an intentional test case, not a bug to crash on. The
  pipeline must always produce a usable model + report, even on maximally broken input.
- **Preserve format-agnosticism.** JSON, Excel, and CSV parsers must all stay in sync with the
  canonical model — a change to what the model captures must be implemented in all three
  `EaDataParser` implementations, not just Excel, even if only Excel is used in the demo.
- **Everything must be config-driven, not hardcoded.** Sheet names, column names, and thresholds
  belong in `application.yml`/`EaIngestionProperties`/`InsightProperties`, matching the existing
  externalized-configuration principle.
- Treat `Confidential`, `Confidential/PII`, and `Restricted/PCI` values on
  `InformationObjects.Classification` as sensitive throughout — in DTOs, logs, and exports, not
  just as a display label.
- Favor small, focused, well-named classes over adding more branching to existing large ones —
  e.g. each new insight detector should be its own strategy/class behind `InsightService`, not a
  new `if` block in one giant method (see Insight Engine section below for the exact pattern to use).

## Complete backend redesign — package by package

Work through these in order; later packages depend on earlier ones.

### 1. `model/` — canonical domain model (rebuild)

Current entities are too thin for the real dataset. Rebuild as follows, keeping the existing
style (immutable Java records, Lombok `@Builder`, `List.copyOf()` defensive collections, no-null
collections):

- **`Application`** — rebuild with the full field set: `id`, `name`, `description`,
  `businessDomain`, `businessCriticality` (enum: `MISSION_CRITICAL`, `BUSINESS_CRITICAL`,
  `BUSINESS_OPERATIONAL`, `ADMINISTRATIVE`), `lifecycleStatus` (expand enum to `PLAN`,
  `PHASE_IN`, `ACTIVE`, `PHASE_OUT`, `END_OF_LIFE` — this replaces the current
  `ACTIVE/DEPRECATED/EOL/PLANNED` set, so grep for every usage of the old enum values and update
  them), `lifecycleStartDate`, `lifecycleEndDate` (both `LocalDate`, nullable), `hosting` (enum:
  `ON_PREM`, `PRIVATE_CLOUD`, `PUBLIC_CLOUD`, `SAAS`), `vendorType` (enum: `COTS`, `CUSTOM`),
  `costCenter`. Drop the old single `owner` string field and the old single `processId` field —
  ownership and process mapping are now separate joined entities (see below); do not keep a
  parallel denormalized copy that can drift out of sync with them.
- **`Relationship`** (new) — `id`, `sourceApplicationId`, `targetApplicationId`, `relationshipType`
  (enum: `DEPENDS_ON`, `USES`), `dependencyCriticality` (enum: `HIGH`, `MEDIUM`, `LOW`). This is
  the primary dependency edge table — reuse the existing `InterfaceEdge`-style "edge metadata
  wrapper" pattern from the graph package as your model here so `GraphBuilderService` can consume
  it the same way it already consumes interface edges.
- **`Interface`** — rebuild with: `id`, `name`, `providerApplicationId`, `consumerApplicationId`,
  `protocol` (replace `InterfaceType` enum with: `REST_HTTPS`, `SOAP`, `GRAPHQL`, `SFTP_FILE`,
  `KAFKA`, `JDBC`, `GRPC`), `dataFormat` (enum: `JSON`, `XML`, `CSV`, `AVRO`, `EDI`), `frequency`
  (enum: `REAL_TIME`, `NEAR_REAL_TIME`, `HOURLY_BATCH`, `DAILY_BATCH`, `WEEKLY_BATCH`),
  `interfaceStatus` (enum: `ACTIVE`, `DEPRECATED`).
- **`InformationObject`** — rebuild from a near-stub into a full flow entity: `id`,
  `informationObject` (the data object name, e.g. "Customer Data"), `classification` (enum:
  `INTERNAL`, `CONFIDENTIAL`, `CONFIDENTIAL_PII`, `RESTRICTED_PCI`), `sourceApplicationId`,
  `targetApplicationId`, `operation` (enum: `CREATE`, `READ`, `UPDATE`, `REPLICATE`),
  `interfaceId` (nullable FK to `Interface`).
- **`BusinessProcess`** — keep `id`, `name`, add `processDomain`. Remove any assumption of a
  single supporting application; supporting applications now live in `ProcessMapping`.
- **`ProcessMapping`** (new) — `id`, `businessProcessId`, `supportingApplicationId`,
  `roleOfApplication` (enum: `PRIMARY`, `SUPPORTING`), `processCriticality` (reuse the
  `BusinessCriticality`-style enum values: `MISSION_CRITICAL`, `BUSINESS_CRITICAL`,
  `BUSINESS_OPERATIONAL`). This is the many-to-many join table between processes and
  applications — one process can have many supporting apps and one app can support many
  processes; design accordingly (don't collapse it back to a map keyed by a single ID on either
  side).
- **`ApplicationOwnership`** (new) — `id`, `applicationId`, `applicationOwner`,
  `ownerEmployeeId`, `systemCustodian`, `businessOwner` (nullable), `supportGroup`, `department`.
  Independent of `Application` — an app can have zero ownership records, and an ownership record
  can be missing `businessOwner` while still existing. Do not fold this into `Application`.
- **`DataQualityGap`** (new, optional but recommended) — `id`, `gapType`, `entityType`,
  `entityId`, `relatedApplicationId`, `description`, `severity`. Represents the pre-declared,
  partial `KnownDataQualityGaps` sheet, kept separate from your own computed `Finding`s so the
  service layer can later compare "declared" vs. "detected".
- **Remove the standalone `Domain` entity and its ingestion.** Auriga has no Domain sheet —
  domain is just `Application.businessDomain`. Derive domain groupings on the fly in the graph/
  projection layer instead of ingesting/storing a `Domain` list. Update every place that currently
  reads `CanonicalModel.domains()` (or equivalent) to derive from applications instead, and remove
  the field from `CanonicalModel` once nothing depends on it.
- **`CanonicalModel`** — rebuild the record to hold: `applications`, `relationships`,
  `interfaces`, `informationObjects`, `businessProcesses`, `processMappings`,
  `applicationOwnerships`, `dataQualityGaps` (optional). Same defensive-copying/no-null-collection
  discipline as today. This is the object every downstream layer consumes — get its shape right
  before moving on, since ingestion, validation, graph, and insight all key off it.

### 2. `config/` — externalized configuration (extend, reuse pattern)

- Rewrite the `ea.ingestion.*` section of `application.yml` for the new schema: Excel sheet name
  mappings for `Applications`, `Relationships`, `Interfaces`, `InformationObjects`,
  `BusinessProcesses` (now a mapping-table sheet), `ApplicationOwnership`, and (optionally)
  `KnownDataQualityGaps`; JSON root array names and CSV file names for the same set, kept in sync.
  Field-level column mappings for every entity above, replacing the current 5-entity mapping
  block entirely (don't leave the old `domain`/single-process mappings in the file — remove them).
- Extend `EaIngestionProperties` to bind the new YAML shape — reuse its existing
  `@ConfigurationProperties("ea.ingestion")` pattern, just with a richer nested structure.
- Extend `InsightProperties` with any new tunable thresholds the new detectors need (e.g. a
  duplicate-name case-sensitivity flag, or a "lifecycle risk lookahead window" if you choose to
  make one configurable) — follow the existing `hotspot-degree-threshold` precedent rather than
  inlining magic numbers into detector code.
- No changes expected to `CorsConfig` or `AiConfig` — leave them alone.

### 3. `ingestion/` — parsers (rebuild parsing logic, reuse the parser interface & strategy pattern)

- Keep the `EaDataParser` interface (`parse(InputStream) → CanonicalModel`) and the
  strategy-by-file-extension selection in the service layer — this part of the design is correct
  and should not change.
- **`ExcelEaDataParser`** — rebuild sheet-reading logic to cover all seven sheets, including the
  two brand-new ones (`Relationships`, `ApplicationOwnership`) and the restructured
  `BusinessProcesses` (now a mapping table, not a process-with-embedded-app structure) and
  `InformationObjects` (now a full flow record). Reuse the existing POI row-iteration and
  config-driven column lookup utilities in `IngestionSupport` rather than writing new
  per-sheet parsing helpers from scratch — extend `IngestionSupport` if it's missing a generic
  capability you need (e.g. enum-safe string-to-enum parsing with a fallback for unrecognized
  values, since Auriga's enums use display strings like "Business Critical" or "REST/HTTPS" that
  need mapping to your Java enum constants).
- **`JsonEaDataParser`** and **`CsvEaDataParser`** — update in parallel so all three formats
  produce an equivalent `CanonicalModel` from equivalent input; add fixture files for each format
  if none exist, so the parser trio can be tested independently of the Excel workbook.
- **`IngestionSupport`** — this is the shared utility layer; put all new cross-cutting parsing
  concerns here rather than duplicating logic per parser:
  - Config-driven column/field extraction (already exists — extend for new entities).
  - Enum parsing with a documented fallback/`UNKNOWN` value for unrecognized source strings,
    logged as a warning rather than thrown.
  - Foreign-key resolution helper that looks up a target ID in a known-ID set and returns an
    `Optional`/nullable result plus a "was this a ghost reference" flag, so validation and insight
    detectors don't each reimplement ghost-ID detection independently — this is the single
    most-reused piece of logic across the anomaly catalogue (broken references appear in
    Relationships, Interfaces, InformationObjects, and ProcessMappings alike), so build it once
    here and have every parser/validator/detector call it.
  - Date parsing helper for `LifecycleStartDate`/`LifecycleEndDate` (ISO date strings, nullable).

### 4. `validation/` — referential integrity (extend existing service, add rules not new classes)

Keep `ValidationService`, `ValidationReport`, `ValidationIssue`, `Severity` as-is structurally;
add rules to `ValidationService`'s rule set (reusing the `IngestionSupport` FK-resolution helper
from above) for:
- `Relationships.targetApplicationId` → `Applications` (ERROR: broken reference)
- `Interfaces.consumerApplicationId` → `Applications` (ERROR: dangling interface consumer)
- `InformationObjects.sourceApplicationId` / `targetApplicationId` → `Applications` (ERROR: broken
  reference)
- `InformationObjects.interfaceId` → `Interfaces`, when present (WARNING: dangling flow)
- `ProcessMapping.supportingApplicationId` → `Applications` (ERROR: unmapped process application)
- Required-field checks per entity per the dataset report's "Req" column (e.g.
  `Application.businessDomain`, `Application.businessCriticality`, `Interface.interfaceName`,
  `InformationObject.classification`, `BusinessProcess.businessProcessId`/`name` are all marked
  required in the schema — enforce them as WARNING if missing, consistent with the existing
  non-blocking philosophy)

All of this stays WARNING/ERROR on `ValidationReport`, never an exception — this is the same
contract `ValidationService` already has, just with more rules registered.

### 5. `graph/` — graph engine (rebuild the edge model, reuse JGraphT integration)

- **`GraphBuilderService`** — change the primary dependency graph's edge source from `Interface`
  to `Relationship`. Reuse the existing JGraphT directed-pseudograph setup and the
  `InterfaceEdge`-style edge-metadata-wrapper pattern, just retarget it: introduce a
  `RelationshipEdge` wrapper (id, relationshipType, dependencyCriticality) as the primary edge
  type, and keep interface data available either as a second graph or as supplementary edge
  metadata joined in by provider/consumer ID pair, for use in the information-flow projection.
  Handle duplicate/ghost-referencing relationships gracefully exactly as the current
  implementation does for interfaces (reuse that graceful-degradation logic, don't rewrite it from
  scratch).
- **Cycle detection** (new) — add using JGraphT's built-in `org.jgrapht.alg.cycle` utilities
  (e.g. `CycleDetector` for existence, `TarjanSimpleCycles` or `JohnsonSimpleCycles` if you need
  the actual cycle membership for a finding's traceability list). Expose a method like
  `List<List<String>> findCycles(Graph<...>)` that `InsightService` can call.
  Add this as a new small class (e.g. `CycleDetectionService`) rather than growing
  `GraphBuilderService` further — it's a distinct enough responsibility to warrant separation.
- **`GraphProjectionService`** — rebuild the business-process projection to aggregate over
  `ProcessMapping` (many-to-many) instead of a single `processId` per app. Rebuild the domain
  projection to group applications by `businessDomain` attribute instead of a joined `Domain`
  entity/list. Keep the application-view and information-flow-view projection shapes as close to
  today's as the new model allows, since the frontend's `graphAdapter.js` depends on that shape —
  document any shape change clearly if one is unavoidable.
- **`ImpactAnalysisService`** — update blast-radius BFS to traverse the relationship-based graph;
  reuse the existing upstream/downstream traversal logic as-is, just against the new edge source.
  If low-cost, tag each hop in the result with whether it was a `DEPENDS_ON` or `USES` edge.

### 6. `insight/` — detector engine (rebuild as a strategy-per-detector, not one big method)

The current `InsightService` runs five detectors inline. Given the jump to ~18 scenarios, refactor
to a **detector-strategy pattern**: define a small `Detector` interface
(`List<Finding> detect(CanonicalModel model, DependencyGraph graph)`), implement each scenario
below as its own class implementing it, and have `InsightService` hold an injected
`List<Detector>` (Spring will autowire all beans implementing the interface) and simply run all of
them and flatten the results. This keeps each detector independently testable and means adding a
19th scenario later doesn't require touching existing detector code — reuse this pattern instead
of adding more branches to a monolithic method.

Rebuild/replace the current five detectors and add the rest, using the dataset report's section 4
join logic exactly:

| Detector | Logic (reuse `IngestionSupport`'s FK-resolution helper wherever "broken reference" appears) |
|---|---|
| `HubDetector` (rebuild) | In-degree per application in the relationship graph exceeds configurable threshold — reuse existing hotspot config, but switch from total-degree to in-degree per the dataset's definition |
| `CircularDependencyDetector` (new) | Uses graph engine's cycle detection; one `Finding` per distinct cycle, `relatedEntityIds` = every app ID in the cycle |
| `BrokenRelationshipReferenceDetector` (new) | `Relationships.targetApplicationId` not in `Applications` |
| `DanglingInterfaceConsumerDetector` (rebuild from `OrphanInterface`) | `Interfaces.consumerApplicationId` not in `Applications` |
| `BrokenInformationFlowReferenceDetector` (new) | `InformationObjects.targetApplicationId` (and source) not in `Applications` |
| `UnmappedProcessApplicationDetector` (rebuild from `MissingProcessMapping`, reversed direction) | `ProcessMapping.supportingApplicationId` not in `Applications` |
| `DuplicateApplicationDetector` (new) | Group `Applications` by `name`; any group with count > 1 |
| `OrphanApplicationDetector` (new) | Application ID appears in no `Relationship`, `Interface`, `InformationObject`, or `ProcessMapping` on either side |
| `OwnershipRecordMissingDetector` (new) | Application has no row in `ApplicationOwnership` at all |
| `OwnershipPartialGapDetector` (new) | `ApplicationOwnership` row exists but `businessOwner` is blank |
| `MissingOwnerFieldDetector` (rebuild from `OwnershipGap`) | Cross-check: application referenced with no matching `ApplicationOwnership.ownerEmployeeId` |
| `LifecycleRiskCriticalProcessDetector` (new) | `Application.lifecycleStatus == END_OF_LIFE` and it supports (via `ProcessMapping`) a process with `processCriticality == MISSION_CRITICAL` |
| `LifecycleRiskEolProviderDetector` (new) | `Interface.interfaceStatus == ACTIVE` and its `providerApplicationId`'s `lifecycleStatus == END_OF_LIFE` |
| `LifecycleInconsistencyDetector` (new) | `Application.lifecycleStatus == ACTIVE` and `lifecycleEndDate` is in the past |
| `DeprecatedInterfaceInUseDetector` (new) | `InformationObject.interfaceId` resolves to an `Interface` with `interfaceStatus == DEPRECATED` |
| `InterfaceWithoutRelationshipDetector` (new) | Provider/consumer pair in `Interfaces` has no matching source/target pair in `Relationships` |
| `SensitiveDataInsecureFlowDetector` (new) | `InformationObject.classification` in `{CONFIDENTIAL_PII, RESTRICTED_PCI}` and its `Interface` is `SFTP_FILE` protocol and/or `DEPRECATED` status |
| `PhaseOutCriticalPathDetector` (new) | `Application.lifecycleStatus == PHASE_OUT` and supports (via `ProcessMapping`) a `MISSION_CRITICAL` process |

Expand `FindingType` to one value per detector above. Rebuild `Finding` to carry
`List<String> relatedEntityIds` instead of a single `entityId` (cycles and hub findings are
inherently multi-entity) — update every current caller/consumer of `entityId` accordingly, don't
add a second field alongside the old one.

If you ingested `DataQualityGap` in the model step, add a small comparison utility (e.g. in
`InsightService` or a dedicated `GapComparisonService`) that flags which detected findings
correspond to a declared gap vs. which are newly discovered — expose both counts, since the
participant guide explicitly rewards surfacing gaps beyond the pre-declared list.

### 7. `ai/` — summary generation (light touch, reuse as-is)

`SummaryGenerator`/`TemplateSummaryGenerator`/`AzureOpenAiSummaryGenerator` don't need structural
change. Update `TemplateSummaryGenerator`'s template text so it references the new, larger set of
finding types and the new `GraphStats` fields sensibly (e.g. mention hub apps by in-degree,
mention cycle count) rather than only the original five categories.

### 8. `dto/` — API contracts (extend, keep response shapes stable where possible)

- `GraphNode`/`GraphEdge` — extend the `data` map with the new attributes needed for frontend
  styling/filtering (criticality, lifecycle status, hosting, classification, relationship type),
  reusing the existing generic-map-of-attributes shape rather than adding rigid new fields to the
  DTO class itself.
- `Finding` — add `relatedEntityIds: List<String>` (replacing single `entityId`, or keep
  `entityId` as `relatedEntityIds.get(0)` only if you need to avoid a breaking frontend change in
  this pass — prefer the clean break and update the frontend adapter, per the "don't leave two
  parallel paths" rule above).
- `GraphStats` — add fields useful for the new detectors' context (e.g. cycle count, hub count,
  orphan count) so `/api/summary` has richer input.
- Add a `DataQualityGapDto` and, if you built the declared-vs-detected comparison, a small
  `GapComparisonDto` (declaredCount, detectedCount, newlyDetected: List<Finding>).

### 9. `api/` — REST layer & orchestration (extend the facade, keep existing endpoint contracts)

- `EaController` — keep existing endpoints (`/api/upload`, `/api/graph/{frame}`,
  `/api/node/{id}/impact`, `/api/insights`, `/api/summary`, `/api/export`) working with the same
  method/path/response shape where the change is additive (extra fields), and update where the
  response shape fundamentally changed (e.g. `Finding.entityId` → `relatedEntityIds`). Add a new
  endpoint only if genuinely justified — e.g. `GET /api/insights/gaps` returning the
  declared-vs-detected comparison — don't add endpoints speculatively.
- `EaContextService` — this facade's orchestration order (parse → validate → build graph →
  detect insights → cache) stays correct; update the calls inside it to match the new service
  signatures from the packages above. This is the class most likely to need touching in every
  phase without needing a structural rewrite — reuse it as the seam.
- `SessionModelStore` — no structural change expected; verify it still compiles against the new
  `CanonicalModel`/graph/finding types.
- `ExportService` — update text-content generation (PNG/PDF/PPTX) to summarize the new finding
  categories and key application attributes sensibly, reusing the existing rendering code paths
  (`BufferedImage`/`PDDocument`/`XMLSlideShow`) as-is.
- `SampleDataInitializer` — update the bundled sample dataset files
  (`sample_ea_dataset.json`/`.xlsx`) to match the new schema so local dev / auto-load still works;
  base them on a small hand-built fixture that includes at least one instance of every anomaly
  type, not a copy of the real Auriga workbook.

### 10. `exception/` — error handling (no structural change expected)

`GlobalExceptionHandler`, `EaIngestionException`, `EaNotFoundException`, `ModelNotLoadedException`
should not need to change. Verify ingestion failures for genuinely malformed files (not just
ghost-ID references, which are handled as findings, but actually corrupt/unreadable files) still
surface through `EaIngestionException` as before.

## Requirements traceability — confirm every one of these is satisfied before calling it done

Map your implementation back to the participant guide explicitly:

- **F1 (ingest & validate):** all seven sheets parse; every cross-sheet key resolves or is flagged,
  never silently dropped.
- **F2 (observation frame):** application, business process, domain, and information-object frames
  all selectable and correctly scoped.
- **F3 (scoped context diagram):** frame projections return only the relevant neighborhood, not
  the whole graph.
- **F4 (tech-to-business link):** business-process view actually shows supporting applications via
  `ProcessMapping`, not a proxy.
- **F5 (insight layer):** all 18 anomaly-catalogue scenarios have a corresponding detector (see
  table above) — this is the most failure-prone requirement, don't undercount.
- **F6 (regeneratable):** re-run the full pipeline against a hand-modified copy of the sample
  data (different IDs, same anomaly shapes) and confirm every detector still fires correctly —
  add this as an explicit test case, not just a manual check.
- **Governance/traceability:** every `Finding` carries enough `relatedEntityIds` to trace back to
  source records; sensitive classification is respected in DTOs/exports.

## Working style

- Work package by package in the order listed above (model → config → ingestion → validation →
  graph → insight → ai → dto → api → exception); each depends on the ones before it.
- After each package, compile and run the existing test suite (`./mvnw test`) and report what
  passed/failed before moving to the next package.
- Ask me before starting the model package if anything in the dataset report or participant guide
  is ambiguous — don't guess on schema or enum value details.
- Keep diffs scoped per package so changes are reviewable incrementally rather than as one giant
  diff; when you fully replace a class's responsibility, delete the old class and update all
  callers in the same commit rather than leaving it unused.
- Once the backend package-by-package rebuild above is complete and tested, come back to me
  before touching the frontend — we'll scope that as a separate pass (new filters, node detail
  fields, edge styling, expanded dashboard, sensitive-data badges) once the API contract is
  final and stable.
