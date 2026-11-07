@echo off
title Privacy & Safety Gateway - Spring Boot Server
color 0A

echo ============================================================
echo   Privacy ^& Safety Gateway for AI - Server Startup
echo ============================================================
echo.

REM Change to project directory (same folder as this script)
cd /d "%~dp0"

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
echo [INFO] H2 Database Console:         http://localhost:8080/h2-console
echo [INFO] API Health Check:            http://localhost:8080/api/health
echo.
echo [INFO] Press Ctrl+C to stop the server.
echo ============================================================
echo.

REM Run the Spring Boot application using Maven
mvn spring-boot:run

echo.
echo [INFO] Server stopped.
pause
