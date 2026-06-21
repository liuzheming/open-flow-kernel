package io.github.openflowkernel.example.packet;

import io.github.openflowkernel.packet.PacketRecord;
import io.github.openflowkernel.packet.PacketStatusEnum;
import io.github.openflowkernel.packet.PacketValueRecord;
import io.github.openflowkernel.packet.repo.PacketDataRepo;
import io.github.openflowkernel.packet.repo.PacketRepo;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryPacketRepo implements PacketRepo {
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, PacketRecord> packetsByProcessId = new ConcurrentHashMap<>();
    private final PacketDataRepo packetDataRepo;
    private final Clock clock;

    public InMemoryPacketRepo(PacketDataRepo packetDataRepo) {
        this(packetDataRepo, Clock.systemUTC());
    }

    public InMemoryPacketRepo(PacketDataRepo packetDataRepo, Clock clock) {
        this.packetDataRepo = packetDataRepo;
        this.clock = clock;
    }

    @Override
    public Optional<PacketRecord> getPacket(long processInstanceId) {
        return Optional.ofNullable(packetsByProcessId.get(processInstanceId));
    }

    @Override
    public PacketRecord insert(long processInstanceId) {
        Instant now = clock.instant();
        PacketRecord record = new PacketRecord(
            ids.incrementAndGet(),
            processInstanceId,
            0,
            PacketStatusEnum.IN_PROGRESS,
            now,
            now
        );
        PacketRecord previous = packetsByProcessId.putIfAbsent(processInstanceId, record);
        if (previous != null) {
            throw new IllegalStateException("Packet already exists: " + processInstanceId);
        }
        return record;
    }

    @Override
    public synchronized void expire(
        long processInstanceId,
        String taskCode,
        long oldPacketValueId
    ) {
        Optional<PacketValueRecord> latest =
            packetDataRepo.getLatestFirstUnexpiredCommit(processInstanceId, taskCode);
        if (latest.isEmpty()) {
            return;
        }
        packetDataRepo.expireFromValueId(processInstanceId, latest.get().id());
        long targetValueId = packetDataRepo.getLatestUnexpiredValue(processInstanceId)
            .map(PacketValueRecord::id)
            .orElse(0L);
        updatePointer(processInstanceId, oldPacketValueId, targetValueId, "任务并行变更");
    }

    @Override
    public synchronized long commit(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode,
        String value,
        String source,
        long oldPacketValueId
    ) {
        long newValueId = packetDataRepo.insertPacketValue(
            processInstanceId,
            processTaskInstanceId,
            taskCode,
            value,
            source
        );
        updatePointer(processInstanceId, oldPacketValueId, newValueId, "任务并行提交");
        return newValueId;
    }

    @Override
    public synchronized boolean updatePacketStatus(
        long processInstanceId,
        PacketStatusEnum beforeStatus,
        PacketStatusEnum afterStatus
    ) {
        PacketRecord current = packetsByProcessId.get(processInstanceId);
        if (current == null || current.status() != beforeStatus) {
            return false;
        }
        packetsByProcessId.put(
            processInstanceId,
            withStatus(current, afterStatus, clock.instant())
        );
        return true;
    }

    private void updatePointer(
        long processInstanceId,
        long oldPacketValueId,
        long targetValueId,
        String message
    ) {
        PacketRecord current = packetsByProcessId.get(processInstanceId);
        if (current == null || current.dataPacketValueId() != oldPacketValueId) {
            throw new IllegalStateException(
                message + ", procInstId=" + processInstanceId
                    + ", oldValueId=" + oldPacketValueId
            );
        }
        packetsByProcessId.put(
            processInstanceId,
            new PacketRecord(
                current.id(),
                current.processInstanceId(),
                targetValueId,
                current.status(),
                current.createdAt(),
                clock.instant()
            )
        );
    }

    private static PacketRecord withStatus(
        PacketRecord current,
        PacketStatusEnum status,
        Instant now
    ) {
        return new PacketRecord(
            current.id(),
            current.processInstanceId(),
            current.dataPacketValueId(),
            status,
            current.createdAt(),
            now
        );
    }
}
