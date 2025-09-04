# Comprehensive JWT Token Implementation

## Overview

This implementation provides a robust JWT token system with access/refresh token functionality, comprehensive user details in tokens, and proper token management.

## Features

### 🔐 **JWT Token Provider (`JwtTokenProvider`)**
- **Access Token Generation**: Contains user details (ID, email, role, location, creation time)
- **Refresh Token Generation**: Minimal claims for security
- **Token Validation**: Validates both access and refresh tokens
- **Token Type Detection**: Differentiates between access and refresh tokens
- **Comprehensive User Detail Extraction**: Extract username, role, email, location, etc.
- **Expiration Management**: Check token expiration and time remaining

### 🏗️ **Enhanced DTO (`JwtAuthResponse`)**
- **Comprehensive Response**: Includes both access and refresh tokens
- **User Details**: Username, email, role, location, user ID
- **Expiration Information**: Expiration times for both tokens
- **Builder Pattern**: Easy object construction
- **Backward Compatibility**: Maintains existing constructor

### 🔧 **Enhanced Auth Service (`AuthService`)**
- **Token Generation**: Creates tokens with comprehensive user details
- **Token Refresh**: Validates and refreshes tokens
- **Token Validation**: Validates token integrity and expiration
- **User Detail Extraction**: Extracts user information from tokens

### 🌐 **Enhanced Auth Controller (`AuthController`)**
- **Login Endpoint**: Returns comprehensive JWT response
- **Refresh Token Endpoint**: `/api/auth/refresh-token`
- **Token Validation Endpoint**: `/api/auth/validate-token`
- **Enhanced Responses**: Detailed success responses with all token information

## API Endpoints

### 1. **Login** - `POST /api/auth/login`
```json
{
  "usernameOrEmail": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "timestamp": "2024-01-01T12:00:00",
  "message": "Login successful",
  "description": "You have been successfully authenticated. Use the access token for API requests and the refresh token to get new access tokens.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "userId": 1,
    "username": "johndoe",
    "email": "user@example.com",
    "role": "SELLER",
    "location": "New York",
    "expiresAt": "2024-01-02T12:00:00",
    "refreshExpiresAt": "2024-01-08T12:00:00",
    "expiresIn": 86400,
    "refreshExpiresIn": 604800
  },
  "success": true
}
```

### 2. **Refresh Token** - `POST /api/auth/refresh-token`
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response:** Same as login response with new tokens.

### 3. **Validate Token** - `POST /api/auth/validate-token`
**Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:**
```json
{
  "timestamp": "2024-01-01T12:00:00",
  "message": "Token validation completed",
  "description": "Token is valid and active",
  "data": {
    "valid": true,
    "userDetails": {
      "username": "johndoe",
      "userId": 1,
      "email": "user@example.com",
      "role": "SELLER",
      "location": "New York",
      "roles": ["ROLE_SELLER", "car:create", "car:update:own"],
      "expiresAt": "2024-01-02T12:00:00",
      "issuedAt": "2024-01-01T12:00:00"
    }
  },
  "success": true
}
```

## JWT Token Structure

### Access Token Claims
```json
{
  "sub": "username",
  "userId": 1,
  "email": "user@example.com",
  "role": "SELLER",
  "location": "New York",
  "roles": ["ROLE_SELLER", "car:create", "car:update:own"],
  "tokenType": "access",
  "createdAt": "2024-01-01T10:00:00",
  "iat": 1704110400,
  "exp": 1704196800
}
```

### Refresh Token Claims (Minimal for Security)
```json
{
  "sub": "username",
  "userId": 1,
  "tokenType": "refresh",
  "iat": 1704110400,
  "exp": 1704715200
}
```

## Token Expiration Configuration

**Application Properties:**
```properties
# Access token expires in 24 hours (86400000 ms)
app.jwt.expiration-ms=${JWT_EXPIRATION_MS:86400000}

# Refresh token expires in 7 days (604800000 ms)
app.jwt.refresh-token-expiration-ms=${JWT_REFRESH_EXPIRATION_MS:604800000}
```

## Security Features

### 🔒 **Token Types**
- **Access Tokens**: Short-lived, contain comprehensive user details
- **Refresh Tokens**: Long-lived, minimal claims for security

### 🛡️ **Validation**
- **Signature Verification**: All tokens are cryptographically signed
- **Expiration Checking**: Tokens are validated for expiration
- **Type Validation**: Ensures correct token type for each operation
- **User Validation**: Validates user existence during token operations

### 🔐 **Security Best Practices**
- **Minimal Refresh Token Claims**: Only essential information
- **Comprehensive Access Token**: Full user context for API operations
- **Proper Error Handling**: Secure error messages without information leakage
- **Token Type Discrimination**: Different handling for access vs refresh tokens

## Usage Examples

### Client-Side Token Management
```javascript
// Login
const loginResponse = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    usernameOrEmail: 'user@example.com',
    password: 'password123'
  })
});

const { data } = await loginResponse.json();
localStorage.setItem('accessToken', data.accessToken);
localStorage.setItem('refreshToken', data.refreshToken);

// API Request with Access Token
const apiResponse = await fetch('/api/cars', {
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  }
});

// Refresh Token when Access Token Expires
const refreshResponse = await fetch('/api/auth/refresh-token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    refreshToken: localStorage.getItem('refreshToken')
  })
});

const refreshData = await refreshResponse.json();
localStorage.setItem('accessToken', refreshData.data.accessToken);
localStorage.setItem('refreshToken', refreshData.data.refreshToken);
```

### Token Validation
```javascript
// Validate current token
const validateResponse = await fetch('/api/auth/validate-token', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  }
});

const validation = await validateResponse.json();
if (validation.data.valid) {
  console.log('Token is valid:', validation.data.userDetails);
} else {
  // Token is invalid, redirect to login
  window.location.href = '/login';
}
```

## Benefits

### 🚀 **For Developers**
- **Comprehensive User Context**: Access to user details without additional database calls
- **Flexible Token Management**: Separate access and refresh token handling
- **Easy Integration**: RESTful endpoints with consistent response format
- **Rich Error Handling**: Detailed error messages for debugging

### 🏢 **For Applications**
- **Scalable Authentication**: JWT tokens reduce database load
- **Secure Token Refresh**: Automatic token refresh without re-authentication
- **Role-Based Access**: Embedded roles and permissions in tokens
- **User Context Awareness**: Location, role, and user details readily available

### 🔒 **For Security**
- **Short-lived Access Tokens**: Reduced risk window
- **Secure Refresh Mechanism**: Separate refresh token with minimal claims
- **Proper Validation**: Comprehensive token validation
- **Information Segregation**: Different information levels for different token types

## Migration from Previous Implementation

### Update Client Code
1. **Login Response**: Access `data.accessToken` instead of `data.token`
2. **Add Refresh Logic**: Implement refresh token handling
3. **Enhanced User Info**: Access additional user details from login response

### API Integration
- All existing endpoints remain compatible
- New endpoints provide enhanced functionality
- Backward compatibility maintained where possible

## Testing

### Test Scenarios
1. **Login Flow**: Test comprehensive JWT response
2. **Token Refresh**: Test refresh token functionality  
3. **Token Validation**: Test token validation endpoint
4. **Expiration Handling**: Test expired token scenarios
5. **Invalid Token Handling**: Test malformed/invalid tokens
6. **User Details Extraction**: Test comprehensive user detail extraction

This implementation provides a robust, secure, and comprehensive JWT authentication system that scales well with your application needs.
