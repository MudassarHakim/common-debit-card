Feature: Public Cards Service API

  Scenario: Retrieve cards for a customer
    Given the integration service returns cards for mobile number "1234567890"
    When I request cards with mobile number "1234567890"
    Then I should receive a successful response
    And the response should contain the cards
