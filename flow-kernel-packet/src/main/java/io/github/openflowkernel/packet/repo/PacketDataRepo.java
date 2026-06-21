package io.github.openflowkernel.packet.repo;

import io.github.openflowkernel.packet.PacketValueRecord;

import java.util.List;
import java.util.Optional;

public interface PacketDataRepo {
    Optional<PacketValueRecord> getLatestExpired(long processInstanceId, String taskCode);

    Optional<PacketValueRecord> getLatestFirstUnexpiredCommit(
        long processInstanceId,
        String taskCode
    );

    void expireFromValueId(long processInstanceId, long valueId);

    Optional<PacketValueRecord> getLatestUnexpiredValue(long processInstanceId);

    Optional<PacketValueRecord> getPacketValue(long id);

    List<PacketValueRecord> getPacketValueByProcInstId(long processInstanceId);

    Optional<PacketValueRecord> getPacketValue(
        long processInstanceId,
        long processTaskInstanceId
    );

    long insertPacketValue(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode,
        String value,
        String source
    );

    void updatePacketValue(long id, String value);

    void updatePacketValue(long processInstanceId, long processTaskInstanceId, String value);
}
