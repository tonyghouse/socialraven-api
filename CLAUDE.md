# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

# Build Docker image — must run from the deploy directory (context needs socialraven-common alongside socialraven-api)
# docker build cannot be run from inside socialraven-api/ directly anymore.
# On EC2 this is handled by deploy.sh + docker-compose.
# Locally: docker build -f socialraven-api/Dockerfile -t socialraven-api .
# (run from the directory that contains both socialraven-api/ and socialraven-common/)
```

## Project Overview

Spring Boot 3.2.5 / Java 21 REST API for social media post scheduling and publishing across multiple platforms (LinkedIn, X/Twitter, YouTube, Instagram, Facebook).

**Key dependencies:** Spring Web + Security, Spring Data JPA + PostgreSQL, Redis (Jedis), RabbitMQ (AMQP), AWS S3, Clerk authentication, MapStruct, Lombok, `socialraven-common` (email, shared enums).

## Architecture

**Package root:** `com.tonyghouse.socialraven`

Layered architecture: Controllers → Services → Repositories → Entities

### Authentication
All requests authenticated via `ClerkAuthenticationFilter` — a Spring Security filter that validates Clerk session tokens. User identity is extracted via `SecurityContextUtil`.

### OAuth Provider Pattern
Each social platform has a dedicated pair of files:
- `*OAuthService.java` — handles the OAuth flow (token exchange, storage)
- `*ProfileService.java` — fetches and maps profile data

The `OAuthInfoEntity` stores tokens for all providers. `OAuthInfoRefreshService` + `OAuthRefreshScheduler` handle token refresh on a schedule.

### Post Lifecycle
1. Posts are created via `PostController` → `PostService`, stored as `PostEntity` with `PostStatus` enum (DRAFT, SCHEDULED, PUBLISHED, FAILED).
2. `PostMediaEntity` handles S3-backed media attachments; `S3PresignedUrlService` generates upload/download URLs.
3. `PostPublishScheduler` polls for scheduled posts; `RabbitPublisher` enqueues them for async processing.
4. `PostRedisService` caches post state; `PostCollectionEntity` groups posts into campaigns.

### Caching
Redis (via Jedis) is used for:
- OAuth token expiry tracking (`RedisTokenExpirySaver`)
- Post pooling/batching (`PostRedisService`, `PostPoolHelper`)

### Configuration
- Production: `src/main/resources/application.properties` — PostgreSQL on `postgres:5432`, Redis on `redis:6379`, S3 bucket `socialraven-uploads` (us-east-1)
- Tests: `src/test/resources/application.properties` — H2 in-memory DB on port 9001
- Schema: `socialraven`, DDL mode: `update`

### Key Constants
API-local enums: `Provider.java`, `Platform.java`, `PostType.java`, `PostStatus.java` — use these rather than raw strings.

Shared enums (from `socialraven-common`): `WorkspaceRole.java`, `EmailCategory.java` — same package `com.tonyghouse.socialraven.constant`, available on the classpath via the common dependency.

### Exception Handling
Throw `SocialRavenException` for business logic errors; `GlobalExceptionHandler` centralizes HTTP response mapping.

### DTOs & Mapping
MapStruct mappers (`ProviderPlatformMapper`, `UserPlatformMapper`, `PostTypeMapper`) handle entity↔DTO conversion. Use existing mappers before writing manual conversion code.

## Docker

Multi-stage build: Maven builder → Eclipse Temurin 21 JRE Alpine. JVM flags: `-Xms256m -Xmx512m -XX:+UseG1GC`. Tests are skipped in Docker builds.

The Dockerfile expects the build context to be the **parent directory** containing both `socialraven-api/` and `socialraven-common/`. It builds `socialraven-common` first (installs to the container's local `.m2`), then builds the API. This is handled automatically by `deploy.sh` via `docker-compose.blue/green.yaml`.
