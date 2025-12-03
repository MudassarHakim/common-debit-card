package com.example.cardsservice.dto;

import lombok.Data;

@Data
public class CardEventDto {
    private String tokenRef;
    private String maskedCardNumber;
    private String last4;
    private String programCode;
    private String programCategory;
    private String network;
    private String bin;
    private String lifecycleStatus;
    private String rawStatus;
    private String customerMobileNumber;
    private String custId;
    private String accountNo;
    private String issuedBySystem;
    private String issuanceChannel;
    private String eventTimestamp;
}
