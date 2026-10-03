package com.expensetracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS = Cross-Origin Resource Sharing.
 *
 * Browsers block a web page on one origin (http://localhost:5173, our React
 * dev server) from calling an API on another origin (http://localhost:8080,
 * this backend) unless the API explicitly allows it. This config allows the
 * frontend origin to call our API with any method/header, including the
 * Authorization header that carries the JWT.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
