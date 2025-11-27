package com.example.cardrepo.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CardResponseDto {
    private String tokenRef;
    private String maskedCardNumber;
    private String last4;
    private String programCode;
    private String lifecycleStatus;
    private LocalDateTime eventTimestamp;
}
