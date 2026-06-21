package io.github.openflowkernel.event;

public record EventDraft<T extends DomainEvent>(
    T payload,
    String subjectType,
    String subjectId,
    String partitionKey,
    String correlationId,
    Long causationEventId
) {
}
