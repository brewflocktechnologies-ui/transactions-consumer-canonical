# transactions-consumer-canonical

Spring Boot 3.5 / Java 25 service that ingests and serves canonical `SEND_TRANSACTIONS` rows and their child tables on Oracle. Ships with two parallel REST API surfaces — a typed **v1** controller and a fully **metadata-driven v2** controller — plus external DB reference scripts, Swagger/OpenAPI, Prometheus metrics, Docker packaging, and CI.

> Adding a new column to any table is a **one-line YAML edit** + DDL change on the v2 surface. No Java edits, no rebuild.

---

## Table of contents

- [Architecture at a glance](#architecture-at-a-glance)
- [Running locally](#running-locally)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Adding a new column](#adding-a-new-column)
- [Database change management](#database-change-management)
- [Observability](#observability)
- [Security & secrets](#security--secrets)
- [Testing](#testing)
- [Build & Docker](#build--docker)
- [Operational runbook](#operational-runbook)
- [Project layout](#project-layout)

---

## Architecture at a glance

```
                  ┌────────── v1 (typed POJO) ───────────┐
HTTP ─► Tomcat ──►│ SendTransactionController            │──► SendTransactionService
                  │ /api/v1/send-transactions/*          │       │
                  └──────────────────────────────────────┘       │
                                                                 ▼
                  ┌────────── v2 (metadata-driven) ──────┐  Spring JDBC
HTTP ─► Tomcat ──►│ MetadataTransactionController        │──► NamedParameterJdbcTemplate
                  │ /api/v2/{alias}/{id}                 │       │
                  │ /api/v2/_metadata[/{alias}]          │       ▼
                  └────────── ▲ ──────────────────────────┘  HikariCP ──► Oracle XE
                              │
                  MetadataTransactionService
                              │
                  ┌───────────┴──────────────────────────┐
                  │ MetadataRegistry  (loads classpath:  │
                  │   metadata/*.yaml at startup, fails  │
                  │   fast on validation errors)         │
                  │ SqlBuilder        (MERGE / SELECT /  │
                  │   DELETE — caches per table)         │
                  │ GenericTableRepository (CRUD with    │
                  │   Map<String,Object> payloads)       │
                  │ GenericRowMapper, ValueConverter,    │
                  │ MetadataValidator                    │
                  └──────────────────────────────────────┘
```

Both controllers share the same datasource, the same `GlobalExceptionHandler`, and the same `RequestIdFilter` (MDC `traceId` + `X-Request-ID` header).

---

## Running locally

### Option A — Docker Compose (recommended)

Starts Oracle XE + the app in one command:

```bash
docker compose up --build
```

- App: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Actuator: <http://localhost:8080/actuator>
- Oracle: `localhost:1521/XEPDB1` (user/pass override via `DB_USER` / `DB_PASS` env)

Tear down (and drop DB volume): `docker compose down -v`

### Option B — Run against an existing Oracle

```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_URL="jdbc:oracle:thin:@localhost:1521/XEPDB1"
export DB_USER=SEND_TXN_OWNER
export DB_PASS='your-secret'

./mvnw spring-boot:run
```

The Oracle schema and tables must already exist before the app starts. Run DBA-owned setup/migration SQL outside the application deployment.

---

## Configuration

All settings are externalized through environment variables with sane defaults in `application.properties`. Profile-specific overrides live in `application-dev.properties` and `application-prod.properties`.

| Concern | Env var | Default (dev) | Notes |
|---|---|---|---|
| Active profile | `SPRING_PROFILES_ACTIVE` | `dev` | Set to `prod` in production |
| HTTP port | `SERVER_PORT` | `8080` | |
| Tomcat threads | `SERVER_THREADS_MAX` | `200` | |
| Oracle JDBC URL | `DB_URL` | `jdbc:oracle:thin:@localhost:1521/XEPDB1` | |
| DB username | `DB_USER` | `SEND_TXN_OWNER` | |
| DB password | `DB_PASS` | `Oracle123` (dev only!) | **MUST be overridden in prod** |
| Schema | `APP_DB_SCHEMA` | `SEND_TXN_OWNER` | Used for qualified table names |
| Hikari max pool | `DB_MAX_POOL` | `20` (dev) / `50` (prod) | |
| Hikari min idle | `DB_MIN_IDLE` | `5` (dev) / `10` (prod) | |
| Hikari connection timeout | `DB_CONN_TIMEOUT_MS` | `30000` | |
| Hikari leak detection | `DB_LEAK_DETECT_MS` | `60000` | |
| Flyway enabled | n/a | `false` | DB creation, DDL migrations, and seed/backfill DML are outside app startup |
| Kafka bootstrap | `KAFKA_BOOTSTRAP` | `localhost:9092` | |
| Kafka group id | `KAFKA_GROUP_ID` | `transactions-group` | |
| Swagger UI | `OPENAPI_ENABLED` | `true` (dev) / `false` (prod) | Lock down in prod |
| CORS origins | `CORS_ALLOWED_ORIGINS` | dev SPA defaults | Empty by default in prod |
| Actuator health details | `ACTUATOR_HEALTH_DETAILS` | `when-authorized` | Force `never` in prod |

**Never commit real secrets.** Use a secrets manager (Vault, AWS Secrets Manager, Kubernetes Secrets) and inject as env vars at deploy.

---

## API reference

### v1 — typed controller (`/api/v1/send-transactions`)

| Method | Path | Description |
|---|---|---|
| `PUT` | `/{tranId}` | Upsert parent + optional children. MERGE with COALESCE null-guard. |
| `GET` | `/{tranId}` | Returns parent with `tranDtl`, `recipDtl`, `addrDtl[]` nested. |
| `GET` | `/?page=N&size=M` | Paginated parent-only list (max size 100). |
| `DELETE` | `/{tranId}` | Cascade-deletes children, then the parent. |

### v2 — metadata-driven controller (`/api/v2`)

| Method | Path | Description |
|---|---|---|
| `PUT` | `/{alias}/{id}` | Upsert any registered table. Payload is `Map<String,Object>`. |
| `GET` | `/{alias}/{id}` | Fetch with all nested children. |
| `GET` | `/{alias}?page=N&size=M` | Paginated parent-only list. |
| `DELETE` | `/{alias}/{id}` | Cascade-delete. |
| `GET` | `/_metadata` | List every loaded table's column metadata. |
| `GET` | `/_metadata/{alias}` | Describe one table — replaces typed Swagger schema. |

Registered aliases (from `src/main/resources/metadata/*.yaml`):
`send-transactions`, `send-tran-dtl`, `send-recip-dtl`, `send-tran-addr-dtl`.

### Discovery

| URL | What |
|---|---|
| `/swagger-ui.html` | Interactive API explorer |
| `/v3/api-docs` | OpenAPI 3 JSON |
| `/api/v2/_metadata` | All YAML-registered tables (preferred for v2 clients) |

### Postman collection

`postman/Send_Transactions_API.postman_collection.json` ships with **16 v1** tests and **25 v2** tests across Discovery / Upsert / Query / Direct Child / Delete / Error sub-folders.

---

## Adding a new column

The whole reason for the v2 architecture. Example: add `EXTRA_NOTE VARCHAR2(100)` to `SEND_TRANSACTIONS`.

**1. DDL** — have the DB activity pipeline/DBA apply the table change:

```sql
ALTER TABLE SEND_TXN_OWNER.SEND_TRANSACTIONS ADD (EXTRA_NOTE VARCHAR2(100));
```

**2. Metadata** — append one line to `src/main/resources/metadata/send_transactions.yaml`:

```yaml
  - { jsonName: extraNote, dbColumn: EXTRA_NOTE, sqlType: VARCHAR, maxLength: 100 }
```

**3. Restart.** That's it. The new column is now accepted, validated, persisted, and round-tripped by:
- `PUT  /api/v2/send-transactions/T1   {"extraNote":"abc"}`
- `GET  /api/v2/send-transactions/T1`  → returns `"extraNote":"abc"`
- `GET  /api/v2/_metadata/send-transactions` advertises the new field

**Zero Java edits.** The v1 typed surface ignores unknown fields, so the v1 contract is unaffected.

---

## Database change management

Database creation, DDL migrations, and seed/backfill DML are intentionally outside this Spring Boot application's startup lifecycle.

- Application startup does not create schemas, tables, indexes, Flyway history tables, or seed data.
- `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration`, `spring.flyway.enabled=false`, and `spring.sql.init.mode=never` are set in `application.properties`.
- Schema objects must be provisioned before deploying or starting the service.
- Reference DDL is kept under `src/main/resources/sql/init.sql` and `src/main/resources/db/migration/V1__create_send_txn_schema.sql` for DB-owned execution/review only.

DBA bootstrap example:

```sql
ALTER USER SEND_TXN_OWNER IDENTIFIED BY <strong-password>;
GRANT CREATE SESSION, CREATE TABLE, CREATE INDEX, UNLIMITED TABLESPACE
  TO SEND_TXN_OWNER;
```

The application user should have runtime DML privileges required by the API, but schema creation and schema evolution should be handled by DBA/database deployment tooling.

---

## Observability

Spring Boot Actuator + Micrometer Prometheus registry are pre-wired.

| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Composite health (DB + disk + ping) |
| `/actuator/health/liveness` | K8s liveness probe — process responsiveness |
| `/actuator/health/readiness` | K8s readiness probe — DB reachable, app ready to serve traffic |
| `/actuator/info` | Build / git info (when present) |
| `/actuator/metrics` | All registered Micrometer metrics |
| `/actuator/prometheus` | Prometheus scrape target |
| `/actuator/loggers` | (dev only) inspect/change log levels at runtime |

Per-request `X-Request-ID` is generated (or accepted from the client) by `RequestIdFilter` and stored in MDC under `traceId` — every log line includes it. Error responses echo it back in the `traceId` field.

HTTP server metrics include percentile histograms and SLO buckets at 50ms / 100ms / 200ms / 500ms / 1s / 2s / 5s.

---

## Security & secrets

| Topic | Status |
|---|---|
| Secret externalization | Yes — all credentials via env vars; never in `application.properties` |
| SQL parameter logging | DEBUG only in dev (`org.springframework.jdbc.core`). **Prod profile forces WARN** because parameter values include card numbers, government-id URIs, DOBs |
| Error response masking | Yes — `GlobalExceptionHandler` returns generic messages for `DataAccessException` and unhandled `Exception`; full stack logged server-side only |
| PII helper | `LogSanitizer.maskPayload(...)` for any code that needs to log a payload Map |
| CORS | Configurable per env; prod default is empty allow-list |
| HTTP/2, compression, graceful shutdown | Yes |
| Stacktrace / message leak | `server.error.include-*` defaults to `never`; dev profile relaxes |
| AuthN / AuthZ | **Not bundled.** Add Spring Security or front the service with an API gateway (Kong, Apigee, AWS API Gateway) for OAuth2/JWT |
| Rate limiting | Not bundled. Add Bucket4j or use the gateway tier |

---

## Testing

```bash
./mvnw test
```

| Test class | What it proves |
|---|---|
| `MetadataRegistryTest` | YAML parses; `validate()` catches missing PK, duplicate columns, CLOB+nullGuard conflict, unknown sqlType |
| `SqlBuilderTest` | Generated MERGE/SELECT/DELETE has the right COALESCE / TO_CLOB / SYSTIMESTAMP / OFFSET-FETCH fragments; SQL is cached |
| `ValueConverterTest` | ISO string → LocalDateTime/LocalDate, Number → BigDecimal, Boolean ↔ 0/1, null → null |
| `MetadataValidatorTest` | `required`, `maxLength`, `pattern` all enforced; audit/readOnly fields skipped |
| `TransactionsConsumerCanonicalApplicationTests` | Full Spring context loads against an H2 in-memory DB — proves every controller, service, repository, and config bean wires together |

End-to-end DB behaviour (CLOB, MERGE, FK cascade) must be validated against a live Oracle — Testcontainers integration tests are a good follow-up.

---

## Build & Docker

```bash
# Local build
./mvnw clean package

# Build container
docker build -t transactions-consumer-canonical:dev .

# Or full stack
docker compose up --build
```

The Dockerfile is multi-stage (JDK 25 build → JRE 25 runtime), uses Spring Boot's layered jar for fast incremental builds, runs as non-root user `app:app`, and ships with container-aware JVM defaults (`MaxRAMPercentage=75`) plus a liveness probe.

---

## Operational runbook

| Symptom | First check |
|---|---|
| 500 with `"error":"Database Error"` | Application log near the `traceId` — full ORA-xxxxx is masked from clients but always logged |
| HikariCP `connection is not available` | Pool exhausted — check `/actuator/metrics/hikaricp.connections.active` and bump `DB_MAX_POOL` |
| `ORA-00942: table or view does not exist` at startup/request time | DB objects were not provisioned externally, privileges are missing, or `APP_DB_SCHEMA` mismatches |
| New column doesn't appear in `/api/v2/_metadata/{alias}` | YAML file not on classpath; check `target/classes/metadata/` after build |
| `IllegalStateException: clob:true requires nullGuard:false` at startup | YAML self-validation — fix the column; Oracle COALESCE can't mix VARCHAR2 bind with CLOB |
| Slow paged GETs | Check `IDX_SEND_TXN_TRAN_CRTE_DT`; the default sort uses it |

---

## Project layout

```
src/main/java/com/poc/transactions_consumer_canonical/
├── TransactionsConsumerCanonicalApplication.java
├── RequestIdFilter.java                    ← MDC traceId + X-Request-ID
├── config/
│   ├── CorsConfig.java                     ← env-driven CORS
│   └── OpenApiConfig.java                  ← Swagger metadata
├── logging/
│   └── LogSanitizer.java                   ← PII masking helper
├── controller/
│   ├── SendTransactionController.java      ← v1 typed
│   └── MetadataTransactionController.java  ← v2 generic + discovery
├── service/
│   ├── SendTransactionService.java (+impl) ← v1
│   └── MetadataTransactionService.java     ← v2 orchestrator
├── repository/
│   ├── Send*Repository{,Impl}.java         ← v1 hand-written SQL
│   ├── SqlBuilder.java                     ← v2 SQL generator (MERGE/SELECT/...)
│   ├── GenericTableRepository.java         ← v2 CRUD over any table
│   ├── GenericRowMapper.java               ← v2 ResultSet → Map
│   ├── ValueConverter.java + Converters.java
│   └── SqlQueries.java                     ← legacy externalized SQL (kept)
├── metadata/
│   ├── TableMetadata.java, ColumnMetadata.java, ChildMetadata.java
│   └── MetadataRegistry.java               ← loads + validates YAML at boot
├── validation/
│   └── MetadataValidator.java              ← required / maxLength / pattern
├── mapper/                                 ← v1 RowMappers
├── model/                                  ← v1 POJOs
├── dto/                                    ← v1 Request/Response + ErrorResponse + PagedResponse
└── exception/
    ├── GlobalExceptionHandler.java         ← masked 500s + DataAccessException
    ├── ResourceNotFoundException.java
    └── MetadataValidationException.java

src/main/resources/
├── application.properties                  ← base (env-overridable)
├── application-dev.properties              ← verbose, full errors, all actuator
├── application-prod.properties             ← masked, minimal actuator
├── db/migration/
│   └── V1__create_send_txn_schema.sql      ← reference DDL for DB-owned execution
├── metadata/
│   ├── send_transactions.yaml              ← parent (40 cols + 3 children)
│   ├── send_tran_dtl.yaml                  ← 1:1 child (CLOBs)
│   ├── send_recip_dtl.yaml                 ← 1:1 child (DATEs + CLOBs)
│   └── send_tran_addr_dtl.yaml             ← 1:many child
└── sql/                                    ← legacy reference scripts

src/test/...                                ← 33 tests, no DB required
.github/workflows/build.yml                 ← CI: mvn test + docker build
Dockerfile, .dockerignore, docker-compose.yml
postman/Send_Transactions_API.postman_collection.json
```
