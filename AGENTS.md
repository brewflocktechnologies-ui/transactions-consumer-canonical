# AGENTS.md — Coding Guide for AI Agents

## Overview

**transactions-consumer-canonical** is a Spring Boot 3.5 / Java 25 service exposing Oracle transactions via two REST API surfaces: a **typed v1** controller (SendTransactionController) and a **metadata-driven v2** controller (MetadataTransactionController). The critical architectural decision is that v2 is schema-agnostic — adding a new column requires **only YAML + DDL**, never Java code changes.

---

## Architecture: The Dual API Pattern

```
HTTP → v1 (SendTransactionController)      → SendTransactionService → SR*Repository (hand-written SQL)
    → v2 (MetadataTransactionController)   → MetadataTransactionService → GenericTableRepository
                                                 ↓ pulls metadata at boot
                                           MetadataRegistry (loads src/main/resources/metadata/*.yaml)
                                                 ↓ validates + caches SQL
                                           SqlBuilder (generates MERGE/SELECT/DELETE)
```

**Why two surfaces?**  
- **v1** is a typed contract (POJOs like `SendTransaction`, `SendTransactionDtl`, `SendRecipDtl`). Backward-compatible; ignores unknown fields from v2.
- **v2** accepts generic `Map<String,Object>` payloads and operates on **any table** registered in metadata YAML. No Java code needed to add columns.

**Both share:**
- Same `DataSource` (HikariCP → Oracle)
- Same `RequestIdFilter` (injects `traceId` into MDC + `X-Request-ID` header)
- Same `GlobalExceptionHandler` (masks PII, 500s return generic messages; full stack logged server-side)

---

## The Metadata System: Core Innovation

Metadata YAML files define tables **once**. Example: `src/main/resources/metadata/send_transactions.yaml`:

```yaml
name: SEND_TRANSACTIONS
alias: send-transactions
pk: TRAN_ID
columns:
  - { jsonName: tranId, dbColumn: TRAN_ID, sqlType: VARCHAR, maxLength: 50, pk: true, required: true }
  - { jsonName: tranAmt, dbColumn: TRAN_AMT, sqlType: NUMERIC }
  - { jsonName: tranCrteDt, dbColumn: TRAN_CRTE_DT, sqlType: TIMESTAMP, audit: true, readOnly: true }
  - { jsonName: note, dbColumn: NOTE, sqlType: CLOB, clob: true, nullGuard: false }
```

**Key field annotations:**
- `pk: true` — primary key (exactly one per table)
- `required: true` — validated on PUT; prohibits null
- `readOnly: true` — ignored on PUT; only returned on GET
- `audit: true` — system-managed (e.g., `CRTE_TS`, `UPDT_TS`); excluded from validation
- `clob: true` — large object; mapped via `TO_CLOB()` in SQL. Requires `nullGuard: false` (no COALESCE).
- `insertOnly: true` — set on PUT, never updated
- `pattern: "^[A-Z]{3}$"` — regex validation
- `maxLength` — string length validation

**Metadata loading:** `MetadataRegistry` reads all YAML from classpath at **startup**. Validation errors fail fast. Tables registered: `send-transactions`, `send-tran-dtl`, `send-recip-dtl`, `send-tran-addr-dtl`.

**When adding a new column:**  
1. DDL applied first (DBA-owned): `ALTER TABLE SEND_TRANSACTIONS ADD (NEW_COL VARCHAR2(100))`
2. Add to YAML: `- { jsonName: newCol, dbColumn: NEW_COL, sqlType: VARCHAR, maxLength: 100 }`
3. Restart app. v2 API immediately accepts `PUT /api/v2/send-transactions/ID {"newCol":"value"}`. v1 unaffected.

---

## SQL Generation & Caching

**SqlBuilder** generates MERGE/SELECT/DELETE SQL at runtime, **cached per table** in memory.

