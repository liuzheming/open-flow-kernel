package io.github.openflowkernel.event;

import java.time.Duration;
import java.util.List;

public record RetryPolicy(
    int maximumAttempts,
    BackoffPolicy backoff,
    List<Class<? extends Throwable>> includedExceptions,
    List<Class<? extends Throwable>> excludedExceptions
) {
    public RetryPolicy {
        if (maximumAttempts <= 0) {
            throw new IllegalArgumentException("maximumAttempts must be positive");
        }
        if (backoff == null) {
            throw new IllegalArgumentException("backoff must not be null");
        }
        includedExceptions = List.copyOf(includedExceptions);
        excludedExceptions = List.copyOf(excludedExceptions);
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(
            1,
            new BackoffPolicy(Duration.ZERO, 1, Duration.ZERO),
            List.of(),
            List.of()
        );
    }

    public boolean shouldRetry(Throwable failure) {
        if (matches(excludedExceptions, failure)) {
            return false;
        }
        return includedExceptions.isEmpty() || matches(includedExceptions, failure);
    }

    private static boolean matches(
        List<Class<? extends Throwable>> types,
        Throwable failure
    ) {
        Throwable current = failure;
        while (current != null) {
            for (Class<? extends Throwable> type : types) {
                if (type.isInstance(current)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
