package io.github.openflowkernel.core.engine;

public record EngineTaskCompleted(
    String processDefinitionKey,
    String businessKey,
    String taskCode,
    String engineTaskId
) {
}
