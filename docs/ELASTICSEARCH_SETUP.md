# Elasticsearch Setup and Management Guide

Complete guide for setting up, configuring, and managing Elasticsearch for the Car Selling Application.

## Table of Contents
1. [Initial Setup](#initial-setup)
2. [Index Template Application](#index-template-application)
3. [Bulk Data Sync](#bulk-data-sync)
4. [Index Rebuild Process](#index-rebuild-process)
5. [Custom Analyzer Testing](#custom-analyzer-testing)
6. [Performance Tuning](#performance-tuning)
7. [Common Issues and Solutions](#common-issues-and-solutions)

## Initial Setup

### Prerequisites
- Docker and Docker Compose installed
- At least 2GB of RAM available for Elasticsearch
- Port 9200 (Elasticsearch) and 5601 (Kibana) available

### Starting Elasticsearch

1. Navigate to the Elasticsearch directory:
   ```bash
   cd docker/elasticsearch
   ```

2. Start the containers:
   ```bash
   docker-compose up -d
   ```

3. Verify Elasticsearch is running:
   ```bash
   curl -X GET "localhost:9200/_cluster/health?pretty"
   ```

   Expected response:
   ```json
   {
     "status": "yellow" // or "green"
   }
   ```

4. Verify the init container applied the template:
   ```bash
   docker logs car-search-elasticsearch-init
   ```

   You should see:
   ```
   ✓ Index template applied successfully
   ✓ Index template verified
   Elasticsearch initialization complete!
   ```

### Accessing Kibana

Kibana provides a web interface for Elasticsearch management:

1. Open browser: `http://localhost:5601`
2. Navigate to Dev Tools (left sidebar) for query testing
3. Navigate to Index Management to view indices

## Index Template Application

The index template defines the schema and analyzers for the `car_search_index`.

### Manual Template Application

If the init container fails or you need to reapply the template:

```bash
curl -X PUT "localhost:9200/_index_template/car_search_template" \
  -H 'Content-Type: application/json' \
  -d @index-template.json
```

### Verify Template

```bash
curl -X GET "localhost:9200/_index_template/car_search_template?pretty"
```

### Update Template

To update the template (does NOT affect existing indices):

```bash
curl -X PUT "localhost:9200/_index_template/car_search_template?pretty" \
  -H 'Content-Type: application/json' \
  -d @index-template.json
```

> **Important**: Updating the template does NOT update existing indices. You must rebuild the index for changes to take effect.

## Bulk Data Sync

### Initial Sync

After starting the application,synchronize all existing car data to Elasticsearch:

**Option 1: Using REST API**
```bash
curl -X POST "http://localhost:8080/api/admin/search/bulk-sync" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

**Option 2: Via Application Startup**

Add to `application.yml`:
```yaml
elasticsearch:
  sync-on-startup: true
```

### Verify Sync

Check the number of documents indexed:

```bash
curl -X GET "localhost:9200/car_search_index/_count?pretty"
```

### View Sample Documents

```bash
curl -X GET "localhost:9200/car_search_index/_search?size=5&pretty"
```

## Index Rebuild Process

When to rebuild:
- After changing analyzers in the index template
- After adding new fields to VehicleSearchDocument
- After major mapping changes

### Step-by-Step Rebuild

1. **Delete the existing index**
   ```bash
   curl -X DELETE "localhost:9200/car_search_index?pretty"
   ```

2. **Verify template is still present**
   ```bash
   curl -X GET "localhost:9200/_index_template/car_search_template?pretty"
   ```

3. **Create new index (template will be auto-applied)**
   
   The index will be created automatically when data is inserted, OR create it manually:
   ```bash
   curl -X PUT "localhost:9200/car_search_index?pretty"
   ```

4. **Verify index settings and mappings**
   ```bash
   # Check settings
   curl -X GET "localhost:9200/car_search_index/_settings?pretty"
   
   # Check mappings
   curl -X GET "localhost:9200/car_search_index/_mapping?pretty"
   ```

5. **Re-sync all data**
   ```bash
   curl -X POST "http://localhost:8080/api/admin/search/bulk-sync" \
     -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
   ```

### Zero-Downtime Rebuild

For production environments:

1. **Create a new index with a timestamp**
   ```bash
   curl -X PUT "localhost:9200/car_search_index_v2?pretty"
   ```

2. **Sync data to the new index** (modify service to write to both indices temporarily)

3. **Create an alias**
   ```bash
   curl -X POST "localhost:9200/_aliases?pretty" \
     -H 'Content-Type: application/json' \
     -d '{
       "actions": [
         { "remove": { "index": "car_search_index_v1", "alias": "car_search_index" } },
         { "add": { "index": "car_search_index_v2", "alias": "car_search_index" } }
       ]
     }'
   ```

4. **Delete old index**
   ```bash
   curl -X DELETE "localhost:9200/car_search_index_v1?pretty"
   ```

## Custom Analyzer Testing

### Test Autocomplete Analyzer

Test how the edge n-gram analyzer processes brand names:

```bash
curl -X POST "localhost:9200/car_search_index/_analyze?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "analyzer": "autocomplete",
    "text": "Toyota Camry"
  }'
```

Expected tokens: `to`, `toy`, `toyo`, `toyot`, `toyota`, `ca`, `cam`, `camr`, `camry`

### Test Search Analyzer

```bash
curl -X POST "localhost:9200/car_search_index/_analyze?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "analyzer": "autocomplete_search",
    "text": "toy"
  }'
```

Expected: Single token `toy` (no n-grams on search side)

### Test Suggestions

```bash
curl -X GET "localhost:9200/car_search_index/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "suggest": {
      "brand-suggest": {
        "prefix": "toy",
        "completion": {
          "field": "suggest",
          "size": 5
        }
      }
    }
  }'
```

## Performance Tuning

### Optimize Index Settings

For production, adjust these settings:

```bash
curl -X PUT "localhost:9200/car_search_index/_settings?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "index": {
      "number_of_replicas": 1,
      "refresh_interval": "5s",
      "max_result_window": 50000
    }
  }'
