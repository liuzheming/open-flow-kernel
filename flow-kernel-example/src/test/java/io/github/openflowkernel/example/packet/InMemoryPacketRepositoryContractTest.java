package io.github.openflowkernel.example.packet;

import io.github.openflowkernel.packet.contract.PacketRepositories;
import io.github.openflowkernel.packet.contract.PacketRepositoryContract;

final class InMemoryPacketRepositoryContractTest implements PacketRepositoryContract {
    @Override
    public PacketRepositories repositories() {
        InMemoryPacketDataRepo packetDataRepo = new InMemoryPacketDataRepo();
        return new PacketRepositories(
            new InMemoryPacketRepo(packetDataRepo),
            packetDataRepo
        );
    }
}
