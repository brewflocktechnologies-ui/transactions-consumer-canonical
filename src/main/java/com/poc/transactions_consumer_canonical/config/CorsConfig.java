package com.poc.transactions_consumer_canonical.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Cross-origin configuration driven by {@code app.cors.*} properties.
 * Defaults in {@code application.properties} permit typical SPA dev origins;
 * {@code application-prod.properties} starts with an empty allow-list — set
 * {@code CORS_ALLOWED_ORIGINS} explicitly at deploy time.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,PUT,POST,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${app.cors.max-age-seconds:3600}")
    private long maxAgeSeconds;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();

        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            // empty → no CORS allowed (safe prod default)
            cfg.setAllowedOrigins(List.of());
        } else if ("*".equals(allowedOrigins.trim())) {
            // Wildcard — disable credentials per spec
            cfg.addAllowedOriginPattern("*");
        } else {
            cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());
            cfg.setAllowCredentials(true);
        }

        cfg.setAllowedMethods(Arrays.stream(allowedMethods.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("X-Request-ID", "Location"));
        cfg.setMaxAge(maxAgeSeconds);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        source.registerCorsConfiguration("/actuator/**", cfg);
        return new CorsFilter(source);
    }
}
