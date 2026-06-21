package io.github.openflowkernel.packet.contract;

import io.github.openflowkernel.core.enums.ProcessEventEnum;
import io.github.openflowkernel.core.event.ProcessEvent;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.packet.PacketService;
import io.github.openflowkernel.packet.PacketStatusEnum;
import io.github.openflowkernel.packet.PacketValueRecord;
import io.github.openflowkernel.packet.PacketValueStatusEnum;
import io.github.openflowkernel.packet.event.PacketEventListener;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public interface PacketRepositoryContract {
    PacketRepositories repositories();

    @Test
    default void initializesAndCommitsPacketWithPointerCas() {
        PacketRepositories repositories = repositories();
        PacketService service = service(repositories);

        service.initPacketDataRecord(100, "{\"init\":true}", "{\"source\":\"init\"}");
        PacketValueRecord init = service.getLatest(100).orElseThrow();
        assertThat(init.processTaskInstanceId()).isZero();
        assertThat(init.value()).isEqualTo("{\"init\":true}");

        PacketValueRecord first = service.commit(
            100,
            10,
            "submit",
            packet -> new PacketValueRecord(
                packet.id(),
                packet.processInstanceId(),
                packet.processTaskInstanceId(),
                packet.taskCode(),
                packet.initValue(),
                "{\"step\":\"submit\"}",
                packet.initSource(),
                "{\"source\":\"submit\"}",
                packet.status(),
                packet.createdAt(),
                packet.updatedAt()
            )
        );

        assertThat(first.processTaskInstanceId()).isEqualTo(10);
        assertThat(service.getLatest(100).orElseThrow().id()).isEqualTo(first.id());
        assertThat(repositories.packetRepo().getPacket(100).orElseThrow().dataPacketValueId())
            .isEqualTo(first.id());
        assertThatThrownBy(() -> repositories.packetRepo().commit(
            100,
            11,
            "review",
            "{}",
            "{}",
            init.id()
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    default void expiresLatestTaskCommitAndResetsPointer() {
        PacketRepositories repositories = repositories();
        PacketService service = service(repositories);

        service.initPacketDataRecord(200);
        PacketValueRecord init = service.getLatest(200).orElseThrow();
        PacketValueRecord first = service.commit(
            200,
            20,
            "submit",
            packet -> withValue(packet, "{\"step\":1}")
        );
        PacketValueRecord second = service.commit(
            200,
            21,
            "submit",
            packet -> withValue(packet, "{\"step\":2}")
        );

        service.expire(200, "submit");

        assertThat(repositories.packetDataRepo().getPacketValue(first.id()).orElseThrow().status())
            .isEqualTo(PacketValueStatusEnum.COMPLETED);
        assertThat(repositories.packetDataRepo().getPacketValue(second.id()).orElseThrow().status())
            .isEqualTo(PacketValueStatusEnum.EXPIRED);
        assertThat(repositories.packetRepo().getPacket(200).orElseThrow().dataPacketValueId())
            .isEqualTo(first.id());
        assertThat(repositories.packetDataRepo().getLatestExpired(200, "submit"))
            .isPresent();
    }

    @Test
    default void updatesPacketStatusOnProcessLifecycleEvents() {
        PacketRepositories repositories = repositories();
        PacketService service = service(repositories);
        PacketEventListener listener = new PacketEventListener(service);

        service.initPacketDataRecord(300);
        listener.listen(envelope(300, ProcessEventEnum.PROCESS_COMPLETE));
        assertThat(repositories.packetRepo().getPacket(300).orElseThrow().status())
            .isEqualTo(PacketStatusEnum.COMPLETED);

        service.initPacketDataRecord(301);
        listener.listen(envelope(301, ProcessEventEnum.PROCESS_CANCEL));
        assertThat(repositories.packetRepo().getPacket(301).orElseThrow().status())
            .isEqualTo(PacketStatusEnum.CANCELED);
    }

    private static PacketService service(PacketRepositories repositories) {
        return new PacketService(repositories.packetRepo(), repositories.packetDataRepo());
    }

    private static PacketValueRecord withValue(PacketValueRecord packet, String value) {
        return new PacketValueRecord(
            packet.id(),
            packet.processInstanceId(),
            packet.processTaskInstanceId(),
            packet.taskCode(),
            packet.initValue(),
            value,
            packet.initSource(),
            packet.source(),
            packet.status(),
            packet.createdAt(),
            packet.updatedAt()
        );
    }

    private static EventEnvelope<ProcessEvent> envelope(
        long processInstanceId,
        ProcessEventEnum eventEnum
    ) {
        ProcessEvent event = new ProcessEvent();
        event.setProcessInstId(processInstanceId);
        event.setProcName("Packet Test");
        event.setMain(true);
        event.setProcEventCode(eventEnum.getCode());
        return new EventEnvelope<>(
            processInstanceId,
            event,
            Instant.parse("2026-01-01T00:00:00Z"),
            "process",
            Long.toString(processInstanceId),
            Long.toString(processInstanceId),
            Long.toString(processInstanceId),
            null
        );
    }
}