**Key generation rules:**
- **MERGE** (upsert): Inserts all columns except `audit` + read-only; updates all except `pk`, `audit`, `readOnly`. Uses `COALESCE(?, null_sentinel)` for nullable values — prevents null-guard conflicts with CLOBs.
- **SELECT**: Includes all columns; defaults to `ORDER BY pk` or metadata's `defaultOrderBy` (e.g., `TRAN_CRTE_DT DESC`).
- **DELETE**: Via single PK; cascade is application-enforced (delete children before parent).
- **OFFSET/FETCH**: Pagination always includes with Spring `Pageable` (max size 100).

**Example MERGE pseudocode:**
```sql
MERGE INTO SEND_TXN_OWNER.SEND_TRANSACTIONS t 
USING (SELECT ? pk_col, ? col1, ? col2 FROM DUAL) s
ON (t.TRAN_ID = s.pk_col)
WHEN MATCHED THEN UPDATE SET col1=COALESCE(s.col1, t.col1), col2=...
WHEN NOT MATCHED THEN INSERT (...) VALUES (...)
```

**Testing:** See `repository/SqlBuilderTest.java` — validates generated SQL structure, COALESCE fragments, and cache behavior.

---

## Developer Workflows

### Local Development with Docker Compose

```bash
docker compose up --build
```
Starts Oracle XE + app in one command.
- **App**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Actuator**: http://localhost:8080/actuator
- **Oracle**: localhost:1521/XEPDB1 (user: `SEND_TXN_OWNER`, pass: `Oracle123` — dev only!)

Tear down: `docker compose down -v` (drops DB volume).

### Build & Test

```bash
./mvnw clean package          # Full build + tests + JAR
./mvnw test                   # Unit tests only (33 tests, no real DB required)
./mvnw spring-boot:run        # Dev run against existing Oracle
```

**Against existing Oracle (Option B):**
```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_URL="jdbc:oracle:thin:@localhost:1521/XEPDB1"
export DB_USER=SEND_TXN_OWNER
export DB_PASS='your-secret'
./mvnw spring-boot:run
```

### Configuration Management

All settings externalized via env vars + profiles. **Base defaults** in `application.properties`. **Profile overrides** in `application-dev.properties` (verbose logging, all actuator endpoints) and `application-prod.properties` (minimal logging, masked actuator).

| Setting | Env var | Dev default | Notes |
|---------|---------|-------------|-------|
| Active profile | `SPRING_PROFILES_ACTIVE` | `dev` | Set to `prod` in production |
| HTTP port | `SERVER_PORT` | `8080` | |
| DB URL | `DB_URL` | `jdbc:oracle:thin:@localhost:1521/XEPDB1` | |
| HikariCP pool size | `DB_MAX_POOL` | 20 (dev) / 50 (prod) | |
| Swagger enabled | `OPENAPI_ENABLED` | true (dev) / false (prod) | Lock down in prod |
| CORS origins | `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173,http://localhost:8080` | Empty in prod |

**Never commit real secrets.** Use Vault, AWS Secrets Manager, or K8s Secrets; inject as env vars at deploy.

### Code Quality & CI

```bash
# Local SonarQube analysis (requires SonarQube running on localhost:9000)
./mvnw clean verify sonar:sonar -Dsonar.token=<token>

# SonarCloud
./mvnw clean verify sonar:sonar \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.organization=your-org \
  -Dsonar.token=<token>
```

**Exclusions from analysis:**  
- `dto/`, `model/`, `messagesdto/` — pure data carriers (Lombok @Data, no testable logic)
- `config/` — Spring wiring only
- `*Application.java` — entry point

**CI pipeline:** `.github/workflows/build.yml` runs `mvn test && docker build` (pre-configured).

---

## Testing Strategy

**Unit tests** (no real DB): 33 tests in `src/test/java/com/poc/transactions_consumer_canonical/`