```

### Bulk Indexing Performance

For large data imports:

1. **Disable refresh during import**
   ```bash
   curl -X PUT "localhost:9200/car_search_index/_settings?pretty" \
     -H 'Content-Type: application/json' \
     -d '{ "index": { "refresh_interval": "-1" } }'
   ```

2. **Run bulk sync**

3. **Re-enable refresh**
   ```bash
   curl -X PUT "localhost:9200/car_search_index/_settings?pretty" \
     -H 'Content-Type: application/json' \
     -d '{  "index": { "refresh_interval": "1s" } }'
   ```

4. **Force refresh**
   ```bash
   curl -X POST "localhost:9200/car_search_index/_refresh?pretty"
   ```

### Query Performance

#### Use Filter Context

Filters are cached and faster than queries:

```json
{
  "query": {
    "bool": {
      "filter": [
        { "term": { "brand.keyword": "Toyota" } },
        { "term": { "fuelType": "Petrol" } }
      ]
    }
  }
}
```

#### Use Profiling

Analyze slow queries:

```bash
curl -X GET "localhost:9200/car_search_index/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "profile": true,
    "query": {
      "multi_match": {
        "query": "Toyota",
        "fields": ["brand^3", "model^3"]
      }
    }
  }'
```

## Common Issues and Solutions

### Issue: Index Template Not Applied

**Symptoms**: New indices don't have custom analyzers

**Solution**:
```bash
# Check template priority
curl -X GET "localhost:9200/_index_template/car_search_template?pretty"

# Ensure priority is high enough (100+)
# Reapply template if needed
curl -X PUT "localhost:9200/_index_template/car_search_template" \
  -H 'Content-Type: application/json' \
  -d @index-template.json
