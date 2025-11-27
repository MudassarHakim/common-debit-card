package com.example.cardrepo.controller;

import com.example.cardrepo.dto.CardResponseDto;
import com.example.cardrepo.entity.Card;
import com.example.cardrepo.repository.CardRepository;
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
