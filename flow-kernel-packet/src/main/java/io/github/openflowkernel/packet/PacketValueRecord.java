package io.github.openflowkernel.packet;

import java.time.Instant;
import java.util.Objects;

public record PacketValueRecord(
    long id,
    long processInstanceId,
    long processTaskInstanceId,
    String taskCode,
    String initValue,
    String value,
    String initSource,
    String source,
    PacketValueStatusEnum status,
    Instant createdAt,
    Instant updatedAt
) {
    public PacketValueRecord {
        Objects.requireNonNull(taskCode, "taskCode");
        Objects.requireNonNull(initValue, "initValue");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(initSource, "initSource");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
