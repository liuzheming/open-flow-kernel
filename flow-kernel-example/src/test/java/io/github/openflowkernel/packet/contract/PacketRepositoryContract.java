package io.github.openflowkernel.packet.contract;

import io.github.openflowkernel.core.enums.ProcessEventEnum;
import io.github.openflowkernel.core.event.ProcessEvent;
import io.github.openflowkernel.core.enums.ProcStatusEnum;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessInstance;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.packet.Packet;
import io.github.openflowkernel.packet.PacketFactory;
import io.github.openflowkernel.packet.PacketInitConfig;
import io.github.openflowkernel.packet.PacketInitManager;
import io.github.openflowkernel.packet.PacketProcessStartHook;
import io.github.openflowkernel.packet.PacketService;
import io.github.openflowkernel.packet.PacketStatusEnum;
import io.github.openflowkernel.packet.PacketValueRecord;
import io.github.openflowkernel.packet.PacketValueStatusEnum;
import io.github.openflowkernel.packet.ProcessPacketInit;
import io.github.openflowkernel.packet.event.PacketEventListener;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    @Test
    default void supportsTypedPacketFactoryCommitAndInitManager() {
        PacketRepositories repositories = repositories();
        PacketFactory packetFactory = new PacketFactory();
        packetFactory.registerPacketClass("samplePacket", SamplePacket::new);
        PacketService service = new PacketService(
            repositories.packetRepo(),
            repositories.packetDataRepo(),
            packetFactory
        );
        PacketInitManager initManager = new PacketInitManager(List.of(
            new SamplePacketInit()
        ));

        Packet<?> initPacket = initManager.init(
            new PacketInitConfig("sampleInit"),
            new ProcessPacketInit.ProcessPacketInitParam(
                "sample-flow",
                1,
                Map.of("packetValue", "created")
            )
        ).orElseThrow();
        service.initPacketDataRecord(400, initPacket);

        SamplePacket latest = service.<SamplePacket>getLatest(400, "samplePacket")
            .orElseThrow();
        assertThat(latest.getValue()).isEqualTo("created");

        SamplePacket committed = service.commit(
            400,
            40,
            "submit",
            "samplePacket",
            packet -> {
                packet.setValue(packet.getValue() + "-submitted");
                return packet;
            }
        );

        assertThat(committed.getProcInstId()).isEqualTo(400);
        assertThat(committed.getProcTaskInstId()).isEqualTo(40);
        assertThat(committed.getTaskCode()).isEqualTo("submit");
        assertThat(committed.getValue()).isEqualTo("created-submitted");
        assertThat(service.<SamplePacket>getLatest(400, "samplePacket").orElseThrow()
            .getValue()).isEqualTo("created-submitted");
    }

    @Test
    default void packetProcessStartHookInitializesPacketFromDefinitionConfig() {
        PacketRepositories repositories = repositories();
        PacketService service = service(repositories);
        PacketInitManager initManager = new PacketInitManager(List.of(
            new SamplePacketInit()
        ));
        PacketProcessStartHook hook = new PacketProcessStartHook(service, initManager);
        ProcessDefinition definition = new ProcessDefinition(
            "sample-flow",
            "Sample Flow",
            List.of(),
            "samplePacket",
            "{\"name\":\"sampleInit\"}"
        );
        ProcessInstance instance = new ProcessInstance(
            500,
            definition.key(),
            definition.name(),
            ProcStatusEnum.INIT,
            0,
            0,
            Map.of(),
            0,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z")
        );

        hook.afterProcessCreated(
            definition,
            instance,
            Map.of("packetValue", "from-start")
        );

        assertThat(service.getLatest(500)).get()
            .extracting(PacketValueRecord::value)
            .isEqualTo("from-start");
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

    final class SamplePacket extends Packet<String> {
        @Override
        protected String deserializeValue(String value) {
            return value;
        }

        @Override
        protected String serializeValue(String value) {
            return value == null ? "{}" : value;
        }

        @Override
        public String getCurrentProcIntroduction() {
            return "sample packet";
        }

        @Override
        public String getCurrentProcName() {
            return "Sample Packet";
        }
    }

    final class SamplePacketInit implements ProcessPacketInit {
        @Override
        public String initName() {
            return "sampleInit";
        }

        @Override
        public Packet<?> initPacket(ProcessPacketInitParam param) {
            SamplePacket packet = new SamplePacket();
            packet.setValue(param.processInstData().getOrDefault("packetValue", "{}"));
            return packet;
        }
    }
}
