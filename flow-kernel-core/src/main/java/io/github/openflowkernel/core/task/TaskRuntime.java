package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.candidate.TaskCandidateService;
import io.github.openflowkernel.core.engine.WorkflowEngine;
import io.github.openflowkernel.core.process.ProcessDefinitionRepository;
import io.github.openflowkernel.core.process.ProcessInstanceRepository;
import io.github.openflowkernel.core.relation.TaskRelationRepository;

import java.util.Objects;

public record TaskRuntime(
    ProcessDefinitionRepository processDefinitionRepository,
    ProcessInstanceRepository processInstanceRepository,
    TaskInstanceRepository taskInstanceRepository,
    WorkflowEngine workflowEngine,
    TaskRelationRepository taskRelationRepository,
    TaskCandidateService taskCandidateService
) {
    public TaskRuntime(
        ProcessDefinitionRepository processDefinitionRepository,
        ProcessInstanceRepository processInstanceRepository,
        TaskInstanceRepository taskInstanceRepository,
        WorkflowEngine workflowEngine
    ) {
        this(
            processDefinitionRepository,
            processInstanceRepository,
            taskInstanceRepository,
            workflowEngine,
            null,
            null
        );
    }

    public TaskRuntime(
        ProcessDefinitionRepository processDefinitionRepository,
        ProcessInstanceRepository processInstanceRepository,
        TaskInstanceRepository taskInstanceRepository,
        WorkflowEngine workflowEngine,
        TaskRelationRepository taskRelationRepository
    ) {
        this(
            processDefinitionRepository,
            processInstanceRepository,
            taskInstanceRepository,
            workflowEngine,
            taskRelationRepository,
            null
        );
    }

    public TaskRuntime {
        Objects.requireNonNull(processDefinitionRepository);
        Objects.requireNonNull(processInstanceRepository);
        Objects.requireNonNull(taskInstanceRepository);
        Objects.requireNonNull(workflowEngine);
    }
}
