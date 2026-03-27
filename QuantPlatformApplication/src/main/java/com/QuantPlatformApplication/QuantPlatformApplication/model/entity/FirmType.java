package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

/**
 * Defines the types of trading firms that can be created.
 * Each firm type determines which agents are spawned and their mandates.
 */
public enum FirmType {
    HEDGE_FUND,
    HFT,
    PROP_TRADING,
    GLOBAL_MACRO,
    MULTI_STRATEGY,
    CUSTOM
}
