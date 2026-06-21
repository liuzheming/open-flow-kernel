package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.log.ProcessLogRecord;
import io.github.openflowkernel.core.log.ProcessLogRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcProcessLogRepository implements ProcessLogRepository {
    private final DataSource dataSource;

    public JdbcProcessLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void add(
        long processInstanceId,
        long taskInstanceId,
        String stage,
        String result,
        String content
    ) {
        String normalizedContent = content == null
            ? ""
            : content.substring(0, Math.min(content.length(), 2000));
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "insert into process_log(proc_inst_id, task_inst_id, stage, result, content) "
                     + "values (?, ?, ?, ?, ?)"
             )) {
            statement.setLong(1, processInstanceId);
            statement.setLong(2, taskInstanceId);
            statement.setString(3, stage == null ? "" : stage);
            statement.setString(4, result == null ? "" : result);
            statement.setString(5, normalizedContent);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot add process log", exception);
        }
    }

    @Override
    public List<ProcessLogRecord> queryByProcessInstIds(List<Long> processInstanceIds) {
        if (processInstanceIds == null || processInstanceIds.isEmpty()) {
            return List.of();
        }
        List<ProcessLogRecord> result = new ArrayList<>();
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            for (Long processInstanceId : processInstanceIds) {
                try (PreparedStatement statement = connection.prepareStatement(
                    "select * from process_log where proc_inst_id = ? order by id"
                )) {
                    statement.setLong(1, processInstanceId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            result.add(row(resultSet));
                        }
                    }
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot query process log", exception);
        }
    }

    private static ProcessLogRecord row(ResultSet resultSet) throws SQLException {
        return new ProcessLogRecord(
            resultSet.getLong("id"),
            resultSet.getLong("proc_inst_id"),
            resultSet.getLong("task_inst_id"),
            resultSet.getString("stage"),
            resultSet.getString("result"),
            resultSet.getString("content"),
            JdbcSupport.instant(resultSet, "ctime"),
            JdbcSupport.instant(resultSet, "mtime")
        );
    }
}
