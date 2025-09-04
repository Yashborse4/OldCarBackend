# 🚗 Sell-the-old-Car Backend

A comprehensive Spring Boot backend application for a car selling platform with real-time chat functionality, advanced search capabilities, and robust admin management features.

## 🌟 Features

### 🔐 Authentication & Security
- **JWT-based authentication** with access and refresh tokens
- **Role-based access control** (VIEWER, SELLER, DEALER, ADMIN)
- **Account lockout** after failed login attempts
- **OTP verification** for password resets
- **Secure password hashing** with BCrypt

### 🚗 Car Management
- **Full CRUD operations** for car listings
- **Advanced search and filtering** capabilities
- **Featured cars** and view tracking
- **Image upload and management**
- **Car statistics and analytics**
- **Role-based permissions** for operations

### 💬 Real-Time Chat System
- **WebSocket + STOMP** protocol for real-time messaging
- **Chat rooms** linked to specific car listings
- **Message history** with pagination
- **Read receipts** and unread message counts
- **Typing indicators** and presence management
- **Real-time notifications**

### 👥 User Management
- **User profile management** (view, update, delete)
- **Admin dashboard** with system statistics
- **User role management** and bulk operations
- **Activity logging** and audit trails
- **User search and filtering**

### ⚙️ Administrative Features
- **System statistics** and monitoring
- **User management** (ban/unban, role changes)
- **Bulk operations** for administrative tasks
- **Registration analytics** and reporting
- **Activity logs** and user tracking

## 🛠️ Tech Stack

- **Framework**: Spring Boot 3.x
- **Security**: Spring Security with JWT
- **Database**: MySQL with Hibernate/JPA
- **Connection Pool**: HikariCP
- **Real-time**: WebSocket + STOMP
- **Build Tool**: Maven
- **Java Version**: 17+
- **Documentation**: OpenAPI 3 (Swagger)

## 📋 Prerequisites

Before running this application, make sure you have the following installed:

- **Java 17** or higher
- **MySQL 8.0** or higher
- **Maven 3.6** or higher
- **Git**

## 🚀 Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/Yashborse4/OldCarBackend.git
cd OldCarBackend
```

### 2. Environment Setup
Create a `.env` file in the root directory:
```bash
cp .env.example .env
```

Edit the `.env` file with your configuration:
```properties
# Database Configuration
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
DATABASE_URL=jdbc:mysql://localhost:3306/car_selling_dev

# JWT Configuration
JWT_SECRET=your_jwt_secret_key_32_characters_minimum
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000

# Upload Configuration
UPLOAD_PATH=./uploads

# Email Configuration (Optional)
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USERNAME=your_email@gmail.com
EMAIL_PASSWORD=your_app_password
EMAIL_FROM=noreply@yourapp.com
```

### 3. Database Setup
```sql
-- Create database
CREATE DATABASE car_selling_dev;

-- Create user (optional)
CREATE USER 'car_user'@'localhost' IDENTIFIED BY 'car_password';
GRANT ALL PRIVILEGES ON car_selling_dev.* TO 'car_user'@'localhost';
FLUSH PRIVILEGES;
```

### 4. Build and Run
```bash
# Build the application
mvn clean compile

# Run the application
mvn spring-boot:run

# Or run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The application will start on `http://localhost:8080`

## 🔧 Configuration

### Environment Profiles

The application supports multiple environments:

- **development** (`dev`): Local development with detailed logging
- **production** (`prod`): Production-ready configuration
- **testing** (`test`): Test environment with H2 database

### Key Configuration Files

- `application.yml` - Main configuration
- `application-dev.yml` - Development environment
- `application-prod.yml` - Production environment
- `application-test.yml` - Test environment

## 📡 API Documentation

Once the application is running, you can access:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **API Docs**: `http://localhost:8080/v3/api-docs`
- **Health Check**: `http://localhost:8080/actuator/health`

## 🔗 API Endpoints

### Authentication
```
POST /api/auth/register - User registration
POST /api/auth/login - User login
POST /api/auth/refresh - Refresh JWT token
POST /api/auth/forgot-password - Request password reset
POST /api/auth/reset-password - Reset password with OTP
GET  /api/auth/check-username - Check username availability
GET  /api/auth/check-email - Check email availability
```

