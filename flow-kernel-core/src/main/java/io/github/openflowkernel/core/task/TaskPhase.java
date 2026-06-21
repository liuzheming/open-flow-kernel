package io.github.openflowkernel.core.task;

public interface TaskPhase {
    default TaskResult init(TaskContext context) {
        return TaskResult.empty();
    }

    default void afterInit(TaskContext context) {
    }

    default TaskResult beforeComplete(TaskContext context) {
        return TaskResult.empty();
    }

    default TaskResult postComplete(TaskContext context) {
        return TaskResult.empty();
    }
}
