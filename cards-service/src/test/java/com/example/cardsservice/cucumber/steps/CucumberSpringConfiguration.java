package com.example.cardsservice.cucumber.steps;

import com.example.cardsservice.CardsServiceApplication;
import com.example.cardsservice.service.CardIntegrationService;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;

@CucumberContextConfiguration
@SpringBootTest(classes = CardsServiceApplication.class)
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {

    @MockBean
    public CardIntegrationService cardIntegrationService;
}
