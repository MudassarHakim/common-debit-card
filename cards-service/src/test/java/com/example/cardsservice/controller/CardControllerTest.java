package com.example.cardsservice.controller;

import com.example.cardsservice.service.CardIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CardIntegrationService cardIntegrationService;

    @Test
    void getCards_ShouldReturnList() throws Exception {
        when(cardIntegrationService.getCards(anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/cards")
                .header("X-Mobile-Number", "1234567890")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getEligibleCards_ShouldReturnList() throws Exception {
        mockMvc.perform(get("/cards/eligible-cards")
                .header("X-Mobile-Number", "1234567890")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
