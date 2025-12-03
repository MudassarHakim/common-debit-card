# C360 Sync Retry Mechanism

## Overview
This implementation provides a robust retry mechanism for syncing card data to Customer360 (C360) with automatic retries and manual sync capabilities.

## Features

### 1. **Automatic Retry with Exponential Backoff**
- Automatically retries failed sync operations up to 3 times (configurable)
- Uses exponential backoff strategy:
  - 1st retry: 1 second delay
  - 2nd retry: 2 seconds delay
  - 3rd retry: 4 seconds delay
- Tracks retry attempts in the database

### 2. **Manual Sync Capability**
After automatic retries are exhausted, you can manually trigger sync operations via REST API endpoints.

### 3. **Scheduled Retry Job**
A background scheduler runs every 5 minutes to retry cards with pending sync that haven't exceeded max retries.

### 4. **Monitoring & Statistics**
REST endpoints to monitor sync status and get statistics.

## Database Schema Changes

New fields added to `Card` entity:
```sql
ALTER TABLE cards ADD COLUMN sync_retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE cards ADD COLUMN last_sync_attempt DATETIME;
```

## Configuration

Add these properties to `application.properties`:

```properties
# C360 Sync Configuration
c360.sync.max-retries=3
c360.sync.initial-delay-ms=1000
c360.sync.scheduler.enabled=true
c360.sync.scheduler.batch-size=50
c360.sync.scheduler.cron=0 */5 * * * *
```

### Configuration Properties Explained

| Property | Default | Description |
|----------|---------|-------------|
| `c360.sync.max-retries` | 3 | Maximum number of automatic retry attempts |
| `c360.sync.initial-delay-ms` | 1000 | Initial delay in milliseconds before first retry |
| `c360.sync.scheduler.enabled` | true | Enable/disable the scheduled retry job |
| `c360.sync.scheduler.batch-size` | 50 | Number of cards to process in each scheduler run |
| `c360.sync.scheduler.cron` | `0 */5 * * * *` | Cron expression for scheduler (every 5 minutes) |

## REST API Endpoints

### 1. Manual Sync Single Card
```bash
POST /api/cards/sync/manual/{tokenRef}
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/cards/sync/manual/tok_123
```

**Response:**
```json
{
  "tokenRef": "tok_123",
  "success": true,
  "syncPending": false,
  "retryCount": 0,
  "lastSyncAttempt": "2025-12-03T12:20:10"
}
```

### 2. Get Pending Sync Cards
```bash
GET /api/cards/sync/pending?page=0&size=50
```

**Example:**
```bash
curl http://localhost:8080/api/cards/sync/pending?page=0&size=50
```

**Response:**
```json
{
  "cards": [...],
  "totalElements": 10,
  "totalPages": 1,
  "currentPage": 0
}
```

### 3. Manual Sync All Pending Cards
```bash
POST /api/cards/sync/manual/all?limit=100
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/cards/sync/manual/all?limit=100
```

**Response:**
```json
{
  "totalProcessed": 10,
  "successCount": 8,
  "failureCount": 2
}
```

### 4. Get Sync Statistics
```bash
GET /api/cards/sync/stats
```

**Example:**
```bash
curl http://localhost:8080/api/cards/sync/stats
```

**Response:**
```json
{
  "totalCards": 1000,
  "pendingSyncCards": 10,
  "syncedCards": 990,
  "syncSuccessRate": 99.0
}
```

## How It Works

### Flow Diagram

```
Card Event → Save to DB → Sync to C360
                              ↓
                          Success? 
                          ↓     ↓
                        Yes    No
                         ↓      ↓
                       Done   Retry (attempt 1)
                                ↓
                            Success?
                            ↓     ↓
                          Yes    No
                           ↓      ↓
                         Done   Retry (attempt 2)
                                  ↓
                              Success?
                              ↓     ↓
                            Yes    No
                             ↓      ↓
                           Done   Retry (attempt 3)
                                    ↓
                                Success?
                                ↓     ↓
                              Yes    No
                               ↓      ↓
                             Done   Mark as Pending
                                    (Manual Sync Required)
```

### Automatic Retry Process

1. **Initial Sync Attempt**: When a card is saved, it automatically attempts to sync to C360
2. **Retry on Failure**: If sync fails, it retries with exponential backoff
3. **Track Attempts**: Each attempt is tracked in the database
4. **Max Retries**: After 3 failed attempts, the card is marked as `syncPending=true`
5. **Scheduled Retry**: Background job picks up pending cards and retries them
6. **Manual Intervention**: If automatic retries fail, use manual sync endpoints

### Scheduled Retry Job

The scheduler runs every 5 minutes and:
- Finds cards with `syncPending=true`
- Skips cards that exceeded max retries
- Skips cards attempted in the last 5 minutes (to avoid overwhelming the system)
- Attempts to sync each card
- Logs success/failure statistics

## Monitoring

### Check Pending Syncs
```bash
# Get count of pending syncs
curl http://localhost:8080/api/cards/sync/stats

# Get list of pending cards
curl http://localhost:8080/api/cards/sync/pending
```

### Logs
Monitor application logs for sync status:
```
INFO  - Syncing card tok_123 to Customer360 (attempt 1/3)
INFO  - Successfully synced card tok_123 to Customer360
ERROR - Failed to sync card tok_456 to Customer360 (attempt 1/3)
INFO  - Will retry syncing card tok_456 after 1000ms
```

## Troubleshooting

### Card Stuck in Pending State
If a card has `syncPending=true` and `syncRetryCount >= 3`:

1. Check C360 service availability
2. Verify the card data is valid
3. Use manual sync endpoint:
   ```bash
   curl -X POST http://localhost:8080/api/cards/sync/manual/{tokenRef}
   ```

### Disable Automatic Retries
Set in `application.properties`:
```properties
c360.sync.scheduler.enabled=false
```

### Adjust Retry Timing
Modify the cron expression:
```properties
# Run every 10 minutes instead of 5
c360.sync.scheduler.cron=0 */10 * * * *

# Run every hour
c360.sync.scheduler.cron=0 0 * * * *
```

## Testing

### Unit Tests
The test file has been updated to use the new `syncToC360WithRetry` method:
```java
when(c360SyncService.syncToC360WithRetry(any(Card.class)))
    .thenReturn(CompletableFuture.completedFuture(true));
```

### Integration Testing
1. Start the application
2. Send a card event via Kafka
3. Simulate C360 failure (stop C360 service)
4. Observe retry attempts in logs
5. Check pending sync count
6. Manually trigger sync after fixing C360

## Best Practices

1. **Monitor Pending Syncs**: Regularly check `/api/cards/sync/stats` endpoint
2. **Alert on High Pending Count**: Set up alerts when pending sync count exceeds threshold
3. **Batch Manual Sync**: Use `/api/cards/sync/manual/all` during maintenance windows
4. **Adjust Retry Delays**: Tune `c360.sync.initial-delay-ms` based on C360 response times
5. **Review Failed Syncs**: Investigate cards that consistently fail to sync

## Future Enhancements

- [ ] Dead Letter Queue (DLQ) for permanently failed syncs
- [ ] Webhook notifications for failed syncs
- [ ] Admin UI for managing pending syncs
- [ ] Metrics and dashboards (Prometheus/Grafana)
- [ ] Circuit breaker pattern for C360 service
