package io.github.openflowkernel.core.engine;

import java.util.Map;

public interface WorkflowEngine {
    void setListener(WorkflowEngineListener listener);

    void start(String processDefinitionKey, String businessKey, Map<String, Object> variables);

    void completeTask(
        String processDefinitionKey,
        String businessKey,
        String taskCode,
        Map<String, Object> variables
    );
}
