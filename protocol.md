# Media Ratings Platform (MRP) - Development Protocol

## Project Overview

The Media Ratings Platform (MRP) is a REST API server built with Java 24 that enables users to manage and rate media content (movies, series, and games). The system provides user authentication, media management, rating functionality, and a favorites system.


---

## Technical Architecture & Design Decisions

### 1. Technology Stack

#### Core Framework
- **Java HttpServer**: Chosen for its lightweight nature and zero external framework dependencies
- **Rationale**: Provides sufficient HTTP handling capabilities without the overhead of Spring Boot or similar frameworks
- **Trade-off**: More manual routing and request handling, but greater control and simplicity

#### Database Layer
- **PostgreSQL 16**: Robust, production-ready relational database
- **JDBC Driver**: postgresql for direct database connectivity
- **UUID v7**: Time-sortable UUIDs for distributed-friendly primary keys
  - **Library**: uuid-creator (com.github.f4b6a3)
  - **Rationale**: Better performance than UUID v4, maintains chronological ordering, avoids auto-increment issues in distributed systems

#### Security
- **BCrypt**: at.favre.lib.bcrypt  for password hashing
  - **Configuration**: Cost factor of 12 (provides strong security while maintaining reasonable performance)
  - **Rationale**: Industry-standard algorithm resistant to rainbow table attacks and brute force

#### Data Serialization
- **Jackson**: jackson-databind for JSON processing
- **Rationale**: Mature, widely-used library with excellent performance and feature set

#### Testing Framework
- **JUnit 5**: junit-jupiter-api and junit-jupiter-engine
- **Current Status**: Dependencies configured, test implementation pending

### 2. Application Architecture

#### Layered Structure
```
org.example/
├── Main.java                 # Application entry point
├── db/
│   └── Database.java         # Database singleton with connection management
├── handlers/
│   ├── AuthHandler.java      # Authentication endpoints
│   ├── MediaHandler.java     # Media CRUD operations
│   ├── RatingHandler.java    # Rating operations
│   └── UserHandler.java      # User profile operations
├── models/
│   ├── User.java            # User entity
│   ├── MediaEntry.java      # Media entity
│   └── Rating.java          # Rating entity
└── utils/
    ├── Router.java          # Central request router
    ├── JsonHelper.java      # JSON utilities
    └── UUIDGenerator.java   # UUID v7 generation
```

#### Design Patterns

**1. Singleton Pattern (Database.java)**
- **Purpose**: Ensure single database connection instance across application
- **Implementation**: Thread-safe getInstance() with lazy initialization
- **Benefit**: Centralized connection management, resource efficiency

**2. Handler Pattern (All *Handler.java files)**
- **Purpose**: Separate concerns by domain (auth, media, ratings)
- **Implementation**: Each handler implements HttpHandler interface
- **Benefit**: Modular, maintainable code organization

**3. Dependency Injection (Router.java)**
- **Purpose**: Router instantiates and manages all handlers
- **Implementation**: Handler instances created in Router constructor
- **Benefit**: Centralized handler lifecycle management

### 3. Database Design

#### Schema Principles
- **UUID Primary Keys**: All tables use UUID v7 for globally unique, time-ordered IDs
- **Foreign Key Constraints**: Enforce referential integrity
- **Cascade Deletes**: Automatic cleanup of related data (ratings, favorites)
- **Timestamps**: Track creation time for all entities
- **Unique Constraints**: Prevent duplicate ratings (user + media combination)

#### Key Tables
1. **users**: User accounts with bcrypt-hashed passwords
2. **auth_tokens**: Token-based session management
3. **media_entries**: Movies, series, and games with metadata
4. **ratings**: User ratings (1-5 stars) with optional comments
5. **rating_likes**: Social feature for liking ratings
6. **favorites**: User's favorite media items

### 4. API Design

#### RESTful Principles
- **Resource-based URLs**: `/api/media/{id}`, `/api/ratings/{id}`
- **HTTP Methods**: GET (read), POST (create), PUT (update), DELETE (remove)
- **Status Codes**: 200 (success), 201 (created), 400 (bad request), 401 (unauthorized), 403 (forbidden), 404 (not found), 500 (server error)

#### Authentication Strategy
- **Token-based Authentication**: Bearer token in Authorization header
- **Token Generation**: UUID v7 tokens (time-sortable, globally unique)
- **Token Storage**: PostgreSQL table with user_id foreign key
- **Session Management**: Single active token per user (new login invalidates previous token)

