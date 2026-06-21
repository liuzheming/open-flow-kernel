package io.github.openflowkernel.core.process;

import java.util.List;
import java.util.Optional;

public interface ProcessInstanceRelationRepository {
    void addRelation(
        long processInstanceId,
        String relationType,
        String relationCode,
        String relationInstanceId
    );

    List<ProcessInstanceRelationRecord> queryRelationList(
        String relationType,
        String relationCode,
        String relationInstanceId,
        int limit
    );

    Optional<ProcessInstanceRelationRecord> getProcessInstRelationEntity(
        long processInstanceId
    );

    List<ProcessInstanceRelationRecord> getRelationEntitiesByInstId(
        long processInstanceId
    );
}
