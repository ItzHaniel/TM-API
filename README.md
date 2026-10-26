# TMAPI — Task Management API

A production-grade RESTful Task Management API built with **Java 21**, **Spring Boot 3.3**, **PostgreSQL**, **Redis**, and **Docker**.

---

## Architecture Overview

```
HTTP Client
    |
JwtAuthenticationFilter  (stateless JWT validation)
    |
REST Controllers (Auth / Workspace / Task)
    |
Service Layer
    @PreAuthorize  (workspace-scoped RBAC)
    @Cacheable     (Redis TTL caching)
    @Retryable     (optimistic lock conflict retry)
    |           |
PostgreSQL      Redis
  JPA Specs       JSON cache
  @Version        Per-cache TTL
  Flyway
  Composite indexes

Background:
DeadlineNotificationScheduler (@Scheduled)
  -> NotificationDispatcher (@Async deadlineExecutor)
```

---

## Project Structure

```
src/main/java/com/tmapi/
+-- TmapiApplication.java
+-- config/
¦   +-- SecurityConfig.java        # JWT + STATELESS + @EnableMethodSecurity
¦   +-- RedisConfig.java           # Per-cache TTL, JSON serialization
¦   +-- AsyncConfig.java           # Two ThreadPoolTaskExecutors
+-- domain/
¦   +-- User.java                  # UUID PK, indexed email/username
¦   +-- Workspace.java             # Multi-tenant boundary
¦   +-- WorkspaceMember.java       # Per-workspace RBAC roles
¦   +-- Task.java                  # @Version optimistic lock
+-- repository/
¦   +-- UserRepository.java        # JOIN FETCH memberships
¦   +-- WorkspaceRepository.java
¦   +-- WorkspaceMemberRepository.java
¦   +-- TaskRepository.java        # JpaSpecificationExecutor
¦   +-- TaskSpecification.java     # JPA Criteria composable filters
+-- security/
¦   +-- TmApiUserDetails.java
¦   +-- TmApiUserDetailsService.java
¦   +-- JwtTokenProvider.java      # JJWT 0.12.x HMAC-SHA256
¦   +-- JwtAuthenticationFilter.java
+-- service/
¦   +-- AuthService.java
¦   +-- WorkspaceService.java      # Cached reads + RBAC
¦   +-- TaskService.java           # State machine + opt-lock + caching
+-- controller/
¦   +-- AuthController.java
¦   +-- WorkspaceController.java
¦   +-- TaskController.java
+-- dto/
¦   +-- AuthDtos.java
¦   +-- TaskDtos.java
¦   +-- WorkspaceDtos.java
¦   +-- UserSummary.java
+-- exception/
¦   +-- GlobalExceptionHandler.java  # RFC 7807 ProblemDetail
¦   +-- (NotFoundException, ConflictException, ...)
+-- scheduler/
    +-- DeadlineNotificationScheduler.java
    +-- NotificationDispatcher.java

src/main/resources/
+-- application.properties
+-- db/migration/V1__init_schema.sql  # Schema + 12 indexes
```

---

## Key Design Decisions

### 1. Stateless JWT Authentication
- No HttpSession — every request is self-contained
- Access token (1h) + Refresh token (24h)
- Workspace-scoped authorities: `ROLE_WS_{workspaceId}_{ROLE}` encoded in token
- `@PreAuthorize` RBAC evaluated from JWT — **zero extra DB queries**

### 2. Redis Caching Layer

| Cache | TTL | Key |
|-------|-----|-----|
| `tasks` | 5 min | `{workspaceId}:{taskId}` |
| `task_list` | 2 min | `{workspaceId}:{status}:{priority}:{page}:{size}` |
| `workspaces` | 10 min | `{workspaceId}` |
| `workspace_members` | 10 min | `{workspaceId}` |
| `user_profile` | 15 min | `{userId}` |

`@CacheEvict` on all mutations. JSON (not Java) serialization.

### 3. PostgreSQL Indexing

- Single-column: email, username, status, priority, deadline, assignee_id
- **Composite**: `(workspace_id, status, deadline)` — hot list query path
- **Composite**: `(workspace_id, assignee_id)` — workload view
- **Partial**: `(deadline, deadline_notified) WHERE deadline_notified = FALSE` — scheduler
- **GIN Trigram**: `title` — fast ILIKE search

### 4. Optimistic Locking (`@Version`)

```
Task.version managed by Hibernate
  Concurrent write ? OptimisticLockingFailureException
    ? @Retryable(max=3, backoff=50ms*2x)
      ? Exhausted ? 409 Conflict ProblemDetail
```

### 5. Async Deadline Notification Pipeline

```
@Scheduled (every 5 min, fixed-delay)
  +- query: deadline in next 24h AND notified=false
  +- CompletableFuture per task ? @Async("deadlineExecutor")
  +- allOf(...).thenRun(bulkMarkNotified)
```

Two isolated pools: `taskExecutor` + `deadlineExecutor`.

---

## API Endpoints

### Auth (public)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/auth/register` | Register |
| POST | `/api/v1/auth/login` | Login |
| POST | `/api/v1/auth/refresh` | Refresh token |

### Workspaces

| Method | Path | Role |
|--------|------|------|
| GET | `/api/v1/workspaces` | Member |
| POST | `/api/v1/workspaces` | Any |
| GET | `/api/v1/workspaces/{id}` | Member |
| PATCH | `/api/v1/workspaces/{id}` | ADMIN/OWNER |
| DELETE | `/api/v1/workspaces/{id}` | OWNER |
| POST | `/api/v1/workspaces/{id}/members` | ADMIN/OWNER |
| PATCH | `/api/v1/workspaces/{id}/members/{mid}` | ADMIN/OWNER |
| DELETE | `/api/v1/workspaces/{id}/members/{mid}` | ADMIN/OWNER |

### Tasks

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/workspaces/{wid}/tasks` | List (filtered + paginated) |
| POST | `/api/v1/workspaces/{wid}/tasks` | Create |
| GET | `/api/v1/workspaces/{wid}/tasks/{id}` | Get (cached) |
| PUT | `/api/v1/workspaces/{wid}/tasks/{id}` | Update |
| PATCH | `/api/v1/workspaces/{wid}/tasks/{id}/status` | Transition (opt-locked) |
| DELETE | `/api/v1/workspaces/{wid}/tasks/{id}` | Delete |

### Task Status Machine

```
TODO ? IN_PROGRESS ? IN_REVIEW ? DONE
 |          |              |
 |          ?              ?
 +---- CANCELLED ?---------+
```

---

## Running

### Docker Compose (recommended)

```bash
cd tmapi/
docker-compose up --build
# API: http://localhost:8080
```

### Local (needs Postgres + Redis)

```bash
export POSTGRES_URL=jdbc:postgresql://localhost:5432/tmapi
export POSTGRES_USER=tmapi
export POSTGRES_PASSWORD=tmapi
./gradlew bootRun
```

---

## Quick Start

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123"}'

# Create workspace
curl -X POST http://localhost:8080/api/v1/workspaces \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"My Team","slug":"my-team"}'

# Create task
curl -X POST http://localhost:8080/api/v1/workspaces/<WS_ID>/tasks \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Build login","priority":"HIGH","deadline":"2025-12-31T23:59:00Z"}'

# Transition status
curl -X PATCH http://localhost:8080/api/v1/workspaces/<WS_ID>/tasks/<TASK_ID>/status \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"newStatus":"IN_PROGRESS"}'
```
