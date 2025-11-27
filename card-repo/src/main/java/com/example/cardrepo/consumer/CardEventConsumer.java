package com.example.cardrepo.consumer;

import com.example.cardrepo.dto.CardEventDto;
import com.example.cardrepo.entity.Card;
import com.example.cardrepo.repository.CardRepository;
import com.example.cardrepo.service.C360SyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class CardEventConsumer {

    private final CardRepository cardRepository;
    private final C360SyncService c360SyncService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "card-events", groupId = "card-repo-group")
    public void consume(String message) {
        log.info("Received message: {}", message);
        try {
            CardEventDto event = objectMapper.readValue(message, CardEventDto.class);
            
            if (event.getTokenRef() == null) {
                log.error("Missing tokenRef in event: {}", message);
                return;
            }

            Optional<Card> existingCardOpt = cardRepository.findByTokenRef(event.getTokenRef());
            Card card = existingCardOpt.orElse(new Card());

            // Map fields from DTO
            card.setTokenRef(event.getTokenRef());
            card.setMaskedCardNumber(event.getMaskedCardNumber());
            card.setLast4(event.getLast4());
            card.setProgramCode(event.getProgramCode());
            card.setProgramCategory(event.getProgramCategory());
            card.setNetwork(event.getNetwork());
            card.setBin(event.getBin());
            card.setLifecycleStatus(event.getLifecycleStatus());
            card.setRawStatus(event.getRawStatus());
            card.setCustomerMobileNumber(event.getCustomerMobileNumber());
            card.setCustId(event.getCustId());
            card.setAccountNo(event.getAccountNo());
            card.setIssuedBySystem(event.getIssuedBySystem());
            card.setIssuanceChannel(event.getIssuanceChannel());
            
            if (event.getEventTimestamp() != null) {
                card.setEventTimestamp(LocalDateTime.parse(event.getEventTimestamp(), DateTimeFormatter.ISO_DATE_TIME));
            }

            // Check timestamp for idempotency
            if (existingCardOpt.isPresent() && card.getEventTimestamp() != null && 
                existingCardOpt.get().getEventTimestamp() != null &&
                card.getEventTimestamp().isBefore(existingCardOpt.get().getEventTimestamp())) {
                log.warn("Ignoring stale event for tokenRef: {}", event.getTokenRef());
                return;
            }

            Card savedCard = cardRepository.save(card);
            log.info("Saved card: {}", savedCard.getTokenRef());

            // Sync to C360
            c360SyncService.syncToC360(savedCard).thenAccept(success -> {
                if (!success) {
                    savedCard.setSyncPending(true);
                    cardRepository.save(savedCard);
                }
            });

        } catch (Exception e) {
            log.error("Error processing message: {}", message, e);
            // In a real scenario, we might throw here to let Kafka retry or DLQ handle it
            // throw new RuntimeException(e); 
        }
    }
}
