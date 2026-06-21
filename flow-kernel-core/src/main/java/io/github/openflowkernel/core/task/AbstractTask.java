package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;

import java.util.Objects;

public abstract class AbstractTask implements ITask, TaskPhase {
    private TaskInstance taskInstance;

    final void bind(TaskInstance taskInstance) {
        if (this.taskInstance != null) {
            throw new IllegalStateException("Task is already bound");
        }
        this.taskInstance = Objects.requireNonNull(taskInstance);
    }

    protected final TaskInstance taskInstance() {
        if (taskInstance == null) {
            throw new IllegalStateException("Task is not bound to a task instance");
        }
        return taskInstance;
    }

    public final TaskInstance getTaskInstance() {
        return taskInstance();
    }

    @Override
    public final long getProcessInstanceId() {
        return taskInstance().processInstanceId();
    }

    @Override
    public final long getTaskInstanceId() {
        return taskInstance().taskInstanceId();
    }

    @Override
    public final String getTaskCode() {
        return taskInstance().taskCode();
    }

    @Override
    public final ProcTaskStatusEnum getStatus() {
        return taskInstance().status();
    }

    @Override
    public final void complete(TaskResult result) {
        taskInstance().complete(result == null ? TaskResult.empty() : result);
    }

    @Override
    public void cancel() {
        taskInstance().cancel();
    }

    public Boolean isImmediatelyComplete() {
        return false;
    }
}
