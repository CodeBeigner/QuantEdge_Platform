package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.RiskConfigRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.RiskConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.RiskConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/risk-config")
@RequiredArgsConstructor
public class RiskConfigController {

    private final RiskConfigRepository riskConfigRepo;

    @GetMapping
    public ResponseEntity<RiskConfig> getRiskConfig() {
        long userId = 1L;
        RiskConfig config = riskConfigRepo.findByUserId(userId)
            .orElseGet(() -> {
                RiskConfig defaultConfig = RiskConfig.builder().userId(userId).build();
                return riskConfigRepo.save(defaultConfig);
            });
        return ResponseEntity.ok(config);
    }

    @PutMapping
    public ResponseEntity<RiskConfig> updateRiskConfig(@RequestBody RiskConfigRequest request) {
        long userId = 1L;
        RiskConfig config = riskConfigRepo.findByUserId(userId)
            .orElseGet(() -> RiskConfig.builder().userId(userId).build());
        if (request.getRiskPerTradePct() != null) config.setRiskPerTradePct(request.getRiskPerTradePct());
        if (request.getMaxEffectiveLeverage() != null) config.setMaxEffectiveLeverage(request.getMaxEffectiveLeverage());
        if (request.getDailyLossHaltPct() != null) config.setDailyLossHaltPct(request.getDailyLossHaltPct());
        if (request.getMaxDrawdownPct() != null) config.setMaxDrawdownPct(request.getMaxDrawdownPct());
        if (request.getMaxConcurrentPositions() != null) config.setMaxConcurrentPositions(request.getMaxConcurrentPositions());
        if (request.getMaxStopDistancePct() != null) config.setMaxStopDistancePct(request.getMaxStopDistancePct());
        if (request.getMinRiskRewardRatio() != null) config.setMinRiskRewardRatio(request.getMinRiskRewardRatio());
        if (request.getFeeImpactThreshold() != null) config.setFeeImpactThreshold(request.getFeeImpactThreshold());
        if (request.getExecutionMode() != null) config.setExecutionMode(request.getExecutionMode());
        return ResponseEntity.ok(riskConfigRepo.save(config));
    }
}
