package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.config.EncryptionConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.DeltaCredentialRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.DeltaCredential;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.DeltaCredentialRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.delta.DeltaExchangeClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/delta")
@RequiredArgsConstructor
public class DeltaExchangeController {

    private final DeltaExchangeClient client;
    private final DeltaCredentialRepository credentialRepo;
    private final EncryptionConfig encryption;

    @PostMapping("/credentials")
    public ResponseEntity<Map<String, String>> saveCredentials(@RequestBody DeltaCredentialRequest request) {
        long userId = 1L;
        DeltaCredential cred = credentialRepo
            .findByUserIdAndIsTestnet(userId, request.isTestnet())
            .orElse(new DeltaCredential());
        cred.setUserId(userId);
        cred.setApiKeyEncrypted(encryption.encrypt(request.getApiKey()));
        cred.setApiSecretEncrypted(encryption.encrypt(request.getApiSecret()));
        cred.setIsTestnet(request.isTestnet());
        credentialRepo.save(cred);
        return ResponseEntity.ok(Map.of("status", "saved", "environment", request.isTestnet() ? "testnet" : "production"));
    }

    @DeleteMapping("/credentials")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteCredentials(@RequestParam(defaultValue = "true") boolean testnet) {
        long userId = 1L;
        credentialRepo.deleteByUserIdAndIsTestnet(userId, testnet);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/products")
    public Mono<JsonNode> getProducts(@RequestParam(defaultValue = "true") boolean testnet) {
        return client.getProducts(testnet);
    }

    @GetMapping("/ticker/{symbol}")
    public Mono<JsonNode> getTicker(@PathVariable String symbol, @RequestParam(defaultValue = "true") boolean testnet) {
        return client.getTicker(symbol, testnet);
    }

    @GetMapping("/orderbook/{productId}")
    public Mono<JsonNode> getOrderBook(@PathVariable int productId, @RequestParam(defaultValue = "20") int depth, @RequestParam(defaultValue = "true") boolean testnet) {
        return client.getOrderBook(productId, depth, testnet);
    }

    @GetMapping("/connection-status")
    public ResponseEntity<Map<String, Object>> connectionStatus(@RequestParam(defaultValue = "true") boolean testnet) {
        long userId = 1L;
        boolean hasCredentials = credentialRepo.findByUserIdAndIsTestnet(userId, testnet).isPresent();
        return ResponseEntity.ok(Map.of("hasCredentials", hasCredentials, "environment", testnet ? "testnet" : "production"));
    }
}
