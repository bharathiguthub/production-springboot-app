\# Production Spring Boot Application



\## Project Goal



Build a production-grade Spring Boot backend application.



This is not a tutorial, prototype, or demo application.



All implementations must follow production-quality Java and Spring Boot

engineering practices.



The application will eventually include:



\- REST APIs

\- Local relational database

\- Database migrations

\- Authentication and authorization

\- Validation

\- Global exception handling

\- Structured logging

\- Correlation IDs

\- Auditing

\- Caching

\- Resilience patterns

\- Monitoring

\- Metrics

\- Distributed tracing

\- Docker

\- Automated testing

\- CI/CD



Do not implement all features at once.



Implement the application incrementally according to the user's requested phase.



\---



\# Technology Stack



Use:



\- Java 21

\- Spring Boot

\- Gradle

\- Spring MVC

\- Spring Data JPA

\- Flyway

\- Spring Security

\- Jakarta Bean Validation

\- Spring Boot Actuator

\- Micrometer

\- Prometheus

\- Grafana

\- OpenTelemetry

\- Resilience4j

\- Redis

\- Docker

\- Docker Compose

\- JUnit 5

\- Mockito

\- Testcontainers



Do not introduce another framework or library without explaining why it is

needed.



\---



\# Java Standards



Use Java 21.



Prefer modern Java features where they improve readability.



Use:



\- records for immutable DTOs when appropriate

\- switch expressions where appropriate

\- pattern matching where appropriate

\- Optional only where semantically appropriate



Do not use Optional as entity fields.



Prefer immutable objects where practical.



Use constructor injection.



Never use field injection.



Do not use:



@Autowired

private SomeService service;



Prefer:



private final SomeService service;



public SomeController(SomeService service) {

&#x20;   this.service = service;

}



Avoid unnecessary inheritance.



Prefer composition.



Avoid utility classes unless there is a clear reason for them.



\---



\# Application Architecture



Use clear layered architecture.



Default request flow:



Controller

&#x20;   |

&#x20;   v

Service

&#x20;   |

&#x20;   v

Repository

&#x20;   |

&#x20;   v

Database



Controllers must not access repositories directly.



Controllers should contain minimal business logic.



Business logic belongs in the service layer.



Persistence logic belongs in repositories.



External integrations should have dedicated client components.



\---



\# Package Structure



Use feature-oriented organization where practical while maintaining clear

architectural boundaries.



Typical components include:



controller

service

repository

entity

dto

mapper

config

exception

security

observability



Do not create unnecessary abstraction layers.



\---



\# REST API Standards



Use RESTful resource-oriented URLs.



Use versioned APIs.



Example:



/api/v1/customers



Use appropriate HTTP methods:



GET

POST

PUT

PATCH

DELETE



Return appropriate HTTP status codes.



Do not expose JPA entities directly from controllers.



Use DTOs for request and response contracts.



Validate incoming requests.



\---



\# Database Standards



Use Spring Data JPA.



Database schema changes must be managed through Flyway.



Never depend on Hibernate automatic schema generation in production.



Production configuration must use:



spring.jpa.hibernate.ddl-auto=validate



Use transactions at the service layer where appropriate.



Avoid N+1 query problems.



Avoid unnecessary database queries.



Use pagination for potentially large result sets.



Use database indexes intentionally.



Do not store database credentials in source code.



\---



\# Security Standards



Never hardcode:



\- passwords

\- database credentials

\- API keys

\- JWT secrets

\- access tokens



Secrets must come from environment variables or an external secret-management

system.



Never log credentials, tokens, passwords, or sensitive information.



Authentication and authorization will be implemented using Spring Security.



Security must default to deny unless explicitly permitted.



\---



\# Validation



Use Jakarta Bean Validation.



Examples:



@NotNull

@NotBlank

@Size

@Email

@Pattern

@Positive



Business validation belongs in the service layer.



Do not duplicate validation unnecessarily.



\---



\# Exception Handling



Use centralized exception handling.



Use:



@RestControllerAdvice



Provide a standardized API error response.



Error responses should contain useful fields such as:



timestamp

status

error

message

path

correlationId



Never expose stack traces to API clients.



\---



\# Logging



