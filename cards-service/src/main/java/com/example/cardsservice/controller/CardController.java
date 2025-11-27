package com.example.cardsservice.controller;

import com.example.cardsservice.service.CardIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardIntegrationService cardIntegrationService;

    @GetMapping
    public List<Object> getCards(@RequestHeader("X-Mobile-Number") String mobileNumber) {
        return cardIntegrationService.getCards(mobileNumber);
    }
    
    @GetMapping("/eligible-cards")
    public List<Object> getEligibleCards(@RequestHeader("X-Mobile-Number") String mobileNumber) {
        // Mock eligibility logic
        return List.of("Standard Debit Card", "Platinum Debit Card");
    }
}
