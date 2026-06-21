package io.github.openflowkernel.core.task;

import java.util.HashMap;
import java.util.Map;

public class TaskResult {
    private Map<String, String> processInstData;
    private Map<String, String> taskInstData;

    public TaskResult() {
    }

    public TaskResult(
        Map<String, String> processInstData,
        Map<String, String> taskInstData
    ) {
        this.processInstData = processInstData;
        this.taskInstData = taskInstData;
    }

    public static TaskResult empty() {
        return new TaskResult();
    }

    public static TaskResultBuilder builder() {
        return new TaskResultBuilder();
    }

    public Map<String, String> getProcessInstData() {
        return processInstData;
    }

    public void setProcessInstData(Map<String, String> processInstData) {
        this.processInstData = processInstData;
    }

    public Map<String, String> getTaskInstData() {
        return taskInstData;
    }

    public void setTaskInstData(Map<String, String> taskInstData) {
        this.taskInstData = taskInstData;
    }

    public void putTaskData(String key, String val) {
        if (taskInstData == null) {
            taskInstData = new HashMap<>();
        }
        taskInstData.put(key, val);
    }

    public void putProcInstData(String key, String val) {
        if (processInstData == null) {
            processInstData = new HashMap<>();
        }
        processInstData.put(key, val);
    }

    public Map<String, String> processData() {
        return processInstData == null ? Map.of() : processInstData;
    }

    public Map<String, String> taskData() {
        return taskInstData == null ? Map.of() : taskInstData;
    }

    public static final class TaskResultBuilder {
        private final TaskResult taskResult;

        public TaskResultBuilder() {
            this(new TaskResult());
        }

        public TaskResultBuilder(TaskResult taskResult) {
            this.taskResult = taskResult;
        }

        public TaskResultBuilder putTaskInstData(String key, String value) {
            taskResult.putTaskData(key, value);
            return this;
        }

        public TaskResultBuilder putProcessInstData(String key, String value) {
            taskResult.putProcInstData(key, value);
            return this;
        }

        public TaskResultBuilder taskData(String key, String value) {
            return putTaskInstData(key, value);
        }

        public TaskResultBuilder processData(String key, String value) {
            return putProcessInstData(key, value);
        }

        public TaskResult build() {
            return taskResult;
        }
    }
}
