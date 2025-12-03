# C360 Sync Retry Strategy with Circuit Breaker

## Overview
This implementation provides a robust, production-ready retry mechanism for syncing card data to Customer360 (C360) with automatic retries, circuit breaker protection, and Kafka-based retry queue.

## Architecture

### Flow Diagram
```
Card Event → Consumer → Save to DB → C360 Sync (with Circuit Breaker)
                                           ↓
                                    Circuit Breaker Check
                                           ↓
                                    ┌──────┴──────┐
                                    │   CLOSED    │ (Normal operation)
                                    └──────┬──────┘
                                           ↓
                                    Attempt Sync (Retry up to 3 times)
                                           ↓
                                    ┌──────┴──────┐
                                    │   Success?  │
                                    └──────┬──────┘
                                    ┌──────┴──────┐
                              Yes ←─┤             ├─→ No
                               ↓                      ↓
                            Done              Max Retries?
                                                     ↓
                                              ┌──────┴──────┐
                                        Yes ←─┤             ├─→ No (Retry)
                                         ↓                      ↓
                                  Push to Retry Queue    Exponential Backoff
                                                               ↓
                                                         Retry Attempt

If Circuit Breaker OPENS (50% failure rate):
    → Immediate fallback → Push to Retry Queue (no retries)
```

## Components

### 1. C360SyncService
**Location**: `src/main/java/com/example/cardsservice/service/C360SyncService.java`

**Key Features**:
- **Retry Logic**: Attempts sync up to 3 times with exponential backoff (1s, 2s, 4s)
- **Circuit Breaker**: Protects against cascading failures when C360 is down
- **Fallback**: Immediately pushes to retry queue when circuit is OPEN
- **Async Processing**: Non-blocking execution using `@Async` and `CompletableFuture`

**Methods**:
- `syncToC360(Card)`: Public entry point for syncing
- `syncToC360Internal(Card, int)`: Internal method with retry logic and circuit breaker
- `syncFallback(Card, int, Throwable)`: Fallback when circuit breaker is OPEN
- `pushToRetryQueue(Card)`: Sends failed cards to Kafka retry topic

### 2. Circuit Breaker Configuration

**Configuration** (`application.properties`):
```properties
# Circuit Breaker opens when 50% of calls fail
resilience4j.circuitbreaker.instances.c360Sync.failure-rate-threshold=50

# Circuit Breaker opens when 50% of calls are slow (>5s)
resilience4j.circuitbreaker.instances.c360Sync.slow-call-rate-threshold=50
resilience4j.circuitbreaker.instances.c360Sync.slow-call-duration-threshold=5s

# Wait 30 seconds before transitioning to HALF_OPEN
resilience4j.circuitbreaker.instances.c360Sync.wait-duration-in-open-state=30s

# In HALF_OPEN state, allow 3 test calls
resilience4j.circuitbreaker.instances.c360Sync.permitted-number-of-calls-in-half-open-state=3

# Use a sliding window of 10 calls to calculate failure rate
resilience4j.circuitbreaker.instances.c360Sync.sliding-window-type=COUNT_BASED
resilience4j.circuitbreaker.instances.c360Sync.sliding-window-size=10

# Need at least 5 calls before circuit breaker can open
resilience4j.circuitbreaker.instances.c360Sync.minimum-number-of-calls=5

# Automatically transition from OPEN to HALF_OPEN
resilience4j.circuitbreaker.instances.c360Sync.automatic-transition-from-open-to-half-open-enabled=true
```

### 3. Circuit Breaker States

#### CLOSED (Normal Operation)
- All requests pass through to C360
- Failures are counted in sliding window
- If failure rate exceeds 50% → transition to OPEN

#### OPEN (C360 is Down/Degraded)
- **No requests** are sent to C360
- All requests immediately trigger fallback (push to retry queue)
- After 30 seconds → transition to HALF_OPEN

#### HALF_OPEN (Testing Recovery)
- Allow 3 test calls to C360
- If all succeed → transition to CLOSED
- If any fail → transition back to OPEN

## Retry Strategy

### Application-Level Retries (Before Queue)
1. **Attempt 1**: Immediate
2. **Attempt 2**: After 1 second (if attempt 1 fails)
3. **Attempt 3**: After 2 seconds (if attempt 2 fails)
4. **Attempt 4**: After 4 seconds (if attempt 3 fails)

