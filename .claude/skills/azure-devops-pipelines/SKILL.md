---
name: azure-devops-pipelines
description: >-
  CI/CD pipeline design for this platform. The ACTIVE pipelines are GitHub Actions
  (.github/workflows/**); this skill governs them and provides a forward-compatible
  Azure DevOps mapping for orgs that standardize on Azure Pipelines. Use for any
  build/test/scan/deploy pipeline change.
---

# CI/CD Pipelines (GitHub Actions today · Azure DevOps forward-map)

## Description
Governs the delivery pipelines. **The implemented, active CI/CD in this repo is GitHub Actions**
(`docker_build.yaml` = build & push all/one service multi-arch; `ibm_cloud_build.yaml` = build →
push → deploy to IBM Cloud IKS). This skill is the source of truth for changing those, **and** it
provides a one-to-one Azure DevOps translation so the platform can move to Azure Pipelines without
redesign if the org standardizes there.

> **Do not assume Azure DevOps exists in this repo — it does not yet.** Never claim a pipeline runs
> on Azure unless a `azure-pipelines.yml` is actually added. Until then, treat GitHub Actions as
> canonical and use the mapping below only when explicitly migrating.

**Reasoning:** the delivery contract (build → test → scan → push immutable tag → deploy → verify
rollout) is platform-independent. Documenting it once and mapping it to both runners keeps the repo
portable across CI vendors and prevents accidental drift between local scripts and CI.

## Scope
- **In scope:** `.github/workflows/**`, build/test/scan/push/deploy stages, image tagging strategy,
  secret wiring, matrix builds, rollout verification; the Azure Pipelines equivalent when migrating.
- **Out of scope:** what gets deployed to (`eks-kubernetes`/`aws-platform-engineering`), app logic.

## Inputs
- The pipeline change; the two workflow files, the `deploy-ibm-cloud.sh` script (local equivalent),
  and the required-secrets header comments.

## Outputs
- Validated pipeline changes preserving the delivery contract; secrets kept in the CI secret store;
  local scripts and CI kept in sync; (on migration) an equivalent `azure-pipelines.yml`.

## Process (active — GitHub Actions)
1. **Build/test** each affected service (Maven build+tests for Java; agent image build which also
   builds the UI); keep multi-arch (`linux/amd64,linux/arm64`) and layer caching.
2. **Tag** images immutably (SHA / run number / timestamp) for shared deploys; PRs build but don't push.
3. **Secrets** via GitHub Actions secrets only (`DOCKER_USERNAME`, `DOCKER_PASSWORD`, `IBM_CLOUD_API_KEY`).
4. **Deploy** (IKS workflow): login → `ibmcloud ks cluster config` (re-run in each step that needs
   kubectl context) → apply namespaces + manifests → `set image` → `rollout status` per deployment.
5. **Validate** via PR build / `workflow_dispatch`; update the workflow header comments documenting
   required secrets/prereqs; keep local `deploy-ibm-cloud.sh` in sync.

## Azure DevOps mapping (forward-compatible, use only when migrating)
| GitHub Actions | Azure Pipelines equivalent |
|---|---|
| `jobs` / `strategy.matrix` | `stages`/`jobs` + `strategy.matrix` |
| Actions secrets | Variable groups / Azure Key Vault-linked variables |
| `docker/build-push-action` + Buildx | `Docker@2` / Buildx task, multi-arch via QEMU |
| `ibmcloud`/`kubectl` steps | `Bash@3`/`Kubernetes@1` tasks (or a service connection) |
| `workflow_dispatch` | manual trigger / pipeline `parameters` |
| GH environments/approvals | Azure environments + approval checks |
Keep the same delivery contract, immutable tags, secret-store-only secrets, and rollout verification.

## Best Practices
- Keep local scripts and CI equivalent so behavior doesn't diverge.
- Immutable tags for shared deploys; multi-arch preserved; deps pinned.
- Fail the pipeline on build/test/scan failure; never print secrets.
- One change per pipeline PR; validate before merge.

## Anti-Patterns
- Claiming/adding Azure DevOps config that isn't wired and tested (misleads operators).
- Deploying `latest` to shared clusters; secrets in pipeline YAML or logs.
- Letting CI and local deploy scripts drift; skipping rollout verification.
- Removing multi-arch or caching without cause.

## Examples
- *Add a Trivy image scan* (active) → new step in `docker_build.yaml` after build, fail on
  HIGH/CRITICAL, before push; mirror in the Azure map as a `Trivy` task in the build stage.
- *Migrate to Azure Pipelines* → author `azure-pipelines.yml` mirroring the table, wire Key Vault
  variable groups, keep IKS deploy + rollout verification identical; only then update docs to say Azure is active.