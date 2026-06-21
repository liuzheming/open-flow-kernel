package io.github.openflowkernel.packet;

public enum PacketValueStatusEnum {
    UNCOMPLETED(0, "未完成"),
    COMPLETED(1, "已完成"),
    EXPIRED(2, "已过期");

    private final int code;
    private final String desc;

    PacketValueStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static PacketValueStatusEnum valueByCode(int code) {
        for (PacketValueStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException(
            "invalid code for PacketValueStatusEnum, code: " + code
        );
    }
}
