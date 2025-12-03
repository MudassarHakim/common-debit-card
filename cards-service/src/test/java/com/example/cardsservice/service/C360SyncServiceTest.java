package com.example.cardsservice.service;

import com.example.cardsservice.dto.CardEventDto;
import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class C360SyncServiceTest {

    private MockWebServer mockWebServer;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private C360SyncService c360SyncService;
    private Card testCard;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        // Create mock Tracer and MeterRegistry
        io.micrometer.tracing.Tracer tracer = mock(io.micrometer.tracing.Tracer.class);
        io.micrometer.core.instrument.MeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

        c360SyncService = new C360SyncService(webClient, cardRepository, kafkaTemplate, objectMapper, tracer,
                meterRegistry);

        ReflectionTestUtils.setField(c360SyncService, "profile360Url", mockWebServer.url("/").toString());
        ReflectionTestUtils.setField(c360SyncService, "retryTopic", "card-events-retry");
        ReflectionTestUtils.setField(c360SyncService, "maxRetries", 3);
        ReflectionTestUtils.setField(c360SyncService, "initialDelayMs", 10L); // Short delay for tests

        testCard = new Card();
        testCard.setId(1L);
        testCard.setTokenRef("tok_test_123");
        testCard.setMaskedCardNumber("4111xxxx1111");
        testCard.setLast4("1111");
        testCard.setLifecycleStatus("ACTIVE");
        testCard.setEventTimestamp(LocalDateTime.now());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void syncToC360_Success_ShouldReturnTrue() throws ExecutionException, InterruptedException {
        // Arrange
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // Act
        CompletableFuture<Boolean> result = c360SyncService.syncToC360(testCard);

        // Assert
        assertTrue(result.get());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());

        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository, times(1)).save(cardCaptor.capture());
        assertNotNull(cardCaptor.getValue().getLastSyncAttempt());
    }

    @Test
    void syncToC360_RetryThenSuccess_ShouldReturnTrue() throws ExecutionException, InterruptedException {
        // Arrange - Fail twice, then succeed
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // Act
        CompletableFuture<Boolean> result = c360SyncService.syncToC360(testCard);

        // Assert
        assertTrue(result.get());
        assertEquals(3, mockWebServer.getRequestCount());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(cardRepository, times(1)).save(any(Card.class));
    }

    @Test
    void syncToC360_AllRetriesFail_ShouldPushToRetryQueue()
            throws ExecutionException, InterruptedException, JsonProcessingException {
        // Arrange - All attempts fail
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        when(objectMapper.writeValueAsString(any(CardEventDto.class))).thenReturn("{\"tokenRef\":\"tok_test_123\"}");

        // Act
        CompletableFuture<Boolean> result = c360SyncService.syncToC360(testCard);

        // Assert
        assertFalse(result.get());
        // Should be called 4 times: 1 initial + 3 retries
        assertEquals(4, mockWebServer.getRequestCount());

        verify(kafkaTemplate, times(1)).send(eq("card-events-retry"), eq("tok_test_123"), anyString());
        verify(cardRepository, never()).save(any(Card.class));
    }
}
