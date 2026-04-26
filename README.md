# unqueryservice

A production-ready **multi-database unified query service** built with Spring Boot 3 and Apache Calcite.

Clients send a single REST API call with a SQL statement and a logical data-source name; the service authenticates them, validates and optimises the query through Calcite, enforces row-level and field-level permissions, caches results in Redis, and returns a consistent JSON payload.

---

## Architecture Overview

```
Client
  │
  ▼  POST /api/query  (JWT Bearer token)
┌─────────────────────────────────────────────────────┐
│  JwtAuthenticationFilter  (Spring Security)         │
│       ↓ authenticated user + roles                  │
│  QueryController                                    │
│       ↓                                             │
│  QueryServiceImpl  (orchestrator)                   │
│   ├── PermissionService.checkDataSourceAccess()     │
│   ├── SqlSecuritySandbox.validate()  (Calcite AST)  │
│   ├── CacheService.get()  (Redis)                   │
│   ├── CalciteQueryService.execute()                 │
│   │     └── JdbcSchema (Calcite JDBC adapter)       │
│   │           └── HikariCP → real JDBC driver       │
│   ├── PermissionService.applyFieldMasking()         │
│   └── CacheService.put()  (Redis)                   │
│       ↓                                             │
│  QueryResult JSON response                          │
└─────────────────────────────────────────────────────┘
```

---

## Features

| Feature | Detail |
|---|---|
| **Multi-database** | MySQL · Oracle · SQL Server · SQLite · H2 (any JDBC-compliant database) |
| **SQL planning** | Apache Calcite parses, validates, and optimises every query |
| **Security sandbox** | Only `SELECT` is permitted; DDL, DML, and dangerous keywords are rejected before execution |
| **JWT authentication** | Stateless Bearer token auth (HS256, configurable expiry) |
| **Role-based access** | Per-data-source role allow-lists (e.g. `ROLE_ADMIN`, `ROLE_ANALYST`) |
| **Field-level masking** | Sensitive columns replaced with `***` for non-admin users |
| **Row-level filtering** | A configurable SQL predicate is appended to every query via a sub-select wrapper |
| **Redis caching** | Results cached by SHA-256(datasource+sql); per-source eviction endpoint |
| **Parameterised queries** | Bind parameters prevent SQL injection even if the sandbox is somehow bypassed |
| **Dockerised** | Multi-stage Dockerfile + docker-compose.yml with Redis |
| **Actuator** | `/actuator/health` and `/actuator/info` exposed without auth |

---

## Project Structure

```
unqueryservice/
├── src/main/java/com/unqueryservice/
│   ├── UnqueryserviceApplication.java   # Spring Boot entry point
│   ├── config/
│   │   ├── DataSourceRegistry.java      # Manages HikariCP pools per logical DS
│   │   ├── QueryServiceProperties.java  # Typed config (query-service.*)
│   │   ├── RedisConfig.java             # Redis template + CacheManager
│   │   └── SecurityConfig.java          # Spring Security filter chain
│   ├── controller/
│   │   ├── AuthController.java          # POST /api/auth/login → JWT
│   │   ├── QueryController.java         # POST /api/query, GET /api/datasources
│   │   └── GlobalExceptionHandler.java  # Unified error response body
│   ├── exception/
│   │   ├── DataSourceNotFoundException.java
│   │   ├── PermissionDeniedException.java
│   │   ├── QueryServiceException.java
│   │   └── SqlSecurityException.java
│   ├── model/
│   │   ├── ErrorResponse.java
│   │   ├── LoginRequest.java
│   │   ├── LoginResponse.java
│   │   ├── QueryRequest.java
│   │   └── QueryResult.java
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java # Extracts + validates JWT per request
│   │   ├── JwtTokenProvider.java        # Issues + parses JWTs
│   │   └── SqlSecuritySandbox.java      # Calcite-based SQL validation
│   └── service/
│       ├── CalciteQueryService.java     # Executes SQL via Calcite JDBC adapter
│       ├── CacheService.java            # Redis get/put/evict helpers
│       ├── PermissionService.java       # Access checks + field masking
│       ├── QueryService.java            # Interface
│       ├── QueryServiceImpl.java        # Pipeline orchestrator
│       └── UserDetailsServiceImpl.java  # In-memory user store (demo)
├── src/main/resources/
│   ├── application.yml                  # Main configuration
│   ├── application-test.yml             # Test profile overrides
│   └── calcite-model.json               # Reference Calcite schema model
├── src/test/java/com/unqueryservice/
│   ├── security/SqlSecuritySandboxTest.java
│   └── service/
│       ├── PermissionServiceTest.java
│       └── QueryServiceIntegrationTest.java
├── Dockerfile                           # Multi-stage build
├── docker-compose.yml                   # App + Redis
└── pom.xml
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for containerised deployment)
- A running Redis instance (or use docker-compose)

### 1. Configure Data Sources

Edit `src/main/resources/application.yml`:

```yaml
query-service:
  data-sources:
    mysql-prod:
      type: mysql
      url: jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC
      username: root
      password: secret
      max-pool-size: 20
