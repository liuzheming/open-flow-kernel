package io.github.openflowkernel.packet;

import java.util.Map;

public interface ProcessPacketInit {
    String initName();

    Packet<?> initPacket(ProcessPacketInitParam param);

    record ProcessPacketInitParam(
        String processDefinitionKey,
        long processDefinitionId,
        Map<String, String> processInstData
    ) {
        public ProcessPacketInitParam {
            processInstData = processInstData == null ? Map.of() : Map.copyOf(processInstData);
        }
    }
}
