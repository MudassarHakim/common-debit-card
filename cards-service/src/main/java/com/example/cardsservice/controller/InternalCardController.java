package com.example.cardsservice.controller;

import com.example.cardsservice.dto.CardResponseDto;
import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/cards")
@RequiredArgsConstructor
public class InternalCardController {

    private final CardRepository cardRepository;

    @GetMapping
    public List<CardResponseDto> getCards(@RequestParam String mobile) {
        return cardRepository.findByCustomerMobileNumber(mobile).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private CardResponseDto mapToDto(Card card) {
        CardResponseDto dto = new CardResponseDto();
        dto.setTokenRef(card.getTokenRef());
        dto.setMaskedCardNumber(card.getMaskedCardNumber());
        dto.setLast4(card.getLast4());
        dto.setProgramCode(card.getProgramCode());
        dto.setLifecycleStatus(card.getLifecycleStatus());
        dto.setEventTimestamp(card.getEventTimestamp());
        return dto;
    }
}
