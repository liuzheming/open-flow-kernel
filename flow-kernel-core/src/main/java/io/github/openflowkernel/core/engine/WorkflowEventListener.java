package io.github.openflowkernel.core.engine;

import io.github.openflowkernel.core.event.ProcessTaskEvent;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessDefinitionRepository;
import io.github.openflowkernel.core.process.ProcessService;
import io.github.openflowkernel.core.task.AbstractTask;
import io.github.openflowkernel.core.task.TaskDefinition;
import io.github.openflowkernel.core.task.TaskFactory;
import io.github.openflowkernel.core.task.TaskInstanceRecord;
import io.github.openflowkernel.core.task.TaskInstanceRepository;
import io.github.openflowkernel.event.EventDraft;
import io.github.openflowkernel.event.EventPublisher;

import java.util.Objects;

public final class WorkflowEventListener implements WorkflowEngineListener {
    public static final String CREATE = "create";
    public static final String COMPLETE = "complete";
    public static final String DELETE = "delete";
    public static final String END = "end";
    public static final String TASK_INIT_COMPLETE = "taskInitComplete";

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final TaskFactory taskFactory;
    private final ProcessService processService;
    private final EventPublisher eventPublisher;

    public WorkflowEventListener(
        ProcessDefinitionRepository processDefinitionRepository,
        TaskInstanceRepository taskInstanceRepository,
        TaskFactory taskFactory,
        ProcessService processService,
        EventPublisher eventPublisher
    ) {
        this.processDefinitionRepository = Objects.requireNonNull(processDefinitionRepository);
        this.taskInstanceRepository = Objects.requireNonNull(taskInstanceRepository);
        this.taskFactory = Objects.requireNonNull(taskFactory);
        this.processService = Objects.requireNonNull(processService);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public void onTaskCreated(EngineTaskCreated event) {
        long processInstanceId = Long.parseLong(event.businessKey());
        TaskDefinition definition = processDefinition(event.processDefinitionKey())
            .task(event.taskCode());
        AbstractTask task = taskFactory.createTask(
            processInstanceId,
            event.engineTaskId(),
            definition.code(),
            definition.config()
        );
        ProcessTaskEvent processTaskEvent = new ProcessTaskEvent(
            CREATE,
            processInstanceId,
            event.processDefinitionKey(),
            definition.code(),
            task.getTaskInstanceId()
        );
        processTaskEvent.getBasicInfo().setContextKey(Long.toString(processInstanceId));
        processTaskEvent.getBasicInfo().setContextKeyType("PROCESS_INSTANCE_ID");
        eventPublisher.publish(new EventDraft<>(
            processTaskEvent,
            "task",
            Long.toString(task.getTaskInstanceId()),
            Long.toString(processInstanceId),
            Long.toString(processInstanceId),
            null
        ));
    }

    @Override
    public void onTaskCompleted(EngineTaskCompleted event) {
        TaskInstanceRecord record = taskInstanceRepository
            .findByEngineTaskId(event.engineTaskId())
            .orElseThrow(() -> new IllegalStateException(
                "Business task not found for engine task: " + event.engineTaskId()
            ));
        TaskDefinition definition = processDefinition(event.processDefinitionKey())
            .task(event.taskCode());
        taskFactory.load(record, definition).getTaskInstance().postComplete();
        ProcessTaskEvent processTaskEvent = new ProcessTaskEvent(
            COMPLETE,
            Long.parseLong(event.businessKey()),
            event.processDefinitionKey(),
            event.taskCode(),
            record.id()
        );
        processTaskEvent.getBasicInfo().setContextKey(event.businessKey());
        processTaskEvent.getBasicInfo().setContextKeyType("PROCESS_INSTANCE_ID");
        eventPublisher.publish(new EventDraft<>(
            processTaskEvent,
            "task",
            Long.toString(record.id()),
            event.businessKey(),
            event.businessKey(),
            null
        ));
    }

    @Override
    public void onTaskDeleted(EngineTaskDeleted event) {
        long processInstanceId = Long.parseLong(event.businessKey());
        AbstractTask task = taskFactory.getTask(processInstanceId, event.taskCode());
        if (task == null) {
            return;
        }
        task.cancel();
        ProcessTaskEvent processTaskEvent = new ProcessTaskEvent(
            DELETE,
            processInstanceId,
            event.processDefinitionKey(),
            event.taskCode(),
            task.getTaskInstanceId()
        );
        processTaskEvent.getBasicInfo().setContextKey(event.businessKey());
        processTaskEvent.getBasicInfo().setContextKeyType("PROCESS_INSTANCE_ID");
        eventPublisher.publish(new EventDraft<>(
            processTaskEvent,
            "task",
            Long.toString(task.getTaskInstanceId()),
            event.businessKey(),
            event.businessKey(),
            null
        ));
    }

    @Override
    public void onProcessCompleted(EngineProcessCompleted event) {
        processService.complete(Long.parseLong(event.businessKey()));
    }

    private ProcessDefinition processDefinition(String key) {
        return processDefinitionRepository.findByKey(key)
            .orElseThrow(() -> new IllegalArgumentException(
                "Process definition not found: " + key
            ));
    }
}