| Test class | What it proves |
|---|---|
| `MetadataRegistryTest` | YAML parsing; validation catches missing PK, duplicate columns, CLOB+nullGuard conflicts, unknown sqlTypes |
| `SqlBuilderTest` | Generated MERGE/SELECT/DELETE has correct COALESCE / TO_CLOB / SYSTIMESTAMP / OFFSET-FETCH structures; caching works |
| `ValueConverterTest` | Type conversions: ISO string ↔ LocalDateTime/LocalDate, Number ↔ BigDecimal, Boolean ↔ 0/1, null → null |
| `MetadataValidatorTest` | Column validation: `required`, `maxLength`, `pattern`; audit/readOnly fields skipped |
| `TransactionsConsumerCanonicalApplicationTests` | Full Spring context loads against H2 in-memory DB; all beans wire correctly |

**Important:** E2E DB behavior (CLOB, MERGE, FK cascade) must be validated against **live Oracle**. Testcontainers integration tests are a good follow-up (not included yet).

Run tests: `./mvnw test` or inside IDE.

---

## Key File References

| File | Purpose |
|---|---|
| `src/main/java/com/poc/transactions_consumer_canonical/` | Core packages (see below) |
| `src/main/resources/metadata/*.yaml` | Table definitions (4 files: send_transactions, send_tran_dtl, send_recip_dtl, send_tran_addr_dtl) |
| `src/main/resources/application.properties` | Base config (env-overridable defaults) |
| `src/main/resources/application-dev.properties` | Dev profile (verbose logging, all actuator) |
| `src/main/resources/application-prod.properties` | Prod profile (masked errors, minimal actuator) |
| `src/main/resources/sql/init.sql` | Reference DDL (DB-owned execution only; not for app startup) |
| `postman/Send_Transactions_API.postman_collection.json` | 41 API tests (16 v1 + 25 v2) |
| `Dockerfile` | Multi-stage build (JDK 25 → JRE 25); non-root user; layered JAR |
| `docker-compose.yml` | App + Oracle XE local dev |

---

## Java Package Structure

```
com.poc.transactions_consumer_canonical/
├── TransactionsConsumerCanonicalApplication.java     [app entry point]
├── RequestIdFilter.java                              [MDC traceId + X-Request-ID]
├── config/                                           [Spring wiring; excluded from SonarQube]
│   ├── CorsConfig.java                               [env-driven CORS]
│   └── OpenApiConfig.java                            [Swagger metadata]
├── controller/
│   ├── SendTransactionController.java                [v1 typed REST]
│   └── MetadataTransactionController.java            [v2 generic REST + metadata discovery]
├── service/
│   ├── SendTransactionService(Impl).java             [v1 business logic]
│   └── MetadataTransactionService.java               [v2 orchestrator]
├── repository/
│   ├── Send*Repository(Impl).java                    [v1 hand-written SQL]
│   ├── SqlBuilder.java                               [v2 SQL generator; caches per table]
│   ├── GenericTableRepository.java                   [v2 CRUD using Map<String,Object>]
│   ├── GenericRowMapper.java                         [v2 ResultSet → Map<String,Object>]
│   ├── ValueConverter.java + Converters             [ISO dates, Numbers, Booleans, CLOBs]
│   └── SqlQueries.java                               [legacy externalized SQL; kept for reference]
├── metadata/
│   ├── TableMetadata.java, ColumnMetadata.java, ChildMetadata.java  [POJO structure]
│   └── MetadataRegistry.java                         [loads + validates YAML at startup]
├── validation/
│   └── MetadataValidator.java                        [required, maxLength, pattern validation]
├── mapper/                                           [v1 RowMappers for typed POJOs]
├── model/                                            [v1 data classes (Lombok @Data)]
├── dto/                                              [v1 Request/Response + ErrorResponse]
├── logging/
│   └── LogSanitizer.java                             [PII masking helper]
├── producer/                                         [Kafka producer (if applicable)]
├── consumer/                                         [Kafka consumer (if applicable)]
└── exception/
    ├── GlobalExceptionHandler.java                   [masks 500s; logs full stack server-side]
    ├── ResourceNotFoundException.java
    └── MetadataValidationException.java
```

