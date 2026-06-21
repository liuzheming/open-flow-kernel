package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.event.DomainEvent;

public interface JdbcEventPayloadCodec {
    String encode(DomainEvent event);

    DomainEvent decode(String payloadClassName, String eventType, String payload);
}