#### Error Handling
- **Consistent Format**: All errors return JSON with `error` field
- **Validation**: Input validation at handler level before database operations
- **Exception Handling**: Centralized try-catch blocks in handlers

---

## Development Journey

### Phase 1: Foundation 
**Task**: Implement user authentication with registration and login endpoints

**Technical Steps**:
1. Created Database.java with connection pooling
2. Implemented AuthHandler with register/login endpoints
3. Added BCrypt password hashing (cost factor 12)
4. Created User model and JsonHelper utility
5. Set up initial database schema (users, auth_tokens tables)

**Challenges & Solutions**:
- **Challenge**: Initial design used static methods in Database class
- **Solution**: Refactored to singleton pattern for better connection management (Phase 2)


### Phase 2: Refactoring
**Task**: Change static methods to instance methods in AuthHandler and Database classes

**Technical Steps**:
1. Converted Database from static utility class to singleton
2. Changed AuthHandler to use instance methods
3. Updated Database connection management for instance pattern

**Rationale**:
- Instance pattern allows better state management
- Facilitates future testing with dependency injection
- More object-oriented design


### Phase 3: Error Handling
**Task**: Handle JSON parsing errors in registration and login endpoints

**Technical Steps**:
1. Added try-catch for JsonParseException in AuthHandler
2. Return 400 status with clear error message for invalid JSON
3. Improved error response consistency

**Problem Encountered**:
- Invalid JSON was causing 500 errors instead of 400 Bad Request
- Stack traces were not user-friendly

**Solution**:
- Wrapped JSON parsing in try-catch blocks
- Return specific error messages ("Invalid JSON format")
- Maintain error logging while providing clean client responses


### Phase 4: Media & Rating Foundation
**Task**: Add media and rating handling with new endpoints

**Technical Steps**:
1. Created MediaHandler with full CRUD operations
2. Implemented search and filtering (by type, genre, year, age restriction)
3. Added sorting capabilities (by title, year, rating)
4. Implemented favorites system (add/remove)
5. Created RatingHandler skeleton with routing logic
6. Updated Router to delegate media and rating requests

**Features Implemented**:
- GET /api/media with query parameters (search, type, genre, year, age, sort)
- POST /api/media for creating media entries
- GET /api/media/{id} with aggregated ratings
- PUT /api/media/{id} with authorization (creator only)
- DELETE /api/media/{id} with cascade delete
- POST/DELETE /api/media/{id}/favorite

**Authorization Logic**:
- All media endpoints require authentication (Bearer token)
- Update/Delete restricted to media creator
- Validation of media ownership before modifications


### Phase 5: UUID Migration & Error Handling
**Task**: Update database schema and handling for UUIDs, improve error handling for media queries

**Technical Steps**:
1. Migrated from String UUIDs to native UUID objects
2. Updated Database.java with getUUID() helper methods
3. Added UUID validation in handlers (parseUUID method)
4. Improved error messages for invalid UUID format
5. Updated all queries to use UUID objects instead of strings

**Problem Encountered**:
- String-based UUID handling was error-prone
- No validation of UUID format before database queries
- SQL errors were unclear when invalid UUIDs were provided

**Solution**:
- Use PostgreSQL native UUID type
- Added parseUUID() validation method in handlers
- Return 400 with "Invalid UUID format" error message
- Store UUIDs as objects throughout application layer

**Technical Benefit**:
- Type safety at compile time
- Better database performance (native UUID indexing)
- Clearer error messages for clients


---

## Unit Test Coverage

### Current Status
**Framework**: JUnit 5 (Jupiter) with Mockito for mocking
**Total Tests**: 38 unit tests across 6 test classes
**Test Directory**: `src/test/java/org/example/`

### Test Classes Overview

| Test Class | Tests | Coverage Area |
|------------|-------|---------------|
| `AuthHandlerTest.java` | 8 | Password hashing, token extraction, username/password validation |
| `MediaHandlerTest.java` | 8 | Media type validation, title validation, sort parameter validation |
| `RatingHandlerTest.java` | 9 | Star validation (1-5), auto-confirmation, comment updates |
| `UserHandlerTest.java` | 8 | Leaderboard ordering, activity calculation, recommendations |
| `JsonHelperTest.java` | 3 | JSON parsing, query parameter parsing |
| `UUIDGeneratorTest.java` | 2 | UUID v7 generation and validation |

### Testing Strategy