### User Management
```
GET    /api/users/profile - Get current user profile
PUT    /api/users/profile - Update user profile
DELETE /api/users/profile - Delete user account
GET    /api/users/{id} - Get user by ID
GET    /api/users/search - Search users (Admin)
```

### Car Management
```
GET    /api/cars - Get all cars
POST   /api/cars - Create new car listing
GET    /api/cars/{id} - Get car by ID
PUT    /api/cars/{id} - Update car listing
DELETE /api/cars/{id} - Delete car listing
GET    /api/cars/search - Advanced car search
GET    /api/cars/featured - Get featured cars
```

### Chat System
```
GET    /api/chat/rooms - Get user's chat rooms
POST   /api/chat/rooms - Create new chat room
GET    /api/chat/rooms/{id}/messages - Get chat messages
POST   /api/chat/rooms/{id}/messages - Send message (REST fallback)
PUT    /api/chat/rooms/{id}/read - Mark messages as read
```

### WebSocket Endpoints
```
/ws - WebSocket connection endpoint
/app/chat/{roomId} - Send message via WebSocket
/topic/chat/{roomId} - Subscribe to room messages
/app/chat/{roomId}/typing - Send typing indicator
```

### Admin Operations
```
GET    /api/admin/users - Get all users with filters
PUT    /api/admin/users/{id}/role - Change user role
POST   /api/admin/users/{id}/ban - Ban user
POST   /api/admin/users/{id}/unban - Unban user
DELETE /api/admin/users/{id} - Delete user
GET    /api/admin/statistics - Get system statistics
```

## 🧪 Testing

### Run Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AuthServiceTest

# Run with test profile
mvn test -Dspring.profiles.active=test
```

### Test Coverage
```bash
# Generate test coverage report
mvn clean test jacoco:report
```

## 🐳 Docker Deployment

### Using Docker Compose
```bash
# Start all services
docker-compose up -d

# Stop services
docker-compose down
```

### Build Docker Image
```bash
# Build application
mvn clean package -DskipTests

# Build Docker image
docker build -t sell-the-old-car:latest .

# Run container
docker run -p 8080:8080 --env-file .env sell-the-old-car:latest
```

## 🔒 Security Considerations

### Production Deployment
1. **Environment Variables**: Never commit sensitive data to version control
2. **JWT Secrets**: Use strong, randomly generated secrets (32+ characters)
3. **Database Security**: Use strong passwords and limit database access
4. **HTTPS**: Enable SSL/TLS in production
5. **CORS**: Configure CORS for specific frontend domains
6. **Rate Limiting**: Implement rate limiting for API endpoints

### Security Headers
The application includes security headers:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`

## 🚀 Production Deployment

### Environment Variables (Production)
```bash
# Required environment variables for production
export DATABASE_URL="jdbc:mysql://prod-server:3306/car_selling_prod"
export DB_USERNAME="production_user"
export DB_PASSWORD="strong_production_password"
export JWT_SECRET="your_strong_jwt_secret_key_minimum_32_characters"
export UPLOAD_PATH="/var/uploads/car-selling"
export EMAIL_HOST="your_smtp_server"
export EMAIL_USERNAME="your_email_username"
export EMAIL_PASSWORD="your_email_password"
```

### Build for Production
```bash
# Create production JAR
mvn clean package -Pprod -DskipTests

# Run production build
java -jar -Dspring.profiles.active=prod target/sell-the-old-car-1.0.0.jar
```

## 📊 Monitoring & Health Checks

### Actuator Endpoints
- `/actuator/health` - Application health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics

### Logging
Logs are written to:
- **Development**: Console output
- **Production**: `/var/log/sell-the-old-car/application.log`

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/carselling/oldcar/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers
│   │   ├── dto/            # Data Transfer Objects
│   │   ├── exception/      # Custom exceptions
│   │   ├── model/          # Entity models
│   │   ├── repository/     # Data repositories
│   │   ├── security/       # Security components
│   │   ├── service/        # Business logic
│   │   └── util/           # Utility classes
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── application-test.yml
└── test/
    └── java/               # Test classes
