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

    @org.springframework.beans.factory.annotation.Value("${card-repo.url}")
    private String cardRepoUrl;

    @CircuitBreaker(name = "cardRepo", fallbackMethod = "getCardsFallback")
    public List<Object> getCards(String mobileNumber) {
        return restTemplate.getForObject(cardRepoUrl + "?mobile=" + mobileNumber, List.class);
    }

    public List<Object> getCardsFallback(String mobileNumber, Throwable t) {
        return Collections.emptyList(); // Graceful degradation
    }

    @org.springframework.beans.factory.annotation.Value("${eligibility.url}")
    private String eligibilityUrl;

    @CircuitBreaker(name = "eligibility", fallbackMethod = "checkEligibilityFallback")
    public boolean checkEligibility(String mobileNumber) {
        // Assuming a GET request to check eligibility
        // Adjust the path/query params as per the actual contract
        try {
            // Example: GET /customer-products?mobile=...
            // We assume 200 OK means eligible, or response body contains status
            restTemplate.getForEntity(eligibilityUrl + "?mobile=" + mobileNumber, Void.class);
            return true;
        } catch (Exception e) {
            // Log error
            return false;
        }
    }

    public boolean checkEligibilityFallback(String mobileNumber, Throwable t) {
        return false; // Default to not eligible on failure
    }
}
