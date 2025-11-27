# Unified Card Repository

A centralized system for managing debit card metadata, ingesting events via Kafka, and exposing APIs through a public microservice. Built with **Java 17**, **Spring Boot 3**, and **PostgreSQL**.

## 🚀 Architecture

The system consists of two main microservices:

1.  **Card Repo Service** (`card-repo`):
    *   **Internal Service**: Not exposed to the public internet.
    *   **Kafka Consumer**: Ingests card events from the `card-events` topic.
    *   **Database**: Stores card metadata in PostgreSQL.
    *   **Sync**: Synchronizes updates to the Customer360 system (mocked).
    *   **Tech Stack**: Spring Boot, Spring Data JPA, Spring Kafka, PostgreSQL, Redis.

2.  **Cards Microservice** (`cards-service`):
    *   **Public API**: Exposes REST endpoints for frontend applications.
    *   **Gateway**: Acts as a facade for the internal Card Repo.
    *   **Resilience**: Uses **Resilience4j** for circuit breaking and graceful degradation.
    *   **Tech Stack**: Spring Boot Web, Spring Cloud Circuit Breaker, Redis.

## 🛠️ Prerequisites

*   **Java 17** or higher
*   **Maven 3.8+**
*   **Docker** & **Docker Compose**

## 🏃‍♂️ Getting Started

### 1. Start Infrastructure
Start PostgreSQL, Kafka, Zookeeper, and Redis using Docker Compose:

```bash
docker-compose up -d
```

### 2. Build the Project
Build both modules using Maven:

```bash
mvn clean install
```

### 3. Run the Services

**Terminal 1: Card Repo**
```bash
cd card-repo
mvn spring-boot:run
```

**Terminal 2: Cards Service**
```bash
cd cards-service
mvn spring-boot:run
```

## 🧪 Verification & Testing

### Run Automated Tests
Run unit and integration tests:

```bash
mvn test
```

### End-to-End Verification
Use the provided script to produce a Kafka event and verify the API response:

```bash
./verify-flow.sh
```

## 📡 API Documentation

### Public APIs (`cards-service`)

| Method | Endpoint | Description | Headers |
| :--- | :--- | :--- | :--- |
| `GET` | `/cards` | Get cards for a customer | `X-Mobile-Number: <mobile>` |
| `GET` | `/cards/eligible-cards` | Get eligible card programs | `X-Mobile-Number: <mobile>` |

## 🛡️ Best Practices Implemented

*   **Resilience**: Circuit Breakers (Resilience4j) prevent cascading failures.
*   **Observability**: Spring Boot Actuator for health checks and metrics.
*   **Clean Architecture**: DTO pattern to decouple API from persistence.
*   **Testability**: Integration tests and decoupled service layers.
*   **Error Handling**: Global Exception Handling for consistent API responses.
