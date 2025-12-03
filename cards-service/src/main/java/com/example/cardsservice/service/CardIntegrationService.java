package com.example.cardsservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CardIntegrationService {

    private final RestTemplate restTemplate;

    private final com.example.cardsservice.repository.CardRepository cardRepository;

    public List<com.example.cardsservice.dto.CardResponseDto> getCards(String mobileNumber) {
        return cardRepository.findByCustomerMobileNumber(mobileNumber).stream()
                .map(this::mapToDto)
                .collect(java.util.stream.Collectors.toList());
    }

    private com.example.cardsservice.dto.CardResponseDto mapToDto(com.example.cardsservice.entity.Card card) {
        com.example.cardsservice.dto.CardResponseDto dto = new com.example.cardsservice.dto.CardResponseDto();
        dto.setTokenRef(card.getTokenRef());
        dto.setMaskedCardNumber(card.getMaskedCardNumber());
        dto.setLast4(card.getLast4());
        dto.setProgramCode(card.getProgramCode());
        dto.setLifecycleStatus(card.getLifecycleStatus());
        dto.setEventTimestamp(card.getEventTimestamp());
        return dto;
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
