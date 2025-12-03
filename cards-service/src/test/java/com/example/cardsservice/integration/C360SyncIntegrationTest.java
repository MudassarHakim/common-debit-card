package com.example.cardsservice.integration;

import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import com.example.cardsservice.service.C360SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for C360 Sync functionality
 * Tests the complete flow from REST API to database
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class C360SyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardRepository cardRepository;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        cardRepository.deleteAll();
    }

    @Test
    void manualSync_EndToEnd_ShouldWorkCorrectly() throws Exception {
        // Arrange - Create a card with pending sync
        Card card = new Card();
        card.setTokenRef("tok_integration_test");
        card.setMaskedCardNumber("4111xxxx1111");
        card.setLast4("1111");
        card.setLifecycleStatus("ACTIVE");
        card.setSyncPending(true);
        card.setSyncRetryCount(2);
        card.setLastSyncAttempt(LocalDateTime.now().minusMinutes(10));

        cardRepository.save(card);

        // Mock successful C360 response
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        // Act & Assert
        mockMvc.perform(post("/api/cards/sync/manual/tok_integration_test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenRef").value("tok_integration_test"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.syncPending").value(false))
                .andExpect(jsonPath("$.retryCount").value(0));

        // Verify database state
        Card updatedCard = cardRepository.findByTokenRef("tok_integration_test").orElseThrow();
        assert !updatedCard.isSyncPending();
        assert updatedCard.getSyncRetryCount() == 0;
    }

    @Test
    void getPendingSyncCards_ShouldReturnOnlyPendingCards() throws Exception {
        // Arrange - Create cards with different sync states
        Card pendingCard1 = createCard("tok_pending_1", true, 1);
        Card pendingCard2 = createCard("tok_pending_2", true, 2);
        Card syncedCard = createCard("tok_synced", false, 0);

        cardRepository.save(pendingCard1);
        cardRepository.save(pendingCard2);
        cardRepository.save(syncedCard);

        // Act & Assert
        mockMvc.perform(get("/api/cards/sync/pending")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.cards[*].tokenRef",
                        containsInAnyOrder("tok_pending_1", "tok_pending_2")));
    }

    @Test
    void getSyncStats_ShouldCalculateCorrectly() throws Exception {
        // Arrange - Create 10 cards, 3 with pending sync
        for (int i = 0; i < 7; i++) {
            cardRepository.save(createCard("tok_synced_" + i, false, 0));
        }
        for (int i = 0; i < 3; i++) {
            cardRepository.save(createCard("tok_pending_" + i, true, 2));
        }

        // Act & Assert
        mockMvc.perform(get("/api/cards/sync/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(10))
                .andExpect(jsonPath("$.pendingSyncCards").value(3))
                .andExpect(jsonPath("$.syncedCards").value(7))
                .andExpect(jsonPath("$.syncSuccessRate").value(70.0));
    }

    @Test
    void manualSyncAll_ShouldProcessMultipleCards() throws Exception {
        // Arrange - Create multiple pending cards
        cardRepository.save(createCard("tok_bulk_1", true, 1));
        cardRepository.save(createCard("tok_bulk_2", true, 2));
        cardRepository.save(createCard("tok_bulk_3", true, 1));

        // Mock successful C360 response
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        // Act & Assert
        mockMvc.perform(post("/api/cards/sync/manual/all")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(3))
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(0));

        // Verify all cards are now synced
        long pendingCount = cardRepository.countBySyncPending(true);
        assert pendingCount == 0;
    }

    @Test
    void manualSync_WhenC360Fails_ShouldUpdateRetryCount() throws Exception {
        // Arrange
        Card card = createCard("tok_fail_test", true, 1);
        cardRepository.save(card);

        // Mock failed C360 response
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("C360 service unavailable"));

        // Act & Assert
        mockMvc.perform(post("/api/cards/sync/manual/tok_fail_test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.syncPending").value(true));

        // Verify retry count was updated
        Card updatedCard = cardRepository.findByTokenRef("tok_fail_test").orElseThrow();
        assert updatedCard.isSyncPending();
        assert updatedCard.getSyncRetryCount() == 1; // Reset to 1 on manual sync failure
    }

    @Test
    void pagination_ShouldWorkCorrectly() throws Exception {
        // Arrange - Create 25 pending cards
        for (int i = 0; i < 25; i++) {
            cardRepository.save(createCard("tok_page_" + i, true, 1));
        }

        // Act & Assert - First page
        mockMvc.perform(get("/api/cards/sync/pending")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards", hasSize(10)))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.currentPage").value(0));

        // Act & Assert - Second page
        mockMvc.perform(get("/api/cards/sync/pending")
                .param("page", "1")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards", hasSize(10)))
                .andExpect(jsonPath("$.currentPage").value(1));

        // Act & Assert - Last page
        mockMvc.perform(get("/api/cards/sync/pending")
                .param("page", "2")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards", hasSize(5)))
                .andExpect(jsonPath("$.currentPage").value(2));
    }

    private Card createCard(String tokenRef, boolean syncPending, int retryCount) {
        Card card = new Card();
        card.setTokenRef(tokenRef);
        card.setMaskedCardNumber("4111xxxx1111");
        card.setLast4("1111");
        card.setLifecycleStatus("ACTIVE");
        card.setSyncPending(syncPending);
        card.setSyncRetryCount(retryCount);
        if (syncPending) {
            card.setLastSyncAttempt(LocalDateTime.now().minusMinutes(10));
        }
        return card;
    }
}
