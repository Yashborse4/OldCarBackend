# Redis and Elasticsearch Setup Guide for Windows

This comprehensive guide provides step-by-step instructions for setting up Redis and Elasticsearch on Windows systems.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Redis Setup](#redis-setup)
- [Elasticsearch Setup](#elasticsearch-setup)
- [Configuration](#configuration)
- [Testing Installation](#testing-installation)
- [Production Configuration](#production-configuration)
- [Troubleshooting](#troubleshooting)

## Prerequisites

Before starting, ensure you have:
- Windows 10/11 or Windows Server 2016+
- Administrator privileges
- At least 4GB RAM (8GB+ recommended for Elasticsearch)
- Java 11+ (for Elasticsearch)
- PowerShell or Command Prompt access

---

## Redis Setup

### Method 1: Using Windows Subsystem for Linux (WSL) - Recommended

#### Step 1: Enable WSL
1. Open PowerShell as Administrator
2. Run the following command:
```powershell
wsl --install
```
3. Restart your computer when prompted
4. Set up Ubuntu or your preferred Linux distribution

#### Step 2: Install Redis in WSL
1. Open WSL terminal
2. Update package lists:
```bash
sudo apt update
```
3. Install Redis:
```bash
sudo apt install redis-server
```
4. Start Redis service:
```bash
sudo service redis-server start
```
5. Enable Redis to start on boot:
```bash
sudo systemctl enable redis-server
```

### Method 2: Using Docker (Alternative)

#### Step 1: Install Docker Desktop
1. Download Docker Desktop from https://docker.com/products/docker-desktop
2. Install and restart your computer
3. Start Docker Desktop

#### Step 2: Run Redis Container
```powershell
docker run --name redis-server -p 6379:6379 -d redis:latest
```

### Method 3: Windows Native (Third-party builds)

#### Step 1: Download Redis for Windows
1. Visit https://github.com/tporadowski/redis/releases
2. Download the latest `.msi` installer
3. Run the installer as Administrator

#### Step 2: Install and Configure
1. Follow the installation wizard
2. Choose installation directory (default: `C:\Program Files\Redis`)
3. Select "Add Redis to PATH" option
4. Complete installation

#### Step 3: Start Redis Service
1. Open Services (services.msc)
2. Find "Redis" service
3. Right-click → Start
4. Set startup type to "Automatic" for auto-start

---

## Elasticsearch Setup

### Step 1: Install Java 11+

#### Download and Install Java
1. Visit https://adoptium.net/temurin/releases/
2. Download Java 11+ for Windows x64
3. Install with default settings
4. Verify installation:
```powershell
java -version
```

#### Set JAVA_HOME (if not set automatically)
```powershell
# Add to system environment variables
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-11.x.x.x-hotspot"
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-11.x.x.x-hotspot", "Machine")
```

### Step 2: Download Elasticsearch

1. Visit https://www.elastic.co/downloads/elasticsearch
2. Download the Windows ZIP archive (not the MSI)
3. Extract to a directory like `C:\elasticsearch-8.x.x`
4. Rename the folder to `C:\elasticsearch` for simplicity

### Step 3: Configure Elasticsearch

#### Basic Configuration
1. Navigate to `C:\elasticsearch\config`
2. Edit `elasticsearch.yml`:
```yaml
# Cluster name
cluster.name: my-application

# Node name
node.name: node-1

# Network settings
network.host: localhost
http.port: 9200

# Discovery settings for single node
discovery.type: single-node

# Security settings (for development)
xpack.security.enabled: false

# Path settings
path.data: C:\elasticsearch\data
path.logs: C:\elasticsearch\logs
```

#### Memory Configuration
1. Edit `C:\elasticsearch\config\jvm.options`
2. Adjust heap size based on your RAM:
```
# For 8GB RAM system
-Xms2g
-Xmx2g

# For 16GB RAM system
-Xms4g
-Xmx4g
```

### Step 4: Create Windows Service for Elasticsearch

#### Method 1: Using elasticsearch-service.bat
1. Open Command Prompt as Administrator
2. Navigate to Elasticsearch bin directory:
```cmd
cd C:\elasticsearch\bin
```
3. Install as service:
```cmd
elasticsearch-service.bat install
```
4. Start the service:
```cmd
elasticsearch-service.bat start
```

#### Method 2: Manual Service Creation
1. Create a batch file `start-elasticsearch.bat`:
```batch
@echo off
cd /d "C:\elasticsearch\bin"
elasticsearch.bat
```
2. Use NSSM (Non-Sucking Service Manager) to create service:
   - Download NSSM from https://nssm.cc/download
   - Extract and run as Administrator:
```cmd
nssm install Elasticsearch "C:\elasticsearch\start-elasticsearch.bat"
nssm set Elasticsearch DisplayName "Elasticsearch Server"
nssm set Elasticsearch Description "Elasticsearch search engine"
nssm start Elasticsearch
```

---

## Configuration

### Redis Configuration

#### Basic Redis Configuration (redis.conf)
```conf
# Network
bind 127.0.0.1
port 6379

# General
daemonize yes
supervised systemd

# Logging
loglevel notice
logfile /var/log/redis/redis-server.log

# Persistence
save 900 1
save 300 10
save 60 10000

# Security (set a password)
requirepass your_strong_password_here

# Memory
maxmemory 256mb
maxmemory-policy allkeys-lru
```

### Elasticsearch Configuration

#### Production elasticsearch.yml
```yaml
# Cluster
cluster.name: production-cluster
node.name: ${HOSTNAME}

# Paths
path.data: C:\elasticsearch\data
path.logs: C:\elasticsearch\logs

# Network
network.host: 0.0.0.0
http.port: 9200
transport.port: 9300

# Discovery (for single node)
discovery.type: single-node

# Security
xpack.security.enabled: true
xpack.security.transport.ssl.enabled: true
xpack.security.http.ssl.enabled: true

# Monitoring
xpack.monitoring.collection.enabled: true
```

---

## Testing Installation

### Test Redis Connection

#### Using Redis CLI (WSL/Docker)
```bash
# Connect to Redis
redis-cli

# Test basic operations
127.0.0.1:6379> ping
PONG
127.0.0.1:6379> set test "Hello Redis"
OK
127.0.0.1:6379> get test
"Hello Redis"
```

#### Using PowerShell (with Redis module)
```powershell
# Install Redis PowerShell module
Install-Module -Name Redis -Force

# Test connection
$redis = New-RedisConnection -Server "localhost" -Port 6379
Set-RedisString -Connection $redis -Key "test" -Value "Hello from PowerShell"
Get-RedisString -Connection $redis -Key "test"
```

### Test Elasticsearch

#### Using PowerShell/Curl
```powershell
# Check cluster health
Invoke-RestMethod -Uri "http://localhost:9200/_cluster/health" | ConvertTo-Json

# Check node info
Invoke-RestMethod -Uri "http://localhost:9200/_nodes" | ConvertTo-Json

# Create a test index
$body = @{
    settings = @{
        number_of_shards = 1
        number_of_replicas = 0
    }
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:9200/test-index" -Method PUT -Body $body -ContentType "application/json"
```

#### Using Kibana (Optional)
1. Download Kibana from https://www.elastic.co/downloads/kibana
2. Extract to `C:\kibana`
3. Edit `C:\kibana\config\kibana.yml`:
```yaml
server.port: 5601
server.host: "localhost"
elasticsearch.hosts: ["http://localhost:9200"]
```
4. Start Kibana: `C:\kibana\bin\kibana.bat`
5. Access at http://localhost:5601

---

## Production Configuration

### Redis Production Settings

#### Security
```conf
# Strong password
requirepass very_strong_password_123!

# Disable dangerous commands
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command EVAL ""
rename-command DEBUG ""
rename-command CONFIG "CONFIG_b7a9f8e2c4d1"
```

#### Performance
```conf
# Optimize for your use case
maxmemory 2gb
maxmemory-policy allkeys-lru

# TCP keepalive
tcp-keepalive 300

# Timeout
timeout 300

# Background saving
save 900 1
save 300 10
save 60 10000

# AOF persistence
appendonly yes
appendfsync everysec
```

### Elasticsearch Production Settings

#### Memory and Performance
```yaml
# Bootstrap memory lock
bootstrap.memory_lock: true

# Indices settings
indices.memory.index_buffer_size: 10%
indices.memory.min_index_buffer_size: 48mb

# Thread pool
thread_pool.write.queue_size: 1000
thread_pool.search.queue_size: 1000

# Cache
indices.queries.cache.size: 10%
indices.fielddata.cache.size: 20%
```

#### Security (X-Pack)
```yaml
# Enable security
xpack.security.enabled: true

# SSL/TLS
xpack.security.transport.ssl.enabled: true
xpack.security.transport.ssl.verification_mode: certificate
xpack.security.transport.ssl.keystore.path: elastic-certificates.p12
xpack.security.transport.ssl.truststore.path: elastic-certificates.p12

# HTTP SSL
xpack.security.http.ssl.enabled: true
xpack.security.http.ssl.keystore.path: elastic-certificates.p12
```

---

## Environment Integration

### Application Connection Examples

#### Node.js/JavaScript
```javascript
// Redis
const redis = require('redis');
const redisClient = redis.createClient({
    host: 'localhost',
    port: 6379,
    password: 'your_password'
});

// Elasticsearch
const { Client } = require('@elastic/elasticsearch');
const esClient = new Client({ 
    node: 'http://localhost:9200'
});
```

#### Python
```python
# Redis
import redis
r = redis.Redis(host='localhost', port=6379, password='your_password')

# Elasticsearch
from elasticsearch import Elasticsearch
es = Elasticsearch(['http://localhost:9200'])
```

#### C# (.NET)
```csharp
// Redis
using StackExchange.Redis;
var redis = ConnectionMultiplexer.Connect("localhost:6379");

// Elasticsearch
using Nest;
var settings = new ConnectionSettings(new Uri("http://localhost:9200"));
var client = new ElasticClient(settings);
```

---

## Monitoring and Maintenance

### Redis Monitoring
```bash
# Monitor commands in real-time
redis-cli monitor

# Get server info
redis-cli info

# Check memory usage
redis-cli info memory

# List all keys (careful in production)
redis-cli keys "*"
```

### Elasticsearch Monitoring
```powershell
# Cluster health
Invoke-RestMethod "http://localhost:9200/_cluster/health"

# Node stats
Invoke-RestMethod "http://localhost:9200/_nodes/stats"

# Index stats
Invoke-RestMethod "http://localhost:9200/_stats"

# Hot threads
Invoke-RestMethod "http://localhost:9200/_nodes/hot_threads"
```

### Automated Backup Scripts

#### Redis Backup (PowerShell)
```powershell
# Redis backup script
$backupDir = "C:\Backups\Redis"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupFile = "$backupDir\redis-backup-$timestamp.rdb"

if (!(Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir -Force
}

# Copy RDB file
Copy-Item "C:\Redis\dump.rdb" $backupFile

# Compress backup
Compress-Archive -Path $backupFile -DestinationPath "$backupFile.zip"
Remove-Item $backupFile
```

#### Elasticsearch Backup
```powershell
# Create snapshot repository
$repoConfig = @{
    type = "fs"
    settings = @{
        location = "C:\elasticsearch\backups"
    }
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:9200/_snapshot/my_backup" -Method PUT -Body $repoConfig -ContentType "application/json"

# Create snapshot
$snapshotConfig = @{
    indices = "*"
    ignore_unavailable = $true
    include_global_state = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:9200/_snapshot/my_backup/snapshot_$(Get-Date -Format 'yyyyMMdd')" -Method PUT -Body $snapshotConfig -ContentType "application/json"
```

---

## Troubleshooting

### Common Redis Issues

#### Redis Won't Start
```bash
# Check if port is in use
netstat -an | findstr 6379

# Check Redis logs
tail -f /var/log/redis/redis-server.log

# Test configuration
redis-server --test-memory 1024
```

#### Connection Issues
```bash
# Test local connection
redis-cli ping

# Test with password
redis-cli -a your_password ping

# Check firewall
netsh advfirewall firewall add rule name="Redis" dir=in action=allow protocol=TCP localport=6379
```

### Common Elasticsearch Issues

#### Elasticsearch Won't Start
```powershell
# Check Java installation
java -version

# Check Elasticsearch logs
Get-Content "C:\elasticsearch\logs\*.log" -Wait

# Check memory settings
echo $env:ES_HEAP_SIZE
```

#### Memory Issues
```yaml
# In elasticsearch.yml
indices.breaker.total.limit: 70%
indices.breaker.fielddata.limit: 40%
indices.breaker.request.limit: 40%
```

#### Port Conflicts
```powershell
# Check if ports are in use
netstat -an | findstr "9200"
netstat -an | findstr "9300"

# Kill processes using ports
Get-Process -Port 9200 | Stop-Process -Force
```

### Performance Tuning

#### Redis Performance
```conf
# Disable save if pure cache
save ""

# Use pipelining for bulk operations
# Client-side implementation required

# Optimize for your access patterns
maxmemory-policy allkeys-lru  # For cache
maxmemory-policy noeviction   # For persistent data
```

#### Elasticsearch Performance
```yaml
# Disable swapping
bootstrap.memory_lock: true

# Optimize for search or indexing
index.refresh_interval: 30s  # For heavy indexing
indices.memory.index_buffer_size: 20%  # For heavy indexing

# For search-heavy workloads
indices.queries.cache.size: 20%
```

---

## Security Best Practices

### Redis Security
- Always use strong passwords
- Bind to specific interfaces only
- Use SSL/TLS for connections
- Disable dangerous commands
- Run with limited user privileges
- Regular security updates

### Elasticsearch Security
- Enable X-Pack security
- Use strong authentication
- Configure SSL/TLS
- Implement role-based access control
- Regular security updates
- Network segmentation

---

## Conclusion

This guide provides a comprehensive setup for both Redis and Elasticsearch on Windows. Remember to:

1. Always test configurations in a development environment first
2. Monitor resource usage and performance
3. Implement proper backup strategies
4. Keep security configurations up to date
5. Regular maintenance and updates

For production deployments, consider using container orchestration platforms like Docker Swarm or Kubernetes for better scalability and management.

## Additional Resources

- [Redis Documentation](https://redis.io/documentation)
- [Elasticsearch Guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [Elastic Stack on Windows](https://www.elastic.co/guide/en/elastic-stack/current/installing-elastic-stack.html)
- [Redis on Windows GitHub](https://github.com/tporadowski/redis)

---

*Last updated: January 2024*
*Version: 1.0*
