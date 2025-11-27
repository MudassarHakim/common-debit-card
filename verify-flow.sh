#!/bin/bash

# Wait for services to start (manual step usually, but good for docs)
echo "Ensure Docker Compose and Spring Boot apps are running..."

# 1. Produce Kafka Event
echo "Producing Kafka Event..."
docker exec -i common-debit-card-kafka-1 kafka-console-producer --bootstrap-server localhost:9092 --topic card-events <<EOF
{"tokenRef":"tok_12345","maskedCardNumber":"4111xxxxxxxx1111","last4":"1111","programCode":"PRG_001","programCategory":"STANDARD","network":"VISA","bin":"411111","lifecycleStatus":"ACTIVE","rawStatus":"OPEN","customerMobileNumber":"9876543210","custId":"CUST_001","accountNo":"ACC_001","issuedBySystem":"CMS_A","issuanceChannel":"DIGITAL","eventTimestamp":"2023-10-27T10:00:00"}
EOF

# 2. Wait for processing
echo "Waiting for processing..."
sleep 5

# 3. Verify via Public API
echo "Verifying via Public API..."
RESPONSE=$(curl -s -H "X-Mobile-Number: 9876543210" http://localhost:8080/cards)

echo "Response: $RESPONSE"

if [[ $RESPONSE == *"tok_12345"* ]]; then
  echo "✅ Verification SUCCESS: Card found!"
else
  echo "❌ Verification FAILED: Card not found."
fi
