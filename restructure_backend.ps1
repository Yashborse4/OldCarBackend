# PowerShell script to restructure the backend code
# This script will reorganize the Java code to follow proper naming conventions

$baseDir = "D:\Startup\Car Frontend Backend\Sell-the-old-Car"
$srcDir = "$baseDir\src\main\java"
$testDir = "$baseDir\src\test\java"

# Step 1: Create new proper package structure
Write-Host "Creating new package structure..." -ForegroundColor Green

$newPackagePath = "$srcDir\com\carselling\oldcar"
$newTestPackagePath = "$testDir\com\carselling\oldcar"

# Create main directories
New-Item -ItemType Directory -Force -Path "$newPackagePath\config"
New-Item -ItemType Directory -Force -Path "$newPackagePath\controller\auth"
New-Item -ItemType Directory -Force -Path "$newPackagePath\controller\car"
New-Item -ItemType Directory -Force -Path "$newPackagePath\controller\user"
New-Item -ItemType Directory -Force -Path "$newPackagePath\controller\chat"
New-Item -ItemType Directory -Force -Path "$newPackagePath\controller\dealer"
New-Item -ItemType Directory -Force -Path "$newPackagePath\dto\auth"
New-Item -ItemType Directory -Force -Path "$newPackagePath\dto\car"
New-Item -ItemType Directory -Force -Path "$newPackagePath\dto\user"
New-Item -ItemType Directory -Force -Path "$newPackagePath\dto\chat"
New-Item -ItemType Directory -Force -Path "$newPackagePath\dto\common"
New-Item -ItemType Directory -Force -Path "$newPackagePath\entity"
New-Item -ItemType Directory -Force -Path "$newPackagePath\entity\chat"
New-Item -ItemType Directory -Force -Path "$newPackagePath\exception"
New-Item -ItemType Directory -Force -Path "$newPackagePath\repository"
New-Item -ItemType Directory -Force -Path "$newPackagePath\repository\chat"
New-Item -ItemType Directory -Force -Path "$newPackagePath\security"
New-Item -ItemType Directory -Force -Path "$newPackagePath\security\jwt"
New-Item -ItemType Directory -Force -Path "$newPackagePath\service"
New-Item -ItemType Directory -Force -Path "$newPackagePath\service\impl"
New-Item -ItemType Directory -Force -Path "$newPackagePath\service\chat"
New-Item -ItemType Directory -Force -Path "$newPackagePath\util"
New-Item -ItemType Directory -Force -Path "$newPackagePath\graphql"
New-Item -ItemType Directory -Force -Path "$newPackagePath\graphql\resolver"

# Create test directories
New-Item -ItemType Directory -Force -Path "$newTestPackagePath"

Write-Host "Package structure created successfully!" -ForegroundColor Green

# Step 2: Backup current structure
Write-Host "Creating backup..." -ForegroundColor Yellow
$backupPath = "$baseDir\backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
Copy-Item -Path "$srcDir\com\CarSelling" -Destination $backupPath -Recurse -Force
Write-Host "Backup created at: $backupPath" -ForegroundColor Green

Write-Host "Restructuring complete! Next steps:" -ForegroundColor Cyan
Write-Host "1. Move files to new structure" -ForegroundColor White
Write-Host "2. Update package declarations in all Java files" -ForegroundColor White
Write-Host "3. Update imports in all Java files" -ForegroundColor White
Write-Host "4. Update build.gradle with new package name" -ForegroundColor White
Write-Host "5. Clean and rebuild the project" -ForegroundColor White
