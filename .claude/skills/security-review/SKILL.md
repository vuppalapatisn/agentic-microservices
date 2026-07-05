---
name: security-review
description: >-
  The security GATE for a specific change/PR before merge — a checklist-driven pass
  that decides pass/block. Use on a diff or release candidate. For general security
  capability (methods, threat modeling, dependency audits) use the `security` skill.
---

# Security Review (change gate)

## Description
A focused, decision-oriented security pass on a **specific change** (a diff, branch, or release
candidate) that returns a clear **pass / block** verdict against the repo's Security Rules. It is
the merge gate; the broader `security` skill is the capability toolkit (SAST/DAST/threat-modeling)
it draws on.

**Reasoning:** capability and gating are different jobs. Engineers/agents need a fast, unambiguous
"is this change safe to merge?" answer with a fixed checklist, separate from open-ended security
research. Separating them keeps reviews consistent across many contributors and CI automation.

## Scope
- **In scope:** reviewing one change for secret exposure, input validation, contract/authorization
  impact, dependency risk, container/K8s posture, and secret/PII logging — then blocking or passing.
- **Out of scope:** open-ended threat modeling, DAST campaigns, dependency audits at large (use
  `security`); functional correctness (`code-review`).

## Inputs
- The diff/PR and its description; `.claude/rules/rules.md` (Security Rules), the touched surface
  (routes, `@Tool`s, CORS, ingress), and changed dependency manifests.

## Outputs
- A **verdict (PASS / BLOCK)** with a completed checklist; for each BLOCK, the file:line, the risk,
  and the required fix. No advisory-only output — this gate decides.

## Process — the gate checklist
1. **Secrets:** diff contains no keys/tokens/passwords/kubeconfig; no secret moved into code/YAML/logs. ▢
2. **Input validation:** every new inbound path (controller, `@Tool`, route) validates and fails
   closed (blank/inverted-range/oversized/wrong-content-type → 4xx). ▢
3. **Contracts/authz:** no new externally-exposed surface without an auth posture stated + ADR;
   CORS allow-list not widened; no `allow_origins=["*"]` with credentials. ▢
4. **LLM boundary:** user/model text can't alter tool selection or be injected into LogQL/PromQL;
   no secrets/PII sent to the model; model output not executed. ▢
5. **Dependencies:** additions/bumps are pinned, maintained, license-OK, no known critical CVE. ▢
6. **Container/K8s:** non-root preserved; no `privileged`/`hostNetwork`/`hostPath`/added caps;
   TLS/cert verification intact. ▢
7. **Logging:** no secrets/PII/request bodies logged; structured events only. ▢
→ **All boxes checked = PASS. Any unchecked = BLOCK** with the required fix.

## Best Practices
- Decide, don't muse — every finding is either "must fix to merge" or out of scope for the gate.
- Verify by reading the changed code path, not the PR description.
- Keep the checklist stable so results are comparable across reviewers and CI runs.

## Anti-Patterns
- Turning the gate into open research (belongs in `security`); passing with unchecked boxes.
- "Looks fine" with no checklist; blocking on style/perf issues unrelated to security.
- Accepting a suppressed vulnerability without a documented, approved justification.

## Examples
- *PR adds a raw-string MCP tool* → BLOCK on box 2/4 until the input is validated and the query is
  parameterized; PASS once fixed and a validation test is added.
- *PR bumps a transitive dep with a known CVE* → BLOCK on box 5; PASS after pinning to a patched version.