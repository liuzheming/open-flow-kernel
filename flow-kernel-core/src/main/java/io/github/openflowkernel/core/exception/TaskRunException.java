package io.github.openflowkernel.core.exception;

public class TaskRunException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String stage;
    private Long processInstId;
    private Long taskInstId;

    public TaskRunException(String stage) {
        this.stage = stage;
    }

    public TaskRunException(String stage, Throwable throwable) {
        super(throwable);
        this.stage = stage;
    }

    public TaskRunException(String stage, String message, Throwable throwable) {
        super(message, throwable);
        this.stage = stage;
    }

    public TaskRunException(
        String stage,
        Long processInstId,
        Long taskInstId,
        Throwable throwable
    ) {
        super(throwable);
        this.stage = stage;
        this.processInstId = processInstId;
        this.taskInstId = taskInstId;
    }

    public String getStage() {
        return stage;
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

    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (processInstId != null || taskInstId != null) {
            return String.format(
                "[stage=%s, processInstId=%s, taskInstId=%s] %s",
                stage,
                processInstId,
                taskInstId,
                baseMessage != null ? baseMessage : ""
            );
        }
        return baseMessage;
    }
}
