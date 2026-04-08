package com.folo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/swagger-ui.html",
                                "/swagger-ui.html",
                                "/api/swagger-ui/**",
                                "/swagger-ui/**",
                                "/api/v3/api-docs/**",
                                "/v3/api-docs/**",
                                "/api/actuator/health",
                                "/actuator/health",
                                "/api/uploads/**",
                                "/uploads/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/find-id",
                                "/api/auth/password/reset-temp",
                                "/api/auth/social/*/start",
                                "/api/internal/stock-enrichment/dividends/sync",
                                "/api/internal/stock-enrichment/dividends/debug/kis",
                                "/api/internal/stock-enrichment/metadata/sync",
                                "/api/internal/stock-enrichment/issuer-profiles/sync",
                                "/api/auth/refresh",
                                "/api/auth/email/verify",
                                "/api/auth/email/confirm",
                                "/api/auth/social/exchange",
                                "/api/auth/social/apple/verify",
                                "/api/auth/social/complete-profile",
                                "/api/uploads/profile-image",
                                "/auth/signup",
                                "/auth/login",
                                "/auth/find-id",
                                "/auth/password/reset-temp",
                                "/auth/social/*/start",
                                "/internal/stock-enrichment/dividends/sync",
                                "/internal/stock-enrichment/dividends/debug/kis",
                                "/internal/stock-enrichment/metadata/sync",
                                "/internal/stock-enrichment/issuer-profiles/sync",
                                "/auth/refresh",
                                "/auth/email/verify",
                                "/auth/email/confirm",
                                "/auth/social/exchange",
                                "/auth/social/apple/verify",
                                "/auth/social/complete-profile",
                                "/uploads/profile-image"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/stocks/search",
                                "/api/stocks/discover",
                                "/api/stocks/*/logo",
                                "/api/stocks/*/price",
                                "/api/auth/social/*/callback",
                                "/stocks/search",
                                "/stocks/discover",
                                "/stocks/*/logo",
                                "/stocks/*/price",
                                "/auth/social/*/callback"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://localhost:*",
                "https://127.0.0.1:*"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
