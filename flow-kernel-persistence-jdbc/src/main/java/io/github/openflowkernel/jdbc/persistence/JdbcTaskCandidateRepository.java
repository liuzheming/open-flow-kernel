package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.candidate.TaskCandidate;
import io.github.openflowkernel.core.candidate.TaskCandidateRecord;
import io.github.openflowkernel.core.candidate.TaskCandidateRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JdbcTaskCandidateRepository implements TaskCandidateRepository {
    private final DataSource dataSource;

    public JdbcTaskCandidateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<TaskCandidate> list(long taskInstanceId) {
        return listEntities(taskInstanceId).stream()
            .map(TaskCandidateRecord::toCandidate)
            .toList();
    }

    @Override
    public List<TaskCandidateRecord> listEntities(long taskInstanceId) {
        return query(
            "select * from process_task_inst_candidate "
                + "where proc_task_inst_id = ? and is_delete = 0 order by id",
            statement -> statement.setLong(1, taskInstanceId)
        );
    }

    @Override
    public List<TaskCandidate> listByProcessInstId(long processInstanceId) {
        return query(
            "select * from process_task_inst_candidate "
                + "where proc_inst_id = ? and proc_task_inst_id = 0 and is_delete = 0 "
                + "order by id",
            statement -> statement.setLong(1, processInstanceId)
        ).stream().map(TaskCandidateRecord::toCandidate).toList();
    }

    @Override
    public List<TaskCandidate> listAllByProInstId(long processInstanceId) {
        return allListByProcessInstId(processInstanceId);
    }

    @Override
    public List<TaskCandidate> allListByProcessInstId(long processInstanceId) {
        return query(
            "select * from process_task_inst_candidate "
                + "where proc_inst_id = ? and is_delete = 0 order by id",
            statement -> statement.setLong(1, processInstanceId)
        ).stream().map(TaskCandidateRecord::toCandidate).toList();
    }

    @Override
    public List<TaskCandidate> distinctCandidateByTaskInstIds(List<Long> taskInstanceIds) {
        if (taskInstanceIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(
            taskInstanceIds.size(),
            "?"
        ));
        List<TaskCandidateRecord> records = query(
            "select * from process_task_inst_candidate "
                + "where proc_task_inst_id in (" + placeholders + ") "
                + "and is_delete = 0 order by id",
            statement -> {
                for (int i = 0; i < taskInstanceIds.size(); i++) {
                    statement.setLong(i + 1, taskInstanceIds.get(i));
                }
            }
        );
        Set<String> uniqueUcids = new HashSet<>();
        return records.stream()
            .filter(record -> uniqueUcids.add(record.ucid()))
            .map(TaskCandidateRecord::toCandidate)
            .toList();
    }

    @Override
    public void addCandidates(
        long processInstanceId,
        long taskInstanceId,
        List<TaskCandidate> candidates
    ) {
        if (candidates.isEmpty()) {
            return;
        }
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "insert into process_task_inst_candidate("
                     + "proc_inst_id, proc_task_inst_id, ucid, code, name, is_delete"
                     + ") values (?, ?, ?, ?, ?, 0)"
             )) {
            for (TaskCandidate candidate : candidates) {
                statement.setLong(1, processInstanceId);
                statement.setLong(2, taskInstanceId);
                statement.setString(3, value(candidate.getUcid()));
                statement.setString(4, value(candidate.getUserCode()));
                statement.setString(5, value(candidate.getUsername()));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot add task candidates", exception);
        }
    }

    @Override
    public void deleteCandidates(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update process_task_inst_candidate set is_delete = 1, "
                     + "mtime = current_timestamp where id in (" + placeholders + ")"
             )) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setLong(i + 1, ids.get(i));
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot delete task candidates", exception);
        }
    }

    private List<TaskCandidateRecord> query(String sql, Binder binder) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TaskCandidateRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot query task candidates", exception);
        }
    }

    private static TaskCandidateRecord map(ResultSet resultSet) throws SQLException {
        return new TaskCandidateRecord(
            resultSet.getLong("id"),
            resultSet.getLong("proc_inst_id"),
            resultSet.getLong("proc_task_inst_id"),
            resultSet.getString("ucid"),
            resultSet.getString("code"),
            resultSet.getString("name"),
            resultSet.getInt("is_delete") != 0,
            JdbcSupport.instant(resultSet, "ctime"),
            JdbcSupport.instant(resultSet, "mtime")
        );
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
