package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.core.task.TaskInstanceRecord;
import io.github.openflowkernel.core.task.TaskInstanceRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public final class JdbcTaskInstanceRepository implements TaskInstanceRepository {
    private final DataSource dataSource;

    public JdbcTaskInstanceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public TaskInstanceRecord create(
        long processInstanceId,
        String requestId,
        String taskCode,
        String taskName
    ) {
        return create(processInstanceId, requestId, taskCode, taskName, Map.of());
    }

    @Override
    public TaskInstanceRecord create(
        long processInstanceId,
        String requestId,
        String taskCode,
        String taskName,
        Map<String, String> instData
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            connection.setAutoCommit(false);
            long id;
            try (PreparedStatement statement = JdbcSupport.insertStatement(
                connection,
                "insert into process_task_inst(proc_inst_id, task_code, task_name, request_id, status) "
                    + "values (?, ?, ?, ?, ?)"
            )) {
                statement.setLong(1, processInstanceId);
                statement.setString(2, taskCode);
                statement.setString(3, taskName);
                statement.setString(4, requestId);
                statement.setInt(5, ProcTaskStatusEnum.CREATE.getStatus());
                statement.executeUpdate();
                id = JdbcSupport.generatedId(statement);
            }
            JdbcSupport.upsertData(
                connection,
                "update process_task_inst_data set `value` = ? where proc_task_inst_id = ? and `key` = ?",
                "insert into process_task_inst_data(proc_task_inst_id, `key`, `value`) values (?, ?, ?)",
                id,
                instData
            );
            connection.commit();
            return findById(id).orElseThrow();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot create task instance", exception);
        }
    }

    @Override
    public Optional<TaskInstanceRecord> findById(long taskInstanceId) {
        return findBy("select * from process_task_inst where id = ?", taskInstanceId);
    }

    @Override
    public Optional<TaskInstanceRecord> findByEngineTaskId(String engineTaskId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from process_task_inst where request_id = ?"
             )) {
            statement.setString(1, engineTaskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTask(connection, resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find task by requestId", exception);
        }
    }

    @Override
    public Optional<TaskInstanceRecord> findLatest(long processInstanceId, String taskCode) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from process_task_inst where proc_inst_id = ? and task_code = ? "
                     + "order by id desc limit 1"
             )) {
            statement.setLong(1, processInstanceId);
            statement.setString(2, taskCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTask(connection, resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find latest task", exception);
        }
    }

    @Override
    public boolean compareAndSetStatus(
        long taskInstanceId,
        ProcTaskStatusEnum expected,
        ProcTaskStatusEnum target
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update process_task_inst set status = ?, mtime = current_timestamp "
                     + "where id = ? and status = ?"
             )) {
            statement.setInt(1, target.getStatus());
            statement.setLong(2, taskInstanceId);
            statement.setInt(3, expected.getStatus());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot update task status", exception);
        }
    }

    @Override
    public void mergeData(long taskInstanceId, Map<String, String> data) {
        if (data.isEmpty()) {
            return;
        }
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            JdbcSupport.upsertData(
                connection,
                "update process_task_inst_data set `value` = ? where proc_task_inst_id = ? and `key` = ?",
                "insert into process_task_inst_data(proc_task_inst_id, `key`, `value`) values (?, ?, ?)",
                taskInstanceId,
                data
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot merge task data", exception);
        }
    }

    private Optional<TaskInstanceRecord> findBy(String sql, long id) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTask(connection, resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find task: " + id, exception);
        }
    }

    private static TaskInstanceRecord mapTask(Connection connection, ResultSet resultSet)
        throws SQLException {
        long id = resultSet.getLong("id");
        Map<String, String> data = JdbcSupport.loadData(
            connection,
            "select `key` as data_key, `value` as data_value from process_task_inst_data "
                + "where proc_task_inst_id = ? order by id",
            id
        );
        return new TaskInstanceRecord(
            id,
            resultSet.getLong("proc_inst_id"),
            resultSet.getString("request_id"),
            resultSet.getString("task_code"),
            resultSet.getString("task_name"),
            ProcTaskStatusEnum.valueByStatus(resultSet.getInt("status")),
            data,
            0,
            JdbcSupport.instant(resultSet, "ctime"),
            JdbcSupport.instant(resultSet, "mtime")
        );
    }
}
