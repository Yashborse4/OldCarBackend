# Sell The Old Car - Production Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying the Sell The Old Car application using Docker and Docker Compose in a production environment.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Docker Network                        │
│                     (car-network: 172.20.0.0/16)            │
├─────────────┬─────────────┬─────────────┬─────────────────┤
│     App     │  PostgreSQL │    Redis    │   OpenSearch    │
│   (8080)    │   (5432)    │   (6379)    │    (9200)       │
│  Java 21    │   v16       │    v7       │    v2.12        │
│  Spring     │   Alpine    │   Alpine    │    Official     │
└─────────────┴─────────────┴─────────────┴─────────────────┘
```

## Prerequisites

### Required Software
- **Docker** 20.10+ (Install from [docker.com](https://docs.docker.com/get-docker/))
- **Docker Compose** 2.0+ (Included with Docker Desktop)
- **Git** (for cloning the repository)

### System Requirements
- **RAM**: 4GB minimum, 8GB recommended
- **CPU**: 2 cores minimum, 4 cores recommended
- **Storage**: 20GB free space minimum
- **OS**: Linux (Ubuntu 20.04+), macOS 11+, or Windows 10/11 with WSL2

## Quick Start

### 1. Clone and Navigate

```bash
git clone <your-repo-url>
cd Sell-the-old-Car
```

### 2. Configure Environment

```bash
# Copy the example environment file
cp .env.example .env

# Edit with your actual values
nano .env  # or use your preferred editor
```

**Required variables to set:**

| Variable | Description | How to Get |
|----------|-------------|------------|
| `DB_PASSWORD` | PostgreSQL password | Generate strong password |
| `JWT_SECRET` | JWT signing key | `openssl rand -hex 32` |
| `B2_APPLICATION_KEY_ID` | Backblaze B2 Key ID | [B2 Console](https://secure.backblaze.com/b2_buckets.htm) |
| `B2_APPLICATION_KEY` | Backblaze B2 App Key | B2 Console |
| `B2_BUCKET_NAME` | B2 Bucket name | B2 Console |
| `B2_BUCKET_ID` | B2 Bucket ID | B2 Console |
| `B2_CDN_DOMAIN` | CDN domain | B2 Console → Bucket Settings |
| `OPENSEARCH_PASSWORD` | OpenSearch admin password | Set your password |

### 3. Deploy

**Linux/macOS:**
```bash
chmod +x deploy.sh
./deploy.sh start
```

**Windows (PowerShell):**
```powershell
.\deploy.ps1 start
```

### 4. Verify Deployment

```bash
# Check all services are running
docker-compose ps

# View logs
docker-compose logs -f app

