package io.github.openflowkernel.core.candidate;

import java.util.List;

public interface TaskCandidateRepository {
    List<TaskCandidate> list(long taskInstanceId);

    List<TaskCandidateRecord> listEntities(long taskInstanceId);

    List<TaskCandidate> listByProcessInstId(long processInstanceId);

    List<TaskCandidate> listAllByProInstId(long processInstanceId);

    List<TaskCandidate> allListByProcessInstId(long processInstanceId);

    List<TaskCandidate> distinctCandidateByTaskInstIds(List<Long> taskInstanceIds);

    void addCandidates(
        long processInstanceId,
        long taskInstanceId,
        List<TaskCandidate> candidates
    );

    void deleteCandidates(List<Long> ids);
}
