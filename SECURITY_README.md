# Car Selling Platform - Security Guide

## Overview

This document outlines the security features, best practices, and deployment instructions for the Car Selling Platform.

## Security Features Implemented

### 1. Authentication & Authorization
- **JWT-based Authentication** with secure token generation
- **Role-based Access Control (RBAC)** with granular permissions:
  - `VIEWER`: Read-only access to car listings
  - `SELLER`: Can create, update, and delete own cars
  - `DEALER`: Enhanced permissions including car featuring and analytics
  - `ADMIN`: Full system access including user management

### 2. Secure Password Management
- **BCrypt Password Hashing** with salt
- **Secure OTP System** with database storage and expiration
- **Password Reset** functionality with time-limited OTPs

### 3. Data Security
- **Environment-based Configuration** for sensitive data
- **Database Connection Security** with parameterized queries
- **Input Validation** using Jakarta Validation
- **SQL Injection Prevention** through JPA/Hibernate

### 4. API Security
- **Method-level Security** with `@PreAuthorize` annotations
- **Resource Ownership Verification** for car operations
- **Proper HTTP Status Codes** and error handling
- **Request/Response Logging** for audit trails

### 5. Session Security
- **Stateless JWT Authentication** (no server-side sessions)
- **Secure Token Storage** with configurable expiration
- **Automatic Token Validation** on each request

## Quick Start (Development)

### Prerequisites
- Java 21+
- PostgreSQL 12+
- Maven 3.6+

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Sell-the-old-Car
   ```

2. **Configure environment variables**
   ```bash
   cp .env.template .env
   # Edit .env file with your configuration
   ```

3. **Create database**
   ```sql
   CREATE DATABASE carselling;
   CREATE USER carsellinguser WITH PASSWORD 'your_secure_password';
   GRANT ALL PRIVILEGES ON DATABASE carselling TO carsellinguser;
   ```

4. **Generate JWT Secret**
   ```bash
   # Generate a secure 512-bit base64 encoded key
   openssl rand -base64 64
   # Add this to JWT_SECRET in .env file
   ```

5. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

## Production Deployment

### Environment Variables (Required)

```bash
# Database
export DB_URL="jdbc:postgresql://your-db-host:5432/carselling"
export DB_USERNAME="your_db_user"
export DB_PASSWORD="your_secure_db_password"

# JWT Security
export JWT_SECRET="your_512_bit_base64_encoded_secret"
export JWT_EXPIRATION_MS=86400000
export JWT_REFRESH_EXPIRATION_MS=604800000
```

### Security Checklist

#### Database Security
- [ ] Use a dedicated database user with minimal privileges
- [ ] Enable SSL/TLS for database connections
- [ ] Set up database backups with encryption
- [ ] Configure firewall rules to restrict database access

#### Application Security  
- [ ] Set strong JWT secret (minimum 512 bits)
- [ ] Configure HTTPS/TLS certificates
- [ ] Set up rate limiting (recommended: 100 requests/minute per IP)
- [ ] Enable security headers (CSP, HSTS, X-Frame-Options)
- [ ] Configure CORS appropriately for your frontend

#### Infrastructure Security
- [ ] Use a reverse proxy (nginx/Apache) with security headers
- [ ] Set up Web Application Firewall (WAF)
- [ ] Configure monitoring and alerting
- [ ] Set up log aggregation and analysis
- [ ] Regular security updates and patches

#### Monitoring
- [ ] Set up application performance monitoring
- [ ] Configure security event logging
- [ ] Monitor failed authentication attempts
- [ ] Set up alerts for suspicious activities

## API Documentation

### Authentication Endpoints

#### Register User
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePassword123!",
  "role": "SELLER"
}
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "usernameOrEmail": "john_doe",
  "password": "SecurePassword123!"
}
```

#### Forgot Password
```http
POST /api/auth/forgot-password
Content-Type: application/json

{
  "username": "john_doe"
}
```

#### Reset Password
```http
POST /api/auth/reset-password
Content-Type: application/json

{
  "username": "john_doe",
  "otp": "123456",
  "newPassword": "NewSecurePassword123!"
}
```

### Car Management Endpoints (Secure)

All car endpoints require authentication via JWT token in the Authorization header:
```http
Authorization: Bearer your_jwt_token_here
```

#### Get All Cars
```http
GET /api/v2/cars
Authorization: Bearer {token}
```
**Required Permission:** `car:read`

#### Create Car
```http
POST /api/v2/cars
Authorization: Bearer {token}
Content-Type: application/json

{
  "make": "Toyota",
  "model": "Camry",
  "year": 2020,
  "price": 25000,
  "description": "Well maintained car"
}
```
**Required Permission:** `car:create`

#### Update Car
```http
PATCH /api/v2/cars/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "price": 24000,
  "description": "Updated description"
}
```
**Required Permission:** `car:update:own` (for own cars) or `car:update:any` (for any car)

#### Delete Car
```http
DELETE /api/v2/cars/{id}?hard=false
Authorization: Bearer {token}
```
**Required Permission:** `car:delete:own` (for own cars) or `car:delete:any` (for any car)

#### Feature Car
```http
POST /api/v2/cars/{id}/feature?featured=true
Authorization: Bearer {token}
```
**Required Permission:** `car:feature`

## Role-Permission Matrix

| Action | VIEWER | SELLER | DEALER | ADMIN |
|--------|--------|--------|--------|-------|
| View Cars | ✅ | ✅ | ✅ | ✅ |
| Create Cars | ❌ | ✅ | ✅ | ✅ |
| Update Own Cars | ❌ | ✅ | ✅ | ✅ |
| Update Any Cars | ❌ | ❌ | ❌ | ✅ |
| Delete Own Cars | ❌ | ✅ | ✅ | ✅ |
| Delete Any Cars | ❌ | ❌ | ❌ | ✅ |
| Feature Cars | ❌ | ❌ | ✅ | ✅ |
| User Management | ❌ | ❌ | ❌ | ✅ |
| Analytics | ❌ | ❌ | ✅ | ✅ |

## Security Best Practices

### For Developers
1. **Never commit sensitive information** (passwords, API keys, JWT secrets) to version control
2. **Use parameterized queries** to prevent SQL injection
3. **Validate all input data** at the API layer
4. **Log security events** without exposing sensitive data
5. **Use HTTPS** for all API communications
6. **Implement proper error handling** without exposing system details

### For Operations
1. **Regular security updates** for all dependencies
2. **Monitor application logs** for security events
3. **Set up automated backups** with encryption
4. **Use secrets management systems** for production
5. **Implement network segmentation** and firewalls
6. **Regular security audits** and penetration testing

## Troubleshooting

### Common Issues

#### Authentication Failures
- Check JWT secret configuration
- Verify token expiration settings
- Ensure proper Authorization header format: `Bearer <token>`

#### Permission Denied Errors
- Verify user role assignments
- Check permission requirements for endpoints
- Ensure proper method-level security configuration

#### Database Connection Issues
- Verify database credentials
- Check network connectivity
- Ensure database server is running

### Logs and Monitoring

Application logs are available in:
- Development: Console output
- Production: `/logs/application.log`

Key log events to monitor:
- Failed authentication attempts
- Permission denied access
- Unusual API usage patterns
- System errors and exceptions

## Support

For security-related issues or questions, please contact the development team or create an issue in the project repository.

## License

This project follows security-first development practices and includes industry-standard security features for production use.