```

Any number of data sources can be registered.

### 2. Configure Permissions (optional)

```yaml
query-service:
  permissions:
    mysql-prod:
      allowed-roles:
        - ROLE_ADMIN
        - ROLE_ANALYST
      masked-columns:
        - credit_card_number
        - ssn
      row-filter: "tenant_id = 'acme'"   # appended via sub-select wrapper
```

### 3. Build

```bash
mvn clean package -DskipTests
```

### 4. Run Locally

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Start the service
java -jar target/unqueryservice-1.0.0.jar
```

### 5. Docker Compose (recommended)

```bash
docker compose up --build
```

---

## API Reference

### Authentication

**POST** `/api/auth/login`

```json
{
  "username": "analyst",
  "password": "analyst123"
}
```

Response:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "username": "analyst"
}
```

---

### Execute Query

**POST** `/api/query`  
`Authorization: Bearer <token>`

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
  "timestamp": "2026-04-26T05:52:00Z"
}
```

---

### List Data Sources

**GET** `/api/datasources`  
`Authorization: Bearer <token>`

```json
["mysql-prod", "sqlite-demo"]
```

---

### Evict Cache (Admin only)

**DELETE** `/api/cache/{dataSource}`  
`Authorization: Bearer <token>` (must have `ROLE_ADMIN`)

Returns `204 No Content`.

---

## Security Design

### SQL Sandbox

Every SQL statement passes through `SqlSecuritySandbox` before execution:

1. **Length cap** – max 10,000 characters
2. **Keyword deny-list** – blocks `INTO OUTFILE`, `SLEEP(`, `XP_CMDSHELL`, `EXEC(`, `WAITFOR DELAY`, and others
3. **Calcite AST parse** – statement must parse as valid SQL
4. **Statement kind check** – only `SELECT`, `UNION`, `INTERSECT`, `EXCEPT`, `VALUES`, and `ORDER BY` are allowed

Even if the sandbox is bypassed, all queries use **JDBC prepared statements with bind parameters**, so injection via parameter values is impossible.

### JWT

- HS256 signed with a configurable secret key (≥ 256 bits)
- Claims: `sub` (username), `roles`, `iat`, `exp`
- Stateless: no server-side session storage

---

## Default Users (demo)

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | `ROLE_ADMIN` |
| `analyst` | `analyst123` | `ROLE_ANALYST` |
| `viewer` | `viewer123` | `ROLE_VIEWER` |

Replace `UserDetailsServiceImpl` with a database-backed implementation for production use.

---

## Configuration Reference

```yaml
query-service:
  default-row-limit: 1000        # Used when client does not specify a limit
  max-row-limit: 10000           # Hard server-side cap
  cache-ttl-seconds: 60          # Redis TTL (0 = caching disabled)
  jwt:
    secret: "<min-256-bit-string>"
    expiration-ms: 3600000       # 1 hour
  data-sources:
    <name>:
      type: mysql|oracle|sqlserver|sqlite|h2
      url: <jdbc-url>
      username: <user>
      password: <pass>
      driver-class-name: <optional, auto-detected from url>
      max-pool-size: 10
  permissions:
    <name>:                      # Must match a data-source name
      allowed-roles: [ROLE_X]    # Empty = all authenticated users
      masked-columns: [col1]     # Empty = no masking
      row-filter: "col = 'val'"  # Empty = no row filter
```

---

## Running Tests

```bash
mvn test
```

Tests use an in-memory H2 database and a mocked Redis template — no external services required.

---

## Production Checklist

- [ ] Replace `jwt.secret` with a cryptographically random 256-bit value (inject via env var `QUERY_SERVICE_JWT_SECRET`)
- [ ] Replace the in-memory user store with a database-backed `UserDetailsService`
- [ ] Enable TLS on the reverse proxy in front of the service
- [ ] Set `cache-ttl-seconds: 0` or restrict cache to trusted read-only data sources if data freshness is critical
- [ ] Add Oracle/SQL Server JDBC drivers to the Docker image (they are not bundled due to commercial licensing)
- [ ] Review and tighten `masked-columns` and `row-filter` per data source
