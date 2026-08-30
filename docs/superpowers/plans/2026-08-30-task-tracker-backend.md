# Task Tracker Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Spring Boot task-tracking backend described in `docs/superpowers/specs/2026-08-30-task-tracker-backend-design.md` — REST API + WebSocket notifications + AI-assisted descriptions, package-by-feature, Postgres/Flyway, JWT auth.

**Architecture:** Package-by-feature modular monolith (`auth`, `user`, `team`, `task`, `comment`, `attachment`, `notification`, `ai`, plus shared `common`/`config`). Each module owns its entity/repository/service/controller/DTOs and talks to other modules only through their public services.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Maven, PostgreSQL 16, Flyway, Spring Security + JWT (jjwt), Spring WebSocket (STOMP), springdoc-openapi, Lombok, JUnit 5 + Mockito + Testcontainers, JaCoCo.

## Global Constraints

- Java 21, Spring Boot 3.3.x, Maven build (`pom.xml`, not Gradle).
- Database: PostgreSQL, schema managed exclusively via Flyway migrations under `src/main/resources/db/migration`.
- All entity IDs are `UUID`, generated in-app via `GenerationType.UUID` (no DB-side default) so Testcontainers Postgres needs no extensions.
- All API responses use the envelope `{ success, data, error, meta }` — implemented once in `common.web.ApiResponse` and reused everywhere; no controller returns a bare DTO.
- Passwords hashed with BCrypt; JWT access tokens ~15 min TTL, refresh tokens rotated on every use and stored hashed (never in plaintext) so they can be revoked.
- Every team-scoped endpoint must call `TeamMembershipService.requireMember(...)` (or `requireRole(...)`) before acting — no controller bypasses this.
- Secrets (`DB_*`, `JWT_SECRET`, `ANTHROPIC_API_KEY`) come only from environment variables, validated present at startup via `@ConfigurationProperties` + Bean Validation; never hardcoded.
- Tests: unit tests use Mockito (no Spring context, no network); integration/e2e tests use Testcontainers PostgreSQL + a real Spring context — no test ever calls the real Anthropic API.
- Target 80%+ line coverage, enforced by the `jacoco-maven-plugin` check goal wired in Task 1.
- Repo `D:\Airtribe\task-tracker-backend` is pushed to GitHub as a **public** repository (final task).

---

### Task 1: Project Scaffold, Build Config, and Health Check

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/airtribe/tasktracker/TaskTrackerApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-test.yml`
- Create: `.gitignore`
- Create: `.env.example`
- Create: `docker-compose.yml`
- Create: `src/main/java/com/airtribe/tasktracker/health/HealthController.java`
- Test: `src/test/java/com/airtribe/tasktracker/health/HealthControllerTest.java`

**Interfaces:**
- Produces: a bootable Spring Boot app on port `8080`, `GET /api/health` returning `{"success":true,"data":{"status":"UP"},"error":null,"meta":null}` (raw, ahead of the shared `ApiResponse` type introduced in Task 2 — this task hand-writes the same shape so later tasks can swap it in without changing the contract).
- Produces: Maven coordinates `groupId=com.airtribe`, `artifactId=task-tracker-backend`, `packaging=jar`.

**Deliberately deferred:** `spring-boot-starter-security` and `spring-security-test` are NOT added to `pom.xml` here — Task 5 adds them together with `SecurityConfig`. If they were on the classpath from Task 1 with no `SecurityConfig` yet, Spring Boot's default auto-configuration would deny every request (including `/api/health`) with 401, breaking this task's own test and Task 2's `GlobalExceptionHandlerTest`. Task 5, Step 0 adds both dependencies back in as part of wiring up security.

- [ ] **Step 1: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>

    <groupId>com.airtribe</groupId>
    <artifactId>task-tracker-backend</artifactId>
    <version>0.1.0</version>
    <name>task-tracker-backend</name>
    <description>Task tracking and team collaboration backend</description>

    <properties>
        <java.version>21</java.version>
        <jjwt.version>0.12.6</jjwt.version>
        <testcontainers.version>1.20.1</testcontainers.version>
        <springdoc.version>2.6.0</springdoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
            <!-- WebClient only, for the AI module's outbound HTTP call -->
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.12</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals><goal>prepare-agent</goal></goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals><goal>report</goal></goals>
                    </execution>
                    <execution>
                        <id>check</id>
                        <phase>verify</phase>
                        <goals><goal>check</goal></goals>
                        <configuration>
                            <rules>
                                <rule>
                                    <element>BUNDLE</element>
                                    <limits>
                                        <limit>
                                            <counter>LINE</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>0.80</minimum>
                                        </limit>
                                    </limits>
                                </rule>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Write the application entry point**

```java
package com.airtribe.tasktracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaskTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskTrackerApplication.class, args);
    }
}
```

- [ ] **Step 3: Write `application.yml`**

```yaml
server:
  port: 8080

spring:
  application:
    name: task-tracker-backend
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:task_tracker}
    username: ${DB_USER:task_tracker}
    password: ${DB_PASSWORD:task_tracker}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

app:
  jwt:
    secret: ${JWT_SECRET:dev-only-secret-key-change-me-please-32bytes-min}
    access-token-ttl-minutes: 15
    refresh-token-ttl-days: 14
  storage:
    root-dir: ${STORAGE_ROOT:./data/attachments}
  ai:
    anthropic-api-key: ${ANTHROPIC_API_KEY:}
    model: claude-fable-5
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 4: Write `application-test.yml`** (used by `@ActiveProfiles("test")` integration tests; Testcontainers overrides the datasource URL at runtime)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
app:
  jwt:
    secret: test-only-secret-key-not-for-production-use-32bytes
  storage:
    root-dir: ./build/test-attachments
  ai:
    anthropic-api-key: ""
```

- [ ] **Step 5: Write `.gitignore`**

```
target/
build/
*.class
.idea/
*.iml
.vscode/
.env
data/
```

- [ ] **Step 6: Write `.env.example`**

```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=task_tracker
DB_USER=task_tracker
DB_PASSWORD=task_tracker
JWT_SECRET=change-me-to-a-long-random-string-min-32-bytes
STORAGE_ROOT=./data/attachments
ANTHROPIC_API_KEY=
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

- [ ] **Step 7: Write `docker-compose.yml`** (local Postgres for dev)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: task_tracker
      POSTGRES_USER: task_tracker
      POSTGRES_PASSWORD: task_tracker
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

- [ ] **Step 8: Write the failing test for the health endpoint**

```java
package com.airtribe.tasktracker.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
```

Note: this test needs a running Postgres (via Testcontainers) once `spring-boot-starter-data-jpa`/Flyway are on the classpath and JPA entities exist. For this task only (before any entity exists), it will boot against the `application.yml` datasource — so run it against the `docker-compose.yml` Postgres:

```bash
docker compose up -d postgres
```

- [ ] **Step 9: Run test to verify it fails**

Run: `mvn -q test -Dtest=HealthControllerTest`
Expected: FAIL — compilation error, `HealthController` does not exist yet.

- [ ] **Step 10: Implement the health controller**

```java
package com.airtribe.tasktracker.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        // LinkedHashMap, not Map.of(...) — Map.of() throws NullPointerException
        // on a null value, and this shape needs "error"/"meta" to serialize as null.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", Map.of("status", "UP"));
        body.put("error", null);
        body.put("meta", null);
        return body;
    }
}
```

- [ ] **Step 11: Run test to verify it passes**

Run: `docker compose up -d postgres && mvn -q test -Dtest=HealthControllerTest`
Expected: PASS

- [ ] **Step 12: Commit**

```bash
git add pom.xml src/main/java/com/airtribe/tasktracker/TaskTrackerApplication.java \
        src/main/resources/application.yml src/main/resources/application-test.yml \
        .gitignore .env.example docker-compose.yml \
        src/main/java/com/airtribe/tasktracker/health src/test/java/com/airtribe/tasktracker/health
git commit -m "chore: scaffold Spring Boot project with health check"
```

### Task 2: Common Module — API Envelope, Exceptions, Global Error Handling, Auditing Base

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/common/web/ApiResponse.java`
- Create: `src/main/java/com/airtribe/tasktracker/common/web/ApiError.java`
- Create: `src/main/java/com/airtribe/tasktracker/common/web/PageMeta.java`
- Create: `src/main/java/com/airtribe/tasktracker/common/exception/NotFoundException.java`
- Create: `src/main/java/com/airtribe/tasktracker/common/exception/ForbiddenException.java`
- Create: `src/main/java/com/airtribe/tasktracker/common/exception/ConflictException.java`
- Create: `src/main/java/com/airtribe/tasktracker/common/exception/BadRequestException.java`
- Create: `src/main/java/com/airtribe/tasktracker/common/web/GlobalExceptionHandler.java`
- Create: `src/main/java/com/airtribe/tasktracker/common/persistence/AuditableEntity.java`
- Create: `src/main/java/com/airtribe/tasktracker/common/config/JpaAuditingConfig.java`
- Modify: `src/main/java/com/airtribe/tasktracker/health/HealthController.java` — use `ApiResponse` instead of a hand-rolled `Map`
- Test: `src/test/java/com/airtribe/tasktracker/common/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: nothing (foundation module).
- Produces: `ApiResponse<T>` with static factories `ApiResponse.ok(T data)`, `ApiResponse.ok(T data, PageMeta meta)`, `ApiResponse.error(ApiError error)` — **every controller in every later task returns `ApiResponse<...>`, never a bare DTO or `ResponseEntity<DtoType>` without wrapping.** `PageMeta(int page, int limit, long total)`. Exceptions `NotFoundException(String message)`, `ForbiddenException(String message)`, `ConflictException(String message)`, `BadRequestException(String message)` — later services throw these directly; `GlobalExceptionHandler` maps them to HTTP 404/403/409/400 respectively, `MethodArgumentNotValidException` to 400, anything unmapped to 500 with a generic message (never a stack trace).

- [ ] **Step 1: Write the failing test for exception → HTTP mapping**

```java
package com.airtribe.tasktracker.common.web;

import com.airtribe.tasktracker.common.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/api/test/not-found")
        void notFound() { throw new NotFoundException("thing missing"); }

        @GetMapping("/api/test/forbidden")
        void forbidden() { throw new ForbiddenException("nope"); }

        @GetMapping("/api/test/conflict")
        void conflict() { throw new ConflictException("dup"); }

        @GetMapping("/api/test/bad-request")
        void badRequest() { throw new BadRequestException("bad input"); }

        @GetMapping("/api/test/boom")
        void boom() { throw new RuntimeException("secret internal detail"); }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notFoundMapsTo404() throws Exception {
        mockMvc.perform(get("/api/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("thing missing"));
    }

    @Test
    void forbiddenMapsTo403() throws Exception {
        mockMvc.perform(get("/api/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("nope"));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        mockMvc.perform(get("/api/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("dup"));
    }

    @Test
    void badRequestMapsTo400() throws Exception {
        mockMvc.perform(get("/api/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("bad input"));
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/api/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.error.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret internal detail"))));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL — `ApiResponse`, exception classes, and `GlobalExceptionHandler` don't exist.

- [ ] **Step 3: Implement `ApiError`, `PageMeta`, `ApiResponse`**

```java
package com.airtribe.tasktracker.common.web;

public record ApiError(String code, String message) {
}
```

```java
package com.airtribe.tasktracker.common.web;

public record PageMeta(int page, int limit, long total) {
}
```

```java
package com.airtribe.tasktracker.common.web;

public record ApiResponse<T>(boolean success, T data, ApiError error, PageMeta meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, PageMeta meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, error, null);
    }
}
```

- [ ] **Step 4: Implement the domain exceptions**

```java
package com.airtribe.tasktracker.common.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
```

```java
package com.airtribe.tasktracker.common.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
```

```java
package com.airtribe.tasktracker.common.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
```

```java
package com.airtribe.tasktracker.common.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
```

- [ ] **Step 5: Implement `GlobalExceptionHandler`**

```java
package com.airtribe.tasktracker.common.web;

import com.airtribe.tasktracker.common.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message.isBlank() ? "Invalid request" : message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(new ApiError(code, message)));
    }
}
```

- [ ] **Step 6: Implement the JPA auditing base entity and config**

```java
package com.airtribe.tasktracker.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
```

```java
package com.airtribe.tasktracker.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
```

- [ ] **Step 7: Update `HealthController` to use `ApiResponse`**

```java
package com.airtribe.tasktracker.health;

import com.airtribe.tasktracker.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn -q test -Dtest=GlobalExceptionHandlerTest,HealthControllerTest`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/common src/main/java/com/airtribe/tasktracker/health \
        src/test/java/com/airtribe/tasktracker/common
git commit -m "feat: add API response envelope, domain exceptions, global error handling, auditing base"
```

---

### Task 3: Database Schema — Flyway Migration for All Tables

**Files:**
- Create: `src/main/resources/db/migration/V1__init_schema.sql`
- Create: `src/test/java/com/airtribe/tasktracker/AbstractIntegrationTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/db/SchemaMigrationTest.java`

**Interfaces:**
- Produces: `AbstractIntegrationTest` — the shared Testcontainers base class every later integration/e2e test class extends. It starts one shared `PostgreSQLContainer<>` (via a static field + `@Container`, container reuse across the test JVM), registers its JDBC properties with `@DynamicPropertySource`, and is annotated `@SpringBootTest(webEnvironment = RANDOM_PORT) @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers`. Later test classes just `extends AbstractIntegrationTest` and get `mockMvc`, a running Postgres with migrations applied, and a clean Spring context.
- Produces: tables `users`, `refresh_tokens`, `teams`, `team_memberships`, `invitations`, `tasks`, `comments`, `attachments`, `notifications` — exact column names referenced by every entity class in Tasks 4–15.

- [ ] **Step 1: Write the migration**

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

CREATE TABLE teams (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE team_memberships (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_team_memberships_team_user UNIQUE (team_id, user_id)
);
CREATE INDEX idx_team_memberships_user_id ON team_memberships(user_id);

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED')),
    invited_by UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_invitations_token UNIQUE (token)
);
CREATE INDEX idx_invitations_team_id ON invitations(team_id);

CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED')),
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    due_date TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id),
    assignee_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_tasks_team_status ON tasks(team_id, status);
CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id);

CREATE TABLE comments (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_comments_task_id ON comments(task_id);

CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_attachments_task_id ON attachments(task_id);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL CHECK (type IN ('TASK_ASSIGNED', 'TASK_UPDATED', 'COMMENT_ADDED')),
    payload JSONB NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, read);
```

- [ ] **Step 2: Write the shared Testcontainers base class**

```java
package com.airtribe.tasktracker;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("task_tracker_test")
                    .withUsername("task_tracker")
                    .withPassword("task_tracker");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;
}
```

- [ ] **Step 3: Write the failing test — migration applies cleanly and produces the expected tables**

```java
package com.airtribe.tasktracker.db;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allExpectedTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "users", "refresh_tokens", "teams", "team_memberships",
                "invitations", "tasks", "comments", "attachments", "notifications",
                "flyway_schema_history");
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -q test -Dtest=SchemaMigrationTest`
Expected: FAIL — no migration file yet, so only `flyway_schema_history` exists (or Flyway has nothing to run and the assertion mismatches).

- [ ] **Step 5: Confirm the migration file from Step 1 is in place, then run test to verify it passes**

Run: `mvn -q test -Dtest=SchemaMigrationTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V1__init_schema.sql \
        src/test/java/com/airtribe/tasktracker/AbstractIntegrationTest.java \
        src/test/java/com/airtribe/tasktracker/db
git commit -m "feat: add Flyway schema migration and shared Testcontainers test base"
```

### Task 4: User Module — Entity, Repository, Service

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/user/User.java`
- Create: `src/main/java/com/airtribe/tasktracker/user/UserRepository.java`
- Create: `src/main/java/com/airtribe/tasktracker/user/UserService.java`
- Create: `src/main/java/com/airtribe/tasktracker/user/dto/UserResponse.java`
- Test: `src/test/java/com/airtribe/tasktracker/user/UserServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/user/UserRepositoryIT.java`

**Interfaces:**
- Consumes: `AuditableEntity` (Task 2), `NotFoundException`/`ConflictException` (Task 2).
- Produces: `User` entity with getters `getId() UUID`, `getName() String`, `getEmail() String`, `getPasswordHash() String`, `getAvatarUrl() String`, and setters for each (Lombok `@Getter @Setter`). `UserRepository.findByEmail(String) Optional<User>`, `UserRepository.existsByEmail(String) boolean`. `UserService.createUser(String name, String email, String passwordHash) User`, `UserService.findById(UUID id) User` (throws `NotFoundException`), `UserService.findByEmail(String email) User` (throws `NotFoundException`), `UserService.existsByEmail(String email) boolean`, `UserService.updateProfile(UUID id, String name, String avatarUrl) User`. `UserResponse(UUID id, String name, String email, String avatarUrl, Instant createdAt)` with static `UserResponse.from(User user)`. **These exact signatures are consumed by the `auth` module (Task 6) and the user-profile endpoints (Task 7).**

- [ ] **Step 1: Write the failing unit tests for `UserService`**

```java
package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.common.exception.ConflictException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createUserSavesAndReturnsUser() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.createUser("Alice", "a@b.com", "hashed");

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getEmail()).isEqualTo("a@b.com");
        assertThat(result.getPasswordHash()).isEqualTo("hashed");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("Alice", "a@b.com", "hashed"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("a@b.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void findByIdReturnsUserWhenPresent() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(userService.findById(id)).isSameAs(user);
    }

    @Test
    void findByIdThrowsNotFoundWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateProfileChangesNameAndAvatarAndSaves() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setName("Old Name");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateProfile(id, "New Name", "https://img/avatar.png");

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getAvatarUrl()).isEqualTo("https://img/avatar.png");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=UserServiceTest`
Expected: FAIL — `User`, `UserRepository`, `UserService` don't exist yet.

