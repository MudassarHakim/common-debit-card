package com.example.cardsservice.service;

import com.example.cardsservice.entity.Card;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class C360SyncService {

    private final org.springframework.web.client.RestTemplate restTemplate;
    
    @org.springframework.beans.factory.annotation.Value("${profile360.url}")
    private String profile360Url;

    public C360SyncService(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    @Async
    public CompletableFuture<Boolean> syncToC360(Card card) {
        log.info("Syncing card {} to Customer360 at {}...", card.getTokenRef(), profile360Url);
        try {
            // Assuming a POST request with the card object as body
            // In a real scenario, we might need to map Card to a specific C360RequestDto
            restTemplate.postForEntity(profile360Url, card, Void.class);
            
            log.info("Successfully synced card {} to Customer360.", card.getTokenRef());
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Failed to sync card {} to Customer360", card.getTokenRef(), e);
            return CompletableFuture.completedFuture(false);
        }
    }
}
