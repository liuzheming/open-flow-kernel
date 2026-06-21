package io.github.openflowkernel.example.packet;

import io.github.openflowkernel.packet.PacketValueRecord;
import io.github.openflowkernel.packet.PacketValueStatusEnum;
import io.github.openflowkernel.packet.repo.PacketDataRepo;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryPacketDataRepo implements PacketDataRepo {
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, PacketValueRecord> values = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryPacketDataRepo() {
        this(Clock.systemUTC());
    }

    public InMemoryPacketDataRepo(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<PacketValueRecord> getLatestExpired(
        long processInstanceId,
        String taskCode
    ) {
        return values.values().stream()
            .filter(value -> value.processInstanceId() == processInstanceId)
            .filter(value -> value.taskCode().equals(taskCode))
            .filter(value -> value.status() == PacketValueStatusEnum.EXPIRED)
            .max(Comparator.comparingLong(PacketValueRecord::id));
    }

    @Override
    public Optional<PacketValueRecord> getLatestFirstUnexpiredCommit(
        long processInstanceId,
        String taskCode
    ) {
        Optional<PacketValueRecord> latest = values.values().stream()
            .filter(value -> value.processInstanceId() == processInstanceId)
            .filter(value -> value.taskCode().equals(taskCode))
            .filter(value -> value.status() != PacketValueStatusEnum.EXPIRED)
            .max(Comparator.comparingLong(PacketValueRecord::id));
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        long latestTaskInstanceId = latest.get().processTaskInstanceId();
        return values.values().stream()
            .filter(value -> value.processInstanceId() == processInstanceId)
            .filter(value -> value.processTaskInstanceId() == latestTaskInstanceId)
            .min(Comparator.comparingLong(PacketValueRecord::id));
    }

    @Override
    public void expireFromValueId(long processInstanceId, long valueId) {
        Instant now = clock.instant();
        values.replaceAll((id, value) -> {
            if (value.processInstanceId() == processInstanceId && value.id() >= valueId) {
                return withStatus(value, PacketValueStatusEnum.EXPIRED, now);
            }
            return value;
        });
    }

    @Override
    public Optional<PacketValueRecord> getLatestUnexpiredValue(long processInstanceId) {
        return values.values().stream()
            .filter(value -> value.processInstanceId() == processInstanceId)
            .filter(value -> value.status() != PacketValueStatusEnum.EXPIRED)
            .max(Comparator.comparingLong(PacketValueRecord::id));
    }

    @Override
    public Optional<PacketValueRecord> getPacketValue(long id) {
        if (id == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(id));
    }

    @Override
    public List<PacketValueRecord> getPacketValueByProcInstId(long processInstanceId) {
        return values.values().stream()
            .filter(value -> value.processInstanceId() == processInstanceId)
            .sorted(Comparator.comparingLong(PacketValueRecord::id))
            .toList();
    }

    @Override
    public Optional<PacketValueRecord> getPacketValue(
        long processInstanceId,
        long processTaskInstanceId
    ) {
        return values.values().stream()
            .filter(value -> value.processInstanceId() == processInstanceId)
            .filter(value -> value.processTaskInstanceId() == processTaskInstanceId)
            .max(Comparator.comparingLong(PacketValueRecord::id));
    }

    @Override
    public long insertPacketValue(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode,
        String value,
        String source
    ) {
        long id = ids.incrementAndGet();
        Instant now = clock.instant();
        values.put(id, new PacketValueRecord(
            id,
            processInstanceId,
            processTaskInstanceId,
            taskCode,
            "",
            value,
            "",
            source,
            PacketValueStatusEnum.COMPLETED,
            now,
            now
        ));
        return id;
    }

    @Override
    public void updatePacketValue(long id, String value) {
        PacketValueRecord current = required(id);
        values.put(id, withValue(current, value, clock.instant()));
    }

    @Override
    public void updatePacketValue(
        long processInstanceId,
        long processTaskInstanceId,
        String value
    ) {
        getPacketValue(processInstanceId, processTaskInstanceId)
            .ifPresent(record -> updatePacketValue(record.id(), value));
    }

    private PacketValueRecord required(long id) {
        PacketValueRecord value = values.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Packet value not found: " + id);
        }
        return value;
    }

    private static PacketValueRecord withStatus(
        PacketValueRecord value,
        PacketValueStatusEnum status,
        Instant now
    ) {
        return new PacketValueRecord(
            value.id(),
            value.processInstanceId(),
            value.processTaskInstanceId(),
            value.taskCode(),
            value.initValue(),
            value.value(),
            value.initSource(),
            value.source(),
            status,
            value.createdAt(),
            now
        );
    }

    private static PacketValueRecord withValue(
        PacketValueRecord value,
        String newValue,
        Instant now
    ) {
        return new PacketValueRecord(
            value.id(),
            value.processInstanceId(),
            value.processTaskInstanceId(),
            value.taskCode(),
            value.initValue(),
            newValue,
            value.initSource(),
            value.source(),
            value.status(),
            value.createdAt(),
            now
        );
    }
}
