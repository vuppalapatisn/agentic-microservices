# ADR 0003: Introduce Postgres for the product and images services

- **Status:** Accepted
- **Date:** 2026-07-13
- **SemVer impact:** MINOR per service (runtime datastore swap; REST contracts and payloads unchanged)

## Context and Problem
The product and images services stored data in embedded H2 (in-memory). The demo needs a real,
networked database that runs as its own containerized service, configured the way a production
microservice would be — external datastore, config via ConfigMap, credentials via Secret. The
`Forbidden Changes` list in CLAUDE.md requires an ADR before adding a new database, and the security
rules forbid putting secrets in ConfigMaps.

## Decision
Add a single **Postgres 16** service (`k8s/postgres/`) in the `ecommerce` namespace and repoint the
product and images services at it:

- **One Postgres instance, two databases** — `productsdb` (from `POSTGRES_DB`) and `imagesdb`
  (created by a one-time initdb script mounted from a ConfigMap). Both owned by role `ecommerce`.
- **Config split by sensitivity** — non-secret settings (user, DB name, PGDATA, JDBC URL, dialect)
  live in ConfigMaps (`postgres-config`, `product-config`, `images-config`); the **password lives in
  the `postgres-secret` Kubernetes Secret** and is injected into Postgres (`POSTGRES_PASSWORD`) and
  the app pods (`SPRING_DATASOURCE_PASSWORD`) via `secretKeyRef`. `secret-example.yaml` is a template
  only.
- **Persistence** — a `PersistentVolumeClaim` (`postgres-data`, 1Gi, default StorageClass) with the
  Deployment using `Recreate` strategy (single writer on a RWO volume). `start.bat`/`stop.bat` never
  delete the PVC or Secret, so data and credentials survive redeploys.
- **Platform-split SQL** — `schema-h2.sql`/`data-h2.sql` back the test suite (H2, `spring.sql.init.platform=h2`
  in `application.properties`); `schema-postgresql.sql`/`data-postgresql.sql` back runtime
  (`SPRING_SQL_INIT_PLATFORM=postgresql` in the ConfigMap). The Postgres scripts are idempotent
  (`CREATE TABLE IF NOT EXISTS`, `INSERT … ON CONFLICT (product_id) DO NOTHING`) so re-seeding onto a
  persistent volume never duplicates rows.
- **Startup ordering** — product/images get a `wait-for-postgres` initContainer; `start.sh`/`start.bat`/`deploy-ibm-cloud.sh`
  deploy Postgres and wait for its rollout before the apps.

## Consequences
- Positive: realistic external datastore; product/images survive restarts with data intact;
  credentials handled per the security rules (Secret, never ConfigMap).
- Negative / obligations:
  - New runtime prerequisite: the `postgres-secret` Secret must exist in `ecommerce` before deploy
    (fail-fast check added to the deploy scripts). Documented alongside the OpenAI secret.
  - Tests keep H2 (test scope) so CI needs no database; runtime keeps only the Postgres driver.
  - The services now depend on Postgres being reachable — mitigated by the initContainer and rollout
    ordering, but a Postgres outage now takes product/images down (previously impossible with H2).
  - PVC relies on a default StorageClass (present on Docker Desktop and IKS/EKS).

## Options Considered
- **Chosen: one Postgres instance with two databases.** Mirrors the existing productsdb/imagesdb
  split, one pod/PVC to operate, keeps the services independently configured.
- **Rejected: a Postgres per service.** Two pods + two PVCs for a demo — more moving parts with no
  benefit at this scale (YAGNI).
- **Chosen: password in a Secret, other config in ConfigMap.** Required by the repo security rules;
  the user's "use configmap" is honored for all non-secret configuration.
- **Rejected: password in the ConfigMap** (as literally requested). Violates `Secrets handling`;
  surfaced to the user and declined.
- **Chosen: platform-split init SQL (h2 vs postgresql).** Lets the same modules run H2 in tests and
  Postgres at runtime without a live DB in CI.
- **Rejected: Hibernate `ddl-auto=update` + data loader.** Less explicit than versioned SQL and
  muddies the read-only demo; kept `ddl-auto=none` with explicit schema scripts.
