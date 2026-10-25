@echo off
:: ShopEase Microservices Launcher
:: Starts all services locally for development (Java 17/21/25 compatible)
:: Requires MySQL running on port 3306 with root password empty or set via DB_PASS env var

setlocal

set JAVA_OPTS=--enable-native-access=ALL-UNNAMED -Dspring.main.banner-mode=off
set ROOT=%~dp0

echo [1/8] Starting Discovery Server (Eureka) on :8761...
start "Eureka" java %JAVA_OPTS% -jar "%ROOT%discovery-server\target\discovery-server-1.0.0.jar"
echo     Waiting 15s for Eureka to be ready...
timeout /t 15 /nobreak >nul

echo [2/8] Starting API Gateway on :8080...
start "Gateway" java %JAVA_OPTS% -jar "%ROOT%api-gateway\target\api-gateway-1.0.0.jar"
timeout /t 5 /nobreak >nul

echo [3/8] Starting Auth Service on :8081...
start "Auth" java %JAVA_OPTS% -jar "%ROOT%services\auth-service\target\auth-service-1.0.0.jar"

echo [4/8] Starting Product Service on :8082...
start "Product" java %JAVA_OPTS% -jar "%ROOT%services\product-service\target\product-service-1.0.0.jar"

echo [5/8] Starting Order Service on :8083...
start "Order" java %JAVA_OPTS% -jar "%ROOT%services\order-service\target\order-service-1.0.0.jar"

echo [6/8] Starting Payment Service on :8084...
start "Payment" java %JAVA_OPTS% -jar "%ROOT%services\payment-service\target\payment-service-1.0.0.jar"

echo [7/8] Starting Notification Service on :8085...
start "Notification" java %JAVA_OPTS% -jar "%ROOT%services\notification-service\target\notification-service-1.0.0.jar"

echo [8/8] Starting Recommendation Service on :8086...
start "Recommendation" java %JAVA_OPTS% -jar "%ROOT%services\recommendation-service\target\recommendation-service-1.0.0.jar"

echo.
echo All services launched. Check windows for startup logs.
echo Eureka dashboard: http://localhost:8761
echo API Gateway:      http://localhost:8080
echo Frontend:         http://localhost:5173  (run: cd frontend ^&^& npm run dev)
endlocal
