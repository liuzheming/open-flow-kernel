package io.github.openflowkernel.event;

import java.time.Instant;
import java.util.Objects;

public record EventEnvelope<T extends DomainEvent>(
    long eventId,
    T payload,
    Instant occurredAt,
    String subjectType,
    String subjectId,
    String partitionKey,
    String correlationId,
    Long causationEventId
) {
    public EventEnvelope {
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(occurredAt, "occurredAt");
        subjectType = requireText(subjectType, "subjectType");
        subjectId = requireText(subjectId, "subjectId");
        partitionKey = requireText(partitionKey, "partitionKey");
        correlationId = requireText(correlationId, "correlationId");
    }

    public String eventType() {
        return payload.eventType();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
