package io.github.openflowkernel.core.enums;

public enum ProcTaskTypeEnum {
    DEFAULT("默认"),
    FORM_TASK("表单任务"),
    HIDDEN_FORM_TASK("隐藏表单任务"),
    SUB_PROCESS_TASK("子流程任务"),
    ACTION_TASK("ACTION 任务"),
    ALONE_TASK("独立任务");

    private final String code;
    private final String desc;

    ProcTaskTypeEnum(String desc) {
        this.code = name();
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
