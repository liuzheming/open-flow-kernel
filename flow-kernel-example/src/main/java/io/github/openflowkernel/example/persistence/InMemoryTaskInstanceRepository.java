package io.github.openflowkernel.example.persistence;

import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.core.task.TaskInstanceRecord;
import io.github.openflowkernel.core.task.TaskInstanceRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryTaskInstanceRepository implements TaskInstanceRepository {
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, TaskInstanceRecord> instances = new ConcurrentHashMap<>();
    private final Map<String, Long> idsByRequestId = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryTaskInstanceRepository() {
        this(Clock.systemUTC());
    }

    public InMemoryTaskInstanceRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized TaskInstanceRecord create(
        long processInstanceId,
        String requestId,
        String taskCode,
        String taskName
    ) {
        return create(processInstanceId, requestId, taskCode, taskName, Map.of());
    }

    @Override
    public synchronized TaskInstanceRecord create(
        long processInstanceId,
        String requestId,
        String taskCode,
        String taskName,
        Map<String, String> instData
    ) {
        long id = ids.incrementAndGet();
        Instant now = clock.instant();
        TaskInstanceRecord instance = new TaskInstanceRecord(
            id,
            processInstanceId,
            requestId,
            taskCode,
            taskName,
            ProcTaskStatusEnum.CREATE,
            instData,
            0,
            now,
            now
        );
        instances.put(id, instance);
        idsByRequestId.put(requestId, id);
        return instance;
    }

    @Override
    public Optional<TaskInstanceRecord> findById(long taskInstanceId) {
        return Optional.ofNullable(instances.get(taskInstanceId));
    }

    @Override
    public Optional<TaskInstanceRecord> findByEngineTaskId(String engineTaskId) {
        Long id = idsByRequestId.get(engineTaskId);
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public Optional<TaskInstanceRecord> findLatest(long processInstanceId, String taskCode) {
        return instances.values().stream()
            .filter(instance -> instance.processInstanceId() == processInstanceId)
            .filter(instance -> instance.taskCode().equals(taskCode))
            .max(java.util.Comparator.comparingLong(TaskInstanceRecord::id));
    }

    @Override
    public synchronized boolean compareAndSetStatus(
        long taskInstanceId,
        ProcTaskStatusEnum expected,
        ProcTaskStatusEnum target
    ) {
        TaskInstanceRecord current = required(taskInstanceId);
        if (current.status() != expected) {
            return false;
        }
        instances.put(taskInstanceId, current.withStatus(target, clock.instant()));
        return true;
    }

    @Override
    public synchronized void mergeData(long taskInstanceId, Map<String, String> data) {
        if (data.isEmpty()) {
            return;
        }
        TaskInstanceRecord current = required(taskInstanceId);
        Map<String, String> merged = new LinkedHashMap<>(current.data());
        merged.putAll(data);
        instances.put(taskInstanceId, current.withData(merged, clock.instant()));
    }

    private TaskInstanceRecord required(long taskInstanceId) {
        TaskInstanceRecord instance = instances.get(taskInstanceId);
        if (instance == null) {
            throw new IllegalArgumentException(
                "Task instance not found: " + taskInstanceId
            );
        }
        return instance;
    }
}
