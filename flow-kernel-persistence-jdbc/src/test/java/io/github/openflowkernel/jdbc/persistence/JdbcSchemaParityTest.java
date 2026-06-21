package io.github.openflowkernel.jdbc.persistence;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class JdbcSchemaParityTest {
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = dataSource();
        new JdbcSchemaInitializer(dataSource).initialize();
    }

    @Test
    void flowTablesKeepReferenceMigrationColumns() throws Exception {
        assertColumns(
            "process_def",
            "id",
            "proc_code",
            "proc_name",
            "biz_proc_code",
            "version",
            "proc_type",
            "proc_type_name",
            "business_code",
            "data_packet_resolver_class",
            "pre_check_config",
            "packet_init_config",
            "init_inst_data",
            "init_params"
        );
        assertColumns(
            "process_inst",
            "id",
            "proc_def_id",
            "proc_name",
            "business_code",
            "city_operate_mode",
            "relate_proc_inst_id",
            "relate_task_inst_id",
            "main_proc_inst_id",
            "parent_proc_inst_id",
            "derived_proc_inst_id",
            "process_candidates",
            "cancel_task_name",
            "cancel_code",
            "cancel_reason",
            "proc_type",
            "biz_scene",
            "proc_desc",
            "city_code",
            "source"
        );
        assertColumns(
            "candidate_inst_relation",
            "proc_inst_id",
            "relate_inst_id",
            "ucid",
            "code",
            "name",
            "deleted",
            "type",
            "status",
            "task_name"
        );
        assertColumns(
            "process_task_inst_relation",
            "task_inst_id",
            "relation_type",
            "relation_inst_id",
            "status"
        );
        assertColumnSize("process_task_inst_relation", "relation_inst_id", 128);
    }

    @Test
    void eventTablesKeepAdapterPersistenceColumns() throws Exception {
        assertColumns(
            "event_record",
            "event_id",
            "event_type",
            "payload_class",
            "payload",
            "occurred_at",
            "subject_type",
            "subject_id",
            "partition_key",
            "correlation_id",
            "causation_event_id",
            "status",
            "recorded_at",
            "dispatched_at"
        );
        assertColumns(
            "event_delivery",
            "event_id",
            "listener_name",
            "status",
            "attempts",
            "next_attempt_at",
            "last_error"
        );
    }

    private void assertColumns(String table, String... expectedColumns) throws SQLException {
        assertThat(columns(table))
            .contains(expectedColumns);
    }

    private Set<String> columns(String table) throws SQLException {
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet resultSet = metadata.getColumns(null, null, table, null)) {
                Set<String> columns = new LinkedHashSet<>();
                while (resultSet.next()) {
                    columns.add(resultSet.getString("COLUMN_NAME"));
                }
                return columns;
            }
        }
    }

    private void assertColumnSize(String table, String column, int expectedSize) throws SQLException {
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet resultSet = metadata.getColumns(null, null, table, column)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt("COLUMN_SIZE")).isEqualTo(expectedSize);
            }
        }
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
