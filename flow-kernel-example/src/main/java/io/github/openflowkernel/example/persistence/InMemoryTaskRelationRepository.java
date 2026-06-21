package io.github.openflowkernel.example.persistence;

import io.github.openflowkernel.core.relation.TaskRelation;
import io.github.openflowkernel.core.relation.TaskRelationRepository;
import io.github.openflowkernel.core.relation.TaskRelationStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryTaskRelationRepository implements TaskRelationRepository {
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, TaskRelation> relations = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryTaskRelationRepository() {
        this(Clock.systemUTC());
    }

    public InMemoryTaskRelationRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized TaskRelation create(
        long taskInstanceId,
        String relationType,
        String relationInstanceId
    ) {
        long id = ids.incrementAndGet();
        Instant now = clock.instant();
        TaskRelation relation = new TaskRelation(
            id,
            taskInstanceId,
            relationType,
            relationInstanceId,
            TaskRelationStatus.PENDING,
            0,
            now,
            now
        );
        relations.put(id, relation);
        return relation;
    }

    @Override
    public Optional<TaskRelation> find(String relationType, String relationInstanceId) {
        return relations.values().stream()
            .filter(relation -> relation.relationType().equals(relationType))
            .filter(relation -> relation.relationInstanceId().equals(relationInstanceId))
            .findFirst();
    }

    @Override
    public List<TaskRelation> findByTaskInstanceId(long taskInstanceId) {
        return relations.values().stream()
            .filter(relation -> relation.taskInstanceId() == taskInstanceId)
            .toList();
    }

    @Override
    public synchronized boolean compareAndSetCompleted(long relationId) {
        TaskRelation current = relations.get(relationId);
        if (current == null) {
            throw new IllegalArgumentException("Task relation not found: " + relationId);
        }
        if (current.status() == TaskRelationStatus.COMPLETED) {
            return false;
        }
        relations.put(relationId, current.completed(clock.instant()));
        return true;
    }

}
