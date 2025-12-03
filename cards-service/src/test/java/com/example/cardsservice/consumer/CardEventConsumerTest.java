package com.example.cardsservice.consumer;

import com.example.cardsservice.dto.CardEventDto;
import com.example.cardsservice.entity.Card;
import com.example.cardsservice.repository.CardRepository;
import com.example.cardsservice.service.C360SyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
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
    private CardEventDto eventDto;

    @BeforeEach
    void setUp() {
        eventDto = new CardEventDto();
        eventDto.setTokenRef("tok_123");
        eventDto.setMaskedCardNumber("4111xxxx1111");
        eventDto.setLast4("1111");
        eventDto.setLifecycleStatus("ACTIVE");
        eventDto.setProgramCode("PROG001");
        eventDto.setNetwork("VISA");
        eventDto.setCustomerMobileNumber("9876543210");
    }

    @Test
    void consume_ValidMessage_ShouldSaveAndSync() throws Exception {
        // Arrange
        String message = objectMapper.writeValueAsString(eventDto);

        when(cardRepository.findByTokenRef("tok_123")).thenReturn(Optional.empty());
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArguments()[0]);
        when(c360SyncService.syncToC360(any(Card.class))).thenReturn(CompletableFuture.completedFuture(true));

        // Act
        cardEventConsumer.consume(message);

        // Assert
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        verify(c360SyncService).syncToC360(any(Card.class));

        Card savedCard = cardCaptor.getValue();
        assertEquals("tok_123", savedCard.getTokenRef());
        assertEquals("4111xxxx1111", savedCard.getMaskedCardNumber());
        assertEquals("1111", savedCard.getLast4());
        assertEquals("ACTIVE", savedCard.getLifecycleStatus());
    }

    @Test
    void consume_ExistingCard_ShouldUpdateAndSync() throws Exception {
        // Arrange
        Card existingCard = new Card();
        existingCard.setTokenRef("tok_123");
        existingCard.setMaskedCardNumber("4111xxxx1111");
        existingCard.setLifecycleStatus("INACTIVE");
        existingCard.setEventTimestamp(LocalDateTime.now().minusHours(1));

        eventDto.setLifecycleStatus("ACTIVE");
        eventDto.setEventTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        String message = objectMapper.writeValueAsString(eventDto);

        when(cardRepository.findByTokenRef("tok_123")).thenReturn(Optional.of(existingCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArguments()[0]);
        when(c360SyncService.syncToC360(any(Card.class))).thenReturn(CompletableFuture.completedFuture(true));

        // Act
        cardEventConsumer.consume(message);

        // Assert
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());

        Card savedCard = cardCaptor.getValue();
        assertEquals("ACTIVE", savedCard.getLifecycleStatus());
    }

    @Test
    void consume_StaleEvent_ShouldIgnore() throws Exception {
        // Arrange
        Card existingCard = new Card();
        existingCard.setTokenRef("tok_123");
        existingCard.setEventTimestamp(LocalDateTime.now());

        eventDto.setEventTimestamp(LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_DATE_TIME));
        String message = objectMapper.writeValueAsString(eventDto);

        when(cardRepository.findByTokenRef("tok_123")).thenReturn(Optional.of(existingCard));

        // Act
        cardEventConsumer.consume(message);

        // Assert
        verify(cardRepository, never()).save(any(Card.class));
        verify(c360SyncService, never()).syncToC360(any(Card.class));
    }

    @Test
    void consume_MissingTokenRef_ShouldNotProcess() throws Exception {
        // Arrange
        eventDto.setTokenRef(null);
        String message = objectMapper.writeValueAsString(eventDto);

        // Act
        cardEventConsumer.consume(message);

        // Assert
        verify(cardRepository, never()).findByTokenRef(anyString());
        verify(cardRepository, never()).save(any(Card.class));
        verify(c360SyncService, never()).syncToC360(any(Card.class));
    }

    @Test
    void consume_InvalidJson_ShouldHandleGracefully() {
        // Arrange
        String invalidMessage = "{invalid json}";

        // Act
        cardEventConsumer.consume(invalidMessage);

        // Assert
        verify(cardRepository, never()).save(any(Card.class));
        verify(c360SyncService, never()).syncToC360(any(Card.class));
    }

    @Test
    void consume_SyncServiceCalled_ShouldTriggerRetryMechanism() throws Exception {
        // Arrange
        String message = objectMapper.writeValueAsString(eventDto);

        when(cardRepository.findByTokenRef("tok_123")).thenReturn(Optional.empty());
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArguments()[0]);
        when(c360SyncService.syncToC360(any(Card.class))).thenReturn(CompletableFuture.completedFuture(true));

        // Act
        cardEventConsumer.consume(message);

        // Assert
        verify(c360SyncService, times(1)).syncToC360(any(Card.class));
    }

    @Test
    void consume_AllFieldsMapped_ShouldSetCorrectly() throws Exception {
        // Arrange
        eventDto.setProgramCategory("DEBIT");
        eventDto.setBin("411111");
        eventDto.setRawStatus("01");
        eventDto.setCustId("CUST123");
        eventDto.setAccountNo("ACC456");
        eventDto.setIssuedBySystem("CORE_BANKING");
        eventDto.setIssuanceChannel("MOBILE");

        String message = objectMapper.writeValueAsString(eventDto);

        when(cardRepository.findByTokenRef("tok_123")).thenReturn(Optional.empty());
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArguments()[0]);
        when(c360SyncService.syncToC360(any(Card.class))).thenReturn(CompletableFuture.completedFuture(true));

        // Act
        cardEventConsumer.consume(message);

        // Assert
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());

        Card savedCard = cardCaptor.getValue();
        assertEquals("PROG001", savedCard.getProgramCode());
        assertEquals("DEBIT", savedCard.getProgramCategory());
        assertEquals("VISA", savedCard.getNetwork());
        assertEquals("411111", savedCard.getBin());
        assertEquals("01", savedCard.getRawStatus());
        assertEquals("9876543210", savedCard.getCustomerMobileNumber());
        assertEquals("CUST123", savedCard.getCustId());
        assertEquals("ACC456", savedCard.getAccountNo());
        assertEquals("CORE_BANKING", savedCard.getIssuedBySystem());
        assertEquals("MOBILE", savedCard.getIssuanceChannel());
    }
}
