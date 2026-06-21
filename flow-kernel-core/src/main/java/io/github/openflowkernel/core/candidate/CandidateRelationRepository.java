package io.github.openflowkernel.core.candidate;

import java.util.List;

public interface CandidateRelationRepository {
    List<TaskCandidate> list(String relateInstanceId, int type);

    List<CandidateRelationRecord> listEntities(String relateInstanceId, int type);

    List<CandidateRelationRecord> listByUcId(String ucid);

    void addCandidates(
        long processInstanceId,
        String relateInstanceId,
        String relateInstanceName,
        int type,
        List<TaskCandidate> candidates,
        int status
    );

    void updateCandidateStatus(String relateInstanceId, int type, int status);

    void updateCandidateDeleted(String relateInstanceId, int type, int deleted);

    void deleteCandidates(List<Long> ids);

    List<TaskCandidate> distinctCandidateByActionItemNos(
        List<String> relateInstanceIds,
        int type
    );
}
