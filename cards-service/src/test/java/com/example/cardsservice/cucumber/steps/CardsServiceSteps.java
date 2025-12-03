package com.example.cardsservice.cucumber.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class CardsServiceSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.example.cardsservice.repository.CardRepository cardRepository;

    private ResultActions resultActions;

    @Given("the integration service returns cards for mobile number {string}")
    public void the_integration_service_returns_cards_for_mobile_number(String mobileNumber) {
        // Clean up and create test data
        String tokenRef1 = "tok_" + mobileNumber + "_1";
        String tokenRef2 = "tok_" + mobileNumber + "_2";

        if (!cardRepository.findByTokenRef(tokenRef1).isPresent()) {
            com.example.cardsservice.entity.Card card1 = new com.example.cardsservice.entity.Card();
            card1.setCustomerMobileNumber(mobileNumber);
            card1.setTokenRef(tokenRef1);
            card1.setMaskedCardNumber("XXXX-XXXX-XXXX-1111");
            card1.setLast4("1111");
            card1.setProgramCode("DEBIT");
            card1.setLifecycleStatus("ACTIVE");
            cardRepository.save(card1);
        }

        if (!cardRepository.findByTokenRef(tokenRef2).isPresent()) {
            com.example.cardsservice.entity.Card card2 = new com.example.cardsservice.entity.Card();
            card2.setCustomerMobileNumber(mobileNumber);
            card2.setTokenRef(tokenRef2);
            card2.setMaskedCardNumber("XXXX-XXXX-XXXX-2222");
            card2.setLast4("2222");
            card2.setProgramCode("DEBIT");
            card2.setLifecycleStatus("ACTIVE");
            cardRepository.save(card2);
        }
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
