package io.github.openflowkernel.form;

import io.github.openflowkernel.form.event.FormSubmitted;
import io.github.openflowkernel.event.EventDraft;
import io.github.openflowkernel.event.EventPublisher;

import java.util.Objects;

public final class FormCompleteListener implements FormSubmissionListener {
    private final EventPublisher eventPublisher;

    public FormCompleteListener(EventPublisher eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public void onSubmitted(long formInstanceId) {
        eventPublisher.publish(new EventDraft<>(
            new FormSubmitted(formInstanceId),
            "form",
            Long.toString(formInstanceId),
            Long.toString(formInstanceId),
            "form-" + formInstanceId,
            null
        ));
    }
}
