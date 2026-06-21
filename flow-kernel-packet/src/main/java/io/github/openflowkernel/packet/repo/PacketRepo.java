package io.github.openflowkernel.packet.repo;

import io.github.openflowkernel.packet.PacketRecord;
import io.github.openflowkernel.packet.PacketStatusEnum;

import java.util.Optional;

public interface PacketRepo {
    Optional<PacketRecord> getPacket(long processInstanceId);

    PacketRecord insert(long processInstanceId);

    void expire(long processInstanceId, String taskCode, long oldPacketValueId);

    long commit(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode,
        String value,
        String source,
        long oldPacketValueId
    );

    boolean updatePacketStatus(
        long processInstanceId,
        PacketStatusEnum beforeStatus,
        PacketStatusEnum afterStatus
    );
}
