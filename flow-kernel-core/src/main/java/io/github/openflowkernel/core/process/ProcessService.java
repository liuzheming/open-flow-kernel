package io.github.openflowkernel.core.process;

import io.github.openflowkernel.core.engine.WorkflowEngine;
import io.github.openflowkernel.core.enums.ProcStatusEnum;
import io.github.openflowkernel.core.enums.ProcessEventEnum;
import io.github.openflowkernel.core.event.ProcessEvent;
import io.github.openflowkernel.event.EventDraft;
import io.github.openflowkernel.event.EventPublisher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProcessService {
    private final ProcessDefinitionRepository definitionRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final WorkflowEngine workflowEngine;
    private final EventPublisher eventPublisher;
    private final List<ProcessStartHook> startHooks;

    public ProcessService(
        ProcessDefinitionRepository definitionRepository,
        ProcessInstanceRepository instanceRepository,
        WorkflowEngine workflowEngine
    ) {
        this(definitionRepository, instanceRepository, workflowEngine, null);
    }

    public ProcessService(
        ProcessDefinitionRepository definitionRepository,
        ProcessInstanceRepository instanceRepository,
        WorkflowEngine workflowEngine,
        EventPublisher eventPublisher
    ) {
        this(
            definitionRepository,
            instanceRepository,
            workflowEngine,
            eventPublisher,
            List.of()
        );
    }

    public ProcessService(
        ProcessDefinitionRepository definitionRepository,
        ProcessInstanceRepository instanceRepository,
        WorkflowEngine workflowEngine,
        EventPublisher eventPublisher,
        List<ProcessStartHook> startHooks
    ) {
        this.definitionRepository = Objects.requireNonNull(definitionRepository);
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.workflowEngine = Objects.requireNonNull(workflowEngine);
        this.eventPublisher = eventPublisher;
        this.startHooks = startHooks == null ? List.of() : List.copyOf(startHooks);
    }

    public long start(String definitionKey, Map<String, String> initialData) {
        ProcessDefinition definition = definitionRepository.findByKey(definitionKey)
            .orElseThrow(() -> new IllegalArgumentException(
                "Process definition not found: " + definitionKey
            ));
        Map<String, String> processData = new LinkedHashMap<>(initialData);
        processData.putIfAbsent("processName", definition.name());

        ProcessInstance instance = instanceRepository.create(
            definition.key(),
            definition.name(),
            processData
        );
        for (ProcessStartHook startHook : startHooks) {
            startHook.afterProcessCreated(definition, instance, processData);
        }
        workflowEngine.start(
            definition.key(),
            Long.toString(instance.id()),
            toEngineVariables(processData)
        );
        postProcessEvent(instance.id(), ProcessEventEnum.PROCESS_START);
        return instance.id();
    }

    public void complete(long processInstanceId) {
        boolean updated = instanceRepository.compareAndSetStatus(
            processInstanceId,
            ProcStatusEnum.INIT,
            ProcStatusEnum.NORMAL_END
        );
        if (!updated) {
            ProcessInstance instance = get(processInstanceId);
            if (instance.status() != ProcStatusEnum.NORMAL_END) {
                throw new IllegalStateException(
                    "Cannot complete process in status " + instance.status()
                );
            }
        }
        postProcessEvent(processInstanceId, ProcessEventEnum.PROCESS_COMPLETE);
    }

    public void suspend(long processInstanceId) {
        boolean updated = instanceRepository.compareAndSetStatus(
            processInstanceId,
            ProcStatusEnum.INIT,
            ProcStatusEnum.SUSPEND
        );
        if (!updated) {
            throw new IllegalStateException(
                "Cannot suspend process in status " + get(processInstanceId).status()
            );
        }
    }

    public void processContinue(long processInstanceId) {
        boolean updated = instanceRepository.compareAndSetStatus(
            processInstanceId,
            ProcStatusEnum.SUSPEND,
            ProcStatusEnum.INIT
        );
        if (!updated) {
            throw new IllegalStateException(
                "Cannot continue process in status " + get(processInstanceId).status()
            );
        }
        postProcessEvent(processInstanceId, ProcessEventEnum.PROCESS_CONTINUE);
    }

    public void cancel(long processInstanceId) {
        boolean updated = instanceRepository.compareAndSetStatus(
            processInstanceId,
            ProcStatusEnum.INIT,
            ProcStatusEnum.CANCEL
        );
        if (!updated) {
            throw new IllegalStateException(
                "Cannot cancel process in status " + get(processInstanceId).status()
            );
        }
        postProcessEvent(processInstanceId, ProcessEventEnum.PROCESS_CANCEL);
    }

    public ProcessInstance get(long processInstanceId) {
        return instanceRepository.findById(processInstanceId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Process instance not found: " + processInstanceId
            ));
    }

    private static Map<String, Object> toEngineVariables(Map<String, String> processData) {
        return new LinkedHashMap<>(processData);
    }

    private void postProcessEvent(long processInstanceId, ProcessEventEnum eventEnum) {
        if (eventPublisher == null) {
            return;
        }
        ProcessInstance process = get(processInstanceId);
        ProcessEvent event = new ProcessEvent();
        event.setProcessInstId(processInstanceId);
        event.setProcName(process.name());
        event.setMain(process.relateProcessInstanceId() == 0);
        event.setProcEventCode(eventEnum.getCode());
        event.setRelateProcInstId(process.relateProcessInstanceId());
        event.setRelateTaskInstId(process.relateTaskInstanceId());
        event.getBasicInfo().setContextKeyType("PROCESS_INSTANCE_ID");
        event.getBasicInfo().setContextKey(String.valueOf(processInstanceId));
        eventPublisher.publish(new EventDraft<>(
            event,
            "process",
            String.valueOf(processInstanceId),
            String.valueOf(processInstanceId),
            String.valueOf(processInstanceId),
            null
        ));
    }
}
