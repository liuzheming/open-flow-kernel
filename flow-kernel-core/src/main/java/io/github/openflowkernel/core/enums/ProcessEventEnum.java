package io.github.openflowkernel.core.enums;

public enum ProcessEventEnum {
    PROCESS_START("PROCESS_START", "流程开始"),
    PROCESS_COMPLETE("PROCESS_COMPLETE", "流程完成"),
    PROCESS_CANCEL("PROCESS_CANCEL", "流程取消"),
    PROCESS_CONTINUE("PROCESS_CONTINUE", "流程继续");

    private final String code;
    private final String name;

    ProcessEventEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static ProcessEventEnum codeOf(String code) {
        for (ProcessEventEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("invalid code for ProcessEventEnum, code: " + code);
    }
}
