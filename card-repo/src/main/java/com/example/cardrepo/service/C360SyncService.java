package com.example.cardrepo.service;

import com.example.cardrepo.entity.Card;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class C360SyncService {

    @Async
    public CompletableFuture<Boolean> syncToC360(Card card) {
        log.info("Syncing card {} to Customer360...", card.getTokenRef());
        // Mocking external call
        try {
            Thread.sleep(100); // Simulate network latency
            log.info("Successfully synced card {} to Customer360.", card.getTokenRef());
            return CompletableFuture.completedFuture(true);
        } catch (InterruptedException e) {
            log.error("Failed to sync card {} to Customer360", card.getTokenRef(), e);
            return CompletableFuture.completedFuture(false);
        }
    }
}
