package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessDefinitionRepository;
import io.github.openflowkernel.core.task.TaskDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcProcessDefinitionRepository
    implements ProcessDefinitionRepository {
    private final DataSource dataSource;

    public JdbcProcessDefinitionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(ProcessDefinition definition) {
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            connection.setAutoCommit(false);
            long processDefinitionId = upsertProcessDefinition(connection, definition);
            deleteTaskConfig(connection, processDefinitionId);
            for (TaskDefinition task : definition.tasks()) {
                insertTaskConfig(connection, processDefinitionId, task);
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save process definition", exception);
        }
    }

    @Override
    public Optional<ProcessDefinition> findByKey(String key) {
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            ProcessDefRow process = findProcessDefinition(connection, key);
            if (process == null) {
                return Optional.empty();
            }
            return Optional.of(new ProcessDefinition(
                process.code(),
                process.name(),
                loadTaskDefinitions(connection, process.id()),
                process.dataPacketResolverClass(),
                process.packetInitConfig()
            ));
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find process definition: " + key, exception);
        }
    }

    private static long upsertProcessDefinition(
        Connection connection,
        ProcessDefinition definition
    ) throws SQLException {
        ProcessDefRow existing = findProcessDefinition(connection, definition.key());
        if (existing != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                "update process_def set proc_name = ?, data_packet_resolver_class = ?, "
                    + "packet_init_config = ? where id = ?"
            )) {
                statement.setString(1, definition.name());
                statement.setString(2, definition.dataPacketResolverClass());
                statement.setString(3, definition.packetInitConfig());
                statement.setLong(4, existing.id());
                statement.executeUpdate();
            }
            return existing.id();
        }
        try (PreparedStatement statement = JdbcSupport.insertStatement(
            connection,
            "insert into process_def(proc_code, proc_name, data_packet_resolver_class, packet_init_config) "
                + "values (?, ?, ?, ?)"
        )) {
            statement.setString(1, definition.key());
            statement.setString(2, definition.name());
            statement.setString(3, definition.dataPacketResolverClass());
            statement.setString(4, definition.packetInitConfig());
            statement.executeUpdate();
            return JdbcSupport.generatedId(statement);
        }
    }

    private static ProcessDefRow findProcessDefinition(Connection connection, String key)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "select id, proc_code, proc_name, data_packet_resolver_class, packet_init_config "
                + "from process_def where proc_code = ? and is_delete = 0"
        )) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new ProcessDefRow(
                    resultSet.getLong("id"),
                    resultSet.getString("proc_code"),
                    resultSet.getString("proc_name"),
                    resultSet.getString("data_packet_resolver_class"),
                    resultSet.getString("packet_init_config")
                );
            }
        }
    }

    private static void deleteTaskConfig(Connection connection, long processDefinitionId)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "delete from process_task_config where process_def_id = ?"
        )) {
            statement.setLong(1, processDefinitionId);
            statement.executeUpdate();
        }
    }

    private static void insertTaskConfig(
        Connection connection,
        long processDefinitionId,
        TaskDefinition task
    ) throws SQLException {
        for (Map.Entry<String, String> entry : task.config().entrySet()) {
            try (PreparedStatement statement = connection.prepareStatement(
                "insert into process_task_config(process_def_id, task_code, `key`, `value`) values (?, ?, ?, ?)"
            )) {
                statement.setLong(1, processDefinitionId);
                statement.setString(2, task.code());
                statement.setString(3, entry.getKey());
                statement.setString(4, entry.getValue());
                statement.executeUpdate();
            }
        }
    }

    private static List<TaskDefinition> loadTaskDefinitions(
        Connection connection,
        long processDefinitionId
    ) throws SQLException {
        Map<String, Map<String, String>> configByTask = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select task_code, `key` as config_key, `value` as config_value "
                + "from process_task_config where process_def_id = ? order by id"
        )) {
            statement.setLong(1, processDefinitionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    configByTask.computeIfAbsent(
                        resultSet.getString("task_code"),
                        ignored -> new LinkedHashMap<>()
                    ).put(resultSet.getString("config_key"), resultSet.getString("config_value"));
                }
            }
        }
        List<TaskDefinition> tasks = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : configByTask.entrySet()) {
            Map<String, String> config = entry.getValue();
            tasks.add(new TaskDefinition(
                entry.getKey(),
                config.get(TaskConfigKeyConstant.TASK_NAME),
                config.get(TaskConfigKeyConstant.HANDLER_NAME),
                config
            ));
        }
        return tasks;
    }

    private record ProcessDefRow(
        long id,
        String code,
        String name,
        String dataPacketResolverClass,
        String packetInitConfig
    ) {
    }
}
