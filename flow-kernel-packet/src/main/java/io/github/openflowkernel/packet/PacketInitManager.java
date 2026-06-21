package io.github.openflowkernel.packet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PacketInitManager {
    private final Map<String, ProcessPacketInit> processPacketInitMap =
        new LinkedHashMap<>();

    public PacketInitManager() {
    }

    public PacketInitManager(List<ProcessPacketInit> processPacketInits) {
        if (processPacketInits != null) {
            processPacketInits.forEach(this::register);
        }
    }

    public void register(ProcessPacketInit processPacketInit) {
        Objects.requireNonNull(processPacketInit, "processPacketInit");
        String initName = processPacketInit.initName();
        if (processPacketInitMap.putIfAbsent(initName, processPacketInit) != null) {
            throw new IllegalArgumentException(
                "Duplicate packet init name: " + initName
            );
        }
    }

    public Optional<Packet<?>> init(
        PacketInitConfig packetInitConfig,
        ProcessPacketInit.ProcessPacketInitParam param
    ) {
        if (packetInitConfig == null
            || packetInitConfig.getName() == null
            || packetInitConfig.getName().isBlank()) {
            return Optional.empty();
        }
        ProcessPacketInit processPacketInit = getPacketInit(packetInitConfig.getName())
            .orElse(null);
        if (processPacketInit == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(processPacketInit.initPacket(param));
    }

    public Optional<ProcessPacketInit> getPacketInit(String initName) {
        return Optional.ofNullable(processPacketInitMap.get(initName));
    }
}
