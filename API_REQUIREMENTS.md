# Car Marketplace API Requirements

A comprehensive guide for backend developers to implement the APIs required for the Car Marketplace React Native application.

## 📋 Table of Contents

- [Overview](#overview)
- [Base Configuration](#base-configuration)
- [Authentication & Authorization](#authentication--authorization)
- [Vehicle Management APIs](#vehicle-management-apis)
- [Chat & Messaging APIs](#chat--messaging-apis)
- [WebSocket APIs](#websocket-apis)
- [Dealer Group Management](#dealer-group-management)
- [File Upload & Media](#file-upload--media)
- [Notifications](#notifications)
- [Analytics & Statistics](#analytics--statistics)
- [User Management](#user-management)
- [API Response Standards](#api-response-standards)
- [Error Handling](#error-handling)
- [Security Requirements](#security-requirements)

## 🏗 Overview

The Car Marketplace app requires a comprehensive backend API system to support:
- User authentication and authorization
- Vehicle listing and management
- Real-time chat system
- Dealer group management
- File uploads and media handling
- Notifications
- Analytics and statistics

### Technology Stack Requirements
- **Database**: PostgreSQL/MySQL (recommended)
- **Real-time**: WebSocket support with STOMP protocol
- **File Storage**: AWS S3 or similar cloud storage
- **Authentication**: JWT-based with refresh tokens
- **Framework**: Spring Boot (recommended) or Node.js/Express

---

## 🔧 Base Configuration

### Environment Variables Required
```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=carworld
DB_USERNAME=carworld_user
DB_PASSWORD=secure_password

# JWT Configuration  
JWT_SECRET=your_jwt_secret_key_here
JWT_EXPIRATION=1800000   # 30 minutes
REFRESH_TOKEN_EXPIRATION=604800000  # 7 days

# File Storage
AWS_S3_BUCKET=carworld-images
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key

# WebSocket
WEBSOCKET_ENDPOINT=/ws
WEBSOCKET_ALLOWED_ORIGINS=*

# CORS Configuration
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:19006
```

### Base URL Structure
```
Development: http://localhost:9000
Production: https://api.carworld.com
```

---

## 🔐 Authentication & Authorization

### 1. User Registration
**Endpoint:** `POST /api/auth/register`

**Request Body:**
```json
{
  "username": "john_dealer",
  "email": "john@example.com", 
  "password": "SecurePass123!",
  "role": "DEALER"
}
```

**Response (201 Created):**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "message": "User registered successfully",
  "details": "Account created successfully. Please verify your email.",
  "data": {
    "userId": 1,
    "username": "john_dealer",
    "email": "john@example.com",
    "role": "DEALER",
    "createdAt": "2024-01-15T10:30:00Z"
  },
  "success": true
}
```

### 2. User Login
**Endpoint:** `POST /api/auth/login`

**Request Body:**
```json
{
  "usernameOrEmail": "john_dealer",
  "password": "SecurePass123!",
  "deviceInfo": {
    "platform": "android",
    "version": "13",
    "deviceId": "device_unique_id"
  }
}
```

**Response (200 OK):**
```json
{
  "timestamp": "2024-01-15T10:35:00Z",
  "message": "Login successful",
  "details": "Authentication completed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "tokenType": "Bearer",
    "userId": 1,
    "username": "john_dealer",
    "email": "john@example.com",
    "role": "DEALER",
    "location": "Mumbai, India",
    "expiresAt": "2024-01-15T11:05:00Z",
    "refreshExpiresAt": "2024-01-22T10:35:00Z",
    "expiresIn": 1800,
    "refreshExpiresIn": 604800
  },
  "success": true
}
```

### 3. Token Refresh
**Endpoint:** `POST /api/auth/refresh-token`

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

### 4. Token Validation
**Endpoint:** `POST /api/auth/validate-token`

**Headers:** `Authorization: Bearer {access_token}`

**Response:**
```json
{
  "data": {
    "valid": true,
    "userDetails": {
      "userId": 1,
      "username": "john_dealer",
      "email": "john@example.com", 
      "role": "DEALER",
      "location": "Mumbai, India"
    }
  }
}
```

### 5. Forgot Password
**Endpoint:** `POST /api/auth/forgot-password`

**Request Body:**
```json
{
  "username": "john_dealer"
}
```

### 6. Reset Password  
**Endpoint:** `POST /api/auth/reset-password`

**Request Body:**
```json
{
  "username": "john_dealer",
  "otp": "123456",
  "newPassword": "NewSecurePass123!"
}
```

### 7. Logout
**Endpoint:** `POST /api/auth/logout`

**Headers:** `Authorization: Bearer {access_token}`

---

## 🚗 Vehicle Management APIs

### 1. Get All Vehicles
**Endpoint:** `GET /api/v2/cars`

**Query Parameters:**
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)
- `sort` (optional): Sort field and direction (e.g., "price,desc")

**Response:**
```json
{
  "data": {
    "content": [
      {
        "id": "1",
        "make": "Toyota",
        "model": "Camry",
        "year": 2022,
        "price": 2500000,
        "mileage": 15000,
        "location": "Mumbai",
        "condition": "Excellent",
        "images": [
          "https://s3.amazonaws.com/carworld/images/car1_1.jpg"
        ],
        "specifications": {
          "fuelType": "Petrol",
          "transmission": "Automatic",
          "engineCapacity": "2.5L",
          "seatingCapacity": 5
        },
        "dealerId": "dealer123",
        "dealerName": "Premium Motors",
        "isCoListed": true,
        "coListedIn": ["group1", "group2"],
        "views": 1250,
        "inquiries": 45,
        "shares": 12,
        "status": "Available",
        "featured": true,
        "createdAt": "2024-01-10T08:00:00Z",
        "updatedAt": "2024-01-15T10:30:00Z"
      }
    ],
    "totalElements": 150,
    "totalPages": 8,
    "page": 0,
    "size": 20
  }
}
```

### 2. Get Vehicle by ID
**Endpoint:** `GET /api/cars/{id}`

### 3. Create Vehicle
**Endpoint:** `POST /api/v2/cars`

**Headers:** `Authorization: Bearer {access_token}`

**Request Body:**
```json
{
  "make": "Honda",
  "model": "City", 
  "year": 2023,
  "price": 1200000,
  "mileage": 5000,
  "location": "Delhi",
  "condition": "Like New",
  "images": [
    "https://s3.amazonaws.com/carworld/images/honda_city_1.jpg"
  ],
  "specifications": {
    "fuelType": "Petrol",
    "transmission": "CVT",
    "engineCapacity": "1.5L",
    "seatingCapacity": 5,
    "features": ["ABS", "Airbags", "Power Steering"]
  },
  "description": "Well maintained Honda City with low mileage"
}
```

### 4. Update Vehicle
**Endpoint:** `PATCH /api/v2/cars/{id}`

**Headers:** `Authorization: Bearer {access_token}`

### 5. Delete Vehicle
**Endpoint:** `DELETE /api/v2/cars/{id}`

**Query Parameters:**
- `hard` (optional): Boolean for hard delete (default: false)

### 6. Update Vehicle Status
**Endpoint:** `POST /api/v2/cars/{id}/status`

**Request Body:**
```json
{
  "status": "Sold"
}
```

### 7. Search Vehicles
**Endpoint:** `GET /api/v2/cars/search`

**Query Parameters:**
- `make` (optional): Car make
- `model` (optional): Car model  
- `minYear` (optional): Minimum year
- `maxYear` (optional): Maximum year
- `minPrice` (optional): Minimum price
- `maxPrice` (optional): Maximum price
- `location` (optional): Location filter
- `condition` (optional): Condition filter
- `status` (optional): Status filter
- `featured` (optional): Featured only
- `page` (optional): Page number
- `size` (optional): Page size
- `sort` (optional): Sort criteria

### 8. Get Vehicle Analytics
**Endpoint:** `GET /api/v2/cars/{id}/analytics`

**Response:**
```json
{
  "data": {
    "vehicleId": "1",
    "views": 1250,
    "inquiries": 45,
    "shares": 12,
    "coListings": 3,
    "avgTimeOnMarket": 15,
    "lastActivity": "2024-01-15T10:30:00Z",
    "topLocations": ["Mumbai", "Pune", "Delhi"],
    "dealerInterest": 85
  }
}
```

### 9. Feature/Unfeature Vehicle
**Endpoint:** `POST /api/v2/cars/{id}/feature`

**Query Parameters:**
- `featured`: Boolean (true/false)

### 10. Track Vehicle View
**Endpoint:** `POST /api/v2/cars/{id}/view`

### 11. Track Vehicle Share  
**Endpoint:** `POST /api/v2/cars/{id}/share`

**Request Body:**
```json
{
  "platform": "whatsapp"
}
```

### 12. Get Similar Vehicles
**Endpoint:** `GET /api/v2/cars/{id}/similar`

**Query Parameters:**
- `limit` (optional): Number of similar cars to return

### 13. Get Vehicles by Dealer
**Endpoint:** `GET /api/v1/seller/{dealerId}/cars`

---

## 💬 Chat & Messaging APIs

### 1. Get User's Chats
**Endpoint:** `GET /api/chat/my-chats`

**Query Parameters:**
- `page` (optional): Page number
- `size` (optional): Page size

**Response:**
```json
{
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Chat about Toyota Camry",
        "description": "Inquiry about 2022 Toyota Camry",
        "type": "CAR_INQUIRY",
        "createdBy": {
          "id": 2,
          "username": "buyer_user"
        },
        "isActive": true,
        "carId": 1,
        "createdAt": "2024-01-15T10:00:00Z",
        "updatedAt": "2024-01-15T10:30:00Z",
        "lastActivityAt": "2024-01-15T10:30:00Z",
        "participantCount": 2,
        "unreadCount": 3,
        "lastMessage": {
          "id": 10,
          "content": "Is the car still available?",
          "messageType": "TEXT",
          "sender": {
            "id": 2,
            "username": "buyer_user"
          },
          "createdAt": "2024-01-15T10:30:00Z"
        }
      }
    ],
    "totalElements": 25,
    "totalPages": 2,
    "page": 0,
    "size": 20
  }
}
```

### 2. Get Chat Details
**Endpoint:** `GET /api/chat/{chatId}`

### 3. Create Private Chat
**Endpoint:** `POST /api/chat/private`

**Request Body:**
```json
{
  "otherUserId": 5,
  "name": "Chat with John Dealer"
}
```

### 4. Create Group Chat
**Endpoint:** `POST /api/chat/group`

**Request Body:**
```json
{
  "name": "Mumbai Dealers Group",
  "description": "Group for dealers in Mumbai region",
  "type": "DEALER_ONLY",
  "maxParticipants": 50,
  "participantIds": [2, 3, 4]
}
```

### 5. Get Messages in Chat
**Endpoint:** `GET /api/chat/{chatId}/messages`

**Query Parameters:**
- `page` (optional): Page number
- `size` (optional): Page size (max 50)

**Response:**
```json
{
  "data": {
    "content": [
      {
        "id": 10,
        "chatId": 1,
        "sender": {
          "id": 2,
          "username": "buyer_user"
        },
        "content": "Is the car still available?",
        "messageType": "TEXT",
        "isEdited": false,
        "isDeleted": false,
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:30:00Z",
        "deliveryStatus": "DELIVERED"
      }
    ],
    "totalElements": 45,
    "totalPages": 1,
    "page": 0,
    "size": 50
  }
}
```

### 6. Send Message
**Endpoint:** `POST /api/chat/{chatId}/messages`

**Request Body:**
```json
{
  "content": "Yes, the car is still available. Would you like to schedule a test drive?",
  "messageType": "TEXT",
  "replyToId": 10
}
```

### 7. Edit Message
**Endpoint:** `PUT /api/chat/messages/{messageId}`

### 8. Delete Message
**Endpoint:** `DELETE /api/chat/messages/{messageId}`

### 9. Mark Messages as Read
**Endpoint:** `POST /api/chat/{chatId}/mark-read`

**Request Body:**
```json
{
  "messageIds": [10, 11, 12]
}
```

### 10. Search Messages
**Endpoint:** `GET /api/chat/{chatId}/messages/search`

**Query Parameters:**
- `query`: Search term
- `page` (optional): Page number
- `size` (optional): Page size

### 11. Get Chat Participants
**Endpoint:** `GET /api/chat/{chatId}/participants`

### 12. Add Participants
**Endpoint:** `POST /api/chat/{chatId}/participants`

**Request Body:**
```json
{
  "userIds": [5, 6]
}
```

### 13. Remove Participant
**Endpoint:** `DELETE /api/chat/{chatId}/participants/{userId}`

### 14. Leave Chat
**Endpoint:** `POST /api/chat/{chatId}/leave`

### 15. Get Unread Count
**Endpoint:** `GET /api/chat/unread-count`

**Response:**
```json
{
  "data": {
    "totalUnread": 8,
    "unreadByChat": {
      "1": 3,
      "5": 2,
      "8": 3
    }
  }
}
```

### 16. Create Car Inquiry Chat
**Endpoint:** `POST /api/chat/car-inquiry`

**Request Body:**
```json
{
  "carId": 1,
  "sellerId": 3,
  "message": "Hi, I'm interested in this car. Can you provide more details?"
}
```

### 17. File Upload for Messages
**Endpoint:** `POST /api/chat/upload`

**Content-Type:** `multipart/form-data`

**Response:**
```json
{
  "data": {
    "fileUrl": "https://s3.amazonaws.com/carworld/chat/files/file123.jpg",
    "fileName": "car_interior.jpg", 
    "fileSize": 524288,
    "mimeType": "image/jpeg"
  }
}
```

---

## 🔄 WebSocket APIs

### Connection Endpoint
**URL:** `ws://localhost:9000/ws` or `wss://api.carworld.com/ws`

### Authentication
**Headers:**
```
Authorization: Bearer {access_token}
User-Agent: CarWorld-{Platform}-WebSocket
```

### Subscription Destinations

#### 1. Chat Messages
**Destination:** `/topic/chat/{chatId}`

**Message Format:**
```json
{
  "type": "MESSAGE",
  "data": {
    "id": 15,
    "chatId": 1,
    "sender": {
      "id": 2,
      "username": "buyer_user"
    },
    "content": "Thank you for the quick response!",
    "messageType": "TEXT",
    "createdAt": "2024-01-15T10:45:00Z"
  },
  "timestamp": "2024-01-15T10:45:00Z"
}
```

#### 2. User Messages Queue
**Destination:** `/user/queue/messages`

#### 3. User Status Updates
**Destination:** `/topic/user-status`

**Message Format:**
```json
{
  "type": "USER_STATUS_CHANGE",
  "data": {
    "userId": 2,
    "username": "buyer_user",
    "status": "ONLINE",
    "lastActiveAt": "2024-01-15T10:45:00Z"
  },
  "timestamp": "2024-01-15T10:45:00Z"
}
```

#### 4. Typing Indicators
**Destination:** `/topic/chat/{chatId}/typing`

### Publishing Destinations

#### 1. Send Message
**Destination:** `/app/chat/{chatId}/send`

**Body:**
```json
{
  "content": "Hello, is this car still available?",
  "messageType": "TEXT",
  "replyToId": 10
}
```

#### 2. Edit Message
**Destination:** `/app/message/{messageId}/edit`

#### 3. Delete Message
**Destination:** `/app/message/{messageId}/delete`

#### 4. Mark as Read
**Destination:** `/app/message/{messageId}/read`

#### 5. Send Typing Indicator
**Destination:** `/app/chat/{chatId}/typing`

**Body:**
```json
{
  "isTyping": true
}
```

#### 6. Heartbeat/Ping
**Destination:** `/app/ping`

**Body:**
```json
{
  "timestamp": 1705312800000
}
```

---

## 👥 Dealer Group Management

### 1. Get All Groups
**Endpoint:** `GET /api/v2/groups`

**Response:**
```json
{
  "data": [
    {
      "id": "group1",
      "name": "Mumbai Premium Dealers",
      "description": "High-end car dealers in Mumbai",
      "isPrivate": false,
      "adminId": "dealer123",
      "members": [
        {
          "id": "dealer123", 
          "name": "John Motors",
          "dealership": "Premium Auto",
          "role": "admin",
          "avatar": "https://...",
          "joinedAt": "2024-01-01T00:00:00Z"
        }
      ],
      "createdAt": "2024-01-01T00:00:00Z",
      "vehicleCount": 25
    }
  ]
}
```

### 2. Get Group by ID
**Endpoint:** `GET /api/v2/groups/{groupId}`

### 3. Create Group
**Endpoint:** `POST /api/v2/groups`

**Request Body:**
```json
{
  "name": "Delhi Auto Dealers",
  "description": "Dealers network in Delhi NCR",
  "isPrivate": true
}
```

### 4. Update Group
**Endpoint:** `PATCH /api/v2/groups/{groupId}`

### 5. Delete Group
**Endpoint:** `DELETE /api/v2/groups/{groupId}`

### 6. Invite Member
**Endpoint:** `POST /api/v2/groups/{groupId}/invite`

**Request Body:**
```json
{
  "dealerId": "dealer456"
}
```

### 7. Remove Member
**Endpoint:** `DELETE /api/v2/groups/{groupId}/members/{memberId}`

### 8. Accept Invitation
**Endpoint:** `POST /api/v2/invitations/{invitationId}/accept`

### 9. Reject Invitation
**Endpoint:** `POST /api/v2/invitations/{invitationId}/reject`

### 10. Get My Invitations
**Endpoint:** `GET /api/v2/invitations/my`

### 11. Leave Group
**Endpoint:** `POST /api/v2/groups/{groupId}/leave`

---

## 🗂 File Upload & Media

### 1. Upload Single File
**Endpoint:** `POST /api/v2/upload/file`

**Content-Type:** `multipart/form-data`

**Request:**
```
POST /api/v2/upload/file
Content-Type: multipart/form-data
Authorization: Bearer {access_token}

file: [binary data]
```

**Response:**
```json
{
  "data": {
    "fileId": "file123",
    "originalName": "car_front.jpg",
    "fileName": "uploads/cars/2024/01/15/car_front_1705312800.jpg",
    "fileUrl": "https://s3.amazonaws.com/carworld/uploads/cars/2024/01/15/car_front_1705312800.jpg",
    "mimeType": "image/jpeg",
    "fileSize": 1048576,
    "uploadedAt": "2024-01-15T10:00:00Z"
  }
}
```

### 2. Upload Multiple Files
**Endpoint:** `POST /api/v2/upload/files`

### 3. Delete File
**Endpoint:** `DELETE /api/v2/upload/file/{fileId}`

### 4. Get File Info
**Endpoint:** `GET /api/v2/upload/file/{fileId}/info`

---

## 🔔 Notifications

### 1. Get Notifications
**Endpoint:** `GET /api/v2/notifications`

**Query Parameters:**
- `page` (optional): Page number
- `size` (optional): Page size

**Response:**
```json
{
  "data": {
    "content": [
      {
        "id": "notif123",
        "title": "New Message",
        "message": "You have a new message about Toyota Camry",
        "type": "CHAT_MESSAGE",
        "data": {
          "chatId": 1,
          "messageId": 15,
          "carId": 1
        },
        "isRead": false,
        "createdAt": "2024-01-15T10:30:00Z",
        "userId": 1
      }
    ],
    "totalElements": 50,
    "totalPages": 3
  }
}
```

### 2. Mark Notification as Read
**Endpoint:** `POST /api/v2/notifications/{notificationId}/read`

### 3. Update Notification Settings
**Endpoint:** `POST /api/v2/notifications/settings`

**Request Body:**
```json
{
  "enablePushNotifications": true,
  "enableEmailNotifications": false,
  "enableChatNotifications": true,
  "enableCarUpdateNotifications": true,
  "enableMarketingNotifications": false
}
```

---

## 📊 Analytics & Statistics

### 1. Get Dashboard Statistics
**Endpoint:** `GET /api/v2/analytics/dashboard`

**Response:**
```json
{
  "data": {
    "totalCars": 150,
    "totalViews": 25000,
    "totalInquiries": 500,
    "activeChats": 25,
    "featuredCars": 10,
    "soldCars": 45,
    "monthlyStats": {
      "newListings": 20,
      "completedSales": 8,
      "avgTimeToSale": 18
    }
  }
}
```

### 2. Get Vehicle Performance
**Endpoint:** `GET /api/v2/analytics/vehicles/{vehicleId}`

### 3. Get Market Trends
**Endpoint:** `GET /api/v2/analytics/market-trends`

**Query Parameters:**
- `period` (optional): "week", "month", "quarter", "year"
- `category` (optional): Car category filter

---

## 👤 User Management

### 1. Get User Profile
**Endpoint:** `GET /api/user/profile`

**Response:**
```json
{
  "data": {
    "userId": 1,
    "username": "john_dealer",
    "email": "john@example.com",
    "role": "DEALER",
    "location": "Mumbai, India",
    "profileImage": "https://s3.amazonaws.com/carworld/profiles/user1.jpg",
    "phoneNumber": "+91-9876543210",
    "dealershipName": "Premium Motors",
    "verified": true,
    "joinedAt": "2024-01-01T00:00:00Z",
    "lastActiveAt": "2024-01-15T10:30:00Z"
  }
}
```

### 2. Update Profile
**Endpoint:** `PATCH /api/user/profile`

### 3. Change Password
**Endpoint:** `POST /api/user/change-password`

### 4. Get User Statistics
**Endpoint:** `GET /api/user/statistics`

---

## 📐 API Response Standards

### Success Response Format
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "message": "Operation completed successfully",
  "details": "Additional context about the operation",
  "data": {},
  "success": true
}
```

### Error Response Format
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "message": "Validation failed",
  "details": "One or more fields contain invalid data",
  "path": "/api/v2/cars",
  "errorCode": "VALIDATION_ERROR",
  "fieldErrors": {
    "price": "Price must be greater than 0",
    "year": "Year must be between 1900 and 2024"
  }
}
```

### Pagination Response Format
```json
{
  "data": {
    "content": [],
    "totalElements": 150,
    "totalPages": 8,
    "page": 0,
    "size": 20,
    "first": true,
    "last": false,
    "hasNext": true,
    "hasPrev": false
  }
}
```

---

## ⚠️ Error Handling

### HTTP Status Codes

| Code | Meaning | Usage |
|------|---------|--------|
| 200 | OK | Successful GET, PUT, PATCH requests |
| 201 | Created | Successful POST requests |
| 204 | No Content | Successful DELETE requests |
| 400 | Bad Request | Invalid request data |
| 401 | Unauthorized | Invalid or missing authentication |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Resource already exists |
| 422 | Unprocessable Entity | Validation errors |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server-side errors |

### Error Codes

| Code | Description |
|------|-------------|
| AUTH_001 | Invalid credentials |
| AUTH_002 | Token expired |
| AUTH_003 | Insufficient permissions |
| VALIDATION_001 | Required field missing |
| VALIDATION_002 | Invalid field format |
| RESOURCE_001 | Resource not found |
| RESOURCE_002 | Resource already exists |
| BUSINESS_001 | Business rule violation |
| SYSTEM_001 | Database error |
| SYSTEM_002 | External service error |

---

## 🔒 Security Requirements

### 1. Authentication
- JWT tokens with short expiration (30 minutes)
- Refresh tokens with longer expiration (7 days)  
- Secure token storage and rotation

### 2. Authorization
- Role-based access control (RBAC)
- Resource-level permissions
- API endpoint protection

### 3. Data Protection
- HTTPS enforcement
- SQL injection prevention
- XSS protection
- CSRF protection

### 4. Rate Limiting
- API rate limiting per user/IP
- Progressive backoff for failed attempts
- Burst protection

### 5. Input Validation
- Server-side validation for all inputs
- File type and size validation
- SQL injection prevention
- XSS sanitization

### 6. Logging & Monitoring
- API access logging
- Error logging and monitoring
- Security event logging
- Performance monitoring

---

## 🚀 Implementation Priority

### Phase 1 (Core Features)
1. User Authentication & Authorization APIs
2. Basic Vehicle Management APIs
3. File Upload APIs
4. Basic Chat APIs (without WebSocket)

### Phase 2 (Real-time Features)
1. WebSocket implementation
2. Real-time chat features
3. Notifications APIs
4. Advanced vehicle search

### Phase 3 (Advanced Features)
1. Dealer Group Management
2. Analytics & Statistics
3. Advanced chat features
4. Performance optimizations

### Phase 4 (Enhancement)
1. Advanced search and filtering
2. Recommendation system
3. Mobile app specific APIs
4. Performance monitoring

---

## 🧪 Testing Requirements

### Unit Tests
- API endpoint testing
- Business logic testing
- Authentication/Authorization testing

### Integration Tests
- Database integration
- External service integration
- WebSocket functionality

### Performance Tests
- Load testing for high traffic
- Database query optimization
- API response time optimization

### Security Tests
- Authentication bypass testing
- Authorization testing
- Input validation testing
- SQL injection testing

---

## 📝 Documentation Requirements

### API Documentation
- Complete endpoint documentation
- Request/response examples
- Error handling documentation
- Authentication flow documentation

### Developer Documentation
- Setup and configuration guide
- Database schema documentation
- Business logic documentation
- Deployment guide

---

**Note**: This documentation serves as a comprehensive guide for backend developers. Please ensure proper testing, security measures, and performance optimization when implementing these APIs.

For questions or clarifications, please refer to the React Native app source code in the `/src/services/` directory for detailed implementation examples.
