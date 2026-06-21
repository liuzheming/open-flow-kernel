package io.github.openflowkernel.core.relation;

import java.util.List;
import java.util.Optional;

public interface TaskRelationRepository {
    TaskRelation create(long taskInstanceId, String relationType, String relationInstanceId);

    Optional<TaskRelation> find(String relationType, String relationInstanceId);

    List<TaskRelation> findByTaskInstanceId(long taskInstanceId);

    boolean compareAndSetCompleted(long relationId);
}
