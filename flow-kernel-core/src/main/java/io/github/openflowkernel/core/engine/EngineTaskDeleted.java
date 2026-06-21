package io.github.openflowkernel.core.engine;

public record EngineTaskDeleted(
    String processDefinitionKey,
    String businessKey,
    String taskCode,
    String engineTaskId
) {
}
