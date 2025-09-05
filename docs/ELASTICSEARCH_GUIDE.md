# Elasticsearch Advanced Search Guide

This guide covers the setup and usage of Elasticsearch-based advanced search functionality in the Car Selling Platform.

## Overview

Our platform uses Elasticsearch to provide advanced search capabilities including:

- **Full-text search** with relevance boosting
- **Geo-spatial search** for location-based filtering
- **Faceted search** with aggregations for filtering
- **Auto-complete suggestions** for search queries
- **Similar vehicle recommendations**
- **Search analytics** and trending terms

## Quick Start

### 1. Start Elasticsearch with Docker

```bash
# Navigate to the elasticsearch docker directory
cd docker/elasticsearch

# Start Elasticsearch and Kibana
docker-compose up -d

# Check if services are running
docker-compose ps
```

### 2. Verify Elasticsearch Connection

```bash
# Check Elasticsearch health
curl http://localhost:9200/_cluster/health

# Check if index exists
curl http://localhost:9200/vehicles
```

### 3. Access Kibana Dashboard

Open your browser and navigate to: http://localhost:5601

## API Endpoints

### Intelligent Search
Combines multiple search strategies based on input parameters.

```http
GET /api/search/intelligent?query=toyota&make=Toyota&minPrice=15000&maxPrice=50000
```

**Parameters:**
- `query` - Search text (optional)
- `make` - Vehicle make (optional)  
- `model` - Vehicle model (optional)
- `minPrice` / `maxPrice` - Price range (optional)
- `minYear` / `maxYear` - Year range (optional)
- `fuelType` - Fuel type filter (optional)
- `transmission` - Transmission type (optional)
- `bodyType` - Body type filter (optional)
- `location` - Location filter (optional)
- `latitude` / `longitude` / `radius` - Geo search (optional)
- `verified` - Only verified vehicles (optional)
- `featured` - Only featured vehicles (optional)
- `sortBy` - Sort field (default: _score)
- `sortDirection` - Sort direction (asc/desc)
- `page` - Page number (default: 0)
- `size` - Page size (default: 20)

### Full-Text Search
Advanced text search with relevance boosting.

```http
GET /api/search/fulltext?query=luxury sedan&page=0&size=10
```

### Geo-spatial Search
Find vehicles within a geographic area.

```http
GET /api/search/geo?latitude=40.7128&longitude=-74.0060&radius=25&page=0&size=15
```

### Faceted Search
Search with facet aggregations for filtering options.

```http
GET /api/search/faceted?query=SUV&page=0&size=20
```

### Auto-complete Suggestions
Get search suggestions for auto-complete functionality.

```http
GET /api/search/suggestions?prefix=toy&limit=5
```

### Similar Vehicles
Find vehicles similar to a specific vehicle.

```http
GET /api/search/similar/123?page=0&size=10
```

### Trending Search Terms
Get currently trending search terms.

```http
GET /api/search/trending?limit=10
```

## Data Synchronization

### Manual Sync
Synchronize specific vehicle or all vehicles to Elasticsearch:

```http
# Sync specific vehicle
POST /api/search/sync/123

# Bulk sync all vehicles
POST /api/search/sync/bulk
```

### Automatic Sync
The system automatically synchronizes vehicle data when:
- A new vehicle is created
- An existing vehicle is updated  
- A vehicle is deleted

This is handled by `VehicleSearchSyncListener` using Spring Events.

## Search Document Structure

The `VehicleSearchDocument` contains optimized fields for searching:

```json
{
  "id": "123",
  "vehicleId": 123,
  "make": "Toyota",
  "model": "Camry",
  "year": 2022,
  "price": 25000.00,
  "mileage": 15000,
  "fuelType": "PETROL",
  "transmission": "AUTOMATIC",
  "bodyType": "SEDAN",
  "description": "Well maintained sedan...",
  "searchableText": "Toyota Camry 2022 Sedan...",
  "features": ["AC", "GPS", "Leather"],
  "tags": ["reliable", "fuel-efficient"],
  "location": "New York, NY",
  "geoPoint": {
    "lat": 40.7128,
    "lon": -74.0060
  },
  "ownerId": 456,
  "ownerName": "John Doe",
  "ownerType": "INDIVIDUAL",
  "status": "ACTIVE",
  "isVerified": true,
  "isFeatured": false,
  "viewCount": 245,
  "searchBoost": 1.2,
  "marketValue": 24000.00,
  "depreciation": 0.05,
  "demandScore": 0.8,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-16T14:45:00Z",
  "suggest": {
    "input": ["Toyota", "Camry", "Toyota Camry"],
    "weight": 5
  }
}
```

