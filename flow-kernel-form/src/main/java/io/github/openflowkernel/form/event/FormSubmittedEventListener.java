package io.github.openflowkernel.form.event;

import io.github.openflowkernel.form.FormTask;
import io.github.openflowkernel.core.relation.TaskRelationService;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.event.EventListener;

import java.util.Objects;

public final class FormSubmittedEventListener implements EventListener<FormSubmitted> {
    private final TaskRelationService relationService;

    public FormSubmittedEventListener(TaskRelationService relationService) {
        this.relationService = Objects.requireNonNull(relationService);
    }

    @Override
    public void listen(EventEnvelope<FormSubmitted> envelope) {
        relationService.complete(
            FormTask.RELATION_TYPE,
            Long.toString(envelope.payload().formInstanceId())
        );
    }
}
