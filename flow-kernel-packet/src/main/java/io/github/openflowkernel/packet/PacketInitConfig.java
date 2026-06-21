package io.github.openflowkernel.packet;

public final class PacketInitConfig {
    private String name;

    public PacketInitConfig() {
    }

    public PacketInitConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
