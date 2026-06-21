package io.github.openflowkernel.core.process;

import io.github.openflowkernel.core.enums.ProcStatusEnum;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ProcessInstance(
    long id,
    String definitionKey,
    String name,
    ProcStatusEnum status,
    long relateProcessInstanceId,
    long relateTaskInstanceId,
    Map<String, String> data,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
    public ProcessInstance {
        Objects.requireNonNull(definitionKey, "definitionKey");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        data = Map.copyOf(data);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public ProcessInstance withStatus(ProcStatusEnum newStatus, Instant now) {
        return new ProcessInstance(
            id,
            definitionKey,
            name,
            newStatus,
            relateProcessInstanceId,
            relateTaskInstanceId,
            data,
            version + 1,
            createdAt,
            now
        );
    }

    public ProcessInstance withData(Map<String, String> newData, Instant now) {
        return new ProcessInstance(
            id,
            definitionKey,
            name,
            status,
            relateProcessInstanceId,
            relateTaskInstanceId,
            newData,
            version + 1,
            createdAt,
            now
        );
    }
}
