package com.example.cardsservice.scheduler;

import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import com.example.cardsservice.service.C360SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job to retry failed C360 sync operations
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class C360SyncRetryScheduler {

    private final CardRepository cardRepository;
    private final C360SyncService c360SyncService;

    @Value("${c360.sync.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${c360.sync.scheduler.batch-size:50}")
    private int batchSize;

    @Value("${c360.sync.max-retries:3}")
    private int maxRetries;

    /**
     * Runs every 5 minutes to retry failed syncs
     */
    @Scheduled(cron = "${c360.sync.scheduler.cron:0 */5 * * * *}")
    public void retryFailedSyncs() {
        if (!schedulerEnabled) {
            log.debug("C360 sync scheduler is disabled");
            return;
        }

        log.info("Starting scheduled retry of failed C360 syncs");

        try {
            // Find cards with pending sync that haven't exceeded max retries
            Page<Card> pendingCards = cardRepository.findBySyncPending(true, PageRequest.of(0, batchSize));
            List<Card> cards = pendingCards.getContent();

            if (cards.isEmpty()) {
                log.info("No pending syncs found");
                return;
            }

            log.info("Found {} cards with pending sync", cards.size());

            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;

            for (Card card : cards) {
                // Skip if max retries already exceeded
                if (card.getSyncRetryCount() >= maxRetries) {
                    log.warn("Card {} has exceeded max retries ({}). Skipping automatic retry.",
                            card.getTokenRef(), maxRetries);
                    skippedCount++;
                    continue;
                }

                // Optional: Add a delay between last attempt and retry (e.g., 5 minutes)
                if (card.getLastSyncAttempt() != null &&
                        card.getLastSyncAttempt().isAfter(LocalDateTime.now().minusMinutes(5))) {
                    log.debug("Card {} was attempted recently. Skipping for now.", card.getTokenRef());
                    skippedCount++;
                    continue;
                }

                try {
                    boolean success = c360SyncService.manualSyncToC360(card);

                    if (success) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.error("Error processing sync for card {}", card.getTokenRef(), e);
                    failureCount++;
                }

                // Small delay between syncs to avoid overwhelming the C360 service
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Scheduler interrupted", e);
                    break;
                }
            }

            log.info("Scheduled sync retry completed. Success: {}, Failed: {}, Skipped: {}",
                    successCount, failureCount, skippedCount);

        } catch (Exception e) {
            log.error("Error during scheduled sync retry", e);
        }
    }
}
