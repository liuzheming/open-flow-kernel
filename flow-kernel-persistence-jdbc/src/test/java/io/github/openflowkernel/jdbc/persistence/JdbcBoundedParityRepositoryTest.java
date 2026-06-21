package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.candidate.TaskCandidate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class JdbcBoundedParityRepositoryTest {
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = dataSource();
        new JdbcSchemaInitializer(dataSource).initialize();
    }

    @Test
    void processInstanceRelationRepositoryMirrorsReferenceQueries() {
        JdbcProcessInstanceRelationRepository repository =
            new JdbcProcessInstanceRelationRepository(dataSource);

        repository.addRelation(100, "brand", "brandCode", "brand-1");
        repository.addRelation(101, "brand", "brandCode", "brand-2");

        assertThat(repository.getRelationEntitiesByInstId(100))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.processInstanceId()).isEqualTo(100);
                assertThat(record.relationType()).isEqualTo("brand");
                assertThat(record.relationCode()).isEqualTo("brandCode");
                assertThat(record.relationInstanceId()).isEqualTo("brand-1");
            });
        assertThat(repository.queryRelationList("brand", "brandCode", null, 10))
            .extracting(record -> record.processInstanceId())
            .containsExactly(101L, 100L);
    }

    @Test
    void processLogRepositoryTruncatesContentAndQueriesByProcessIds() {
        JdbcProcessLogRepository repository = new JdbcProcessLogRepository(dataSource);
        String longContent = "x".repeat(2100);

        repository.add(200, 20, "preExecute", "success", longContent);
        repository.add(201, "start", "success");

        assertThat(repository.queryByProcessInstIds(List.of(200L, 201L)))
            .hasSize(2);
        assertThat(repository.queryByProcessInstIds(List.of(200L)))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.taskInstanceId()).isEqualTo(20);
                assertThat(record.stage()).isEqualTo("preExecute");
                assertThat(record.result()).isEqualTo("success");
                assertThat(record.content()).hasSize(2000);
            });
    }

    @Test
    void processInstanceDataInitRepositorySupportsInitUpdateDelete() {
        JdbcProcessInstanceDataInitRepository repository =
            new JdbcProcessInstanceDataInitRepository(dataSource);

        repository.init(300, List.of("110000", "120000"), "cityMode", "INNER");
        repository.init(300, List.of("110000"), "cityMode", "SHOULD_NOT_REPLACE");

        assertThat(repository.get(300, "110000"))
            .containsExactlyEntriesOf(Map.of("cityMode", "INNER"));

        repository.update(300, List.of("110000"), "cityMode", "OUTER");
        assertThat(repository.get(300, "110000"))
            .containsEntry("cityMode", "OUTER");

        repository.delete(300, List.of("110000"), List.of("cityMode"));
        assertThat(repository.get(300, "110000")).isEmpty();
        assertThat(repository.get(300, "120000"))
            .containsEntry("cityMode", "INNER");
    }

    @Test
    void candidateRelationRepositorySupportsActionCandidateBaseline() {
        JdbcCandidateRelationRepository repository =
            new JdbcCandidateRelationRepository(dataSource);

        repository.addCandidates(
            400,
            "action-1",
            "Action One",
            1,
            List.of(
                new TaskCandidate("1001", "alice", "Alice"),
                new TaskCandidate("1002", "bob", "Bob")
            ),
            0
        );
        repository.addCandidates(
            401,
            "action-2",
            "Action Two",
            1,
            List.of(new TaskCandidate("1001", "alice", "Alice")),
            0
        );

        assertThat(repository.list("action-1", 1))
            .extracting(TaskCandidate::getUcid)
            .containsExactly("1001", "1002");
        assertThat(repository.distinctCandidateByActionItemNos(
            List.of("action-1", "action-2"),
            1
        )).extracting(TaskCandidate::getUcid)
            .containsExactly("1001", "1002");

        long firstId = repository.listEntities("action-1", 1).get(0).id();
        repository.deleteCandidates(List.of(firstId));
        repository.updateCandidateStatus("action-1", 1, 1);

        assertThat(repository.list("action-1", 1))
            .extracting(TaskCandidate::getUcid)
            .containsExactly("1002");
        assertThat(repository.listEntities("action-1", 1))
            .singleElement()
            .extracting(record -> record.status())
            .isEqualTo(1);
        assertThat(repository.listByUcId("1001"))
            .extracting(record -> record.relateInstanceId())
            .containsExactly("action-2");
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl(
            "jdbc:h2:mem:" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
