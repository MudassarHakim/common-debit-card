#!/bin/bash

set -e

echo "========================================="
echo "  End-to-End System Verification"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test timestamp
TIMESTAMP=$(date +%s)
TEST_TOKEN="tok_TEST_${TIMESTAMP}"
TEST_MOBILE="9876543210"

echo "📋 Test Configuration:"
echo "  Token: $TEST_TOKEN"
echo "  Mobile: $TEST_MOBILE"
echo ""

# Step 1: Verify Docker containers
echo "Step 1: Verifying Docker containers..."
RUNNING_CONTAINERS=$(docker ps --filter "name=common-debit-card" --format "{{.Names}}" | wc -l | tr -d ' ')
if [ "$RUNNING_CONTAINERS" -eq 4 ]; then
    echo -e "${GREEN}✓${NC} All 4 containers are running"
else
    echo -e "${RED}✗${NC} Expected 4 containers, found $RUNNING_CONTAINERS"
    exit 1
fi
echo ""

# Step 2: Wait for Kafka to be ready
echo "Step 2: Waiting for Kafka to be ready..."
sleep 5
echo -e "${GREEN}✓${NC} Kafka should be ready"
echo ""

# Step 3: Produce test event to Kafka
echo "Step 3: Producing test event to Kafka..."
docker exec -i common-debit-card-kafka-1 kafka-console-producer \
  --bootstrap-server localhost:29092 \
  --topic card-events <<EOF
{"tokenRef":"${TEST_TOKEN}","maskedCardNumber":"4111xxxxxxxx1111","last4":"1111","programCode":"PRG_001","programCategory":"STANDARD","network":"VISA","bin":"411111","lifecycleStatus":"ACTIVE","rawStatus":"OPEN","customerMobileNumber":"${TEST_MOBILE}","custId":"CUST_${TIMESTAMP}","accountNo":"ACC_${TIMESTAMP}","issuedBySystem":"CMS_A","issuanceChannel":"DIGITAL","eventTimestamp":"$(date -u +%Y-%m-%dT%H:%M:%S)"}
EOF

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Event produced to Kafka successfully"
else
    echo -e "${RED}✗${NC} Failed to produce event to Kafka"
    exit 1
fi
echo ""

# Step 4: Wait for processing
echo "Step 4: Waiting for event processing (5 seconds)..."
sleep 5
echo -e "${GREEN}✓${NC} Processing time elapsed"
echo ""

# Step 5: Verify in MySQL
echo "Step 5: Verifying data in MySQL..."
CARD_COUNT=$(docker exec common-debit-card-mysql-1 mysql -uuser -ppassword card_repo -se "SELECT COUNT(*) FROM cards WHERE token_ref='${TEST_TOKEN}'" 2>/dev/null)

if [ "$CARD_COUNT" == "1" ]; then
    echo -e "${GREEN}✓${NC} Card found in database"
    echo ""
    echo "  Card Details:"
    docker exec common-debit-card-mysql-1 mysql -uuser -ppassword card_repo -t -e "SELECT token_ref, last4, lifecycle_status, customer_mobile_number FROM cards WHERE token_ref='${TEST_TOKEN}'" 2>/dev/null
else
    echo -e "${RED}✗${NC} Card not found in database (expected 1, found $CARD_COUNT)"
    exit 1
fi
echo ""

# Step 6: Check Kafka consumer lag
echo "Step 6: Checking Kafka consumer lag..."
docker exec common-debit-card-kafka-1 kafka-consumer-groups \
  --bootstrap-server localhost:29092 \
  --describe \
  --group card-repo-group 2>/dev/null || echo -e "${YELLOW}⚠${NC} Consumer group not yet registered (this is normal if services aren't running)"
echo ""

echo "========================================="
echo -e "${GREEN}✓ Infrastructure Verification Complete!${NC}"
echo "========================================="
echo ""
echo "📊 Summary:"
echo "  ✓ Docker containers: Running"
echo "  ✓ Kafka: Accepting messages"
echo " ✓ MySQL: Storing data"
echo ""
echo "⚠️  Note: To complete verification, start the Spring Boot services:"
echo "  1. cd card-repo && mvn spring-boot:run"
echo "  2. cd cards-service && mvn spring-boot:run"
echo "  3. Test API: curl -H 'X-Mobile-Number: ${TEST_MOBILE}' http://localhost:8080/cards"
echo ""
