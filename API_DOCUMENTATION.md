# Car Selling Platform API Documentation

## Overview

The Car Selling Platform API is a comprehensive REST API that provides endpoints for user management, vehicle listings, real-time chat, notifications, file uploads, and administrative functions. Built with Spring Boot 3.x and featuring modern security, caching, rate limiting, and monitoring capabilities.

## Base URLs

- **Development**: `http://localhost:8080`
- **Staging**: `https://staging-api.carselling.com`
- **Production**: `https://api.carselling.com`

## Authentication

The API uses JWT (JSON Web Token) authentication. Include the token in the Authorization header:

```
Authorization: Bearer <your-jwt-token>
```

## API Documentation UI

Interactive API documentation is available via Swagger UI:
- **Development**: `http://localhost:8080/swagger-ui/index.html`
- **Production**: `https://api.carselling.com/swagger-ui/index.html`

## Rate Limiting

The API implements rate limiting to ensure fair usage:

| Endpoint Category | Rate Limit | Time Window |
|------------------|------------|-------------|
| General API | 100 requests | 1 minute |
| Authentication (Login) | 5 attempts | 15 minutes |
| Registration | 3 attempts | 10 minutes |
| File Upload | 10 uploads | 1 hour |
| Email Services | 5 emails | 1 hour |

Rate limit headers are included in responses:
- `X-Rate-Limit-Remaining`: Remaining requests
- `X-Rate-Limit-Limit`: Total limit
- `X-Rate-Limit-Retry-After-Seconds`: Retry after seconds (when exceeded)

## Core API Endpoints

### Authentication & User Management

#### POST /api/auth/register
Register a new user account.

**Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "password": "securePassword123",
  "phoneNumber": "+1234567890"
}
```

#### POST /api/auth/login
Authenticate user and receive JWT token.

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "password": "securePassword123"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "email": "john.doe@example.com",
      "firstName": "John",
      "lastName": "Doe"
    }
  }
}
```

### Vehicle Management

#### GET /api/vehicles/search
Advanced vehicle search with filtering and pagination.

**Query Parameters:**
- `make`: Vehicle make (e.g., Toyota, BMW)
- `model`: Vehicle model
- `minPrice`: Minimum price
- `maxPrice`: Maximum price
- `minYear`: Minimum year
- `maxYear`: Maximum year
- `fuelType`: Fuel type (PETROL, DIESEL, ELECTRIC, HYBRID)
- `location`: Location filter
- `sortBy`: Sort field (price, year, mileage)
- `sortDirection`: Sort direction (ASC, DESC)
- `page`: Page number (default: 0)
- `size`: Page size (default: 10)

#### GET /api/vehicles/{id}
Get detailed information about a specific vehicle.

#### POST /api/vehicles
Create a new vehicle listing (authenticated users only).

#### GET /api/vehicles/recommendations
Get personalized vehicle recommendations based on user preferences.

#### GET /api/vehicles/trending
Get trending vehicles based on views and interactions.

#### GET /api/vehicles/location/{latitude}/{longitude}
Get vehicles near a specific location within a radius.

### Real-time Chat

#### GET /api/chat/conversations
Get all conversations for the authenticated user.

#### GET /api/chat/{conversationId}/messages
Get messages for a specific conversation.

#### POST /api/chat/{conversationId}/messages
Send a message in a conversation.

#### WebSocket Endpoint: `/ws/chat`
Real-time messaging via WebSocket connection.

### File Upload

#### POST /api/files/upload
Upload files (images, documents) with support for multiple files.

**Request:** Multipart form data with file(s)

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "fileName": "car_image.jpg",
      "fileUrl": "https://s3.amazonaws.com/bucket/uploads/car_image.jpg",
      "fileSize": 1048576,
      "contentType": "image/jpeg"
    }
  ]
}
```

### Notifications

#### GET /api/notifications
Get notifications for the authenticated user with pagination.

#### POST /api/notifications/{id}/mark-read
Mark a notification as read.

#### POST /api/notifications/mark-all-read
Mark all notifications as read.

### Email Services

#### POST /api/email/send
Send various types of emails (verification, password reset, etc.).

### Admin Panel (Admin Role Required)

#### GET /api/admin/users
Get all users with admin management capabilities.

#### PUT /api/admin/users/{userId}/role
Change user role.

#### POST /api/admin/users/{userId}/ban
Ban a user account.

#### GET /api/admin/dashboard/stats
Get comprehensive dashboard statistics.

## Response Format

All API responses follow a consistent format:

**Success Response:**
```json
{
  "success": true,
  "data": { /* response data */ },
  "message": "Optional success message",
  "timestamp": "2023-12-07T10:30:00Z"
}
```

**Error Response:**
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": "Optional detailed error information"
  },
  "timestamp": "2023-12-07T10:30:00Z"
}
```

