package com.example.cardsservice.controller;

import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import com.example.cardsservice.service.C360SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(C360SyncController.class)
class C360SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CardRepository cardRepository;

    @MockBean
    private C360SyncService c360SyncService;

    private Card testCard;

    @BeforeEach
    void setUp() {
        testCard = new Card();
        testCard.setId(1L);
        testCard.setTokenRef("tok_test_123");
        testCard.setMaskedCardNumber("4111xxxx1111");
        testCard.setLast4("1111");
        testCard.setLifecycleStatus("ACTIVE");
        testCard.setSyncPending(true);
        testCard.setSyncRetryCount(2);
        testCard.setLastSyncAttempt(LocalDateTime.now());
    }

    @Test
    void manualSyncCard_Success_ShouldReturnOk() throws Exception {
        // Arrange
        when(cardRepository.findByTokenRef("tok_test_123")).thenReturn(Optional.of(testCard));
        when(c360SyncService.syncToC360(any(Card.class))).thenReturn(CompletableFuture.completedFuture(true));
        testCard.setSyncPending(false);
        testCard.setSyncRetryCount(0);

        // Act & Assert
        mockMvc.perform(post("/api/cards/sync/manual/tok_test_123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tokenRef").value("tok_test_123"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.syncPending").value(false))
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.lastSyncAttempt").exists());
    }

    @Test
    void manualSyncCard_Failure_ShouldReturnOkWithFailureStatus() throws Exception {
        // Arrange
        when(cardRepository.findByTokenRef("tok_test_123")).thenReturn(Optional.of(testCard));
        when(c360SyncService.syncToC360(any(Card.class))).thenReturn(CompletableFuture.completedFuture(false));

        // Act & Assert
        mockMvc.perform(post("/api/cards/sync/manual/tok_test_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenRef").value("tok_test_123"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.syncPending").value(true));
    }

    @Test
    void manualSyncCard_CardNotFound_ShouldReturnError() throws Exception {
        // Arrange
        when(cardRepository.findByTokenRef("tok_not_found")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(post("/api/cards/sync/manual/tok_not_found"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void getPendingSyncCards_ShouldReturnPagedResults() throws Exception {
        // Arrange
        Card card1 = new Card();
        card1.setTokenRef("tok_1");
        card1.setSyncPending(true);

        Card card2 = new Card();
        card2.setTokenRef("tok_2");
        card2.setSyncPending(true);

        List<Card> cards = Arrays.asList(card1, card2);
        Page<Card> page = new PageImpl<>(cards, PageRequest.of(0, 50), 2);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/cards/sync/pending")
                .param("page", "0")
                .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.currentPage").value(0));
    }

    @Test
    void manualSyncAllPending_ShouldProcessMultipleCards() throws Exception {
        // Arrange
        Card card1 = new Card();
        card1.setTokenRef("tok_1");
        card1.setSyncPending(true);

        Card card2 = new Card();
        card2.setTokenRef("tok_2");
        card2.setSyncPending(true);

        Card card3 = new Card();
        card3.setTokenRef("tok_3");
        card3.setSyncPending(true);

        List<Card> cards = Arrays.asList(card1, card2, card3);
        Page<Card> page = new PageImpl<>(cards, PageRequest.of(0, 100), 3);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);
        when(c360SyncService.syncToC360(card1)).thenReturn(CompletableFuture.completedFuture(true));
        when(c360SyncService.syncToC360(card2)).thenReturn(CompletableFuture.completedFuture(true));
        when(c360SyncService.syncToC360(card3)).thenReturn(CompletableFuture.completedFuture(false));

        // Act & Assert
        mockMvc.perform(post("/api/cards/sync/manual/all")
                .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(3))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(1));
    }

    @Test
    void getSyncStats_ShouldReturnStatistics() throws Exception {
        // Arrange
        when(cardRepository.count()).thenReturn(1000L);
        when(cardRepository.countBySyncPending(true)).thenReturn(10L);

        // Act & Assert
        mockMvc.perform(get("/api/cards/sync/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(1000))
                .andExpect(jsonPath("$.pendingSyncCards").value(10))
                .andExpect(jsonPath("$.syncedCards").value(990))
                .andExpect(jsonPath("$.syncSuccessRate").value(99.0));
    }

    @Test
    void getSyncStats_NoCards_ShouldReturnZeroSuccessRate() throws Exception {
        // Arrange
        when(cardRepository.count()).thenReturn(0L);
        when(cardRepository.countBySyncPending(true)).thenReturn(0L);

        // Act & Assert
        mockMvc.perform(get("/api/cards/sync/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(0))
                .andExpect(jsonPath("$.pendingSyncCards").value(0))
                .andExpect(jsonPath("$.syncedCards").value(0))
                .andExpect(jsonPath("$.syncSuccessRate").value(0));
    }

    @Test
    void getPendingSyncCards_WithCustomPagination_ShouldRespectParameters() throws Exception {
        // Arrange
        List<Card> cards = Arrays.asList(testCard);
        Page<Card> page = new PageImpl<>(cards, PageRequest.of(0, 10), 1);

        when(cardRepository.findBySyncPending(eq(true), any(PageRequest.class))).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/cards/sync/pending")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }
}
