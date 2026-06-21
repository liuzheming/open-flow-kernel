package io.github.openflowkernel.core.engine;

public record EngineTaskCreated(
    String processDefinitionKey,
    String businessKey,
    String taskCode,
    String engineTaskId
) {
}
