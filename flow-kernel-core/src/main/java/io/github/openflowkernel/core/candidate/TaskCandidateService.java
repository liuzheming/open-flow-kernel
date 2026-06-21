package io.github.openflowkernel.core.candidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class TaskCandidateService {
    private final TaskCandidateRepository repository;
    private final TaskCandidateSource candidateSource;

    public TaskCandidateService(TaskCandidateRepository repository) {
        this(repository, null);
    }

    public TaskCandidateService(
        TaskCandidateRepository repository,
        TaskCandidateSource candidateSource
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.candidateSource = candidateSource;
    }

    public void refresh(
        String selectionConfig,
        Map<String, String> processInstData,
        long processInstanceId,
        long taskInstanceId
    ) {
        if (candidateSource == null || selectionConfig == null || selectionConfig.isBlank()) {
            return;
        }
        List<TaskCandidate> taskCandidates;
        try {
            taskCandidates = candidateSource.select(
                selectionConfig,
                processInstData == null ? Map.of() : processInstData
            );
        } catch (RuntimeException exception) {
            return;
        }
        if (taskCandidates == null || taskCandidates.isEmpty()) {
            return;
        }
        diffAndUpdateCandidates(
            processInstanceId,
            taskInstanceId,
            taskCandidates
        );
    }

    public void refresh(
        String selectionConfig,
        Map<String, String> processInstData,
        long processInstanceId,
        long taskInstanceId,
        String taskName
    ) {
        refresh(selectionConfig, processInstData, processInstanceId, taskInstanceId);
    }

    public void diffAndUpdateCandidates(
        long processInstanceId,
        long taskInstanceId,
        List<TaskCandidate> taskCandidates
    ) {
        List<TaskCandidate> distinctCandidates = distinctByUcid(
            taskCandidates == null ? Collections.emptyList() : taskCandidates
        );
        List<TaskCandidateRecord> existing = repository.listEntities(taskInstanceId);
        Set<String> existingUcids = existing.stream()
            .map(TaskCandidateRecord::ucid)
            .collect(Collectors.toSet());
        Set<String> newUcids = distinctCandidates.stream()
            .map(TaskCandidate::getUcid)
            .collect(Collectors.toSet());
        List<TaskCandidate> needAdd = distinctCandidates.stream()
            .filter(candidate -> !existingUcids.contains(candidate.getUcid()))
            .toList();
        List<Long> needDelete = existing.stream()
            .filter(candidate -> !newUcids.contains(candidate.ucid()))
            .map(TaskCandidateRecord::id)
            .toList();

        repository.addCandidates(processInstanceId, taskInstanceId, needAdd);
        repository.deleteCandidates(needDelete);
    }

    private static List<TaskCandidate> distinctByUcid(List<TaskCandidate> candidates) {
        Map<String, TaskCandidate> result = new LinkedHashMap<>();
        for (TaskCandidate candidate : candidates) {
            if (candidate.getUcid() != null) {
                result.putIfAbsent(candidate.getUcid(), candidate);
            }
        }
        return new ArrayList<>(result.values());
    }
}
