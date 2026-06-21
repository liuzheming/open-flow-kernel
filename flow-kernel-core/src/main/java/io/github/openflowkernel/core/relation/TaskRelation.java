package io.github.openflowkernel.core.relation;

import java.time.Instant;
import java.util.Objects;

public record TaskRelation(
    long id,
    long taskInstanceId,
    String relationType,
    String relationInstanceId,
    TaskRelationStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
    public TaskRelation {
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(relationInstanceId, "relationInstanceId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public TaskRelation completed(Instant now) {
        return new TaskRelation(
            id,
            taskInstanceId,
            relationType,
            relationInstanceId,
            TaskRelationStatus.COMPLETED,
            version + 1,
            createdAt,
            now
        );
    }
}
