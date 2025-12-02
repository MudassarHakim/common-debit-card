package com.example.cardrepo.cucumber.steps;

import com.example.cardrepo.CardRepoApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = CardRepoApplication.class)
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {
}