- [ ] **Step 3: Implement `User` entity**

```java
package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "avatar_url")
    private String avatarUrl;
}
```

- [ ] **Step 4: Implement `UserRepository`**

```java
package com.airtribe.tasktracker.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 5: Implement `UserService`**

```java
package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.common.exception.ConflictException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String name, String email, String passwordHash) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with email " + email + " already exists.");
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return userRepository.save(user);
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public User updateProfile(UUID id, String name, String avatarUrl) {
        User user = findById(id);
        user.setName(name);
        user.setAvatarUrl(avatarUrl);
        return userRepository.save(user);
    }
}
```

- [ ] **Step 6: Implement `UserResponse` DTO**

```java
package com.airtribe.tasktracker.user.dto;

import com.airtribe.tasktracker.user.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String avatarUrl, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getAvatarUrl(), user.getCreatedAt());
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q test -Dtest=UserServiceTest`
Expected: PASS

- [ ] **Step 8: Write and run the repository integration test (unique email constraint)**

```java
package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsByEmail() {
        User user = new User();
        user.setName("Bob");
        user.setEmail("bob@example.com");
        user.setPasswordHash("hashed");
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByEmail("bob@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    }

    @Test
    void rejectsDuplicateEmailAtDbLevel() {
        User first = new User();
        first.setName("Carl");
        first.setEmail("carl@example.com");
        first.setPasswordHash("hashed");
        userRepository.saveAndFlush(first);

        User duplicate = new User();
        duplicate.setName("Carl 2");
        duplicate.setEmail("carl@example.com");
        duplicate.setPasswordHash("hashed2");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

Run: `mvn -q test -Dtest=UserRepositoryIT`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/user src/test/java/com/airtribe/tasktracker/user
git commit -m "feat: add User entity, repository, and service"
```

### Task 5: Security Infrastructure — JWT, Principal, Filter Chain

**Files:**
- Modify: `pom.xml` — add back `spring-boot-starter-security` and `spring-security-test` (deferred from Task 1, see that task's note)
- Modify: `src/test/java/com/airtribe/tasktracker/common/web/GlobalExceptionHandlerTest.java` — authenticate its requests now that `anyRequest().authenticated()` applies to them
- Create: `src/main/java/com/airtribe/tasktracker/security/JwtProperties.java`
- Create: `src/main/java/com/airtribe/tasktracker/security/JwtPrincipal.java`
- Create: `src/main/java/com/airtribe/tasktracker/security/JwtService.java`
- Create: `src/main/java/com/airtribe/tasktracker/security/UserPrincipal.java`
- Create: `src/main/java/com/airtribe/tasktracker/security/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/airtribe/tasktracker/security/JwtAuthenticationEntryPoint.java`
- Create: `src/main/java/com/airtribe/tasktracker/security/JwtAccessDeniedHandler.java`
- Create: `src/main/java/com/airtribe/tasktracker/config/SecurityConfig.java`
- Test: `src/test/java/com/airtribe/tasktracker/security/JwtServiceTest.java`

**Interfaces:**
- Consumes: `User` (Task 4).
- Produces: `JwtService.generateAccessToken(User user) String`, `JwtService.parseAccessToken(String token) JwtPrincipal` (throws `io.jsonwebtoken.JwtException` subtypes on invalid/expired tokens). `JwtPrincipal(UUID userId, String email)`. `UserPrincipal` — implements `org.springframework.security.core.userdetails.UserDetails`, constructed as `new UserPrincipal(User user)`, exposes `getUserId() UUID` and `getUser() User` in addition to the `UserDetails` contract. A `PasswordEncoder` bean (`BCryptPasswordEncoder`) available for injection everywhere. **Task 6 (auth) and every later controller that needs the current user obtain it via `@AuthenticationPrincipal UserPrincipal principal` and call `principal.getUserId()`.**

- [ ] **Step 0: Add the security dependencies back to `pom.xml`**

Insert these two `<dependency>` blocks — the first alongside the other non-test `spring-boot-starter-*` entries (e.g. directly after `spring-boot-starter-data-jpa`), the second alongside the other `scope=test` entries (e.g. directly after `spring-boot-starter-test`):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
```

```xml
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 0b: Authenticate `GlobalExceptionHandlerTest`'s requests**

Once `SecurityConfig` (this task, Step 10) is in place, `anyRequest().authenticated()` applies to `GlobalExceptionHandlerTest`'s `/api/test/*` probe endpoints too — without a fix they'd all return 401 instead of the status each test expects. Fix it now, in the same commit as `SecurityConfig`, so the test suite never has a broken intermediate state.

Add this import to `GlobalExceptionHandlerTest.java`:

```java
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
```

Add `.with(user("test-user"))` to every `mockMvc.perform(get(...))` call in the file, for example:

```java
    @Test
    void notFoundMapsTo404() throws Exception {
        mockMvc.perform(get("/api/test/not-found").with(user("test-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("thing missing"));
    }
```

Apply the same `.with(user("test-user"))` addition to `forbiddenMapsTo403`, `conflictMapsTo409`, `badRequestMapsTo400`, and `unexpectedExceptionMapsTo500WithoutLeakingDetails`. Re-run `mvn.cmd -q test -Dtest=GlobalExceptionHandlerTest` after `SecurityConfig` exists (Step 10) to confirm all five still pass.

- [ ] **Step 1: Write the failing test for `JwtService`**

```java
package com.airtribe.tasktracker.security;

import com.airtribe.tasktracker.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private User sampleUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jwt-test@example.com");
        user.setName("JWT Test");
        return user;
    }

    @Test
    void generatesAndParsesRoundTrip() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-at-least-32-bytes-long!!");
        props.setAccessTokenTtlMinutes(15);
        JwtService jwtService = new JwtService(props);

        User user = sampleUser();
        String token = jwtService.generateAccessToken(user);
        JwtPrincipal principal = jwtService.parseAccessToken(token);

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.email()).isEqualTo(user.getEmail());
    }

    @Test
    void rejectsExpiredToken() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-at-least-32-bytes-long!!");
        props.setAccessTokenTtlMinutes(-1);
        JwtService jwtService = new JwtService(props);

        String token = jwtService.generateAccessToken(sampleUser());

        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtProperties issuerProps = new JwtProperties();
        issuerProps.setSecret("first-secret-key-at-least-32-bytes-long!");
        issuerProps.setAccessTokenTtlMinutes(15);
        String token = new JwtService(issuerProps).generateAccessToken(sampleUser());

        JwtProperties verifierProps = new JwtProperties();
        verifierProps.setSecret("second-secret-key-at-least-32-bytes-lon");
        verifierProps.setAccessTokenTtlMinutes(15);
        JwtService verifier = new JwtService(verifierProps);

        assertThatThrownBy(() -> verifier.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=JwtServiceTest`
Expected: FAIL — none of the classes exist.

- [ ] **Step 3: Implement `JwtProperties`**

```java
package com.airtribe.tasktracker.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private int accessTokenTtlMinutes = 15;
    private int refreshTokenTtlDays = 14;
}
```

- [ ] **Step 4: Implement `JwtPrincipal`**

```java
package com.airtribe.tasktracker.security;

import java.util.UUID;

public record JwtPrincipal(UUID userId, String email) {
}
```

- [ ] **Step 5: Implement `JwtService`**

```java
package com.airtribe.tasktracker.security;

import com.airtribe.tasktracker.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getAccessTokenTtlMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public JwtPrincipal parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtPrincipal(UUID.fromString(claims.getSubject()), claims.get("email", String.class));
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q test -Dtest=JwtServiceTest`
Expected: PASS

- [ ] **Step 7: Implement `UserPrincipal`**

```java
package com.airtribe.tasktracker.security;

import com.airtribe.tasktracker.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
}
```

- [ ] **Step 8: Implement the JWT authentication filter**

```java
package com.airtribe.tasktracker.security;

import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                JwtPrincipal claims = jwtService.parseAccessToken(token);
                User user = userService.findById(claims.userId());
                UserPrincipal principal = new UserPrincipal(user);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException | com.airtribe.tasktracker.common.exception.NotFoundException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 9: Implement the JSON auth entry point and access-denied handler**

```java
package com.airtribe.tasktracker.security;

import com.airtribe.tasktracker.common.web.ApiError;
import com.airtribe.tasktracker.common.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var body = ApiResponse.error(new ApiError("UNAUTHORIZED", "Authentication is required."));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
```

```java
package com.airtribe.tasktracker.security;

import com.airtribe.tasktracker.common.web.ApiError;
import com.airtribe.tasktracker.common.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var body = ApiResponse.error(new ApiError("FORBIDDEN", "You do not have access to this resource."));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
```

- [ ] **Step 10: Implement `SecurityConfig`**

```java
package com.airtribe.tasktracker.config;

import com.airtribe.tasktracker.security.*;
import com.airtribe.tasktracker.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        return new JwtAuthenticationFilter(jwtService, userService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     JwtAuthenticationEntryPoint entryPoint,
                                                     JwtAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/auth/**", "/invitations/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

- [ ] **Step 11: Commit**

```bash
git add pom.xml src/main/java/com/airtribe/tasktracker/security src/main/java/com/airtribe/tasktracker/config/SecurityConfig.java \
        src/test/java/com/airtribe/tasktracker/security src/test/java/com/airtribe/tasktracker/common/web/GlobalExceptionHandlerTest.java
git commit -m "feat: add JWT service, security principal, and stateless filter chain"
```

### Task 6: Auth Module — Register, Login, Refresh, Logout

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/common/exception/UnauthorizedException.java`
- Modify: `src/main/java/com/airtribe/tasktracker/common/web/GlobalExceptionHandler.java` — add a 401 handler for `UnauthorizedException`
- Create: `src/main/java/com/airtribe/tasktracker/auth/RefreshToken.java`
- Create: `src/main/java/com/airtribe/tasktracker/auth/RefreshTokenRepository.java`
- Create: `src/main/java/com/airtribe/tasktracker/auth/RefreshTokenService.java`
- Create: `src/main/java/com/airtribe/tasktracker/auth/AuthService.java`
- Create: `src/main/java/com/airtribe/tasktracker/auth/AuthController.java`
- Create: `src/main/java/com/airtribe/tasktracker/auth/dto/RegisterRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/auth/dto/LoginRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/auth/dto/RefreshRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/auth/dto/AuthResponse.java`
- Test: `src/test/java/com/airtribe/tasktracker/auth/RefreshTokenServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/auth/AuthServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/auth/AuthControllerIT.java`

**Interfaces:**
- Consumes: `UserService` (Task 4), `JwtService`, `JwtProperties`, `PasswordEncoder` (Task 5).
- Produces: `AuthService.register(String name, String email, String rawPassword) AuthResponse`, `AuthService.login(String email, String rawPassword) AuthResponse` (throws `UnauthorizedException` on bad credentials, same message for unknown-email and wrong-password to avoid account enumeration), `AuthService.refresh(String rawRefreshToken) AuthResponse`, `AuthService.logout(String rawRefreshToken) void`. `AuthResponse(String accessToken, String refreshToken, UserResponse user)`. `RefreshTokenService.issue(User user) String` (raw token), `RefreshTokenService.rotate(String rawToken) RefreshTokenService.RotationResult` (record `RotationResult(User user, String newRawToken)`, throws `UnauthorizedException` if invalid/expired/revoked), `RefreshTokenService.revoke(String rawToken) void`.

- [ ] **Step 1: Add `UnauthorizedException` and wire it into `GlobalExceptionHandler`**

```java
package com.airtribe.tasktracker.common.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
```

Add this handler method to `GlobalExceptionHandler` (alongside the existing `handleForbidden` method):

```java
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }
```

- [ ] **Step 2: Write the failing unit test for `RefreshTokenService`**

```java
package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.common.exception.UnauthorizedException;
import com.airtribe.tasktracker.security.JwtProperties;
import com.airtribe.tasktracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService service() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-at-least-32-bytes-long!!");
        props.setRefreshTokenTtlDays(14);
        return new RefreshTokenService(refreshTokenRepository, props);
    }

    private User sampleUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("refresh-test@example.com");
        return user;
    }

    @Test
    void issueSavesHashedTokenAndReturnsRawToken() {
        RefreshTokenService service = service();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String raw = service.issue(sampleUser());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(raw);
        assertThat(captor.getValue().isRevoked()).isFalse();
        assertThat(raw).isNotBlank();
    }

    @Test
    void rotateRejectsUnknownToken() {
        RefreshTokenService service = service();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("does-not-exist"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshTokenService service = service();
        User user = sampleUser();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash("irrelevant-because-mocked-lookup");
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate("some-raw-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotateRejectsRevokedToken() {
        RefreshTokenService service = service();
        User user = sampleUser();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash("irrelevant-because-mocked-lookup");
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        stored.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate("some-raw-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOne() {
        RefreshTokenService service = service();
        User user = sampleUser();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash("irrelevant-because-mocked-lookup");
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.RotationResult result = service.rotate("some-raw-token");

        assertThat(stored.isRevoked()).isTrue();
        assertThat(result.user()).isSameAs(user);
        assertThat(result.newRawToken()).isNotBlank();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=RefreshTokenServiceTest`
Expected: FAIL — `RefreshToken`, `RefreshTokenRepository`, `RefreshTokenService` don't exist.

- [ ] **Step 4: Implement `RefreshToken` entity and repository**

```java
package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.common.persistence.AuditableEntity;
import com.airtribe.tasktracker.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;
}
```

```java
package com.airtribe.tasktracker.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
```

- [ ] **Step 5: Implement `RefreshTokenService`**

```java
package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.common.exception.UnauthorizedException;
import com.airtribe.tasktracker.security.JwtProperties;
import com.airtribe.tasktracker.user.User;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    public record RotationResult(User user, String newRawToken) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    public String issue(User user) {
        String raw = randomToken();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash(raw));
        entity.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS));
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);
        return raw;
    }

    public RotationResult rotate(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token."));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired refresh token.");
        }
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        String newRaw = issue(stored.getUser());
        return new RotationResult(stored.getUser(), newRaw);
    }

    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to hash refresh token", e);
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q test -Dtest=RefreshTokenServiceTest`
Expected: PASS

- [ ] **Step 7: Write the failing unit test for `AuthService`**

```java
package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.common.exception.UnauthorizedException;
import com.airtribe.tasktracker.security.JwtService;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed-pw");
        return user;
    }

    @Test
    void registerHashesPasswordAndIssuesTokens() {
        User created = sampleUser();
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed-pw");
        when(userService.createUser("Alice", "alice@example.com", "hashed-pw")).thenReturn(created);
        when(jwtService.generateAccessToken(created)).thenReturn("access-token");
        when(refreshTokenService.issue(created)).thenReturn("refresh-token");

        AuthResponse response = authService.register("Alice", "alice@example.com", "plaintext");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User user = sampleUser();
        when(userService.findByEmail("alice@example.com")).thenReturn(user);
        when(passwordEncoder.matches("plaintext", "hashed-pw")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");

        AuthResponse response = authService.login("alice@example.com", "plaintext");

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = sampleUser();
        when(userService.findByEmail("alice@example.com")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice@example.com", "wrong"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginFailsWithUnknownEmailUsingSameMessageAsWrongPassword() {
        when(userService.findByEmail("nobody@example.com"))
                .thenThrow(new com.airtribe.tasktracker.common.exception.NotFoundException("User not found."));

        assertThatThrownBy(() -> authService.login("nobody@example.com", "whatever"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void refreshDelegatesToRefreshTokenServiceAndIssuesNewAccessToken() {
        User user = sampleUser();
        when(refreshTokenService.rotate("old-raw"))
                .thenReturn(new RefreshTokenService.RotationResult(user, "new-raw"));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");

        AuthResponse response = authService.refresh("old-raw");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-raw");
    }

    @Test
    void logoutRevokesTheGivenRefreshToken() {
        authService.logout("some-raw");

        verify(refreshTokenService).revoke("some-raw");
    }
}
```

- [ ] **Step 8: Run test to verify it fails**

Run: `mvn -q test -Dtest=AuthServiceTest`
Expected: FAIL — `AuthService`, `AuthResponse` don't exist.

- [ ] **Step 9: Implement the DTOs**

```java
package com.airtribe.tasktracker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "is required") String name,
        @NotBlank(message = "is required") @Email(message = "must be a valid email") String email,
        @NotBlank(message = "is required") @Size(min = 8, message = "must be at least 8 characters") String password
) {
}
```

```java
package com.airtribe.tasktracker.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank(message = "is required") String email,
                            @NotBlank(message = "is required") String password) {
}
```

```java
package com.airtribe.tasktracker.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "is required") String refreshToken) {
}
```

```java
package com.airtribe.tasktracker.auth.dto;

import com.airtribe.tasktracker.user.dto.UserResponse;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
}
```

- [ ] **Step 10: Implement `AuthService`**

```java
package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.auth.dto.AuthResponse;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.common.exception.UnauthorizedException;
import com.airtribe.tasktracker.security.JwtService;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import com.airtribe.tasktracker.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password.";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,
                        JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse register(String name, String email, String rawPassword) {
        User user = userService.createUser(name, email, passwordEncoder.encode(rawPassword));
        return issueTokens(user);
    }

    public AuthResponse login(String email, String rawPassword) {
        User user;
        try {
            user = userService.findByEmail(email);
        } catch (NotFoundException ex) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawRefreshToken);
        String accessToken = jwtService.generateAccessToken(result.user());
        return new AuthResponse(accessToken, result.newRawToken(), UserResponse.from(result.user()));
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken, UserResponse.from(user));
    }
}
```

- [ ] **Step 11: Run test to verify it passes**

Run: `mvn -q test -Dtest=AuthServiceTest`
Expected: PASS

- [ ] **Step 12: Implement `AuthController`**

```java
package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.auth.dto.*;
import com.airtribe.tasktracker.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(null);
    }
}
```

- [ ] **Step 13: Write and run the full-flow integration test**

```java
package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshLogoutFlow() throws Exception {
        String registerBody = """
                {"name":"Dana","email":"dana@example.com","password":"supersecret1"}
                """;

        String registerResponseJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("dana@example.com"))
                .andReturn().getResponse().getContentAsString();

        JsonNode registerJson = objectMapper.readTree(registerResponseJson);
        String firstRefreshToken = registerJson.get("data").get("refreshToken").asText();

        String loginBody = """
                {"email":"dana@example.com","password":"supersecret1"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        String wrongPasswordBody = """
                {"email":"dana@example.com","password":"wrongpassword"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody))
                .andExpect(status().isUnauthorized());

        String refreshBody = objectMapper.writeValueAsString(new Object() {
            public final String refreshToken = firstRefreshToken;
        });
        String refreshResponseJson = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode refreshJson = objectMapper.readTree(refreshResponseJson);
        String secondRefreshToken = refreshJson.get("data").get("refreshToken").asText();
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // the rotated-out token can no longer be used
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());

        String logoutBody = objectMapper.writeValueAsString(new Object() {
            public final String refreshToken = secondRefreshToken;
        });
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerRejectsDuplicateEmailWithConflict() throws Exception {
        String body = """
                {"name":"Eve","email":"eve@example.com","password":"supersecret1"}
                """;
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void registerRejectsInvalidPayload() throws Exception {
        String body = """
                {"name":"","email":"not-an-email","password":"short"}
                """;
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
```

Run: `mvn -q test -Dtest=AuthControllerIT`
Expected: PASS

- [ ] **Step 14: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/common/exception/UnauthorizedException.java \
        src/main/java/com/airtribe/tasktracker/common/web/GlobalExceptionHandler.java \
        src/main/java/com/airtribe/tasktracker/auth src/test/java/com/airtribe/tasktracker/auth
git commit -m "feat: add auth module with register/login/refresh/logout"
```

### Task 7: User Profile Endpoints

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/user/UserController.java`
- Create: `src/main/java/com/airtribe/tasktracker/user/dto/UpdateProfileRequest.java`
- Test: `src/test/java/com/airtribe/tasktracker/user/UserControllerIT.java`

**Interfaces:**
- Consumes: `UserService` (Task 4), `UserPrincipal` (Task 5), `AuthService`/`AuthController` (Task 6, used only by the test to obtain a token).
- Produces: `GET /api/users/me` and `PUT /api/users/me`, both returning `ApiResponse<UserResponse>`. No other module depends on this controller directly.

- [ ] **Step 1: Write the failing integration test**

```java
package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetAccessToken(String email) throws Exception {
        String body = """
                {"name":"Frank","email":"%s","password":"supersecret1"}
                """.formatted(email);
        String responseJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(responseJson);
        return node.get("data").get("accessToken").asText();
    }

    @Test
    void getMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMeReturnsCurrentUser() throws Exception {
        String token = registerAndGetAccessToken("frank@example.com");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("frank@example.com"))
                .andExpect(jsonPath("$.data.name").value("Frank"));
    }

    @Test
    void putMeUpdatesNameAndAvatar() throws Exception {
        String token = registerAndGetAccessToken("frank2@example.com");
        String updateBody = """
                {"name":"Franklin","avatarUrl":"https://img/avatar.png"}
                """;

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Franklin"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://img/avatar.png"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=UserControllerIT`
Expected: FAIL — `UserController` and `UpdateProfileRequest` don't exist.

- [ ] **Step 3: Implement `UpdateProfileRequest`**

```java
package com.airtribe.tasktracker.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "is required") String name,
        String avatarUrl
) {
}
```

- [ ] **Step 4: Implement `UserController`**

```java
package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import com.airtribe.tasktracker.user.dto.UpdateProfileRequest;
import com.airtribe.tasktracker.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(UserResponse.from(userService.findById(principal.getUserId())));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        User updated = userService.updateProfile(principal.getUserId(), request.name(), request.avatarUrl());
        return ApiResponse.ok(UserResponse.from(updated));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=UserControllerIT`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/user src/test/java/com/airtribe/tasktracker/user/UserControllerIT.java
git commit -m "feat: add user profile GET/PUT endpoints"
```

### Task 8: Team Module — Teams, Membership, Roles

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/team/TeamRole.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/Team.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/TeamMembership.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/TeamRepository.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/TeamMembershipRepository.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/TeamService.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/TeamMembershipService.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/TeamController.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/dto/CreateTeamRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/dto/TeamResponse.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/dto/TeamMemberResponse.java`
- Test: `src/test/java/com/airtribe/tasktracker/team/TeamServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/team/TeamMembershipServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/team/TeamControllerIT.java`

**Interfaces:**
- Consumes: `User`, `UserService` (Task 4), `UserPrincipal` (Task 5).
- Produces: `TeamRole` enum (`OWNER`, `ADMIN`, `MEMBER`). `TeamService.createTeam(UUID ownerId, String name, String description) Team` (also creates the owner's `TeamMembership`), `TeamService.listMyTeams(UUID userId) List<Team>`, `TeamService.getTeam(UUID teamId) Team` (throws `NotFoundException`), `TeamService.listMembers(UUID teamId) List<TeamMembership>`. `TeamMembershipService.requireMember(UUID teamId, UUID userId) TeamMembership` (throws `ForbiddenException`), `TeamMembershipService.requireRole(UUID teamId, UUID userId, Set<TeamRole> allowedRoles) TeamMembership` (throws `ForbiddenException`), `TeamMembershipService.addMember(Team team, User user, TeamRole role) TeamMembership`. **Every later team-scoped module (invitation, task, comment, attachment, notification) calls `TeamMembershipService.requireMember`/`requireRole` before acting — this is the single authorization chokepoint for team-scoped resources.**

- [ ] **Step 1: Write the failing unit tests for `TeamService` and `TeamMembershipService`**

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMembershipRepository membershipRepository;
    @Mock private UserService userService;

    private TeamService teamService() {
        return new TeamService(teamRepository, membershipRepository, userService);
    }

    @Test
    void createTeamSavesTeamAndOwnerMembership() {
        UUID ownerId = UUID.randomUUID();
        User owner = new User();
        owner.setId(ownerId);
        when(userService.findById(ownerId)).thenReturn(owner);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepository.save(any(TeamMembership.class))).thenAnswer(inv -> inv.getArgument(0));

        Team team = teamService().createTeam(ownerId, "Engineering", "The eng team");

        assertThat(team.getName()).isEqualTo("Engineering");
        assertThat(team.getOwner()).isSameAs(owner);

        ArgumentCaptor<TeamMembership> captor = ArgumentCaptor.forClass(TeamMembership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(TeamRole.OWNER);
        assertThat(captor.getValue().getUser()).isSameAs(owner);
    }

    @Test
    void getTeamThrowsNotFoundWhenMissing() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService().getTeam(teamId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listMyTeamsReturnsTeamsFromMemberships() {
        UUID userId = UUID.randomUUID();
        Team team = new Team();
        team.setId(UUID.randomUUID());
        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(membership));
        when(teamRepository.findAllById(List.of(team.getId()))).thenReturn(List.of(team));

        List<Team> result = teamService().listMyTeams(userId);

        assertThat(result).containsExactly(team);
    }
}
```

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamMembershipServiceTest {

    @Mock private TeamMembershipRepository membershipRepository;
    @InjectMocks private TeamMembershipService teamMembershipService;

    @Test
    void requireMemberThrowsWhenNotAMember() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(membershipRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamMembershipService.requireMember(teamId, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRoleThrowsWhenRoleNotAllowed() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(membershipRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> teamMembershipService.requireRole(teamId, userId, Set.of(TeamRole.OWNER, TeamRole.ADMIN)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRoleSucceedsWhenRoleAllowed() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.ADMIN);
        when(membershipRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(membership));

        TeamMembership result = teamMembershipService.requireRole(teamId, userId, Set.of(TeamRole.OWNER, TeamRole.ADMIN));

        assertThat(result).isSameAs(membership);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=TeamServiceTest,TeamMembershipServiceTest`
Expected: FAIL — none of the team classes exist yet.

- [ ] **Step 3: Implement `TeamRole`, `Team`, `TeamMembership`**

```java
package com.airtribe.tasktracker.team;

public enum TeamRole {
    OWNER, ADMIN, MEMBER
}
```

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.persistence.AuditableEntity;
import com.airtribe.tasktracker.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "teams")
public class Team extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}
```

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.persistence.AuditableEntity;
import com.airtribe.tasktracker.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "team_memberships")
public class TeamMembership extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
}
```

- [ ] **Step 4: Implement the repositories**

```java
package com.airtribe.tasktracker.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
}
```

```java
package com.airtribe.tasktracker.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, UUID> {
    Optional<TeamMembership> findByTeamIdAndUserId(UUID teamId, UUID userId);
    List<TeamMembership> findByTeamId(UUID teamId);
    List<TeamMembership> findByUserId(UUID userId);
    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);
}
```

- [ ] **Step 5: Implement `TeamService` and `TeamMembershipService`**

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository membershipRepository;
    private final UserService userService;

    public TeamService(TeamRepository teamRepository, TeamMembershipRepository membershipRepository,
                        UserService userService) {
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
    }

    public Team createTeam(UUID ownerId, String name, String description) {
        User owner = userService.findById(ownerId);
        Team team = new Team();
        team.setName(name);
        team.setDescription(description);
        team.setOwner(owner);
        team = teamRepository.save(team);

        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        membership.setUser(owner);
        membership.setRole(TeamRole.OWNER);
        membership.setJoinedAt(Instant.now());
        membershipRepository.save(membership);

        return team;
    }

    public Team getTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found."));
    }

    public List<Team> listMyTeams(UUID userId) {
        List<UUID> teamIds = membershipRepository.findByUserId(userId).stream()
                .map(m -> m.getTeam().getId())
                .toList();
        return teamRepository.findAllById(teamIds);
    }

    public List<TeamMembership> listMembers(UUID teamId) {
        return membershipRepository.findByTeamId(teamId);
    }
}
```

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.user.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class TeamMembershipService {

    private final TeamMembershipRepository membershipRepository;

    public TeamMembershipService(TeamMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public TeamMembership requireMember(UUID teamId, UUID userId) {
        return membershipRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this team."));
    }

    public TeamMembership requireRole(UUID teamId, UUID userId, Set<TeamRole> allowedRoles) {
        TeamMembership membership = requireMember(teamId, userId);
        if (!allowedRoles.contains(membership.getRole())) {
            throw new ForbiddenException("You do not have permission to perform this action.");
        }
        return membership;
    }

    public TeamMembership addMember(Team team, User user, TeamRole role) {
        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        membership.setUser(user);
        membership.setRole(role);
        membership.setJoinedAt(Instant.now());
        return membershipRepository.save(membership);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -q test -Dtest=TeamServiceTest,TeamMembershipServiceTest`
Expected: PASS

- [ ] **Step 7: Implement the DTOs**

```java
package com.airtribe.tasktracker.team.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTeamRequest(@NotBlank(message = "is required") String name, String description) {
}
```

```java
package com.airtribe.tasktracker.team.dto;

import com.airtribe.tasktracker.team.Team;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(UUID id, String name, String description, UUID ownerId, Instant createdAt) {
    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getDescription(),
                team.getOwner().getId(), team.getCreatedAt());
    }
}
```

```java
package com.airtribe.tasktracker.team.dto;

import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamRole;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(UUID userId, String name, String email, String avatarUrl,
                                  TeamRole role, Instant joinedAt) {
    public static TeamMemberResponse from(TeamMembership membership) {
        return new TeamMemberResponse(
                membership.getUser().getId(),
                membership.getUser().getName(),
                membership.getUser().getEmail(),
                membership.getUser().getAvatarUrl(),
                membership.getRole(),
                membership.getJoinedAt());
    }
}
```

- [ ] **Step 8: Implement `TeamController`**

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import com.airtribe.tasktracker.team.dto.CreateTeamRequest;
import com.airtribe.tasktracker.team.dto.TeamMemberResponse;
import com.airtribe.tasktracker.team.dto.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;
    private final TeamMembershipService teamMembershipService;

    public TeamController(TeamService teamService, TeamMembershipService teamMembershipService) {
        this.teamService = teamService;
        this.teamMembershipService = teamMembershipService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
                                                              @Valid @RequestBody CreateTeamRequest request) {
        Team team = teamService.createTeam(principal.getUserId(), request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(TeamResponse.from(team)));
    }

    @GetMapping
    public ApiResponse<List<TeamResponse>> myTeams(@AuthenticationPrincipal UserPrincipal principal) {
        List<TeamResponse> teams = teamService.listMyTeams(principal.getUserId()).stream()
                .map(TeamResponse::from).toList();
        return ApiResponse.ok(teams);
    }

    @GetMapping("/{teamId}")
    public ApiResponse<TeamResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID teamId) {
        teamMembershipService.requireMember(teamId, principal.getUserId());
        return ApiResponse.ok(TeamResponse.from(teamService.getTeam(teamId)));
    }

    @GetMapping("/{teamId}/members")
    public ApiResponse<List<TeamMemberResponse>> members(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID teamId) {
        teamMembershipService.requireMember(teamId, principal.getUserId());
        List<TeamMemberResponse> members = teamService.listMembers(teamId).stream()
                .map(TeamMemberResponse::from).toList();
        return ApiResponse.ok(members);
    }
}
```

- [ ] **Step 9: Write and run the integration test**

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetAccessToken(String email, String name) throws Exception {
        String body = """
                {"name":"%s","email":"%s","password":"supersecret1"}
                """.formatted(name, email);
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    @Test
    void ownerCanCreateTeamAndSeeItInMyTeamsAndMembers() throws Exception {
        String ownerToken = registerAndGetAccessToken("owner1@example.com", "Owner One");

        String createBody = """
                {"name":"Engineering","description":"The eng team"}
                """;
        String createJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Engineering"))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(createJson).get("data").get("id").asText();

        mockMvc.perform(get("/api/teams").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Engineering"));

        mockMvc.perform(get("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"));
    }

    @Test
    void nonMemberCannotAccessTeam() throws Exception {
        String ownerToken = registerAndGetAccessToken("owner2@example.com", "Owner Two");
        String outsiderToken = registerAndGetAccessToken("outsider@example.com", "Outsider");

        String createBody = """
                {"name":"Design","description":"The design team"}
                """;
        String createJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(createJson).get("data").get("id").asText();

        mockMvc.perform(get("/api/teams/" + teamId).header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }
}
```

Run: `mvn -q test -Dtest=TeamControllerIT`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/team src/test/java/com/airtribe/tasktracker/team
git commit -m "feat: add team module with roles and membership authorization"
```

### Task 9: Invitation Flow — Invite and Accept

**Files:**
- Modify: `src/main/java/com/airtribe/tasktracker/config/SecurityConfig.java` — remove the `"/invitations/**"` permitAll pattern added in Task 5 (accepting an invitation requires an authenticated user, same as every other non-auth endpoint; the pattern was premature and is corrected here)
- Create: `src/main/java/com/airtribe/tasktracker/team/InvitationStatus.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/Invitation.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/InvitationRepository.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/InvitationService.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/InvitationController.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/dto/CreateInvitationRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/team/dto/InvitationResponse.java`
- Test: `src/test/java/com/airtribe/tasktracker/team/InvitationServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/team/InvitationControllerIT.java`

**Interfaces:**
- Consumes: `Team`, `TeamService`, `TeamMembership`, `TeamMembershipRepository`, `TeamMembershipService`, `TeamRole`, `TeamMemberResponse` (Task 8), `User` (Task 4), `UserPrincipal` (Task 5).
- Produces: `InvitationService.createInvitation(UUID teamId, UUID inviterId, String email) Invitation` (throws `ForbiddenException` if the inviter isn't `OWNER`/`ADMIN`, `ConflictException` if a `PENDING` invitation for that team+email already exists), `InvitationService.acceptInvitation(String token, User acceptingUser) TeamMembership` (throws `NotFoundException` for an unknown token, `BadRequestException` if not `PENDING`/expired, `ForbiddenException` if the invitation's email doesn't match the accepting user — idempotent if the user is already a member). `InvitationResponse(UUID id, UUID teamId, String email, String token, String status, Instant expiresAt)`.

- [ ] **Step 1: Remove the premature permitAll pattern in `SecurityConfig`**

Find this line inside `securityFilterChain(...)` (added in Task 5, Step 10):

```java
                        .requestMatchers("/api/health", "/api/auth/**", "/invitations/**").permitAll()
```

Replace it with:

```java
                        .requestMatchers("/api/health", "/api/auth/**").permitAll()
```

- [ ] **Step 2: Write the failing unit tests for `InvitationService`**

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ConflictException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private TeamService teamService;
    @Mock private TeamMembershipService teamMembershipService;
    @Mock private TeamMembershipRepository teamMembershipRepository;

    private InvitationService service() {
        return new InvitationService(invitationRepository, teamService, teamMembershipService, teamMembershipRepository);
    }

    private Team sampleTeam(UUID id) {
        Team team = new Team();
        team.setId(id);
        return team;
    }

    private User sampleUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        return user;
    }

    @Test
    void createInvitationRequiresAdminOrOwner() {
        UUID teamId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        when(teamMembershipService.requireRole(teamId, inviterId, Set.of(TeamRole.OWNER, TeamRole.ADMIN)))
                .thenThrow(new ForbiddenException("You do not have permission to perform this action."));

        assertThatThrownBy(() -> service().createInvitation(teamId, inviterId, "new@example.com"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createInvitationRejectsDuplicatePendingInvite() {
        UUID teamId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        when(invitationRepository.findByTeamIdAndEmailAndStatus(teamId, "new@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(new Invitation()));

        assertThatThrownBy(() -> service().createInvitation(teamId, inviterId, "new@example.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createInvitationSavesPendingInvitationWithToken() {
        UUID teamId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        when(invitationRepository.findByTeamIdAndEmailAndStatus(teamId, "new@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.empty());
        when(teamService.getTeam(teamId)).thenReturn(sampleTeam(teamId));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));

        Invitation invitation = service().createInvitation(teamId, inviterId, "new@example.com");

        assertThat(invitation.getEmail()).isEqualTo("new@example.com");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.getToken()).isNotBlank();
        assertThat(invitation.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void acceptInvitationThrowsNotFoundForUnknownToken() {
        when(invitationRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().acceptInvitation("bad-token", sampleUser("a@b.com")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void acceptInvitationRejectsExpiredInvitation() {
        Invitation invitation = new Invitation();
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setEmail("a@b.com");
        invitation.setExpiresAt(Instant.now().minusSeconds(60));
        when(invitationRepository.findByToken("token")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service().acceptInvitation("token", sampleUser("a@b.com")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptInvitationRejectsMismatchedEmail() {
        Invitation invitation = new Invitation();
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setEmail("intended@b.com");
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));
        when(invitationRepository.findByToken("token")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service().acceptInvitation("token", sampleUser("someone-else@b.com")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptInvitationAddsMembershipAndMarksAccepted() {
        UUID teamId = UUID.randomUUID();
        Team team = sampleTeam(teamId);
        Invitation invitation = new Invitation();
        invitation.setTeam(team);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setEmail("a@b.com");
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));
        when(invitationRepository.findByToken("token")).thenReturn(Optional.of(invitation));
        User user = sampleUser("a@b.com");
        when(teamMembershipRepository.existsByTeamIdAndUserId(teamId, user.getId())).thenReturn(false);
        TeamMembership membership = new TeamMembership();
        when(teamMembershipService.addMember(team, user, TeamRole.MEMBER)).thenReturn(membership);

        TeamMembership result = service().acceptInvitation("token", user);

        assertThat(result).isSameAs(membership);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(invitationRepository).save(invitation);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=InvitationServiceTest`
Expected: FAIL — none of the invitation classes exist.

- [ ] **Step 4: Implement `InvitationStatus` and `Invitation`**

```java
package com.airtribe.tasktracker.team;

public enum InvitationStatus {
    PENDING, ACCEPTED, EXPIRED
}
```

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.persistence.AuditableEntity;
import com.airtribe.tasktracker.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "invitations")
public class Invitation extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
```

- [ ] **Step 5: Implement `InvitationRepository`**

```java
package com.airtribe.tasktracker.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByToken(String token);
    Optional<Invitation> findByTeamIdAndEmailAndStatus(UUID teamId, String email, InvitationStatus status);
}
```

- [ ] **Step 6: Implement `InvitationService`**

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ConflictException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.user.User;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
public class InvitationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int INVITATION_TTL_DAYS = 7;

    private final InvitationRepository invitationRepository;
    private final TeamService teamService;
    private final TeamMembershipService teamMembershipService;
    private final TeamMembershipRepository teamMembershipRepository;

    public InvitationService(InvitationRepository invitationRepository, TeamService teamService,
                              TeamMembershipService teamMembershipService,
                              TeamMembershipRepository teamMembershipRepository) {
        this.invitationRepository = invitationRepository;
        this.teamService = teamService;
        this.teamMembershipService = teamMembershipService;
        this.teamMembershipRepository = teamMembershipRepository;
    }

    public Invitation createInvitation(UUID teamId, UUID inviterId, String email) {
        teamMembershipService.requireRole(teamId, inviterId, Set.of(TeamRole.OWNER, TeamRole.ADMIN));

        invitationRepository.findByTeamIdAndEmailAndStatus(teamId, email, InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new ConflictException("There is already a pending invitation for " + email + ".");
                });

        Team team = teamService.getTeam(teamId);
        User inviter = teamMembershipService.requireMember(teamId, inviterId).getUser();

        Invitation invitation = new Invitation();
        invitation.setTeam(team);
        invitation.setEmail(email);
        invitation.setToken(randomToken());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitedBy(inviter);
        invitation.setExpiresAt(Instant.now().plus(INVITATION_TTL_DAYS, ChronoUnit.DAYS));
        return invitationRepository.save(invitation);
    }

    public TeamMembership acceptInvitation(String token, User acceptingUser) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Invitation not found."));

        if (invitation.getStatus() != InvitationStatus.PENDING || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This invitation is no longer valid.");
        }
        if (!invitation.getEmail().equalsIgnoreCase(acceptingUser.getEmail())) {
            throw new ForbiddenException("This invitation was sent to a different email address.");
        }

        UUID teamId = invitation.getTeam().getId();
        if (teamMembershipRepository.existsByTeamIdAndUserId(teamId, acceptingUser.getId())) {
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitationRepository.save(invitation);
            return teamMembershipRepository.findByTeamIdAndUserId(teamId, acceptingUser.getId()).orElseThrow();
        }

        TeamMembership membership = teamMembershipService.addMember(invitation.getTeam(), acceptingUser, TeamRole.MEMBER);
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
        return membership;
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q test -Dtest=InvitationServiceTest`
Expected: PASS

- [ ] **Step 8: Implement the DTOs and `InvitationController`**

```java
package com.airtribe.tasktracker.team.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateInvitationRequest(
        @NotBlank(message = "is required") @Email(message = "must be a valid email") String email
) {
}
```

```java
package com.airtribe.tasktracker.team.dto;

import com.airtribe.tasktracker.team.Invitation;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(UUID id, UUID teamId, String email, String token, String status, Instant expiresAt) {
    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(), invitation.getTeam().getId(), invitation.getEmail(),
                invitation.getToken(), invitation.getStatus().name(), invitation.getExpiresAt());
    }
}
```

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import com.airtribe.tasktracker.team.dto.CreateInvitationRequest;
import com.airtribe.tasktracker.team.dto.InvitationResponse;
import com.airtribe.tasktracker.team.dto.TeamMemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/teams/{teamId}/invitations")
    public ResponseEntity<ApiResponse<InvitationResponse>> invite(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID teamId,
                                                                    @Valid @RequestBody CreateInvitationRequest request) {
        Invitation invitation = invitationService.createInvitation(teamId, principal.getUserId(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(InvitationResponse.from(invitation)));
    }

    @PostMapping("/api/invitations/{token}/accept")
    public ApiResponse<TeamMemberResponse> accept(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable String token) {
        TeamMembership membership = invitationService.acceptInvitation(token, principal.getUser());
        return ApiResponse.ok(TeamMemberResponse.from(membership));
    }
}
```

- [ ] **Step 9: Write and run the integration test**

```java
package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvitationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email, String name) throws Exception {
        String body = """
                {"name":"%s","email":"%s","password":"supersecret1"}
                """.formatted(name, email);
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    private String createTeam(String ownerToken, String name) throws Exception {
        String body = """
                {"name":"%s","description":"desc"}
                """.formatted(name);
        String json = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("id").asText();
    }

    @Test
    void ownerInvitesAndInviteeAccepts() throws Exception {
        String ownerToken = register("owner3@example.com", "Owner Three");
        String teamId = createTeam(ownerToken, "Marketing");
        String inviteeToken = register("invitee@example.com", "Invitee");

        String inviteBody = """
                {"email":"invitee@example.com"}
                """;
        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(inviteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(inviteJson).get("data").get("token").asText();

        mockMvc.perform(post("/api/invitations/" + token + "/accept")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));

        mockMvc.perform(get("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void nonAdminCannotInvite() throws Exception {
        String ownerToken = register("owner4@example.com", "Owner Four");
        String teamId = createTeam(ownerToken, "Sales");
        String memberToken = register("member@example.com", "Member");

        String inviteMemberBody = """
                {"email":"member@example.com"}
                """;
        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(inviteMemberBody))
                .andReturn().getResponse().getContentAsString();
        String memberInviteToken = objectMapper.readTree(inviteJson).get("data").get("token").asText();
        mockMvc.perform(post("/api/invitations/" + memberInviteToken + "/accept")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        String outsiderInviteBody = """
                {"email":"someone@example.com"}
                """;
        mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON).content(outsiderInviteBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptingWithWrongEmailIsForbidden() throws Exception {
        String ownerToken = register("owner5@example.com", "Owner Five");
        String teamId = createTeam(ownerToken, "Support");
        String wrongUserToken = register("wrong-person@example.com", "Wrong Person");

        String inviteBody = """
                {"email":"intended@example.com"}
                """;
        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(inviteBody))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(inviteJson).get("data").get("token").asText();

        mockMvc.perform(post("/api/invitations/" + token + "/accept")
                        .header("Authorization", "Bearer " + wrongUserToken))
                .andExpect(status().isForbidden());
    }
}
```

Run: `mvn -q test -Dtest=InvitationControllerIT`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/config/SecurityConfig.java \
        src/main/java/com/airtribe/tasktracker/team src/test/java/com/airtribe/tasktracker/team
git commit -m "feat: add team invitation create/accept flow"
```

### Task 10: Task Module — CRUD, Filtering, Search, Pagination

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/task/TaskStatus.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/TaskPriority.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/Task.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/TaskRepository.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/TaskSpecifications.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/TaskService.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/TaskController.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/dto/CreateTaskRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/dto/UpdateTaskRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/dto/TaskResponse.java`
- Test: `src/test/java/com/airtribe/tasktracker/task/TaskServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/task/TaskControllerIT.java`

**Interfaces:**
- Consumes: `Team`, `TeamMembership`, `TeamMembershipService`, `TeamRole` (Task 8), `User` (Task 4), `UserPrincipal` (Task 5).
- Produces: `TaskStatus` (`OPEN`, `IN_PROGRESS`, `COMPLETED`), `TaskPriority` (`LOW`, `MEDIUM`, `HIGH`). `TaskService.createTask(UUID teamId, UUID creatorId, String title, String description, TaskPriority priority, Instant dueDate) Task`, `TaskService.getTaskForMember(UUID taskId, UUID requesterId) Task` (throws `NotFoundException`/`ForbiddenException`), `TaskService.updateTask(UUID taskId, UUID actingUserId, String title, String description, TaskPriority priority, Instant dueDate) Task`, `TaskService.deleteTask(UUID taskId, UUID actingUserId) void`, `TaskService.listTasks(UUID teamId, UUID requesterId, TaskStatus status, UUID assigneeId, String search, Pageable pageable) Page<Task>`. **Task 11 (status/assignment) and Task 12 (comments) and Task 13 (attachments) all call `TaskService.getTaskForMember` to load a task while enforcing team membership, and reuse the same "creator, assignee, or team admin/owner" edit rule — so that rule lives in one private helper, `requireCanEdit`, in this service.**

- [ ] **Step 1: Write the failing unit tests for `TaskService`**

```java
package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.team.*;
import com.airtribe.tasktracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TeamService teamService;
    @Mock private TeamMembershipService teamMembershipService;

    private TaskService service() {
        return new TaskService(taskRepository, teamService, teamMembershipService);
    }

    private Team sampleTeam(UUID id) {
        Team team = new Team();
        team.setId(id);
        return team;
    }

    private User sampleUser(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Task sampleTask(UUID teamId, UUID creatorId, UUID assigneeId) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setTeam(sampleTeam(teamId));
        task.setCreatedBy(sampleUser(creatorId));
        task.setStatus(TaskStatus.OPEN);
        task.setPriority(TaskPriority.MEDIUM);
        if (assigneeId != null) {
            task.setAssignee(sampleUser(assigneeId));
        }
        return task;
    }

    @Test
    void createTaskRequiresTeamMembership() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        when(teamMembershipService.requireMember(eq(teamId), eq(creatorId)))
                .thenThrow(new ForbiddenException("You are not a member of this team."));

        assertThatThrownBy(() -> service().createTask(teamId, creatorId, "Title", "Desc", TaskPriority.HIGH, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createTaskDefaultsPriorityToMediumWhenNull() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        TeamMembership membership = new TeamMembership();
        membership.setUser(sampleUser(creatorId));
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(membership);
        when(teamService.getTeam(teamId)).thenReturn(sampleTeam(teamId));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task task = service().createTask(teamId, creatorId, "Title", "Desc", null, null);

        assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    void getTaskForMemberThrowsNotFoundWhenMissing() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getTaskForMember(taskId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateTaskAllowedForCreator() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId, null);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(membership);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service().updateTask(task.getId(), creatorId, "New Title", "New Desc", TaskPriority.LOW, null);

        assertThat(updated.getTitle()).isEqualTo("New Title");
    }

    @Test
    void updateTaskForbiddenForUnrelatedMember() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID unrelatedUserId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId, null);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, unrelatedUserId)).thenReturn(membership);

        assertThatThrownBy(() -> service().updateTask(task.getId(), unrelatedUserId, "X", "Y", TaskPriority.LOW, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateTaskAllowedForTeamAdmin() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId, null);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.ADMIN);
        when(teamMembershipService.requireMember(teamId, adminId)).thenReturn(membership);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service().updateTask(task.getId(), adminId, "Admin Edit", "Y", TaskPriority.LOW, null);

        assertThat(updated.getTitle()).isEqualTo("Admin Edit");
    }

    @Test
    void deleteTaskDelegatesToRepositoryWhenAllowed() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId, null);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(membership);

        service().deleteTask(task.getId(), creatorId);

        verify(taskRepository).delete(task);
    }

    private static UUID eq(UUID id) {
        return org.mockito.ArgumentMatchers.eq(id);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TaskServiceTest`
Expected: FAIL — none of the task classes exist.

- [ ] **Step 3: Implement `TaskStatus`, `TaskPriority`, `Task`**

```java
package com.airtribe.tasktracker.task;

public enum TaskStatus {
    OPEN, IN_PROGRESS, COMPLETED
}
```

```java
package com.airtribe.tasktracker.task;

public enum TaskPriority {
    LOW, MEDIUM, HIGH
}
```

```java
package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.common.persistence.AuditableEntity;
import com.airtribe.tasktracker.team.Team;
import com.airtribe.tasktracker.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "tasks")
public class Task extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    @Column(name = "due_date")
    private Instant dueDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;
}
```

- [ ] **Step 4: Implement `TaskRepository` and `TaskSpecifications`**

```java
package com.airtribe.tasktracker.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {
}
```

```java
package com.airtribe.tasktracker.task;

import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> belongsToTeam(UUID teamId) {
        return (root, query, cb) -> cb.equal(root.get("team").get("id"), teamId);
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasAssignee(UUID assigneeId) {
        return (root, query, cb) -> assigneeId == null ? null : cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<Task> assignedToUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), userId);
    }

    public static Specification<Task> titleOrDescriptionContains(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String pattern = "%" + q.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern));
        };
    }
}
```

- [ ] **Step 5: Implement `TaskService`**

```java
package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.team.Team;
import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamMembershipService;
import com.airtribe.tasktracker.team.TeamRole;
import com.airtribe.tasktracker.team.TeamService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TeamService teamService;
    private final TeamMembershipService teamMembershipService;

    public TaskService(TaskRepository taskRepository, TeamService teamService,
                        TeamMembershipService teamMembershipService) {
        this.taskRepository = taskRepository;
        this.teamService = teamService;
        this.teamMembershipService = teamMembershipService;
    }

    public Task createTask(UUID teamId, UUID creatorId, String title, String description,
                            TaskPriority priority, Instant dueDate) {
        TeamMembership membership = teamMembershipService.requireMember(teamId, creatorId);
        Team team = teamService.getTeam(teamId);

        Task task = new Task();
        task.setTeam(team);
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(TaskStatus.OPEN);
        task.setPriority(priority == null ? TaskPriority.MEDIUM : priority);
        task.setDueDate(dueDate);
        task.setCreatedBy(membership.getUser());
        return taskRepository.save(task);
    }

    public Task getTaskForMember(UUID taskId, UUID requesterId) {
        Task task = findById(taskId);
        teamMembershipService.requireMember(task.getTeam().getId(), requesterId);
        return task;
    }

    public Task updateTask(UUID taskId, UUID actingUserId, String title, String description,
                            TaskPriority priority, Instant dueDate) {
        Task task = findById(taskId);
        requireCanEdit(task, actingUserId);
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority == null ? task.getPriority() : priority);
        task.setDueDate(dueDate);
        return taskRepository.save(task);
    }

    public void deleteTask(UUID taskId, UUID actingUserId) {
        Task task = findById(taskId);
        requireCanEdit(task, actingUserId);
        taskRepository.delete(task);
    }

    public Page<Task> listTasks(UUID teamId, UUID requesterId, TaskStatus status, UUID assigneeId,
                                 String search, Pageable pageable) {
        teamMembershipService.requireMember(teamId, requesterId);
        Specification<Task> spec = Specification.where(TaskSpecifications.belongsToTeam(teamId))
                .and(TaskSpecifications.hasStatus(status))
                .and(TaskSpecifications.hasAssignee(assigneeId))
                .and(TaskSpecifications.titleOrDescriptionContains(search));
        return taskRepository.findAll(spec, pageable);
    }

    Task findById(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found."));
    }

    void requireCanEdit(Task task, UUID userId) {
        TeamMembership membership = teamMembershipService.requireMember(task.getTeam().getId(), userId);
        boolean isCreator = task.getCreatedBy().getId().equals(userId);
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(userId);
        boolean isTeamAdmin = membership.getRole() == TeamRole.OWNER || membership.getRole() == TeamRole.ADMIN;
        if (!isCreator && !isAssignee && !isTeamAdmin) {
            throw new ForbiddenException("You do not have permission to modify this task.");
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q test -Dtest=TaskServiceTest`
Expected: PASS

- [ ] **Step 7: Implement the DTOs**

```java
package com.airtribe.tasktracker.task.dto;

import com.airtribe.tasktracker.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateTaskRequest(
        @NotBlank(message = "is required") String title,
        String description,
        TaskPriority priority,
        Instant dueDate
) {
}
```

```java
package com.airtribe.tasktracker.task.dto;

import com.airtribe.tasktracker.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record UpdateTaskRequest(
        @NotBlank(message = "is required") String title,
        String description,
        TaskPriority priority,
        Instant dueDate
) {
}
```

```java
package com.airtribe.tasktracker.task.dto;

import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskPriority;
import com.airtribe.tasktracker.task.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(UUID id, UUID teamId, String title, String description, TaskStatus status,
                            TaskPriority priority, Instant dueDate, UUID createdBy, UUID assigneeId,
                            Instant createdAt, Instant updatedAt) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(), task.getTeam().getId(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getPriority(), task.getDueDate(), task.getCreatedBy().getId(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
```

- [ ] **Step 8: Implement `TaskController`**

```java
package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.common.web.PageMeta;
import com.airtribe.tasktracker.security.UserPrincipal;
import com.airtribe.tasktracker.task.dto.CreateTaskRequest;
import com.airtribe.tasktracker.task.dto.TaskResponse;
import com.airtribe.tasktracker.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
public class TaskController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "dueDate", "title", "priority", "status");

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/teams/{teamId}/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
                                                              @PathVariable UUID teamId,
                                                              @Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.createTask(teamId, principal.getUserId(), request.title(), request.description(),
                request.priority(), request.dueDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(TaskResponse.from(task)));
    }

    @GetMapping("/api/teams/{teamId}/tasks")
    public ApiResponse<List<TaskResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID teamId,
                                                 @RequestParam(required = false) TaskStatus status,
                                                 @RequestParam(required = false) UUID assignee,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "createdAt,desc") String sort,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int limit) {
        PageRequest pageRequest = PageRequest.of(page, limit, parseSort(sort));
        Page<Task> result = taskService.listTasks(teamId, principal.getUserId(), status, assignee, q, pageRequest);
        List<TaskResponse> data = result.getContent().stream().map(TaskResponse::from).toList();
        return ApiResponse.ok(data, new PageMeta(page, limit, result.getTotalElements()));
    }

    @GetMapping("/api/tasks/{taskId}")
    public ApiResponse<TaskResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID taskId) {
        return ApiResponse.ok(TaskResponse.from(taskService.getTaskForMember(taskId, principal.getUserId())));
    }

    @PutMapping("/api/tasks/{taskId}")
    public ApiResponse<TaskResponse> update(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID taskId,
                                             @Valid @RequestBody UpdateTaskRequest request) {
        Task task = taskService.updateTask(taskId, principal.getUserId(), request.title(), request.description(),
                request.priority(), request.dueDate());
        return ApiResponse.ok(TaskResponse.from(task));
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID taskId) {
        taskService.deleteTask(taskId, principal.getUserId());
        return ApiResponse.ok(null);
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0];
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new BadRequestException("Cannot sort by '" + field + "'.");
        }
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
```

- [ ] **Step 9: Write and run the integration test**

```java
package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"name":"User","email":"%s","password":"supersecret1"}
                """.formatted(email);
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    private String createTeam(String token) throws Exception {
        String json = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("id").asText();
    }

    @Test
    void memberCanCreateListFilterAndSearchTasks() throws Exception {
        String token = register("taskowner1@example.com");
        String teamId = createTeam(token);

        mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write proposal","description":"Draft the Q3 proposal","priority":"HIGH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Fix bug","description":"Null pointer in checkout"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(2));

        mockMvc.perform(get("/api/teams/" + teamId + "/tasks?q=proposal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Write proposal"));
    }

    @Test
    void onlyCreatorAssigneeOrAdminCanEditTask() throws Exception {
        String ownerToken = register("taskowner2@example.com");
        String teamId = createTeam(ownerToken);
        String outsiderToken = register("taskoutsider@example.com");

        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Original","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskJson).get("data").get("id").asText();

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked","description":"d"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated by creator","description":"d"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated by creator"));
    }
}
```

Run: `mvn -q test -Dtest=TaskControllerIT`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/task src/test/java/com/airtribe/tasktracker/task
git commit -m "feat: add task CRUD with filtering, search, and pagination"
```

### Task 11: Task Status Transitions, Assignment, and "My Tasks"

**Files:**
- Modify: `src/main/java/com/airtribe/tasktracker/task/TaskService.java` — add `changeStatus`, `assignTask`, `listMyTasks`
- Modify: `src/main/java/com/airtribe/tasktracker/task/TaskController.java` — add `PATCH /api/tasks/{taskId}/status`, `PATCH /api/tasks/{taskId}/assign`, `GET /api/tasks/mine`
- Create: `src/main/java/com/airtribe/tasktracker/task/dto/ChangeStatusRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/task/dto/AssignTaskRequest.java`
- Test: `src/test/java/com/airtribe/tasktracker/task/TaskAssignmentServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/task/TaskAssignmentControllerIT.java`

**Interfaces:**
- Consumes: `TaskService` internals from Task 10 (`findById`, `requireCanEdit`, `taskRepository`, `teamMembershipService`), `TaskSpecifications` (Task 10).
- Produces: `TaskService.changeStatus(UUID taskId, UUID actingUserId, TaskStatus newStatus) Task`, `TaskService.assignTask(UUID taskId, UUID actingUserId, UUID assigneeId) Task` (throws `BadRequestException` if `assigneeId` isn't a member of the task's team), `TaskService.listMyTasks(UUID userId, TaskStatus status, String search, Pageable pageable) Page<Task>`.

- [ ] **Step 1: Write the failing unit tests**

```java
package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.team.Team;
import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamMembershipService;
import com.airtribe.tasktracker.team.TeamRole;
import com.airtribe.tasktracker.team.TeamService;
import com.airtribe.tasktracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskAssignmentServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TeamService teamService;
    @Mock private TeamMembershipService teamMembershipService;

    private TaskService service() {
        return new TaskService(taskRepository, teamService, teamMembershipService);
    }

    private Team sampleTeam(UUID id) {
        Team team = new Team();
        team.setId(id);
        return team;
    }

    private User sampleUser(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Task sampleTask(UUID teamId, UUID creatorId) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setTeam(sampleTeam(teamId));
        task.setCreatedBy(sampleUser(creatorId));
        task.setStatus(TaskStatus.OPEN);
        task.setPriority(TaskPriority.MEDIUM);
        return task;
    }

    @Test
    void changeStatusUpdatesAndSaves() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(membership);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service().changeStatus(task.getId(), creatorId, TaskStatus.IN_PROGRESS);

        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void assignTaskRequiresAssigneeToBeTeamMember() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID nonMemberId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership creatorMembership = new TeamMembership();
        creatorMembership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(creatorMembership);
        when(teamMembershipService.requireMember(teamId, nonMemberId))
                .thenThrow(new ForbiddenException("You are not a member of this team."));

        assertThatThrownBy(() -> service().assignTask(task.getId(), creatorId, nonMemberId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void assignTaskSetsAssigneeWhenValid() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership creatorMembership = new TeamMembership();
        creatorMembership.setRole(TeamRole.MEMBER);
        TeamMembership assigneeMembership = new TeamMembership();
        assigneeMembership.setUser(sampleUser(assigneeId));
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(creatorMembership);
        when(teamMembershipService.requireMember(teamId, assigneeId)).thenReturn(assigneeMembership);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service().assignTask(task.getId(), creatorId, assigneeId);

        assertThat(updated.getAssignee().getId()).isEqualTo(assigneeId);
    }

    @Test
    void listMyTasksFiltersByAssignee() {
        UUID userId = UUID.randomUUID();
        org.springframework.data.domain.Page<Task> emptyPage =
                org.springframework.data.domain.Page.empty();
        when(taskRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Task>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(emptyPage);

        org.springframework.data.domain.Page<Task> result = service().listMyTasks(
                userId, null, null, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result).isEqualTo(emptyPage);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TaskAssignmentServiceTest`
Expected: FAIL — `changeStatus`, `assignTask`, `listMyTasks` don't exist on `TaskService` yet.

- [ ] **Step 3: Add the three methods to `TaskService`**

Insert these methods into `TaskService`, directly after the existing `deleteTask` method:

```java
    public Task changeStatus(UUID taskId, UUID actingUserId, TaskStatus newStatus) {
        Task task = findById(taskId);
        requireCanEdit(task, actingUserId);
        task.setStatus(newStatus);
        return taskRepository.save(task);
    }

    public Task assignTask(UUID taskId, UUID actingUserId, UUID assigneeId) {
        Task task = findById(taskId);
        requireCanEdit(task, actingUserId);
        TeamMembership assigneeMembership;
        try {
            assigneeMembership = teamMembershipService.requireMember(task.getTeam().getId(), assigneeId);
        } catch (ForbiddenException ex) {
            throw new BadRequestException("The assignee must be a member of this team.");
        }
        task.setAssignee(assigneeMembership.getUser());
        return taskRepository.save(task);
    }

    public Page<Task> listMyTasks(UUID userId, TaskStatus status, String search, Pageable pageable) {
        Specification<Task> spec = Specification.where(TaskSpecifications.assignedToUser(userId))
                .and(TaskSpecifications.hasStatus(status))
                .and(TaskSpecifications.titleOrDescriptionContains(search));
        return taskRepository.findAll(spec, pageable);
    }
```

Add the missing import to the top of `TaskService.java`:

```java
import com.airtribe.tasktracker.common.exception.BadRequestException;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=TaskAssignmentServiceTest`
Expected: PASS

- [ ] **Step 5: Implement the DTOs**

```java
package com.airtribe.tasktracker.task.dto;

import com.airtribe.tasktracker.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull(message = "is required") TaskStatus status) {
}
```

```java
package com.airtribe.tasktracker.task.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignTaskRequest(@NotNull(message = "is required") UUID assigneeId) {
}
```

- [ ] **Step 6: Add the three endpoints to `TaskController`**

Insert these methods directly after the existing `delete` method, before `parseSort`:

```java
    @PatchMapping("/api/tasks/{taskId}/status")
    public ApiResponse<TaskResponse> changeStatus(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID taskId,
                                                    @jakarta.validation.Valid @RequestBody ChangeStatusRequest request) {
        Task task = taskService.changeStatus(taskId, principal.getUserId(), request.status());
        return ApiResponse.ok(TaskResponse.from(task));
    }

    @PatchMapping("/api/tasks/{taskId}/assign")
    public ApiResponse<TaskResponse> assign(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID taskId,
                                             @jakarta.validation.Valid @RequestBody AssignTaskRequest request) {
        Task task = taskService.assignTask(taskId, principal.getUserId(), request.assigneeId());
        return ApiResponse.ok(TaskResponse.from(task));
    }

    @GetMapping("/api/tasks/mine")
    public ApiResponse<List<TaskResponse>> mine(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestParam(required = false) TaskStatus status,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "createdAt,desc") String sort,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int limit) {
        PageRequest pageRequest = PageRequest.of(page, limit, parseSort(sort));
        Page<Task> result = taskService.listMyTasks(principal.getUserId(), status, q, pageRequest);
        List<TaskResponse> data = result.getContent().stream().map(TaskResponse::from).toList();
        return ApiResponse.ok(data, new PageMeta(page, limit, result.getTotalElements()));
    }
```

Add the missing imports to the top of `TaskController.java`:

```java
import com.airtribe.tasktracker.task.dto.AssignTaskRequest;
import com.airtribe.tasktracker.task.dto.ChangeStatusRequest;
```

- [ ] **Step 7: Write and run the integration test**

```java
package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskAssignmentControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"User","email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    private String createTeam(String token) throws Exception {
        String json = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("id").asText();
    }

    private String inviteAndAccept(String ownerToken, String teamId, String inviteeToken, String inviteeEmail) throws Exception {
        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + inviteeEmail + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(inviteJson).get("data").get("token").asText();
        String acceptJson = mockMvc.perform(post("/api/invitations/" + token + "/accept")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(acceptJson).get("data").get("userId").asText();
    }

    @Test
    void statusTransitionAssignmentAndMineFlow() throws Exception {
        String ownerToken = register("assignowner@example.com");
        String teamId = createTeam(ownerToken);
        String memberToken = register("assignee@example.com");
        String memberId = inviteAndAccept(ownerToken, teamId, memberToken, "assignee@example.com");

        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ship feature","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskJson).get("data").get("id").asText();

        mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + memberId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(memberId));

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/tasks/mine")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Ship feature"));
    }
}
```

Run: `mvn -q test -Dtest=TaskAssignmentControllerIT`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/task src/test/java/com/airtribe/tasktracker/task
git commit -m "feat: add task status transitions, assignment, and my-tasks endpoint"
```

### Task 12: Comment Module

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/comment/Comment.java`
- Create: `src/main/java/com/airtribe/tasktracker/comment/CommentRepository.java`
- Create: `src/main/java/com/airtribe/tasktracker/comment/CommentService.java`
- Create: `src/main/java/com/airtribe/tasktracker/comment/CommentController.java`
- Create: `src/main/java/com/airtribe/tasktracker/comment/dto/CreateCommentRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/comment/dto/CommentResponse.java`
- Test: `src/test/java/com/airtribe/tasktracker/comment/CommentServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/comment/CommentControllerIT.java`

**Interfaces:**
- Consumes: `Task`, `TaskService.getTaskForMember` (Task 10), `User`, `UserService` (Task 4).
- Produces: `CommentService.addComment(UUID taskId, UUID authorId, String body) Comment` (throws whatever `getTaskForMember` throws — `NotFoundException`/`ForbiddenException`), `CommentService.listComments(UUID taskId, UUID requesterId, Pageable pageable) Page<Comment>`. **Task 14 (notifications) wraps `addComment` to publish a `COMMENT_ADDED` notification to the task's assignee.**

- [ ] **Step 1: Write the failing unit tests**

```java
package com.airtribe.tasktracker.comment;

import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskService;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private TaskService taskService;
    @Mock private UserService userService;

    private CommentService service() {
        return new CommentService(commentRepository, taskService, userService);
    }

    @Test
    void addCommentSavesAndReturnsComment() {
        UUID taskId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Task task = new Task();
        task.setId(taskId);
        User author = new User();
        author.setId(authorId);
        when(taskService.getTaskForMember(taskId, authorId)).thenReturn(task);
        when(userService.findById(authorId)).thenReturn(author);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment comment = service().addComment(taskId, authorId, "Looks good to me");

        assertThat(comment.getBody()).isEqualTo("Looks good to me");
        assertThat(comment.getTask()).isSameAs(task);
        assertThat(comment.getAuthor()).isSameAs(author);
    }

    @Test
    void addCommentPropagatesForbiddenFromTaskLookup() {
        UUID taskId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(taskService.getTaskForMember(taskId, authorId))
                .thenThrow(new ForbiddenException("You are not a member of this team."));

        assertThatThrownBy(() -> service().addComment(taskId, authorId, "Hi"))
                .isInstanceOf(ForbiddenException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void listCommentsRequiresTaskMembershipThenDelegatesToRepository() {
        UUID taskId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Task task = new Task();
        task.setId(taskId);
        when(taskService.getTaskForMember(taskId, requesterId)).thenReturn(task);
        Page<Comment> page = Page.empty();
        when(commentRepository.findByTaskId(taskId, PageRequest.of(0, 20))).thenReturn(page);

        Page<Comment> result = service().listComments(taskId, requesterId, PageRequest.of(0, 20));

        assertThat(result).isEqualTo(page);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=CommentServiceTest`
Expected: FAIL — none of the comment classes exist.

- [ ] **Step 3: Implement `Comment` entity and `CommentRepository`**

```java
package com.airtribe.tasktracker.comment;

import com.airtribe.tasktracker.common.persistence.AuditableEntity;
import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "comments")
public class Comment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
}
```

```java
package com.airtribe.tasktracker.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    Page<Comment> findByTaskId(UUID taskId, Pageable pageable);
}
```

- [ ] **Step 4: Implement `CommentService`**

```java
package com.airtribe.tasktracker.comment;

import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskService;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskService taskService;
    private final UserService userService;

    public CommentService(CommentRepository commentRepository, TaskService taskService, UserService userService) {
        this.commentRepository = commentRepository;
        this.taskService = taskService;
        this.userService = userService;
    }

    public Comment addComment(UUID taskId, UUID authorId, String body) {
        Task task = taskService.getTaskForMember(taskId, authorId);
        User author = userService.findById(authorId);

        Comment comment = new Comment();
        comment.setTask(task);
        comment.setAuthor(author);
        comment.setBody(body);
        return commentRepository.save(comment);
    }

    public Page<Comment> listComments(UUID taskId, UUID requesterId, Pageable pageable) {
        taskService.getTaskForMember(taskId, requesterId);
        return commentRepository.findByTaskId(taskId, pageable);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=CommentServiceTest`
Expected: PASS

- [ ] **Step 6: Implement the DTOs and `CommentController`**

```java
package com.airtribe.tasktracker.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(@NotBlank(message = "is required") String body) {
}
```

```java
package com.airtribe.tasktracker.comment.dto;

import com.airtribe.tasktracker.comment.Comment;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, UUID taskId, UUID authorId, String authorName, String body, Instant createdAt) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(), comment.getTask().getId(), comment.getAuthor().getId(),
                comment.getAuthor().getName(), comment.getBody(), comment.getCreatedAt());
    }
}
```

```java
package com.airtribe.tasktracker.comment;

import com.airtribe.tasktracker.comment.dto.CommentResponse;
import com.airtribe.tasktracker.comment.dto.CreateCommentRequest;
import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.common.web.PageMeta;
import com.airtribe.tasktracker.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable UUID taskId,
                                                                 @Valid @RequestBody CreateCommentRequest request) {
        Comment comment = commentService.addComment(taskId, principal.getUserId(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(CommentResponse.from(comment)));
    }

    @GetMapping
    public ApiResponse<List<CommentResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable UUID taskId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int limit) {
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<Comment> result = commentService.listComments(taskId, principal.getUserId(), pageRequest);
        List<CommentResponse> data = result.getContent().stream().map(CommentResponse::from).toList();
        return ApiResponse.ok(data, new PageMeta(page, limit, result.getTotalElements()));
    }
}
```

- [ ] **Step 7: Write and run the integration test**

```java
package com.airtribe.tasktracker.comment;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommentControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"User","email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    private String createTeamAndTask(String token) throws Exception {
        String teamJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(teamJson).get("data").get("id").asText();
        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Discuss design","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(taskJson).get("data").get("id").asText();
    }

    @Test
    void memberCanPostAndListComments() throws Exception {
        String token = register("commenter@example.com");
        String taskId = createTeamAndTask(token);

        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Looks good"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.body").value("Looks good"));

        mockMvc.perform(get("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void nonMemberCannotComment() throws Exception {
        String ownerToken = register("commentowner@example.com");
        String taskId = createTeamAndTask(ownerToken);
        String outsiderToken = register("commentoutsider@example.com");

        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Sneaky comment"}
                                """))
                .andExpect(status().isForbidden());
    }
}
```

Run: `mvn -q test -Dtest=CommentControllerIT`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/comment src/test/java/com/airtribe/tasktracker/comment
git commit -m "feat: add task comments"
```

### Task 13: Attachment Module — Storage Abstraction, Upload, Download, Delete

**Note on the API envelope constraint:** the download endpoint in this task returns raw file bytes with a `Content-Disposition` header, not an `ApiResponse`-wrapped JSON body. This is a deliberate, narrow exception to the Task 2 global constraint — binary content cannot be JSON-wrapped — and is the only endpoint in the system that does this.

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/attachment/StorageProperties.java`
- Create: `src/main/java/com/airtribe/tasktracker/attachment/StorageService.java`
- Create: `src/main/java/com/airtribe/tasktracker/attachment/LocalDiskStorageService.java`
- Create: `src/main/java/com/airtribe/tasktracker/attachment/Attachment.java`
- Create: `src/main/java/com/airtribe/tasktracker/attachment/AttachmentRepository.java`
- Create: `src/main/java/com/airtribe/tasktracker/attachment/AttachmentService.java`
- Create: `src/main/java/com/airtribe/tasktracker/attachment/AttachmentController.java`
- Create: `src/main/java/com/airtribe/tasktracker/attachment/dto/AttachmentResponse.java`
- Test: `src/test/java/com/airtribe/tasktracker/attachment/LocalDiskStorageServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/attachment/AttachmentServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/attachment/AttachmentControllerIT.java`

**Interfaces:**
- Consumes: `Task`, `TaskService.getTaskForMember` (Task 10), `TeamMembership`, `TeamMembershipService`, `TeamRole` (Task 8), `User`, `UserService` (Task 4).
- Produces: `StorageService.store(UUID taskId, String originalFilename, InputStream content, long size) String` (relative storage path, throws `IOException`), `StorageService.load(String storagePath) org.springframework.core.io.Resource`, `StorageService.delete(String storagePath) void` — implemented by `LocalDiskStorageService`, swappable later for an S3-backed implementation without touching `AttachmentService`. `AttachmentService.upload(UUID taskId, UUID uploaderId, MultipartFile file) Attachment` (throws `BadRequestException` on oversized/disallowed files), `AttachmentService.download(UUID taskId, UUID attachmentId, UUID requesterId) AttachmentService.AttachmentDownload` (record of `Attachment` + `Resource`, throws `NotFoundException`), `AttachmentService.delete(UUID taskId, UUID attachmentId, UUID requesterId) void` (throws `ForbiddenException` unless requester is the uploader or a team `ADMIN`/`OWNER`).

- [ ] **Step 1: Write the failing unit test for `LocalDiskStorageService`**

```java
package com.airtribe.tasktracker.attachment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDiskStorageServiceTest {

    private LocalDiskStorageService service(@TempDir Path tempDir) {
        StorageProperties properties = new StorageProperties();
        properties.setRootDir(tempDir.toString());
        return new LocalDiskStorageService(properties);
    }

    @Test
    void storesAndLoadsFileContent(@TempDir Path tempDir) throws Exception {
        LocalDiskStorageService storage = service(tempDir);
        UUID taskId = UUID.randomUUID();
        byte[] content = "hello attachment".getBytes(StandardCharsets.UTF_8);

        String storagePath = storage.store(taskId, "notes.txt", new ByteArrayInputStream(content), content.length);
        Resource resource = storage.load(storagePath);

        try (InputStream in = resource.getInputStream()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello attachment");
        }
    }

    @Test
    void deleteRemovesTheStoredFile(@TempDir Path tempDir) throws Exception {
        LocalDiskStorageService storage = service(tempDir);
        UUID taskId = UUID.randomUUID();
        byte[] content = "bye".getBytes(StandardCharsets.UTF_8);
        String storagePath = storage.store(taskId, "notes.txt", new ByteArrayInputStream(content), content.length);

        storage.delete(storagePath);

        assertThat(storage.load(storagePath).exists()).isFalse();
    }

    @Test
    void sanitizesPathTraversalAttemptsInFilename(@TempDir Path tempDir) throws Exception {
        LocalDiskStorageService storage = service(tempDir);
        UUID taskId = UUID.randomUUID();
        byte[] content = "evil".getBytes(StandardCharsets.UTF_8);

        String storagePath = storage.store(taskId, "../../evil.txt", new ByteArrayInputStream(content), content.length);

        assertThat(storagePath).doesNotContain("..");
        assertThat(tempDir.resolve(storagePath).normalize().startsWith(tempDir)).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=LocalDiskStorageServiceTest`
Expected: FAIL — `StorageProperties`, `LocalDiskStorageService` don't exist.

- [ ] **Step 3: Implement `StorageProperties`, `StorageService`, `LocalDiskStorageService`**

```java
package com.airtribe.tasktracker.attachment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private String rootDir;
}
```

```java
package com.airtribe.tasktracker.attachment;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface StorageService {
    String store(UUID taskId, String originalFilename, InputStream content, long size) throws IOException;
    Resource load(String storagePath);
    void delete(String storagePath);
}
```

```java
package com.airtribe.tasktracker.attachment;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalDiskStorageService implements StorageService {

    private final Path rootDir;

    public LocalDiskStorageService(StorageProperties properties) {
        this.rootDir = Path.of(properties.getRootDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create storage root directory", e);
        }
    }

    @Override
    public String store(UUID taskId, String originalFilename, InputStream content, long size) throws IOException {
        Path taskDir = requireWithinRoot(rootDir.resolve(taskId.toString()).normalize(), rootDir);
        Files.createDirectories(taskDir);
        String storedName = UUID.randomUUID() + "_" + safeFilename(originalFilename);
        Path target = requireWithinRoot(taskDir.resolve(storedName).normalize(), taskDir);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return rootDir.relativize(target).toString();
    }

    @Override
    public Resource load(String storagePath) {
        Path target = requireWithinRoot(rootDir.resolve(storagePath).normalize(), rootDir);
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storagePath) {
        Path target = requireWithinRoot(rootDir.resolve(storagePath).normalize(), rootDir);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to delete attachment file", e);
        }
    }

    private Path requireWithinRoot(Path candidate, Path boundary) {
        if (!candidate.startsWith(boundary)) {
            throw new IllegalArgumentException("Invalid storage path.");
        }
        return candidate;
    }

    private String safeFilename(String original) {
        String name = original == null ? "file" : Path.of(original).getFileName().toString();
        return name.isBlank() ? "file" : name;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=LocalDiskStorageServiceTest`
Expected: PASS

- [ ] **Step 5: Write the failing unit tests for `AttachmentService`**

```java
package com.airtribe.tasktracker.attachment;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskService;
import com.airtribe.tasktracker.team.Team;
import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamMembershipService;
import com.airtribe.tasktracker.team.TeamRole;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private TaskService taskService;
    @Mock private TeamMembershipService teamMembershipService;
    @Mock private UserService userService;
    @Mock private StorageService storageService;

    private AttachmentService service() {
        return new AttachmentService(attachmentRepository, taskService, teamMembershipService, userService, storageService);
    }

    private Task sampleTask(UUID teamId) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        Team team = new Team();
        team.setId(teamId);
        task.setTeam(team);
        return task;
    }

    @Test
    void uploadRejectsFileOverSizeLimit() {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        when(taskService.getTaskForMember(task.getId(), uploaderId)).thenReturn(task);
        byte[] tooBig = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", tooBig);

        assertThatThrownBy(() -> service().upload(task.getId(), uploaderId, file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadRejectsDisallowedContentType() {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        when(taskService.getTaskForMember(task.getId(), uploaderId)).thenReturn(task);
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/x-msdownload", "x".getBytes());

        assertThatThrownBy(() -> service().upload(task.getId(), uploaderId, file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadSavesAttachmentWhenValid() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        User uploader = new User();
        uploader.setId(uploaderId);
        when(taskService.getTaskForMember(task.getId(), uploaderId)).thenReturn(task);
        when(userService.findById(uploaderId)).thenReturn(uploader);
        when(storageService.store(eq(task.getId()), any(), any(), anyLong())).thenReturn("path/to/file.txt");
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hi".getBytes());

        Attachment attachment = service().upload(task.getId(), uploaderId, file);

        assertThat(attachment.getStoragePath()).isEqualTo("path/to/file.txt");
        assertThat(attachment.getUploadedBy()).isSameAs(uploader);
    }

    @Test
    void deleteForbiddenForNonUploaderNonAdmin() {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        Attachment attachment = new Attachment();
        attachment.setId(UUID.randomUUID());
        User uploader = new User();
        uploader.setId(uploaderId);
        attachment.setUploadedBy(uploader);
        when(taskService.getTaskForMember(task.getId(), otherUserId)).thenReturn(task);
        when(attachmentRepository.findByIdAndTaskId(attachment.getId(), task.getId())).thenReturn(Optional.of(attachment));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, otherUserId)).thenReturn(membership);

        assertThatThrownBy(() -> service().delete(task.getId(), attachment.getId(), otherUserId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteAllowedForUploader() {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        Attachment attachment = new Attachment();
        attachment.setId(UUID.randomUUID());
        attachment.setStoragePath("some/path.txt");
        User uploader = new User();
        uploader.setId(uploaderId);
        attachment.setUploadedBy(uploader);
        when(taskService.getTaskForMember(task.getId(), uploaderId)).thenReturn(task);
        when(attachmentRepository.findByIdAndTaskId(attachment.getId(), task.getId())).thenReturn(Optional.of(attachment));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, uploaderId)).thenReturn(membership);

        service().delete(task.getId(), attachment.getId(), uploaderId);

        verify(storageService).delete("some/path.txt");
        verify(attachmentRepository).delete(attachment);
    }

    private static UUID eq(UUID id) {
        return org.mockito.ArgumentMatchers.eq(id);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn -q test -Dtest=AttachmentServiceTest`
Expected: FAIL — `Attachment`, `AttachmentRepository`, `AttachmentService` don't exist.

- [ ] **Step 7: Implement `Attachment` entity and `AttachmentRepository`**

```java
package com.airtribe.tasktracker.attachment;

import com.airtribe.tasktracker.common.persistence.AuditableEntity;
import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "attachments")
public class Attachment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(nullable = false)
    private String filename;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
}
```

```java
package com.airtribe.tasktracker.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    Optional<Attachment> findByIdAndTaskId(UUID id, UUID taskId);
}
```

- [ ] **Step 8: Implement `AttachmentService`**

```java
package com.airtribe.tasktracker.attachment;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskService;
import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamMembershipService;
import com.airtribe.tasktracker.team.TeamRole;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    public record AttachmentDownload(Attachment attachment, Resource resource) {
    }

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "application/pdf", "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip");

    private final AttachmentRepository attachmentRepository;
    private final TaskService taskService;
    private final TeamMembershipService teamMembershipService;
    private final UserService userService;
    private final StorageService storageService;

    public AttachmentService(AttachmentRepository attachmentRepository, TaskService taskService,
                              TeamMembershipService teamMembershipService, UserService userService,
                              StorageService storageService) {
        this.attachmentRepository = attachmentRepository;
        this.taskService = taskService;
        this.teamMembershipService = teamMembershipService;
        this.userService = userService;
        this.storageService = storageService;
    }

    public Attachment upload(UUID taskId, UUID uploaderId, MultipartFile file) {
        Task task = taskService.getTaskForMember(taskId, uploaderId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attachment file is required.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("Attachment exceeds the 10MB size limit.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Attachment content type '" + contentType + "' is not allowed.");
        }

        User uploader = userService.findById(uploaderId);
        String storagePath;
        try {
            storagePath = storageService.store(taskId, file.getOriginalFilename(), file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new BadRequestException("Unable to store attachment.");
        }

        Attachment attachment = new Attachment();
        attachment.setTask(task);
        attachment.setUploadedBy(uploader);
        attachment.setFilename(safeDisplayName(file.getOriginalFilename()));
        attachment.setStoragePath(storagePath);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        return attachmentRepository.save(attachment);
    }

    public AttachmentDownload download(UUID taskId, UUID attachmentId, UUID requesterId) {
        taskService.getTaskForMember(taskId, requesterId);
        Attachment attachment = findAttachment(taskId, attachmentId);
        return new AttachmentDownload(attachment, storageService.load(attachment.getStoragePath()));
    }

    public void delete(UUID taskId, UUID attachmentId, UUID requesterId) {
        Task task = taskService.getTaskForMember(taskId, requesterId);
        Attachment attachment = findAttachment(taskId, attachmentId);
        TeamMembership membership = teamMembershipService.requireMember(task.getTeam().getId(), requesterId);
        boolean isUploader = attachment.getUploadedBy().getId().equals(requesterId);
        boolean isTeamAdmin = membership.getRole() == TeamRole.OWNER || membership.getRole() == TeamRole.ADMIN;
        if (!isUploader && !isTeamAdmin) {
            throw new ForbiddenException("You do not have permission to delete this attachment.");
        }
        storageService.delete(attachment.getStoragePath());
        attachmentRepository.delete(attachment);
    }

    private Attachment findAttachment(UUID taskId, UUID attachmentId) {
        return attachmentRepository.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new NotFoundException("Attachment not found."));
    }

    private String safeDisplayName(String original) {
        String name = original == null ? "file" : Path.of(original).getFileName().toString();
        return name.isBlank() ? "file" : name;
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `mvn -q test -Dtest=AttachmentServiceTest`
Expected: PASS

- [ ] **Step 10: Implement the DTO and `AttachmentController`**

```java
package com.airtribe.tasktracker.attachment.dto;

import com.airtribe.tasktracker.attachment.Attachment;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(UUID id, UUID taskId, UUID uploadedBy, String filename,
                                  String contentType, long sizeBytes, Instant createdAt) {
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(), attachment.getTask().getId(), attachment.getUploadedBy().getId(),
                attachment.getFilename(), attachment.getContentType(), attachment.getSizeBytes(),
                attachment.getCreatedAt());
    }
}
```

```java
package com.airtribe.tasktracker.attachment;

import com.airtribe.tasktracker.attachment.dto.AttachmentResponse;
import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentResponse>> upload(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID taskId,
                                                                    @RequestParam("file") MultipartFile file) {
        Attachment attachment = attachmentService.upload(taskId, principal.getUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(AttachmentResponse.from(attachment)));
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID taskId, @PathVariable UUID attachmentId) {
        AttachmentService.AttachmentDownload download =
                attachmentService.download(taskId, attachmentId, principal.getUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.attachment().getFilename() + "\"")
                .contentType(MediaType.parseMediaType(download.attachment().getContentType()))
                .body(download.resource());
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID taskId, @PathVariable UUID attachmentId) {
        attachmentService.delete(taskId, attachmentId, principal.getUserId());
        return ApiResponse.ok(null);
    }
}
```

- [ ] **Step 11: Write and run the integration test**

```java
package com.airtribe.tasktracker.attachment;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"User","email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    private String createTeamAndTask(String token) throws Exception {
        String teamJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(teamJson).get("data").get("id").asText();
        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Attach spec","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(taskJson).get("data").get("id").asText();
    }

    @Test
    void uploadDownloadAndDeleteFlow() throws Exception {
        String token = register("uploader@example.com");
        String taskId = createTeamAndTask(token);
        MockMultipartFile file = new MockMultipartFile("file", "spec.txt", "text/plain", "hello world".getBytes());

        String uploadJson = mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.filename").value("spec.txt"))
                .andReturn().getResponse().getContentAsString();
        String attachmentId = objectMapper.readTree(uploadJson).get("data").get("id").asText();

        mockMvc.perform(get("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes("hello world".getBytes()));

        mockMvc.perform(delete("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadRejectsDisallowedContentTypeOverHttp() throws Exception {
        String token = register("uploader2@example.com");
        String taskId = createTeamAndTask(token);
        MockMultipartFile file = new MockMultipartFile("file", "app.exe", "application/x-msdownload", "x".getBytes());

        mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
```

Run: `mvn -q test -Dtest=AttachmentControllerIT`
Expected: PASS

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/attachment src/test/java/com/airtribe/tasktracker/attachment
git commit -m "feat: add task attachments with local storage abstraction"
```

### Task 14: Notifications — Persistence, REST, and Real-Time WebSocket Push

**Design note:** `Task`/`Comment` publish plain domain events (`TaskAssignedEvent`, `TaskUpdatedEvent`, `CommentAddedEvent`) via Spring's `ApplicationEventPublisher` instead of calling `NotificationService` directly. This keeps the dependency direction one-way (`task`/`comment` → `notification` event records only, no import of `NotificationService` itself) and means `TaskService`/`CommentService` gain one constructor parameter each, which requires updating the `service()` test helpers written in Tasks 10–12.

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/notification/NotificationType.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/Notification.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/NotificationRepository.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/NotificationService.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/NotificationController.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/dto/NotificationResponse.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/TaskAssignedEvent.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/TaskUpdatedEvent.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/CommentAddedEvent.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/NotificationEventListener.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/StompPrincipal.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/JwtHandshakeHandler.java`
- Create: `src/main/java/com/airtribe/tasktracker/notification/WebSocketConfig.java`
- Modify: `src/main/java/com/airtribe/tasktracker/task/TaskService.java` — add `ApplicationEventPublisher` and publish `TaskAssignedEvent`/`TaskUpdatedEvent`
- Modify: `src/main/java/com/airtribe/tasktracker/comment/CommentService.java` — add `ApplicationEventPublisher` and publish `CommentAddedEvent`
- Modify: `src/test/java/com/airtribe/tasktracker/task/TaskServiceTest.java` — update `service()` helper for the new constructor parameter
- Modify: `src/test/java/com/airtribe/tasktracker/task/TaskAssignmentServiceTest.java` — same
- Modify: `src/test/java/com/airtribe/tasktracker/comment/CommentServiceTest.java` — same
- Test: `src/test/java/com/airtribe/tasktracker/notification/NotificationServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/notification/NotificationControllerIT.java`
- Test: `src/test/java/com/airtribe/tasktracker/notification/WebSocketNotificationIT.java`

**Interfaces:**
- Consumes: `TaskService`, `Task` (Task 10/11), `CommentService`, `Comment` (Task 12), `UserService`, `User` (Task 4), `JwtService`, `JwtPrincipal` (Task 5).
- Produces: `NotificationType` (`TASK_ASSIGNED`, `TASK_UPDATED`, `COMMENT_ADDED`). `NotificationService.create(User recipient, NotificationType type, Map<String,Object> payload) Notification` (persists and pushes over STOMP to `/user/queue/notifications`). `NotificationService.list(UUID userId, Pageable pageable) Page<Notification>`, `NotificationService.markRead(UUID notificationId, UUID userId) Notification` (throws `NotFoundException`). WebSocket clients connect to `ws://<host>/ws/websocket?token=<accessToken>` (or `/ws` with SockJS) and subscribe to `/user/queue/notifications`.

- [ ] **Step 1: Write the failing unit test for `NotificationService`**

```java
package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private NotificationService service() {
        return new NotificationService(notificationRepository, messagingTemplate);
    }

    @Test
    void createSavesAndPushesToUserQueue() {
        UUID userId = UUID.randomUUID();
        User recipient = new User();
        recipient.setId(userId);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification notification = service().create(recipient, NotificationType.TASK_ASSIGNED, Map.of("taskId", "abc"));

        assertThat(notification.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(notification.isRead()).isFalse();
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/notifications"), any(Object.class));
    }

    @Test
    void markReadThrowsNotFoundWhenMissingOrNotOwned() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().markRead(id, userId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markReadSetsReadTrueAndSaves() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRead(false);
        when(notificationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service().markRead(id, userId);

        assertThat(result.isRead()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=NotificationServiceTest`
Expected: FAIL — none of the notification classes exist.

- [ ] **Step 3: Implement `NotificationType` and `Notification`**

```java
package com.airtribe.tasktracker.notification;

public enum NotificationType {
    TASK_ASSIGNED, TASK_UPDATED, COMMENT_ADDED
}
```

```java
package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.common.persistence.AuditableEntity;
import com.airtribe.tasktracker.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    private boolean read = false;
}
```

- [ ] **Step 4: Implement `NotificationRepository` and `NotificationService`**

```java
package com.airtribe.tasktracker.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserId(UUID userId, Pageable pageable);
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
}
```

```java
package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.notification.dto.NotificationResponse;
import com.airtribe.tasktracker.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Notification create(User recipient, NotificationType type, Map<String, Object> payload) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setPayload(payload);
        notification.setRead(false);
        notification = notificationRepository.save(notification);

        messagingTemplate.convertAndSendToUser(
                recipient.getId().toString(), "/queue/notifications", NotificationResponse.from(notification));
        return notification;
    }

    public Page<Notification> list(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable);
    }

    public Notification markRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found."));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=NotificationServiceTest`
Expected: PASS

- [ ] **Step 6: Implement the events, listener, DTO, and controller**

```java
package com.airtribe.tasktracker.notification;

import java.util.UUID;

public record TaskAssignedEvent(UUID taskId, String taskTitle, UUID teamId, UUID assigneeId) {
}
```

```java
package com.airtribe.tasktracker.notification;

import java.util.UUID;

public record TaskUpdatedEvent(UUID taskId, String taskTitle, UUID teamId, UUID recipientId) {
}
```

```java
package com.airtribe.tasktracker.notification;

import java.util.UUID;

public record CommentAddedEvent(UUID taskId, String taskTitle, UUID teamId, UUID recipientId, String commentAuthorName) {
}
```

```java
package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.user.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationEventListener(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @EventListener
    public void onTaskAssigned(TaskAssignedEvent event) {
        notificationService.create(userService.findById(event.assigneeId()), NotificationType.TASK_ASSIGNED,
                Map.of("taskId", event.taskId().toString(), "taskTitle", event.taskTitle(),
                        "teamId", event.teamId().toString()));
    }

    @EventListener
    public void onTaskUpdated(TaskUpdatedEvent event) {
        notificationService.create(userService.findById(event.recipientId()), NotificationType.TASK_UPDATED,
                Map.of("taskId", event.taskId().toString(), "taskTitle", event.taskTitle(),
                        "teamId", event.teamId().toString()));
    }

    @EventListener
    public void onCommentAdded(CommentAddedEvent event) {
        notificationService.create(userService.findById(event.recipientId()), NotificationType.COMMENT_ADDED,
                Map.of("taskId", event.taskId().toString(), "taskTitle", event.taskTitle(),
                        "teamId", event.teamId().toString(), "commentAuthor", event.commentAuthorName()));
    }
}
```

```java
package com.airtribe.tasktracker.notification.dto;

import com.airtribe.tasktracker.notification.Notification;
import com.airtribe.tasktracker.notification.NotificationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationType type, Map<String, Object> payload,
                                    boolean read, Instant createdAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getPayload(),
                notification.isRead(), notification.getCreatedAt());
    }
}
```

```java
package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.common.web.PageMeta;
import com.airtribe.tasktracker.notification.dto.NotificationResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int limit) {
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = notificationService.list(principal.getUserId(), pageRequest);
        List<NotificationResponse> data = result.getContent().stream().map(NotificationResponse::from).toList();
        return ApiResponse.ok(data, new PageMeta(page, limit, result.getTotalElements()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable UUID id) {
        Notification notification = notificationService.markRead(id, principal.getUserId());
        return ApiResponse.ok(NotificationResponse.from(notification));
    }
}
```

- [ ] **Step 7: Implement the WebSocket handshake auth and config**

```java
package com.airtribe.tasktracker.notification;

import java.security.Principal;

public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
```

```java
package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.security.JwtPrincipal;
import com.airtribe.tasktracker.security.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;

public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    private final JwtService jwtService;

    public JwtHandshakeHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
        String token = extractToken(request.getURI().getQuery());
        if (token == null) {
            return null;
        }
        try {
            JwtPrincipal claims = jwtService.parseAccessToken(token);
            return new StompPrincipal(claims.userId().toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractToken(String query) {
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals("token")) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
```

```java
package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    public WebSocketConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new JwtHandshakeHandler(jwtService))
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
```

- [ ] **Step 8: Wire notification events into `TaskService`**

Add the import and constructor field to `TaskService.java`. Change the constructor signature and field list at the top of the class:

```java
    private final TaskRepository taskRepository;
    private final TeamService teamService;
    private final TeamMembershipService teamMembershipService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public TaskService(TaskRepository taskRepository, TeamService teamService,
                        TeamMembershipService teamMembershipService,
                        org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.teamService = teamService;
        this.teamMembershipService = teamMembershipService;
        this.eventPublisher = eventPublisher;
    }
```

Change the end of `updateTask` from `return taskRepository.save(task);` to:

```java
        Task saved = taskRepository.save(task);
        publishUpdateIfAssigneeDiffers(saved, actingUserId);
        return saved;
```

Change the end of `changeStatus` from `return taskRepository.save(task);` to:

```java
        Task saved = taskRepository.save(task);
        publishUpdateIfAssigneeDiffers(saved, actingUserId);
        return saved;
```

Change the end of `assignTask` from `return taskRepository.save(task);` to:

```java
        Task saved = taskRepository.save(task);
        if (!saved.getAssignee().getId().equals(actingUserId)) {
            eventPublisher.publishEvent(new com.airtribe.tasktracker.notification.TaskAssignedEvent(
                    saved.getId(), saved.getTitle(), saved.getTeam().getId(), saved.getAssignee().getId()));
        }
        return saved;
```

Add this private helper to the bottom of the class, just above the final closing brace:

```java
    private void publishUpdateIfAssigneeDiffers(Task task, UUID actingUserId) {
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(actingUserId)) {
            eventPublisher.publishEvent(new com.airtribe.tasktracker.notification.TaskUpdatedEvent(
                    task.getId(), task.getTitle(), task.getTeam().getId(), task.getAssignee().getId()));
        }
    }
```

- [ ] **Step 9: Wire the comment-added event into `CommentService`**

Change the field list and constructor at the top of `CommentService.java`:

```java
    private final CommentRepository commentRepository;
    private final TaskService taskService;
    private final UserService userService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public CommentService(CommentRepository commentRepository, TaskService taskService, UserService userService,
                           org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.commentRepository = commentRepository;
        this.taskService = taskService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }
```

Change the end of `addComment` from `return commentRepository.save(comment);` to:

```java
        Comment saved = commentRepository.save(comment);
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(authorId)) {
            eventPublisher.publishEvent(new com.airtribe.tasktracker.notification.CommentAddedEvent(
                    task.getId(), task.getTitle(), task.getTeam().getId(), task.getAssignee().getId(), author.getName()));
        }
        return saved;
```

- [ ] **Step 10: Update the three earlier unit test files for the new constructor parameters**

In `src/test/java/com/airtribe/tasktracker/task/TaskServiceTest.java`, add a mock field and update `service()`:

```java
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private TaskService service() {
        return new TaskService(taskRepository, teamService, teamMembershipService, eventPublisher);
    }
```

In `src/test/java/com/airtribe/tasktracker/task/TaskAssignmentServiceTest.java`, the same change:

```java
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private TaskService service() {
        return new TaskService(taskRepository, teamService, teamMembershipService, eventPublisher);
    }
```

In `src/test/java/com/airtribe/tasktracker/comment/CommentServiceTest.java`, add the mock and update `service()`:

```java
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private CommentService service() {
        return new CommentService(commentRepository, taskService, userService, eventPublisher);
    }
```

- [ ] **Step 11: Run the full unit test suite to verify everything still passes**

Run: `mvn -q test -Dtest=TaskServiceTest,TaskAssignmentServiceTest,CommentServiceTest,NotificationServiceTest`
Expected: PASS

- [ ] **Step 12: Write and run the REST integration test for notification persistence and listing**

```java
package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"User","email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    @Test
    void assigningATaskCreatesAPersistedNotificationForTheAssignee() throws Exception {
        String ownerToken = register("notifyowner@example.com");
        String assigneeToken = register("notifyassignee@example.com");

        String teamJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(teamJson).get("data").get("id").asText();

        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"notifyassignee@example.com"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String inviteToken = objectMapper.readTree(inviteJson).get("data").get("token").asText();
        String acceptJson = mockMvc.perform(post("/api/invitations/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + assigneeToken))
                .andReturn().getResponse().getContentAsString();
        String assigneeId = objectMapper.readTree(acceptJson).get("data").get("userId").asText();

        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Notify me","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskJson).get("data").get("id").asText();

        mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + assigneeId + "\"}"))
                .andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + assigneeToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].type").value("TASK_ASSIGNED"))
                        .andExpect(jsonPath("$.data[0].read").value(false)));
    }
}
```

Add the Awaitility test dependency to `pom.xml`, inside `<dependencies>`, alongside the other `test` scoped entries:

```xml
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <version>4.2.2</version>
            <scope>test</scope>
        </dependency>
```

Run: `mvn -q test -Dtest=NotificationControllerIT`
Expected: PASS

- [ ] **Step 13: Write and run the live WebSocket push test**

```java
package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class WebSocketNotificationIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"User","email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    @Test
    void assigneeReceivesLivePushWhenTaskIsAssigned() throws Exception {
        String ownerToken = register("wsowner@example.com");
        String assigneeToken = register("wsassignee@example.com");

        String teamJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(teamJson).get("data").get("id").asText();

        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wsassignee@example.com"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String inviteToken = objectMapper.readTree(inviteJson).get("data").get("token").asText();
        String acceptJson = mockMvc.perform(post("/api/invitations/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + assigneeToken))
                .andReturn().getResponse().getContentAsString();
        String assigneeId = objectMapper.readTree(acceptJson).get("data").get("userId").asText();

        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Live push test","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskJson).get("data").get("id").asText();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        BlockingQueue<Map> received = new LinkedBlockingQueue<>();
        String url = "ws://localhost:" + port + "/ws/websocket?token=" + assigneeToken;
        StompSession session = stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map) payload);
            }
        });

        Thread.sleep(500); // allow the subscription to register before triggering the event

        mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + assigneeId + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        Map<String, Object> notification = received.poll(5, TimeUnit.SECONDS);
        assertThat(notification).isNotNull();
        assertThat(notification.get("type")).isEqualTo("TASK_ASSIGNED");

        session.disconnect();
    }
}
```

Run: `mvn -q test -Dtest=WebSocketNotificationIT`
Expected: PASS

- [ ] **Step 14: Commit**

```bash
git add pom.xml src/main/java/com/airtribe/tasktracker/notification \
        src/main/java/com/airtribe/tasktracker/task/TaskService.java \
        src/main/java/com/airtribe/tasktracker/comment/CommentService.java \
        src/test/java/com/airtribe/tasktracker/task/TaskServiceTest.java \
        src/test/java/com/airtribe/tasktracker/task/TaskAssignmentServiceTest.java \
        src/test/java/com/airtribe/tasktracker/comment/CommentServiceTest.java \
        src/test/java/com/airtribe/tasktracker/notification
git commit -m "feat: add persisted notifications with real-time WebSocket push"
```

### Task 15: AI-Assisted Task Description Generation

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/ai/AiProperties.java`
- Create: `src/main/java/com/airtribe/tasktracker/ai/AiService.java`
- Create: `src/main/java/com/airtribe/tasktracker/ai/NoOpAiService.java`
- Create: `src/main/java/com/airtribe/tasktracker/ai/ClaudeAiService.java`
- Create: `src/main/java/com/airtribe/tasktracker/ai/AiConfig.java`
- Create: `src/main/java/com/airtribe/tasktracker/ai/AiController.java`
- Create: `src/main/java/com/airtribe/tasktracker/ai/dto/GenerateDescriptionRequest.java`
- Create: `src/main/java/com/airtribe/tasktracker/ai/dto/GenerateDescriptionResponse.java`
- Test: `src/test/java/com/airtribe/tasktracker/ai/NoOpAiServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/ai/ClaudeAiServiceTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/ai/AiConfigTest.java`
- Test: `src/test/java/com/airtribe/tasktracker/ai/AiControllerIT.java`

**Interfaces:**
- Consumes: nothing outside this module (standalone integration point).
- Produces: `AiService.generateDescription(String title, String notes) String` — the only method later code (the controller) depends on. `AiConfig` selects `NoOpAiService` when `app.ai.anthropic-api-key` is blank/unset, `ClaudeAiService` otherwise. **No test in this task or anywhere else in the suite calls the real Anthropic API** — `ClaudeAiServiceTest` uses a stubbed `ExchangeFunction`, and integration tests run against `application-test.yml` where the key is blank, so `NoOpAiService` is what's actually exercised end-to-end.

- [ ] **Step 1: Write the failing unit test for `NoOpAiService`**

```java
package com.airtribe.tasktracker.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpAiServiceTest {

    @Test
    void returnsClearUnavailableMessageContainingTheTitle() {
        NoOpAiService service = new NoOpAiService();

        String result = service.generateDescription("Migrate database", "some notes");

        assertThat(result.toLowerCase()).contains("unavailable");
        assertThat(result).contains("Migrate database");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=NoOpAiServiceTest`
Expected: FAIL — `NoOpAiService` doesn't exist.

- [ ] **Step 3: Implement `AiService` and `NoOpAiService`**

```java
package com.airtribe.tasktracker.ai;

public interface AiService {
    String generateDescription(String title, String notes);
}
```

```java
package com.airtribe.tasktracker.ai;

public class NoOpAiService implements AiService {

    @Override
    public String generateDescription(String title, String notes) {
        return "AI generation is unavailable (no ANTHROPIC_API_KEY configured). "
                + "Please write a description manually for: " + title;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=NoOpAiServiceTest`
Expected: PASS

- [ ] **Step 5: Write the failing unit test for `ClaudeAiService`**

```java
package com.airtribe.tasktracker.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeAiServiceTest {

    @Test
    void extractsGeneratedTextFromAnthropicResponse() {
        String fakeJson = """
                {"content":[{"type":"text","text":"A generated description."}]}
                """;
        WebClient stubbedClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(fakeJson)
                        .build()))
                .build();

        AiProperties properties = new AiProperties();
        properties.setAnthropicApiKey("test-key");
        properties.setModel("claude-fable-5");

        ClaudeAiService service = new ClaudeAiService(properties, stubbedClient);

        String result = service.generateDescription("Write onboarding doc", "cover setup and FAQ");

        assertThat(result).isEqualTo("A generated description.");
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn -q test -Dtest=ClaudeAiServiceTest`
Expected: FAIL — `AiProperties`, `ClaudeAiService` don't exist.

- [ ] **Step 7: Implement `AiProperties` and `ClaudeAiService`**

```java
package com.airtribe.tasktracker.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private String anthropicApiKey;
    private String model = "claude-fable-5";
}
```

```java
package com.airtribe.tasktracker.ai;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

public class ClaudeAiService implements AiService {

    private final AiProperties properties;
    private final WebClient webClient;

    public ClaudeAiService(AiProperties properties, WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    @Override
    public String generateDescription(String title, String notes) {
        String prompt = "Write a clear, concise task description (2-4 sentences) for a task titled \""
                + title + "\"." + (notes == null || notes.isBlank() ? "" : " Additional notes: " + notes);

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "max_tokens", 300,
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        Map<?, ?> response = webClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", properties.getAnthropicApiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return extractText(response);
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> response) {
        if (response == null) {
            return "";
        }
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            return "";
        }
        Object text = content.get(0).get("text");
        return text == null ? "" : text.toString();
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn -q test -Dtest=ClaudeAiServiceTest`
Expected: PASS

- [ ] **Step 9: Write the failing unit test for `AiConfig`**

```java
package com.airtribe.tasktracker.ai;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigTest {

    @Test
    void choosesNoOpWhenApiKeyBlank() {
        AiProperties properties = new AiProperties();
        properties.setAnthropicApiKey("");

        AiService service = new AiConfig().aiService(properties, WebClient.builder());

        assertThat(service).isInstanceOf(NoOpAiService.class);
    }

    @Test
    void choosesClaudeWhenApiKeyPresent() {
        AiProperties properties = new AiProperties();
        properties.setAnthropicApiKey("real-key");

        AiService service = new AiConfig().aiService(properties, WebClient.builder());

        assertThat(service).isInstanceOf(ClaudeAiService.class);
    }
}
```

- [ ] **Step 10: Run test to verify it fails**

Run: `mvn -q test -Dtest=AiConfigTest`
Expected: FAIL — `AiConfig` doesn't exist.

- [ ] **Step 11: Implement `AiConfig`**

```java
package com.airtribe.tasktracker.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiConfig {

    @Bean
    public AiService aiService(AiProperties properties, WebClient.Builder webClientBuilder) {
        if (properties.getAnthropicApiKey() == null || properties.getAnthropicApiKey().isBlank()) {
            return new NoOpAiService();
        }
        return new ClaudeAiService(properties, webClientBuilder.build());
    }
}
```

- [ ] **Step 12: Run test to verify it passes**

Run: `mvn -q test -Dtest=AiConfigTest`
Expected: PASS

- [ ] **Step 13: Implement the DTOs and `AiController`**

```java
package com.airtribe.tasktracker.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateDescriptionRequest(@NotBlank(message = "is required") String title, String notes) {
}
```

```java
package com.airtribe.tasktracker.ai.dto;

public record GenerateDescriptionResponse(String description) {
}
```

```java
package com.airtribe.tasktracker.ai;

import com.airtribe.tasktracker.ai.dto.GenerateDescriptionRequest;
import com.airtribe.tasktracker.ai.dto.GenerateDescriptionResponse;
import com.airtribe.tasktracker.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/generate-description")
    public ApiResponse<GenerateDescriptionResponse> generate(@Valid @RequestBody GenerateDescriptionRequest request) {
        String description = aiService.generateDescription(request.title(), request.notes());
        return ApiResponse.ok(new GenerateDescriptionResponse(description));
    }
}
```

- [ ] **Step 14: Write and run the integration test**

```java
package com.airtribe.tasktracker.ai;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"User","email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    @Test
    void generateDescriptionRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/tasks/ai/generate-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write changelog"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generateDescriptionReturnsNoOpFallbackWhenNoApiKeyConfigured() throws Exception {
        String token = register("aiuser@example.com");

        mockMvc.perform(post("/api/tasks/ai/generate-description")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write changelog","notes":"summarize v2 release"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value(org.hamcrest.Matchers.containsString("Write changelog")));
    }

    @Test
    void generateDescriptionRejectsBlankTitle() throws Exception {
        String token = register("aiuser2@example.com");

        mockMvc.perform(post("/api/tasks/ai/generate-description")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
```

Run: `mvn -q test -Dtest=AiControllerIT`
Expected: PASS

- [ ] **Step 15: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/ai src/test/java/com/airtribe/tasktracker/ai
git commit -m "feat: add AI-assisted task description generation"
```

### Task 16: Full User Journey End-to-End Test

Every module already has its own integration tests (Tasks 6–15). This task adds the single cross-module journey called for by the spec's testing strategy (§9) — one continuous flow through every user story in the brief, run against the real Spring context and a Testcontainers Postgres, so a regression that only shows up when features are combined (e.g., a notification firing on the wrong actor once assignment *and* comments are both involved) has somewhere to surface.

**Files:**
- Test: `src/test/java/com/airtribe/tasktracker/e2e/FullUserJourneyE2ETest.java`

**Interfaces:**
- Consumes: every controller built in Tasks 6–15, via HTTP through `mockMvc` — no new production code.

- [ ] **Step 1: Write the end-to-end journey test**

```java
package com.airtribe.tasktracker.e2e;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FullUserJourneyE2ETest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String field(String json, String... path) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        for (String p : path) {
            node = node.get(p);
        }
        return node.asText();
    }

    @Test
    void registerCreateTeamInviteAssignCommentAttachAndNotifyEndToEnd() throws Exception {
        // 1. Two users register.
        String leadJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lead Lee","email":"lead@example.com","password":"supersecret1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String leadToken = field(leadJson, "data", "accessToken");

        String memberJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mo Member","email":"member@example.com","password":"supersecret1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String memberToken = field(memberJson, "data", "accessToken");

        // 2. Lead updates their profile.
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lead Leeman","avatarUrl":"https://img/lead.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Lead Leeman"));

        // 3. Lead creates a team.
        String teamJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Launch Squad","description":"Ships the launch"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String teamId = field(teamJson, "data", "id");

        // 4. Lead invites the member; member accepts.
        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String inviteToken = field(inviteJson, "data", "token");

        String acceptJson = mockMvc.perform(post("/api/invitations/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andReturn().getResponse().getContentAsString();
        String memberId = field(acceptJson, "data", "userId");

        mockMvc.perform(get("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 5. Lead uses AI generation to draft a task description, then creates the task with it.
        String aiJson = mockMvc.perform(post("/api/tasks/ai/generate-description")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Prepare launch checklist","notes":"covers infra, comms, and support"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String aiDescription = field(aiJson, "data", "description");
        assertThat(aiDescription).isNotBlank();

        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Object() {
                            public final String title = "Prepare launch checklist";
                            public final String description = aiDescription;
                            public final String priority = "HIGH";
                        })))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String taskId = field(taskJson, "data", "id");

        // 6. Lead assigns the task to the member.
        mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + memberId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(memberId));

        // 7. Member sees the assignment notification.
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + memberToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data[0].type").value("TASK_ASSIGNED")));

        String notificationsJson = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + memberToken))
                .andReturn().getResponse().getContentAsString();
        String notificationId = objectMapper.readTree(notificationsJson).get("data").get(0).get("id").asText();
        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        // 8. Member sees the task under "my tasks" and moves it to in progress.
        mockMvc.perform(get("/api/tasks/mine").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // 9. Member comments on the task and attaches a file; lead is notified of the comment.
        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Infra checklist is drafted, see attached."}
                                """))
                .andExpect(status().isCreated());

        MockMultipartFile checklist = new MockMultipartFile(
                "file", "checklist.txt", "text/plain", "1. DNS\n2. Monitoring\n3. Rollback plan".getBytes());
        String attachmentJson = mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(checklist)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attachmentId = field(attachmentJson, "data", "id");

        mockMvc.perform(get("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes("1. DNS\n2. Monitoring\n3. Rollback plan".getBytes()));

        // 10. Lead marks the task complete, filters the team's tasks by status, and searches by title.
        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/teams/" + teamId + "/tasks?status=COMPLETED")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/teams/" + teamId + "/tasks?q=checklist")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(taskId));

        // 11. Cleanup: delete the attachment, then both users log out.
        mockMvc.perform(delete("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        String leadRefreshToken = field(leadJson, "data", "refreshToken");
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + leadRefreshToken + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + leadRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=FullUserJourneyE2ETest`
Expected: PASS — this exercises code from every prior task, so a failure here means either this test has a bug or an earlier task's behavior doesn't compose correctly with another; debug by re-running the narrower per-module `*IT` tests first to isolate which module regressed.

- [ ] **Step 3: Run the entire test suite**

Run: `mvn -q test`
Expected: PASS — every unit and integration test across all 16 prior tasks is green.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/airtribe/tasktracker/e2e
git commit -m "test: add full user journey end-to-end test"
```

### Task 17: OpenAPI Docs, Context Smoke Test, and Coverage Gate

**Files:**
- Create: `src/main/java/com/airtribe/tasktracker/config/OpenApiConfig.java`
- Test: `src/test/java/com/airtribe/tasktracker/config/OpenApiControllerIT.java`
- Test: `src/test/java/com/airtribe/tasktracker/ApplicationContextLoadsTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `GET /v3/api-docs` (OpenAPI JSON) and `/swagger-ui.html` (interactive docs), both already `permitAll` from `SecurityConfig` (Task 5). A `bearerAuth` security scheme so Swagger UI's "Authorize" button attaches `Authorization: Bearer <token>` to try-it-out requests.

- [ ] **Step 1: Write the failing test for the OpenAPI document**

```java
package com.airtribe.tasktracker.config;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpenApiControllerIT extends AbstractIntegrationTest {

    @Test
    void openApiDocumentIsPubliclyServedAndDescribesTaskEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Task Tracker API"))
                .andExpect(jsonPath("$.paths./api/auth/register").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OpenApiControllerIT`
Expected: FAIL — the default springdoc title/description don't match, and no `bearerAuth` scheme is registered yet.

- [ ] **Step 3: Implement `OpenApiConfig`**

```java
package com.airtribe.tasktracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI taskTrackerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Task Tracker API")
                        .version("v1")
                        .description("Task tracking and team collaboration backend"))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .name(SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OpenApiControllerIT`
Expected: PASS

- [ ] **Step 5: Add a context-loads smoke test**

```java
package com.airtribe.tasktracker;

import org.junit.jupiter.api.Test;

class ApplicationContextLoadsTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Intentionally empty: this test's only job is to prove the full Spring context
        // (all controllers, services, repositories, and the WebSocket/security config)
        // wires together without a missing-bean or circular-dependency failure.
    }
}
```

Run: `mvn -q test -Dtest=ApplicationContextLoadsTest`
Expected: PASS

- [ ] **Step 6: Run the full build with the coverage gate**

Run: `mvn -q verify`
Expected: `BUILD SUCCESS`, including the `jacoco:check` goal bound to the `verify` phase in `pom.xml` (Task 1), which fails the build if line coverage drops below 80%.

- [ ] **Step 7: If the coverage gate fails, close the gap**

Open `target/site/jacoco/index.html` in a browser and sort by "Missed Lines" descending to find the worst-covered classes. For each one:

- If it's a service/controller with a `*ServiceTest`/`*ControllerIT` already in this plan, add the missing case (e.g., a validation-failure path, a not-found path, or a second branch of an `if`) to that existing test file, following the same Mockito-mock-and-assert or MockMvc-request-and-assert pattern used elsewhere in that file.
- If it's a DTO `record`, its accessors/`from(...)` factory are almost always already exercised indirectly by the controller integration tests in the same module — if one genuinely isn't, add one assertion in that module's `*ControllerIT` that checks the specific field.
- Re-run `mvn -q verify` after each addition until it passes.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/airtribe/tasktracker/config/OpenApiConfig.java \
        src/test/java/com/airtribe/tasktracker/config src/test/java/com/airtribe/tasktracker/ApplicationContextLoadsTest.java
git commit -m "feat: add OpenAPI docs with bearer auth scheme, context smoke test, verify coverage gate"
```

### Task 18: README

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: nothing (documentation only).
- Produces: the repo's landing document — setup, run, test, and API reference instructions for anyone cloning the repo cold.

- [ ] **Step 1: Write `README.md`**

```markdown
# Task Tracker Backend

A REST API (+ real-time WebSocket notifications) for team task tracking and
collaboration: accounts, teams, task assignment, comments, attachments, and
AI-assisted task descriptions. Built for the Airtribe backend assignment.

## Stack

Java 21 · Spring Boot 3 (Web, Security, Data JPA, Validation, WebSocket) ·
PostgreSQL + Flyway · JWT auth · STOMP over WebSocket · springdoc-openapi ·
JUnit 5 / Mockito / Testcontainers · Maven

## Architecture

Package-by-feature modular monolith — one module per domain, each owning its
own controller, service, repository, entity, and DTOs:

```
com.airtribe.tasktracker
├── config          cross-cutting config (security, WebSocket wiring lives in `notification`, OpenAPI)
├── common          API response envelope, domain exceptions, global error handling, JPA auditing base
├── security        JWT issuing/parsing, the authenticated-user principal, the stateless filter chain
├── auth             register / login / refresh / logout
├── user             profile read/update
├── team             teams, membership, roles, invitations — the authorization chokepoint other modules call into
├── task             task CRUD, filtering/search/pagination, status transitions, assignment
├── comment          per-task comments
├── attachment       per-task file upload/download/delete behind a swappable storage interface
├── notification     persisted notifications + real-time STOMP push
└── ai               AI-assisted task description generation (Claude API, with an offline no-op fallback)
```

Every team-scoped endpoint (tasks, comments, attachments, invitations) checks
membership through `team.TeamMembershipService` before acting — that's the
single authorization chokepoint for the whole system. Full rationale in
[`docs/superpowers/specs/2026-08-30-task-tracker-backend-design.md`](docs/superpowers/specs/2026-08-30-task-tracker-backend-design.md).

## Getting Started

**Prerequisites:** JDK 21, Maven, Docker (for local Postgres and for the
Testcontainers-backed tests).

```bash
git clone <this-repo-url>
cd task-tracker-backend
cp .env.example .env        # then edit values as needed
docker compose up -d postgres
mvn spring-boot:run
```

The API is now listening on `http://localhost:8080`. Interactive docs are at
`http://localhost:8080/swagger-ui.html`; the raw OpenAPI document is at
`http://localhost:8080/v3/api-docs`.

### Configuration

All configuration is environment-variable driven (see `.env.example` and
`src/main/resources/application.yml`):

| Variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Signing key for access/refresh tokens — set a long random value outside local dev |
| `STORAGE_ROOT` | Local directory attachments are written to |
| `ANTHROPIC_API_KEY` | Optional — enables real AI-generated task descriptions; omitted/blank falls back to a clear "unavailable" message instead of failing |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of origins allowed to call the API from a browser |

### Running Tests

```bash
mvn test      # unit + integration + e2e tests (spins up Testcontainers Postgres automatically)
mvn verify    # the above, plus the JaCoCo 80% coverage gate
```

Coverage report after `mvn verify`: `target/site/jacoco/index.html`.

## API Overview

Every response is wrapped as `{ "success": bool, "data": ..., "error": {code, message} | null, "meta": {page, limit, total} | null }`.
Full endpoint-by-endpoint reference: Swagger UI at `/swagger-ui.html`, or see
§5 of the design spec.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/{register,login,refresh,logout}` |
| Profile | `GET/PUT /api/users/me` |
| Teams | `POST /api/teams`, `GET /api/teams`, `GET /api/teams/{id}`, `GET /api/teams/{id}/members` |
| Invitations | `POST /api/teams/{id}/invitations`, `POST /api/invitations/{token}/accept` |
| Tasks | `POST/GET /api/teams/{id}/tasks`, `GET/PUT/DELETE /api/tasks/{id}`, `PATCH /api/tasks/{id}/status`, `PATCH /api/tasks/{id}/assign`, `GET /api/tasks/mine` |
| Comments | `POST/GET /api/tasks/{id}/comments` |
| Attachments | `POST /api/tasks/{id}/attachments`, `GET/DELETE /api/tasks/{id}/attachments/{attachmentId}` |
| AI | `POST /api/tasks/ai/generate-description` |
| Notifications | `GET /api/notifications`, `PATCH /api/notifications/{id}/read`, `WS /ws` (STOMP, subscribe to `/user/queue/notifications`, connect with `?token=<accessToken>`) |

## Project Docs

- [Design spec](docs/superpowers/specs/2026-08-30-task-tracker-backend-design.md) — requirements, architecture rationale, data model, security model
- [Implementation plan](docs/superpowers/plans/2026-08-30-task-tracker-backend.md) — the task-by-task build log this project was implemented from
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add project README"
```

### Task 19: Publish the Public GitHub Repository

**Files:** none (repository operation only).

**Interfaces:** none — this is the final delivery step, run after Task 18 is committed and `mvn verify` (Task 17, Step 6) is green.

- [ ] **Step 1: Confirm the working tree is clean and the branch is named `main`**

```bash
git status
git branch -M main
```

Expected: `git status` shows nothing to commit (every prior task ended with a commit); the branch is now named `main` regardless of what `git init` picked by default.

- [ ] **Step 2: Create the public GitHub repository**

```bash
gh repo create task-tracker-backend --public --source=. --description "Task tracking and team collaboration backend (Spring Boot, Airtribe assignment)"
```

Expected: a new **public** repository is created under the authenticated account and added as the `origin` remote. (Per project convention, Airtribe assignment repos are always public — if `gh` reports insufficient permissions to create a public repo, stop and tell the user rather than silently falling back to `--private`.)

- [ ] **Step 3: Push**

```bash
git push -u origin main
```

Expected: the full commit history (Tasks 1–18) is pushed; `git log --oneline origin/main` on GitHub matches local `git log --oneline`.

- [ ] **Step 4: Verify**

```bash
gh repo view --web
```

Expected: the repository opens in the browser, showing the README, green from `mvn verify` having been run locally, and marked **Public** in the repo header.
