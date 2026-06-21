package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.relation.TaskRelationCompleteResult;
import io.github.openflowkernel.core.relation.TaskRelationService;

import java.util.List;
import java.util.Objects;

public class ExternalTask extends AbstractTask {
    private final TaskRelationService taskRelationService;

    protected ExternalTask(TaskRelationService taskRelationService) {
        this.taskRelationService = Objects.requireNonNull(taskRelationService);
    }

    public Long registerExternalRelation(String relationType, String relationInstId) {
        return taskRelationService.register(getTaskInstanceId(), relationType, relationInstId);
    }

    public void registerExternalRelations(String relationType, List<String> relationInstIds) {
        for (String relationInstId : relationInstIds) {
            registerExternalRelation(relationType, relationInstId);
        }
    }

    public void complete(String relationType, String relationInstId) {
        TaskRelationCompleteResult result =
            taskRelationService.relationComplete(relationType, relationInstId);
        if (result != null && Boolean.TRUE.equals(result.getAllCompleted())) {
            super.complete(null);
        }
    }
}
