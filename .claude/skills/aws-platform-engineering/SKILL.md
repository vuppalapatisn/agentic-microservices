---
name: aws-platform-engineering
description: >-
  Work with the AWS deployment path — the CloudFormation stacks (CF/**) that run the
  platform on ECS Fargate behind an ALB with Cloud Map, CloudWatch, and Secrets Manager.
  Use for any AWS infra change, stack update, or ECS service operation.
---

# AWS Platform Engineering

## Description
Governs the AWS deployment path defined in `CF/**`: three ordered CloudFormation stacks running the
five services + observability stack on **ECS Fargate**, behind an **Application Load Balancer** with
path-based routing, **AWS Cloud Map** service discovery, **CloudWatch Logs**, and **Secrets Manager**.

> **Current state:** the in-repo AWS implementation is **ECS Fargate via CloudFormation**
> (`CF/01-infrastructure.yaml` → `CF/02-microservices.yaml` → `CF/03-observability.yaml`).
> AWS **EKS** is documented as a target in `EKS-Architecture.pdf` and is covered by
> `eks-kubernetes`; it is not yet implemented in CloudFormation. Do not conflate the two.

**Reasoning:** the CloudFormation stacks are a real, ordered, export/import-coupled deployment.
Getting the stack order, service-discovery names, ALB priorities, and IAM boundaries right is what
keeps this path reproducible; drift from the documented contract breaks cross-stack imports.

## Scope
- **In scope:** `CF/*.yaml` (VPC/subnets/NAT/ALB/ECS/Cloud Map/IAM/Secrets Manager/CloudWatch),
  ECS task/service definitions, ALB listener rules, stack deploy/teardown order, `PermissionsBoundaryPolicyName`.
- **Out of scope:** Kubernetes manifests/EKS (`eks-kubernetes`), CI pipelines (`azure-devops-pipelines`/`devops`),
  app logic.

## Inputs
- The AWS change; `CF/README.md`, the three templates, and the ALB priority / Cloud Map name tables.

## Outputs
- Valid CloudFormation changes preserving stack order + exports/imports, ALB routing, Cloud Map DNS
  names, IAM least-privilege (+ permissions boundary), and Secrets Manager usage; deploy verified.

## Process
1. **Respect stack order + exports.** `agentic-infra` exports (VPC, ALB, cluster, Cloud Map, IAM,
   secrets) are imported by `agentic-microservices` and `agentic-observability`. Never break an
   export another stack imports; teardown is reverse order.
2. **Service discovery:** internal names are `<name>.agentic-microservices.local:<port>` — these are
   contracts (mirror the K8s Service names/ports). Keep ports aligned (ecommerce 8090, server 8091,
   agent 8092, prometheus 9090, grafana 3000, loki 3100).
3. **ALB routing:** keep listener-rule priorities/paths consistent with `CF/README.md`; additive new
   rules take a new priority.
4. **IAM & boundaries:** least-privilege task/execution roles; support `PermissionsBoundaryPolicyName`
   (name only, ARN constructed). Never widen beyond need.
5. **Secrets:** Docker Hub creds + app secrets via **Secrets Manager**, never in templates or logs.
6. **Logs:** CloudWatch `/ecs/agentic-microservices/<service>`; keep retention set.
7. **Validate:** `aws cloudformation validate-template`; deploy with `--capabilities CAPABILITY_NAMED_IAM`;
   confirm `CREATE/UPDATE_COMPLETE`; check ECS service steady state and CloudWatch logs.

## Best Practices
- Change templates, not live console state (avoid drift); one stack concern per template.
- Keep Fargate ports/names aligned with the K8s path so the app behaves identically across targets.
- Least-privilege IAM; honor the permissions boundary; pin image tags for reproducible ECS deploys.

## Anti-Patterns
- Breaking a cross-stack export/import; deploying out of order; deleting `agentic-infra` first.
- Secrets in templates or CloudWatch logs; over-broad IAM; ignoring the permissions boundary.
- Diverging ALB priorities/service names from `CF/README.md`; `latest` tags for reproducible deploys.

## Examples
- *Add a service to ECS* → task def + ECS service in `02-microservices.yaml`, Cloud Map registration,
  ALB rule at a new priority, CloudWatch log group, least-privilege role → validate → deploy →
  confirm steady state.
- *Rotate Docker Hub creds* → update the Secrets Manager secret + IAM read grant; `--force-new-deployment`
  the services; never edit the template with the raw credential.