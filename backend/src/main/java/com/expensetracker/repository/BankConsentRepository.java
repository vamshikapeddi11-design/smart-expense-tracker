package com.expensetracker.repository;

import com.expensetracker.model.BankConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankConsentRepository extends JpaRepository<BankConsent, Long> {
    List<BankConsent> findByUserId(Long userId);
    Optional<BankConsent> findByConsentId(String consentId);
    Optional<BankConsent> findByIdAndUserId(Long id, Long userId);
}
