package com.example.cardsservice.cucumber.steps;

import com.example.cardsservice.service.CardIntegrationService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class CardsServiceSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardIntegrationService cardIntegrationService;

    private ResultActions resultActions;

    @Given("the integration service returns cards for mobile number {string}")
    public void the_integration_service_returns_cards_for_mobile_number(String mobileNumber) {
        given(cardIntegrationService.getCards(mobileNumber))
                .willReturn(List.of(new Object(), new Object())); // Mocking 2 objects
    }

    @When("I request cards with mobile number {string}")
    public void i_request_cards_with_mobile_number(String mobileNumber) throws Exception {
        resultActions = mockMvc.perform(get("/cards")
                .header("X-Mobile-Number", mobileNumber));
    }

    @Then("I should receive a successful response")
    public void i_should_receive_a_successful_response() throws Exception {
        resultActions.andExpect(status().isOk());
    }

    @Then("the response should contain the cards")
    public void the_response_should_contain_the_cards() throws Exception {
        resultActions.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }
}
