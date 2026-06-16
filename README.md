# unqueryservice

A production-ready **multi-database unified query service** built with Spring Boot 3 and Apache Calcite.

Clients (e.g. ThingsBoard rule-chain scripts or widgets) send a `POST /api/query` request with a data-source name and a SQL statement. The service validates queries with a Calcite-powered sandbox, executes validated SELECT statements natively against the target database via HikariCP, caches the result in Redis, and returns a consistent JSON payload.

**Authentication and permission control are handled externally by ThingsBoard.** This service trusts all inbound requests.

---

## Architecture Overview

```
ThingsBoard (auth + permission control)
  │
  ▼  POST /api/query
┌──────────────────────────────────────────────────┐
│  QueryController                                 │
│       ↓                                          │
│  QueryServiceImpl  (orchestrator)                │
│   ├── SqlSecuritySandbox.validate()  (Calcite)   │
│   ├── CacheService.get()  (Redis)                │
│   ├── CalciteQueryService.execute()              │
│   │     └── HikariCP → native JDBC driver        │
│   └── CacheService.put()  (Redis)                │
│       ↓                                          │
│  QueryResult JSON response                       │
└──────────────────────────────────────────────────┘
```

---

## Features

| Feature | Detail |
|---|---|
| **Multi-database** | MySQL · Oracle · SQL Server · SQLite · H2 (any JDBC-compliant database) |
| **SQL validation** | Apache Calcite parses and validates every query before native JDBC execution |
| **Security sandbox** | Only `SELECT` is permitted; DDL, DML, and dangerous keywords are rejected before execution |
| **Redis caching** | Results cached by `SHA-256(datasource + sql)`; configurable TTL and per-source eviction |
| **Parameterised queries** | Bind parameters prevent SQL injection even if the sandbox were bypassed |
| **Row limit** | Server-side `maxRowLimit` cap prevents runaway result sets |
| **Dockerised** | Multi-stage Dockerfile + docker-compose.yml with Redis |
| **Actuator** | `/actuator/health` exposed without restriction |

---

## Project Structure

```
unqueryservice/
├── src/main/java/com/unqueryservice/
│   ├── UnqueryserviceApplication.java
│   ├── config/
│   │   ├── DataSourceRegistry.java      # Manages HikariCP pools per logical data source
│   │   ├── QueryServiceProperties.java  # Typed config (query-service.*)
│   │   └── RedisConfig.java             # Redis template + CacheManager
│   ├── controller/
│   │   ├── QueryController.java         # POST /api/query, GET /api/datasources, DELETE /api/cache/{ds}
│   │   └── GlobalExceptionHandler.java  # Unified error response body
│   ├── exception/
│   │   ├── DataSourceNotFoundException.java
│   │   ├── QueryServiceException.java
│   │   └── SqlSecurityException.java
│   ├── model/
│   │   ├── ErrorResponse.java
│   │   ├── QueryRequest.java
│   │   └── QueryResult.java
│   ├── service/
│   │   ├── CalciteQueryService.java     # Executes validated SQL via native JDBC
│   │   ├── CacheService.java            # Redis get/put/evict helpers
│   │   ├── QueryService.java            # Interface
│   │   └── QueryServiceImpl.java        # Pipeline orchestrator
│   └── util/
│       └── SqlSecuritySandbox.java      # Calcite-based SQL validation
├── src/main/resources/
│   ├── application.yml
│   ├── application-test.yml
│   └── calcite-model.json
├── src/test/java/com/unqueryservice/
│   ├── util/SqlSecuritySandboxTest.java
│   └── service/QueryServiceIntegrationTest.java
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### 1. Configure Data Sources

Edit `src/main/resources/application.yml`:

```yaml
query-service:
  data-sources:
    mysql-prod:
      type: mysql
      url: "jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC"
      username: root
      password: secret
      max-pool-size: 20
```

### 2. Build

```bash
mvn clean package
```

### 3. Run Locally

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Start the service
java -jar target/unqueryservice-1.0.0.jar
```

### 4. Docker Compose (recommended)

```bash
docker compose up --build
```

---

## API Reference

### Execute Query

**POST** `/api/query`

```json
{
  "dataSource": "mysql-prod",
  "sql": "SELECT id, name, amount FROM orders WHERE status = ?",
  "parameters": ["active"],
  "limit": 100
}
```

