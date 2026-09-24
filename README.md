# production-springboot-app

Production-grade Spring Boot backend application.

## Current Phase

**Phase 2 — Customer REST Resource**

Phase 1 established a clean Spring Boot project skeleton. Phase 2 adds the
first production-quality REST resource, `Customer`, backed by a Flyway-managed
PostgreSQL table, with a layered controller/service/repository architecture,
DTOs, centralized exception handling, correlation IDs, and tests. Security,
caching, resilience, Docker, CI/CD, pagination, and auditing are intentionally
out of scope for this phase. See `CLAUDE.md` for the full incremental roadmap.

## Technology Stack (Phase 1)

- Java 21
- Spring Boot
- Gradle (with Gradle Wrapper)
- Spring MVC
- Spring Data JPA
- PostgreSQL (JDBC driver)
- Flyway
- Jakarta Bean Validation
- Spring Boot Actuator
- JUnit 5
- Testcontainers (PostgreSQL) for integration testing

## Package

```
com.example.productionapp
```

## Requirements

- Java 21 (JDK)
- Docker (required only for running integration tests, which use
  Testcontainers to start a real PostgreSQL instance)
- A running PostgreSQL instance for the `local` profile (not included in this
  phase — no Docker Compose file yet)

## Configuration

Database configuration is environment-variable driven. No credentials are
stored in source control.

| Variable      | Description                  | Default (local profile only) |
|---------------|-------------------------------|-------------------------------|
| `DB_HOST`     | PostgreSQL host                | `localhost`                   |
| `DB_PORT`     | PostgreSQL port                | `5432`                        |
| `DB_NAME`     | Database name                  | `production_app`              |
| `DB_USERNAME` | Database username               | `postgres`                    |
| `DB_PASSWORD` | Database password               | `postgres`                    |

The base `application.yml` requires `DB_USERNAME` and `DB_PASSWORD` to be
supplied explicitly (no default), so any non-local profile must set them via
environment variables or an external secret manager.

## Profiles

- `local` — for local development. Provides convenience defaults for
  connecting to a developer's own PostgreSQL instance.
- `test` — used automatically by the test suite. Datasource connection
  details are supplied dynamically by Testcontainers at runtime.

Activate a profile with:

```
SPRING_PROFILES_ACTIVE=local
```

## Database Schema

Schema management is handled by Flyway. Hibernate DDL auto-generation is
disabled (`spring.jpa.hibernate.ddl-auto=validate`). The first migration,
`V1__create_customer_table.sql`, creates the `customers` table, and
`V3__create_redemptions_table.sql` creates the read-only `redemptions` table.

### Local seed data

The `local` profile additionally loads `db/seed-local/R__local_seed_redemptions.sql`,
a repeatable Flyway migration that ensures customer `1` exists and upserts two
sample redemptions for comparison:

| redemptionId | status  | errorCode        |
|--------------|---------|------------------|
| `RDM-1001`   | FAILED  | `VENDOR_TIMEOUT` |
| `RDM-1002`   | SUCCESS | —                |

Both are 5000 points / 50.00 USD with vendor `PAYPAL`. No other profile loads
this location, so seed rows never reach test or production databases.

## MCP Tools

| Tool                     | Arguments        | Description                            |
|--------------------------|------------------|----------------------------------------|
| `get_customer_details`   | `customerId`     | Customer details by id                 |
| `get_customer_list`      | `page`, `size`   | Paginated customer list                |
| `get_redemption_details` | `redemptionId`   | Redemption details by business id      |

The MCP endpoints (`/sse`, `/mcp/message`) are currently unauthenticated and
are acceptable only for local learning; MCP authentication is a separate phase.

## API (Phase 2)

Base path: `/api/v1/customers`

| Method | Path                     | Description              |
|--------|--------------------------|---------------------------|
| POST   | `/api/v1/customers`      | Create a customer (201)   |
| GET    | `/api/v1/customers/{id}` | Get a customer by id      |
| GET    | `/api/v1/customers`      | List all customers        |

Every response carries an `X-Correlation-Id` header (reused from the request
if present, otherwise generated), and error responses include a structured
body with `timestamp`, `status`, `error`, `message`, `path`, and
`correlationId`.

## Actuator

Only the `health` and `info` endpoints are exposed. Health details are shown
only to authorized requests.

## Building and Running

Build the application:

```
gradlew.bat build
```

Run tests:

```
gradlew.bat test
```

Run the application (requires a running PostgreSQL instance and the
environment variables above):

```
gradlew.bat bootRun
```

## Testing

Integration tests use Testcontainers to start a real PostgreSQL container —
Docker must be running. Tests do not depend on a developer's local database
installation.
