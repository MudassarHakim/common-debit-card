package com.example.cardsservice.controller;

import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import com.example.cardsservice.service.C360SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cards/sync")
@RequiredArgsConstructor
@Slf4j
public class C360SyncController {

    private final CardRepository cardRepository;
    private final C360SyncService c360SyncService;

    /**
     * Manually sync a specific card to C360 by tokenRef
     */
    @PostMapping("/manual/{tokenRef}")
    public ResponseEntity<Map<String, Object>> manualSyncCard(@PathVariable String tokenRef) {
        log.info("Manual sync requested for card: {}", tokenRef);

        Card card = cardRepository.findByTokenRef(tokenRef)
                .orElseThrow(() -> new RuntimeException("Card not found: " + tokenRef));

        boolean success = c360SyncService.syncToC360(card).join();

        Map<String, Object> response = new HashMap<>();
        response.put("tokenRef", tokenRef);
        response.put("success", success);
        response.put("syncPending", card.isSyncPending());
        response.put("retryCount", card.getSyncRetryCount());
        response.put("lastSyncAttempt", card.getLastSyncAttempt());

        return ResponseEntity.ok(response);
    }

    /**
     * Get all cards with pending sync
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingSyncCards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<Card> pendingCards = cardRepository.findBySyncPending(true, PageRequest.of(page, size));

        Map<String, Object> response = new HashMap<>();
        response.put("cards", pendingCards.getContent());
        response.put("totalElements", pendingCards.getTotalElements());
        response.put("totalPages", pendingCards.getTotalPages());
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    /**
     * Manually sync all pending cards
     */
    @PostMapping("/manual/all")
    public ResponseEntity<Map<String, Object>> manualSyncAllPending(
            @RequestParam(defaultValue = "100") int limit) {

        log.info("Manual sync requested for all pending cards (limit: {})", limit);

        Page<Card> pendingCards = cardRepository.findBySyncPending(true, PageRequest.of(0, limit));
        List<Card> cards = pendingCards.getContent();

        int successCount = 0;
        int failureCount = 0;

        for (Card card : cards) {
            boolean success = c360SyncService.syncToC360(card).join();
            if (success) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("totalProcessed", cards.size());
        response.put("successCount", successCount);
        response.put("failureCount", failureCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Get sync statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSyncStats() {
        long totalCards = cardRepository.count();
        long pendingSyncCards = cardRepository.countBySyncPending(true);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCards", totalCards);
        stats.put("pendingSyncCards", pendingSyncCards);
        stats.put("syncedCards", totalCards - pendingSyncCards);
        stats.put("syncSuccessRate", totalCards > 0 ? ((totalCards - pendingSyncCards) * 100.0 / totalCards) : 0);

        return ResponseEntity.ok(stats);
    }
}
