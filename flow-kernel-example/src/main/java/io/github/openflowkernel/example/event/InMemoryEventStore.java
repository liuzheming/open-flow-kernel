package io.github.openflowkernel.example.event;

import io.github.openflowkernel.event.DeliveryStatus;
import io.github.openflowkernel.event.DomainEvent;
import io.github.openflowkernel.event.EventDelivery;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.event.EventRecord;
import io.github.openflowkernel.event.EventRecordStatus;
import io.github.openflowkernel.event.EventStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryEventStore implements EventStore {
    private final Map<Long, EventRecord> events = new ConcurrentHashMap<>();
    private final Map<String, EventDelivery> deliveries = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryEventStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized void record(EventEnvelope<? extends DomainEvent> event) {
        if (events.putIfAbsent(
            event.eventId(),
            new EventRecord(event, EventRecordStatus.RECORDED, clock.instant(), null)
        ) != null) {
            throw new IllegalArgumentException("Event already recorded: " + event.eventId());
        }
    }

    @Override
    public Optional<EventRecord> findEvent(long eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    @Override
    public List<EventRecord> findUndispatched() {
        return events.values().stream()
            .filter(record -> record.status() == EventRecordStatus.RECORDED)
            .toList();
    }

    @Override
    public synchronized void markDispatched(long eventId, Instant dispatchedAt) {
        EventRecord current = requiredEvent(eventId);
        events.put(eventId, new EventRecord(
            current.event(),
            EventRecordStatus.DISPATCHED,
            current.recordedAt(),
            dispatchedAt
        ));
    }

    @Override
    public synchronized EventDelivery createDelivery(long eventId, String listenerName) {
        requiredEvent(eventId);
        return deliveries.computeIfAbsent(
            deliveryKey(eventId, listenerName),
            ignored -> new EventDelivery(
                eventId,
                listenerName,
                DeliveryStatus.PENDING,
                0,
                null,
                null
            )
        );
    }

    @Override
    public Optional<EventDelivery> findDelivery(long eventId, String listenerName) {
        return Optional.ofNullable(deliveries.get(deliveryKey(eventId, listenerName)));
    }

    @Override
    public List<EventDelivery> findDueDeliveries(Instant now) {
        return deliveries.values().stream()
            .filter(delivery -> delivery.status() == DeliveryStatus.RETRY_WAIT)
            .filter(delivery -> !delivery.nextAttemptAt().isAfter(now))
            .toList();
    }

    @Override
    public synchronized void markSucceeded(long eventId, String listenerName, int attempts) {
        update(new EventDelivery(
            eventId,
            listenerName,
            DeliveryStatus.SUCCEEDED,
            attempts,
            null,
            null
        ));
    }

    @Override
    public synchronized void scheduleRetry(
        long eventId,
        String listenerName,
        int attempts,
        Instant nextAttemptAt,
        String lastError
    ) {
        update(new EventDelivery(
            eventId,
            listenerName,
            DeliveryStatus.RETRY_WAIT,
            attempts,
            nextAttemptAt,
            lastError
        ));
    }

    @Override
    public synchronized void markFailedPermanently(
        long eventId,
        String listenerName,
        int attempts,
        String lastError
    ) {
        update(new EventDelivery(
            eventId,
            listenerName,
            DeliveryStatus.FAILED_PERMANENTLY,
            attempts,
            null,
            lastError
        ));
    }

    private void update(EventDelivery delivery) {
        String key = deliveryKey(delivery.eventId(), delivery.listenerName());
        if (!deliveries.containsKey(key)) {
            throw new IllegalArgumentException("Delivery not found: " + key);
        }
        deliveries.put(key, delivery);
    }

    private EventRecord requiredEvent(long eventId) {
        EventRecord record = events.get(eventId);
        if (record == null) {
            throw new IllegalArgumentException("Event not found: " + eventId);
        }
        return record;
    }

    private static String deliveryKey(long eventId, String listenerName) {
        return eventId + "\u0000" + listenerName;
    }
}
