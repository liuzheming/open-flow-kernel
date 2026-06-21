package io.github.openflowkernel.core.log;

import java.util.List;

public interface ProcessLogRepository {
    void add(
        long processInstanceId,
        long taskInstanceId,
        String stage,
        String result,
        String content
    );

    default void add(long processInstanceId, long taskInstanceId, String stage, String result) {
        add(processInstanceId, taskInstanceId, stage, result, "");
    }

    default void add(long processInstanceId, String stage, String result, String content) {
        add(processInstanceId, 0, stage, result, content);
    }

    default void add(long processInstanceId, String stage, String result) {
        add(processInstanceId, 0, stage, result, "");
    }

    List<ProcessLogRecord> queryByProcessInstIds(List<Long> processInstanceIds);
}
