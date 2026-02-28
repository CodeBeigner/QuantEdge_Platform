package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

/**
 * Raw signal produced by a strategy's analysis before position sizing.
 */
public record Signal(Action action, double confidence, String reasoning) {
}
