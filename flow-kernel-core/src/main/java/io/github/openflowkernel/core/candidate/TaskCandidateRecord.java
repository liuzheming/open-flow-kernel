package io.github.openflowkernel.core.candidate;

import java.time.Instant;
import java.util.Objects;

public record TaskCandidateRecord(
    long id,
    long processInstanceId,
    long processTaskInstanceId,
    String ucid,
    String userCode,
    String username,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
    public TaskCandidateRecord {
        Objects.requireNonNull(ucid, "ucid");
        Objects.requireNonNull(userCode, "userCode");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public TaskCandidate toCandidate() {
        return new TaskCandidate(ucid, userCode, username);
    }
}
