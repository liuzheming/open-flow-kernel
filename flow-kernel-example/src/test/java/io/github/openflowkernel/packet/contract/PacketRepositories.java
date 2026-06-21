package io.github.openflowkernel.packet.contract;

import io.github.openflowkernel.packet.repo.PacketDataRepo;
import io.github.openflowkernel.packet.repo.PacketRepo;

public record PacketRepositories(
    PacketRepo packetRepo,
    PacketDataRepo packetDataRepo
) {
}
