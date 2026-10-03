package com.expensetracker.repository;

import com.expensetracker.model.MerchantMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantMappingRepository extends JpaRepository<MerchantMapping, Long> {
    Optional<MerchantMapping> findByUserIdAndMerchantKey(Long userId, String merchantKey);
}