Use SLF4J.



Do not use:



System.out.println()



Use appropriate log levels:



TRACE

DEBUG

INFO

WARN

ERROR



Use parameterized logging.



Example:



log.info("Customer created customerId={}", customerId);



Do not build log strings using concatenation.



Never log sensitive data.



\---



\# Correlation IDs



Every incoming request should eventually have a correlation ID.



The correlation ID should be available in:



\- application logs

\- error responses

\- distributed traces



Reuse an incoming valid correlation ID when appropriate or generate one.



\---



\# Observability



The application must eventually support:



Spring Boot Actuator

Micrometer

Prometheus

Grafana

OpenTelemetry



Operational endpoints must be secured appropriately.



Provide health checks for important dependencies.



Support Kubernetes-compatible concepts where appropriate:



liveness

readiness



\---



\# Metrics



Monitor application and JVM metrics including:



\- request count

\- request latency

\- error rate

\- JVM heap

\- JVM non-heap

\- garbage collection

\- threads

\- CPU

\- database connection pool



Add business metrics only when they provide operational value.



Avoid uncontrolled high-cardinality metric tags.



\---



\# Resilience



External service calls should eventually support appropriate:



\- timeout

\- retry

\- exponential backoff

\- circuit breaker

\- bulkhead



Use Resilience4j where appropriate.



Do not retry operations blindly.



Consider idempotency before enabling retries.



\---



\# Testing



Production code must be testable.



Use:



JUnit 5

Mockito

Spring Boot Test

Testcontainers



Create unit tests for business logic.



Create integration tests for important application flows.



Use Testcontainers for database integration tests where appropriate.



Tests must not depend on a developer's local database.



\---



\# Build Rules



The project uses Gradle.



Prefer the Gradle Wrapper:



Windows:



gradlew.bat



Linux/macOS:



./gradlew



Before considering a task complete:



1\. Compile the application.

2\. Run relevant tests.

3\. Run the complete test suite when practical.

4\. Check for compilation warnings/errors.

5\. Fix failures introduced by the change.



\---



\# Configuration



Use Spring profiles.



Expected profiles eventually include:



local

test

prod



Keep environment-specific configuration outside Java code.



Never commit real production secrets.



\---



\# Git Rules



Do not commit:



\- IDE metadata that should be ignored

\- build output

\- secrets

\- credentials

\- local environment files containing secrets

\- logs



Maintain an appropriate .gitignore.



Do not perform destructive Git operations unless explicitly requested.



Do not push code to a remote repository unless explicitly requested.



\---



\# Claude Code Working Rules



Before modifying the project:



1\. Read this CLAUDE.md.

2\. Inspect the existing project structure.

3\. Understand the current implementation.

4\. Reuse existing patterns.

5\. Identify the smallest reasonable change.



Do not rewrite working code unnecessarily.



Do not introduce dependencies without a clear reason.



Do not overengineer simple requirements.



Do not create speculative abstractions for hypothetical future requirements.



Do not implement future phases unless explicitly requested.



When requirements are ambiguous and could materially affect architecture,

ask for clarification.



\---



\# Implementation Workflow



For each requested feature:



1\. Understand the requirement.

2\. Inspect related code.

3\. Describe the intended approach when the change is substantial.

4\. Implement the smallest complete solution.

5\. Add or update tests.

6\. Compile.

7\. Run tests.

8\. Fix failures caused by the change.

9\. Summarize what changed.

10\. Mention important assumptions or remaining risks.



\---



\# Production Mindset



Always consider:



\- security

\- scalability

\- reliability

\- maintainability

\- observability

\- performance

\- testability

\- failure handling



However, do not add complexity solely because something might be needed in

the future.



Prefer simple, explicit, maintainable solutions.



\---

\# Database Decision



The primary relational database for this application is PostgreSQL.



Development database:

\- PostgreSQL

\- Run locally using Docker Compose

\- Spring Data JPA

\- HikariCP connection pooling

\- Flyway for schema migrations



Do not use H2 as a replacement for PostgreSQL in integration tests.

Use PostgreSQL Testcontainers for database integration tests.



Database credentials must be supplied through environment variables.

Never hardcode database passwords in source code.

