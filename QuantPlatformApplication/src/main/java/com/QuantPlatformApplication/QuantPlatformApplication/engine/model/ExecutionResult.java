package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Getter;

/**
 * Outcome of executing a single strategy — either a successful Decision or an
 * error message.
 */
@Getter
public class ExecutionResult {

    private final long strategyId;
    private final boolean success;
    private final Decision decision;
    private final String error;

    private ExecutionResult(long id, boolean success, Decision decision, String error) {
        this.strategyId = id;
        this.success = success;
        this.decision = decision;
        this.error = error;
    }

    public static ExecutionResult success(long id, Decision d) {
        return new ExecutionResult(id, true, d, null);
    }

    public static ExecutionResult failure(long id, String error) {
        return new ExecutionResult(id, false, null, error);
    }
}
