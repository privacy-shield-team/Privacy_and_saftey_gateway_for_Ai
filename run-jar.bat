@echo off
title ProtectoShield - Run JAR
color 0A

echo ============================================================
echo   ProtectoShield - Running Standalone JAR
echo ============================================================
echo.

cd /d "%~dp0"

REM Check if port 8080 is already running
netstat -ano | findstr :8080 | findstr LISTENING >nul 2>&1
if not errorlevel 1 (
    echo [INFO] ProtectoShield is already active on http://localhost:8080!
    echo [INFO] Opening browser...
    start http://localhost:8080
    timeout /t 3 >nul
    exit /b 0
)

REM Check if JAR exists
if not exist "target\ai-privacy-shield-1.0.0.jar" (
    echo [INFO] JAR not found. Packaging first...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Build failed! Check errors above.
        pause
        exit /b 1
    )
)

echo [INFO] Starting ProtectoShield server...
echo [INFO] Server will be available at: http://localhost:8080
echo [INFO] Opening browser in 3 seconds...
echo.

start "" cmd /c "timeout /t 3 >nul && start http://localhost:8080"
java -jar target\ai-privacy-shield-1.0.0.jar

echo.
echo [INFO] Server stopped.
pause
