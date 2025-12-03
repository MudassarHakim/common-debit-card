package com.example.cardsservice.scheduler;

import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import com.example.cardsservice.service.C360SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class C360SyncRetrySchedulerTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private C360SyncService c360SyncService;

    @InjectMocks
    private C360SyncRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);
        ReflectionTestUtils.setField(scheduler, "batchSize", 50);
        ReflectionTestUtils.setField(scheduler, "maxRetries", 3);
    }

    @Test
    void retryFailedSyncs_NoPendingCards_ShouldDoNothing() {
        // Arrange
        Page<Card> emptyPage = new PageImpl<>(Collections.emptyList());
        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(emptyPage);

        // Act
        scheduler.retryFailedSyncs();

        // Assert
        verify(cardRepository, times(1)).findBySyncPending(eq(true), any(PageRequest.class));
        verify(c360SyncService, never()).manualSyncToC360(any(Card.class));
    }

    @Test
    void retryFailedSyncs_SchedulerDisabled_ShouldNotProcess() {
        // Arrange
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);

        // Act
        scheduler.retryFailedSyncs();

        // Assert
        verify(cardRepository, never()).findBySyncPending(anyBoolean(), any(PageRequest.class));
        verify(c360SyncService, never()).manualSyncToC360(any(Card.class));
    }

    @Test
    void retryFailedSyncs_WithPendingCards_ShouldProcessThem() {
        // Arrange
        Card card1 = createCard("tok_1", 1);
        Card card2 = createCard("tok_2", 2);

        List<Card> cards = Arrays.asList(card1, card2);
        Page<Card> page = new PageImpl<>(cards);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);
        when(c360SyncService.manualSyncToC360(card1)).thenReturn(true);
        when(c360SyncService.manualSyncToC360(card2)).thenReturn(true);

        // Act
        scheduler.retryFailedSyncs();

        // Assert
        verify(c360SyncService, times(1)).manualSyncToC360(card1);
        verify(c360SyncService, times(1)).manualSyncToC360(card2);
    }

    @Test
    void retryFailedSyncs_CardExceededMaxRetries_ShouldSkip() {
        // Arrange
        Card card1 = createCard("tok_1", 3); // Already at max retries
        Card card2 = createCard("tok_2", 1);

        List<Card> cards = Arrays.asList(card1, card2);
        Page<Card> page = new PageImpl<>(cards);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);
        when(c360SyncService.manualSyncToC360(card2)).thenReturn(true);

        // Act
        scheduler.retryFailedSyncs();

        // Assert
        verify(c360SyncService, never()).manualSyncToC360(card1); // Should skip
        verify(c360SyncService, times(1)).manualSyncToC360(card2); // Should process
    }

    @Test
    void retryFailedSyncs_CardAttemptedRecently_ShouldSkip() {
        // Arrange
        Card card1 = createCard("tok_1", 1);
        card1.setLastSyncAttempt(LocalDateTime.now().minusMinutes(2)); // Attempted 2 minutes ago

        Card card2 = createCard("tok_2", 1);
        card2.setLastSyncAttempt(LocalDateTime.now().minusMinutes(10)); // Attempted 10 minutes ago

        List<Card> cards = Arrays.asList(card1, card2);
        Page<Card> page = new PageImpl<>(cards);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);
        when(c360SyncService.manualSyncToC360(card2)).thenReturn(true);

        // Act
        scheduler.retryFailedSyncs();

        // Assert
        verify(c360SyncService, never()).manualSyncToC360(card1); // Should skip (too recent)
        verify(c360SyncService, times(1)).manualSyncToC360(card2); // Should process
    }

    @Test
    void retryFailedSyncs_MixedResults_ShouldProcessAll() {
        // Arrange
        Card card1 = createCard("tok_1", 1);
        Card card2 = createCard("tok_2", 1);
        Card card3 = createCard("tok_3", 1);

        List<Card> cards = Arrays.asList(card1, card2, card3);
        Page<Card> page = new PageImpl<>(cards);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);
        when(c360SyncService.manualSyncToC360(card1)).thenReturn(true);
        when(c360SyncService.manualSyncToC360(card2)).thenReturn(false);
        when(c360SyncService.manualSyncToC360(card3)).thenReturn(true);

        // Act
        scheduler.retryFailedSyncs();

        // Assert
        verify(c360SyncService, times(1)).manualSyncToC360(card1);
        verify(c360SyncService, times(1)).manualSyncToC360(card2);
        verify(c360SyncService, times(1)).manualSyncToC360(card3);
    }

    @Test
    void retryFailedSyncs_ExceptionDuringSync_ShouldContinue() {
        // Arrange
        Card card1 = createCard("tok_1", 1);
        Card card2 = createCard("tok_2", 1);

        List<Card> cards = Arrays.asList(card1, card2);
        Page<Card> page = new PageImpl<>(cards);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);
        when(c360SyncService.manualSyncToC360(card1)).thenThrow(new RuntimeException("Unexpected error"));
        when(c360SyncService.manualSyncToC360(card2)).thenReturn(true);

        // Act
        scheduler.retryFailedSyncs();

        // Assert - Should still try to process card2 despite card1 throwing exception
        verify(c360SyncService, times(1)).manualSyncToC360(card1);
        verify(c360SyncService, times(1)).manualSyncToC360(card2);
    }

    @Test
    void retryFailedSyncs_RespectsBatchSize() {
        // Arrange
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);

        List<Card> cards = Arrays.asList(createCard("tok_1", 1));
        Page<Card> page = new PageImpl<>(cards);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);
        when(c360SyncService.manualSyncToC360(any(Card.class))).thenReturn(true);

        // Act
        scheduler.retryFailedSyncs();

        // Assert
        verify(cardRepository).findBySyncPending(eq(true), eq(PageRequest.of(0, 10)));
    }

    @Test
    void retryFailedSyncs_CardWithNullLastAttempt_ShouldProcess() {
        // Arrange
        Card card = createCard("tok_1", 1);
        card.setLastSyncAttempt(null); // No previous attempt

        List<Card> cards = Arrays.asList(card);
        Page<Card> page = new PageImpl<>(cards);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);
        when(c360SyncService.manualSyncToC360(card)).thenReturn(true);

        // Act
        scheduler.retryFailedSyncs();

        // Assert
        verify(c360SyncService, times(1)).manualSyncToC360(card);
    }

    private Card createCard(String tokenRef, int retryCount) {
        Card card = new Card();
        card.setTokenRef(tokenRef);
        card.setMaskedCardNumber("4111xxxx1111");
        card.setLast4("1111");
        card.setLifecycleStatus("ACTIVE");
        card.setSyncPending(true);
        card.setSyncRetryCount(retryCount);
        card.setLastSyncAttempt(LocalDateTime.now().minusMinutes(10));
        return card;
    }
}