```

## 🐛 Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Check if MySQL is running
   - Verify database credentials in `.env`
   - Ensure database exists

2. **JWT Token Issues**
   - Verify JWT_SECRET is at least 32 characters
   - Check token expiration settings

3. **WebSocket Connection Failed**
   - Verify WebSocket endpoint configuration
   - Check CORS settings for WebSocket

4. **File Upload Issues**
   - Ensure upload directory exists and is writable
   - Check file size limits in configuration

## 📞 Support

For support, email yashborse4@gmail.com or create an issue on GitHub.

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- Spring Security for robust security features
- WebSocket/STOMP for real-time communication capabilities
- All contributors and open-source libraries used in this project

# User Registration & Login API

A secure, optimized authentication API with JWT token-based authentication for the Car Selling application.

## Features

- User registration with email and username validation
- Secure login with JWT token authentication
- Password encryption using BCrypt with proper salt
- Protected endpoints requiring JWT token validation
- Comprehensive exception handling and validation
- Database integration with Spring Data JPA
- Optimized database queries with proper transaction management
- Robust logging throughout the application
- Role-based access control (VIEWER, SELLER, DEALER, ADMIN)
- User profile management (Get, Update, Delete) - *Documented, implementation ongoing*
- Car management functionalities (Upload, Update, Delete, List own) for Sellers/Dealers - *Initial implementation with placeholder logic*
- Admin functionalities: View all users, change user roles, ban/remove users - *Initial implementation with placeholder logic*
- Comprehensive API documentation (`ApiDocument.readme`)
- Car Viewing (Public): List all cars with filters, view specific car details - *Initial implementation with placeholder logic*
- GraphQL API for flexible data querying and mutations (see `/graphql` endpoint and `schema.graphqls`)

## Architecture Overview

The application follows a layered architecture with:

- **Model Layer**: User entity with validation annotations
- **Repository Layer**: JPA repositories for efficient data access
- **Service Layer**: Business logic with transaction management
- **Controller Layer**: RESTful API endpoints with validation
- **Security Layer**: JWT authentication and authorization
- **Exception Handling**: Global exception handler with proper HTTP status codes

## Code Structure

```
src/main/java/com/CarSelling/Sell/the/old/Car/
├── controller/
│   ├── AuthController.java - Authentication endpoints
│   └── UserController.java - Protected user endpoints
├── dto/
│   ├── JwtAuthResponse.java - JWT response structure
│   ├── LoginRequest.java - Login payload with validation
│   └── RegisterRequest.java - Registration payload with validation
├── exception/
│   ├── GlobalExceptionHandler.java - Central exception handling
│   ├── ResourceAlreadyExistsException.java - Conflict exception
│   └── ResourceNotFoundException.java - Not found exception
├── model/
│   └── User.java - User entity with validation
├── repository/
│   └── UserRepository.java - Data access interface
├── security/
│   ├── CustomUserDetailsService.java - User details implementation
│   ├── SecurityConfig.java - Security configuration
│   └── jwt/
│       ├── JwtAuthenticationEntryPoint.java - Unauthorized handler
│       ├── JwtAuthenticationFilter.java - Token validation filter
│       └── JwtTokenProvider.java - Token generation and validation
└── service/
    ├── AuthService.java - Authentication business logic
    └── UserService.java - User management business logic