## HTTP Status Codes

| Status Code | Description |
|-------------|-------------|
| 200 | OK - Request successful |
| 201 | Created - Resource created successfully |
| 400 | Bad Request - Invalid request format/data |
| 401 | Unauthorized - Authentication required |
| 403 | Forbidden - Access denied |
| 404 | Not Found - Resource not found |
| 429 | Too Many Requests - Rate limit exceeded |
| 500 | Internal Server Error - Server error |

## Data Models

### User
```json
{
  "id": 1,
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phoneNumber": "+1234567890",
  "role": "USER",
  "isActive": true,
  "createdAt": "2023-12-07T10:30:00Z"
}
```

### Vehicle
```json
{
  "id": 1,
  "make": "Toyota",
  "model": "Camry",
  "year": 2020,
  "price": 25000,
  "mileage": 30000,
  "fuelType": "PETROL",
  "transmission": "AUTOMATIC",
  "description": "Well maintained vehicle",
  "location": "New York, NY",
  "images": ["image1.jpg", "image2.jpg"],
  "owner": { /* User object */ },
  "isActive": true,
  "createdAt": "2023-12-07T10:30:00Z"
}
```

## Caching

The API implements Redis caching for improved performance:

| Cache | TTL | Description |
|-------|-----|-------------|
| users | 30 minutes | User profile data |
| vehicles | 2 hours | Vehicle listings |
| vehicleSearch | 15 minutes | Search results |
| vehicleRecommendations | 1 hour | User recommendations |
| vehicleStats | 4 hours | Vehicle statistics |

## Performance Monitoring

The API includes comprehensive performance monitoring:

- **API Response Times**: Tracked per endpoint
- **Error Rates**: Monitored and logged
- **Cache Hit Rates**: Redis cache performance
- **System Metrics**: Memory, CPU, thread usage
- **Database Query Performance**: Query execution times

Performance metrics are logged every 5 minutes and available via:
- Application logs
- Micrometer metrics endpoint: `/actuator/metrics`
- Custom performance dashboard

## Security Features

- **JWT Authentication**: Secure token-based authentication
- **Rate Limiting**: Prevent abuse and ensure fair usage
- **Input Validation**: Comprehensive request validation
- **SQL Injection Protection**: Parameterized queries
- **XSS Protection**: Input sanitization
- **HTTPS Enforcement**: Secure communication
- **Role-Based Access Control**: User and admin roles

## Testing

Comprehensive test suite available:
- Unit tests for all services
- Integration tests for controllers
- Performance tests for critical endpoints
- Security tests for authentication flows

Run tests with:
```bash
mvn test
```

## Environment Variables

Required environment variables:

```bash
# Database
DB_URL=jdbc:mysql://localhost:3306/carsellingdb
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password

# JWT
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION=86400000

# AWS S3
AWS_ACCESS_KEY_ID=your_aws_key
AWS_SECRET_ACCESS_KEY=your_aws_secret
AWS_S3_BUCKET_NAME=your_s3_bucket

# Firebase (for notifications)
FIREBASE_PROJECT_ID=your_firebase_project
FIREBASE_PRIVATE_KEY=your_firebase_key

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

# Email
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_app_password
```

## Support

For API support and questions:
- Email: support@carselling.com
- Documentation: This file and Swagger UI
- GitHub Issues: [Repository Issues Page]

## Changelog

### Version 2.0
- Enhanced vehicle search and recommendations
- Real-time chat functionality
- Comprehensive notification system
- File upload with AWS S3 integration
- Email service with template support
- Rate limiting and security enhancements
- Redis caching layer
- Performance monitoring
- Admin dashboard APIs
- Swagger API documentation
