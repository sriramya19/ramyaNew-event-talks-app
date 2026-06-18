# Spring AI Release Pulse - Java Launcher Script
# Set script directory as active location
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

Write-Host "=============================================" -ForegroundColor Green
Write-Host "   SPRING AI RELEASE PULSE - JAVA RUNNER    " -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
Write-Host ""

# 1. Check if Java is installed
if (!(Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Java (JDK) was not found in your PATH. Please install JDK 17+." -ForegroundColor Red
    Write-Host "You can download it from: https://adoptium.net/" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit
}

# 2. Locate or download Maven
$mvnCmd = "mvn"
if (!(Get-Command mvn -ErrorAction SilentlyContinue)) {
    # If global mvn is not found, check local Maven installation
    $localMvnDir = Join-Path $scriptPath "java-equivalent\maven\apache-maven-3.9.6\bin"
    $localMvnPath = Join-Path $localMvnDir "mvn.cmd"
    
    if (Test-Path $localMvnPath) {
        Write-Host "[INFO] Using local Maven installation found at $localMvnPath" -ForegroundColor Cyan
        $mvnCmd = $localMvnPath
    } else {
        Write-Host "[INFO] Maven is not installed on this system." -ForegroundColor Yellow
        Write-Host "[INFO] Downloading standalone Apache Maven 3.9.6 locally (~10MB)..." -ForegroundColor Cyan
        
        $mavenUrl = "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"
        $mavenDir = Join-Path $scriptPath "java-equivalent\maven"
        $zipPath = Join-Path $scriptPath "java-equivalent\maven.zip"
        
        # Create directories
        if (!(Test-Path $mavenDir)) {
            New-Item -ItemType Directory -Force -Path $mavenDir | Out-Null
        }
        
        try {
            Write-Host "[INFO] Downloading from $mavenUrl ..." -ForegroundColor Cyan
            # Download file
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $mavenUrl -OutFile $zipPath
            
            Write-Host "[INFO] Extracting archive..." -ForegroundColor Cyan
            Expand-Archive -Path $zipPath -DestinationPath $mavenDir -Force
            
            # Clean up zip
            Remove-Item -Path $zipPath -Force
            
            if (Test-Path $localMvnPath) {
                Write-Host "[SUCCESS] Local Maven downloaded and extracted successfully!" -ForegroundColor Green
                $mvnCmd = $localMvnPath
            } else {
                throw "Maven executable not found after extraction at $localMvnPath"
            }
        } catch {
            Write-Host "[ERROR] Failed to download or extract Maven: $_" -ForegroundColor Red
            Write-Host "Please install Maven manually or add it to your PATH." -ForegroundColor Yellow
            Read-Host "Press Enter to exit"
            exit
        }
    }
}

# 3. Go to java directory
Set-Location java-equivalent

# 4. Compile and Run Spring Boot Application
Write-Host "[INFO] Running Spring Boot application using: $mvnCmd" -ForegroundColor Cyan
& $mvnCmd spring-boot:run
