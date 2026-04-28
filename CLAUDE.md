# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) and Codex CLI when working with this sub-project.

## Reference Implementation

The original implementation lives at `../v0/v0-socialraven-api/` — **read-only, never modify it**.
When implementing a feature, read the relevant v0 code to understand the original logic, then re-implement it cleanly here with tests.

## Build & Run Commands

```bash
# Build socialraven-common first (one-time, or after any common changes)
cd ../socialraven-common && ./mvnw clean install -DskipTests

# Build (skip tests)
mvn clean package -DskipTests

# Build with tests
mvn clean package

# Run locally
mvn spring-boot:run

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName

# Build Docker image — run from parent directory (needs socialraven-common alongside)
# docker build -f socialraven-api/Dockerfile -t socialraven-api .
```

## Current State (Barebones)

This project has been stripped to a clean foundation. What exists:

| File | Purpose |
|------|---------|
| `SocialRavenApplication.java` | Spring Boot entry point |
| `config/ClerkAuthenticationFilter.java` | Clerk JWT validation — sets `SecurityContext` |
| `config/ClerkAuthHelper.java` | Clerk SDK wrapper |
| `config/SecurityConfig.java` | Spring Security filter chain |
| `config/CorsConfig.java` | CORS configuration |
| `config/WorkspaceAccessFilter.java` | Reads `X-Workspace-Id` header; sets `WorkspaceContext` (role stub — wire DB lookup when workspace feature lands) |
| `annotation/RequiresRole.java` | `@RequiresRole(WorkspaceRole.X)` method annotation |
| `aspect/WorkspaceRoleAspect.java` | Enforces `@RequiresRole` via AOP |
| `exception/SocialRavenException.java` | Business logic exception with HTTP status |
| `exception/GlobalExceptionHandler.java` | Maps exceptions to HTTP responses |
| `model/ClerkAuthenticationToken.java` | Spring Security token wrapping Clerk session |
| `util/SecurityContextUtil.java` | Extracts `userId` from `SecurityContext` |
| `util/WorkspaceContext.java` | ThreadLocal holder for workspace ID + role |
| `util/GenericUtil.java` | Date utilities |
| `controller/HealthCheckController.java` | `GET /health` |

**Not yet implemented** (add feature-by-feature with tests):
- User profile + onboarding
- Workspace CRUD + `WorkspaceMemberRepo` (wire into `WorkspaceAccessFilter`)
- Social account OAuth flows
- Post creation, scheduling, publishing
- Media upload (S3 presign)
- Analytics, billing, client reports

## Architecture

**Package root:** `com.tonyghouse.socialraven`

**Key dependencies:** Spring Web, Spring Security, Spring Data JPA, PostgreSQL, Flyway, Clerk SDK, AOP, Lombok, `socialraven-common`.

**Removed (add back per feature):** Redis/Jedis, RabbitMQ/AMQP, AWS S3, MapStruct, Twitter4J.

### Authentication

All requests pass through `ClerkAuthenticationFilter` which validates the Clerk JWT and populates `SecurityContext`. User ID is extracted via `SecurityContextUtil.getUserId()`.

### Roles & Permissions

`WorkspaceAccessFilter` reads the `X-Workspace-Id` request header and sets `WorkspaceContext` (workspace ID + `WorkspaceRole`). Use `@RequiresRole(WorkspaceRole.EDITOR)` on controller methods — `WorkspaceRoleAspect` enforces it.

`WorkspaceRole` is defined in `socialraven-common` (`com.tonyghouse.socialraven.constant.WorkspaceRole`).

### Entities

All JPA entities live in `socialraven-common` (`com.tonyghouse.socialraven.entity`). They are picked up automatically by Spring Data JPA via classpath scanning. Flyway migrations (`src/main/resources/db/migration/`) own the schema — add `V1__...sql` when the first feature lands.

### Exception Handling

Throw `SocialRavenException(message, HttpStatus)` for business errors. `GlobalExceptionHandler` maps it to a JSON error response.

### Configuration

- Production: `src/main/resources/application.properties` — PostgreSQL, Flyway, Clerk
- Tests: add `src/test/resources/application.properties` with H2 in-memory DB when first tests are written

## Docker

Multi-stage build: Maven builder → Eclipse Temurin 21 JRE Alpine.
Build context must be the **parent directory** containing both `socialraven-api/` and `socialraven-common/`.
