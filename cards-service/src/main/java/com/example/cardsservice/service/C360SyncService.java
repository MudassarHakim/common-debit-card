package com.example.cardsservice.service;

import com.example.cardsservice.dto.CardEventDto;
import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class C360SyncService {

    private final WebClient webClient;
    private final CardRepository cardRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    // Metrics
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter retryCounter;
    private final Counter dlqPushCounter;
    private final Timer syncTimer;

    @Value("${profile360.url}")
    private String profile360Url;

    @Value("${c360.sync.retry.topic:card-events-retry}")
    private String retryTopic;

    @Value("${c360.sync.dlq.topic:card-events-dlq}")
    private String dlqTopic;

    @Value("${c360.sync.max-retries:3}")
    private int maxRetries;

    @Value("${c360.sync.initial-delay-ms:1000}")
    private long initialDelayMs;

    public C360SyncService(WebClient webClient,
            CardRepository cardRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            Tracer tracer,
            MeterRegistry meterRegistry) {
        this.webClient = webClient;
        this.cardRepository = cardRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tracer = tracer;

        // Initialize metrics
        this.successCounter = Counter.builder("c360.sync.success")
                .description("Number of successful C360 sync operations")
                .register(meterRegistry);

        this.failureCounter = Counter.builder("c360.sync.failure")
                .description("Number of failed C360 sync operations")
                .register(meterRegistry);

        this.retryCounter = Counter.builder("c360.sync.retries")
                .description("Number of C360 sync retry attempts")
                .register(meterRegistry);

        this.dlqPushCounter = Counter.builder("c360.sync.dlq_push")
                .description("Number of messages pushed to DLQ")
                .register(meterRegistry);

        this.syncTimer = Timer.builder("c360.sync.duration")
                .description("Time taken for C360 sync operations")
                .register(meterRegistry);
    }

    /**
     * Syncs card to C360. If failed, retries up to maxRetries. If still failed,
     * pushes to retry queue.
     */
    @Async
    public CompletableFuture<Boolean> syncToC360(Card card) {
        // Set up MDC for structured logging
        setupMDC(card);

        return syncToC360Internal(card, 0)
                .doFinally(signal -> MDC.clear())
                .toFuture();
    }

    @CircuitBreaker(name = "c360Sync", fallbackMethod = "syncFallback")
    private Mono<Boolean> syncToC360Internal(Card card, int attemptNumber) {
        // Ensure MDC is set for reactive context
        setupMDC(card);

        if (attemptNumber > 0) {
            retryCounter.increment();
            log.info("Retry attempt for card sync",
                    kv("attemptNumber", attemptNumber),
                    kv("maxRetries", maxRetries));
        }

        log.info("Syncing card to Customer360",
                kv("attempt", attemptNumber + 1),
                kv("maxAttempts", maxRetries + 1));

        return Mono.fromCallable(() -> System.nanoTime())
                .flatMap(startTime -> webClient.post()
                        .uri(profile360Url)
                        .bodyValue(card)
                        .retrieve()
                        .toBodilessEntity()
                        .then(Mono.fromRunnable(() -> {
                            long duration = System.nanoTime() - startTime;
                            syncTimer.record(Duration.ofNanos(duration));
                            successCounter.increment();

                            log.info("Successfully synced card to Customer360",
                                    kv("durationMs", duration / 1_000_000));

                            card.setLastSyncAttempt(LocalDateTime.now());
                            cardRepository.save(card);
                        }))
                        .thenReturn(true))
                .onErrorResume(error -> {
                    failureCounter.increment();

                    log.error("Failed to sync card to Customer360",
                            kv("attempt", attemptNumber + 1),
                            kv("maxAttempts", maxRetries + 1),
                            kv("errorType", error.getClass().getSimpleName()),
                            kv("errorMessage", error.getMessage()));

                    if (attemptNumber < maxRetries) {
                        long delayMs = initialDelayMs * (long) Math.pow(2, attemptNumber);

                        log.info("Will retry syncing card",
                                kv("delayMs", delayMs),
                                kv("nextAttempt", attemptNumber + 2));

                        // Non-blocking delay using Mono.delay()
                        return Mono.delay(Duration.ofMillis(delayMs))
                                .flatMap(tick -> syncToC360Internal(card, attemptNumber + 1));
                    } else {
                        log.error("Max retries exhausted for card. Pushing to retry queue.");
                        pushToRetryQueue(card);
                        return Mono.just(false);
                    }
                });
    }

    /**
     * Fallback method for Circuit Breaker.
     * When Circuit Breaker is OPEN, this method is called immediately.
     * We push to retry queue directly.
     */
    private Mono<Boolean> syncFallback(Card card, int attemptNumber, Throwable t) {
        setupMDC(card);

        log.warn("Circuit Breaker is OPEN or fallback triggered. Pushing to retry queue directly.",
                kv("errorType", t.getClass().getSimpleName()),
                kv("errorMessage", t.getMessage()));

        pushToRetryQueue(card);
        return Mono.just(false);
    }

    private void pushToRetryQueue(Card card) {
        try {
            CardEventDto eventDto = mapCardToDto(card);
            String message = objectMapper.writeValueAsString(eventDto);
            kafkaTemplate.send(retryTopic, card.getTokenRef(), message);

            log.info("Pushed card to retry topic",
                    kv("topic", retryTopic));
        } catch (Exception e) {
            log.error("Failed to push card to retry queue. Pushing to DLQ.",
                    kv("errorType", e.getClass().getSimpleName()),
                    kv("errorMessage", e.getMessage()));
            pushToDLQ(card, e);
        }
    }

    public void pushToDLQ(Card card, Exception originalError) {
        try {
            dlqPushCounter.increment();

            CardEventDto eventDto = mapCardToDto(card);
            String message = objectMapper.writeValueAsString(eventDto);
            kafkaTemplate.send(dlqTopic, card.getTokenRef(), message);

            log.error("Pushed card to DLQ",
                    kv("topic", dlqTopic),
                    kv("originalError", originalError.getMessage()));
        } catch (Exception e) {
            log.error("CRITICAL: Failed to push card to DLQ. Manual intervention required.",
                    kv("tokenRef", card.getTokenRef()),
                    kv("errorType", e.getClass().getSimpleName()),
                    kv("errorMessage", e.getMessage()));
        }
    }

    private CardEventDto mapCardToDto(Card card) {
        CardEventDto dto = new CardEventDto();
        dto.setTokenRef(card.getTokenRef());
        dto.setMaskedCardNumber(card.getMaskedCardNumber());
        dto.setLast4(card.getLast4());
        dto.setProgramCode(card.getProgramCode());
        dto.setProgramCategory(card.getProgramCategory());
        dto.setNetwork(card.getNetwork());
        dto.setBin(card.getBin());
        dto.setLifecycleStatus(card.getLifecycleStatus());
        dto.setRawStatus(card.getRawStatus());
        dto.setCustomerMobileNumber(card.getCustomerMobileNumber());
        dto.setCustId(card.getCustId());
        dto.setAccountNo(card.getAccountNo());
        dto.setIssuedBySystem(card.getIssuedBySystem());
        dto.setIssuanceChannel(card.getIssuanceChannel());

        if (card.getEventTimestamp() != null) {
            dto.setEventTimestamp(card.getEventTimestamp().format(DateTimeFormatter.ISO_DATE_TIME));
        }

        return dto;
    }

    private void setupMDC(Card card) {
        MDC.put("tokenRef", card.getTokenRef());
        MDC.put("cardId", String.valueOf(card.getId()));

        // Add trace context if available
        if (tracer != null && tracer.currentSpan() != null) {
            MDC.put("traceId", tracer.currentSpan().context().traceId());
            MDC.put("spanId", tracer.currentSpan().context().spanId());
        }
    }

    // Helper method for structured logging
    private static org.slf4j.event.KeyValuePair kv(String key, Object value) {
        return new org.slf4j.event.KeyValuePair(key, value);
    }
}
