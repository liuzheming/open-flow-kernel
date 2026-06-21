package io.github.openflowkernel.example.engine;

import io.github.openflowkernel.core.engine.EngineProcessCompleted;
import io.github.openflowkernel.core.engine.EngineTaskCompleted;
import io.github.openflowkernel.core.engine.EngineTaskCreated;
import io.github.openflowkernel.core.engine.WorkflowEngine;
import io.github.openflowkernel.core.engine.WorkflowEngineListener;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.task.TaskDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryWorkflowEngine implements WorkflowEngine {
    private final Map<String, ProcessDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Execution> executions = new LinkedHashMap<>();
    private final AtomicLong taskIds = new AtomicLong();
    private WorkflowEngineListener listener;

    public void deploy(ProcessDefinition definition) {
        definitions.put(definition.key(), definition);
    }

    @Override
    public void setListener(WorkflowEngineListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    @Override
    public synchronized void start(
        String processDefinitionKey,
        String businessKey,
        Map<String, Object> variables
    ) {
        ProcessDefinition definition = requiredDefinition(processDefinitionKey);
        if (definition.tasks().isEmpty()) {
            requiredListener().onProcessCompleted(
                new EngineProcessCompleted(processDefinitionKey, businessKey)
            );
            return;
        }
        Execution execution = new Execution(definition, businessKey, 0, null, false);
        executions.put(executionKey(processDefinitionKey, businessKey), execution);
        activate(execution);
    }

    @Override
    public synchronized void completeTask(
        String processDefinitionKey,
        String businessKey,
        String taskCode,
        Map<String, Object> variables
    ) {
        String key = executionKey(processDefinitionKey, businessKey);
        Execution execution = executions.get(key);
        if (execution == null || execution.completed()) {
            return;
        }
        TaskDefinition activeTask = execution.definition().tasks().get(execution.taskIndex());
        if (!activeTask.code().equals(taskCode)) {
            throw new IllegalStateException(
                "Expected active task " + activeTask.code() + " but got " + taskCode
            );
        }

        requiredListener().onTaskCompleted(new EngineTaskCompleted(
            processDefinitionKey,
            businessKey,
            taskCode,
            execution.engineTaskId()
        ));

        int nextIndex = execution.taskIndex() + 1;
        if (nextIndex >= execution.definition().tasks().size()) {
            executions.put(key, execution.completedExecution());
            requiredListener().onProcessCompleted(
                new EngineProcessCompleted(processDefinitionKey, businessKey)
            );
            return;
        }
        Execution next = execution.next(nextIndex);
        executions.put(key, next);
        activate(next);
    }

    private void activate(Execution execution) {
        TaskDefinition task = execution.definition().tasks().get(execution.taskIndex());
        String engineTaskId = "memory-task-" + taskIds.incrementAndGet();
        Execution active = execution.withEngineTaskId(engineTaskId);
        executions.put(
            executionKey(execution.definition().key(), execution.businessKey()),
            active
        );
        requiredListener().onTaskCreated(new EngineTaskCreated(
            execution.definition().key(),
            execution.businessKey(),
            task.code(),
            engineTaskId
        ));
    }

    private ProcessDefinition requiredDefinition(String key) {
        ProcessDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Engine definition not deployed: " + key);
        }
        return definition;
    }

    private WorkflowEngineListener requiredListener() {
        if (listener == null) {
            throw new IllegalStateException("Workflow engine listener is not configured");
        }
        return listener;
    }

    private static String executionKey(String processDefinitionKey, String businessKey) {
        return processDefinitionKey + '\u0000' + businessKey;
    }

    private record Execution(
        ProcessDefinition definition,
        String businessKey,
        int taskIndex,
        String engineTaskId,
        boolean completed
    ) {
        private Execution withEngineTaskId(String value) {
            return new Execution(definition, businessKey, taskIndex, value, completed);
        }

        private Execution next(int index) {
            return new Execution(definition, businessKey, index, null, false);
        }

        private Execution completedExecution() {
            return new Execution(definition, businessKey, taskIndex, engineTaskId, true);
        }
    }
}
