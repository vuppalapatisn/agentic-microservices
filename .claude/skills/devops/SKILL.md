---
name: devops
description: >-
  Work on pipelines, deployment, Kubernetes, and cloud infrastructure for the
  agentic-microservices platform. Use for GitHub Actions changes, Dockerfile/image
  work, k8s manifests, rollouts, and local vs IBM Cloud (IKS) / CloudFormation deploys.
---

# DevOps

## Description
Owns the build, packaging, and deployment surface: GitHub Actions workflows, multi-stage
multi-arch Docker images, Kubernetes manifests, and the local (Docker Desktop) and remote
(IBM Cloud IKS) deployment paths.

## Scope
- **In scope:** `.github/workflows/**`, `microservices/*/Dockerfile`, `k8s/**`, `CF/**`,
  the `start`/`stop`/`restart--redeploy-service`/`deploy-ibm-cloud` scripts, image tagging,
  secrets/pull-secrets, rollouts.
- **Out of scope:** application logic (`feature-development`), runtime incident RCA
  (`troubleshooting`), vulnerability analysis depth (`security`).

## Inputs
- The infra/pipeline change requested; target environment (local vs IKS); the affected service(s).
- `CLAUDE.md` deployment section, the two workflows, and the relevant manifests/scripts.

## Outputs
- Working, validated pipeline/manifest/Docker changes with reproducible image tags.
- Updated in-file documentation (workflow header comments) and `CLAUDE.md`/`DEV-Readme.md` as needed.

## Process
1. **Identify the path.** Local (`start.*`, `restart--redeploy-service.*`) builds locally and
   `kubectl set image`; CI: `docker_build.yaml` (build+push, PRs don't push) and
   `ibm_cloud_build.yaml` (build→push→deploy to IKS). Keep local and CI in sync when either changes.
2. **Preserve invariants:** multi-arch (`linux/amd64,linux/arm64`), non-root containers,
   pinned/immutable image tags for shared deploys (`latest` local-only), and the two-namespace
   layout (`ecommerce`, `observability`).
3. **Handle secrets correctly:** `DOCKER_USERNAME`/`DOCKER_PASSWORD`/`IBM_CLOUD_API_KEY` as
   GitHub secrets; `OPENAI_API_KEY` and `dockerhub-registry-secret` as K8s secrets (never in YAML/CI logs).
4. **Manifest changes:** keep service names/ports stable (they are contracts); update the matching
   `set image`/`rollout status` lists in scripts + workflows if you add a deployment.
5. **Validate:** for workflows, use a PR build or `workflow_dispatch`; for manifests,
   `kubectl apply --dry-run=client -f <path>` and a real rollout on Docker Desktop; confirm
   `kubectl rollout status` succeeds for each affected deployment.
6. **Document:** update the workflow header comments (required secrets/prereqs) and `CLAUDE.md`.

## Best Practices
- Change local and CI paths together so they don't drift.
- Cache Maven and Docker layers (as the workflows already do) to keep builds fast.
- Re-run `ibmcloud ks cluster config` in any CI step that needs kubectl context (it is not
  preserved across steps).
- Prefer additive manifest changes; roll out one service at a time when possible.

## Anti-Patterns
- Deploying `latest` to a shared cluster; unpinned actions or base images without reason.
- Putting secrets in workflow env, manifests, or Dockerfiles; echoing secrets to logs.
- Dropping the non-root user, adding `privileged`/`hostNetwork`/`hostPath` without an ADR.
- Renaming a K8s Service/port (a contract) without updating every consumer + governance.
- Breaking one architecture in a multi-arch build; skipping rollout verification.

## Examples
- *"Add a new service to CI + deploy."* → add it to the `docker_build.yaml` matrix and the
  `ibm_cloud_build.yaml` build/set-image/rollout lists; add its `k8s/<svc>` manifests; wire it
  into `start.*`/`restart--redeploy-service.*`; verify a full local rollout.
- *"Speed up the agent image build."* → leverage the existing GHA build cache scope; keep the
  Node→Python multi-stage split; verify UI still lands in `/app/static`.