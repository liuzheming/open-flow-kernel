package io.github.openflowkernel.jdbc.persistence;

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
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEventStoreTest {
    @Test
    void recordsDispatchesRetriesAndReplaysEvents() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JdbcEventStore store = new JdbcEventStore(dataSource(), new TestEventCodec());
        EventBus eventBus = new EventBus(
            new IncrementingEventIds(),
            store,
            Runnable::run,
            Runnable::run,
            clock
        );
        AtomicInteger flakyAttempts = new AtomicInteger();
        List<String> received = new ArrayList<>();
        eventBus.register(new EventListenerRegistration<>(
            "flaky-listener",
            TestEvent.class,
            EventExecutionMode.SYNCHRONOUS,
            new RetryPolicy(
                3,
                new BackoffPolicy(Duration.ofSeconds(1), 2, Duration.ofSeconds(5)),
                List.of(IllegalStateException.class),
                List.of()
            ),
            event -> {
                if (flakyAttempts.incrementAndGet() < 2) {
                    throw new IllegalStateException("temporary");
                }
                received.add(event.payload().action());
            }
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
            .isEqualTo(EventRecordStatus.DISPATCHED);
        assertThat(store.findDelivery(event.eventId(), "flaky-listener").orElseThrow())
            .satisfies(delivery -> {
                assertThat(delivery.status()).isEqualTo(DeliveryStatus.RETRY_WAIT);
                assertThat(delivery.attempts()).isEqualTo(1);
                assertThat(delivery.lastError()).contains("temporary");
            });

        clock.advance(Duration.ofSeconds(1));
        eventBus.retryDue();

        assertThat(received).containsExactly("created");
        assertThat(store.findDelivery(event.eventId(), "flaky-listener").orElseThrow()
            .status()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(store.findUndispatched()).isEmpty();
    }

    @Test
    void dispatchesRecordedEventAfterRestartLikeReplay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JdbcEventStore store = new JdbcEventStore(dataSource(), new TestEventCodec());
        EventBus firstBus = new EventBus(
            new IncrementingEventIds(),
            store,
            action -> {
            },
            Runnable::run,
            clock
        );

        EventEnvelope<TestEvent> event = firstBus.publish(new EventDraft<>(
            new TestEvent("replay"),
            "process",
            "200",
            "200",
            "correlation-200",
            null
        ));

        assertThat(store.findEvent(event.eventId()).orElseThrow().status())
            .isEqualTo(EventRecordStatus.RECORDED);

        List<String> received = new ArrayList<>();
        EventBus restartedBus = new EventBus(
            new IncrementingEventIds(),
            store,
            Runnable::run,
            Runnable::run,
            clock
        );
        restartedBus.register(new EventListenerRegistration<>(
            "recording-listener",
            TestEvent.class,
            EventExecutionMode.SYNCHRONOUS,
            RetryPolicy.noRetry(),
            envelope -> received.add(envelope.payload().action())
        ));

        restartedBus.dispatchUndispatched();

        assertThat(received).containsExactly("replay");
        assertThat(store.findEvent(event.eventId()).orElseThrow().status())
            .isEqualTo(EventRecordStatus.DISPATCHED);
        assertThat(store.findDelivery(event.eventId(), "recording-listener")
            .orElseThrow().status()).isEqualTo(DeliveryStatus.SUCCEEDED);
    }

    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new JdbcSchemaInitializer(dataSource).initialize();
        return dataSource;
    }

    private record TestEvent(String action) implements DomainEvent {
        @Override
        public String eventType() {
            return "test-event";
        }
    }

    private static final class TestEventCodec implements JdbcEventPayloadCodec {
        @Override
        public String encode(DomainEvent event) {
            return ((TestEvent) event).action();
        }

        @Override
        public DomainEvent decode(String payloadClassName, String eventType, String payload) {
            if (!TestEvent.class.getName().equals(payloadClassName)) {
                throw new IllegalArgumentException("Unsupported event class: " + payloadClassName);
            }
            return new TestEvent(payload);
        }
    }

    private static final class IncrementingEventIds implements io.github.openflowkernel.event.EventIdGenerator {
        private long value;

        @Override
        public long nextId() {
            return ++value;
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
