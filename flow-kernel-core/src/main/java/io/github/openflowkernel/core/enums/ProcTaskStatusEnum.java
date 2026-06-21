package io.github.openflowkernel.core.enums;

public enum ProcTaskStatusEnum {
    UNINIT(-4, "未开启"),
    NOTSTART(-3, "待开始"),
    CREATE(-2, "等待初始化"),
    CANCEL(-1, "已取消"),
    INIT(0, "待完成"),
    COMPLETE(1, "已完成"),
    FAILED(2, "生成失败"),
    PENDING(3, "暂停"),
    IN_DISPOSING(10, "处置中"),
    DISPOSED(11, "处置结束");

    private final Integer status;
    private final String desc;

    ProcTaskStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static ProcTaskStatusEnum valueByStatus(Integer status) {
        for (ProcTaskStatusEnum value : values()) {
            if (value.status.equals(status)) {
                return value;
            }
        }
        throw new IllegalArgumentException("invalid status for ProcTaskStatusEnum");
    }

    public Integer getStatus() {
        return status;
    }

    public String getDesc() {
        return desc;
    }
}
