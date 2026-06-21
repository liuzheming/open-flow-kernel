package io.github.openflowkernel.packet;

public enum PacketStatusEnum {
    IN_PROGRESS(0, "进行中"),
    COMPLETED(1, "已完成"),
    CANCELED(-2, "已取消");

    private final int code;
    private final String desc;

    PacketStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static PacketStatusEnum valueByCode(int code) {
        for (PacketStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("invalid code for PacketStatusEnum, code: " + code);
    }
}
