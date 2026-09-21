# production-springboot-app

Production-grade Spring Boot backend application.

## Current Phase

**Phase 1 — Foundation**

This phase establishes a clean Spring Boot project skeleton only. No business
APIs, security, caching, resilience, or observability stack have been added
yet. See `CLAUDE.md` for the full incremental roadmap.

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
disabled (`spring.jpa.hibernate.ddl-auto=validate`). No migrations exist yet
in `src/main/resources/db/migration` — they will be added once entities are
introduced.

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
