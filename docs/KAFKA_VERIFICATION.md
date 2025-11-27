# Kafka to Database Verification Guide

This guide explains how to verify that Kafka events are consumed, processed, and stored in the MySQL database.

## 🔍 Verification Methods

### Method 1: Check Application Logs (Recommended)

The Card Repo service logs every step of the ingestion process.

**Steps:**

1.  **Start the Card Repo service** and watch the logs:
    ```bash
    cd card-repo
    mvn spring-boot:run
    ```

2.  **Produce a test event** to Kafka:
    ```bash
    docker exec -i common-debit-card-kafka-1 kafka-console-producer \
      --bootstrap-server localhost:9092 \
      --topic card-events <<EOF
    {"tokenRef":"tok_TEST_001","maskedCardNumber":"4111xxxxxxxx1111","last4":"1111","programCode":"PRG_001","programCategory":"STANDARD","network":"VISA","bin":"411111","lifecycleStatus":"ACTIVE","rawStatus":"OPEN","customerMobileNumber":"9876543210","custId":"CUST_001","accountNo":"ACC_001","issuedBySystem":"CMS_A","issuanceChannel":"DIGITAL","eventTimestamp":"2023-10-27T10:00:00"}
    EOF
    ```

3.  **Watch for these log entries** in the Card Repo terminal:
    ```
    INFO  CardEventConsumer : Received message: {"tokenRef":"tok_TEST_001"...}
    INFO  CardEventConsumer : Saved card: tok_TEST_001
    INFO  C360SyncService   : Syncing card tok_TEST_001 to Customer360...
    INFO  C360SyncService   : Successfully synced card tok_TEST_001 to Customer360.
    ```

**What to look for:**
- ✅ "Received message" - Kafka consumer received the event
- ✅ "Saved card" - Event was persisted to MySQL
- ✅ "Successfully synced" - C360 sync completed

---

### Method 2: Query MySQL Database Directly

Verify the data is stored in MySQL.

**Steps:**

1.  **Connect to MySQL container**:
    ```bash
    docker exec -it common-debit-card-mysql-1 mysql -uuser -ppassword card_repo
    ```

2.  **Query the cards table**:
    ```sql
    SELECT token_ref, masked_card_number, last4, lifecycle_status, customer_mobile_number, created_at
    FROM cards
    ORDER BY created_at DESC
    LIMIT 5;
    ```

3.  **Expected output**:
    ```
    +---------------+----------------------+-------+------------------+-----------------------+---------------------+
    | token_ref     | masked_card_number   | last4 | lifecycle_status | customer_mobile_number| created_at          |
    +---------------+----------------------+-------+------------------+-----------------------+---------------------+
    | tok_TEST_001  | 4111xxxxxxxx1111     | 1111  | ACTIVE           | 9876543210            | 2023-10-27 10:15:30 |
    +---------------+----------------------+-------+------------------+-----------------------+---------------------+
    ```

4.  **Count total records**:
    ```sql
    SELECT COUNT(*) as total_cards FROM cards;
    ```

5.  **Check sync status**:
    ```sql
    SELECT token_ref, sync_pending, updated_at
    FROM cards
    WHERE customer_mobile_number = '9876543210';
    ```

**Exit MySQL**: Type `exit` and press Enter.

---

### Method 3: Verify via Internal API

Use the Card Repo's internal API to check if data is accessible.

**Steps:**

1.  **Call the internal API**:
    ```bash
    curl -X GET "http://localhost:8081/internal/cards?mobile=9876543210" | jq
    ```

2.  **Expected response**:
    ```json
    [
      {
        "tokenRef": "tok_TEST_001",
        "maskedCardNumber": "4111xxxxxxxx1111",
        "last4": "1111",
        "programCode": "PRG_001",
        "lifecycleStatus": "ACTIVE",
        "eventTimestamp": "2023-10-27T10:00:00"
      }
    ]
    ```

---

### Method 4: Verify via Public API (End-to-End)

Test the complete flow through the Cards Service.

**Steps:**

1.  **Call the public API**:
    ```bash
    curl -X GET "http://localhost:8080/cards" \
      -H "X-Mobile-Number: 9876543210" | jq
    ```

2.  **Expected response**: Same JSON as Method 3.

---

### Method 5: Monitor Kafka Consumer Group

Check if the consumer is actively consuming messages.

