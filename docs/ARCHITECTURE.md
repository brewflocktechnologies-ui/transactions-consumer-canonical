# Architecture — `transactions-consumer-canonical`

**Audience:** Engineering manager / tech leadership · review of the current design
**Author:** Platform team
**Status:** Production-grade POC, both API surfaces live

---

## 1. Executive summary

We have built a single Spring Boot service that persists a **canonical Send-Transactions schema** to Oracle through three complementary surfaces:

| Surface | Mechanism | Purpose |
|---|---|---|
| **v1 REST** | Typed POJOs, hand-written SQL, full Swagger schema | Stable contract for existing callers; preserves type-safety |
| **v2 REST** | YAML-metadata-driven engine, generated SQL, `Map<String,Object>` payloads | **Adding a new column = 1 YAML line + DBA DDL. Zero code change, zero rebuild.** |
| **Kafka consumer** | YAML-driven canonical mapping pipeline | **Adding a new event type = 1 YAML file. Zero code change, zero rebuild.** |

All three surfaces share one datasource, one error-handling layer, one observability stack, and one deployment. The v2 engine and the Kafka mapping engine together are roughly **12 generic classes** that replace what would otherwise be **~30 hand-written classes per table** with Spring Data JPA + ModelMapper.

The architectural payoff is a measurable reduction in **lead-time-for-change**: a new column or event type lands in one PR touching one YAML file, versus a JPA approach that requires editing the entity, repository, mapper, service, and DTOs in lockstep.

---

## 2. Goals

| # | Goal | How we achieve it |
|---|---|---|
| G1 | One canonical schema for downstream consumers | 5 tables (`SEND_TRANSACTIONS` + 4 children) modelled once, served via both API versions and the Kafka consumer |
| G2 | Add a new column without a code release | Metadata-driven v2 engine — YAML descriptor drives SQL, validation, mapping |
| G3 | Adding new tables should reuse the same engine | Generic `MetadataTransactionService` works for any table whose YAML is loaded |
| G4 | Preserve existing client contracts during evolution | Keep v1 typed surface live indefinitely alongside v2 |
| G5 | DBA team owns schema lifecycle, not the application | App does **no** DDL/DML at startup. Flyway intentionally not used. DBA pipeline applies migrations |
| G6 | Production-grade ops profile | Externalized config, masked errors, HikariCP tuned, MDC trace IDs, Prometheus metrics, profile separation, hardened Docker image |
| G7 | Add a new event type without a code release | Drop a YAML file under `canonical-mappings/` — `CanonicalMappingRegistry` picks it up on restart. `pipeline: CLRG_SETLMT` routes to the 5th-table path; absent = standard 4-table path |

---

## 3. Architecture overview

```
                ┌────────────────────────── HTTP ──────────────────────────┐
                │                                                          │
                ▼                                                          ▼
    ┌──────────────────────────┐                  ┌────────────────────────────────┐
    │ /api/v1/send-transactions│                  │ /api/v2/{alias}/{id}           │
    │   (typed, schema-driven) │                  │ /api/v2/_metadata[/{alias}]    │
    │ SendTransactionController│                  │ MetadataTransactionController  │
    └────────────┬─────────────┘                  └────────────────┬───────────────┘
                 │                                                  │
                 ▼                                                  ▼
    ┌─────────────────────────┐                ┌────────────────────────────────────┐
    │ SendTransactionService  │                │ MetadataTransactionService         │
    │ (hand-written mapping)  │                │ (generic parent + children orches.)│
    └────────────┬────────────┘                └────────────────┬───────────────────┘
                 │                                              │
                 ▼                                              ▼
    ┌─────────────────────────┐                ┌────────────────────────────────────┐
    │ Send*RepositoryImpl × 4 │                │ GenericTableRepository             │
    │ (embedded MERGE SQL)    │                │  - SqlBuilder    (per-table cache) │
    │ Send*RowMapper × 4      │                │  - GenericRowMapper                │
    └────────────┬────────────┘                │  - ValueConverter (Boolean⇄int…)   │
                 │                             │  - MetadataValidator               │
                 │                             └────────────────┬───────────────────┘
                 │                                              │
                 │   shared NamedParameterJdbcTemplate          │
                 └──────────────────────┬───────────────────────┘
                                        │
                                        ▼
                                 ┌─────────────┐
                                 │  HikariCP   │  pool, leak detection, validation
                                 └──────┬──────┘
                                        ▼
                                ┌────────────────┐
                                │  Oracle XE     │  schema owned & migrated by DBA
                                └────────────────┘

    Cross-cutting:
    - RequestIdFilter          → MDC traceId + X-Request-ID header on every request
    - GlobalExceptionHandler   → masked 500s, DataAccessException isolation, fieldErrors map
    - Actuator + Micrometer    → /health (liveness/readiness), /prometheus, HTTP SLO histograms
    - Springdoc OpenAPI        → /swagger-ui.html, /v3/api-docs (v1 typed schemas)
    - MetadataRegistry         → classpath:metadata/*.yaml loaded & validated at @PostConstruct
```

