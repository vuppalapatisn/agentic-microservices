# Agentic Microservices - Agent Prompt Reference

**Repository:** amollimaye/agentic-microservices  
**Primary Language:** Java  
**License:** MIT  
**Description:** Microservices with Agentic workflows - Phase 1 modernization of an ecommerce microservices project to a Kubernetes-native local runtime.

---

## Project Overview

This project implements a modernized microservices architecture for an ecommerce platform. It transitions from traditional deployment patterns to a Kubernetes-native approach with integrated observability.

### Key Characteristics
- **Architecture Pattern:** Microservices with Kubernetes orchestration
- **Runtime Environment:** Docker Desktop with Kubernetes enabled
- **Cloud Native:** Full Kubernetes manifest-based deployment
- **Observability Stack:** Prometheus, Loki, Promtail, and Grafana integration
- **Java Version:** Java 17 (runtime), built with Java 1.8 compatibility (pom.xml)
- **Build Tool:** Maven 3.8+

---

## Core Services

### 1. **Product Service**
- **Artifact ID:** product-service
- **Group ID:** com.amol.microservices
- **Port:** 8090
- **Kubernetes Service:** product-service
- **Description:** Provides product details and catalog information
- **Database:** H2 (in-memory, runtime scope)
- **Key Dependencies:**
  - Spring Boot 2.1.8
  - Spring Data JPA (relational persistence)
  - Spring Actuator (health checks, metrics)
  - Micrometer Prometheus Registry
  - Logstash Logback Encoder (structured logging)

### 2. **Images Service**
- **Artifact ID:** images
- **Group ID:** com.amol.microservices
- **Port:** 8090
- **Kubernetes Service:** images-service
- **Description:** Provides images for products
- **Database:** H2 (in-memory, runtime scope)
- **Key Dependencies:**
  - Spring Boot 2.1.8
  - Spring Data JPA
  - Spring Actuator
  - Micrometer Prometheus Registry
  - Logstash Logback Encoder

### 3. **Ecommerce Service**
- **Artifact ID:** ecommerce
- **Group ID:** com.amol.microservices
- **Port:** 8090
- **Kubernetes Service:** ecommerce-service (exposed as LoadBalancer)
- **Description:** Aggregates product and image data for ecommerce UI consumption
- **Database:** None (aggregator service)
- **Key Dependencies:**
  - Spring Boot 2.1.8
  - Spring Web (REST API)
  - Spring Actuator
  - Micrometer Prometheus Registry
  - Logstash Logback Encoder

---

## Kubernetes Architecture

### Namespaces
- **ecommerce:** Application services (product, images, ecommerce)
- **observability:** Monitoring stack (prometheus, loki, promtail, grafana)

### Service Discovery
Internal service-to-service communication uses Kubernetes DNS:
- `http://ecommerce-service:8090`
- `http://product-service:8090`
- `http://images-service:8090`

### External Access
- **Main Endpoint:** http://localhost:8090/ecommerce-service/ecommerceProducts
- **Prometheus:** http://localhost:9090
- **Grafana:** http://localhost:3000

### Ingress
Ingress manifests are present but LoadBalancer service is the stable local endpoint.

---

## Observability Stack

### Prometheus
- Metrics collection from application actuator endpoints
- Configuration in `k8s/observability/prometheus/`

### Loki
- Log aggregation service
- Configuration in `k8s/observability/loki/`

### Promtail
- Log shipper for collecting container logs
- Configuration in `k8s/observability/promtail/`

### Grafana
- Visualization and dashboarding
- Configuration in `k8s/observability/grafana/`

---

## Directory Structure

```
agentic-microservices/
├── microservices/
│   ├── product/              # Product service microservice
│   │   ├── pom.xml          # Maven POM with Spring Boot config
│   │   ├── Dockerfile       # Multi-stage Docker build
│   │   └── src/             # Source code (Java, Spring Boot)
│   ├── images/              # Images service microservice
│   │   ├── pom.xml          # Maven POM with Spring Boot config
│   │   ├── Dockerfile       # Multi-stage Docker build
│   │   └── src/             # Source code (Java, Spring Boot)
│   └── ecommerce/           # Ecommerce aggregator service
│       ├── pom.xml          # Maven POM with Spring Boot config
│       ├── Dockerfile       # Multi-stage Docker build
│       └── src/             # Source code (Java, Spring Boot)
├── k8s/                      # Kubernetes manifests
│   ├── namespace.yaml       # ecommerce namespace definition
│   ├── product/             # Product service K8s resources
│   ├── images/              # Images service K8s resources
│   ├── ecommerce/           # Ecommerce service K8s resources
│   ├── ingress/             # Ingress configuration
│   └── observability/       # Observability stack
│       ├── namespace.yaml   # observability namespace
│       ├── prometheus/      # Prometheus configuration
│       ├── loki/            # Loki configuration
│       ├── promtail/        # Promtail configuration
│       └── grafana/         # Grafana configuration
├── start.bat                # Main startup orchestration script
├── clean-start-temp.bat     # Legacy startup script
├── stop.bat                 # Cleanup and shutdown script
├── README.md                # Project documentation
├── LICENSE                  # MIT License
└── .gitignore              # Git ignore patterns (Java/Maven)
```

---

## Build and Deployment Workflow

### Prerequisites
- Java 17
- Maven 3.8+
- Docker Desktop (with Kubernetes enabled)
- kubectl (Kubernetes CLI)

### Build Process (Automated by start.bat)

