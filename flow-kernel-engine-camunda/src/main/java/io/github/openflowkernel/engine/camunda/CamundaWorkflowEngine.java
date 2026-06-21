package io.github.openflowkernel.engine.camunda;

import io.github.openflowkernel.core.engine.WorkflowEngine;
import io.github.openflowkernel.core.engine.WorkflowEngineListener;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.task.Task;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CamundaWorkflowEngine implements WorkflowEngine {
    private final String tenantId;
    private ProcessEngine processEngine;
    private WorkflowEngineListener listener;

    public CamundaWorkflowEngine() {
        this(null, null);
    }

    public CamundaWorkflowEngine(ProcessEngine processEngine) {
        this(processEngine, null);
    }

    public CamundaWorkflowEngine(ProcessEngine processEngine, String tenantId) {
        this.processEngine = processEngine;
        this.tenantId = tenantId;
    }

    @Override
    public void setListener(WorkflowEngineListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    WorkflowEngineListener listener() {
        if (listener == null) {
            throw new IllegalStateException("Workflow engine listener is not configured");
        }
        return listener;
    }

    void bind(ProcessEngine processEngine) {
        this.processEngine = Objects.requireNonNull(processEngine);
    }

    @Override
    public void start(
        String processDefinitionKey,
        String businessKey,
        Map<String, Object> variables
    ) {
        ProcessDefinition definition = latestDefinition(processDefinitionKey);
        requiredProcessEngine().getRuntimeService().startProcessInstanceById(
            definition.getId(),
            businessKey,
            variables
        );
    }

    @Override
    public void completeTask(
        String processDefinitionKey,
        String businessKey,
        String taskCode,
        Map<String, Object> variables
    ) {
        List<Task> tasks = requiredProcessEngine().getTaskService().createTaskQuery()
            .processDefinitionKey(processDefinitionKey)
            .processInstanceBusinessKey(businessKey)
            .taskDefinitionKey(taskCode)
            .list();
        if (tasks.isEmpty()) {
            throw new IllegalStateException(
                "Workflow task not found, processDefinitionKey=" + processDefinitionKey
                    + ", businessKey=" + businessKey
                    + ", taskCode=" + taskCode
            );
        }
        requiredProcessEngine().getTaskService().complete(tasks.get(0).getId(), variables);
    }

    private ProcessDefinition latestDefinition(String processDefinitionKey) {
        var query = requiredProcessEngine().getRepositoryService().createProcessDefinitionQuery()
            .processDefinitionKey(processDefinitionKey);
        if (tenantId != null && !tenantId.isBlank()) {
            query.tenantIdIn(tenantId);
        }
        ProcessDefinition definition = query.latestVersion().singleResult();
        if (definition == null) {
            throw new IllegalStateException(
                "Workflow definition not found: " + processDefinitionKey
            );
        }
        return definition;
    }

    ProcessEngine requiredProcessEngine() {
        if (processEngine == null) {
            throw new IllegalStateException("Camunda process engine is not configured");
        }
        return processEngine;
    }
}
