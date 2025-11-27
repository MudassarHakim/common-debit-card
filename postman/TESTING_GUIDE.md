# Postman Testing Guide

This guide provides step-by-step instructions for testing the Unified Card Repository system using Postman.

## 📦 Setup

### 1. Import Collection and Environment

1.  Open **Postman**
2.  Click **Import** in the top left
3.  Import both files from `postman/` folder:
    *   `Card_Repository.postman_collection.json`
    *   `Card_Repository_Local.postman_environment.json`
4.  Select the **Card Repository - Local** environment from the dropdown in the top right

### 2. Start the Services

Ensure all services are running:

```bash
# Start infrastructure
docker-compose up -d

# Terminal 1: Start Card Repo
cd card-repo
mvn spring-boot:run

# Terminal 2: Start Cards Service
cd cards-service
mvn spring-boot:run
```

## 🧪 Step-by-Step Testing Approach

### Test Flow 1: End-to-End Card Ingestion

**Objective**: Verify that a card event is ingested via Kafka and can be retrieved via the public API.

#### Step 1: Produce a Card Event to Kafka

Since we don't have a Kafka producer endpoint in the base implementation, use the CLI:

```bash
docker exec -i common-debit-card-kafka-1 kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic card-events <<EOF
{"tokenRef":"tok_12345","maskedCardNumber":"4111xxxxxxxx1111","last4":"1111","programCode":"PRG_001","programCategory":"STANDARD","network":"VISA","bin":"411111","lifecycleStatus":"ACTIVE","rawStatus":"OPEN","customerMobileNumber":"9876543210","custId":"CUST_001","accountNo":"ACC_001","issuedBySystem":"CMS_A","issuanceChannel":"DIGITAL","eventTimestamp":"2023-10-27T10:00:00"}
EOF
```

**Expected Result**: Event is produced to Kafka topic `card-events`.

#### Step 2: Wait for Processing

Wait 3-5 seconds for the Card Repo service to consume and process the event.

#### Step 3: Verify via Internal API

In Postman, execute:
*   **Folder**: Card Repo (Internal APIs)
*   **Request**: Get Cards by Mobile (Internal)

**Expected Response**:
```json
[
  {
    "tokenRef": "tok_12345",
    "maskedCardNumber": "4111xxxxxxxx1111",
    "last4": "1111",
    "programCode": "PRG_001",
    "lifecycleStatus": "ACTIVE",
    "eventTimestamp": "2023-10-27T10:00:00"
  }
]
```

**Status Code**: `200 OK`

#### Step 4: Verify via Public API

In Postman, execute:
*   **Folder**: Cards Service (Public APIs)
*   **Request**: Get Cards for Customer

**Expected Response**: Same as Step 3.

**Status Code**: `200 OK`

---

### Test Flow 2: Eligibility Check

**Objective**: Verify that the eligibility endpoint returns eligible card programs.

#### Step 1: Get Eligible Card Programs

In Postman, execute:
*   **Folder**: Cards Service (Public APIs)
*   **Request**: Get Eligible Card Programs

**Expected Response**:
```json
[
  "Standard Debit Card",
  "Platinum Debit Card"
]
```

**Status Code**: `200 OK`

---

### Test Flow 3: Circuit Breaker Resilience

**Objective**: Verify that the Cards Service handles Card Repo failures gracefully.

#### Step 1: Stop the Card Repo Service

Stop the Card Repo service (Ctrl+C in Terminal 1).

#### Step 2: Call Public API

In Postman, execute:
*   **Folder**: Cards Service (Public APIs)
*   **Request**: Get Cards for Customer

**Expected Response**:
```json
[]
```

**Status Code**: `200 OK`

**Note**: The circuit breaker fallback returns an empty list instead of failing.

#### Step 3: Restart Card Repo

Restart the Card Repo service:

```bash
cd card-repo
mvn spring-boot:run
```

#### Step 4: Verify Normal Operation

Repeat Step 2. You should now get the actual card data.

---

## 🔍 Health Check

Check if services are healthy:

*   **Card Repo Health**: `http://localhost:8081/actuator/health`
*   **Cards Service Health**: `http://localhost:8080/actuator/health` (if Actuator is added)

---

## 📝 Testing Checklist

- [ ] Import Postman collection and environment
- [ ] Start all services (Docker + Spring Boot)
- [ ] Produce a test card event to Kafka
- [ ] Verify card retrieval via Internal API
- [ ] Verify card retrieval via Public API
- [ ] Test eligibility endpoint
- [ ] Test circuit breaker by stopping Card Repo
- [ ] Verify health endpoints

---

## 🐛 Troubleshooting

| Issue | Solution |
| :--- | :--- |
| `Connection refused` on `8080` or `8081` | Ensure both services are running |
| Empty response from GET /cards | Ensure Kafka event was produced and consumed |
| Kafka producer error | Verify Docker container name: `docker ps` |
| MySQL connection error | Ensure `docker-compose up -d` was executed |

---

## 📊 Example Kafka Events

### Event 1: Standard Debit Card
```json
{
  "tokenRef": "tok_67890",
  "maskedCardNumber": "5500xxxxxxxx4444",
  "last4": "4444",
  "programCode": "PRG_002",
  "programCategory": "PREMIUM",
  "network": "MASTERCARD",
  "bin": "550000",
  "lifecycleStatus": "ACTIVE",
  "rawStatus": "OPEN",
  "customerMobileNumber": "9876543210",
  "custId": "CUST_002",
  "accountNo": "ACC_002",
  "issuedBySystem": "CMS_B",
  "issuanceChannel": "BRANCH",
  "eventTimestamp": "2023-10-28T10:00:00"
}
```

### Event 2: Corporate Debit Card
```json
{
  "tokenRef": "tok_11111",
  "maskedCardNumber": "6011xxxxxxxx2222",
  "last4": "2222",
  "programCode": "PRG_CORP_001",
  "programCategory": "CORPORATE",
  "network": "RUPAY",
  "bin": "601100",
  "lifecycleStatus": "ACTIVE",
  "rawStatus": "ACTIVE",
  "customerMobileNumber": "9876543210",
  "custId": "CUST_003",
  "accountNo": "ACC_003",
  "issuedBySystem": "CMS_C",
  "issuanceChannel": "DIGITAL",
  "eventTimestamp": "2023-10-29T10:00:00"
}
```
