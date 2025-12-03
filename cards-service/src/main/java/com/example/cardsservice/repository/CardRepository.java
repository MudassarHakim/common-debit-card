package com.example.cardsservice.repository;

import com.example.cardsservice.entity.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {
    Optional<Card> findByTokenRef(String tokenRef);

    List<Card> findByCustomerMobileNumber(String customerMobileNumber);

    // Methods for C360 sync management
    Page<Card> findBySyncPending(boolean syncPending, Pageable pageable);

    long countBySyncPending(boolean syncPending);
}
