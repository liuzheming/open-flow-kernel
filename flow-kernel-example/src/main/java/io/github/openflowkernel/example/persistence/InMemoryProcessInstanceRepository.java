package io.github.openflowkernel.example.persistence;

import io.github.openflowkernel.core.enums.ProcStatusEnum;
import io.github.openflowkernel.core.process.ProcessInstance;
import io.github.openflowkernel.core.process.ProcessInstanceRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryProcessInstanceRepository implements ProcessInstanceRepository {
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryProcessInstanceRepository() {
        this(Clock.systemUTC());
    }

    public InMemoryProcessInstanceRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ProcessInstance create(
        String definitionKey,
        String name,
        Map<String, String> data
    ) {
        return create(definitionKey, name, 0, 0, data);
    }

    @Override
    public ProcessInstance createSubProcess(
        String definitionKey,
        String name,
        long relateProcessInstanceId,
        long relateTaskInstanceId,
        Map<String, String> data
    ) {
        return create(
            definitionKey,
            name,
            relateProcessInstanceId,
            relateTaskInstanceId,
            data
        );
    }

    private ProcessInstance create(
        String definitionKey,
        String name,
        long relateProcessInstanceId,
        long relateTaskInstanceId,
        Map<String, String> data
    ) {
        long id = ids.incrementAndGet();
        Instant now = clock.instant();
        ProcessInstance instance = new ProcessInstance(
            id,
            definitionKey,
            name,
            ProcStatusEnum.INIT,
            relateProcessInstanceId,
            relateTaskInstanceId,
            data,
            0,
            now,
            now
        );
        instances.put(id, instance);
        return instance;
    }

    @Override
    public Optional<ProcessInstance> findById(long processInstanceId) {
        return Optional.ofNullable(instances.get(processInstanceId));
    }

    @Override
    public List<ProcessInstance> findSubProcesses(long relateProcessInstanceId) {
        return instances.values().stream()
            .filter(instance -> instance.relateProcessInstanceId() == relateProcessInstanceId)
            .toList();
    }

    @Override
    public List<ProcessInstance> findSubProcesses(
        long relateProcessInstanceId,
        long relateTaskInstanceId
    ) {
        return instances.values().stream()
            .filter(instance -> instance.relateProcessInstanceId() == relateProcessInstanceId)
            .filter(instance -> instance.relateTaskInstanceId() == relateTaskInstanceId)
            .toList();
    }

    @Override
    public synchronized boolean compareAndSetStatus(
        long processInstanceId,
        ProcStatusEnum expected,
        ProcStatusEnum target
    ) {
        ProcessInstance current = required(processInstanceId);
        if (current.status() != expected) {
            return false;
        }
        instances.put(processInstanceId, current.withStatus(target, clock.instant()));
        return true;
    }

    @Override
    public synchronized void mergeData(long processInstanceId, Map<String, String> data) {
        if (data.isEmpty()) {
            return;
        }
        ProcessInstance current = required(processInstanceId);
        Map<String, String> merged = new LinkedHashMap<>(current.data());
        merged.putAll(data);
        instances.put(processInstanceId, current.withData(merged, clock.instant()));
    }

    private ProcessInstance required(long processInstanceId) {
        ProcessInstance instance = instances.get(processInstanceId);
        if (instance == null) {
            throw new IllegalArgumentException(
                "Process instance not found: " + processInstanceId
            );
        }
        return instance;
    }
}
