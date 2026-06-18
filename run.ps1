# Spring AI Release Pulse - Run Script
# Set script directory as active location
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

Write-Host "=============================================" -ForegroundColor Green
Write-Host "   SPRING AI RELEASE PULSE - INITIALIZER     " -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
Write-Host ""

# 1. Check Python
if (!(Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Python was not found in your PATH. Please install Python 3.8+." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

# 2. Setup Virtual Environment
if (!(Test-Path "venv")) {
    Write-Host "[INFO] Creating virtual environment (venv)..." -ForegroundColor Cyan
    python -m venv venv
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Failed to create venv." -ForegroundColor Red
        Read-Host "Press Enter to exit"
        exit
    }
}

# 3. Activate and Install Dependencies
Write-Host "[INFO] Activating virtual environment & installing requirements..." -ForegroundColor Cyan
& "venv/Scripts/pip.exe" install -r requirements.txt
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed to install dependencies." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

# 4. Open Browser
Write-Host "[INFO] Starting Flask Server at http://127.0.0.1:5000/" -ForegroundColor Cyan
Start-Process "http://127.0.0.1:5000/"

# 5. Run Flask App
Write-Host "[SUCCESS] Running app! Press Ctrl+C in this terminal to stop." -ForegroundColor Green
& "venv/Scripts/python.exe" app.py
