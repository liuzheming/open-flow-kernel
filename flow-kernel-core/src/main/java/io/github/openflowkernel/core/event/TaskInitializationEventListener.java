package io.github.openflowkernel.core.event;

import io.github.openflowkernel.core.engine.WorkflowEventListener;
import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessDefinitionRepository;
import io.github.openflowkernel.core.task.AbstractTask;
import io.github.openflowkernel.core.task.TaskContext;
import io.github.openflowkernel.core.task.TaskDefinition;
import io.github.openflowkernel.core.task.TaskFactory;
import io.github.openflowkernel.core.task.TaskInstanceRecord;
import io.github.openflowkernel.core.task.TaskInstanceRepository;
import io.github.openflowkernel.event.EventDraft;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.event.EventListener;
import io.github.openflowkernel.event.EventPublisher;

import java.util.Objects;

public final class TaskInitializationEventListener
    implements EventListener<ProcessTaskEvent> {

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final TaskFactory taskFactory;
    private final EventPublisher eventPublisher;

    public TaskInitializationEventListener(
        ProcessDefinitionRepository processDefinitionRepository,
        TaskInstanceRepository taskInstanceRepository,
        TaskFactory taskFactory,
        EventPublisher eventPublisher
    ) {
        this.processDefinitionRepository = Objects.requireNonNull(
            processDefinitionRepository
        );
        this.taskInstanceRepository = Objects.requireNonNull(taskInstanceRepository);
        this.taskFactory = Objects.requireNonNull(taskFactory);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public void listen(EventEnvelope<ProcessTaskEvent> envelope) {
        ProcessTaskEvent event = envelope.payload();
        if (!event.isCreate()) {
            return;
        }
        AbstractTask latestTask = taskFactory.getTask(event.getProcInstId(), event.getTaskCode());
        if (latestTask == null) {
            throw new IllegalStateException(
                "Task instance not found: " + event.getProcInstId() + "/" + event.getTaskCode()
            );
        }
        TaskInstanceRecord task = taskInstanceRepository.findById(latestTask.getTaskInstanceId())
            .orElseThrow(() -> new IllegalStateException(
                "Task instance not found: " + latestTask.getTaskInstanceId()
            ));
        if (task.status() != ProcTaskStatusEnum.CREATE) {
            return;
        }
        ProcessDefinition process = processDefinitionRepository
            .findByKey(event.getProcCode())
            .orElseThrow(() -> new IllegalStateException(
                "Process definition not found: " + event.getProcCode()
        ));
        TaskDefinition definition = process.task(event.getTaskCode());
        AbstractTask flowTask = taskFactory.load(task, definition);
        flowTask.getTaskInstance().init();
        if (flowTask.isImmediatelyComplete()) {
            TaskContext taskContext = flowTask.getTaskInstance().context();
            flowTask.getTaskInstance().complete(null, taskContext);
        }

        ProcessTaskEvent completeEvent = new ProcessTaskEvent(
            WorkflowEventListener.TASK_INIT_COMPLETE,
            event.getProcInstId(),
            event.getProcCode(),
            event.getTaskCode(),
            flowTask.getTaskInstanceId()
        );
        completeEvent.getBasicInfo().setContextKey(String.valueOf(event.getProcInstId()));
        completeEvent.getBasicInfo().setContextKeyType("PROCESS_INSTANCE_ID");
        eventPublisher.publish(new EventDraft<>(
            completeEvent,
            "task",
            event.getTaskInstId() == null ? null : Long.toString(event.getTaskInstId()),
            String.valueOf(event.getProcInstId()),
            String.valueOf(event.getProcInstId()),
            envelope.eventId()
        ));
    }
}
