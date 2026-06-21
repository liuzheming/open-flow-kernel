package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;
import io.github.openflowkernel.core.constant.ProcessInstDataKeyConstant;
import io.github.openflowkernel.core.constant.RelationConstant;
import io.github.openflowkernel.core.enums.ProcStatusEnum;
import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessInstance;
import io.github.openflowkernel.core.relation.TaskRelationRepository;
import io.github.openflowkernel.core.support.FlowExpressionEvaluator;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TaskInstance {
    private static final String CURRENT_TASK_INIT_SUCCESS = "currentTaskInitSuccess";
    private static final String CURRENT_TASK_INIT_STATUS = "currentTaskInitStatus";

    private final long taskInstanceId;
    private final TaskPhase phase;
    private final TaskRuntime runtime;
    private final Map<String, String> taskConfig;

    TaskInstance(long taskInstanceId, TaskPhase phase, TaskRuntime runtime) {
        this(taskInstanceId, phase, runtime, Map.of());
    }

    TaskInstance(
        long taskInstanceId,
        TaskPhase phase,
        TaskRuntime runtime,
        Map<String, String> taskConfig
    ) {
        this.taskInstanceId = taskInstanceId;
        this.phase = phase;
        this.runtime = runtime;
        this.taskConfig = Map.copyOf(taskConfig);
    }

    public void init() {
        TaskInstanceRecord record = record();
        if (record.status() != ProcTaskStatusEnum.CREATE) {
            return;
        }
        try {
            TaskResult result = phase.init(context());
            apply(result);
            ProcTaskStatusEnum newStatus = initTargetStatus(result);
            if (!runtime.taskInstanceRepository().compareAndSetStatus(
                taskInstanceId,
                ProcTaskStatusEnum.CREATE,
                newStatus
            )) {
                throw new IllegalStateException("Task initialization raced: " + taskInstanceId);
            }
            initCandidate(context());
        } catch (RuntimeException exception) {
            runtime.taskInstanceRepository().compareAndSetStatus(
                taskInstanceId,
                ProcTaskStatusEnum.CREATE,
                ProcTaskStatusEnum.FAILED
            );
            throw exception;
        }
    }

    public void complete(TaskResult explicitResult) {
        complete(explicitResult, null);
    }

    public void complete(TaskResult explicitResult, TaskContext taskContextInput) {
        TaskInstanceRecord current = record();
        if (current.status() == ProcTaskStatusEnum.COMPLETE) {
            return;
        }
        if (current.status() != ProcTaskStatusEnum.INIT) {
            throw new IllegalStateException(
                "Cannot complete task in status " + current.status()
            );
        }

        outputTaskResult(explicitResult);
        beforeComplete(taskContextInput);

        if (!runtime.taskInstanceRepository().compareAndSetStatus(
            taskInstanceId,
            ProcTaskStatusEnum.INIT,
            ProcTaskStatusEnum.COMPLETE
        )) {
            ProcTaskStatusEnum status = record().status();
            if (status == ProcTaskStatusEnum.COMPLETE) {
                return;
            }
            throw new IllegalStateException("Task completion raced: " + taskInstanceId);
        }

        ProcessInstance process = process();
        if (process.status() != ProcStatusEnum.SUSPEND) {
            runtime.workflowEngine().completeTask(
                process.definitionKey(),
                Long.toString(process.id()),
                taskCode(),
                toEngineVariables(process.data())
            );
        }
    }

    public void postComplete() {
        apply(phase.postComplete(context()));
    }

    public void afterInit() {
        phase.afterInit(context());
    }

    public long createSubProcess(String subProcessDefinitionKey, Map<String, String> inputData) {
        ProcessInstance parent = process();
        ProcessDefinition definition = runtime.processDefinitionRepository()
            .findByKey(subProcessDefinitionKey)
            .orElseThrow(() -> new IllegalArgumentException(
                "Process definition not found: " + subProcessDefinitionKey
            ));
        Map<String, String> processData = new LinkedHashMap<>(
            inputData == null ? Map.of() : inputData
        );
        processData.put(ProcessInstDataKeyConstant.PROC_CODE, definition.key());
        processData.put(ProcessInstDataKeyConstant.PROC_NAME, definition.name());
        processData.putIfAbsent(
            ProcessInstDataKeyConstant.PROC_TYPE,
            ProcessInstDataKeyConstant.PROC_TYPE_RELATE
        );
        ProcessInstance child = runtime.processInstanceRepository().createSubProcess(
            definition.key(),
            definition.name(),
            parent.id(),
            taskInstanceId,
            processData
        );
        registerSubProcessRelation(child.id());
        runtime.workflowEngine().start(
            definition.key(),
            Long.toString(child.id()),
            toEngineVariables(processData)
        );
        return child.id();
    }

    public void beforeComplete(TaskContext taskContextInput) {
        TaskContext taskContext = taskContextInput == null ? context() : taskContextInput;
        apply(phase.beforeComplete(taskContext));
    }

    public void cancel() {
        TaskInstanceRecord current = record();
        if (current.status() == ProcTaskStatusEnum.CANCEL) {
            return;
        }
        if (!runtime.taskInstanceRepository().compareAndSetStatus(
            taskInstanceId,
            current.status(),
            ProcTaskStatusEnum.CANCEL
        )) {
            throw new IllegalStateException("Task cancellation raced: " + taskInstanceId);
        }
    }

    public long processInstanceId() {
        return record().processInstanceId();
    }

    public long taskInstanceId() {
        return taskInstanceId;
    }

    public String taskCode() {
        return record().taskCode();
    }

    public java.util.List<ProcessInstance> findSubProcesses() {
        return runtime.processInstanceRepository().findSubProcesses(
            processInstanceId(),
            taskInstanceId
        );
    }

    public ProcTaskStatusEnum status() {
        return record().status();
    }

    public TaskContext context() {
        TaskInstanceRecord task = record();
        ProcessInstance process = process();
        return new TaskContext(
            process.id(),
            task.id(),
            process.data(),
            task.data(),
            taskConfig,
            task.taskCode()
        );
    }

    private void apply(TaskResult result) {
        if (result == null) {
            return;
        }
        if (!result.taskData().isEmpty()) {
            runtime.taskInstanceRepository().mergeData(taskInstanceId, result.taskData());
        }
        if (!result.processData().isEmpty()) {
            runtime.processInstanceRepository().mergeData(
                processInstanceId(),
                result.processData()
            );
        }
    }

    private void outputTaskResult(TaskResult explicitResult) {
        apply(explicitResult);
        apply(buildOutput());
    }

    private TaskResult buildOutput() {
        String output = taskConfig.get(TaskConfigKeyConstant.OUTPUT);
        if (output == null || output.isBlank()) {
            return TaskResult.empty();
        }
        TaskContext context = context();
        Map<String, String> processOutput = FlowExpressionEvaluator.evaluateMap(
            output,
            context.getProcessInstData(),
            context.getCurrentTaskInstData(),
            Map.of()
        );
        TaskResult result = new TaskResult();
        result.setProcessInstData(processOutput);
        return result;
    }

    private static ProcTaskStatusEnum initTargetStatus(TaskResult result) {
        if (result == null || result.getTaskInstData() == null
            || !result.getTaskInstData().containsKey(CURRENT_TASK_INIT_SUCCESS)) {
            return ProcTaskStatusEnum.INIT;
        }
        String intentionStatus = result.getTaskInstData().get(CURRENT_TASK_INIT_STATUS);
        if (intentionStatus != null && !intentionStatus.isBlank()) {
            return ProcTaskStatusEnum.valueByStatus(Integer.parseInt(intentionStatus));
        }
        boolean success = Boolean.parseBoolean(
            result.getTaskInstData().get(CURRENT_TASK_INIT_SUCCESS)
        );
        return success ? ProcTaskStatusEnum.INIT : ProcTaskStatusEnum.FAILED;
    }

    private void initCandidate(TaskContext taskContext) {
        if (runtime.taskCandidateService() == null) {
            return;
        }
        runtime.taskCandidateService().refresh(
            taskConfig.get(TaskConfigKeyConstant.TASK_CANDIDATE),
            taskContext.getProcessInstData(),
            taskContext.getProcessInstId(),
            taskContext.getTaskInstId(),
            taskConfig.get(TaskConfigKeyConstant.TASK_NAME)
        );
    }

    private TaskInstanceRecord record() {
        return runtime.taskInstanceRepository().findById(taskInstanceId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Task instance not found: " + taskInstanceId
            ));
    }

    private ProcessInstance process() {
        long processInstanceId = record().processInstanceId();
        ProcessInstance process = runtime.processInstanceRepository()
            .findById(processInstanceId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Process instance not found: " + processInstanceId
            ));
        return process;
    }

    private void registerSubProcessRelation(long subProcessInstanceId) {
        TaskRelationRepository relationRepository = runtime.taskRelationRepository();
        if (relationRepository == null) {
            throw new IllegalStateException(
                "TaskRelationRepository is required to create sub process"
            );
        }
        String relationInstanceId = Long.toString(subProcessInstanceId);
        boolean exists = relationRepository.findByTaskInstanceId(taskInstanceId).stream()
            .anyMatch(relation -> RelationConstant.SUB_PROCESS_TASK_RELATION_TYPE
                .equals(relation.relationType())
                && relationInstanceId.equals(relation.relationInstanceId()));
        if (!exists) {
            relationRepository.create(
                taskInstanceId,
                RelationConstant.SUB_PROCESS_TASK_RELATION_TYPE,
                relationInstanceId
            );
        }
    }

    private static Map<String, Object> toEngineVariables(Map<String, String> processData) {
        return new LinkedHashMap<>(processData);
    }
}
