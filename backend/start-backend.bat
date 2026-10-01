@echo off
cd /d "%~dp0"
start "" /B java -jar target\ecommerce-system-0.0.1-SNAPSHOT.jar > backend-run.log 2> backend-run-err.log