package com.example.cardrepo.consumer;

import com.example.cardrepo.dto.CardEventDto;
import com.example.cardrepo.entity.Card;
import com.example.cardrepo.repository.CardRepository;
import com.example.cardrepo.service.C360SyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardEventConsumerTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private C360SyncService c360SyncService;

    @InjectMocks
    private CardEventConsumer cardEventConsumer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void consume_ValidMessage_ShouldSaveAndSync() throws Exception {
        // Arrange
        CardEventDto eventDto = new CardEventDto();
        eventDto.setTokenRef("tok_123");
        eventDto.setMaskedCardNumber("4111xxxx1111");
        eventDto.setLast4("1111");
        eventDto.setLifecycleStatus("ACTIVE");
        
        String message = objectMapper.writeValueAsString(eventDto);

        when(cardRepository.findByTokenRef("tok_123")).thenReturn(Optional.empty());
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArguments()[0]);
        when(c360SyncService.syncToC360(any(Card.class))).thenReturn(CompletableFuture.completedFuture(true));

        // Act
        cardEventConsumer.consume(message);

        // Assert
        verify(cardRepository).save(any(Card.class));
        verify(c360SyncService).syncToC360(any(Card.class));
    }
}
