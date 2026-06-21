package io.github.openflowkernel.core.event;

import io.github.openflowkernel.core.engine.WorkflowEventListener;
import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.core.task.AbstractTask;
import io.github.openflowkernel.core.task.TaskFactory;
import io.github.openflowkernel.core.task.TaskInstanceRecord;
import io.github.openflowkernel.core.task.TaskInstanceRepository;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.event.EventListener;

import java.util.Objects;

public final class TaskInitCompleteEventListener implements EventListener<ProcessTaskEvent> {
    private final TaskInstanceRepository taskInstanceRepository;
    private final TaskFactory taskFactory;

    public TaskInitCompleteEventListener(
        TaskInstanceRepository taskInstanceRepository,
        TaskFactory taskFactory
    ) {
        this.taskInstanceRepository = Objects.requireNonNull(taskInstanceRepository);
        this.taskFactory = Objects.requireNonNull(taskFactory);
    }

    @Override
    public void listen(EventEnvelope<ProcessTaskEvent> envelope) {
        ProcessTaskEvent event = envelope.payload();
        if (!WorkflowEventListener.TASK_INIT_COMPLETE.equals(event.getTaskEventName())) {
            return;
        }
        AbstractTask task = task(event);
        if (task == null || task.getStatus() != ProcTaskStatusEnum.INIT) {
            return;
        }
        task.getTaskInstance().afterInit();
    }

    private AbstractTask task(ProcessTaskEvent event) {
        if (event.getTaskInstId() != null) {
            return taskFactory.getTask(event.getTaskInstId());
        }
        return taskFactory.getTask(event.getProcInstId(), event.getTaskCode());
    }
}
