package com.expensetracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * General application beans.
 *
 * RestTemplate is Spring's classic HTTP client. We use it in
 * {@code MlClientService} to talk to the Python ML microservice.
 * Timeouts are set so a slow/dead ML service can't hang our API forever.
 */
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Simple builder: connect timeout 3s, read timeout 5s.
        return new org.springframework.boot.web.client.RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
