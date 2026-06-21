package io.github.openflowkernel.form.event;

import io.github.openflowkernel.event.DomainEvent;

public record FormSubmitted(long formInstanceId) implements DomainEvent {
    @Override
    public String eventType() {
        return "form-submitted";
    }
}
