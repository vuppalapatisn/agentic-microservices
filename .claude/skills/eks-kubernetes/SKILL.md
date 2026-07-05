---
name: eks-kubernetes
description: >-
  Work with the Kubernetes manifests and cluster deployments in this repo (k8s/**) —
  Deployments, Services, ConfigMaps, Ingress, namespaces, secrets, rollouts. Use for
  any manifest change or cluster deploy on Docker Desktop, IBM Cloud IKS, or AWS EKS.
---

# EKS / Kubernetes

## Description
Governs the Kubernetes surface: the `k8s/**` manifests and how they roll out across the supported
clusters. The manifests are cluster-portable — the same YAML runs on **Docker Desktop** (local),
**IBM Cloud IKS** (the active remote path), and **AWS EKS** (documented as a target in
`EKS-Architecture.pdf`; the manifests port to it without redesign).

> **Current state (do not assume otherwise):** the automated remote cluster is IBM Cloud IKS
> (`.github/workflows/ibm_cloud_build.yaml`). AWS EKS is a documented/portable target, not yet
> wired into CI. AWS ECS Fargate is a separate path — see `aws-platform-engineering`.

**Reasoning:** keeping manifests portable and namespace-clean is what lets the platform move
between Docker Desktop, IKS, and EKS without a rewrite. Service names/ports and namespaces are
contracts other components (agent, MCP server, deep-links) depend on.

## Scope
- **In scope:** `k8s/{ecommerce,product,images,ingress}`, `k8s/observability/**`,
  `k8s/observability-server`, `k8s/observability-debug-agent`, `k8s/namespace.yaml`,
  `k8s/dockerhub-secret.yaml`, rollouts, probes, resources, secrets/pull-secrets.
- **Out of scope:** CloudFormation/ECS (`aws-platform-engineering`), pipeline mechanics
  (`azure-devops-pipelines`/`devops`), app logic.

## Inputs
- The manifest/deploy change; the target cluster; `k8s/**`, the `start`/`restart--redeploy-service`
  scripts, and `ibm_cloud_build.yaml`.

## Outputs
- Portable, validated manifests (works on Docker Desktop, IKS, EKS); stable service names/ports;
  rollouts verified; scripts + workflow `set image`/`rollout status` lists updated if a deployment
  was added.

## Process
1. **Respect the two namespaces:** `ecommerce` (apps + ingress) and `observability` (data plane +
   agentic services). New workloads go in the right namespace.
2. **Keep names/ports stable** — Service names and ports are contracts (the agent resolves
   `observability-server.observability.svc.cluster.local:8091`; deep-links use dashboard UIDs).
3. **Portability:** avoid provider-only fields in the base manifests; keep images pinned to
   immutable tags for shared clusters. EKS specifics (IRSA, ALB Ingress/LoadBalancer class,
   gp3 StorageClass) are cluster-overlay concerns — layer them, don't hardcode into the base.
4. **Secrets:** never in YAML. Use `dockerhub-registry-secret` (pull) + `observability-debug-agent-secret`
   (`OPENAI_API_KEY`), created out-of-band per `k8s/dockerhub-secret.yaml`.
5. **Health & resources:** keep readiness/liveness probes and sane requests/limits; the agent
   Dockerfile runs non-root — don't override with a privileged securityContext.
6. **Validate:** `kubectl apply --dry-run=client -f <path>`, then a real rollout locally; confirm
   `kubectl rollout status` succeeds and pods pass probes.
7. **Sync automation:** adding a deployment means updating `start.*`, `restart--redeploy-service.*`,
   and the workflow's `set image` + `rollout status` lists.

## Best Practices
- One concern per manifest folder; additive changes; immutable image tags on shared clusters.
- Keep base manifests provider-neutral; put EKS/IKS-specific settings in overlays.
- Verify rollouts one service at a time; keep the previous image tag for fast rollback.

## Anti-Patterns
- Renaming a Service/port/namespace (a contract) without updating every consumer + governance.
- Secrets in manifests; `latest` on a shared cluster; `privileged`/`hostNetwork`/`hostPath`.
- Hardcoding EKS-only or IKS-only fields into base manifests (breaks portability).
- Skipping `rollout status` verification.

## Examples
- *Add a new service deployment* → `k8s/<svc>/{deployment,service,configmap}.yaml` in the right
  namespace, pinned image, probes, non-root → wire into scripts + workflow → verify rollout.
- *Prepare for EKS* → add an overlay for ALB Ingress class + IRSA + gp3 StorageClass; leave base
  manifests unchanged so Docker Desktop/IKS keep working.