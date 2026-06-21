package io.github.openflowkernel.packet.event;

import io.github.openflowkernel.core.enums.ProcessEventEnum;
import io.github.openflowkernel.core.event.ProcessEvent;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.event.EventListener;
import io.github.openflowkernel.packet.PacketService;

import java.util.Objects;

public final class PacketEventListener implements EventListener<ProcessEvent> {
    private final PacketService packetService;

    public PacketEventListener(PacketService packetService) {
        this.packetService = Objects.requireNonNull(packetService);
    }

    @Override
    public void listen(EventEnvelope<ProcessEvent> envelope) {
        ProcessEvent event = envelope.payload();
        if (ProcessEventEnum.PROCESS_COMPLETE.getCode().equals(event.getProcEventCode())) {
            packetService.completePacket(event.getProcessInstId());
        } else if (ProcessEventEnum.PROCESS_CANCEL.getCode().equals(event.getProcEventCode())) {
            packetService.cancelPacket(event.getProcessInstId());
        }
    }
}
