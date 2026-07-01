# Running on macOS and Deploying to IBM Cloud Kubernetes Service

This is the full path from a fresh macOS clone of this repo to a running
deployment on an IBM Cloud Kubernetes Service (IKS) cluster.

> **Never commit real credentials.** Wherever you see `<DOCKERHUB_USERNAME>`
> or `<DOCKERHUB_TOKEN>` below, substitute your own values only in your local
> terminal — do not paste them into any file that gets committed to git.

## Prerequisites

- Docker Desktop (or another local Docker daemon) installed and running
- `kubectl` installed
- `ibmcloud` CLI installed, logged in (`ibmcloud login`), with the
  `kubernetes-service` plugin installed
  (`ibmcloud plugin install kubernetes-service`)
- `mvn` (Maven) and a JDK installed, for building the Java services
- A Docker Hub account that owns the image repositories referenced in
  `k8s/*/deployment.yaml` (currently `docker.io/sudhavuppalapati/*`), plus a
  Docker Hub access token (Account Settings -> Security -> Access Tokens)
- An already-provisioned IBM Cloud Kubernetes Service cluster

## 1. Clone the repo

```bash
git clone https://github.com/vuppalapatisn/agentic-microservices.git
cd agentic-microservices
```

## 2. Confirm tooling is present

```bash
docker --version
kubectl version --client
ibmcloud --version
mvn -version
```

## 3. Log in to Docker Hub

```bash
echo "<DOCKERHUB_TOKEN>" | docker login -u <DOCKERHUB_USERNAME> --password-stdin
```

Using `--password-stdin` keeps the token out of your shell history, unlike
passing it with a `-p` flag.

## 4. Point kubectl at your IBM Cloud cluster

```bash
ibmcloud login          # skip if already logged in
ibmcloud ks cluster config --cluster <IKS_CLUSTER_ID>
kubectl config current-context     # sanity check - should show the IKS context
```

## 5. Create the namespaces

Secrets in step 6 must go into an existing namespace.

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/observability/namespace.yaml
```

## 6. Create the Docker Hub image-pull secret in both namespaces

This is a one-time step per cluster - skip it on future redeploys.

```bash
kubectl create secret docker-registry dockerhub-registry-secret \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=<DOCKERHUB_USERNAME> \
  --docker-password=<DOCKERHUB_TOKEN> \
  --namespace=ecommerce

kubectl create secret docker-registry dockerhub-registry-secret \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=<DOCKERHUB_USERNAME> \
  --docker-password=<DOCKERHUB_TOKEN> \
  --namespace=observability
```

See [k8s/dockerhub-secret.yaml](k8s/dockerhub-secret.yaml) for the reference
version of this command.

## 7. Make the scripts executable

Git already marks these executable, but if permissions were lost in transit
(e.g. a zip download instead of a clone), restore them:

```bash
chmod +x start.sh stop.sh clean-start-temp.sh restart--redeploy-service.sh deploy-ibm-cloud.sh
```

## 8. Run the deployment

```bash
./deploy-ibm-cloud.sh
```

This builds each service with `mvn clean package` + `docker build`, pushes
each image to `docker.io/<DOCKERHUB_USERNAME>/<service>:<timestamp>`, applies
all the Kubernetes manifests, points every deployment at the freshly pushed
tag, and waits for rollout.

Override the cluster ID or Docker Hub namespace if needed:

```bash
IKS_CLUSTER_ID=<other-cluster-id> DOCKERHUB_NAMESPACE=<other-namespace> ./deploy-ibm-cloud.sh
```

## 9. Verify

```bash
kubectl get pods -n ecommerce
kubectl get pods -n observability
kubectl get svc -n ecommerce
kubectl get ingress -n ecommerce
```

## 10. Reach the app

Unlike a local Docker Desktop cluster, `localhost` URLs will not work here -
IBM Cloud exposes services via the cluster's public IP/ingress address:

```bash
ibmcloud ks cluster get --cluster <IKS_CLUSTER_ID>    # shows Ingress Subdomain / public IP
kubectl get ingress -n ecommerce -o wide              # shows the ADDRESS to hit
```

Use that address (or `kubectl port-forward` for individual services such as
`observability-server`) instead of `localhost`.

## Later redeploys

Once steps 1-7 are done once, just re-run:

```bash
./deploy-ibm-cloud.sh
```

It rebuilds, re-pushes, and rolls out fresh images every time.

## Optional: automate the build/push/prep step with GitHub Actions

[.github/workflows/ibm_cloud_build.yaml](.github/workflows/ibm_cloud_build.yaml)
automates steps 3-6 above (Maven build, `docker build`, `docker push` for all
five services) plus the cluster-prep steps (`ibmcloud ks cluster config`,
applying the two namespaces, warning if the pull secret is missing). It runs
on every push to `master`/`main` and can also be triggered manually from the
GitHub Actions tab.

It deliberately stops short of deploying the service manifests — that part
still runs locally (steps 8-10 above), using the image tag the workflow
printed in its job summary.

Required repository secrets (Settings -> Secrets and variables -> Actions in
GitHub — never commit these values):

- `DOCKER_USERNAME` / `DOCKER_PASSWORD` — same Docker Hub credentials used by
  the existing `docker_build.yaml` workflow
- `IBM_CLOUD_API_KEY` — an IBM Cloud IAM API key with access to the target
  cluster (create one with `ibmcloud iam api-key-create`)

After a workflow run finishes, open its Summary tab for the exact
`kubectl apply` / `kubectl set image` / `kubectl rollout status` commands to
run locally, with the pushed image tag already filled in.

## Local development (no IBM Cloud)

For local iteration against Docker Desktop's built-in Kubernetes cluster
instead of IBM Cloud, use the scripts documented in the main
[README.md](README.md):

- `./start.sh` - build and deploy everything locally
- `./stop.sh` - tear down local Kubernetes resources
- `./restart--redeploy-service.sh <service>` - rebuild and redeploy one service