# Test health endpoint
curl http://localhost:8080/actuator/health
```

## Deployment Commands

### Linux/macOS (deploy.sh)

```bash
./deploy.sh start      # Start all services
./deploy.sh stop       # Stop all services
./deploy.sh restart    # Restart all services
./deploy.sh build      # Rebuild Docker images
./deploy.sh logs app   # View app logs
./deploy.sh logs postgres  # View database logs
./deploy.sh status     # Check service status
./deploy.sh backup     # Backup database and uploads
./deploy.sh clean      # Remove everything (WARNING: data loss!)
```

### Windows (deploy.ps1)

```powershell
.\deploy.ps1 start        # Start all services
.\deploy.ps1 stop         # Stop all services
.\deploy.ps1 restart      # Restart all services
.\deploy.ps1 build        # Rebuild Docker images
.\deploy.ps1 logs app      # View app logs
.\deploy.ps1 status        # Check service status
.\deploy.ps1 backup        # Backup database and uploads
.\deploy.ps1 clean        # Remove everything (WARNING: data loss!)
```

## Service Details

### Application (app)
- **Port**: 8080
- **Health Check**: `/actuator/health`
- **Logs**: `docker-compose logs -f app`
- **Restart**: `docker-compose restart app`

### PostgreSQL Database
- **Port**: 5432
- **Database**: `carselling`
- **User**: Set in `DB_USERNAME`
- **Data Volume**: `postgres-data`
- **Backup**: `docker exec car-selling-postgres pg_dump -U car_user carselling > backup.sql`

### Redis Cache
- **Port**: 6379
- **Persistence**: AOF enabled
- **Max Memory**: 256MB
- **Eviction Policy**: allkeys-lru

### OpenSearch
- **Port**: 9200 (HTTP), 9600 (Metrics)
- **Security**: Disabled for internal network
- **Heap**: 512MB (configurable via `OPENSEARCH_JAVA_OPTS`)

## Production Checklist

### Security
- [ ] Change all default passwords in `.env`
- [ ] Generate secure JWT secret (256-bit)
- [ ] Configure firewall rules (only expose 8080)
- [ ] Set up SSL/TLS termination (use nginx/traefik)
- [ ] Regular security updates: `docker-compose pull && docker-compose up -d`

### Performance
- [ ] Monitor JVM heap usage
- [ ] Set appropriate memory limits in docker-compose.yml
- [ ] Enable PostgreSQL connection pooling
- [ ] Configure Redis maxmemory policy
- [ ] Set up log rotation

### Backups
- [ ] Automated database backups
- [ ] File upload backups
- [ ] Test restore procedures
- [ ] Offsite backup storage

### Monitoring
- [ ] Set up application metrics (Prometheus)
- [ ] Configure alerting (PagerDuty/Opsgenie)
- [ ] Log aggregation (ELK/Loki)
- [ ] Uptime monitoring

## Troubleshooting

### Services Won't Start

```bash
# Check for port conflicts
sudo lsof -i :8080
sudo lsof -i :5432
sudo lsof -i :9200

# View detailed logs
docker-compose logs --tail=100 app

# Check resource usage
docker stats
```

### Database Connection Issues

```bash
# Test database connectivity
docker exec -it car-selling-postgres psql -U car_user -d carselling -c "\dt"

# Reset database (WARNING: data loss)
docker-compose down -v
```

### Out of Memory

```bash
# Increase memory limits in docker-compose.yml
deploy:
  resources:
    limits:
      memory: 4G  # Increase from 2G
```

### Health Check Failures

```bash
# Check specific service health
docker inspect --format='{{.State.Health.Status}}' car-selling-app

# View health check logs
docker inspect --format='{{json .State.Health}}' car-selling-app | jq
```

## Advanced Configuration

### SSL/TLS with Nginx

Create `docker/nginx/nginx.conf`:

```nginx
server {
    listen 443 ssl http2;
    server_name yourdomain.com;
    
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    
    location / {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### Environment-Specific Configs

Create separate compose files:
- `docker-compose.yml` - Production
- `docker-compose.dev.yml` - Development
- `docker-compose.staging.yml` - Staging

Override with:
```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Scaling Services

```bash
# Scale app to 3 instances
docker-compose up -d --scale app=3

# Note: Requires load balancer (nginx/traefik)
```

## Maintenance

### Weekly Tasks
- Review logs for errors
- Check disk space usage
- Monitor resource utilization

### Monthly Tasks
- Update base images: `docker-compose pull`
- Security patches
- Backup verification
- Performance review

### Update Procedure

```bash
# 1. Backup
docker exec car-selling-postgres pg_dump -U car_user carselling > backup_$(date +%Y%m%d).sql

# 2. Pull updates
docker-compose pull

# 3. Rebuild
docker-compose build --no-cache

# 4. Rolling restart
docker-compose up -d

# 5. Verify
curl http://localhost:8080/actuator/health
```

## Support

For issues and questions:
1. Check logs: `docker-compose logs`
2. Review troubleshooting section
3. Check GitHub Issues
4. Contact development team

---

**Version**: 1.0  
**Last Updated**: 2024-02-23  
**Docker Version**: 20.10+  
**Compose Version**: 2.0+
