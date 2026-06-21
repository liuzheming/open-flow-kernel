package io.github.openflowkernel.core.process;

public record ProcessInstanceRelationRecord(
    long id,
    long processInstanceId,
    String relationCode,
    String relationType,
    String relationInstanceId,
    long processDefinitionId
) {
}