**Steps:**

1.  **Check consumer group status**:
    ```bash
    docker exec common-debit-card-kafka-1 kafka-consumer-groups \
      --bootstrap-server localhost:9092 \
      --describe \
      --group card-repo-group
    ```

2.  **Expected output**:
    ```
    GROUP           TOPIC         PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
    card-repo-group card-events   0          5               5               0
    ```

**What to look for:**
- `LAG = 0`: Consumer is up-to-date (no backlog)
- `CURRENT-OFFSET = LOG-END-OFFSET`: All messages consumed

---

## 🧪 Complete Verification Workflow

Here's a complete step-by-step verification:

### Step 1: Start All Services
```bash
docker-compose up -d
cd card-repo && mvn spring-boot:run &
cd ../cards-service && mvn spring-boot:run &
```

### Step 2: Produce Test Event
```bash
docker exec -i common-debit-card-kafka-1 kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic card-events <<EOF
{"tokenRef":"tok_VERIFY_$(date +%s)","maskedCardNumber":"4111xxxxxxxx1111","last4":"1111","programCode":"PRG_001","programCategory":"STANDARD","network":"VISA","bin":"411111","lifecycleStatus":"ACTIVE","rawStatus":"OPEN","customerMobileNumber":"9876543210","custId":"CUST_001","accountNo":"ACC_001","issuedBySystem":"CMS_A","issuanceChannel":"DIGITAL","eventTimestamp":"$(date -u +%Y-%m-%dT%H:%M:%S)"}
EOF
```

### Step 3: Wait for Processing
```bash
sleep 3
```

### Step 4: Verify in Database
```bash
docker exec -it common-debit-card-mysql-1 mysql -uuser -ppassword card_repo \
  -e "SELECT token_ref, last4, lifecycle_status FROM cards ORDER BY created_at DESC LIMIT 1;"
```

### Step 5: Verify via API
```bash
curl -s "http://localhost:8080/cards" \
  -H "X-Mobile-Number: 9876543210" | jq '.[0].tokenRef'
```

---

## 🐛 Troubleshooting

### Issue: "Received message" log appears but "Saved card" doesn't

**Possible causes:**
1.  Missing mandatory fields in the event
2.  Database connection issue
3.  Exception during processing

**Solution:**
- Check for error logs in Card Repo terminal
- Verify MySQL is running: `docker ps | grep mysql`
- Check database connection in `application.properties`

### Issue: LAG is increasing (not 0)

**Possible causes:**
1.  Consumer is slower than producer
2.  Consumer has crashed
3.  Database write is slow

**Solution:**
- Check Card Repo logs for exceptions
- Verify database performance
- Restart Card Repo service

### Issue: Data in DB but not in API response

**Possible causes:**
1.  Wrong mobile number in query
2.  Cards Service is down
3.  DTO mapping issue

**Solution:**
- Verify mobile number matches: `SELECT DISTINCT customer_mobile_number FROM cards;`
- Check if Cards Service is running on port 8080
- Check Cards Service logs

---

## 📊 Monitoring Commands Cheat Sheet

```bash
# Check Kafka topic
docker exec common-debit-card-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092 --list

# Count messages in topic
docker exec common-debit-card-kafka-1 kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 --topic card-events

# Check database records count
docker exec common-debit-card-mysql-1 mysql -uuser -ppassword card_repo \
  -e "SELECT COUNT(*) FROM cards;"

# Check last 5 cards
docker exec common-debit-card-mysql-1 mysql -uuser -ppassword card_repo \
  -e "SELECT token_ref, lifecycle_status, created_at FROM cards ORDER BY created_at DESC LIMIT 5;"

# Test API
curl -s "http://localhost:8080/cards" -H "X-Mobile-Number: 9876543210" | jq length

# Check consumer lag
docker exec common-debit-card-kafka-1 kafka-consumer-groups \
  --bootstrap-server localhost:9092 --describe --group card-repo-group
```

---

## ✅ Success Criteria

You can confirm the flow is working when:

1.  ✅ Kafka consumer logs show "Received message"
2.  ✅ Application logs show "Saved card"
3.  ✅ Database query returns the card record
4.  ✅ Internal API returns the card data
5.  ✅ Public API returns the card data
6.  ✅ Consumer lag is 0
7.  ✅ No errors in application logs
