package io.github.openflowkernel.example.event;

import io.github.openflowkernel.event.BackoffPolicy;
import io.github.openflowkernel.event.DeliveryStatus;
import io.github.openflowkernel.event.DomainEvent;
import io.github.openflowkernel.event.EventBus;
import io.github.openflowkernel.event.EventDraft;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.event.EventExecutionMode;
import io.github.openflowkernel.event.EventListenerRegistration;
import io.github.openflowkernel.event.EventRecordStatus;
import io.github.openflowkernel.event.RetryPolicy;
import io.github.openflowkernel.event.TransactionBoundary;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EventBusTest {

    @Test
    void recordsEventBeforeDispatchingAfterCommit() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryEventStore store = new InMemoryEventStore(clock);
        DeferredTransactionBoundary transaction = new DeferredTransactionBoundary();
        EventBus eventBus = new EventBus(
            new AtomicEventIdGenerator(),
            store,
            transaction,
            new DirectEventExecutor(),
            clock
        );
        List<Long> received = new ArrayList<>();
        eventBus.register(new EventListenerRegistration<>(
            "recording-listener",
            TestEvent.class,
            EventExecutionMode.SYNCHRONOUS,
            RetryPolicy.noRetry(),
            event -> received.add(event.eventId())
        ));

        EventEnvelope<TestEvent> event = eventBus.publish(new EventDraft<>(
            new TestEvent("created"),
            "process",
            "100",
            "100",
            "correlation-100",
            null
        ));

        assertThat(store.findEvent(event.eventId()).orElseThrow().status())
            .isEqualTo(EventRecordStatus.RECORDED);
        assertThat(received).isEmpty();

        transaction.commit();

        assertThat(received).containsExactly(event.eventId());
        assertThat(store.findEvent(event.eventId()).orElseThrow().status())
            .isEqualTo(EventRecordStatus.DISPATCHED);
        assertThat(store.findDelivery(event.eventId(), "recording-listener")
            .orElseThrow().status()).isEqualTo(DeliveryStatus.SUCCEEDED);
    }

    @Test
    void retriesListenerIndependentlyUntilItSucceeds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryEventStore store = new InMemoryEventStore(clock);
        EventBus eventBus = new EventBus(
            new AtomicEventIdGenerator(),
            store,
            Runnable::run,
            new DirectEventExecutor(),
            clock
        );
        AtomicInteger flakyAttempts = new AtomicInteger();
        AtomicInteger stableAttempts = new AtomicInteger();
        RetryPolicy retryPolicy = new RetryPolicy(
            3,
            new BackoffPolicy(Duration.ofSeconds(1), 2, Duration.ofSeconds(10)),
            List.of(IllegalStateException.class),
            List.of()
        );
        eventBus.register(new EventListenerRegistration<>(
            "flaky-listener",
            TestEvent.class,
            EventExecutionMode.SYNCHRONOUS,
            retryPolicy,
            event -> {
                if (flakyAttempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("temporary");
                }
            }
        ));
        eventBus.register(new EventListenerRegistration<>(
            "stable-listener",
            TestEvent.class,
            EventExecutionMode.SYNCHRONOUS,
            RetryPolicy.noRetry(),
            event -> stableAttempts.incrementAndGet()
        ));

        EventEnvelope<TestEvent> event = eventBus.publish(new EventDraft<>(
            new TestEvent("created"),
            "process",
            "200",
            "200",
            "correlation-200",
            null
        ));

        assertThat(stableAttempts).hasValue(1);
        assertThat(flakyAttempts).hasValue(1);
        assertThat(store.findDelivery(event.eventId(), "flaky-listener")
            .orElseThrow().status()).isEqualTo(DeliveryStatus.RETRY_WAIT);

        clock.advance(Duration.ofSeconds(1));
        eventBus.retryDue();
        assertThat(flakyAttempts).hasValue(2);
        assertThat(stableAttempts).hasValue(1);

        clock.advance(Duration.ofSeconds(2));
        eventBus.retryDue();
        assertThat(flakyAttempts).hasValue(3);
        assertThat(stableAttempts).hasValue(1);
        assertThat(store.findDelivery(event.eventId(), "flaky-listener")
            .orElseThrow().status()).isEqualTo(DeliveryStatus.SUCCEEDED);

        eventBus.retryDue();
        assertThat(flakyAttempts).hasValue(3);
    }

    @Test
    void excludedFailureBecomesPermanentImmediately() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryEventStore store = new InMemoryEventStore(clock);
        EventBus eventBus = new EventBus(
            new AtomicEventIdGenerator(),
            store,
            Runnable::run,
            new DirectEventExecutor(),
            clock
        );
        RetryPolicy retryPolicy = new RetryPolicy(
            5,
            new BackoffPolicy(Duration.ofSeconds(1), 2, Duration.ofMinutes(1)),
            List.of(RuntimeException.class),
            List.of(IllegalArgumentException.class)
        );
        eventBus.register(new EventListenerRegistration<>(
            "invalid-listener",
            TestEvent.class,
            EventExecutionMode.SYNCHRONOUS,
            retryPolicy,
            event -> {
                throw new IllegalArgumentException("invalid event");
            }
        ));

        EventEnvelope<TestEvent> event = eventBus.publish(new EventDraft<>(
            new TestEvent("invalid"),
            "process",
            "300",
            "300",
            "correlation-300",
            null
        ));

        assertThat(store.findDelivery(event.eventId(), "invalid-listener")
            .orElseThrow())
            .satisfies(delivery -> {
                assertThat(delivery.status()).isEqualTo(DeliveryStatus.FAILED_PERMANENTLY);
                assertThat(delivery.attempts()).isEqualTo(1);
                assertThat(delivery.lastError()).contains("invalid event");
            });
    }

    private record TestEvent(String action) implements DomainEvent {
        @Override
        public String eventType() {
            return "test-event";
        }
    }

    private static final class DeferredTransactionBoundary implements TransactionBoundary {
        private final List<Runnable> actions = new ArrayList<>();

        @Override
        public void afterCommit(Runnable action) {
            actions.add(action);
        }

        private void commit() {
            List<Runnable> copy = List.copyOf(actions);
            actions.clear();
            copy.forEach(Runnable::run);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
