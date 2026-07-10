# Free Deployment Options for Testing

Where to run the full `agentic-microservices` stack for free, ranked by fit.
For architecture and local commands see [/CLAUDE.md](../CLAUDE.md) and
[/DEV-Readme.md](../DEV-Readme.md).

---

## What the stack actually needs

Nine workloads across two namespaces:

| Namespace | Workloads |
|-----------|-----------|
| `ecommerce` | `ecommerce`, `product`, `images` |
| `observability` | `observability-server`, `observability-debug-agent`, `prometheus`, `loki`, `grafana`, `promtail` (DaemonSet) |

**Footprint (from `k8s/**` manifests):**

| | CPU | Memory |
|---|-----|--------|
| Sum of `requests` | ~1.0 vCPU | ~2.4 GB |
| Sum of `limits` (peak) | ~3.4 vCPU | ~4.75 GB |

- **Comfortable node:** ~4 vCPU / 8 GB RAM.
- **Tight floor:** 2 vCPU / ~6 GB (JVM services are the memory drivers).

**Hard requirements that rule some platforms out:**

1. **Real Kubernetes with node access.** `promtail` is a `DaemonSet` and both
   `prometheus` and `promtail` use RBAC that reads `nodes`/`nodes/proxy`/`pods`
   (see `k8s/observability/*/rbac.yaml`). Serverless app hosts (Render, Railway,
   Fly.io, Koyeb) can't run a DaemonSet or grant node RBAC — you'd have to rip out
   Promtail and change log shipping. Not a drop-in; **not recommended**.
2. **Multi-arch images.** Images build for `amd64`+`arm64`, so **ARM free tiers work**
   (important for Oracle's free tier below).
3. **An OpenAI API key.** `/api/v1/investigate` calls OpenAI. This is a real, separate
   cost on every platform below — *none* of these free options make the LLM free.
   Keep the key in a Kubernetes secret (never in the repo).

---

## Ranked options

### 1. Local cluster — truly free, best for testing ⭐

Already the supported path. Zero cloud cost, no time limit, no data leaves your machine.

| Option | Notes |
|--------|-------|
| **Docker Desktop (K8s enabled)** | What `start.bat`/`start.sh` targets today. Give Docker **≥ 8 GB / 4 CPUs** in Settings → Resources. |
| **kind** | `kind create cluster` — lightest, great in CI. |
| **minikube** | `minikube start --cpus=4 --memory=8192`. |
| **k3d** (k3s in Docker) | Fast, low overhead; closest to a "real" cluster locally. |

```bash
# Docker Desktop path (already wired up):
start.sh            # or start.bat on Windows
kubectl create secret generic observability-debug-agent-secret \
  --from-literal=OPENAI_API_KEY=your-key -n observability
```

**Use this unless you specifically need a shared/remote URL.**

---

### 2. Oracle Cloud "Always Free" — best free *remote* cluster ⭐

Oracle's Always Free tier includes **up to 4 Ampere A1 (ARM) vCPUs + 24 GB RAM**
with **no expiry** — enough to run this entire stack with headroom, and your images
are already arm64-compatible.

- Provision 1–2 A1 VMs (e.g. one 4-vCPU/24 GB instance).
- Install a lightweight distro: **k3s** (`curl -sfL https://get.k3s.io | sh -`) —
  k3s ships a Traefik ingress and works with the existing manifests.
- Apply `k8s/` manifests, create the OpenAI secret, expose via the built-in ingress
  or a `NodePort`.

Trade-offs: A1 capacity in popular regions can be scarce at sign-up (retry / pick a
quieter region); requires a credit card for identity (not charged on Always Free).

---

### 3. Google Kubernetes Engine (GKE) — generous trial + free control plane

- **$300 credit for 90 days** on a new account, plus GKE gives **one free zonal/Autopilot
  cluster management** (you pay only for nodes, covered by the credit).
- Spin a small node pool (e.g. 1× `e2-standard-2` = 2 vCPU/8 GB, or 2× smaller).
- Best when you want a managed, production-shaped cluster for a bounded test window.

Time-boxed (credit expires); tear down when done to avoid surprise charges after.

---

### 4. Azure AKS — free control plane + trial credit

- **AKS control plane is free**; **$200 credit for 30 days** on a new account covers nodes.
- 1× `Standard_B2ms`/`B4ms` node runs the stack.
- Similar profile to GKE: great for a short, managed test; watch the credit clock.

---

### 5. AWS — weak free story for this stack

- EKS charges **$0.10/hr per control plane** (~$73/mo), *not* in the free tier.
- The repo's CloudFormation path (`CF/**`) targets **ECS Fargate**, which is also not free.
- 12-month free tier (`t2/t3.micro`) is too small for the JVM services + observability.

Usable on the **$300-ish equivalent** only if you self-manage k3s on EC2 (like the Oracle
approach) — but Oracle's Always Free is strictly better for a free goal. **Skip for "free".**

---

## Quick chooser

| Your goal | Pick |
|-----------|------|
| Fastest, private, no cost, no expiry | **Local** (Docker Desktop / kind / k3d) |
| Free *and* reachable via a URL, indefinitely | **Oracle Cloud Always Free + k3s** |
| Managed cluster for a few weeks of demos | **GKE** or **AKS** trial credit |
| App-host PaaS (Render/Railway/Fly) | **Not without re-architecting** Promtail/Prometheus |

---

## Cost reminders (so "free" stays free)

- **OpenAI usage is never free** here — budget for it or point `OPENAI_MODEL` at the
  cheapest acceptable model.
- On trial-credit clouds (GKE/AKS/AWS), **delete the cluster** when you finish testing;
  load balancers and disks bill even when idle.
- Reproducible remote deploys should use **immutable image tags**, not `latest`
  (see `.claude/rules/rules.md`).
