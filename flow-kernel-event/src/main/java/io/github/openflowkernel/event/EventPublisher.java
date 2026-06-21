package io.github.openflowkernel.event;

public interface EventPublisher {
    <T extends DomainEvent> EventEnvelope<T> publish(EventDraft<T> event);
}
