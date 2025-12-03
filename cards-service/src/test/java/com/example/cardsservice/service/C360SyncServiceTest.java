package com.example.cardsservice.service;

import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class C360SyncServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CardRepository cardRepository;

    @InjectMocks
    private C360SyncService c360SyncService;

    private Card testCard;

    @BeforeEach
    void setUp() {
        // Set configuration values using reflection
        ReflectionTestUtils.setField(c360SyncService, "profile360Url", "http://test-c360.com/api");
        ReflectionTestUtils.setField(c360SyncService, "maxRetries", 3);
        ReflectionTestUtils.setField(c360SyncService, "initialDelayMs", 100L); // Reduced for testing

        testCard = new Card();
        testCard.setId(1L);
        testCard.setTokenRef("tok_test_123");
        testCard.setMaskedCardNumber("4111xxxx1111");
        testCard.setLast4("1111");
        testCard.setLifecycleStatus("ACTIVE");
        testCard.setSyncPending(false);
        testCard.setSyncRetryCount(0);
    }

    @Test
    void syncToC360WithRetry_Success_ShouldReturnTrue() throws ExecutionException, InterruptedException {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // Act
        CompletableFuture<Boolean> result = c360SyncService.syncToC360WithRetry(testCard);

        // Assert
        assertTrue(result.get());
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(Void.class));

        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository, atLeastOnce()).save(cardCaptor.capture());

        Card savedCard = cardCaptor.getValue();
        assertFalse(savedCard.isSyncPending());
        assertEquals(0, savedCard.getSyncRetryCount());
        assertNotNull(savedCard.getLastSyncAttempt());
    }

    @Test
    void syncToC360WithRetry_FailureWithRetries_ShouldEventuallySucceed()
            throws ExecutionException, InterruptedException {
        // Arrange - Fail twice, then succeed
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RestClientException("Connection timeout"))
                .thenThrow(new RestClientException("Connection timeout"))
                .thenReturn(ResponseEntity.ok().build());

        when(cardRepository.save(any(Card.class))).thenReturn(testCard);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));

        // Act
        CompletableFuture<Boolean> result = c360SyncService.syncToC360WithRetry(testCard);

        // Assert
        assertTrue(result.get());
        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void syncToC360WithRetry_AllRetriesFail_ShouldReturnFalse() throws ExecutionException, InterruptedException {
        // Arrange - All attempts fail
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RestClientException("Service unavailable"));

        when(cardRepository.save(any(Card.class))).thenReturn(testCard);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));

        // Act
        CompletableFuture<Boolean> result = c360SyncService.syncToC360WithRetry(testCard);

        // Assert
        assertFalse(result.get());
        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(Void.class));

        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository, atLeastOnce()).save(cardCaptor.capture());

        Card savedCard = cardCaptor.getValue();
        assertTrue(savedCard.isSyncPending());
        assertEquals(3, savedCard.getSyncRetryCount());
    }

    @Test
    void manualSyncToC360_Success_ShouldResetRetryCount() {
        // Arrange
        testCard.setSyncPending(true);
        testCard.setSyncRetryCount(3);

        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // Act
        boolean result = c360SyncService.manualSyncToC360(testCard);

        // Assert
        assertTrue(result);

        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository, times(1)).save(cardCaptor.capture());

        Card savedCard = cardCaptor.getValue();
        assertFalse(savedCard.isSyncPending());
        assertEquals(0, savedCard.getSyncRetryCount());
        assertNotNull(savedCard.getLastSyncAttempt());
    }

    @Test
    void manualSyncToC360_Failure_ShouldSetRetryCountToOne() {
        // Arrange
        testCard.setSyncPending(true);
        testCard.setSyncRetryCount(3);

        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RestClientException("Service unavailable"));
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // Act
        boolean result = c360SyncService.manualSyncToC360(testCard);

        // Assert
        assertFalse(result);

        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository, times(1)).save(cardCaptor.capture());

        Card savedCard = cardCaptor.getValue();
        assertTrue(savedCard.isSyncPending());
        assertEquals(1, savedCard.getSyncRetryCount());
    }

    @Test
    void syncToC360WithRetry_ShouldUpdateLastSyncAttempt() throws ExecutionException, InterruptedException {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // Act
        CompletableFuture<Boolean> result = c360SyncService.syncToC360WithRetry(testCard);

        // Assert
        assertTrue(result.get());
        assertNotNull(testCard.getLastSyncAttempt());
    }
}
