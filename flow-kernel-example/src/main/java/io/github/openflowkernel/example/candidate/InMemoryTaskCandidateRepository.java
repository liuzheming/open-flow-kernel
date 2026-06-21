package io.github.openflowkernel.example.candidate;

import io.github.openflowkernel.core.candidate.TaskCandidate;
import io.github.openflowkernel.core.candidate.TaskCandidateRecord;
import io.github.openflowkernel.core.candidate.TaskCandidateRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryTaskCandidateRepository implements TaskCandidateRepository {
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, TaskCandidateRecord> candidates = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryTaskCandidateRepository() {
        this(Clock.systemUTC());
    }

    public InMemoryTaskCandidateRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<TaskCandidate> list(long taskInstanceId) {
        return listEntities(taskInstanceId).stream()
            .map(TaskCandidateRecord::toCandidate)
            .toList();
    }

    @Override
    public List<TaskCandidateRecord> listEntities(long taskInstanceId) {
        return candidates.values().stream()
            .filter(candidate -> candidate.processTaskInstanceId() == taskInstanceId)
            .filter(candidate -> !candidate.deleted())
            .sorted(Comparator.comparingLong(TaskCandidateRecord::id))
            .toList();
    }

    @Override
    public List<TaskCandidate> listByProcessInstId(long processInstanceId) {
        return candidates.values().stream()
            .filter(candidate -> candidate.processInstanceId() == processInstanceId)
            .filter(candidate -> candidate.processTaskInstanceId() == 0)
            .filter(candidate -> !candidate.deleted())
            .sorted(Comparator.comparingLong(TaskCandidateRecord::id))
            .map(TaskCandidateRecord::toCandidate)
            .toList();
    }

    @Override
    public List<TaskCandidate> listAllByProInstId(long processInstanceId) {
        return allListByProcessInstId(processInstanceId);
    }

    @Override
    public List<TaskCandidate> allListByProcessInstId(long processInstanceId) {
        return candidates.values().stream()
            .filter(candidate -> candidate.processInstanceId() == processInstanceId)
            .filter(candidate -> !candidate.deleted())
            .sorted(Comparator.comparingLong(TaskCandidateRecord::id))
            .map(TaskCandidateRecord::toCandidate)
            .toList();
    }

    @Override
    public List<TaskCandidate> distinctCandidateByTaskInstIds(List<Long> taskInstanceIds) {
        Set<Long> taskIds = new HashSet<>(taskInstanceIds);
        Set<String> uniqueUcids = new HashSet<>();
        return candidates.values().stream()
            .filter(candidate -> taskIds.contains(candidate.processTaskInstanceId()))
            .filter(candidate -> !candidate.deleted())
            .sorted(Comparator.comparingLong(TaskCandidateRecord::id))
            .filter(candidate -> uniqueUcids.add(candidate.ucid()))
            .map(TaskCandidateRecord::toCandidate)
            .toList();
    }

    @Override
    public void addCandidates(
        long processInstanceId,
        long taskInstanceId,
        List<TaskCandidate> taskCandidates
    ) {
        Instant now = clock.instant();
        for (TaskCandidate candidate : taskCandidates) {
            long id = ids.incrementAndGet();
            candidates.put(id, new TaskCandidateRecord(
                id,
                processInstanceId,
                taskInstanceId,
                value(candidate.getUcid()),
                value(candidate.getUserCode()),
                value(candidate.getUsername()),
                false,
                now,
                now
            ));
        }
    }

    @Override
    public void deleteCandidates(List<Long> ids) {
        Instant now = clock.instant();
        for (Long id : ids) {
            TaskCandidateRecord current = candidates.get(id);
            if (current != null) {
                candidates.put(id, new TaskCandidateRecord(
                    current.id(),
                    current.processInstanceId(),
                    current.processTaskInstanceId(),
                    current.ucid(),
                    current.userCode(),
                    current.username(),
                    true,
                    current.createdAt(),
                    now
                ));
            }
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
