package com.example.cardsservice.consumer;

import com.example.cardsservice.dto.CardEventDto;
import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import com.example.cardsservice.service.C360SyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
public class CardRetryConsumer {

    private final CardRepository cardRepository;
    private final C360SyncService c360SyncService;
    private final ObjectMapper objectMapper;
    private final Counter retrySuccessCounter;
    private final Counter retryFailureCounter;

    private static final int MAX_RETRY_ATTEMPTS = 5;

    public CardRetryConsumer(CardRepository cardRepository,
            C360SyncService c360SyncService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.cardRepository = cardRepository;
        this.c360SyncService = c360SyncService;
        this.objectMapper = objectMapper;

        this.retrySuccessCounter = Counter.builder("c360.retry.success")
                .description("Number of successful retry queue processing")
                .register(meterRegistry);

        this.retryFailureCounter = Counter.builder("c360.retry.failure")
                .description("Number of failed retry queue processing")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "${c360.sync.retry.topic:card-events-retry}", groupId = "card-retry-consumer-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeRetryQueue(@Payload String message,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset,
            @Header(value = "retry-count", required = false, defaultValue = "0") Integer retryCount) {

        MDC.put("topic", topic);
        MDC.put("offset", String.valueOf(offset));
        MDC.put("retryCount", String.valueOf(retryCount));

        log.info("Received message from retry queue",
                kv("topic", topic),
                kv("offset", offset),
                kv("retryCount", retryCount));

        try {
            CardEventDto event = objectMapper.readValue(message, CardEventDto.class);
            MDC.put("tokenRef", event.getTokenRef());

            // Check if we've exceeded max retry attempts
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                log.error("Max retry attempts exceeded. Moving to DLQ.",
                        kv("maxAttempts", MAX_RETRY_ATTEMPTS));

                Card card = findOrCreateCard(event);
                c360SyncService.pushToDLQ(card, new Exception("Max retry attempts exceeded"));
                retryFailureCounter.increment();
                return;
            }

            // Find the card
            Card card = cardRepository.findByTokenRef(event.getTokenRef())
                    .orElseGet(() -> {
                        log.warn("Card not found in database. Creating from event.");
                        return mapEventToCard(event);
                    });

            // Attempt to sync
            boolean success = c360SyncService.syncToC360(card).get();

            if (success) {
                retrySuccessCounter.increment();
                log.info("Successfully processed retry queue message");
            } else {
                retryFailureCounter.increment();
                log.warn("Failed to process retry queue message. Will be retried by Kafka.");
            }

        } catch (ExecutionException | InterruptedException e) {
            retryFailureCounter.increment();
            log.error("Error processing retry queue message",
                    kv("errorType", e.getClass().getSimpleName()),
                    kv("errorMessage", e.getMessage()));
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to process retry message", e);
        } catch (Exception e) {
            retryFailureCounter.increment();
            log.error("Unexpected error processing retry queue message",
                    kv("errorType", e.getClass().getSimpleName()),
                    kv("errorMessage", e.getMessage()));
            throw new RuntimeException("Failed to process retry message", e);
        } finally {
            MDC.clear();
        }
    }

    private Card findOrCreateCard(CardEventDto event) {
        return cardRepository.findByTokenRef(event.getTokenRef())
                .orElseGet(() -> mapEventToCard(event));
    }

    private Card mapEventToCard(CardEventDto event) {
        Card card = new Card();
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

        return cardRepository.save(card);
    }

    // Helper method for structured logging
    private static org.slf4j.event.KeyValuePair kv(String key, Object value) {
        return new org.slf4j.event.KeyValuePair(key, value);
    }
}
