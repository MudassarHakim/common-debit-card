package com.example.cardsservice.service;

import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class C360SyncService {

    private final RestTemplate restTemplate;
    private final CardRepository cardRepository;

    @Value("${profile360.url}")
    private String profile360Url;

    @Value("${c360.sync.max-retries:3}")
    private int maxRetries;

    @Value("${c360.sync.initial-delay-ms:1000}")
    private long initialDelayMs;

    /**
     * Syncs card to C360 with automatic retry mechanism
     */
    @Async
    public CompletableFuture<Boolean> syncToC360WithRetry(Card card) {
        return syncToC360Internal(card, 0);
    }

    /**
     * Internal method that handles the actual sync with retry logic
     */
    private CompletableFuture<Boolean> syncToC360Internal(Card card, int attemptNumber) {
        log.info("Syncing card {} to Customer360 (attempt {}/{})",
                card.getTokenRef(), attemptNumber + 1, maxRetries);

        try {
            // Update last sync attempt timestamp
            card.setLastSyncAttempt(LocalDateTime.now());

            // Attempt the sync
            restTemplate.postForEntity(profile360Url, card, Void.class);

            log.info("Successfully synced card {} to Customer360", card.getTokenRef());

            // Reset retry count and sync pending flag on success
            card.setSyncPending(false);
            card.setSyncRetryCount(0);
            cardRepository.save(card);

            return CompletableFuture.completedFuture(true);

        } catch (Exception e) {
            log.error("Failed to sync card {} to Customer360 (attempt {}/{})",
                    card.getTokenRef(), attemptNumber + 1, maxRetries, e);

            // Increment retry count
            int newRetryCount = attemptNumber + 1;
            card.setSyncRetryCount(newRetryCount);

            if (newRetryCount < maxRetries) {
                // Calculate exponential backoff delay
                long delayMs = initialDelayMs * (long) Math.pow(2, attemptNumber);

                log.info("Will retry syncing card {} after {}ms", card.getTokenRef(), delayMs);

                // Mark as sync pending
                card.setSyncPending(true);
                cardRepository.save(card);

                // Schedule retry with exponential backoff
                return CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry sleep interrupted for card {}", card.getTokenRef());
                    }
                }).thenCompose(v -> {
                    // Reload card from DB to get latest state
                    Card refreshedCard = cardRepository.findById(card.getId())
                            .orElse(card);
                    return syncToC360Internal(refreshedCard, newRetryCount);
                });

            } else {
                // Max retries exceeded
                log.error("Max retries ({}) exceeded for card {}. Manual sync required.",
                        maxRetries, card.getTokenRef());

                card.setSyncPending(true);
                cardRepository.save(card);

                return CompletableFuture.completedFuture(false);
            }
        }
    }

    /**
     * Manual sync method - can be called from REST endpoint or scheduled job
     * This resets the retry count and attempts sync again
     */
    public boolean manualSyncToC360(Card card) {
        log.info("Manual sync initiated for card {}", card.getTokenRef());

        // Reset retry count for manual sync
        card.setSyncRetryCount(0);
        card.setLastSyncAttempt(LocalDateTime.now());

        try {
            restTemplate.postForEntity(profile360Url, card, Void.class);

            log.info("Manual sync successful for card {}", card.getTokenRef());

            card.setSyncPending(false);
            card.setSyncRetryCount(0);
            cardRepository.save(card);

            return true;

        } catch (Exception e) {
            log.error("Manual sync failed for card {}", card.getTokenRef(), e);

            card.setSyncPending(true);
            card.setSyncRetryCount(1); // Mark as having one failed attempt
            cardRepository.save(card);

            return false;
        }
    }
}
