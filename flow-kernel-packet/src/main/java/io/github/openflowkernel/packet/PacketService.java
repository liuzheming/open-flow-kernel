package io.github.openflowkernel.packet;

import io.github.openflowkernel.packet.repo.PacketDataRepo;
import io.github.openflowkernel.packet.repo.PacketRepo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class PacketService {
    private static final String EMPTY_OBJECT = "{}";

    private final PacketRepo packetRepo;
    private final PacketDataRepo packetDataRepo;
    private final PacketFactory packetFactory;

    public PacketService(PacketRepo packetRepo, PacketDataRepo packetDataRepo) {
        this(packetRepo, packetDataRepo, null);
    }

    public PacketService(
        PacketRepo packetRepo,
        PacketDataRepo packetDataRepo,
        PacketFactory packetFactory
    ) {
        this.packetRepo = Objects.requireNonNull(packetRepo);
        this.packetDataRepo = Objects.requireNonNull(packetDataRepo);
        this.packetFactory = packetFactory;
    }

    public Optional<PacketValueRecord> getCurrentPacket(
        long processInstanceId,
        long processTaskInstanceId
    ) {
        return packetDataRepo.getPacketValue(processInstanceId, processTaskInstanceId);
    }

    public Optional<PacketValueRecord> getLatest(long processInstanceId) {
        return packetRepo.getPacket(processInstanceId)
            .flatMap(packet -> packetDataRepo.getPacketValue(packet.dataPacketValueId()));
    }

    public <P extends Packet<?>> Optional<P> getLatest(
        long processInstanceId,
        String packetResolverClass
    ) {
        return getLatest(processInstanceId)
            .map(value -> packetFactory().instantiate(packetResolverClass, value));
    }

    public List<PacketValueRecord> getAll(long processInstanceId) {
        return packetDataRepo.getPacketValueByProcInstId(processInstanceId);
    }

    public void initPacketDataRecord(long processInstanceId) {
        initPacketDataRecord(processInstanceId, EMPTY_OBJECT, EMPTY_OBJECT);
    }

    public void initPacketDataRecord(
        long processInstanceId,
        String value,
        String source
    ) {
        packetRepo.insert(processInstanceId);
        packetRepo.commit(
            processInstanceId,
            0,
            "",
            value == null ? EMPTY_OBJECT : value,
            source == null ? EMPTY_OBJECT : source,
            0
        );
    }

    public void initPacketDataRecord(long processInstanceId, Packet<?> packet) {
        if (packet == null) {
            initPacketDataRecord(processInstanceId);
            return;
        }
        packet.setProcInstId(processInstanceId);
        packet.setProcTaskInstId(0L);
        packet.setTaskCode("");
        initPacketDataRecord(
            processInstanceId,
            packet.getValueStr(),
            packet.getSourceStr()
        );
    }

    public PacketValueRecord commit(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode,
        UnaryOperator<PacketValueRecord> packetFunction
    ) {
        PacketRecord packet = packetRepo.getPacket(processInstanceId)
            .orElseThrow(() -> new IllegalStateException(
                "Packet not initialized, processInstanceId=" + processInstanceId
            ));
        PacketValueRecord current = packetDataRepo.getLatestUnexpiredValue(processInstanceId)
            .orElseGet(() -> emptyPacketValue(
                processInstanceId,
                processTaskInstanceId,
                taskCode
            ));
        PacketValueRecord result = packetFunction.apply(current);
        if (result == null) {
            return null;
        }
        long newValueId = packetRepo.commit(
            processInstanceId,
            processTaskInstanceId,
            taskCode,
            result.value(),
            result.source(),
            packet.dataPacketValueId()
        );
        return packetDataRepo.getPacketValue(newValueId).orElseThrow();
    }

    public <P extends Packet<?>> P commit(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode,
        String packetResolverClass,
        UnaryOperator<P> packetFunction
    ) {
        PacketValueRecord committed = commit(
            processInstanceId,
            processTaskInstanceId,
            taskCode,
            packet -> {
                P typedPacket = packetFactory().instantiate(packetResolverClass, packet);
                typedPacket.setProcInstId(processInstanceId);
                typedPacket.setProcTaskInstId(processTaskInstanceId);
                typedPacket.setTaskCode(taskCode);
                P result = packetFunction.apply(typedPacket);
                if (result == null) {
                    return null;
                }
                return new PacketValueRecord(
                    packet.id(),
                    processInstanceId,
                    processTaskInstanceId,
                    taskCode,
                    packet.initValue(),
                    result.getValueStr(),
                    packet.initSource(),
                    result.getSourceStr(),
                    packet.status(),
                    packet.createdAt(),
                    packet.updatedAt()
                );
            }
        );
        return packetFactory().instantiate(packetResolverClass, committed);
    }

    public Optional<PacketValueRecord> defaultCommit(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode
    ) {
        Optional<PacketValueRecord> current = getCurrentPacket(
            processInstanceId,
            processTaskInstanceId
        );
        if (current.isPresent()) {
            return current;
        }
        return Optional.ofNullable(commit(
            processInstanceId,
            processTaskInstanceId,
            taskCode,
            packet -> packet
        ));
    }

    public void expire(long processInstanceId, String taskCode) {
        long oldValueId = packetRepo.getPacket(processInstanceId)
            .map(PacketRecord::dataPacketValueId)
            .orElse(0L);
        packetRepo.expire(processInstanceId, taskCode, oldValueId);
    }

    public boolean completePacket(long processInstanceId) {
        return packetRepo.updatePacketStatus(
            processInstanceId,
            PacketStatusEnum.IN_PROGRESS,
            PacketStatusEnum.COMPLETED
        );
    }

    public boolean cancelPacket(long processInstanceId) {
        return packetRepo.updatePacketStatus(
            processInstanceId,
            PacketStatusEnum.IN_PROGRESS,
            PacketStatusEnum.CANCELED
        );
    }

    private static PacketValueRecord emptyPacketValue(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode
    ) {
        return new PacketValueRecord(
            0,
            processInstanceId,
            processTaskInstanceId,
            taskCode,
            "",
            EMPTY_OBJECT,
            "",
            EMPTY_OBJECT,
            PacketValueStatusEnum.COMPLETED,
            java.time.Instant.EPOCH,
            java.time.Instant.EPOCH
        );
    }

    private PacketFactory packetFactory() {
        if (packetFactory == null) {
            throw new IllegalStateException("PacketFactory is required for typed packet API");
        }
        return packetFactory;
    }
}
