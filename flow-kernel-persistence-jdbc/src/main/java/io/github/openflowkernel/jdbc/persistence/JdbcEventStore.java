package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.event.DeliveryStatus;
import io.github.openflowkernel.event.DomainEvent;
import io.github.openflowkernel.event.EventDelivery;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.event.EventRecord;
import io.github.openflowkernel.event.EventRecordStatus;
import io.github.openflowkernel.event.EventStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcEventStore implements EventStore {
    private final DataSource dataSource;
    private final JdbcEventPayloadCodec payloadCodec;

    public JdbcEventStore(DataSource dataSource, JdbcEventPayloadCodec payloadCodec) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.payloadCodec = Objects.requireNonNull(payloadCodec);
    }

    @Override
    public void record(EventEnvelope<? extends DomainEvent> event) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "insert into event_record("
                     + "event_id, event_type, payload_class, payload, occurred_at, "
                     + "subject_type, subject_id, partition_key, correlation_id, "
                     + "causation_event_id, status, recorded_at"
                     + ") values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
             )) {
            statement.setLong(1, event.eventId());
            statement.setString(2, event.eventType());
            statement.setString(3, event.payload().getClass().getName());
            statement.setString(4, payloadCodec.encode(event.payload()));
            statement.setTimestamp(5, Timestamp.from(event.occurredAt()));
            statement.setString(6, event.subjectType());
            statement.setString(7, event.subjectId());
            statement.setString(8, event.partitionKey());
            statement.setString(9, event.correlationId());
            if (event.causationEventId() == null) {
                statement.setNull(10, java.sql.Types.BIGINT);
            } else {
                statement.setLong(10, event.causationEventId());
            }
            statement.setString(11, EventRecordStatus.RECORDED.name());
            statement.setTimestamp(12, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot record event", exception);
        }
    }

    @Override
    public Optional<EventRecord> findEvent(long eventId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from event_record where event_id = ?"
             )) {
            statement.setLong(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(toEventRecord(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find event", exception);
        }
    }

    @Override
    public List<EventRecord> findUndispatched() {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from event_record where status = ? order by event_id"
             )) {
            statement.setString(1, EventRecordStatus.RECORDED.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<EventRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(toEventRecord(resultSet));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find undispatched events", exception);
        }
    }

    @Override
    public void markDispatched(long eventId, Instant dispatchedAt) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update event_record set status = ?, dispatched_at = ? where event_id = ?"
             )) {
            statement.setString(1, EventRecordStatus.DISPATCHED.name());
            statement.setTimestamp(2, Timestamp.from(dispatchedAt));
            statement.setLong(3, eventId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot mark event dispatched", exception);
        }
    }

    @Override
    public EventDelivery createDelivery(long eventId, String listenerName) {
        findEvent(eventId).orElseThrow(() -> new IllegalArgumentException(
            "Event not found: " + eventId
        ));
        Optional<EventDelivery> existing = findDelivery(eventId, listenerName);
        if (existing.isPresent()) {
            return existing.get();
        }
        EventDelivery delivery = new EventDelivery(
            eventId,
            listenerName,
            DeliveryStatus.PENDING,
            0,
            null,
            null
        );
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "insert into event_delivery("
                     + "event_id, listener_name, status, attempts, next_attempt_at, last_error"
                     + ") values(?, ?, ?, ?, ?, ?)"
             )) {
            bindDelivery(statement, delivery);
            statement.executeUpdate();
            return delivery;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot create event delivery", exception);
        }
    }

    @Override
    public Optional<EventDelivery> findDelivery(long eventId, String listenerName) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from event_delivery where event_id = ? and listener_name = ?"
             )) {
            statement.setLong(1, eventId);
            statement.setString(2, listenerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(toDelivery(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find event delivery", exception);
        }
    }

    @Override
    public List<EventDelivery> findDueDeliveries(Instant now) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from event_delivery "
                     + "where status = ? and next_attempt_at <= ? order by event_id"
             )) {
            statement.setString(1, DeliveryStatus.RETRY_WAIT.name());
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<EventDelivery> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(toDelivery(resultSet));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find due event deliveries", exception);
        }
    }

    @Override
    public void markSucceeded(long eventId, String listenerName, int attempts) {
        updateDelivery(new EventDelivery(
            eventId,
            listenerName,
            DeliveryStatus.SUCCEEDED,
            attempts,
            null,
            null
        ));
    }

    @Override
    public void scheduleRetry(
        long eventId,
        String listenerName,
        int attempts,
        Instant nextAttemptAt,
        String lastError
    ) {
        updateDelivery(new EventDelivery(
            eventId,
            listenerName,
            DeliveryStatus.RETRY_WAIT,
            attempts,
            nextAttemptAt,
            lastError
        ));
    }

    @Override
    public void markFailedPermanently(
        long eventId,
        String listenerName,
        int attempts,
        String lastError
    ) {
        updateDelivery(new EventDelivery(
            eventId,
            listenerName,
            DeliveryStatus.FAILED_PERMANENTLY,
            attempts,
            null,
            lastError
        ));
    }

    private void updateDelivery(EventDelivery delivery) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update event_delivery set status = ?, attempts = ?, "
                     + "next_attempt_at = ?, last_error = ? "
                     + "where event_id = ? and listener_name = ?"
             )) {
            statement.setString(1, delivery.status().name());
            statement.setInt(2, delivery.attempts());
            bindInstant(statement, 3, delivery.nextAttemptAt());
            statement.setString(4, delivery.lastError());
            statement.setLong(5, delivery.eventId());
            statement.setString(6, delivery.listenerName());
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException(
                    "Delivery not found: " + delivery.eventId() + "/"
                        + delivery.listenerName()
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot update event delivery", exception);
        }
    }

    private EventRecord toEventRecord(ResultSet resultSet) throws SQLException {
        DomainEvent payload = payloadCodec.decode(
            resultSet.getString("payload_class"),
            resultSet.getString("event_type"),
            resultSet.getString("payload")
        );
        EventEnvelope<DomainEvent> envelope = new EventEnvelope<>(
            resultSet.getLong("event_id"),
            payload,
            JdbcSupport.instant(resultSet, "occurred_at"),
            resultSet.getString("subject_type"),
            resultSet.getString("subject_id"),
            resultSet.getString("partition_key"),
            resultSet.getString("correlation_id"),
            nullableLong(resultSet, "causation_event_id")
        );
        return new EventRecord(
            envelope,
            EventRecordStatus.valueOf(resultSet.getString("status")),
            JdbcSupport.instant(resultSet, "recorded_at"),
            nullableInstant(resultSet, "dispatched_at")
        );
    }

    private static EventDelivery toDelivery(ResultSet resultSet) throws SQLException {
        return new EventDelivery(
            resultSet.getLong("event_id"),
            resultSet.getString("listener_name"),
            DeliveryStatus.valueOf(resultSet.getString("status")),
            resultSet.getInt("attempts"),
            nullableInstant(resultSet, "next_attempt_at"),
            resultSet.getString("last_error")
        );
    }

    private static void bindDelivery(PreparedStatement statement, EventDelivery delivery)
        throws SQLException {
        statement.setLong(1, delivery.eventId());
        statement.setString(2, delivery.listenerName());
        statement.setString(3, delivery.status().name());
        statement.setInt(4, delivery.attempts());
        bindInstant(statement, 5, delivery.nextAttemptAt());
        statement.setString(6, delivery.lastError());
    }

    private static void bindInstant(
        PreparedStatement statement,
        int index,
        Instant value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static Instant nullableInstant(ResultSet resultSet, String column)
        throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
