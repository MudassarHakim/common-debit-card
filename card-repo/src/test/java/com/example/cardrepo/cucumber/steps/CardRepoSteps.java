package com.example.cardrepo.cucumber.steps;

import com.example.cardrepo.entity.Card;
import com.example.cardrepo.repository.CardRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

public class CardRepoSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardRepository cardRepository;

    private ResultActions resultActions;

    @Given("the card repository has a card for mobile number {string}")
    public void the_card_repository_has_a_card_for_mobile_number(String mobileNumber) {
        // Clean up potentially existing card to avoid unique constraint violation
        String tokenRef = "TOKEN_" + mobileNumber;
        if (cardRepository.findByTokenRef(tokenRef).isPresent()) {
            // Already exists
            return;
        }

        Card card = new Card();
        card.setCustomerMobileNumber(mobileNumber);
        card.setTokenRef(tokenRef);
        card.setMaskedCardNumber("XXXX-XXXX-XXXX-1234");
        card.setLast4("1234");
        card.setProgramCode("DEBIT");
        card.setLifecycleStatus("ACTIVE");
        cardRepository.save(card);
    }

    @When("I request cards for mobile number {string}")
    public void i_request_cards_for_mobile_number(String mobileNumber) throws Exception {
        resultActions = mockMvc.perform(get("/internal/cards")
                .param("mobile", mobileNumber));
    }

    @Then("I should receive a list containing at least one card")
    public void i_should_receive_a_list_containing_at_least_one_card() throws Exception {
        resultActions.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Then("the card should have the token ref {string}")
    public void the_card_should_have_the_token_ref(String tokenRef) throws Exception {
        resultActions.andExpect(jsonPath("$[0].tokenRef", is(tokenRef)));
    }
}
