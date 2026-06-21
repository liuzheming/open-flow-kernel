package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;

import java.util.LinkedHashMap;
import java.util.Map;

public record TaskDefinition(
    String code,
    String name,
    String handlerName,
    Map<String, String> config
) {
    public TaskDefinition {
        requireText(code, "code");
        requireText(name, "name");
        requireText(handlerName, "handlerName");
        Map<String, String> mergedConfig = new LinkedHashMap<>();
        if (config != null) {
            mergedConfig.putAll(config);
        }
        mergedConfig.putIfAbsent(TaskConfigKeyConstant.HANDLER_NAME, handlerName);
        mergedConfig.putIfAbsent(TaskConfigKeyConstant.TASK_NAME, name);
        config = Map.copyOf(mergedConfig);
    }

    public TaskDefinition(String code, String name, String handlerName) {
        this(code, name, handlerName, Map.of());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
