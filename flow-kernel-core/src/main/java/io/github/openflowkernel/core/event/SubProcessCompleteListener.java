package io.github.openflowkernel.core.event;

import io.github.openflowkernel.core.enums.ProcessEventEnum;
import io.github.openflowkernel.core.relation.TaskRelationCompleteResult;
import io.github.openflowkernel.core.relation.TaskRelationService;
import io.github.openflowkernel.core.task.SubProcessTask;
import io.github.openflowkernel.core.task.TaskResult;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.event.EventListener;

import java.util.Objects;

public final class SubProcessCompleteListener implements EventListener<ProcessEvent> {
    private final TaskRelationService relationService;

    public SubProcessCompleteListener(TaskRelationService relationService) {
        this.relationService = Objects.requireNonNull(relationService);
    }

    @Override
    public void listen(EventEnvelope<ProcessEvent> envelope) {
        ProcessEvent event = envelope.payload();
        if (event.isMain()
            || !ProcessEventEnum.PROCESS_COMPLETE.getCode().equals(event.getProcEventCode())) {
            return;
        }
        TaskRelationCompleteResult result = relationService.relationComplete(
            SubProcessTask.relationType(),
            Long.toString(event.getProcessInstId())
        );
        if (result == null || !Boolean.TRUE.equals(result.getAllCompleted())) {
            return;
        }
        if (result.getTask() instanceof SubProcessTask subProcessTask) {
            TaskResult output = subProcessTask.outputData();
            result.getTask().complete(output);
            return;
        }
        result.getTask().complete(null);
    }
}
