package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

public enum TimeFrame {
    M15(15), H1(60), H4(240);

    private final int minutes;

    TimeFrame(int minutes) {
        this.minutes = minutes;
    }

    public int getMinutes() {
        return minutes;
    }

    public int getMultiplierFrom(TimeFrame base) {
        return this.minutes / base.minutes;
    }
}
