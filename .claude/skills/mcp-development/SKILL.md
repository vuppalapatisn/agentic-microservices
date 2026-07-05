---
name: mcp-development
description: >-
  Build and evolve MCP tools on the Spring AI observability-server, and the agent-side
  MCP client that consumes them. Use when adding/changing an @Tool, its input record,
  validation, or the ObservabilityAgentClient method that calls it.
---

# MCP Development

## Description
Governs the Model Context Protocol surface: the Spring AI MCP server (`observability-server`)
that exposes observability data as `@Tool` methods, and the agent-side `ObservabilityAgentClient`
that consumes them. This is the contract seam between the two agentic services — treat it carefully.

**Reasoning:** MCP tool names and parameters are a **public contract** shared by the server and the
agent (and any future MCP client). Consistent, validated, well-described tools keep the LLM-facing
surface reliable and let the two services evolve independently. This is the highest-leverage,
highest-risk seam in the repo.

## Scope
- **In scope:** `ObservabilityTools` `@Tool`/`@ToolParam` methods, their input records + validation,
  `McpConfiguration`, `ObservabilityService`/client wiring, and the agent's `ObservabilityAgentClient`
  fetch methods that mirror them.
- **Out of scope:** LLM reasoning/graph flow (`agent-orchestration`), Spring internals unrelated to
  MCP (`spring-boot-architecture`), infra (`eks-kubernetes`/`devops`).

## Inputs
- The tool/data need; `observability-server/.../mcp/ObservabilityTools.java`,
  `ObservabilityService`, the Prometheus/Loki clients, and `app/mcp/observability_client.py`.

## Outputs
- A new/updated `@Tool` with validated input and a clear description, backed by service+client
  logic, mirrored by an agent client method — plus tests for validation and parsing. No breaking
  change to existing tool names/params unless versioned per governance.

## Process
1. **Define the tool contract:** a stable snake_case `name`, a one-line `description`, and a typed
   input record with `@ToolParam` descriptions and `required` flags (mirror `get_heap_metrics`,
   `RequestIdInput`/`MetricsInput`).
2. **Validate every input** in the tool method: blank service name, inverted/absent time range,
   non-positive `stepSeconds`, blank requestId — reuse the existing private validators.
3. **Implement behind the service:** add the query to `ObservabilityService` + the relevant
   `PrometheusClient`/`LokiClient` method; return a DTO, never a raw upstream body.
4. **Mirror on the agent side:** add a method to `ObservabilityAgentClient` that calls the tool/REST
   endpoint with the same parameters and maps the response into a Pydantic finding model.
5. **Keep names/params stable.** Additive changes only (new tool, new optional param). A rename or
   type change is a breaking contract change — version it and update the agent + docs together.
6. **Test:** unit-test validation (bad inputs → `IllegalArgumentException`) and DTO parsing on the
   Java side; stub HTTP on the agent side. Never call live Prometheus/Loki in tests.

## Best Practices
- Descriptions are for the LLM — make them precise and unambiguous; name tools by intent.
- Keep tools narrow and composable; one tool = one query. Fail closed on bad input.
- Parameterize queries — never build LogQL/PromQL from unvalidated free text.
- Keep the REST mirror and the MCP tool consistent so both interfaces behave identically.

## Anti-Patterns
- Renaming/removing a tool or param without versioning + updating the agent (silent break).
- A single "do-everything" tool with mode flags instead of focused tools (SRP/OCP).
- Returning raw Prometheus/Loki JSON instead of a stable DTO.
- Injecting user/model text directly into a backend query.

## Examples
- *Add `get_gc_pause_metrics`* → new `@Tool` + `MetricsInput` reuse + validation → service +
  Prometheus client query → agent `get_gc_pause_metrics()` → Pydantic `MetricFinding`. Unit tests
  for validation and parsing.
- *Add optional `limit` to `get_logs_by_service`* → additive `@ToolParam(required=false)`; default
  preserves old behavior; agent passes it through; no version bump beyond MINOR.