package com.example.cardsservice.cucumber.steps;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = com.example.cardsservice.CardsServiceApplication.class)
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {
}
