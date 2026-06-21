package io.github.openflowkernel.event;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EventBus implements EventPublisher {
    private final EventIdGenerator idGenerator;
    private final EventStore eventStore;
    private final TransactionBoundary transactionBoundary;
    private final EventExecutor eventExecutor;
    private final Clock clock;
    private final Map<String, EventListenerRegistration<?>> registrations =
        new LinkedHashMap<>();

    public EventBus(
        EventIdGenerator idGenerator,
        EventStore eventStore,
        TransactionBoundary transactionBoundary,
        EventExecutor eventExecutor,
        Clock clock
    ) {
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.eventStore = Objects.requireNonNull(eventStore);
        this.transactionBoundary = Objects.requireNonNull(transactionBoundary);
        this.eventExecutor = Objects.requireNonNull(eventExecutor);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized <T extends DomainEvent> void register(
        EventListenerRegistration<T> registration
    ) {
        if (registrations.putIfAbsent(
            registration.listenerName(),
            registration
        ) != null) {
            throw new IllegalArgumentException(
                "Listener already registered: " + registration.listenerName()
            );
        }
    }

    @Override
    public <T extends DomainEvent> EventEnvelope<T> publish(EventDraft<T> draft) {
        EventEnvelope<T> event = new EventEnvelope<>(
            idGenerator.nextId(),
            draft.payload(),
            clock.instant(),
            draft.subjectType(),
            draft.subjectId(),
            draft.partitionKey(),
            draft.correlationId(),
            draft.causationEventId()
        );
        eventStore.record(event);
        transactionBoundary.afterCommit(() -> dispatch(event.eventId()));
        return event;
    }

    public void dispatchUndispatched() {
        eventStore.findUndispatched().stream()
            .sorted(java.util.Comparator.comparingLong(record -> record.event().eventId()))
            .forEach(record -> dispatch(record.event().eventId()));
    }

    public void retryDue() {
        Instant now = clock.instant();
        List<EventDelivery> due = new ArrayList<>(eventStore.findDueDeliveries(now));
        due.sort(java.util.Comparator
            .comparing((EventDelivery delivery) -> requiredEvent(delivery.eventId())
                .partitionKey())
            .thenComparingLong(EventDelivery::eventId));
        for (EventDelivery delivery : due) {
            deliver(delivery.eventId(), delivery.listenerName());
        }
    }

    public void retryOne(long eventId, String listenerName) {
        EventDelivery delivery = eventStore.findDelivery(eventId, listenerName)
            .orElseThrow(() -> new IllegalArgumentException(
                "Delivery not found: " + eventId + "/" + listenerName
            ));
        if (delivery.status() == DeliveryStatus.SUCCEEDED) {
            return;
        }
        deliver(eventId, listenerName);
    }

    private void dispatch(long eventId) {
        EventEnvelope<? extends DomainEvent> event = requiredEvent(eventId);
        for (EventListenerRegistration<?> registration : matching(event.payload())) {
            EventDelivery delivery = eventStore.createDelivery(
                eventId,
                registration.listenerName()
            );
            if (delivery.status() == DeliveryStatus.SUCCEEDED) {
                continue;
            }
            Runnable action = () -> deliver(eventId, registration.listenerName());
            if (registration.executionMode() == EventExecutionMode.ASYNCHRONOUS) {
                eventExecutor.execute(action);
            } else {
                action.run();
            }
        }
        eventStore.markDispatched(eventId, clock.instant());
    }

    @SuppressWarnings("unchecked")
    private <T extends DomainEvent> void invoke(
        EventListenerRegistration<?> rawRegistration,
        EventEnvelope<? extends DomainEvent> rawEvent
    ) {
        EventListenerRegistration<T> registration =
            (EventListenerRegistration<T>) rawRegistration;
        EventEnvelope<T> event = (EventEnvelope<T>) rawEvent;
        registration.listener().listen(event);
    }

    private void deliver(long eventId, String listenerName) {
        EventEnvelope<? extends DomainEvent> event = requiredEvent(eventId);
        EventListenerRegistration<?> registration = requiredRegistration(listenerName);
        EventDelivery current = eventStore.findDelivery(eventId, listenerName)
            .orElseGet(() -> eventStore.createDelivery(eventId, listenerName));
        if (current.status() == DeliveryStatus.SUCCEEDED) {
            return;
        }

        int attempt = current.attempts() + 1;
        try {
            invoke(registration, event);
            eventStore.markSucceeded(eventId, listenerName, attempt);
        } catch (RuntimeException failure) {
            RetryPolicy retryPolicy = registration.retryPolicy();
            boolean retry = retryPolicy.shouldRetry(failure)
                && attempt < retryPolicy.maximumAttempts();
            String error = failure.getClass().getName() + ": " + failure.getMessage();
            if (retry) {
                Instant nextAttempt = clock.instant().plus(
                    retryPolicy.backoff().delayForAttempt(attempt)
                );
                eventStore.scheduleRetry(
                    eventId,
                    listenerName,
                    attempt,
                    nextAttempt,
                    error
                );
            } else {
                eventStore.markFailedPermanently(
                    eventId,
                    listenerName,
                    attempt,
                    error
                );
            }
        }
    }

    private List<EventListenerRegistration<?>> matching(DomainEvent event) {
        return registrations.values().stream()
            .filter(registration -> registration.eventClass().isInstance(event))
            .toList();
    }

    private EventEnvelope<? extends DomainEvent> requiredEvent(long eventId) {
        return eventStore.findEvent(eventId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Event not found: " + eventId
            ))
            .event();
    }

    private EventListenerRegistration<?> requiredRegistration(String listenerName) {
        EventListenerRegistration<?> registration = registrations.get(listenerName);
        if (registration == null) {
            throw new IllegalArgumentException(
                "Listener not registered: " + listenerName
            );
        }
        return registration;
    }
}
