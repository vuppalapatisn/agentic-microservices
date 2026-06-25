# CloudFormation Deployment — ECS Fargate

## Architecture

Three stacks are deployed in order. Each stack exports values consumed by the next.

| Stack | Template | Purpose |
|---|---|---|
| `agentic-infra` | `01-infrastructure.yaml` | VPC, subnets, NAT, ALB, ECS cluster, Cloud Map namespace, IAM roles, Secrets Manager, CloudWatch log groups |
| `agentic-microservices` | `02-microservices.yaml` | ECS task definitions + services for ecommerce, product, images, observability-server, observability-debug-agent |
| `agentic-observability` | `03-observability.yaml` | ECS task definitions + services for Prometheus, Grafana, Loki |

**Traffic flow:** Internet → ALB (port 80) → path-based listener rules → ECS tasks in private subnets.

**Internal service discovery:** AWS Cloud Map private DNS namespace `agentic-microservices.local`. Each service resolves as `<name>.agentic-microservices.local:<port>` inside the VPC.

**Logs:** All containers write to CloudWatch Logs under `/ecs/agentic-microservices/<service>` with a 7-day retention policy.

---

## Prerequisites

- AWS CLI v2 installed and configured (`aws configure`)
- Docker images pushed to Docker Hub under `sudhavuppalapati/*`:
  - `sudhavuppalapati/ecommerce:latest`
  - `sudhavuppalapati/product:latest`
  - `sudhavuppalapati/images:latest`
  - `sudhavuppalapati/observability-server:latest`
  - `sudhavuppalapati/observability-debug-agent:latest`
- IAM permissions to create VPCs, ECS resources, IAM roles, Secrets Manager secrets, and CloudWatch log groups
- **Permissions boundary policy name** if your AWS organization requires one (e.g. `MyOrgBoundaryPolicy`). Pass just the **name** as `PermissionsBoundaryPolicyName` — the ARN is constructed automatically. Omit the parameter to deploy without a boundary.

---

## Deploy Order

### Step 1 — Infrastructure

```bash
aws cloudformation deploy \
  --template-file CF/01-infrastructure.yaml \
  --stack-name agentic-infra \
  --parameter-overrides \
    ProjectName=agentic-microservices \
    DockerHubUsername=sudhavuppalapati \
    DockerHubPassword=<your-dockerhub-token> \
    PermissionsBoundaryPolicyName=<BoundaryPolicyName> \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

> Omit `PermissionsBoundaryArn` if your environment does not require a permissions boundary.

Wait for `CREATE_COMPLETE` before proceeding to Step 2.

### Step 2 — Microservices

```bash
aws cloudformation deploy \
  --template-file CF/02-microservices.yaml \
  --stack-name agentic-microservices \
  --parameter-overrides \
    ProjectName=agentic-microservices \
    PermissionsBoundaryPolicyName=<BoundaryPolicyName> \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

### Step 3 — Observability

```bash
aws cloudformation deploy \
  --template-file CF/03-observability.yaml \
  --stack-name agentic-observability \
  --parameter-overrides \
    ProjectName=agentic-microservices \
    GrafanaAdminPassword=<your-grafana-password> \
    PermissionsBoundaryPolicyName=<BoundaryPolicyName> \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

---

### Get the Public ALB DNS Name

```bash
aws cloudformation describe-stacks \
  --stack-name agentic-infra \
  --query 'Stacks[0].Outputs[?OutputKey==`ALBDNSName`].OutputValue' \
  --output text \
  --region us-east-1
```

---

## Public URLs

Replace `<ALB-DNS>` with the value returned above.

| Service | URL | Notes |
|---|---|---|
| Ecommerce | `http://<ALB-DNS>/ecommerceApp` | Spring Boot REST API |
| Product | `http://<ALB-DNS>/product-service` | Spring Boot REST API |
| Images | `http://<ALB-DNS>/image-service` | Spring Boot REST API |
| Observability Server | `http://<ALB-DNS>/actuator` | Spring Boot actuator endpoints |
| Debug Agent | `http://<ALB-DNS>/observability` | Python/FastAPI agent |
| Grafana | `http://<ALB-DNS>/grafana` | Login: `admin` / `<your-grafana-password>` |
| Prometheus | `http://<ALB-DNS>/prometheus` | Metrics UI |

---

## ALB Listener Rule Priorities

| Priority | Path Pattern | Target |
|---|---|---|
| 10 | `/ecommerceApp*` | ecommerce (port 8090) |
| 20 | `/product-service*` | product (port 8090) |
| 30 | `/image-service*` | images (port 8090) |
| 40 | `/actuator*` | observability-server (port 8091) |
| 50 | `/observability*`, `/health` | observability-debug-agent (port 8092) |
| 60 | `/prometheus*` | Prometheus (port 9090) |
| 70 | `/grafana*` | Grafana (port 3000) |
| 80 | `/loki*` | Loki (port 3100) |
| — | default | Fixed 200 response |

---

## Useful Commands

### Check ECS service status

```bash
aws ecs list-services \
  --cluster agentic-microservices-cluster \
  --region us-east-1

aws ecs describe-services \
  --cluster agentic-microservices-cluster \
  --services agentic-microservices-ecommerce \
  --region us-east-1
```

### Tail CloudWatch logs for a service

```bash
aws logs tail /ecs/agentic-microservices/ecommerce \
  --follow \
  --region us-east-1
```

### Force a new deployment (pull latest image)

```bash
aws ecs update-service \
  --cluster agentic-microservices-cluster \
  --service agentic-microservices-ecommerce \
  --force-new-deployment \
  --region us-east-1
```

---

## Teardown

Delete stacks in reverse order. CloudFormation will not delete a stack while downstream stacks still import its exports.

```bash
aws cloudformation delete-stack \
  --stack-name agentic-observability \
  --region us-east-1

# Wait for deletion to complete
aws cloudformation wait stack-delete-complete \
  --stack-name agentic-observability \
  --region us-east-1

aws cloudformation delete-stack \
  --stack-name agentic-microservices \
  --region us-east-1

aws cloudformation wait stack-delete-complete \
  --stack-name agentic-microservices \
  --region us-east-1

aws cloudformation delete-stack \
  --stack-name agentic-infra \
  --region us-east-1

aws cloudformation wait stack-delete-complete \
  --stack-name agentic-infra \
  --region us-east-1
```

> **Note:** The NAT Gateway and EIP are deleted with the infrastructure stack, which ends egress traffic billing. The Secrets Manager secret may enter a 7-day recovery window by default — to delete it immediately append `--force-delete-without-recovery` in the AWS Console or use the CLI.
