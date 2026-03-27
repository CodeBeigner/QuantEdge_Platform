package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.FirmProfile;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.FirmType;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.User;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.UserRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.FirmProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST controller for firm profile management.
 *
 * <ul>
 *   <li>POST /api/v1/firm/setup        — Create firm + spawn agents</li>
 *   <li>GET  /api/v1/firm              — Get current user's firm</li>
 *   <li>GET  /api/v1/firm/setup-status — Check if setup is complete</li>
 *   <li>PUT  /api/v1/firm              — Update firm name/risk appetite</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/firm")
@RequiredArgsConstructor
public class FirmController {

    private final FirmProfileService firmService;
    private final UserRepository userRepository;

    /**
     * Create a new firm and spawn the default agent roster.
     *
     * @param body request body with firmName, firmType, initialCapital, riskAppetite
     * @return the created firm profile
     */
    @PostMapping("/setup")
    public ResponseEntity<?> setupFirm(@RequestBody Map<String, Object> body) {
        try {
            Long userId = resolveUserId();
            String firmName = (String) body.get("firmName");
            FirmType firmType = FirmType.valueOf((String) body.get("firmType"));
            BigDecimal initialCapital = new BigDecimal(body.get("initialCapital").toString());
            String riskAppetite = (String) body.getOrDefault("riskAppetite", "MODERATE");

            FirmProfile firm = firmService.createFirm(userId, firmName, firmType,
                    initialCapital, riskAppetite);
            return ResponseEntity.status(HttpStatus.CREATED).body(firmToMap(firm));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Firm setup failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get the current user's firm profile.
     *
     * @return 200 with firm data, or 404 if not set up
     */
    @GetMapping
    public ResponseEntity<?> getFirm() {
        Long userId = resolveUserId();
        return firmService.getFirmForUser(userId)
                .map(firm -> ResponseEntity.ok(firmToMap(firm)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Check if the current user has completed firm setup.
     *
     * @return setup status with firm type if available
     */
    @GetMapping("/setup-status")
    public ResponseEntity<Map<String, Object>> getSetupStatus() {
        Long userId = resolveUserId();
        boolean setupComplete = firmService.isSetupComplete(userId);
        String firmType = firmService.getFirmForUser(userId)
                .map(f -> f.getFirmType().name())
                .orElse(null);

        return ResponseEntity.ok(Map.of(
                "setupComplete", setupComplete,
                "firmType", firmType != null ? firmType : ""
        ));
    }

    /**
     * Update the current user's firm name and/or risk appetite.
     *
     * @param body request body with optional firmName and riskAppetite
     * @return the updated firm profile
     */
    @PutMapping
    public ResponseEntity<?> updateFirm(@RequestBody Map<String, String> body) {
        try {
            Long userId = resolveUserId();
            FirmProfile updated = firmService.updateFirm(
                    userId,
                    body.get("firmName"),
                    body.get("riskAppetite")
            );
            return ResponseEntity.ok(firmToMap(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Resolve the current user's ID from the JWT-authenticated security context.
     * The JWT stores email as the subject; we look up the User entity by email.
     *
     * @return the user's database ID
     */
    private Long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));
        return user.getId();
    }

    /**
     * Convert a FirmProfile entity to a response map.
     *
     * @param firm the firm profile
     * @return map representation for JSON serialization
     */
    private Map<String, Object> firmToMap(FirmProfile firm) {
        return Map.of(
                "id", firm.getId(),
                "firmName", firm.getFirmName(),
                "firmType", firm.getFirmType().name(),
                "initialCapital", firm.getInitialCapital(),
                "riskAppetite", firm.getRiskAppetite(),
                "setupComplete", firm.getSetupComplete(),
                "ownerUserId", firm.getOwnerUserId(),
                "createdAt", firm.getCreatedAt().toString(),
                "updatedAt", firm.getUpdatedAt().toString()
        );
    }
}
