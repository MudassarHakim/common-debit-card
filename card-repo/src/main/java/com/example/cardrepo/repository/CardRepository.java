package com.example.cardrepo.repository;

import com.example.cardrepo.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {
    Optional<Card> findByTokenRef(String tokenRef);
    List<Card> findByCustomerMobileNumber(String customerMobileNumber);
}
