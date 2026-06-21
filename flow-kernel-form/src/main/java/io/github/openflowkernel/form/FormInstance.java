package io.github.openflowkernel.form;

import java.util.Map;
import java.util.Objects;

public record FormInstance(
    long id,
    String definitionKey,
    FormStatus status,
    Map<String, String> initialData,
    Map<String, String> submittedData
) {
    public FormInstance {
        Objects.requireNonNull(definitionKey, "definitionKey");
        Objects.requireNonNull(status, "status");
        initialData = Map.copyOf(initialData);
        submittedData = Map.copyOf(submittedData);
    }
}
