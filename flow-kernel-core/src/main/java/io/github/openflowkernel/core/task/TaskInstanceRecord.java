package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record TaskInstanceRecord(
    long id,
    long processInstanceId,
    String requestId,
    String taskCode,
    String taskName,
    ProcTaskStatusEnum status,
    Map<String, String> data,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
    public TaskInstanceRecord {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(taskCode, "taskCode");
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(status, "status");
        data = Map.copyOf(data);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public TaskInstanceRecord withStatus(ProcTaskStatusEnum newStatus, Instant now) {
        return new TaskInstanceRecord(
            id,
            processInstanceId,
            requestId,
            taskCode,
            taskName,
            newStatus,
            data,
            version + 1,
            createdAt,
            now
        );
    }

    public TaskInstanceRecord withData(Map<String, String> newData, Instant now) {
        return new TaskInstanceRecord(
            id,
            processInstanceId,
            requestId,
            taskCode,
            taskName,
            status,
            newData,
            version + 1,
            createdAt,
            now
        );
    }
}