**Total**: 4 attempts over ~7 seconds

### Circuit Breaker Override
If the circuit breaker is OPEN:
- **Skip all retries**
- Immediately push to retry queue
- This prevents wasting time on a known-down service

### Kafka Retry Queue
After exhausting retries (or circuit breaker fallback):
- Card event is serialized to JSON
- Pushed to `card-events-retry` topic
- A separate consumer (to be implemented) can process this topic with:
  - Longer delays between retries
  - Different retry strategies
  - Manual intervention capabilities

## Configuration

### Retry Configuration
```properties
# Maximum number of retry attempts (default: 3)
c360.sync.max-retries=3

# Initial delay before first retry in milliseconds (default: 1000)
c360.sync.initial-delay-ms=1000

# Kafka topic for failed syncs
c360.sync.retry.topic=card-events-retry
```

### Tuning Guidelines

**For High-Volume Systems**:
- Reduce `max-retries` to 2 (faster failure detection)
- Reduce `initial-delay-ms` to 500ms
- Lower `failure-rate-threshold` to 30% (more aggressive circuit breaking)

**For Low-Latency C360**:
- Increase `slow-call-duration-threshold` to 10s
- Increase `wait-duration-in-open-state` to 60s

**For Unstable C360**:
- Increase `sliding-window-size` to 20 (more data before opening)
- Increase `minimum-number-of-calls` to 10

## Benefits

### 1. Fast Failure Detection
- Circuit breaker opens after detecting 50% failure rate
- Prevents wasting resources on a down service
- Consumer throughput remains high even when C360 is down

### 2. Automatic Recovery
- Circuit breaker automatically tests recovery every 30 seconds
- No manual intervention needed
- Gradual transition back to normal operation

### 3. Resource Protection
- Prevents thread pool exhaustion
- Avoids cascading failures
- Protects Kafka consumer lag from growing

### 4. Observability
- Logs clearly indicate circuit breaker state
- Easy to monitor failure rates and circuit state
- Can integrate with metrics (Prometheus/Grafana)

## Monitoring

### Key Metrics to Track
1. **Circuit Breaker State**: CLOSED, OPEN, HALF_OPEN
2. **Failure Rate**: Percentage of failed sync attempts
3. **Slow Call Rate**: Percentage of calls exceeding 5s
4. **Retry Queue Size**: Number of messages in `card-events-retry` topic
5. **Consumer Lag**: Lag on main `card-events` topic

### Log Patterns
```
# Normal operation
INFO - Syncing card tok_123 to Customer360 (attempt 1/4)
INFO - Successfully synced card tok_123 to Customer360

# Retry scenario
ERROR - Failed to sync card tok_456 to Customer360 (attempt 1/4)
INFO - Will retry syncing card tok_456 after 1000ms
ERROR - Failed to sync card tok_456 to Customer360 (attempt 4/4)
ERROR - Max retries exhausted for card tok_456. Pushing to retry queue.

# Circuit breaker scenario
WARN - Circuit Breaker is OPEN or fallback triggered for card tok_789. Pushing to retry queue directly.
```

## Best Practices

### 1. Monitor Circuit Breaker State
Set up alerts when circuit breaker opens:
```
Alert: C360 Circuit Breaker OPEN
Severity: High
Action: Check C360 service health
```

### 2. Process Retry Queue
Implement a separate consumer for `card-events-retry` topic with:
- Lower throughput (to avoid overwhelming C360 during recovery)
- Longer delays between retries (e.g., 5 minutes)
- Dead Letter Queue (DLQ) for permanent failures

### 3. Idempotency
Ensure C360 API is idempotent to handle duplicate requests safely.

### 4. Testing
- Test circuit breaker behavior in staging
- Simulate C360 downtime
- Verify fallback behavior
- Monitor retry queue processing

## Future Enhancements

- [ ] Add metrics with Micrometer
- [ ] Implement retry queue consumer
- [ ] Add Dead Letter Queue (DLQ)
- [ ] Switch to WebClient for non-blocking I/O
- [ ] Add distributed tracing (Zipkin/Jaeger)
- [ ] Implement bulkhead pattern for thread isolation
- [ ] Add rate limiting for C360 API calls
