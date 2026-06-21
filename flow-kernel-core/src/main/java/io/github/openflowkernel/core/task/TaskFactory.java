package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;
import io.github.openflowkernel.core.constant.TaskInstDataKeyConstant;
import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.core.exception.TaskException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class TaskFactory {
    private final Map<String, Supplier<? extends AbstractTask>> handlers = new LinkedHashMap<>();
    private final TaskRuntime runtime;

    public TaskFactory(TaskRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
    }

    public void register(String handlerName, Supplier<? extends AbstractTask> supplier) {
        Objects.requireNonNull(handlerName, "handlerName");
        Objects.requireNonNull(supplier, "supplier");
        if (handlers.putIfAbsent(handlerName, supplier) != null) {
            throw new IllegalArgumentException("Task handler already registered: " + handlerName);
        }
    }

    public void registerTask(
        String className,
        Supplier<? extends AbstractTask> taskInstanceCreator
    ) {
        register(className, taskInstanceCreator);
    }

    public synchronized AbstractTask createTask(
        Long processInstId,
        String requestId,
        String taskCode,
        Map<String, String> taskConfig
    ) {
        String handlerName = String.valueOf(taskConfig.get(TaskConfigKeyConstant.HANDLER_NAME));
        String taskName = String.valueOf(taskConfig.get(TaskConfigKeyConstant.TASK_NAME));
        AbstractTask task = instantiate(handlerName);

        TaskInstanceRecord latestTask = runtime.taskInstanceRepository()
            .findLatest(processInstId, taskCode)
            .orElse(null);
        TaskInstanceRecord record;
        if (latestTask == null
            || latestTask.status() == ProcTaskStatusEnum.CANCEL
            || latestTask.status() == ProcTaskStatusEnum.FAILED
            || latestTask.requestId().contains("signInBpm-")) {
            record = runtime.taskInstanceRepository()
                .create(processInstId, requestId, taskCode, taskName);
        } else if (latestTask.status() == ProcTaskStatusEnum.INIT
            || latestTask.status() == ProcTaskStatusEnum.PENDING) {
            load(latestTask, definitionFrom(taskCode, taskName, handlerName, taskConfig))
                .cancel();
            record = runtime.taskInstanceRepository()
                .create(processInstId, requestId, taskCode, taskName);
        } else if (latestTask.status() == ProcTaskStatusEnum.COMPLETE) {
            record = runtime.taskInstanceRepository().create(
                processInstId,
                requestId,
                taskCode,
                taskName,
                Map.of(TaskInstDataKeyConstant.LAST_TASK_INST_ID, Long.toString(latestTask.id()))
            );
        } else {
            throw new IllegalStateException("创建任务实例错误，不存在的流程任务状态");
        }

        task.bind(new TaskInstance(record.id(), task, runtime, taskConfig));
        return task;
    }

    public AbstractTask create(TaskInstanceRecord record, TaskDefinition definition) {
        AbstractTask task = instantiate(definition.handlerName());
        task.bind(new TaskInstance(record.id(), task, runtime, definition.config()));
        return task;
    }

    public AbstractTask load(TaskInstanceRecord record, TaskDefinition definition) {
        return create(record, definition);
    }

    public AbstractTask getTask(Long processInstId, String taskCode) {
        TaskInstanceRecord taskInst = runtime.taskInstanceRepository()
            .findLatest(processInstId, taskCode)
            .orElse(null);
        if (taskInst == null) {
            return null;
        }
        return getTask(taskInst);
    }

    public AbstractTask getTask(Long taskInstId) {
        TaskInstanceRecord taskInst = runtime.taskInstanceRepository()
            .findById(taskInstId)
            .orElse(null);
        if (taskInst == null) {
            return null;
        }
        return getTask(taskInst);
    }

    private AbstractTask getTask(TaskInstanceRecord taskInst) {
        return runtime.processInstanceRepository()
            .findById(taskInst.processInstanceId())
            .flatMap(process -> runtime.processDefinitionRepository()
                .findByKey(process.definitionKey())
                .map(definition -> load(taskInst, definition.task(taskInst.taskCode()))))
            .orElse(null);
    }

    private AbstractTask instantiate(String handlerName) {
        Supplier<? extends AbstractTask> supplier = handlers.get(handlerName);
        if (supplier == null) {
            throw new TaskException("Instantiate Task Error: " + handlerName);
        }
        return supplier.get();
    }

    private static TaskDefinition definitionFrom(
        String taskCode,
        String taskName,
        String handlerName,
        Map<String, String> taskConfig
    ) {
        return new TaskDefinition(taskCode, taskName, handlerName, taskConfig);
    }
}
