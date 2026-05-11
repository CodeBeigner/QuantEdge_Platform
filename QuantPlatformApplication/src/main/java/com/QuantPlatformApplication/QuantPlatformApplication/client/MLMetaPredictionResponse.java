package com.QuantPlatformApplication.QuantPlatformApplication.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MLMetaPredictionResponse(
        String symbol,
        @JsonProperty("meta_prob") double metaProb,
        int direction,
        @JsonProperty("primary_signal") String primarySignal
) {}
