@echo off
title ProtectoShield - Spring Boot Server
color 0A

echo ============================================================
echo   ProtectoShield - AI Privacy ^& Safety Gateway Server
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

REM Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java is not found in PATH!
    echo Please install Java 17 and add it to PATH.
    pause
    exit /b 1
)

REM Check if Maven is available
mvn -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven (mvn) is not found in PATH!
    echo Please install Maven and add it to PATH.
    pause
    exit /b 1
)

echo [INFO] Java and Maven found. Starting server...
echo [INFO] Server will be available at: http://localhost:8080
echo [INFO] Opening browser in 5 seconds...
echo.

start "" cmd /c "timeout /t 5 >nul && start http://localhost:8080"
mvn spring-boot:run

echo.
echo [INFO] Server stopped.
pause
