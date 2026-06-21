package io.github.openflowkernel.core.enums;

public enum ProcStatusEnum {
    INIT(0, "流程进行中", "进行中"),
    NORMAL_END(1, "流程正常结束", "已完成"),
    SUSPEND(2, "流程挂起", "暂停"),
    CANCEL(-1, "流程取消", "已取消");

    private final Integer status;
    private final String desc;
    private final String label;

    ProcStatusEnum(Integer status, String desc, String label) {
        this.status = status;
        this.desc = desc;
        this.label = label;
    }

    public static ProcStatusEnum valueByStatus(Integer status) {
        for (ProcStatusEnum value : values()) {
            if (value.status.equals(status)) {
                return value;
            }
        }
        return INIT;
    }

    public Integer getStatus() {
        return status;
    }

    public String getDesc() {
        return desc;
    }

    public String getLabel() {
        return label;
    }
}
