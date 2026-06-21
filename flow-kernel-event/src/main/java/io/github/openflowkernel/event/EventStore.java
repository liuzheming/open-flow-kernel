package io.github.openflowkernel.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventStore {
    void record(EventEnvelope<? extends DomainEvent> event);

    Optional<EventRecord> findEvent(long eventId);

    List<EventRecord> findUndispatched();

    void markDispatched(long eventId, Instant dispatchedAt);

    EventDelivery createDelivery(long eventId, String listenerName);

    Optional<EventDelivery> findDelivery(long eventId, String listenerName);

    List<EventDelivery> findDueDeliveries(Instant now);

    void markSucceeded(long eventId, String listenerName, int attempts);

    void scheduleRetry(
        long eventId,
        String listenerName,
        int attempts,
        Instant nextAttemptAt,
        String lastError
    );

    void markFailedPermanently(
        long eventId,
        String listenerName,
        int attempts,
        String lastError
    );
}
