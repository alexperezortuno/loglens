# LogLens

LogLens is a Java-based application built with Spring Boot that provides log analysis and visualization capabilities. The project follows modern Java development practices with a layered architecture and comprehensive testing.

## Features

- Log analysis and visualization
- RESTful API for log management
- Modular architecture with clear separation of concerns
- Comprehensive test coverage with JUnit and Mockito
- Spring Boot integration with dependency injection
- Support for database migrations (Flyway/Liquibase)

## Tech Stack

- **Language**: Java 11+
- **Framework**: Spring Boot
- **Build System**: Gradle
- **Testing**: JUnit 5, Mockito
- **Database**: H2 (for testing), PostgreSQL (for production)
- **ORM**: JPA/Hibernate
- **Code Quality**: Spotless for code formatting
- **Documentation**: Javadoc, OpenAPI/Swagger

## Requirements

- Java 11 or higher
- Gradle 6.0 or higher
- Git

## Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd loglens
   ```

2. Build the project:
   ```bash
   ./gradlew build
   ```

3. Run the application:
   ```bash
   ./gradlew bootRun
   ```

## Configuration

The application uses Spring Boot's configuration management. Configuration properties can be set in:
- `application.properties` or `application.yml` files
- Environment variables
- Command line arguments

## Usage

After starting the application, you can access:
- REST API endpoints at `http://localhost:8080`
- Swagger UI at `http://localhost:8080/swagger-ui.html` (if enabled)

## Development

### Building the Project

```bash
# Clean and build
./gradlew clean build

# Run tests
./gradlew test

# Run specific test class
./gradlew test --tests "ClassNameTest"

# Run specific test method
./gradlew test --tests "ClassNameTest.testMethodName"

# Generate coverage report
./gradlew test jacocoTestReport
```

### Code Quality

```bash
# Run all checks
./gradlew check

# Run linting
./gradlew spotlessCheck
```

## Testing

The project uses JUnit 5 for unit testing and Mockito for mocking. Tests are organized in the standard Maven/Gradle structure under `src/test/java`.

## Project Structure

```
loglens/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/loglens/
│   └── test/
│       └── java/
└── .idea/ (IDE configuration)
```

## Architecture

LogLens follows a layered architecture pattern:
- **Controller Layer**: REST endpoints and request handling
- **Service Layer**: Business logic and application services
- **Repository Layer**: Data access and persistence
- **Model Layer**: Data models and entities

The architecture emphasizes:
- Separation of concerns
- Dependency injection via Spring
- Use of interfaces for loose coupling
- RESTful API design

## Environment Variables

The application can be configured using environment variables. Common configuration options include:
- `SERVER_PORT`: Port number for the application
- `SPRING_DATASOURCE_URL`: Database connection URL
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password

## API Documentation

API documentation is available through Swagger/OpenAPI. The documentation can be accessed at:
- `http://localhost:8080/swagger-ui.html` (if enabled)

## Troubleshooting

### Common Issues

1. **Build Failures**: Ensure you have Java 11+ and Gradle installed
2. **Port Conflicts**: Change `server.port` in application properties if port 8080 is in use
3. **Database Connection**: Verify database configuration in application properties

### Running Tests

To run tests with coverage:
```bash
./gradlew test jacocoTestReport
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.