*Note: Some files (`User.java`, `Role.java`, `AdminController.java`, `ChangeRoleRequest.java`, `UserPrincipal.java`, `Car.java`, `CarController.java`, `CarResponseDTO.java`, `CarRequestDTO.java`) are currently in the base package `com.CarSelling.Sell.the.old.Car` or `com.CarSelling.Sell.the.old.Car.security` due to initial setup constraints and may be refactored into a more structured package layout later.*
```

## API Endpoints

### Authentication

- **POST /api/auth/register**: Register a new user
  - Request Body: `{ "username": "user1", "email": "user1@example.com", "password": "password123" }`
  - Response: `{ "success": true, "message": "User registered successfully", "userId": 1, "username": "user1" }`
  - Status Codes:
    - 201 Created: User registered successfully
    - 400 Bad Request: Validation errors
    - 409 Conflict: Username or email already exists

- **POST /api/auth/login**: Authenticate and receive JWT token
  - Request Body: `{ "usernameOrEmail": "user1", "password": "password123" }`
  - Response: `{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "userId": 1, "username": "user1", "email": "user1@example.com" }`
  - Status Codes:
    - 200 OK: Authentication successful
    - 400 Bad Request: Validation errors
    - 401 Unauthorized: Invalid credentials

### Protected Endpoints

- **GET /api/user/profile**: Get current user profile (requires JWT token)
  - Headers: `Authorization: Bearer {jwt_token}`
  - Response: `{ "id": 1, "username": "user1", "email": "user1@example.com", "createdAt": "2025-05-30T12:34:56" }`
  - Status Codes:
    - 200 OK: Profile retrieved successfully
    - 401 Unauthorized: Missing or invalid token
    - 403 Forbidden: Insufficient permissions
    - 404 Not Found: User not found

### Admin Endpoints (Requires ADMIN role)

Details for these endpoints can be found in `ApiDocument.readme`. Summary:

- **GET /api/admin/users**: View all users (paginated).
- **PUT /api/admin/users/{id}/role**: Change a specific user's role.
- **DELETE /api/admin/users/{id}**: Ban/remove a specific user.

### Public Car Viewing Endpoints

Details for these endpoints can be found in `ApiDocument.readme`. Summary:

- **GET /api/cars**: View all cars (paginated, filterable by make, model, year range, price range, owner role).
- **GET /api/cars/{id}**: View details for a specific car.

### Car Management Endpoints (Seller/Dealer - Requires SELLER/DEALER role)

Details for these endpoints can be found in `ApiDocument.readme`. Summary:

- **POST /api/cars**: Upload a new car.
- **PUT /api/cars/{id}**: Update an existing car owned by the user.
- **DELETE /api/cars/{id}**: Delete a car owned by the user.
- **GET /api/cars/mycars**: List all cars owned by the authenticated user.

## Setup & Configuration

### Database Configuration

The application is configured to use PostgreSQL. Update the database connection details in `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/carselling
spring.datasource.username=postgres
spring.datasource.password=password
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

### JWT Configuration

For production use, make sure to update the JWT secret key in `application.properties`:

```properties
app.jwt.secret=your_jwt_secret_key_which_should_be_at_least_256_bits_long_for_security
app.jwt.expiration-ms=86400000
```

## Usage

### Authentication Flow

1. Register a new user using the `/api/auth/register` endpoint
2. Login with the registered credentials using the `/api/auth/login` endpoint
3. Store the JWT token returned from the login response
4. Include the JWT token in the Authorization header for protected API requests:
   ```
   Authorization: Bearer {jwt_token}
   ```

### Testing with Postman

1. **Register a User**:
   - Method: POST
   - URL: http://localhost:8080/api/auth/register
   - Body (raw JSON):
     ```json
     {
       "username": "testuser",
       "email": "test@example.com",
       "password": "password123"
     }
     ```

2. **Login**:
   - Method: POST
   - URL: http://localhost:8080/api/auth/login
   - Body (raw JSON):
     ```json
     {
       "usernameOrEmail": "testuser",
       "password": "password123"
     }
     ```
   - Save the returned JWT token

3. **Access Protected Endpoint**:
   - Method: GET
   - URL: http://localhost:8080/api/user/profile
   - Headers: Authorization: Bearer {your_jwt_token}

## Security Features

- Password hashing with BCrypt using proper salt generation
- JWT token with configurable expiration time
- Token signature validation to prevent tampering
- Protection against common security vulnerabilities
- Input validation on all endpoints
- Comprehensive exception handling with appropriate HTTP status codes
- Secure password storage without exposing sensitive data
- Stateless authentication to support scaling

## Changelog