```

### Issue: Autocomplete Not Working

**Symptoms**: Partial searches don't return results

**Solution**:
1. Verify analyzer is applied:
   ```bash
   curl -X GET "localhost:9200/car_search_index/_mapping?pretty"
   ```
   
   Should show `"analyzer": "autocomplete"` for brand/model fields

2. Test analyzer:
   ```bash
   curl -X POST "localhost:9200/car_search_index/_analyze" \
     -H 'Content-Type: application/json' \
     -d '{ "field": "brand", "text": "Toyota" }'
   ```

3. If analyzer is missing, rebuild the index

### Issue: Slow Search Queries

**Symptoms**: Queries take >500ms

**Solution**:
1. Check cluster health:
   ```bash
   curl -X GET "localhost:9200/_cluster/health?pretty"
   ```

2. Check index stats:
   ```bash
   curl -X GET "localhost:9200/car_search_index/_stats?pretty"
   ```

3. Use bool queries with filter context instead of query context

4. Increase JVM heap size in docker-compose.yml:
   ```yaml
   environment:
     - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
   ```

### Issue: Index Size Too Large

**Symptoms**: Disk space issues

**Solution**:
1. Check index size:
   ```bash
   curl -X GET "localhost:9200/_cat/indices/car*?v&h=index,docs.count,store.size"
   ```

2. Remove old/unused fields from VehicleSearchDocument

3. Disable `_source` for large text fields (if not needed for retrieval):
   ```json
   "description": {
     "type": "text",
     "_source": { "enabled": false }
   }
   ```

4. Use index lifecycle management (ILM) to delete old documents

### Issue: Connection Refused

**Symptoms**: Application can't connect to Elasticsearch

**Solution**:
1. Verify Elasticsearch is running:
   ```bash
   docker ps | grep elasticsearch
   ```

2. Check application.yml configuration:
   ```yaml
   elasticsearch:
     host: localhost  # or elasticsearch if running in Docker network
     port: 9200
   ```

3. Verify network connectivity:
   ```bash
   docker network ls
   docker network inspect car-search-network
   ```

### Issue: Role-Based Masking Not Working

**Symptoms**: All users see seller contact info

**Solution**:
1. Verify VehicleSearchResultMapper is being called:
   - Add logging in `applyRoleBasedMasking` method
   - Check that `currentUser` is being passed correctly

2. Check user role in token:
   ```java
   log.info("Current user role: {}", currentUser != null ? currentUser.getRole() : "ANONYMOUS");
   ```

3. Verify mapper bean is registered:
   ```bash
   # Should show VehicleSearchResultMapper in component scan
   ```

## Monitoring and Maintenance

### Daily Checks

```bash
# Cluster health
curl -X GET "localhost:9200/_cluster/health?pretty"

# Index stats
curl -X GET "localhost:9200/_cat/indices/car*?v"

# Search performance
curl -X GET "localhost:9200/_cat/thread_pool/search?v"
```

### Weekly Maintenance

1. Review slow query logs
2. Optimize indices:
   ```bash
   curl -X POST "localhost:9200/car_search_index/_forcemerge?max_num_segments=1"
   ```
3. Clear cache if needed:
   ```bash
   curl -X POST "localhost:9200/car_search_index/_cache/clear?pretty"
   ```

### Backup and Restore

**Create Snapshot Repository**:
```bash
curl -X PUT "localhost:9200/_snapshot/backup_repo?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "fs",
    "settings": {
      "location": "/usr/share/elasticsearch/backup"
    }
  }'
```

**Create Snapshot**:
```bash
curl -X PUT "localhost:9200/_snapshot/backup_repo/snapshot_1?wait_for_completion=true"
```

**Restore Snapshot**:
```bash
curl -X POST "localhost:9200/_snapshot/backup_repo/snapshot_1/_restore?pretty"
```

## Additional Resources

- [Elasticsearch Official Documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [Spring Data Elasticsearch](https://docs.spring.io/spring-data/elasticsearch/docs/current/reference/html/)
- [Elasticsearch Performance Tuning](https://www.elastic.co/guide/en/elasticsearch/reference/current/tune-for-search-speed.html)