**1. Handler Validation Testing**
- Focus on input validation logic at the handler level
- Test boundary conditions (e.g., star ratings 0, 1, 5, 6)
- Validate error handling for invalid inputs

**2. Parameterized Tests**
- Used for testing multiple input variations efficiently
- Example: RatingHandlerTest tests various star values with `@ParameterizedTest`

**3. Edge Case Coverage**
- Empty inputs, null values, boundary values
- Invalid formats (UUID parsing, JSON parsing)
- Authorization edge cases

**4. Isolation with Mocking**
- Mockito used to isolate handler logic from database
- Enables testing business logic without database dependencies

### Example Test (RatingHandlerTest.java)
```java
@ParameterizedTest
@ValueSource(ints = {1, 2, 3, 4, 5})
@DisplayName("Valid star ratings should be accepted")
void testValidStarRatings(int stars) {
    assertTrue(RatingHandler.isValidStarRating(stars));
}

@ParameterizedTest
@ValueSource(ints = {0, -1, 6, 100})
@DisplayName("Invalid star ratings should be rejected")
void testInvalidStarRatings(int stars) {
    assertFalse(RatingHandler.isValidStarRating(stars));
}
```

### Running Tests
```bash
mvn test
```

---

## SOLID Principles Implementation

This project demonstrates several SOLID principles in its architecture. Below are concrete examples from the codebase:

### 1. Single Responsibility Principle (SRP)

**Definition**: A class should have only one reason to change.

**Implementation in MRP**:

The project separates concerns into distinct layers, each with a single responsibility:

| Layer | Classes | Single Responsibility |
|-------|---------|----------------------|
| Handlers | `AuthHandler`, `MediaHandler`, `RatingHandler`, `UserHandler` | HTTP request/response handling |
| Services | `AuthService`, `MediaService`, `RatingService`, etc. | Business logic and validation |
| Repositories | `UserRepository`, `MediaRepository`, `RatingRepository`, etc. | Data access operations |
| Models | `User`, `MediaEntry`, `Rating` | Data representation |

**Example - RatingService.java**:
```java
public class RatingService {
    private final RatingRepository ratingRepository;
    private final MediaRepository mediaRepository;

    // Only handles rating business logic
    public Rating createRating(UUID mediaId, UUID userId, int stars, String comment) {
        // Validation logic
        if (ratingRepository.existsByMediaAndUser(mediaId, userId)) {
            throw new ConflictException("Rating already exists");
        }
        // Delegates data access to repository
        return ratingRepository.create(mediaId, userId, stars, comment);
    }
}
```

The service handles only business logic, while data access is delegated to the repository.

---

### 2. Liskov Substitution Principle (LSP)

**Definition**: Objects of a superclass should be replaceable with objects of its subclasses without affecting correctness.

**Implementation in MRP**:

All handlers implement the `HttpHandler` interface and can be used interchangeably:

**Router.java**:
```java
public class Router implements HttpHandler {
    private final AuthHandler authHandler;
    private final MediaHandler mediaHandler;
    private final RatingHandler ratingHandler;
    private final UserHandler userHandler;

    @Override
    public void handle(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();

        // Any HttpHandler implementation can be used here
        if (path.startsWith("/api/auth")) {
            authHandler.handle(exchange);  // HttpHandler
        } else if (path.startsWith("/api/media")) {
            mediaHandler.handle(exchange); // HttpHandler
        } else if (path.startsWith("/api/ratings")) {
            ratingHandler.handle(exchange); // HttpHandler
        }
        // All handlers are substitutable - LSP in action
    }
}
```

Each handler can be substituted without breaking the router's behavior.

---

### 3. Open/Closed Principle (OCP)

**Definition**: Software entities should be open for extension but closed for modification.

**Implementation in MRP**:

The exception hierarchy allows adding new exception types without modifying existing code:

**Exception Hierarchy**:
```
RuntimeException
├── ValidationException (400 Bad Request)
├── UnauthorizedException (401 Unauthorized)
├── NotFoundException (404 Not Found)
└── ConflictException (409 Conflict)
```

**Adding a new exception** (extension without modification):
```java
// New exception can be added without changing existing handlers
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
```

The handlers catch exceptions by type and map them to HTTP status codes, allowing new exceptions to be added without modifying the error handling structure.

---

### 4. Interface Segregation Principle (ISP)

**Definition**: Clients should not be forced to depend on interfaces they don't use.

**Implementation in MRP**:

The `Database` class provides focused methods rather than a monolithic interface:

**Database.java**:
```java
public class Database {
    // Focused query methods - clients use only what they need
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);
    public int update(String sql, Object... params);
    public UUID insert(String sql, Object... params);
    public boolean exists(String sql, Object... params);
    public Object getValue(String sql, Object... params);
}
```

Repositories use only the methods they need:
- `UserRepository` uses `query()` and `insert()`
- `RatingRepository` uses `exists()`, `update()`, and `insert()`

---

### Summary

| Principle | Where Applied | Benefit |
|-----------|--------------|---------|
| **SRP** | Handler/Service/Repository layers | Easy to modify one layer without affecting others |
| **LSP** | HttpHandler implementations | Handlers are interchangeable in Router |
| **OCP** | Exception hierarchy | Add new exceptions without modifying handlers |
| **ISP** | Database class methods | Repositories depend only on methods they use |

---

## Problems Encountered & Solutions

### 1. Static vs Instance Methods
**Problem**: Initial implementation used static methods in Database and handlers, making testing difficult and state management unclear.

**Impact**:
- Hard to mock for unit tests
- Unclear ownership of database connections
- Potential concurrency issues

**Solution**:
- Refactored Database to singleton pattern
- Changed handlers to instance methods
- Router instantiates and manages handler lifecycle


---

### 2. JSON Parsing Error Handling
**Problem**: Invalid JSON in request bodies caused 500 Internal Server Error instead of appropriate 400 Bad Request.

**Impact**:
- Poor client experience (unclear error messages)
- Exposed internal stack traces
- Difficulty debugging on client side

**Solution**:
- Added try-catch for JsonParseException in all handlers
- Return 400 status with clear error message
- Log stack traces server-side while sending clean errors to clients

**Implementation**:
```java
try {
    request = JsonHelper.parseRequest(exchange, HashMap.class);
} catch (JsonParseException e) {
    JsonHelper.sendError(exchange, 400, "Invalid JSON format");
    return;
}
```


---

### 3. UUID Type Safety
**Problem**: Using String for UUIDs throughout application led to:
- No compile-time type checking
- Invalid UUIDs reaching database layer
- Unclear SQL errors for clients

**Impact**:
- Runtime errors instead of validation errors
- Poor error messages
- Potential SQL injection risk

**Solution**:
- Use native Java UUID type throughout application
- PostgreSQL native UUID column type
- Added parseUUID() validation method in handlers
- Return 400 with "Invalid UUID format" before database queries

**Benefits**:
- Type safety at compile time
- Better database performance
- Clear, user-friendly error messages



---

### 4. Database Connection Management
**Problem**: No clear strategy for connection pooling and lifecycle.

**Potential Issues**:
- Connection leaks
- Performance bottlenecks
- Stale connections

**Solution**:
- Singleton Database instance with getConnection() method
- Connection validation before use (checks if closed)
- Auto-reconnect on closed connection

**Current Implementation**:
```java
public Connection getConnection() {
    try {
        if (connection == null || connection.isClosed()) {
            connect();
        }
    } catch (SQLException e) {
        connect();
    }
    return connection;
}
```

---

### 5. Authorization Logic
**Problem**: Needed to ensure only media creators can update/delete their entries.

**Security Requirement**: Prevent unauthorized modifications.

**Solution**:
- Validate user ownership before updates/deletes
- Query creator_id from database
- Compare with authenticated user's ID
- Return 403 Forbidden if ownership check fails

**Implementation** (MediaHandler.java:250-262):
```java
Object creatorIdObj = db.getValue("SELECT creator_id FROM media_entries WHERE id = ?", mediaUUID);
if (creatorIdObj == null) {
    JsonHelper.sendError(exchange, 404, "Media not found");
    return;
}
UUID creatorId = (UUID) creatorIdObj;
if (!creatorId.equals(userId)) {
    JsonHelper.sendError(exchange, 403, "Only the creator can edit this media");
    return;
}
```

---

### 6. Rating System Complexity
**Problem**: Rating handler requires multiple endpoints with different authorization rules.

**Complexity**:
- Create rating (authenticated users only)
- Update/delete own ratings
- Confirm comment (media creator only)
- Like/unlike ratings (any authenticated user)

**Solution**:
- Created RatingHandler skeleton with routing logic
- Placeholder implementations for future development
- Clear separation of endpoints by responsibility

**Status**: All rating endpoints fully implemented with business logic.

---

## Lessons Learned