### 3.1 REST API surfaces — component responsibilities

| Layer | v1 (typed) | v2 (metadata-driven) |
|---|---|---|
| Controller | `SendTransactionController` — read-only `GET /api/v1/send-transactions/{tranId}` returning a typed `SendTransactionResponse` | `MetadataTransactionController` — read-only `GET /api/v2/{alias}/{id}` returning `Map<String,Object>` |
| Service | `SendTransactionServiceImpl` — orchestrates 4 hand-written repos, has 6 hand-written `toXxxModel` / `toXxxResponse` methods (~300 LOC) | `MetadataTransactionService` — single orchestrator iterating `parent.children` from metadata; no per-table branches |
| Repository | `Send{Transaction,TranDtl,RecipDtl,TranAddrDtl}RepositoryImpl` — each contains a 60-100 line MERGE SQL string + a `toParams` with explicit `java.sql.Types.*` per column | `GenericTableRepository` — one class; `SqlBuilder` generates MERGE/SELECT/DELETE from `TableMetadata`; `Converters` resolves per-column transformers |
| Row mapping | 4 hand-written `RowMapper<T>` classes | `GenericRowMapper` — reads each column per its `sqlType` flag, applies `ValueConverter.fromJdbc` |
| Validation | Jakarta `@NotNull`/`@NotBlank`/`@Size` on DTO fields | `MetadataValidator` consults YAML `required` / `maxLength` / `pattern` flags |

---

### 3.2 Kafka Consumer Pipeline

A second inbound surface runs alongside the REST APIs. A `@KafkaListener` on the `transactions` topic drives a YAML-configured canonical mapping pipeline:

```
  Kafka topic: transactions
         │
         ▼
┌──────────────────────────────────────────────────────────────────────┐
│  KafkaCanonicalConsumer  (8-step pipeline)                           │
│                                                                      │
│  1. Deserialise  raw JSON          →  EventEnvelope                  │
│  2. Check        ignore flag          skip if true (flagged upstream)│
│  3. Route        eventName         →  EventTypeMapping  (YAML)       │
│  4. Evaluate     rules             →  allowedSources / operations    │
│  5. Sanitize     eventPayload         4-strategy rectification       │
│  6. Deserialise  eventPayload      →  Map&lt;String,Object&gt; (case-insens.)│
│  7. Map fields   (reflection)      →  canonical DTO                  │
│  8. Persist      to Oracle                                           │
└──────────────────────────┬───────────────────────────────────────────┘
                           │
          pipeline: (from YAML EventTypeMapping)
                           │
              ┌────────────┴────────────────────┐
              │                                 │
      absent / null                      "CLRG_SETLMT"
              │                                 │
              ▼                                 ▼
  SendTransactionService               ClearingEventService
  .upsert(tranId, req)                 .upsertClearing(req)
  Standard 4-table path                .upsertSettlement(req)
  (SEND_TRANSACTIONS family)           5th-table path
                                       (SEND_TRAN_CLRG_SETLMT)
```

#### Component responsibilities

