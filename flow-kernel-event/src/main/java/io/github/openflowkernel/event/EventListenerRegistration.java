package io.github.openflowkernel.event;

import java.util.Objects;

public record EventListenerRegistration<T extends DomainEvent>(
    String listenerName,
    Class<T> eventClass,
    EventExecutionMode executionMode,
    RetryPolicy retryPolicy,
    EventListener<T> listener
) {
    public EventListenerRegistration {
        if (listenerName == null || listenerName.isBlank()) {
            throw new IllegalArgumentException("listenerName must not be blank");
        }
        Objects.requireNonNull(eventClass, "eventClass");
        Objects.requireNonNull(executionMode, "executionMode");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        Objects.requireNonNull(listener, "listener");
    }
}
