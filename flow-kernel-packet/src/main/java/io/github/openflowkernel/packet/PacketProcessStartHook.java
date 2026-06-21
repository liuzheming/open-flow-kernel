package io.github.openflowkernel.packet;

import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessInstance;
import io.github.openflowkernel.core.process.ProcessStartHook;

import java.util.Map;
import java.util.Objects;

public final class PacketProcessStartHook implements ProcessStartHook {
    private final PacketService packetService;
    private final PacketInitManager packetInitManager;

    public PacketProcessStartHook(
        PacketService packetService,
        PacketInitManager packetInitManager
    ) {
        this.packetService = Objects.requireNonNull(packetService);
        this.packetInitManager = Objects.requireNonNull(packetInitManager);
    }

    @Override
    public void afterProcessCreated(
        ProcessDefinition definition,
        ProcessInstance instance,
        Map<String, String> initialData
    ) {
        Packet<?> initPacket = packetInitManager.init(
            parsePacketInitConfig(definition.packetInitConfig()),
            new ProcessPacketInit.ProcessPacketInitParam(
                definition.key(),
                0,
                initialData
            )
        ).orElse(null);
        packetService.initPacketDataRecord(instance.id(), initPacket);
    }

    private static PacketInitConfig parsePacketInitConfig(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("{")) {
            return new PacketInitConfig(trimmed);
        }
        String key = "\"name\"";
        int keyIndex = trimmed.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = trimmed.indexOf(':', keyIndex + key.length());
        if (colonIndex < 0) {
            return null;
        }
        int startQuote = trimmed.indexOf('"', colonIndex + 1);
        if (startQuote < 0) {
            return null;
        }
        int endQuote = trimmed.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return null;
        }
        return new PacketInitConfig(trimmed.substring(startQuote + 1, endQuote));
    }
}
