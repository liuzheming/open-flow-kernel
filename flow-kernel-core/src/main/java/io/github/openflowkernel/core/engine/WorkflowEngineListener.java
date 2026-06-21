package io.github.openflowkernel.core.engine;

public interface WorkflowEngineListener {
    void onTaskCreated(EngineTaskCreated event);

    void onTaskCompleted(EngineTaskCompleted event);

    void onTaskDeleted(EngineTaskDeleted event);

    void onProcessCompleted(EngineProcessCompleted event);
}
