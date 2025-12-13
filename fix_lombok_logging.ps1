# PowerShell script to add explicit Logger declarations to classes that have @Slf4j but are failing compilation

# First, let's add a static Logger field to each class that uses @Slf4j but doesn't have the log field working

# For DatabaseConfig.java
$databaseConfigPath = "src\main\java\com\carselling\oldcar\config\DatabaseConfig.java"
$content = Get-Content $databaseConfigPath -Raw
$content = $content -replace '@Slf4j(\r?\n)public class DatabaseConfig', '@Slf4j$1public class DatabaseConfig {$1    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);$1'
$content = $content -replace 'import org.springframework.transaction.annotation.EnableTransactionManagement;', 'import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;'
Set-Content -Path $databaseConfigPath -Value $content

Write-Host "Fixed DatabaseConfig.java"

# Similar pattern for other files...
# This is a starting point - we'll need to apply this pattern to other files as well