Response:

```json
{
  "columns": ["id", "name", "amount"],
  "rows": [
    {"id": 1, "name": "Order A", "amount": 250.00}
  ],
  "rowCount": 1,
  "elapsedMs": 42,
  "dataSource": "mysql-prod",
  "cached": false,
  "timestamp": "2026-04-26T10:00:00Z"
}
```

### List Data Sources

**GET** `/api/datasources`

```json
["mysql-prod", "sqlite-demo"]
```

### Evict Cache

**DELETE** `/api/cache/{dataSource}`

Returns `204 No Content`.

---

## SQL Security

Every statement passes through `SqlSecuritySandbox` before execution:

1. **Length cap** — max 10,000 characters
2. **Keyword deny-list** — blocks `INTO OUTFILE`, `SLEEP(`, `XP_CMDSHELL`, `EXEC(`, `WAITFOR DELAY`, Oracle/MSSQL dangerous functions, etc.
3. **Calcite AST parse** — statement must be valid SQL
4. **Statement kind check** — only `SELECT`, `UNION`, `INTERSECT`, `EXCEPT`, `VALUES`, and `ORDER BY` are allowed

All queries additionally use **JDBC prepared statements with bind parameters**, so parameter-value injection is structurally impossible.

---

## Configuration Reference

```yaml
query-service:
  default-row-limit: 1000        # Used when client does not specify a limit
  max-row-limit: 10000           # Hard server-side cap
  cache-ttl-seconds: 60          # Redis TTL (0 = caching disabled)
  data-sources:
    <name>:
      type: mysql|oracle|sqlserver|sqlite|h2
      url: "<jdbc-url>"          # Must be quoted if it contains colons
      username: <user>
      password: <pass>
      driver-class-name: <optional, auto-detected from type/url>
      catalog: <optional default catalog/database>
      schema: <optional default schema, e.g. APP or dbo>
      connection-test-query: <optional, e.g. SELECT 1 FROM DUAL>
      oracle-privileged-role: <optional Oracle-only sysdba|sysoper for SYS logins>
      data-source-properties:
        <driver-property>: <value>
      min-idle: 1
      max-pool-size: 10
```

### Oracle example

```yaml
query-service:
  data-sources:
    oracle-prod:
      type: oracle
      url: "jdbc:oracle:thin:@//db-host:1521/ORCLPDB1"
      username: app
      password: secret
      schema: APP
      connection-test-query: "SELECT 1 FROM DUAL"
      # Only if username is SYS; prefer a normal application user.
      oracle-privileged-role: sysdba
      max-pool-size: 20
```

### SQL Server example

```yaml
query-service:
  data-sources:
    sqlserver-prod:
      type: sqlserver
      url: "jdbc:sqlserver://db-host:1433;databaseName=app;encrypt=true;trustServerCertificate=true"
      username: app
      password: secret
      schema: dbo
      connection-test-query: "SELECT 1"
      max-pool-size: 20
```

The service executes validated queries with native JDBC, so vendor-specific Oracle and SQL Server SELECT syntax is preserved. Paged requests are wrapped with dialect-specific `OFFSET ... FETCH` for Oracle and SQL Server, and `LIMIT ... OFFSET` for MySQL, SQLite, and H2.

> Oracle note: use a normal application user whenever possible. Oracle rejects ordinary `SYS` password logins with `ORA-28009`; if a `SYS` connection is unavoidable, configure `oracle-privileged-role: sysdba` (or `sysoper`), which maps to the Oracle JDBC `internal_logon` property.

---

## Running Tests

```bash
mvn test
```

Tests use an in-memory H2 database and a mocked `CacheService` — no external services required.

---

## Integration with ThingsBoard

This service is designed to be called from ThingsBoard rule-chain **REST API Call** nodes or from **custom widget** datasources. ThingsBoard handles user authentication and tenant-level permission control; this service simply executes the query and returns results.

Example ThingsBoard rule-chain configuration:

```
Rule node type: REST API Call
URL: http://unqueryservice:8080/api/query
Method: POST
Body:
{
  "dataSource": "mysql-prod",
  "sql": "SELECT ts, value FROM telemetry WHERE device_id = '${deviceId}' ORDER BY ts DESC",
  "limit": 500
}
```
