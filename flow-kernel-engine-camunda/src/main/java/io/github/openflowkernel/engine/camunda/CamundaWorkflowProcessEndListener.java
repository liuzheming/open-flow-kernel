package io.github.openflowkernel.engine.camunda;

import io.github.openflowkernel.core.engine.EngineProcessCompleted;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.repository.ProcessDefinition;

import java.util.Objects;

public final class CamundaWorkflowProcessEndListener implements ExecutionListener {
    private final CamundaWorkflowEngine workflowEngine;

    public CamundaWorkflowProcessEndListener(CamundaWorkflowEngine workflowEngine) {
        this.workflowEngine = Objects.requireNonNull(workflowEngine);
    }

    @Override
    public void notify(DelegateExecution execution) {
        if (!EVENTNAME_END.equals(execution.getEventName())) {
            return;
        }
        ProcessDefinition definition = workflowEngine.requiredProcessEngine().getRepositoryService()
            .getProcessDefinition(execution.getProcessDefinitionId());
        workflowEngine.listener().onProcessCompleted(
            new EngineProcessCompleted(definition.getKey(), execution.getBusinessKey())
        );
    }
}
