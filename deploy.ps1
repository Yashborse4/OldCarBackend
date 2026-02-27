#!/usr/bin/env pwsh
# ============================================================================
# Sell The Old Car - Production Deployment Script (Windows/PowerShell)
# Usage: .\deploy.ps1 [command]
# Commands: start, stop, restart, logs, status, build, clean
# ============================================================================

param(
    [Parameter(Position=0)]
    [string]$Command = "help",
    
    [Parameter(Position=1)]
    [string]$Service
)

# Script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectName = "car-selling"

# Helper functions
function Write-Info { param([string]$Message) Write-Host "[INFO] $Message" -ForegroundColor Blue }
function Write-Success { param([string]$Message) Write-Host "[SUCCESS] $Message" -ForegroundColor Green }
function Write-Warn { param([string]$Message) Write-Host "[WARN] $Message" -ForegroundColor Yellow }
function Write-Error { param([string]$Message) Write-Host "[ERROR] $Message" -ForegroundColor Red }

# Check prerequisites
function Test-Prerequisites {
    Write-Info "Checking prerequisites..."
    
    if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Error "Docker is not installed"
        exit 1
    }
    
    if (!(Get-Command docker-compose -ErrorAction SilentlyContinue)) {
        Write-Error "Docker Compose is not installed"
        exit 1
    }
    
    # Check if .env file exists
    if (!(Test-Path "$ScriptDir\.env")) {
        Write-Warn ".env file not found!"
        Write-Info "Creating .env from .env.example..."
        if (Test-Path "$ScriptDir\.env.example") {
            Copy-Item "$ScriptDir\.env.example" "$ScriptDir\.env"
            Write-Warn "Please edit .env file with your actual values before starting!"
            exit 1
        } else {
            Write-Error ".env.example not found!"
            exit 1
        }
    }
    
    Write-Success "Prerequisites check passed"
}

# Build images
function Build-Images {
    Write-Info "Building Docker images..."
    Set-Location $ScriptDir
    docker-compose build --no-cache
    Write-Success "Build completed"
}

# Start services
function Start-Services {
    Write-Info "Starting services..."
    Set-Location $ScriptDir
    docker-compose up -d
    Write-Success "Services started"
    
    Write-Info "Waiting for health checks..."
    Start-Sleep -Seconds 10
    
    $status = docker-compose ps
    if ($status -match "healthy") {
        Write-Success "All services are healthy!"
    } else {
        Write-Warn "Some services may still be starting. Check status with: .\deploy.ps1 status"
    }
    
    Show-Urls
}

# Stop services
function Stop-Services {
    Write-Info "Stopping services..."
    Set-Location $ScriptDir
    docker-compose down
    Write-Success "Services stopped"
}

# Restart services
function Restart-Services {
    Write-Info "Restarting services..."
    Stop-Services
    Start-Services
}

# View logs
function Show-Logs {
    param([string]$ServiceName)
    Set-Location $ScriptDir
    if ($ServiceName) {
        docker-compose logs -f $ServiceName
    } else {
        docker-compose logs -f
    }
}

# Check status
function Get-Status {
    Set-Location $ScriptDir
    docker-compose ps
}

# Clean everything
function Clear-All {
    Write-Warn "This will remove all containers, volumes, and images!"
    $confirm = Read-Host "Are you sure? (y/N)"
    if ($confirm -eq 'y' -or $confirm -eq 'Y') {
        Set-Location $ScriptDir
        docker-compose down -v --rmi all
        Write-Success "Cleanup completed"
    } else {
        Write-Info "Cleanup cancelled"
    }
}

# Show application URLs
function Show-Urls {
    Write-Host ""
    Write-Info "Application URLs:"
    Write-Host "  - API:        http://localhost:8080"
    Write-Host "  - Actuator:   http://localhost:8080/actuator/health"
    Write-Host "  - OpenSearch: http://localhost:9200"
    Write-Host "  - PostgreSQL: localhost:5432"
    Write-Host "  - Redis:      localhost:6379"
    Write-Host ""
}

# Backup data
function Backup-Data {
    Write-Info "Creating backup..."
    $BackupDir = "$ScriptDir\backups\$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    
    # Backup database
    docker exec car-selling-postgres pg_dump -U car_user carselling > "$BackupDir\database.sql"
    
    # Backup uploads
    docker cp car-selling-app:/app/uploads "$BackupDir\uploads"
    
    Write-Success "Backup created at: $BackupDir"
}

# Show help
function Show-Help {
    Write-Host "Sell The Old Car - Deployment Script (Windows)"
    Write-Host ""
    Write-Host "Usage: .\deploy.ps1 [command] [options]"
    Write-Host ""
    Write-Host "Commands:"
    Write-Host "  start     - Start all services"
    Write-Host "  stop      - Stop all services"
    Write-Host "  restart   - Restart all services"
    Write-Host "  build     - Build Docker images"
    Write-Host "  logs      - View logs (optional: service name)"
    Write-Host "  status    - Check service status"
    Write-Host "  clean     - Remove all containers, volumes, and images"
    Write-Host "  backup    - Backup database and uploads"
    Write-Host "  help      - Show this help message"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\deploy.ps1 start           # Start all services"
    Write-Host "  .\deploy.ps1 logs app        # View app logs"
    Write-Host "  .\deploy.ps1 logs postgres   # View database logs"
}

# Main
switch ($Command.ToLower()) {
    "start" {
        Test-Prerequisites
        Start-Services
    }
    "stop" {
        Stop-Services
    }
    "restart" {
        Restart-Services
    }
    "build" {
        Test-Prerequisites
        Build-Images
    }
    "logs" {
        Show-Logs -ServiceName $Service
    }
    "status" {
        Get-Status
    }
    "clean" {
        Clear-All
    }
    "backup" {
        Backup-Data
    }
    "help" {
        Show-Help
    }
    default {
        Write-Error "Unknown command: $Command"
        Show-Help
        exit 1
    }
}
