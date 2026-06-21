package io.github.openflowkernel.core.relation;

import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessDefinitionRepository;
import io.github.openflowkernel.core.process.ProcessInstance;
import io.github.openflowkernel.core.process.ProcessInstanceRepository;
import io.github.openflowkernel.core.task.AbstractTask;
import io.github.openflowkernel.core.task.TaskDefinition;
import io.github.openflowkernel.core.task.TaskFactory;
import io.github.openflowkernel.core.task.TaskInstanceRecord;
import io.github.openflowkernel.core.task.TaskInstanceRepository;

import java.util.List;
import java.util.Objects;

public final class TaskRelationService {
    private final TaskRelationRepository relationRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final TaskFactory taskFactory;

    public TaskRelationService(
        TaskRelationRepository relationRepository,
        TaskInstanceRepository taskInstanceRepository,
        ProcessInstanceRepository processInstanceRepository,
        ProcessDefinitionRepository processDefinitionRepository,
        TaskFactory taskFactory
    ) {
        this.relationRepository = Objects.requireNonNull(relationRepository);
        this.taskInstanceRepository = Objects.requireNonNull(taskInstanceRepository);
        this.processInstanceRepository = Objects.requireNonNull(processInstanceRepository);
        this.processDefinitionRepository = Objects.requireNonNull(processDefinitionRepository);
        this.taskFactory = Objects.requireNonNull(taskFactory);
    }

    public Long register(long taskInstanceId, String relationType, String relationInstanceId) {
        boolean exists = relationRepository.findByTaskInstanceId(taskInstanceId).stream()
            .anyMatch(relation -> relation.relationType().equals(relationType)
                && relation.relationInstanceId().equals(relationInstanceId));
        if (exists) {
            return null;
        }
        return relationRepository.create(taskInstanceId, relationType, relationInstanceId).id();
    }

    public void complete(String relationType, String relationInstanceId) {
        TaskRelationCompleteResult result = relationComplete(relationType, relationInstanceId);
        if (result != null && Boolean.TRUE.equals(result.getAllCompleted())) {
            result.getTask().complete(null);
        }
    }

    public TaskRelationCompleteResult relationComplete(
        String relationType,
        String relationInstanceId
    ) {
        TaskRelation relation = relationRepository.find(relationType, relationInstanceId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Task relation not found: " + relationType + "/" + relationInstanceId
            ));
        boolean repeatedComplete = relation.status() == TaskRelationStatus.COMPLETED;
        if (relation.status() == TaskRelationStatus.PENDING) {
            relationRepository.compareAndSetCompleted(relation.id());
        }

        List<TaskRelation> relations = relationRepository.findByTaskInstanceId(
            relation.taskInstanceId()
        );
        boolean allCompleted = !relations.isEmpty() && relations.stream()
            .allMatch(item -> item.status() == TaskRelationStatus.COMPLETED);
        TaskRelationCompleteResult result = new TaskRelationCompleteResult();
        result.setAllCompleted(allCompleted);
        result.setRepeatedComplete(repeatedComplete);
        if (!allCompleted) {
            return result;
        }

        TaskInstanceRecord taskRecord = taskInstanceRepository
            .findById(relation.taskInstanceId())
            .orElseThrow(() -> new IllegalStateException(
                "Task instance not found: " + relation.taskInstanceId()
            ));
        if (taskRecord.status() != ProcTaskStatusEnum.INIT) {
            result.setAllCompleted(false);
            return result;
        }
        ProcessInstance process = processInstanceRepository
            .findById(taskRecord.processInstanceId())
            .orElseThrow(() -> new IllegalStateException(
                "Process instance not found: " + taskRecord.processInstanceId()
            ));
        ProcessDefinition processDefinition = processDefinitionRepository
            .findByKey(process.definitionKey())
            .orElseThrow(() -> new IllegalStateException(
                "Process definition not found: " + process.definitionKey()
            ));
        TaskDefinition taskDefinition = processDefinition.task(taskRecord.taskCode());
        AbstractTask task = taskFactory.load(taskRecord, taskDefinition);
        result.setTask(task);
        return result;
    }
}
