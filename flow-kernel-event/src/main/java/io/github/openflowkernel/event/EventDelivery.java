package io.github.openflowkernel.event;

import java.time.Instant;

public record EventDelivery(
    long eventId,
    String listenerName,
    DeliveryStatus status,
    int attempts,
    Instant nextAttemptAt,
    String lastError
) {
}