## Search Features

### Boosting Strategy
Search results use intelligent boosting:
- **Featured vehicles**: 2.0x boost
- **Verified vehicles**: 1.5x boost  
- **New vehicles** (<30 days): 1.2x boost
- **High demand vehicles**: Dynamic boost based on views/inquiries

### Geo-spatial Search
Supports location-based search with configurable radius:
- Uses Elasticsearch geo_point mapping
- Supports distance sorting
- Configurable search radius (default: 50km)

### Faceted Search
Provides filtering facets:
- **Makes**: Top vehicle makes with counts
- **Fuel Types**: Available fuel types
- **Transmissions**: Manual/Automatic options
- **Body Types**: Sedan, SUV, Hatchback, etc.
- **Price Ranges**: Grouped price ranges
- **Year Ranges**: Grouped year ranges

### Auto-complete
Smart suggestions based on:
- Vehicle makes and models
- Popular search terms
- User search history (future enhancement)

## Performance Monitoring

Search operations are monitored for performance:
- Response time tracking
- Success/failure rates
- Popular search terms analytics
- Geographic search patterns

## Configuration

### Environment Variables

```yaml
# Elasticsearch Connection
ELASTICSEARCH_URIS=http://localhost:9200
ELASTICSEARCH_USERNAME=
ELASTICSEARCH_PASSWORD=
ELASTICSEARCH_CONNECTION_TIMEOUT=5s
ELASTICSEARCH_SOCKET_TIMEOUT=10s

# Index Settings  
elasticsearch.index.vehicles.name=vehicles
elasticsearch.index.vehicles.shards=1
elasticsearch.index.vehicles.replicas=0

# Search Settings
elasticsearch.search.default.size=20
elasticsearch.search.max.size=100
elasticsearch.search.boost.featured=2.0
elasticsearch.search.boost.verified=1.5
```

### Elasticsearch Properties
Additional configuration in `elasticsearch.properties`:
- Connection timeouts and retry policies
- Bulk operation settings
- Circuit breaker configuration
- Performance tuning parameters

## Troubleshooting

### Common Issues

1. **Connection Refused**
   ```bash
   # Check if Elasticsearch is running
   docker ps | grep elasticsearch
   
   # Check logs
   docker logs car-search-elasticsearch
   ```

2. **Index Not Found**
   ```bash
   # Recreate index by bulk sync
   curl -X POST http://localhost:9000/api/search/sync/bulk
   ```

3. **Poor Search Performance**
   - Check Elasticsearch cluster health
   - Monitor memory usage
   - Review search query complexity
   - Consider adding more replicas

4. **Sync Issues**
   - Check application logs for sync errors
   - Verify database connectivity
   - Ensure Elasticsearch is accessible

### Health Check
```http
GET /api/search/health
```

Returns:
```json
{
  "status": "healthy",
  "timestamp": 1642234567890,
  "service": "elasticsearch",
  "features": [
    "full-text search",
    "geo-spatial search", 
    "faceted search",
    "auto-complete suggestions",
    "similarity search"
  ]
}
```

## Development Tips

1. **Use Kibana** for debugging queries and exploring data
2. **Monitor logs** for search performance and errors  
3. **Test different boost values** for relevance tuning
4. **Use bulk operations** for large data synchronization
5. **Implement circuit breakers** for resilience

## Production Considerations

1. **Cluster Setup**: Use multi-node cluster for high availability
2. **Security**: Enable authentication and SSL/TLS
3. **Monitoring**: Set up comprehensive monitoring and alerting
4. **Backup**: Configure automated snapshots
5. **Scaling**: Plan for data growth and query load

## Future Enhancements

- Machine Learning based search ranking
- Personalized search results  
- Voice search integration
- Advanced analytics dashboard
- Multi-language search support
