---
name: spring-boot-architecture
description: >-
  Design and extend the Java 21 / Spring Boot services in this repo (ecommerce,
  product, images, observability-server) consistently. Use when adding a controller,
  service, client, config, filter, or DTO to a Spring Boot module, or reviewing that
  a Java change fits the established layering and conventions.
---

# Spring Boot Architecture

## Description
Keeps the four Spring Boot services architecturally consistent. It encodes the layering,
injection style, configuration, validation, and cross-cutting conventions already present so
new Java code reads as if the same team wrote it. It does **not** redesign the services.

**Reasoning:** four independently-deployable Boot apps share the same skeleton
(`com.amol.microservices.<svc>` → controller / service / client / config / entity·dto /
repository). Drift between them raises maintenance cost and onboarding time; a single skill that
names the pattern keeps them convergent over years and across many contributors and agents.

## Scope
- **In scope:** package layout, constructor injection, `@RestController` design, service/client
  separation, Spring config (`@ConfigurationProperties`, filters), validation, exception mapping,
  Micrometer/actuator, JSON logging, per-module `pom.xml` dependency choices.
- **Out of scope:** MCP tool design (`mcp-development`), the Python agent (`agent-orchestration`),
  infra/CI (`eks-kubernetes`/`devops`), broad design decisions (`architecture-review`).

## Inputs
- The Java change requested and the target module (`microservices/ecommerce|product|images|observability-server`).
- `CLAUDE.md` conventions, the module's existing controllers/services/clients, and its `pom.xml`.

## Outputs
- Java code that matches the layering and conventions, with validation, error mapping, structured
  logging, and correlation-id propagation intact; matching JUnit 5 / Spring Boot tests.

## Process
1. **Locate the layer.** Transport → `controller`; business logic → `service`/engine; outbound
   HTTP → `client`; data shape → `entity`/`dto`; persistence → `repository`; wiring → `config`.
2. **Inject by constructor** (as `ObservabilityTools`/`ObservabilityService` do). Do not copy the
   legacy `@Autowired` field injection in `EcommerceController`.
3. **Validate inbound input** at the controller boundary (see `EcommerceController`'s coupon regex
   and `ObservabilityTools`'s blank/range/step checks). Reject invalid input with a clear 4xx.
4. **Configuration** via `application.properties`/`application.yml` + `@ConfigurationProperties`
   (`ObservabilityProperties`); never hardcode URLs/keys. Keep the `spring-boot-starter-parent`
   and Spring AI BOM as the version source of truth.
5. **Cross-cutting:** reuse the existing `CorrelationIdFilter` and `RequestLoggingFilter`; keep
   `logstash-logback-encoder` JSON logging and actuator/Micrometer Prometheus metrics wired.
6. **Map errors** consistently (per-service `GlobalExceptionHandler` where present); return DTOs, not raw exceptions.
7. **Test** with Spring Boot Test + JUnit 5; mock outbound HTTP (mirror `LokiClientTest`/`PrometheusClientTest`).

## Best Practices
- One responsibility per class; thin controllers, logic in services, I/O in clients (SRP/DIP).
- Prefer records for DTOs; immutable where possible.
- Keep each module's `pom.xml` minimal and version-managed by the parent/BOM; pin any extra dep.
- Preserve actuator + Prometheus endpoints — the observability agent depends on them.

## Anti-Patterns
- Field/`@Autowired` injection in new code; business logic in controllers; fat "util god-classes".
- Hardcoded URLs/secrets; bypassing the correlation filter or JSON logger.
- Adding a new starter/library when an existing one suffices (YAGNI); unpinned versions.
- Changing a REST path/port (a contract) without governance + updating consumers.

## Examples
- *Add a `/inventory` endpoint to product* → new controller method → `ProductService` method →
  `ProductRepository` query → `ProductResponse` DTO; validate input; add a Spring Boot Test.
- *New outbound call from ecommerce* → new `*Client` class (mirror `CouponClient`), forward the
  `X-Correlation-Id`, wrap failures into a mapped error response.