### 2025-06-05
- Created `AuthPayload.java` DTO in the `dto` package to handle GraphQL authentication responses, resolving an import error in `QueryResolver.java`.
- Added GraphQL Java Kickstart dependencies (`com.graphql-java-kickstart:graphql-spring-boot-starter` and `com.graphql-java-kickstart:graphql-java-tools`) to `build.gradle` to resolve `graphql.kickstart` type resolution errors.
- Added GraphQL API schema (`src/main/resources/schema.graphqls`) defining queries, mutations, and types for core functionalities.
- Updated API documentation (`ApiDocument.readme`) to include a comprehensive section for the new GraphQL API.
- Added GraphQL API as a feature in `README.md`.
- Created initial GraphQL `QueryResolver.java` and `MutationResolver.java` (Note: manual relocation to `graphql/resolver` package needed).

## Optimizations

- Efficient database queries with proper indexing
- Transaction management to ensure data consistency
- Exception handling with detailed error messages
- Proper logging at different levels (DEBUG, INFO, ERROR)
- Try-catch blocks to handle unexpected errors gracefully
- Validation to prevent invalid data
- Token caching for improved performance

## Progress & Changelog

### 2025-06-05: Car Management for Sellers/Dealers
- Created `CarRequestDTO.java` (in base package) for car creation/update payloads.
- Extended `CarController.java` (in base package) with placeholder service logic for authenticated Sellers/Dealers:
  - `POST /api/cars`: Create a new car.
  - `PUT /api/cars/{id}`: Update an owned car.
  - `DELETE /api/cars/{id}`: Delete an owned car.
  - `GET /api/cars/mycars`: List cars owned by the authenticated user.
- Secured these new endpoints with `@PreAuthorize("hasAnyRole('SELLER', 'DEALER')")`.
- Updated `ApiDocument.readme` with detailed documentation for these Seller/Dealer car management endpoints.
- Updated this `README.md` to reflect these changes.

### 2025-06-05: Car Viewing Implementation (Public)
- Created `Car.java` entity (in base package) with attributes like make, model, year, price, owner, etc.
- Created `CarResponseDTO.java` (in base package) for formatting car API responses, including nested owner details.
- Implemented `CarController.java` (in base package) with placeholder service logic for public car viewing:
  - `GET /api/cars`: List all cars with pagination and filtering (make, model, year, price, ownerRole).
  - `GET /api/cars/{id}`: Get specific car details.
- Updated `ApiDocument.readme` with a "Car Viewing Endpoints (Publicly Accessible)" section.
- Updated this `README.md` to reflect these changes.

### 2025-06-05: Admin Module & Security Enhancements
- Added `ADMIN` role to `Role.java`.
- Implemented `UserPrincipal.java` in the `security` package for richer user details in Spring Security.
- Enhanced `JwtTokenProvider.java` to include roles in JWT claims and provide a method to extract them.
- Updated `CustomUserDetailsService.java` to utilize `UserPrincipal` and fetch users by email.
- Created `AdminController.java` (in base package) with placeholder service logic for:
  - `GET /api/admin/users`: View all users (paginated).
  - `PUT /api/admin/users/{id}/role`: Change user role.
  - `DELETE /api/admin/users/{id}`: Delete/ban user.
- Created `ChangeRoleRequest.java` DTO (in base package).
- All admin controller endpoints are secured with `@PreAuthorize("hasRole('ADMIN')")`.
- Updated `ApiDocument.readme` to include a detailed "Admin Management Endpoints" section.
- Updated this `README.md` with new features, API endpoint summaries, code structure notes, and this changelog entry.

### 2025-05-30: Initial Implementation

- Created User entity with validation annotations
- Implemented UserRepository with efficient query methods
- Added JWT token provider with secure token generation and validation
- Created authentication filter for request interception
- Implemented custom UserDetailsService for security integration
- Set up Spring Security with proper configuration
- Created DTOs with validation for request/response handling
- Implemented AuthService and UserService with transaction management
- Added controllers for registration, login, and profile endpoints
- Implemented global exception handling with @ControllerAdvice
- Set up database configuration with PostgreSQL
- Added comprehensive logging throughout the application
- Created README and PROGRESS documentation
