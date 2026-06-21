package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;

public interface ITask {
    long getProcessInstanceId();

    long getTaskInstanceId();

    String getTaskCode();

    ProcTaskStatusEnum getStatus();

    void complete(TaskResult result);

    void cancel();
}
