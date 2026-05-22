@echo off
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
for /f %%I in ('powershell -NoProfile -Command "(Get-Date).ToString(\"yyyyMMddHHmmss\")"') do set "IMAGE_TAG=%%I"

echo [1/12] Switching to repo root...
cd /d "%ROOT_DIR%" || goto :fail

echo [2/12] Removing old Kubernetes resources if present...
kubectl delete -f "%ROOT_DIR%\k8s\ingress" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\ecommerce" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\images" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\product" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\talk-to-observability-agent\configmap.yaml" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\talk-to-observability-agent\deployment.yaml" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\talk-to-observability-agent\service.yaml" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\observability-server" --ignore-not-found
kubectl delete namespace observability-agent --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\observability\grafana" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\observability\promtail" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\observability\loki" --ignore-not-found
kubectl delete -f "%ROOT_DIR%\k8s\observability\prometheus" --ignore-not-found

echo [3/12] Using image tag !IMAGE_TAG!...

echo [4/12] Building observability-server...
cd /d "%ROOT_DIR%\microservices\observability-server" || goto :fail
call mvn clean package
if errorlevel 1 goto :fail
docker build --no-cache -t observability-server:!IMAGE_TAG! .
if errorlevel 1 goto :fail

echo [5/12] Building talk-to-observability-agent (API + chat UI)...
cd /d "%ROOT_DIR%\microservices\talk-to-observability-agent" || goto :fail
docker build --no-cache -t talk-to-observability-agent:!IMAGE_TAG! .
if errorlevel 1 goto :fail

echo [6/12] Building product-service...
cd /d "%ROOT_DIR%\microservices\product" || goto :fail
call mvn clean package
if errorlevel 1 goto :fail
docker build --no-cache -t product-service:!IMAGE_TAG! .
if errorlevel 1 goto :fail

echo [7/12] Building images...
cd /d "%ROOT_DIR%\microservices\images" || goto :fail
call mvn clean package
if errorlevel 1 goto :fail
docker build --no-cache -t images:!IMAGE_TAG! .
if errorlevel 1 goto :fail

echo [8/12] Building ecommerce...
cd /d "%ROOT_DIR%\microservices\ecommerce" || goto :fail
call mvn clean package
if errorlevel 1 goto :fail
docker build --no-cache -t ecommerce:!IMAGE_TAG! .
if errorlevel 1 goto :fail

echo [9/12] Deploying application Kubernetes resources...
cd /d "%ROOT_DIR%" || goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\namespace.yaml"
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\product"
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\images"
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\ecommerce"
if errorlevel 1 goto :fail
kubectl set image deployment/product product=product-service:!IMAGE_TAG! -n ecommerce
if errorlevel 1 goto :fail
kubectl set image deployment/images images=images:!IMAGE_TAG! -n ecommerce
if errorlevel 1 goto :fail
kubectl set image deployment/ecommerce ecommerce=ecommerce:!IMAGE_TAG! -n ecommerce
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\ingress"
if errorlevel 1 goto :fail

echo [10/12] Deploying observability stack...
kubectl apply -f "%ROOT_DIR%\k8s\observability\namespace.yaml"
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\observability\prometheus"
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\observability\loki"
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\observability\promtail"
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\observability\grafana"
if errorlevel 1 goto :fail

echo [11/12] Deploying observability services...
kubectl apply -f "%ROOT_DIR%\k8s\observability-server"
if errorlevel 1 goto :fail
kubectl set image deployment/observability-server observability-server=observability-server:!IMAGE_TAG! -n observability
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\talk-to-observability-agent\configmap.yaml"
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\talk-to-observability-agent\deployment.yaml"
if errorlevel 1 goto :fail
kubectl apply -f "%ROOT_DIR%\k8s\talk-to-observability-agent\service.yaml"
if errorlevel 1 goto :fail
kubectl set image deployment/talk-to-observability-agent talk-to-observability-agent=talk-to-observability-agent:!IMAGE_TAG! -n observability
if errorlevel 1 goto :fail

echo [12/12] Waiting for application deployments...
kubectl rollout status deployment/product -n ecommerce
if errorlevel 1 goto :fail
kubectl rollout status deployment/images -n ecommerce
if errorlevel 1 goto :fail
kubectl rollout status deployment/ecommerce -n ecommerce
if errorlevel 1 goto :fail
kubectl rollout status deployment/prometheus -n observability
if errorlevel 1 goto :fail
kubectl rollout status deployment/loki -n observability
if errorlevel 1 goto :fail
kubectl rollout status deployment/grafana -n observability
if errorlevel 1 goto :fail
kubectl rollout status deployment/observability-server -n observability
if errorlevel 1 goto :fail
kubectl rollout status deployment/talk-to-observability-agent -n observability
if errorlevel 1 goto :fail

echo.
echo Pods:
kubectl get pods -n ecommerce
echo.
echo Observability Pods:
kubectl get pods -n observability
echo.
echo Services:
kubectl get svc -n ecommerce
echo.
echo Observability Services:
kubectl get svc -n observability
echo.
echo Ingress:
kubectl get ingress -n ecommerce
echo.
echo Startup complete.
echo Test with:
echo   kubectl logs -n ecommerce deploy/product
echo   kubectl logs -n ecommerce deploy/images
echo   kubectl logs -n ecommerce deploy/ecommerce
echo   kubectl logs -n observability deploy/observability-server
echo   kubectl logs -n observability deploy/talk-to-observability-agent
echo   curl http://localhost:3000
echo   curl http://localhost:8090/ecommerce-service/ecommerceProducts
echo   http://localhost:9090
echo   http://localhost:8092/health
echo   http://localhost:8092          (observability chatbot UI)
echo   http://localhost:8092/docs     (FastAPI Swagger)
echo   kubectl port-forward -n observability svc/observability-server 8091:8091
echo   http://localhost:8091/swagger-ui.html
echo.
echo Chatbot UI: see chatbot-ui-readme.md
goto :eof

:fail
echo.
echo Startup failed. Check the command output above.
exit /b 1
