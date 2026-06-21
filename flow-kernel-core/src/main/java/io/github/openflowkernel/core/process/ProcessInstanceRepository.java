package io.github.openflowkernel.core.process;

import io.github.openflowkernel.core.enums.ProcStatusEnum;

import java.util.Map;
import java.util.Optional;
import java.util.List;

public interface ProcessInstanceRepository {
    ProcessInstance create(String definitionKey, String name, Map<String, String> data);

    ProcessInstance createSubProcess(
        String definitionKey,
        String name,
        long relateProcessInstanceId,
        long relateTaskInstanceId,
        Map<String, String> data
    );

    Optional<ProcessInstance> findById(long processInstanceId);

    List<ProcessInstance> findSubProcesses(long relateProcessInstanceId);

    List<ProcessInstance> findSubProcesses(long relateProcessInstanceId, long relateTaskInstanceId);

    boolean compareAndSetStatus(
        long processInstanceId,
        ProcStatusEnum expected,
        ProcStatusEnum target
    );

    void mergeData(long processInstanceId, Map<String, String> data);
}
