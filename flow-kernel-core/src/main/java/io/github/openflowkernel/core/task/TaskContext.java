package io.github.openflowkernel.core.task;

import java.util.Map;

public class TaskContext {
    private Long processInstId;
    private Long taskInstId;
    private Map<String, String> processInstData;
    private Map<String, String> currentTaskInstData;
    private Map<String, String> taskConfig;
    private String taskCode;

    public TaskContext() {
    }

    public TaskContext(
        Long processInstId,
        Long taskInstId,
        Map<String, String> processInstData,
        Map<String, String> currentTaskInstData,
        Map<String, String> taskConfig,
        String taskCode
    ) {
        this.processInstId = processInstId;
        this.taskInstId = taskInstId;
        this.processInstData = processInstData;
        this.currentTaskInstData = currentTaskInstData;
        this.taskConfig = taskConfig;
        this.taskCode = taskCode;
    }

    public TaskContext(
        long processInstId,
        long taskInstId,
        String taskCode,
        Map<String, String> processInstData,
        Map<String, String> currentTaskInstData
    ) {
        this(
            processInstId,
            taskInstId,
            processInstData,
            currentTaskInstData,
            Map.of(),
            taskCode
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getProcessInstId() {
        return processInstId;
    }

    public void setProcessInstId(Long processInstId) {
        this.processInstId = processInstId;
    }

    public Long getTaskInstId() {
        return taskInstId;
    }

    public void setTaskInstId(Long taskInstId) {
        this.taskInstId = taskInstId;
    }

    public Map<String, String> getProcessInstData() {
        return processInstData;
    }

    public void setProcessInstData(Map<String, String> processInstData) {
        this.processInstData = processInstData;
    }

    public Map<String, String> getCurrentTaskInstData() {
        return currentTaskInstData;
    }

    public void setCurrentTaskInstData(Map<String, String> currentTaskInstData) {
        this.currentTaskInstData = currentTaskInstData;
    }

    public Map<String, String> getTaskConfig() {
        return taskConfig;
    }

    public void setTaskConfig(Map<String, String> taskConfig) {
        this.taskConfig = taskConfig;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public long processInstanceId() {
        return processInstId;
    }

    public long taskInstanceId() {
        return taskInstId;
    }

    public Map<String, String> processData() {
        return processInstData;
    }

    public Map<String, String> taskData() {
        return currentTaskInstData;
    }

    public String taskCode() {
        return taskCode;
    }

    public static final class Builder {
        private final TaskContext value = new TaskContext();

        public Builder processInstId(Long processInstId) {
            value.setProcessInstId(processInstId);
            return this;
        }

        public Builder taskInstId(Long taskInstId) {
            value.setTaskInstId(taskInstId);
            return this;
        }

        public Builder processInstData(Map<String, String> processInstData) {
            value.setProcessInstData(processInstData);
            return this;
        }

        public Builder currentTaskInstData(Map<String, String> currentTaskInstData) {
            value.setCurrentTaskInstData(currentTaskInstData);
            return this;
        }

        public Builder taskConfig(Map<String, String> taskConfig) {
            value.setTaskConfig(taskConfig);
            return this;
        }

        public Builder taskCode(String taskCode) {
            value.setTaskCode(taskCode);
            return this;
        }

        public TaskContext build() {
            return value;
        }
    }
}
