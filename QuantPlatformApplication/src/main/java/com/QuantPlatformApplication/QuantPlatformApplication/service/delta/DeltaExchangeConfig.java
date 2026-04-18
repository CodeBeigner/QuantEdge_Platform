package com.QuantPlatformApplication.QuantPlatformApplication.service.delta;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "delta-exchange")
public class DeltaExchangeConfig {
    private String testnetBaseUrl = "https://cdn-ind.testnet.deltaex.org";
    private String productionBaseUrl = "https://api.india.delta.exchange";
    private String testnetWsUrl = "wss://cdn-ind.testnet.deltaex.org/v2/ws";
    private String productionWsUrl = "wss://api.india.delta.exchange/v2/ws";
    private int timeoutSeconds = 10;
    private int maxRetries = 3;
}
