package io.github.openflowkernel.core.log;

import java.time.Instant;

public record ProcessLogRecord(
    long id,
    long processInstanceId,
    long taskInstanceId,
    String stage,
    String result,
    String content,
    Instant createdAt,
    Instant updatedAt
) {
}
