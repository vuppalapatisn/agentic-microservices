@echo off
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

if "%~1"=="" goto :usage
if /i "%~1"=="--help" goto :usage
if /i "%~1"=="-h" goto :usage
if /i "%~1"=="help" goto :usage
if /i "%~1"=="?" goto :usage

cd /d "%ROOT_DIR%" || goto :fail
for /f %%I in ('powershell -NoProfile -Command "(Get-Date).ToString(\"yyyyMMddHHmmss\")"') do set "IMAGE_TAG=%%I"

echo Using image tag !IMAGE_TAG! for custom-built services.
echo.

set "REQUESTED_COUNT=0"
set "SUCCESS_COUNT=0"

:arg_loop
if "%~1"=="" goto :after_args
set "RAW_SVC=%~1"
set "SVC=!RAW_SVC: =!"
set /a REQUESTED_COUNT+=1
echo ============================================================
echo Redeploying: !SVC!
echo ============================================================
call :redeploy "!SVC!"
if errorlevel 1 goto :fail
set /a SUCCESS_COUNT+=1
echo.
shift
goto :arg_loop

:after_args
if !REQUESTED_COUNT! equ 0 goto :usage

echo ============================================================
echo Redeploy complete: !SUCCESS_COUNT! service^(s^), tag !IMAGE_TAG!
echo ============================================================
echo.
kubectl get pods -n ecommerce 2>nul
echo.
kubectl get pods -n observability 2>nul
goto :eof

:redeploy
set "NAME=%~1"
if /i "!NAME!"=="graphana" set "NAME=grafana"

if /i "!NAME!"=="observability-server" goto :do_observability_server
if /i "!NAME!"=="observability-debug-agent" goto :do_observability_debug_agent
if /i "!NAME!"=="ecommerce" goto :do_ecommerce
if /i "!NAME!"=="ecommerce-service" goto :do_ecommerce
if /i "!NAME!"=="product" goto :do_product
if /i "!NAME!"=="product-service" goto :do_product
if /i "!NAME!"=="images" goto :do_images
if /i "!NAME!"=="images-service" goto :do_images
if /i "!NAME!"=="grafana" goto :do_grafana
if /i "!NAME!"=="prometheus" goto :do_prometheus
if /i "!NAME!"=="loki" goto :do_loki
if /i "!NAME!"=="promtail" goto :do_promtail
if /i "!NAME!"=="ingress" goto :do_ingress

echo.
echo ERROR: Unknown service "!NAME!".
echo.
goto :usage
exit /b 1

:do_observability_server
echo [build] mvn clean package...
cd /d "%ROOT_DIR%\microservices\observability-server" || exit /b 1
call mvn clean package
if errorlevel 1 exit /b 1
echo [build] docker image observability-server:!IMAGE_TAG!...
docker build --no-cache -t observability-server:!IMAGE_TAG! .
if errorlevel 1 exit /b 1
cd /d "%ROOT_DIR%" || exit /b 1
echo [deploy] kubectl apply + set image...
kubectl apply -f "%ROOT_DIR%\k8s\observability-server"
if errorlevel 1 exit /b 1
kubectl set image deployment/observability-server observability-server=observability-server:!IMAGE_TAG! -n observability
if errorlevel 1 exit /b 1
kubectl rollout status deployment/observability-server -n observability --timeout=180s
exit /b %errorlevel%

:do_observability_debug_agent
echo [build] docker image observability-debug-agent:!IMAGE_TAG! ^(API + chat UI^)...
cd /d "%ROOT_DIR%\microservices\observability-debug-agent" || exit /b 1
docker build --no-cache -t observability-debug-agent:!IMAGE_TAG! .
if errorlevel 1 exit /b 1
cd /d "%ROOT_DIR%" || exit /b 1
echo [deploy] kubectl apply + set image...
kubectl apply -f "%ROOT_DIR%\k8s\observability-debug-agent\configmap.yaml"
if errorlevel 1 exit /b 1
kubectl apply -f "%ROOT_DIR%\k8s\observability-debug-agent\deployment.yaml"
if errorlevel 1 exit /b 1
kubectl apply -f "%ROOT_DIR%\k8s\observability-debug-agent\service.yaml"
if errorlevel 1 exit /b 1
kubectl set image deployment/observability-debug-agent observability-debug-agent=observability-debug-agent:!IMAGE_TAG! -n observability
if errorlevel 1 exit /b 1
kubectl rollout status deployment/observability-debug-agent -n observability --timeout=180s
exit /b %errorlevel%

