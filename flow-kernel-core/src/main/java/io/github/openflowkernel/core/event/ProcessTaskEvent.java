package io.github.openflowkernel.core.event;

import io.github.openflowkernel.core.engine.WorkflowEventListener;
import io.github.openflowkernel.event.EventV2;

public class ProcessTaskEvent extends EventV2 {
    private String taskEventName;
    private Long procInstId;
    private String procCode;
    private String taskCode;
    private Long operatorUcId;
    private String operatorName;
    private Long taskInstId;

    static {
        EventV2.registerName(ProcessTaskEvent.class, "ProcessTaskEvent");
    }

    public ProcessTaskEvent() {
    }

    public ProcessTaskEvent(
        String taskEventName,
        Long procInstId,
        String procCode,
        String taskCode,
        Long taskInstId
    ) {
        this.taskEventName = taskEventName;
        this.procInstId = procInstId;
        this.procCode = procCode;
        this.taskCode = taskCode;
        this.taskInstId = taskInstId;
    }

    public ProcessTaskEvent(
        String taskEventName,
        Long procInstId,
        String procCode,
        String taskCode
    ) {
        this.taskEventName = taskEventName;
        this.procInstId = procInstId;
        this.procCode = procCode;
        this.taskCode = taskCode;
    }

    public ProcessTaskEvent(
        String taskEventName,
        Long procInstId,
        Long taskInstId,
        Long operatorUcId,
        String operatorName
    ) {
        this.taskEventName = taskEventName;
        this.procInstId = procInstId;
        this.taskInstId = taskInstId;
        this.operatorUcId = operatorUcId;
        this.operatorName = operatorName;
    }

    public boolean isCreate() {
        return WorkflowEventListener.CREATE.equals(taskEventName);
    }

    public boolean isComplete() {
        return WorkflowEventListener.COMPLETE.equals(taskEventName);
    }

    public String getTaskEventName() {
        return taskEventName;
    }

    public void setTaskEventName(String taskEventName) {
        this.taskEventName = taskEventName;
    }

    public Long getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(Long procInstId) {
        this.procInstId = procInstId;
    }

    public String getProcCode() {
        return procCode;
    }

    public void setProcCode(String procCode) {
        this.procCode = procCode;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public Long getOperatorUcId() {
        return operatorUcId;
    }

    public void setOperatorUcId(Long operatorUcId) {
        this.operatorUcId = operatorUcId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public Long getTaskInstId() {
        return taskInstId;
    }

    public void setTaskInstId(Long taskInstId) {
        this.taskInstId = taskInstId;
    }
}
