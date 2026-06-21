package io.github.openflowkernel.packet;

import java.time.Instant;
import java.util.Objects;

public record PacketRecord(
    long id,
    long processInstanceId,
    long dataPacketValueId,
    PacketStatusEnum status,
    Instant createdAt,
    Instant updatedAt
) {
    public PacketRecord {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
