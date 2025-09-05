# Testing Guide for Car Selling Platform Backend

This guide provides comprehensive information about testing the enhanced car selling platform backend with chat functionality.

## 🧪 Test Structure

### Test Categories

1. **Unit Tests** - Test individual components in isolation
2. **Integration Tests** - Test component interactions
3. **Repository Tests** - Test database operations
4. **Controller Tests** - Test REST API endpoints
5. **Service Tests** - Test business logic

## 📋 Test Coverage Overview

### ✅ Currently Implemented Tests

#### Unit Tests
- **ChatServiceV2Test** - Comprehensive chat service testing
- **FileUploadServiceTest** - File upload functionality
- **NotificationServiceTest** - Push notification functionality

#### Integration Tests
- **ChatControllerV2IntegrationTest** - Chat API endpoints

### 🛠️ Test Configuration

#### Test Properties
- **H2 In-Memory Database** for fast, isolated tests
- **Mock AWS S3** configuration
- **Mock Firebase** configuration
- **JWT test configuration**

## 🚀 Running Tests

### Prerequisites
```bash
# Ensure you have Maven and Java 11+ installed
mvn --version
java -version
```

### Running All Tests
```bash
# Run all tests
mvn test

# Run tests with coverage report
mvn test jacoco:report

# Run only unit tests
mvn test -Dtest="*Test"

# Run only integration tests
mvn test -Dtest="*IntegrationTest"
```

### Running Specific Test Classes
```bash
# Run ChatServiceV2 tests
mvn test -Dtest=ChatServiceV2Test

# Run FileUploadService tests
mvn test -Dtest=FileUploadServiceTest

# Run Notification tests
mvn test -Dtest=NotificationServiceTest
```

### Running Specific Test Methods
```bash
# Run specific test method
mvn test -Dtest=ChatServiceV2Test#testCreatePrivateChat_Success

# Run multiple specific methods
mvn test -Dtest=ChatServiceV2Test#testCreatePrivateChat*
```

## 📊 Test Results and Coverage

### Expected Test Results
```
Tests run: 50+, Failures: 0, Errors: 0, Skipped: 0
```

### Coverage Targets
- **Service Layer**: 95%+ coverage
- **Controller Layer**: 90%+ coverage
- **Repository Layer**: 85%+ coverage
- **Overall**: 90%+ coverage

## 🔍 Test Scenarios Covered

### Chat System Tests

#### ChatServiceV2Test
- ✅ Create private chat (success/already exists)
- ✅ Send messages (success/user not participant)
- ✅ Edit messages (success/not owner)
- ✅ Delete messages
- ✅ Mark messages as read
- ✅ Get unread message counts
- ✅ Add/remove participants (success/not admin)
- ✅ Search messages
- ✅ Chat room management

#### ChatControllerV2IntegrationTest
- ✅ API endpoint validation
- ✅ Request/response handling
- ✅ Authentication
- ✅ Input validation
- ✅ Error handling

### File Upload Tests

#### FileUploadServiceTest
- ✅ Valid file uploads (images, documents)
- ✅ File validation (size, type, extension)
- ✅ Error handling (empty files, unsupported types)
- ✅ S3 operations (upload, delete, metadata)
- ✅ Presigned URL generation
- ✅ URL key extraction

### Notification Tests

#### NotificationServiceTest
- ✅ Single user notifications
- ✅ Multiple user notifications
- ✅ FCM token management
- ✅ Notification types (chat, car inquiry, price alerts)
- ✅ Error handling (user not found, no FCM token)
- ✅ WebSocket integration

## 🎯 Testing Best Practices

### Test Structure
```java
@Test
void testMethodName_Scenario_ExpectedResult() {
    // Arrange - Set up test data and mocks
    
    // Act - Execute the method being tested
    
    // Assert - Verify the results
}
```

### Mock Usage
- Use `@Mock` for external dependencies
- Use `@InjectMocks` for the class under test
- Verify mock interactions with `verify()`
- Use `when()` for method stubbing

### Test Data
- Create consistent test data in `@BeforeEach`
- Use builders for complex objects
- Keep test data minimal and focused

## 🔧 Test Debugging

### Common Issues and Solutions

#### Test Fails with "User not authenticated"
```java
// Solution: Set up security context in test
@BeforeEach
void setUp() {
    UsernamePasswordAuthenticationToken auth = 
        new UsernamePasswordAuthenticationToken(mockUser, null, null);
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

#### Test Fails with "Bean not found"
```java
// Solution: Add @MockBean for Spring context
@MockBean
private ChatServiceV2 chatServiceV2;
```

#### H2 Database Issues
```properties
# Check test properties configuration
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=create-drop
```

### Debugging Tips
1. **Enable SQL logging** in test properties
2. **Use @Transactional(rollbackFor = Exception.class)** for database tests
3. **Check mock configurations** with `verifyNoMoreInteractions()`
4. **Use @DirtiesContext** for integration tests that modify context

## 📈 Adding New Tests

### For New Service Methods
```java
@Test
void testNewMethod_ValidInput_ReturnsExpectedResult() {
    // Arrange
    when(dependency.method()).thenReturn(expectedValue);
    
    // Act
    Result result = serviceUnderTest.newMethod(input);
    
    // Assert
    assertThat(result).isNotNull();
    assertEquals(expectedValue, result.getValue());
    verify(dependency).method();
}
```

### For New Controller Endpoints
```java
@Test
void testNewEndpoint_ValidRequest_ReturnsOk() throws Exception {
    mockMvc.perform(post("/api/new-endpoint")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
}
```

### For Repository Methods
```java
@Test
@Transactional
void testNewRepositoryMethod() {
    // Arrange
    Entity entity = createTestEntity();
    entityRepository.save(entity);
    
    // Act
    List<Entity> results = entityRepository.newMethod(criteria);
    
    // Assert
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getId()).isEqualTo(entity.getId());
}
```

## 🎯 Test Maintenance

### Regular Tasks
1. **Update tests** when adding new features
2. **Refactor tests** when code changes
3. **Monitor coverage** and add tests for uncovered code
4. **Review failing tests** promptly
5. **Keep test data** up to date

### Performance Considerations
- Use `@MockBean` sparingly in integration tests
- Prefer unit tests over integration tests for speed
- Use test slices (`@WebMvcTest`, `@DataJpaTest`) when appropriate
- Clean up resources in `@AfterEach` if needed

## 📚 Additional Resources

- [Spring Boot Testing Guide](https://spring.io/guides/gs/testing-web/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [TestContainers](https://www.testcontainers.org/) for integration testing

## 🎉 Success Metrics

### Test Quality Indicators
- ✅ All tests pass consistently
- ✅ High code coverage (>90%)
- ✅ Fast test execution (<30 seconds for all tests)
- ✅ No test dependencies or flaky tests
- ✅ Clear test names and assertions

The comprehensive test suite ensures the reliability and quality of the car selling platform's backend services, particularly the enhanced chat system, file upload functionality, and notification services.