---

## Critical Patterns & Conventions

### 1. YAML Metadata Is Source of Truth for v2

- Columns are **not** hardcoded Java POJOs. They live in YAML.
- Validation rules are in YAML (`required`, `pattern`, `maxLength`).
- Adding a column never touches Java code — only YAML + DDL.
- **Consequence:** When debugging v2 endpoints, always check the corresponding YAML file first.

### 2. Null Handling: COALESCE + nullGuard

- Nullable columns use `COALESCE(?, column_value)` to preserve existing nulls on partial updates.
- CLOBs cannot use COALESCE in Oracle. **Rule:** `clob: true` requires `nullGuard: false` (no COALESCE).
- **Failure symptom:** `IllegalStateException: clob:true requires nullGuard:false` at startup.
- See `MetadataValidator.validate()` for the check.

### 3. Audit & Read-only Fields

- Columns marked `audit: true` (e.g., `CRTE_TS`, `UPDT_TS`, `RPLCTN_UPDT_TS`) are **never** validated or assigned by the app.
- Columns marked `readOnly: true` are included in SELECT responses but ignored on PUT.
- **Testing:** `MetadataValidatorTest` verifies these are skipped during validation.

### 4. Pagination Always Enforced

- v2 paginated GETs: `pageSize` defaults to 10, max 100.
- v1 paginated GETs: Same limits.
- SQL uses `OFFSET ?  FETCH NEXT ? ROWS ONLY` (Oracle 12c+).
- **No offset means first page.** Check `SqlBuilderTest` for examples.

### 5. Database Ownership: No Flyway in Spring Boot

- **Intentional:** DB creation, DDL migrations, and seed DML are outside app startup.
- `spring.flyway.enabled=false`, `spring.sql.init.mode=never`.
- DBA applies schema changes using `src/main/resources/db/migration/V1__create_send_txn_schema.sql` + `src/main/resources/sql/init.sql` as reference.
- **Consequence:** App cannot start if tables don't exist. Schema must be provisioned before deployment.

### 6. Error Masking & Logging

- **Client responses:** `GlobalExceptionHandler` returns generic `{ "error": "Database Error" }` for `DataAccessException` + unhandled `Exception`.
- **Server logs:** Full ORA-xxxxx, stack traces, parameter values (though parameter logging is DEBUG in dev, forced WARN in prod).
- **PII protection:** Use `LogSanitizer.maskPayload(map)` before logging any request/response body.

### 7. Observability via Actuator + Micrometer

- **Health endpoints:**
  - `/actuator/health/liveness` — K8s liveness probe (process responsiveness)
  - `/actuator/health/readiness` — K8s readiness probe (DB reachable, app ready)
- **Metrics:** HTTP server metrics with percentile histograms (50ms / 100ms / 200ms / 500ms / 1s / 2s / 5s SLA buckets); Prometheus at `/actuator/prometheus`.
- **Request tracing:** Every log line includes `traceId` (from MDC, injected by `RequestIdFilter`).

### 8. Connection Pool Tuning

- **HikariCP** is the connection pool.
- Dev profile: 20 max pool, 5 min idle.
- Prod profile: 50 max pool, 10 min idle.
- **Symptom of exhaustion:** Logs show `HikariPool - Connection is not available`. Check `/actuator/metrics/hikaricp.connections.active` and raise `DB_MAX_POOL`.

---

## Adding Features: Common Tasks

### Task: Add a New Column to an Existing Table

1. **DDL** (DBA): `ALTER TABLE SEND_TRANSACTIONS ADD (NEW_FIELD VARCHAR2(50))`
2. **Metadata YAML** (dev): Add one line to `src/main/resources/metadata/send_transactions.yaml`:
   ```yaml
   - { jsonName: newField, dbColumn: NEW_FIELD, sqlType: VARCHAR, maxLength: 50, required: false }
   ```
