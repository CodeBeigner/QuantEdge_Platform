package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import lombok.Data;

@Data
public class DeltaCredentialRequest {
    private String apiKey;
    private String apiSecret;
    private boolean testnet = true;
}