:do_ecommerce
echo [build] mvn clean package...
cd /d "%ROOT_DIR%\microservices\ecommerce" || exit /b 1
call mvn clean package
if errorlevel 1 exit /b 1
echo [build] docker image ecommerce:!IMAGE_TAG!...
docker build --no-cache -t ecommerce:!IMAGE_TAG! .
if errorlevel 1 exit /b 1
cd /d "%ROOT_DIR%" || exit /b 1
kubectl apply -f "%ROOT_DIR%\k8s\ecommerce"
if errorlevel 1 exit /b 1
kubectl set image deployment/ecommerce ecommerce=ecommerce:!IMAGE_TAG! -n ecommerce
if errorlevel 1 exit /b 1
kubectl rollout status deployment/ecommerce -n ecommerce --timeout=180s
exit /b %errorlevel%

:do_product
echo [build] mvn clean package...
cd /d "%ROOT_DIR%\microservices\product" || exit /b 1
call mvn clean package
if errorlevel 1 exit /b 1
echo [build] docker image product-service:!IMAGE_TAG!...
docker build --no-cache -t product-service:!IMAGE_TAG! .
if errorlevel 1 exit /b 1
cd /d "%ROOT_DIR%" || exit /b 1
kubectl apply -f "%ROOT_DIR%\k8s\product"
if errorlevel 1 exit /b 1
kubectl set image deployment/product product=product-service:!IMAGE_TAG! -n ecommerce
if errorlevel 1 exit /b 1
kubectl rollout status deployment/product -n ecommerce --timeout=180s
exit /b %errorlevel%

:do_images
echo [build] mvn clean package...
cd /d "%ROOT_DIR%\microservices\images" || exit /b 1
call mvn clean package
if errorlevel 1 exit /b 1
echo [build] docker image images:!IMAGE_TAG!...
docker build --no-cache -t images:!IMAGE_TAG! .
if errorlevel 1 exit /b 1
cd /d "%ROOT_DIR%" || exit /b 1
kubectl apply -f "%ROOT_DIR%\k8s\images"
if errorlevel 1 exit /b 1
kubectl set image deployment/images images=images:!IMAGE_TAG! -n ecommerce
if errorlevel 1 exit /b 1
kubectl rollout status deployment/images -n ecommerce --timeout=180s
exit /b %errorlevel%

:do_grafana
echo [deploy] grafana manifests ^(config/dashboard changes; upstream image^)...
kubectl apply -f "%ROOT_DIR%\k8s\observability\grafana"
if errorlevel 1 exit /b 1
kubectl rollout restart deployment/grafana -n observability
if errorlevel 1 exit /b 1
kubectl rollout status deployment/grafana -n observability --timeout=120s
exit /b %errorlevel%

:do_prometheus
echo [deploy] prometheus manifests ^(config changes; upstream image^)...
kubectl apply -f "%ROOT_DIR%\k8s\observability\prometheus"
if errorlevel 1 exit /b 1
kubectl rollout restart deployment/prometheus -n observability
if errorlevel 1 exit /b 1
kubectl rollout status deployment/prometheus -n observability --timeout=120s
exit /b %errorlevel%

:do_loki
echo [deploy] loki manifests ^(config changes; upstream image^)...
kubectl apply -f "%ROOT_DIR%\k8s\observability\loki"
if errorlevel 1 exit /b 1
kubectl rollout restart deployment/loki -n observability
if errorlevel 1 exit /b 1
kubectl rollout status deployment/loki -n observability --timeout=120s
exit /b %errorlevel%

:do_promtail
echo [deploy] promtail manifests ^(config changes; upstream image^)...
kubectl apply -f "%ROOT_DIR%\k8s\observability\promtail"
if errorlevel 1 exit /b 1
kubectl rollout restart daemonset/promtail -n observability
if errorlevel 1 exit /b 1
kubectl rollout status daemonset/promtail -n observability --timeout=120s
exit /b %errorlevel%

:do_ingress
echo [deploy] ingress manifests...
kubectl apply -f "%ROOT_DIR%\k8s\ingress"
exit /b %errorlevel%

:usage
echo.
echo restart--redeploy-service.bat — rebuild and redeploy selected services
echo.
echo Usage:
echo   restart--redeploy-service.bat ^<service^> [service2 ...]
echo   restart--redeploy-service.bat --help
echo.
echo Custom-built services ^(mvn clean package + docker build + kubectl set image^):
echo   observability-server
echo   observability-debug-agent
echo   ecommerce          ^(alias: ecommerce-service^)
echo   product            ^(alias: product-service^)
echo   images             ^(alias: images-service^)
echo.
echo Observability stack ^(kubectl apply + rollout restart; upstream images^):
echo   grafana            ^(alias: graphana^)
echo   prometheus
echo   loki
echo   promtail
echo.
echo Other:
echo   ingress
echo.
echo Examples:
echo   restart--redeploy-service.bat observability-debug-agent
echo   restart--redeploy-service.bat observability-server observability-debug-agent
echo   restart--redeploy-service.bat grafana observability-server
echo   restart--redeploy-service.bat ecommerce product images
echo.
if "%~1"=="" exit /b 0
exit /b 1

:fail
echo.
echo Redeploy failed. Check the command output above.
exit /b 1
