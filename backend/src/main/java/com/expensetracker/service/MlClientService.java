package com.expensetracker.service;

import com.expensetracker.model.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Client for the Python ML microservice (categorization model).
 *
 * POST {ml.service.url}/categorize  with  {"text": "..."}
 * expects back                        {"category": "Food"} (or similar).
 *
 * Graceful fallback: if the ML service is down, slow, or returns garbage, we
 * log a warning and return Optional.empty() - the caller then falls back to
 * merchant-learning / "Other" instead of failing the whole request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MlClientService {

    private final RestTemplate restTemplate;

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    /**
     * Asks the ML service for a category. Empty when the service is
     * unreachable or its answer isn't a known category.
     */
    public Optional<Category> categorize(String merchant, String note) {
        String url = mlServiceUrl + "/categorize";
        // Contract with the ML service: POST {ml.service.url}/categorize
        // with body {"text": "<merchant + note combined>"}.
        String combined = ((merchant != null ? merchant : "") + " "
                + (note != null ? note : "")).trim();
        Map<String, String> body = Map.of("text", combined);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response =
                    restTemplate.postForObject(url, body, Map.class);
            if (response == null || response.get("category") == null) {
                return Optional.empty();
            }
            String raw = String.valueOf(response.get("category")).trim();
            return parseCategory(raw);
        } catch (RestClientException e) {
            // ML is a best-effort helper, never a hard dependency.
            log.warn("ML service unavailable at {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /** Accepts "Food", "food", "FOOD" ... maps unknown values to empty. */
    private Optional<Category> parseCategory(String raw) {
        for (Category c : Category.values()) {
            if (c.name().equalsIgnoreCase(raw)) {
                return Optional.of(c);
            }
        }
        log.warn("ML returned unknown category '{}', ignoring", raw);
        return Optional.empty();
    }
}
