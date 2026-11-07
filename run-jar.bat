@echo off
title Privacy & Safety Gateway - Run JAR
color 0A

echo ============================================================
echo   Privacy ^& Safety Gateway - Running Pre-built JAR
echo ============================================================
echo.

cd /d "%~dp0"

REM Check if JAR exists
if not exist "target\ai-privacy-shield-1.0.0.jar" (
    echo [INFO] JAR not found. Building first...
    mvn clean package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Build failed! Check errors above.
        pause
        exit /b 1
    )
)

echo [INFO] Starting server from JAR...
echo [INFO] Server will be available at: http://localhost:8080
echo [INFO] H2 Console:                  http://localhost:8080/h2-console
echo.
echo [INFO] Press Ctrl+C to stop the server.
echo ============================================================
echo.

java -jar target\ai-privacy-shield-1.0.0.jar

echo.
echo [INFO] Server stopped.
pause