| Component | Role |
|---|---|
| `KafkaCanonicalConsumer` | Orchestrates the 8-step pipeline; routes to 5th-table path when `mapping.getPipeline()` equals `"CLRG_SETLMT"` |
| `CanonicalMappingRegistry` | Loads all `classpath:canonical-mappings/*.yaml` at `@PostConstruct`; builds a lookup index by `eventName` and `eventType` |
| `CanonicalRuleEngine` | Evaluates `rules.allowedEventSources` and `rules.allowedOperations` per mapping; filters messages before payload deserialization |
| `EventPayloadSanitizer` | Applies four progressive rectification strategies (trim → unwrap double-serialized JSON → lenient re-parse) to the raw `eventPayload` string |
| `CanonicalMappingEngine` | Map-based field mapper. Reads source values from the case-insensitive payload `Map` via dot-notation paths (e.g. `account.eligible`) and writes to a canonical `Map<String,Object>` keyed by the `jsonName` of each metadata column. Type coercion happens later at JDBC bind time via `ValueConverter`. Source-specific overlays (`sourceMappings:`) applied last on top of common mappings. |
| `CaseInsensitiveJsonMap` | Utility that recursively wraps a Jackson-deserialised payload `Map`/`List` tree into `LinkedCaseInsensitiveMap` instances so engine lookups are case-insensitive throughout. |
| `ClearingEventService` | 5th-table path — verifies the parent `SEND_TRANSACTIONS` row exists, then MERGEs into `SEND_TRAN_CLRG_SETLMT` |

#### YAML-driven event type onboarding

Each event type is a YAML file under `classpath:canonical-mappings/`. No Java changes are required to add a new event type:

```yaml
# Example: adding a new standard (4-table) event type
eventType: REFUND
tranIdSource: tranId
tranType: REFUND
# pipeline: absent → standard 4-table path

rules:
  allowedEventSources: [REFUND_SERVICE]
  allowedOperations:   [A, U]

eventNames:
  - REFUND_INITIATED
  - REFUND_COMPLETED

transaction:
  - { source: tranAmt,  target: tranAmt }
  - { source: curCode,  target: tranAmtCurr }

tranDtl:
  - { source: refRqst,  target: bncGtwyRqst }

sourceMappings:           # optional: per-source field overrides
  AIS_SERVICE:
    transaction:
      - { source: networkSrc, target: ntwrkCd }
```

| `pipeline:` value | Route |
|---|---|
| absent / `null` | Standard 4-table path (`SendTransactionService.upsert`) |
| `CLRG_SETLMT` | 5th-table path (`ClearingEventService`); mapping driven by the `clrgSetlmt:` block |

**To add a standard event type:** drop a YAML file. **To add a 5th-table event type:** drop a YAML file with `pipeline: CLRG_SETLMT`. Zero Java changes either way.

#### Source-specific overlays

The optional `sourceMappings:` block allows different upstream producers sending the same `eventName` to use different source field names. Common mappings run first; the source-specific block (matched case-insensitively on `EventEnvelope.eventSource`) overlays / supplements them. Absent source fields in the JSON are silently skipped — the target retains its value from the common pass.

```yaml
sourceMappings:
  SEND_COMMON_SERVICES:
    transaction:
      - { source: network,    target: ntwrkCd }
  AIS_SERVICE:
    transaction:
      - { source: networkSrc, target: ntwrkCd }
```

---

## 4. Key design decisions

### 4.1 Canonical schema design

The five tables represent **one canonical view of a Send-Transaction** that downstream consumers can rely on regardless of upstream source format:

| Table | Cardinality | Purpose |
|---|---|---|
| `SEND_TRANSACTIONS` | parent | Identity, status, amounts, network codes, audit |
| `SEND_TRAN_DTL` | 1:1 child | Acceptance / merchant detail, **CLOB payloads** (raw request/response) |
| `SEND_RECIP_DTL` | 1:1 child | Sender + recipient PII (names, DOB, addresses, **CLOB govt-id-URI**) |
| `SEND_TRAN_ADDR_DTL` | 1:many child | Address graph (sender / recipient / billing variants) |
| `SEND_TRAN_CLRG_SETLMT` | 1:1 child | Clearing & settlement leg — populated by CLEARING / SETTLEMENT Kafka events after the parent row exists; mapped via `pipeline: CLRG_SETLMT` YAML events |

Design choices that make this "canonical":

- **Wide, sparse tables** instead of polymorphic JSON blobs — every column is queryable, indexable, and reportable.
- **No DB-side `ON DELETE CASCADE`** — deletion order is enforced by the service layer for explicit auditability.
- **MERGE-based upsert semantics with `COALESCE` null-guard**: incoming `null` for a field *preserves* the existing DB value. Allows partial upserts as data trickles in across events (typical for canonical sinks where multiple producers contribute different columns at different times).
- **1:many addresses use MERGE-by-id + prune** (`DELETE … NOT IN (:keepIds)`), so the address list mirrors what the client sends without requiring a synthetic version column.
- **`SYSTIMESTAMP` for audit columns** (`CRTE_TS`, `UPDT_TS`, `RPLCTN_UPDT_TS`) — DB-side clock, never trusts client timestamps.

