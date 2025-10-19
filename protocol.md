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
**Dependencies**: JUnit 5 configured in pom.xml
**Implementation**: No test files currently exist
**Test Directory**: src/test/java (standard Maven structure)

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

**Status**: Routing implemented, business logic pending.

---

## Time Tracking Estimates


| Aufgabe                                 | Stunden |
|-----------------------------------------|---------|
| Setup (Projekt-Grundgerüst, DB, Docker) | 18 h    |
| User Authentifizierung                  | 6 h     |
| Media-Entry CRUD                        | 13 h    |
| Ratings + Comments + Likes              | 2   h   |
| Sortieren + Filter                      | 3  h    |
| Favoriten                               | 3  h    |
| Empfehlungen                            |         |
| Leaderboard                             |         |
| Postman Tests & Debugging               |         |
| Dokumentation (README & Protocol)       |         |
| *Gesamt*                              | 45 h    |


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