1. **Clean and Package Services**
   ```
   cd microservices/product && mvn clean package
   cd microservices/images && mvn clean package
   cd microservices/ecommerce && mvn clean package
   ```

2. **Build Docker Images**
   - `product-service:v2` (from microservices/product/Dockerfile)
   - `images:v2` (from microservices/images/Dockerfile)
   - `ecommerce:v2` (from microservices/ecommerce/Dockerfile)

3. **Apply Kubernetes Manifests**
   - Namespace creation (ecommerce)
   - Service deployments in order: product → images → ecommerce
   - Ingress configuration
   - Observability namespace and stack

4. **Verify Deployment**
   - Rollout status checks for all deployments
   - Pod readiness validation

### Startup Script Workflow (start.bat)
Steps 1-10:
1. Switch to repo root
2. Remove old Kubernetes resources
3. Remove stale Docker images
4. Build product-service (Maven → Docker)
5. Build images service (Maven → Docker)
6. Build ecommerce service (Maven → Docker)
7. Deploy application K8s resources
8. Deploy observability stack
9. Wait for application deployments to be ready
10. Wait for observability deployments to be ready
11. Display pod, service, and ingress status

### Shutdown (stop.bat)
Removes all observability and application resources from Kubernetes cluster.

---

## Container Configuration

### Docker Base Image
- **Image:** `eclipse-temurin:17-jdk-alpine`
- **Maintainer:** amollimaye
- **Port Exposed:** 8090

### Container Security
- Non-root user execution: spring (custom created in container)
- Volume mount: `/tmp`
- Entropy source: `/dev/./urandom` (secure random)

### JAR File Handling
- Built from Maven target directory
- Examples:
  - `product-service-0.0.1-SNAPSHOT.jar` → `product-service.jar`
  - `images-0.0.1-SNAPSHOT.jar` → `images.jar`
  - `ecommerce-0.0.1-SNAPSHOT.jar` → `ecommerce.jar`

---

## Dependencies and Technologies

### Spring Boot Framework
- **Version:** 2.1.8.RELEASE
- **Components Used Across Services:**
  - `spring-boot-starter-web` - REST API endpoints
  - `spring-boot-starter-actuator` - Health checks, metrics endpoints
  - `spring-boot-starter-data-jpa` - ORM/database persistence (product & images)
  - `spring-boot-starter-test` - Unit and integration testing

### Observability
- **Prometheus Metrics:** `micrometer-registry-prometheus`
- **Structured Logging:** `logstash-logback-encoder` v6.6
- **Log Format:** JSON with context for log aggregation

### Database
- **Type:** H2 Database (in-memory, development/testing)
- **Scope:** Runtime (included in container, data not persisted)
- **Usage:** Product and Images services for catalog data storage

### Java Compatibility
- **Compile Target:** Java 1.8 (specified in pom.xml)
- **Runtime:** Java 17 (in Docker containers)
- **Reason:** Legacy compatibility layer while using modern runtime

---

## Common Operations for Coding Agents

### Building Services
```powershell
cd microservices/<service-name>
mvn clean package
```

### Building Docker Images
```powershell
docker build --no-cache -t <service-name>:v2 .
```

### Verifying Kubernetes Deployment
```powershell
kubectl get pods -n ecommerce
kubectl get svc -n ecommerce
kubectl logs -n ecommerce deploy/<service-name>
```

### Testing Endpoints
```powershell
# Main API endpoint
curl http://localhost:8090/ecommerce-service/ecommerceProducts

# Prometheus metrics
http://localhost:9090

# Grafana dashboards
http://localhost:3000
```

### Checking Deployment Status
```powershell
kubectl rollout status deployment/<service-name> -n ecommerce
```

---

## Legacy Notes

The project has removed the following from earlier versions:
- nginx reverse proxy
- Consul service discovery
- consul-template
- HOST_IP environment variable routing
- docker-compose runtime (transitioned to Kubernetes)

---

## Code Patterns and Conventions

### Package Structure
- Group ID: `com.amol.microservices`
- Services follow individual directories under `microservices/`
- Each service is independently buildable and deployable

### Kubernetes Configuration
- Declarative YAML manifests in `k8s/` directory
- Namespace isolation (ecommerce, observability)
- Organized by service and by concern (ingress, observability)

### Container Practices
- Alpine Linux base for minimal image size
- Non-root user execution for security
- Explicit version tagging (`v2`)
- No-cache builds for fresh dependencies

### Observability Practices
- Prometheus endpoints via Spring Actuator
- Structured JSON logging via Logstash encoder
- Loki log aggregation
- Promtail log shipping
- Grafana visualization

---

## Coding Agent Guidelines

When working with this codebase, agents should:

1. **Service Independence:** Each microservice can be built and deployed independently
2. **Kubernetes Awareness:** All deployments assume Kubernetes environment with kubectl access
3. **Port Consistency:** All services use port 8090; no port conflicts
4. **Namespace Isolation:** ecommerce and observability namespaces must not be mixed
5. **Maven Configuration:** All Java services use Spring Boot 2.1.8 parent POM
6. **Docker Builds:** Always use Dockerfile in service root directory
7. **Manifest Order:** Product → Images → Ecommerce (dependency order)
8. **Observability First:** Observability stack deploys with application
9. **Error Handling:** All scripts include rollout status checks before proceeding
10. **Cleanup Policy:** stop.bat provides clean removal without leaving orphaned resources

---

**Last Updated:** 2026-05-12  
**Repository Status:** Actively maintained  
**Version:** v0.0.1-SNAPSHOT
