package com.example.cardsservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CardIntegrationService {

    private final RestTemplate restTemplate;
    private final String CARD_REPO_URL = "http://localhost:8081/internal/cards";

    @CircuitBreaker(name = "cardRepo", fallbackMethod = "getCardsFallback")
    public List<Object> getCards(String mobileNumber) {
        return restTemplate.getForObject(CARD_REPO_URL + "?mobile=" + mobileNumber, List.class);
    }

    public List<Object> getCardsFallback(String mobileNumber, Throwable t) {
        return Collections.emptyList(); // Graceful degradation
    }
}
