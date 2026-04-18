package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;

import java.util.Optional;

public interface MultiTimeFrameStrategy {
    Optional<TradeSignal> analyze(MultiTimeFrameData data);
    ModelType getModelType();
    String getName();
}
