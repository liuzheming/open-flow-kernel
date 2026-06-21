package io.github.openflowkernel.event;

@FunctionalInterface
public interface EventListener<T extends DomainEvent> {
    void listen(EventEnvelope<T> event);
}
