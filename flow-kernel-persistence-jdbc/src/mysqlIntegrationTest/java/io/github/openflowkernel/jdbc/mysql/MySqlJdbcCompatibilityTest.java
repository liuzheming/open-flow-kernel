package io.github.openflowkernel.jdbc.mysql;

import io.github.openflowkernel.core.candidate.TaskCandidate;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.task.TaskDefinition;
import io.github.openflowkernel.event.DomainEvent;
import io.github.openflowkernel.event.EventEnvelope;
import io.github.openflowkernel.jdbc.persistence.JdbcCandidateRelationRepository;
import io.github.openflowkernel.jdbc.persistence.JdbcEventPayloadCodec;
import io.github.openflowkernel.jdbc.persistence.JdbcEventStore;
import io.github.openflowkernel.jdbc.persistence.JdbcPacketDataRepo;
import io.github.openflowkernel.jdbc.persistence.JdbcPacketRepo;
import io.github.openflowkernel.jdbc.persistence.JdbcProcessDefinitionRepository;
import io.github.openflowkernel.packet.PacketService;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
final class MySqlJdbcCompatibilityTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("open_flow_kernel")
        .withUsername("open_flow")
        .withPassword("open_flow");

    @Test
    void mysqlSchemaSupportsCoreJdbcRepositories() throws Exception {
        DataSource dataSource = dataSource();
        initializeMysqlSchema(dataSource);

        JdbcProcessDefinitionRepository definitionRepository =
            new JdbcProcessDefinitionRepository(dataSource);
        definitionRepository.save(new ProcessDefinition(
            "mysql-flow",
            "MySQL Flow",
            List.of(new TaskDefinition("submit", "Submit", "submitTask")),
            "samplePacket",
            "{\"name\":\"sampleInit\"}"
        ));
        assertThat(definitionRepository.findByKey("mysql-flow"))
            .get()
            .satisfies(definition -> {
                assertThat(definition.dataPacketResolverClass()).isEqualTo("samplePacket");
                assertThat(definition.packetInitConfig()).isEqualTo("{\"name\":\"sampleInit\"}");
            });

        JdbcPacketDataRepo packetDataRepo = new JdbcPacketDataRepo(dataSource);
        PacketService packetService = new PacketService(
            new JdbcPacketRepo(dataSource, packetDataRepo),
            packetDataRepo
        );
        packetService.initPacketDataRecord(100, "{\"init\":true}", "{}");
        packetService.commit(
            100,
            10,
            "submit",
            packet -> new io.github.openflowkernel.packet.PacketValueRecord(
                packet.id(),
                packet.processInstanceId(),
                packet.processTaskInstanceId(),
                packet.taskCode(),
                packet.initValue(),
                "{\"submitted\":true}",
                packet.initSource(),
                packet.source(),
                packet.status(),
                packet.createdAt(),
                packet.updatedAt()
            )
        );
        assertThat(packetService.getLatest(100))
            .get()
            .extracting(record -> record.value())
            .isEqualTo("{\"submitted\":true}");

        JdbcCandidateRelationRepository candidateRepository =
            new JdbcCandidateRelationRepository(dataSource);
        candidateRepository.addCandidates(
            100,
            "action-1",
            "Action One",
            1,
            List.of(new TaskCandidate("1001", "alice", "Alice")),
            0
        );
        assertThat(candidateRepository.list("action-1", 1))
            .extracting(TaskCandidate::getUcid)
            .containsExactly("1001");

        JdbcEventStore eventStore = new JdbcEventStore(dataSource, new TextCodec());
        eventStore.record(new EventEnvelope<>(
            1,
            new TestEvent("payload"),
            Instant.parse("2026-01-01T00:00:00Z"),
            "process",
            "100",
            "100",
            "100",
            null
        ));
        assertThat(eventStore.findUndispatched())
            .singleElement()
            .satisfies(record -> assertThat(((TestEvent) record.event().payload()).value())
                .isEqualTo("payload"));
    }

    private static DataSource dataSource() {
        return new DriverManagerDataSource(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        );
    }

    private static void initializeMysqlSchema(DataSource dataSource) throws Exception {
        String root = System.getProperty("openFlowKernel.rootDir");
        Path schema = Path.of(
            root,
            "db",
            "schema",
            "mysql",
            "open-flow-kernel-jdbc-schema.sql"
        );
        String sql = Files.readString(schema, StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String item : sql.split(";")) {
                String normalized = item.trim();
                if (!normalized.isEmpty()) {
                    statement.execute(normalized);
                }
            }
        }
    }

    private record DriverManagerDataSource(
        String jdbcUrl,
        String username,
        String password
    ) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public Connection getConnection(String username, String password)
            throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }

    private record TestEvent(String value) implements DomainEvent {
        @Override
        public String eventType() {
            return "mysql-test";
        }
    }

    private static final class TextCodec implements JdbcEventPayloadCodec {
        @Override
        public String encode(DomainEvent event) {
            return ((TestEvent) event).value();
        }

        @Override
        public DomainEvent decode(String payloadClassName, String eventType, String payload) {
            if (!TestEvent.class.getName().equals(payloadClassName)) {
                throw new IllegalArgumentException("Unsupported event class: " + payloadClassName);
            }
            return new TestEvent(payload);
        }
    }
}