### 4.2 Metadata-driven engine

The v2 controller, service, repository, row-mapper, and validator are **all generic**. Per-table behaviour is encoded in YAML descriptors loaded once at boot:

```yaml
# src/main/resources/metadata/send_transactions.yaml  (excerpt)
name: SEND_TRANSACTIONS
schema: SEND_TXN_OWNER
alias: send-transactions
pk: TRAN_ID
pkJsonName: tranId
defaultOrderBy: TRAN_CRTE_DT DESC
columns:
  - { jsonName: tranId,     dbColumn: TRAN_ID,     sqlType: VARCHAR, maxLength: 50, pk: true, required: true, nullGuard: false }
  - { jsonName: tranAmt,    dbColumn: TRAN_AMT,    sqlType: NUMERIC }
  - { jsonName: nonFinTxn,  dbColumn: NON_FIN_TXN, sqlType: NUMERIC, converter: BOOLEAN_AS_INT }
  - { jsonName: bncGtwyRqst,dbColumn: BNC_GTWY_RQST, sqlType: CLOB, clob: true, nullGuard: false }
  - { jsonName: crteTs,     dbColumn: CRTE_TS,     sqlType: TIMESTAMP, audit: true, readOnly: true }
children:
  - { jsonName: tranDtl,  tableRef: SEND_TRAN_DTL,      cardinality: ONE_TO_ONE,  childKey: TRAN_ID }
  - { jsonName: addrDtl,  tableRef: SEND_TRAN_ADDR_DTL, cardinality: ONE_TO_MANY, childKey: TRAN_ID, idJsonName: id, generateIdIfMissing: true }
```

**What the metadata controls:**

| YAML flag | Effect on generated SQL / behaviour |
|---|---|
| `pk: true` | Used in `MERGE … ON (t.PK = src.PK)`, excluded from `UPDATE SET` |
| `nullGuard: true` (default) | `COL = COALESCE(:p, t.COL)` — incoming null preserves DB |
| `nullGuard: false` | `COL = :p` — incoming null overwrites to null |
| `clob: true` | Emits `CASE WHEN :p IS NOT NULL THEN TO_CLOB(:p) ELSE t.COL END` and binds as `Types.VARCHAR` (Oracle COALESCE cannot mix VARCHAR2 bind with CLOB column) |
| `audit: true` | Excluded from parameter binding; SQL emits `SYSTIMESTAMP` literal |
| `readOnly: true` | Appears in SELECT only |
| `insertOnly: true` (e.g. `CRTE_USER_NAM`) | In `INSERT VALUES`, not in `UPDATE SET` |
| `required: true` | `MetadataValidator` returns 400 if null |
| `maxLength` / `pattern` | Validator constraints |
| `converter: BOOLEAN_AS_INT` | Java `Boolean` ↔ DB `NUMBER(1,0)` |

**Startup-time safety nets** (`MetadataRegistry.validate()`):
- Every table must declare exactly one `pk: true` column
- `dbColumn` and `jsonName` are unique per table
- `clob: true` must be paired with `nullGuard: false` (Oracle type-coercion restriction)
- Every `children[].tableRef` must resolve to another loaded table
- Unknown `sqlType` aborts startup with a clear message

The application **fails fast** on any YAML problem rather than allowing silently corrupt data through.

### 4.3 Why Spring JDBC (not JPA / Hibernate)

| Decision factor | Spring JDBC choice |
|---|---|
| **Control of MERGE semantics** | We hand-write the MERGE statement (or generate it via metadata) — JPA's `merge()` does an INSERT-or-UPDATE that doesn't support per-column COALESCE null-guard. |
| **CLOB handling on Oracle** | The `TO_CLOB(:param)` trick (binding VARCHAR2, letting Oracle promote on insert) is one line in `SqlBuilder` — under JPA you'd need a custom `AttributeConverter` and likely a Hibernate `UserType`. |
| **No object↔relational impedance** | Payloads are `Map<String,Object>` (v2) or plain DTOs (v1). No proxies, no lazy-loading surprises, no second-level cache to invalidate. |
| **Connection-pool predictability** | A single MERGE = a single round-trip. JPA persistence-context flush ordering / cascade semantics can produce surprising query counts. |
| **No schema↔entity sync** | The application has no compile-time coupling to the DB schema. A new DB column is invisible to the JVM until added to a YAML file. |
| **Performance ceiling** | Direct named-parameter binding via `NamedParameterJdbcTemplate` + Oracle JDBC statement cache (size 50, configured in HikariCP). |
| **Operational debuggability** | The SQL we run is the SQL in the code (or in `SqlBuilder` output) — nothing is generated at runtime by an ORM dialect translator. Easier to plan/explain. |

