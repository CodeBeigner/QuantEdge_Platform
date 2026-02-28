package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.AuthResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.LoginRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.RegisterRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.User;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.UserRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Auth Controller — handles user registration and login.

 * POST /api/v1/auth/register → create user, hash password, return JWT
 * POST /api/v1/auth/login → verify credentials, return JWT

 * Both endpoints are PUBLIC (no token required) — configured in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtTokenProvider jwtTokenProvider;
        private final AuthenticationManager authenticationManager;

        public AuthController(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider,
                        AuthenticationManager authenticationManager) {
                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtTokenProvider = jwtTokenProvider;
                this.authenticationManager = authenticationManager;
        }

        // ── Register ─────────────────────────────────────────────────────────────

        /**
         * Create a new user account.
         * 
         * Flow:
         * 1. Check if email already exists → 409 Conflict
         * 2. Hash the password with BCrypt
         * 3. Save the User entity to Postgres
         * 4. Generate a JWT token
         * 5. Return the token + user info
         */
        @PostMapping("/register")
        public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {

                // 1. Check for duplicate email
                if (userRepository.existsByEmail(request.getEmail())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                        .body(Map.of("error", "Email already registered: " + request.getEmail()));
                }

                // 2. Build the user entity with hashed password
                User user = User.builder()
                                .name(request.getName())
                                .email(request.getEmail())
                                .passwordHash(passwordEncoder.encode(request.getPassword()))
                                .role("USER")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();

                // 3. Save to database
                userRepository.save(user);

                // 4. Generate JWT
                String token = jwtTokenProvider.generateToken(user.getEmail());

                // 5. Return response
                AuthResponse response = AuthResponse.builder()
                                .token(token)
                                .email(user.getEmail())
                                .name(user.getName())
                                .role(user.getRole())
                                .build();

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        // ── Login ────────────────────────────────────────────────────────────────

        /**
         * Authenticate an existing user.
         * 
         * Flow:
         * 1. Use AuthenticationManager to verify email + password against DB
         * 2. If valid → generate JWT, update lastLogin timestamp
         * 3. If invalid → Spring Security throws BadCredentialsException → 401
         */
        @PostMapping("/login")
        public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
                try {
                        // 1. Authenticate — Spring Security checks password via
                        // CustomUserDetailsService
                        authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(
                                                        request.getEmail(),
                                                        request.getPassword()));

                        // 2. Fetch user and update last login
                        User user = userRepository.findByEmail(request.getEmail())
                                        .orElseThrow(() -> new RuntimeException("User not found"));

                        user.setLastLogin(Instant.now());
                        user.setUpdatedAt(Instant.now());
                        userRepository.save(user);

                        // 3. Generate JWT
                        String token = jwtTokenProvider.generateToken(user.getEmail());

                        // 4. Return response
                        AuthResponse response = AuthResponse.builder()
                                        .token(token)
                                        .email(user.getEmail())
                                        .name(user.getName())
                                        .role(user.getRole())
                                        .build();

                        return ResponseEntity.ok(response);

                } catch (BadCredentialsException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("error", "Invalid email or password"));
                }
        }
}