3. **Validation** (if applicable):
   ```yaml
   - { jsonName: newField, dbColumn: NEW_FIELD, sqlType: VARCHAR, maxLength: 50, required: true, pattern: "^[A-Z0-9]+$" }
   ```
4. **Restart** the app. Both v1 & v2 now support the column; v1 ignores unknown fields.

### Task: Add a Validation Rule

Edit the metadata YAML column definition:
```yaml
- { jsonName: custRefNum, dbColumn: CUST_REF_NUM, sqlType: VARCHAR, maxLength: 100, required: true, pattern: "^[A-Z]{3}-\\d{5}$" }
```
Validation is enforced by `MetadataValidator` on PUT operations. Restart app; no Java changes.

### Task: Add a Child Table

1. Create a new YAML file: `src/main/resources/metadata/send_new_child.yaml`:
   ```yaml
   name: SEND_NEW_CHILD
   alias: send-new-child
   pk: CHILD_ID
   parentAlias: send-transactions
   parentPk: TRAN_ID
   parentFk: TRAN_ID
   ```
2. Register in `MetadataRegistry` (already auto-loads all YAML).
3. v2 API: `PUT /api/v2/send-new-child/CHILD_ID`, `GET /api/v2/send-new-child/CHILD_ID`, `DELETE /api/v2/send-new-child/CHILD_ID`.

### Task: Debug a v2 API Failure

1. **Check metadata YAML** — Is the alias correct? Do column names match?
2. **Check database** — Does the table exist? Are privileges correct?
3. **Check logs** — Look for `traceId` header in the request and grep server logs for that ID.
4. **Check profile** — Is `SPRING_PROFILES_ACTIVE` set correctly (prod vs dev)?
5. **Check HikariCP** — Visit `/actuator/metrics/hikaricp.connections.active` if pooling is suspected.

---

## Dependencies & Versions

**Key libraries:**
- Spring Boot 3.5.14 (Java 25)
- Oracle JDBC 11 (ojdbc11)
- HikariCP (bundled with Spring Boot JDBC)
- Jackson (YAML + JSON)
- Micrometer Prometheus
- springdoc-openapi-starter-webmvc-ui (Swagger UI)
- Kafka (spring-kafka)
- H2 (test scope, in-memory Oracle-compatible DB)
- Lombok (annotation processing)
- JaCoCo (code coverage)
- SonarQube Maven plugin

**Important:** Never commit secrets to `application.properties`. All sensitive values must come from env vars or secrets managers.

---

## Postman Collection

**File:** `postman/Send_Transactions_API.postman_collection.json` (41 tests)

**Folders:**
- **Discovery** — OpenAPI endpoints (`/v3/api-docs`, `/api/v2/_metadata`)
- **Upsert** — PUT operations (v1 & v2)
- **Query** — GET operations (paged, filtered)
- **Direct Child** — Child table CRUD
- **Delete** — DELETE operations (cascade checks)
- **Error** — Error response validation (404, 400, 500)

Import into Postman and run against `http://localhost:8080` (Docker Compose default).

---

## Summary for Agents

1. **Metadata first**: When working on v2 features, check the YAML before reading Java code.
2. **YAML-to-Java mapping**: `jsonName` is the JSON key; `dbColumn` is the Oracle column; `sqlType` governs conversion (dates, numbers, CLOBs).
3. **Dual API**: v1 and v2 coexist; v1 is hardcoded, v2 is schema-agnostic. Never force schema changes into v1 POJOs when v2 can handle them.
4. **No Flyway**: DD L changes are DBA-owned. App fails fast if schema is missing but never mutates it.
5. **Profile-driven config**: Use env vars for secrets; rely on `application-dev.properties` and `application-prod.properties` for sensible defaults.
6. **Testing**: Unit tests use H2 in-memory DB (fast); E2E validation requires live Oracle.
7. **Local dev**: `docker compose up --build` is the standard flow.
8. **Build & deploy**: `./mvnw clean package && docker build`, then push container image to registry.