### 1. Pure HTTP vs Framework Trade-offs

**Decision**: Use Java's built-in `HttpServer` instead of Spring Boot

**Pros**:
- Zero external framework dependencies
- Complete control over request handling
- Lightweight deployment (smaller JAR)
- Better understanding of HTTP fundamentals

**Cons**:
- More manual routing code required
- No built-in dependency injection
- Manual JSON serialization/deserialization setup

**Takeaway**: For learning purposes and small APIs, pure HTTP provides valuable low-level understanding. For production applications, frameworks like Spring offer productivity benefits.

---

### 2. Manual JDBC vs ORM

**Decision**: Use raw JDBC with `PreparedStatement` instead of Hibernate/JPA

**Pros**:
- Full control over SQL queries
- Better performance optimization opportunities
- Explicit understanding of database operations
- No "magic" - what you write is what executes

**Cons**:
- More boilerplate code (ResultSet mapping)
- Manual transaction management
- No automatic schema generation

**Takeaway**: Manual JDBC enforces SQL discipline and provides excellent SQL injection protection through parameterized queries. The trade-off is more verbose code, but the explicitness aids debugging.

---

### 3. Security-First Mindset

**Key Security Implementations**:

| Security Measure | Implementation |
|-----------------|----------------|
| Password Hashing | BCrypt with cost factor 12 |
| SQL Injection Prevention | Parameterized queries (PreparedStatement) |
| Authorization | Token-based with ownership validation |
| Input Validation | Handler-level validation before database operations |

**Takeaway**: Security should be built-in from the start, not added later. Using parameterized queries from day one prevents SQL injection without extra effort.

---

### 4. Layered Architecture Benefits

**Structure**:
```
Handler → Service → Repository → Database
```

**Benefits Discovered**:
- Easy to test each layer in isolation
- Changes in one layer don't ripple through others
- Clear separation of HTTP concerns from business logic
- Repositories can be swapped (e.g., for testing with in-memory data)

**Takeaway**: Investing time in proper architecture pays dividends in maintainability and testability.

---

### 5. UUID v7 for Primary Keys

**Decision**: Use UUID v7 instead of auto-increment integers

**Benefits**:
- Time-sortable (newer records have "larger" UUIDs)
- No central coordination needed (good for distributed systems)
- Prevents ID enumeration attacks
- No database round-trip needed to generate IDs

**Takeaway**: UUID v7 combines the benefits of UUIDs (global uniqueness) with time-ordering, making it ideal for modern applications.

---

## Time Tracking Estimates


| Aufgabe                                 | Stunden |
|-----------------------------------------|---------|
| Setup (Projekt-Grundgerüst, DB, Docker) | 18 h    |
| User Authentifizierung                  | 6 h     |
| Media-Entry CRUD                        | 13 h    |
| Ratings + Comments + Likes              | 2 h     |
| Sortieren + Filter                      | 3 h     |
| Favoriten                               | 3 h     |
| Empfehlungen (Recommendations)          | 4 h     |
| Leaderboard                             | 2 h     |
| Unit Tests (38 Tests)                   | 5 h     |
| Postman Tests & Debugging               | 4 h     |
| Dokumentation (README & Protocol)       | 3 h     |
| **Gesamt**                              | **63 h**|


---

## API Endpoints Reference

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login user (returns Bearer token)

### Media Management
- `GET /api/media` - Get media list
  - Query params: `search`, `type`, `genre`, `year`, `age`, `sort`
- `POST /api/media` - Create new media entry (authenticated)
- `GET /api/media/{id}` - Get specific media with ratings
- `PUT /api/media/{id}` - Update media (creator only)
- `DELETE /api/media/{id}` - Delete media (creator only)
- `POST /api/media/{id}/favorite` - Add to favorites (authenticated)
- `DELETE /api/media/{id}/favorite` - Remove from favorites (authenticated)

### Ratings (Skeleton Implementation)
- `POST /api/media/{id}/ratings` - Create rating
- `PUT /api/ratings/{id}` - Update rating
- `DELETE /api/ratings/{id}` - Delete rating
- `PUT /api/ratings/{id}/confirm` - Confirm comment
- `POST /api/ratings/{id}/like` - Like rating
- `DELETE /api/ratings/{id}/unlike` - Unlike rating

---



## Development Environment

**Java Version**: 24
**Build Tool**: Maven 3.x
**Database**: PostgreSQL 16 (Docker recommended)
**IDE**: IntelliJ IDEA (project files included)


---

