---
name: security
description: >-
  Perform security work on the agentic-microservices platform — SAST-style code
  review, DAST-style endpoint probing, dependency review, and threat modeling. Use
  when reviewing changes for vulnerabilities, auditing deps, or assessing new
  attack surface (endpoints, MCP tools, the LLM boundary).
---

# Security

## Description
Security review and threat modeling tailored to this platform's surface: HTTP endpoints,
MCP tools, the OpenAI/LLM boundary, container images, Kubernetes manifests, and dependencies.
Enforces the Security Rules in `.claude/rules/rules.md`.

## Scope
- **In scope:** static analysis of the diff (SAST-style), dynamic probing of endpoints/tools
  (DAST-style), dependency/version review, secrets handling, container + K8s hardening,
  threat modeling of new surface (including prompt-injection at the LLM boundary).
- **Out of scope:** functional correctness (`code-review`), pipeline mechanics (`devops`).

## Inputs
- The diff or component under review; the exposed surface (routes, `@Tool`s, CORS, ingress);
  dependency manifests (`requirements.txt`, `pom.xml`, `ui/package.json`).

## Outputs
- Findings ranked by severity with location, exploit scenario, and remediation.
- A short threat model for new surface (assets, entry points, trust boundaries, mitigations).
- A dependency-review verdict (pinned, maintained, license-compatible, no known critical CVEs).

## Process
1. **SAST (static):** scan the diff for secrets (keys/tokens/passwords/kubeconfig), missing
   input validation, injection (LogQL/PromQL/HTTP built from unvalidated input), unsafe
   deserialization, disabled TLS/cert verification, overly broad CORS, and secrets/PII in logs.
2. **DAST (dynamic):** probe endpoints and MCP tools with malformed/hostile input — blank
   service, inverted/huge time ranges, non-positive step, oversized coupon codes, unexpected
   content types — and confirm they fail closed (400/validation) without leaking internals.
3. **LLM boundary:** treat model input/output as untrusted. Check that user text can't smuggle
   instructions that change tool selection or exfiltrate data; keep the payload compact and
   scoped; never send secrets/PII to the model; never `eval` model output.
4. **Dependency review:** confirm every dependency is pinned, maintained, license-compatible,
   and free of known critical CVEs; reject speculative or unmaintained additions.
5. **Container/K8s:** confirm non-root user, no `privileged`/`hostNetwork`/`hostPath`, minimal
   base image, CA certs present for TLS, secrets sourced from K8s/GitHub secrets only.
6. **Threat model** new surface: assets, entry points, trust boundaries, and mitigations; record
   material decisions as an ADR.

## Best Practices
- Assume all input (HTTP body, query text, model output, upstream responses) is hostile.
- Fail closed with validation; log the event, not the payload.
- Keep the blast radius small: least privilege for secrets, tightest CORS/ingress, non-root.
- Pin and minimize dependencies; prefer existing frameworks over new attack surface.

## Anti-Patterns
- Committing or logging secrets; `verify=False`/`--insecure`; `allow_origins=["*"]` with credentials.
- Building LogQL/PromQL/HTTP calls from unvalidated user/model input.
- Trusting LLM output as commands or code paths; sending secrets/PII to the model.
- Suppressing a vulnerability finding without a documented, approved justification.
- Adding capabilities/privilege to a container to "make it work".

## Examples
- *New MCP tool that takes a raw query string* → confirm it is parameterized/validated, cannot
  be coerced into an arbitrary Loki/Prometheus query, and rejects blank/oversized input.
- *Dependency bump PR* → verify pin, changelog for breaking/security notes, license, and that
  no transitive critical CVE is introduced; record rationale in the PR.