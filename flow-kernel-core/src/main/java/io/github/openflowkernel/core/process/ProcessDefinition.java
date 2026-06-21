package io.github.openflowkernel.core.process;

import io.github.openflowkernel.core.task.TaskDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProcessDefinition {
    private final String key;
    private final String name;
    private final List<TaskDefinition> tasks;
    private final Map<String, TaskDefinition> taskByCode;
    private final String dataPacketResolverClass;
    private final String packetInitConfig;

    public ProcessDefinition(String key, String name, List<TaskDefinition> tasks) {
        this(key, name, tasks, "", "");
    }

    public ProcessDefinition(
        String key,
        String name,
        List<TaskDefinition> tasks,
        String dataPacketResolverClass,
        String packetInitConfig
    ) {
        this.key = requireText(key, "key");
        this.name = requireText(name, "name");
        this.tasks = List.copyOf(tasks);
        this.taskByCode = indexTasks(this.tasks);
        this.dataPacketResolverClass = dataPacketResolverClass == null
            ? ""
            : dataPacketResolverClass;
        this.packetInitConfig = packetInitConfig == null ? "" : packetInitConfig;
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public List<TaskDefinition> tasks() {
        return tasks;
    }

    public String dataPacketResolverClass() {
        return dataPacketResolverClass;
    }

    public String packetInitConfig() {
        return packetInitConfig;
    }

    public TaskDefinition task(String taskCode) {
        TaskDefinition definition = taskByCode.get(taskCode);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown task code: " + taskCode);
        }
        return definition;
    }

    private static Map<String, TaskDefinition> indexTasks(List<TaskDefinition> tasks) {
        Map<String, TaskDefinition> result = new LinkedHashMap<>();
        for (TaskDefinition task : tasks) {
            if (result.put(task.code(), task) != null) {
                throw new IllegalArgumentException("Duplicate task code: " + task.code());
            }
        }
        return Map.copyOf(result);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
