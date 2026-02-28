package com.QuantPlatformApplication.QuantPlatformApplication.config;

import com.QuantPlatformApplication.QuantPlatformApplication.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security Configuration — stateless JWT-based auth.
 * 
 * Key design decisions:
 * 1. STATELESS sessions — no server-side session, each request carries its own
 * JWT
 * 2. CSRF disabled — safe because we use JWT, not cookies for auth
 * 3. Public endpoints: /api/v1/auth/**, /actuator/** (health checks)
 * 4. All other endpoints require a valid JWT in the Authorization header
 * 5. Our JwtAuthenticationFilter runs BEFORE Spring's default
 * UsernamePasswordFilter
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — we use stateless JWT, not cookie-based sessions
                .csrf(csrf -> csrf.disable())

                // Enable CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless sessions — no HttpSession created or used
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no token required
                        .requestMatchers(
                                "/api/v1/auth/**", // register + login
                                "/api/v1/market-data/**", // market data (public for dev)
                                "/api/v1/strategies/**", // strategies (public for dev)
                                "/api/v1/backtests/**", // backtesting (public for dev)
                                "/api/v1/agents/**", // trading agents (public for dev)
                                "/api/v1/orders/**", // order management (public for dev)
                                "/api/v1/risk/**", // risk engine (public for dev)
                                "/api/v1/alerts/**", // alerts (public for dev)
                                "/api/v1/ml/**", // ML service (public for dev)
                                "/ws/**", // websocket
                                "/actuator/**" // health checks + metrics
                        ).permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated())

                // Add our JWT filter before Spring's default username/password filter
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder — used to hash passwords on registration
     * and verify them on login. BCrypt automatically salts passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager bean — needed by AuthController to authenticate
     * login requests (email + password) against the database.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * CORS configuration — allows the frontend to call our API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "null"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
