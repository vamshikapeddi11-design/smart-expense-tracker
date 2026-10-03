package com.expensetracker.service;

import com.expensetracker.model.Category;
import com.expensetracker.model.MerchantMapping;
import com.expensetracker.model.User;
import com.expensetracker.repository.MerchantMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The app's tiny on-device "learning" layer.
 *
 * When the user corrects an expense's category, we remember
 * normalized-merchant -> category. Next time a transaction arrives from the
 * same merchant (bank sync, import, QR), we apply the learned category
 * automatically instead of asking the ML service.
 */
@Service
@RequiredArgsConstructor
public class MerchantLearningService {

    private final MerchantMappingRepository mappingRepository;

    /** Normalizes a merchant name into a stable lookup key. */
    public static String normalize(String merchant) {
        return merchant == null ? "" : merchant.trim().toLowerCase();
    }

    /** Learned category for this merchant, if the user taught us one. */
    public Optional<Category> lookup(Long userId, String merchant) {
        String key = normalize(merchant);
        if (key.isEmpty()) return Optional.empty();
        return mappingRepository.findByUserIdAndMerchantKey(userId, key)
                .map(MerchantMapping::getCategory);
    }

    /**
     * Upserts merchant -> category: creates the mapping on first correction,
     * otherwise bumps hitCount (a simple confidence counter) and updates the
     * category to the latest correction.
     */
    @Transactional
    public void learn(User user, String merchant, Category category) {
        String key = normalize(merchant);
        if (key.isEmpty() || category == null) return;
        MerchantMapping mapping = mappingRepository
                .findByUserIdAndMerchantKey(user.getId(), key)
                .orElseGet(() -> MerchantMapping.builder()
                        .user(user)
                        .merchantKey(key)
                        .category(category)
                        .hitCount(0)
                        .build());
        mapping.setCategory(category); // latest correction wins
        mapping.setHitCount(mapping.getHitCount() + 1);
        mappingRepository.save(mapping);
    }
}
