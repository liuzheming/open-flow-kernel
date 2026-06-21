package io.github.openflowkernel.packet;

public record PacketSource(
    Long procInstId,
    Long procTaskInstId,
    Long formInstId
) {
}