### 4.4 Two-surface API strategy (v1 alongside v2)

We did **not** retrofit v1 to call into the generic engine. Reasons:

- **Risk isolation** — v1 was already in use; rewriting its internals would block v2 delivery on regression testing.
- **Different validation semantics** — v1 uses Jakarta Bean Validation annotations on POJOs (compile-checked). v2 uses YAML-declared constraints. Both produce the same `ErrorResponse` shape via the shared `GlobalExceptionHandler`, so clients don't see a difference, but the internal contracts differ.
- **OpenAPI fidelity** — v1 exposes full request/response schemas in `/v3/api-docs`. v2 returns `Map<String,Object>` so Swagger can't infer columns — mitigated by the `GET /api/v2/_metadata/{alias}` discovery endpoint that returns the same column catalog at runtime.
- **Migration path** — new clients should be onboarded to v2; existing v1 callers continue working unchanged. Eventually v1 can be retired with a deprecation header, no rush.

### 4.5 Deployment & operations (DB owned by DBA, not app)

A deliberate architectural boundary: **the application does not run DDL or seed DML at startup**.

- **No Flyway / Liquibase wired into the boot sequence.** Earlier iterations included Flyway; we removed it after recognising the ops-model mismatch (the DBA team's existing change pipeline already covers Oracle).
- **DBA-owned DDL** lives in `db/` at the project root (reference scripts) and is applied by the DBA team's existing change-management tooling.
- **App-owned responsibility** stops at: "given an already-provisioned schema, serve traffic correctly".
- **Operational benefits**:
  - The app pod can start in any environment without needing DDL privileges
  - Schema changes follow the DBA review/approval workflow (Oracle ACL, audit log, dry-run on staging)
  - No risk of an app deployment accidentally altering production data
  - Multiple app instances starting simultaneously cannot race on `CREATE TABLE`

**Runtime topology:**

```
            ┌─────────────────────┐
            │ DBA change pipeline │  ← schema lives here
            └──────────┬──────────┘
                       │ owns DDL & seed DML
                       ▼
                ┌────────────┐
                │  Oracle    │
                └─────┬──────┘
                      │
              ┌───────┴───────┐
              ▼               ▼
      ┌─────────────┐  ┌─────────────┐
      │  App pod 1  │  │  App pod N  │   ← stateless, horizontally scaleable
      │  (k8s)      │  │  (k8s)      │
      └─────────────┘  └─────────────┘
                      ▲
                      │ liveness/readiness probes hit /actuator/health/*
                      │ Prometheus scrapes /actuator/prometheus
                      │ Trace IDs propagate via X-Request-ID header / MDC
              ┌───────┴────────┐
              │  K8s / Ingress │
              └────────────────┘
```

**Container image** — multi-stage Dockerfile, JRE 25 runtime, non-root user, Spring Boot layered jar (deps/app split for fast incremental rebuilds), container-aware JVM (`MaxRAMPercentage=75`), wget-based readiness HEALTHCHECK.

**Profile separation**:

- `application.properties` — env-overridable defaults
- `application-dev.properties` — DEBUG logging, full error details, all actuator endpoints exposed, permissive CORS
- `application-prod.properties` — WARN-and-above logging (critical: **JDBC parameter logging silenced** to prevent PCI/PII leakage in production logs), masked errors, `/health`+`/info`+`/prometheus` only, empty CORS by default

---

## 5. Comparison: this design vs Spring Data JPA + ModelMapper

Concrete example: **add a new column `EXTRA_NOTE VARCHAR2(100)` to `SEND_TRANSACTIONS`**.

### Files touched

| Layer | Spring Data JPA + ModelMapper | Our metadata-driven design |
|---|---|---|
| DDL | DBA migration | DBA migration |
| Entity | `SendTransaction.java` — add field + `@Column` | (no change) |
| DTO request | n/a — the Kafka pipeline reads the raw JSON payload as a Map, no intermediate request DTO | (no change) |
| DTO response | `SendTransactionResponse.java` — add field | (no change) |
| Mapper | ModelMapper config or MapStruct interface — add mapping (or accept auto-detection) | (no change) |
| Service | Likely no change, *if* ModelMapper is configured to copy all properties | (no change) |
| Repository | `JpaRepository` derives queries from entity — usually no change | (no change) |
| Tests | New unit tests for the field | New unit tests for the field |
| **Total source files edited** | **3–5 Java files + 1 DDL** | **1 YAML + 1 DDL** |
| **Rebuild & redeploy required?** | **Yes** (Java source changed) | **No** (config-only change with hot-reload-capable architecture; restart only) |

### Operational comparison

| Concern | JPA + ModelMapper | This design |
|---|---|---|
| **SQL transparency** | Hibernate translates HQL/JPQL to SQL at runtime; vendor-specific quirks (Oracle dialect bugs, CLOB handling, MERGE absence) require workarounds | SQL is generated once per table at startup by `SqlBuilder`. The exact statement is observable via `log.debug` and is identical every execution. |
| **MERGE upsert with null-guard** | Not natively supported. Workarounds: native query annotated on a method, custom Hibernate event listener, or two-step "find then save" (lost-update prone) | First-class — `COALESCE(:p, t.COL)` emitted per column |
| **CLOB columns** | Requires `@Lob` + `LargeObject` config; null-handling on update is non-obvious (Hibernate may delete-then-insert) | Single `clob: true` flag emits the `TO_CLOB CASE WHEN` pattern correctly |
| **Reflection cost** | Hibernate proxies + bytecode enhancement add startup + per-request overhead | Zero reflection in the hot path — `SqlBuilder` output is cached; row mapper iterates the column list directly |
| **Mapping bugs** | ModelMapper auto-mapping is convention-based; subtle bugs when field types differ (e.g. `Date` vs `LocalDateTime`); silent dropped fields | Explicit `sqlType` per column; `ValueConverter` handles coercions; missing JSON name in payload = `null` (deterministic) |
| **Test surface** | Heavy — entity + DTO + mapper + repository + service test stubs per table; requires test container or H2 + Hibernate to wire | Light — `SqlBuilderTest` proves SQL generation; `MetadataValidatorTest` proves validation; both pure-JVM. Context smoke test on H2 covers wiring. |
| **Onboarding a new table** | Whole stack (entity, repo, mapper, DTO request, DTO response, service method, controller method) | Write one YAML file. Generic controller/service/repo serves it. |
| **Schema drift detection** | Compile-time via entity ↔ DDL mismatch (e.g. `hbm2ddl.validate=true`) | Runtime — first SQL against a wrong column fails with Oracle error; can be promoted to startup-time with a `INFORMATION_SCHEMA` cross-check (planned follow-up) |
| **Library footprint** | Hibernate ORM + JPA API + ModelMapper / MapStruct + their transitive deps | Spring JDBC + Jackson YAML — already present for other reasons |
| **Vendor lock-in** | Strong coupling to Hibernate dialect + JPA spec | Spring JDBC is a thin wrapper over `DataSource`; engine ports easily to any RDBMS by adding a dialect-specific `SqlBuilder` variant |

### Where JPA would still win

To be fair, JPA is the right choice when:

- The domain has **rich object behaviour** (aggregates with invariants, deep inheritance hierarchies) — a relational mapping with no business logic, like a canonical sink, doesn't benefit.
- **Lazy-loaded object graphs** are central to the read path — our read path returns a fixed shape per call, lazy loading would add complexity.
- The team has deep JPA expertise and shallow SQL expertise — we have the opposite.
- The DB is treated as an implementation detail of the application — we treat it as a contract with downstream consumers.

For this service, the metadata-driven approach is the better fit because the domain is **shape-stable in semantics but evolution-prone in schema**: new columns are added often as upstream sources change, but the relational model itself stays the same.

---

## 6. Cross-cutting infrastructure

| Concern | Where |
|---|---|
| Request correlation | `RequestIdFilter` — reads or generates `X-Request-ID`, populates MDC `traceId`, echoes header back |
| Error handling | `GlobalExceptionHandler` — 404 (ResourceNotFound), 400 (Jakarta `@Valid`, `ConstraintViolation`, `MetadataValidationException`), 500 (DataAccessException isolated and masked; generic Exception always returns a generic message — internal details only in server logs) |
| PII protection | `LogSanitizer.maskPayload(...)` for code paths that need to log a payload Map. SQL parameter logging is silenced in `application-prod.properties` because Spring JDBC `DEBUG` would dump card numbers, govt-id URIs, DOBs etc. |
| Connection pool | HikariCP — pool size, leak detection (60s), Oracle statement cache (50), validation query `SELECT 1 FROM DUAL`, auto-commit off |
| Health probes | `/actuator/health/liveness` (process), `/actuator/health/readiness` (DB reachable) — wired to k8s probes |
| Metrics | Micrometer + Prometheus registry; HTTP server metrics include percentile histograms and SLO buckets at 50ms / 100ms / 200ms / 500ms / 1s / 2s / 5s |
| API docs | Springdoc OpenAPI — `/swagger-ui.html`, `/v3/api-docs`. Disabled by default in prod profile |
| CORS | `CorsConfig` — env-driven allow-list, empty in prod by default |
| CI | GitHub Actions — `mvn verify` + optional SonarCloud + JaCoCo coverage upload + Docker image build (no push) |

---

## 7. Trade-offs we have accepted

| Trade-off | Why we accept it | Mitigation |
|---|---|---|
| v2 loses compile-time field references | The whole point — that's what enables zero-code column additions | `MetadataValidator` runs at every write; planned follow-up: startup DDL cross-check that compares YAML `dbColumn` set against `INFORMATION_SCHEMA` |
| v2 loses IDE auto-complete for payload keys | Same reason | `GET /api/v2/_metadata/{alias}` returns the column catalog; we plan a TypeScript/Java client generator that consumes the same YAML |
| v2 loses Swagger schema for payloads | Map-based by design | `_metadata` discovery endpoint + v1 surface still has typed Swagger for clients that need it |
| Composite primary keys not supported in v2 | Out of scope for current tables (every PK is single-column) | YAML schema reserves `pk` as a single string today; future change would make it `List<String>` |
| JSON ordering depends on YAML column order | `LinkedHashMap` propagates order — acceptable since clients use field names not positions | Verified in tests; documented |
| No transactional outbox / CDC | Not in MVP scope | Roadmap item — add once a downstream consumer requires it |

---

## 8. Roadmap / follow-ups

1. **AuthN/AuthZ** — front the service with the org's API gateway (OAuth2/JWT) or add Spring Security with a configurable `WebSecurityConfigurer`.
2. **Rate limiting** — Bucket4j or gateway-tier policy.
3. **Resilience** — Resilience4j retry on transient `DataAccessResourceFailureException` (e.g. connection pool starvation under burst).
4. **Schema↔YAML drift detector** — startup-time check that every column listed in YAML actually exists in the DB; warn (or fail) on mismatch.
5. **OpenAPI generation from metadata** — dynamically synthesize `Schema` objects for v2 endpoints so the discovery information shows up in Swagger UI.
6. **Testcontainers integration tests** — replace the H2 smoke test with an Oracle XE container run nightly for full SQL coverage (MERGE, TO_CLOB, FK cascade).
7. **Optional v1 retirement plan** — once all downstream callers migrate to v2, the ~30 hand-written v1 classes can be deleted (~2 days of work).

---

## 9. One-page summary (for slide deck)

> **Problem.** Schema evolves rapidly as new upstream sources land; classic JPA + ModelMapper requires Java edits in 4-6 files for every new column, blocking on a code release.
>
> **Solution.** YAML metadata describes each table. A generic Spring JDBC engine (`SqlBuilder` + `GenericTableRepository` + `MetadataValidator`) generates MERGE / SELECT / DELETE at startup, caches per table, and serves any registered table through a single REST surface (`/api/v2/{alias}/{id}`).
>
> **Outcome.** Adding a new column = **1 line of YAML + 1 DBA DDL**. Zero Java edits, zero rebuild for the schema change. The existing typed v1 surface stays live in parallel so no client is forced to migrate.
>
> **Ops.** DBA team owns the schema (no Flyway in the app). App is a stateless container with Prometheus metrics, MDC trace propagation, masked errors, and dev/prod profile separation that silences PII-leaking JDBC parameter logs in production.
