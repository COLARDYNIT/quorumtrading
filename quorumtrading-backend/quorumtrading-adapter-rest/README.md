# QuorumTrading REST Adapter

REST API adapter layer implementing JAX-RS resources with RFC 9457 Problem Details error handling.

## Purpose

This module provides the HTTP REST interface to the application layer, exposing simulation and strategy management endpoints. It translates HTTP requests into application layer commands/queries and maps domain objects back to HTTP responses.

## Architecture

Following hexagonal architecture principles:
- **Driving Adapter** - Receives HTTP requests and drives the application
- **Port & Adapter Pattern** - Implements REST interface to application use cases
- **RFC 9457 Compliance** - Standardized error responses using Problem Details

## Package Structure

```
com.quorumtrading.adapter.rest/
├── dto/                    # Request/Response DTOs
│   ├── request/            # e.g. CreateSimulationRequest, CreateStrategyRequest
│   └── response/           # e.g. SimulationResponse, StrategyResponse
├── exception/              # RFC 9457 Problem Details
│   ├── ProblemDetails.java
│   └── ProblemDetailsExceptionMapper.java
├── filter/                 # HTTP filters
├── mapper/                 # Domain <-> DTO mappers
│   ├── SimulationMapper.java
│   ├── StrategyMapper.java
│   └── ...
├── security/               # Auth helpers (dev_session cookie, current user)
└── resource/               # JAX-RS REST endpoints (30+ resources)
    ├── SimulationResource.java
    ├── StrategyResource.java
    └── ...
```

## API Endpoints

Core endpoints (see Swagger UI at `/q/swagger-ui` for the full API):

### Simulations

- `POST /api/v1/simulations` - Create new simulation
- `GET /api/v1/simulations` - List all simulations (with optional status filter)
- `GET /api/v1/simulations/{id}` - Get simulation by ID
- `DELETE /api/v1/simulations/{id}` - Delete simulation
- `POST /api/v1/simulations/{id}/start` - Start simulation execution

### Strategies

- `POST /api/v1/strategies` - Create new strategy
- `GET /api/v1/strategies` - List all strategies
- `GET /api/v1/strategies/{id}` - Get strategy by ID
- `PUT /api/v1/strategies/{id}` - Update strategy
- `DELETE /api/v1/strategies/{id}` - Delete strategy

## Error Handling

All errors are returned as RFC 9457 Problem Details:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "timestamp": "2025-10-27T10:30:00Z"
}
```

## Dependencies

- `quorumtrading-application` - Application layer (ports & use cases)
- `quorumtrading-model` - Domain model (value objects)
- `quarkus-rest` - JAX-RS implementation
- `quarkus-rest-jackson` - JSON serialization
- `quarkus-hibernate-validator` - Bean validation

## Current Status

All resources are fully implemented with:
- Complete API structure and OpenAPI documentation
- Request/Response DTOs with validation
- RFC 9457 error handling infrastructure
- Application layer use cases wired up via CDI
- Domain-to-DTO mapping with dedicated mappers
- Integration tests for core endpoints

## Testing

```bash
# Run unit tests
./mvnw test -pl quorumtrading-adapter-rest -am

# Compile module
./mvnw clean compile -pl quorumtrading-adapter-rest -am
```

**MockProducer pattern:** this module's tests run in isolation from other adapters. CDI beans that are normally wired in the bootstrap module (application services, ports) are provided by `src/test/java/com/quorumtrading/adapter/rest/MockProducer.java`, which produces Mockito mocks. When a resource gains a new port/service dependency, add a corresponding producer method to `MockProducer`.

**Auth in tests:** endpoints annotated `@Authenticated` are tested with `@TestSecurity(user = "email")` from `quarkus-test-security`; other resources handle auth manually via the `dev_session` cookie.
