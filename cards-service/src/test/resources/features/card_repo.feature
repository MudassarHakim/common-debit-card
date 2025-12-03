Feature: Internal Card Repository API

  Scenario: Retrieve cards for a customer
    Given the card repository has a card for mobile number "1234567890"
    When I request cards for mobile number "1234567890"
    Then I should receive a list containing at least one card
    And the card should have the token ref "TOKEN_1234567890"
