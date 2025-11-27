package com.example.cardrepo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "cards", indexes = {
    @Index(name = "idx_token_ref", columnList = "tokenRef", unique = true),
    @Index(name = "idx_mobile", columnList = "customerMobileNumber")
})
@Data
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tokenRef;

    @Column(nullable = false)
    private String maskedCardNumber;

    @Column(nullable = false)
    private String last4;

    private String programCode;
    private String programCategory;
    private String network;
    private String bin;

    @Column(nullable = false)
    private String lifecycleStatus;

    private String rawStatus;
    private String customerMobileNumber;
    private String custId;
    private String accountNo;
    private String issuedBySystem;
    private String issuanceChannel;

    private LocalDateTime eventTimestamp;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;

    private boolean syncPending;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
