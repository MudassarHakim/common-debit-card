# Unified Card Repository

A centralized system for managing debit card metadata, ingesting events via Kafka, and exposing APIs. Built with **Java 17**, **Spring Boot 3**, and **MySQL**.

## Architecture
https://gitdiagram.com/MudassarHakim/common-debit-card
![Architecture Diagram](docs/images/architecture_diagram1.png)

The system is a unified **Cards Service** (`cards-service`) that combines all functionality:

*   **Public API**: Exposes REST endpoints for frontend applications.
*   **Internal API**: Provides internal card repository endpoints.
*   **Kafka Consumer**: Ingests card events from the `card-events` topic.
*   **Database**: Stores card metadata in MySQL.
*   **Sync**: Synchronizes updates to the Customer360 system (mocked).
*   **Resilience**: Uses **Resilience4j** for circuit breaking and graceful degradation.
*   **Logging**: Integrated with Logstash for Kibana/Elasticsearch logging.
*   **Tech Stack**: Spring Boot Web, Spring Data JPA, Spring Kafka, Spring Cloud Circuit Breaker, MySQL, Redis, Logstash.

## Prerequisites

*   **Java 17** or higher
*   **Maven 3.8+**
*   **Docker** & **Docker Compose**

## Getting Started

### 1. Start Infrastructure
Start MySQL, Kafka, Zookeeper, and Redis using Docker Compose:

```bash
docker-compose up -d
```

### 2. Build the Project
Build both modules using Maven:

```bash
mvn clean install
```

### 3. Run the Service

```bash
cd cards-service
mvn spring-boot:run
```

## Verification & Testing

### Run Automated Tests
Run unit and integration tests:

```bash
mvn test
```

### 🧪 API Testing
Detailed API testing instructions are available in the [Testing Guide](docs/postman/TESTING_GUIDE.md).

You can import the [Postman Collection](docs/postman/Card_Repository.postman_collection.json) and [Environment](docs/postman/Card_Repository_Local.postman_environment.json) to test the APIs.

### End-to-End Verification
Use the provided script to produce a Kafka event and verify the API response:

```bash
./verify-flow.sh
```

## API Documentation

### Public APIs (`cards-service`)

| Method | Endpoint | Description | Headers |
| :--- | :--- | :--- | :--- |
| `GET` | `/cards` | Get cards for a customer | `X-Mobile-Number: <mobile>` |
| `GET` | `/cards/eligible-cards` | Get eligible card programs | `X-Mobile-Number: <mobile>` |

## Best Practices Implemented

*   **Resilience**: Circuit Breakers (Resilience4j) prevent cascading failures.
*   **Observability**: Spring Boot Actuator for health checks and metrics.
*   **Clean Architecture**: DTO pattern to decouple API from persistence.
*   **Testability**: Integration tests and decoupled service layers.
*   **Error Handling**: Global Exception Handling for consistent API responses.
