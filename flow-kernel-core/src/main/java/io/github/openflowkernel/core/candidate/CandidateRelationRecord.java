package io.github.openflowkernel.core.candidate;

public record CandidateRelationRecord(
    long id,
    long processInstanceId,
    String relateInstanceId,
    String ucid,
    String code,
    String name,
    int deleted,
    int type,
    int status,
    String taskName
) {
}
