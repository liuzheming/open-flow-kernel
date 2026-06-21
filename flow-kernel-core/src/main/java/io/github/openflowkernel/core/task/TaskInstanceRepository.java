package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;

import java.util.Map;
import java.util.Optional;

public interface TaskInstanceRepository {
    TaskInstanceRecord create(
        long processInstanceId,
        String requestId,
        String taskCode,
        String taskName
    );

    TaskInstanceRecord create(
        long processInstanceId,
        String requestId,
        String taskCode,
        String taskName,
        Map<String, String> instData
    );

    Optional<TaskInstanceRecord> findById(long taskInstanceId);

    Optional<TaskInstanceRecord> findByEngineTaskId(String engineTaskId);

    Optional<TaskInstanceRecord> findLatest(long processInstanceId, String taskCode);

    boolean compareAndSetStatus(
        long taskInstanceId,
        ProcTaskStatusEnum expected,
        ProcTaskStatusEnum target
    );

    void mergeData(long taskInstanceId, Map<String, String> data);
}
