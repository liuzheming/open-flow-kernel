package io.github.openflowkernel.engine.camunda;

import io.github.openflowkernel.core.engine.EngineTaskCompleted;
import io.github.openflowkernel.core.engine.EngineTaskCreated;
import io.github.openflowkernel.core.engine.EngineTaskDeleted;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.repository.ProcessDefinition;

import java.util.Objects;

public final class CamundaWorkflowTaskListener implements TaskListener {
    private final CamundaWorkflowEngine workflowEngine;

    public CamundaWorkflowTaskListener(CamundaWorkflowEngine workflowEngine) {
        this.workflowEngine = Objects.requireNonNull(workflowEngine);
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String processDefinitionKey = processDefinitionKey(delegateTask);
        String businessKey = delegateTask.getExecution().getBusinessKey();
        String taskCode = delegateTask.getTaskDefinitionKey();
        String engineTaskId = delegateTask.getId();

        switch (delegateTask.getEventName()) {
            case EVENTNAME_CREATE -> workflowEngine.listener().onTaskCreated(
                new EngineTaskCreated(
                    processDefinitionKey,
                    businessKey,
                    taskCode,
                    engineTaskId
                )
            );
            case EVENTNAME_COMPLETE -> workflowEngine.listener().onTaskCompleted(
                new EngineTaskCompleted(
                    processDefinitionKey,
                    businessKey,
                    taskCode,
                    engineTaskId
                )
            );
            case EVENTNAME_DELETE -> workflowEngine.listener().onTaskDeleted(
                new EngineTaskDeleted(
                    processDefinitionKey,
                    businessKey,
                    taskCode,
                    engineTaskId
                )
            );
            default -> {
            }
        }
    }

    private String processDefinitionKey(DelegateTask delegateTask) {
        ProcessDefinition definition = workflowEngine.requiredProcessEngine().getRepositoryService()
            .getProcessDefinition(delegateTask.getProcessDefinitionId());
        return definition.getKey();
    }
}
