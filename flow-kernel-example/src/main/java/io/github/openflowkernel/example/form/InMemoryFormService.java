package io.github.openflowkernel.example.form;

import io.github.openflowkernel.form.FormInstance;
import io.github.openflowkernel.form.FormService;
import io.github.openflowkernel.form.FormStatus;
import io.github.openflowkernel.form.FormSubmissionListener;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryFormService implements FormService {
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, FormInstance> forms = new ConcurrentHashMap<>();
    private FormSubmissionListener listener;

    @Override
    public void setSubmissionListener(FormSubmissionListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    @Override
    public long create(String definitionKey, Map<String, String> initialData) {
        long id = ids.incrementAndGet();
        forms.put(id, new FormInstance(
            id,
            definitionKey,
            FormStatus.ACTIVE,
            initialData,
            Map.of()
        ));
        return id;
    }

    @Override
    public FormInstance get(long formInstanceId) {
        FormInstance form = forms.get(formInstanceId);
        if (form == null) {
            throw new IllegalArgumentException("Form instance not found: " + formInstanceId);
        }
        return form;
    }

    @Override
    public synchronized void submit(
        long formInstanceId,
        Map<String, String> submittedData
    ) {
        FormInstance current = get(formInstanceId);
        if (current.status() == FormStatus.SUBMITTED) {
            return;
        }
        if (current.status() != FormStatus.ACTIVE) {
            throw new IllegalStateException(
                "Cannot submit form in status " + current.status()
            );
        }
        forms.put(formInstanceId, new FormInstance(
            current.id(),
            current.definitionKey(),
            FormStatus.SUBMITTED,
            current.initialData(),
            submittedData
        ));
        requiredListener().onSubmitted(formInstanceId);
    }

    @Override
    public synchronized void cancel(long formInstanceId) {
        FormInstance current = get(formInstanceId);
        if (current.status() == FormStatus.CANCELLED) {
            return;
        }
        forms.put(formInstanceId, new FormInstance(
            current.id(),
            current.definitionKey(),
            FormStatus.CANCELLED,
            current.initialData(),
            current.submittedData()
        ));
    }

    private FormSubmissionListener requiredListener() {
        if (listener == null) {
            throw new IllegalStateException("Form submission listener is not configured");
        }
        return listener;
    }
}
