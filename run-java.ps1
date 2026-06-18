# Spring AI Release Pulse - Java Launcher Script
# Set script directory as active location
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

Write-Host "=============================================" -ForegroundColor Green
Write-Host "   SPRING AI RELEASE PULSE - JAVA RUNNER    " -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
Write-Host ""

# Check if Maven (mvn) or Java is installed
if (!(Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Java was not found in your PATH. Please install JDK 17+." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

# Go to java directory
Set-Location java-equivalent

# Compile and Run using maven Wrapper if exists, else try mvn directly
if (Test-Path "mvnw.cmd") {
    Write-Host "[INFO] Starting Spring Boot app using Maven Wrapper..." -ForegroundColor Cyan
    ./mvnw.cmd spring-boot:run
} else {
    Write-Host "[INFO] Starting Spring Boot app using local maven installation..." -ForegroundColor Cyan
    mvn spring-boot:run
}
