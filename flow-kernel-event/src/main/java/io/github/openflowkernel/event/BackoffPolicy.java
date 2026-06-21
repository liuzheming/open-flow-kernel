package io.github.openflowkernel.event;

import java.time.Duration;

public record BackoffPolicy(Duration initialDelay, double multiplier, Duration maximumDelay) {
    public BackoffPolicy {
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be negative");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be at least 1");
        }
        if (maximumDelay == null || maximumDelay.isNegative()) {
            throw new IllegalArgumentException("maximumDelay must not be negative");
        }
    }

    public Duration delayForAttempt(int failedAttempt) {
        if (failedAttempt <= 0) {
            throw new IllegalArgumentException("failedAttempt must be positive");
        }
        double factor = Math.pow(multiplier, failedAttempt - 1);
        long calculated;
        try {
            calculated = Math.multiplyExact(
                initialDelay.toMillis(),
                Math.max(1L, (long) Math.floor(factor))
            );
        } catch (ArithmeticException exception) {
            calculated = Long.MAX_VALUE;
        }
        long capped = maximumDelay.isZero()
            ? calculated
            : Math.min(calculated, maximumDelay.toMillis());
        return Duration.ofMillis(capped);
    }
}
