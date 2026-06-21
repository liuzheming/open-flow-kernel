package io.github.openflowkernel.event;

import java.time.Instant;

public record EventRecord(
    EventEnvelope<? extends DomainEvent> event,
    EventRecordStatus status,
    Instant recordedAt,
    Instant dispatchedAt
) {
}
