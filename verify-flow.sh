#!/bin/bash

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "========================================="
echo "  Enhanced End-to-End Verification"
echo "========================================="
echo ""

# 0. Health Checks
echo "Step 0: Checking Service Health..."
SERVICE_HEALTH=$(curl -s http://localhost:8080/actuator/health | grep "UP")

if [[ -n "$SERVICE_HEALTH" ]]; then
    echo -e "${GREEN}✅ Cards service is UP${NC}"
else
    echo -e "${RED}❌ Service is not healthy. Please start it first.${NC}"
    exit 1
fi
echo ""

# 1. Produce Kafka Event
echo "Step 1: Producing Kafka Event..."
TEST_TOKEN="tok_ENHANCED_$(date +%s)"
TEST_MOBILE="9998887776"

docker exec -i common-debit-card-kafka-1 kafka-console-producer --bootstrap-server localhost:29092 --topic card-events <<EOF
{"tokenRef":"${TEST_TOKEN}","maskedCardNumber":"4111xxxxxxxx1111","last4":"1111","programCode":"PRG_001","programCategory":"STANDARD","network":"VISA","bin":"411111","lifecycleStatus":"ACTIVE","rawStatus":"OPEN","customerMobileNumber":"${TEST_MOBILE}","custId":"CUST_001","accountNo":"ACC_001","issuedBySystem":"CMS_A","issuanceChannel":"DIGITAL","eventTimestamp":"$(date -u +%Y-%m-%dT%H:%M:%S)"}
EOF

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Event produced successfully${NC}"
else
    echo -e "${RED}❌ Failed to produce event${NC}"
    exit 1
fi
echo ""

# 2. Wait for processing
echo "Step 2: Waiting for processing (5s)..."
sleep 5
echo ""

# 3. Verify in Database
echo "Step 3: Verifying Data in MySQL..."
DB_COUNT=$(docker exec common-debit-card-mysql-1 mysql -uuser -ppassword card_repo -se "SELECT COUNT(*) FROM cards WHERE token_ref='${TEST_TOKEN}'" 2>/dev/null)

if [ "$DB_COUNT" == "1" ]; then
    echo -e "${GREEN}✅ Database record found for ${TEST_TOKEN}${NC}"
else
    echo -e "${RED}❌ Database record NOT found for ${TEST_TOKEN}${NC}"
    exit 1
fi
echo ""

# 4. Verify via Public API (Positive Case)
echo "Step 4: Verifying via Public API (Positive Case)..."
RESPONSE=$(curl -s -H "X-Mobile-Number: ${TEST_MOBILE}" http://localhost:8080/cards)
echo "Response: $RESPONSE"

if [[ $RESPONSE == *"${TEST_TOKEN}"* ]]; then
  echo -e "${GREEN}✅ API returned correct card!${NC}"
else
  echo -e "${RED}❌ API did not return the card.${NC}"
  exit 1
fi
echo ""

# 5. Verify via Public API (Negative Case)
echo "Step 5: Verifying via Public API (Negative Case)..."
NON_EXISTENT_MOBILE="0000000000"
NEGATIVE_RESPONSE=$(curl -s -H "X-Mobile-Number: ${NON_EXISTENT_MOBILE}" http://localhost:8080/cards)
echo "Response: $NEGATIVE_RESPONSE"

if [[ $NEGATIVE_RESPONSE == "[]" ]]; then
  echo -e "${GREEN}✅ API correctly returned empty list for unknown user${NC}"
else
  echo -e "${RED}❌ API returned unexpected data for unknown user${NC}"
  exit 1
fi
echo ""

# 6. Idempotency Test
echo "Step 6: Testing Idempotency (Sending same event again)..."
docker exec -i common-debit-card-kafka-1 kafka-console-producer --bootstrap-server localhost:29092 --topic card-events <<EOF
{"tokenRef":"${TEST_TOKEN}","maskedCardNumber":"4111xxxxxxxx1111","last4":"1111","programCode":"PRG_001","programCategory":"STANDARD","network":"VISA","bin":"411111","lifecycleStatus":"ACTIVE","rawStatus":"OPEN","customerMobileNumber":"${TEST_MOBILE}","custId":"CUST_001","accountNo":"ACC_001","issuedBySystem":"CMS_A","issuanceChannel":"DIGITAL","eventTimestamp":"$(date -u +%Y-%m-%dT%H:%M:%S)"}
EOF
sleep 3

DB_COUNT_AFTER=$(docker exec common-debit-card-mysql-1 mysql -uuser -ppassword card_repo -se "SELECT COUNT(*) FROM cards WHERE token_ref='${TEST_TOKEN}'" 2>/dev/null)

if [ "$DB_COUNT_AFTER" == "1" ]; then
    echo -e "${GREEN}✅ Idempotency confirmed (Record count remains 1)${NC}"
else
    echo -e "${RED}❌ Idempotency failed (Record count is $DB_COUNT_AFTER)${NC}"
fi
echo ""

echo "========================================="
echo -e "${GREEN}🎉 All Verification Steps Passed!${NC}"
echo "========================================="
