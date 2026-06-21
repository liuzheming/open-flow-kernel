package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.relation.TaskRelation;
import io.github.openflowkernel.core.relation.TaskRelationRepository;
import io.github.openflowkernel.core.relation.TaskRelationStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcTaskRelationRepository implements TaskRelationRepository {
    private final DataSource dataSource;

    public JdbcTaskRelationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public TaskRelation create(
        long taskInstanceId,
        String relationType,
        String relationInstanceId
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = JdbcSupport.insertStatement(
                 connection,
                 "insert into process_task_inst_relation(task_inst_id, relation_type, relation_inst_id, status) "
                     + "values (?, ?, ?, ?)"
             )) {
            statement.setLong(1, taskInstanceId);
            statement.setString(2, relationType);
            statement.setString(3, relationInstanceId);
            statement.setInt(4, 0);
            statement.executeUpdate();
            return findById(JdbcSupport.generatedId(statement)).orElseThrow();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot create task relation", exception);
        }
    }

    @Override
    public Optional<TaskRelation> find(String relationType, String relationInstanceId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from process_task_inst_relation where relation_type = ? and relation_inst_id = ? limit 1"
             )) {
            statement.setString(1, relationType);
            statement.setString(2, relationInstanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRelation(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find task relation", exception);
        }
    }

    @Override
    public List<TaskRelation> findByTaskInstanceId(long taskInstanceId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from process_task_inst_relation where task_inst_id = ? order by id"
             )) {
            statement.setLong(1, taskInstanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TaskRelation> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(mapRelation(resultSet));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot list task relations", exception);
        }
    }

    @Override
    public boolean compareAndSetCompleted(long relationId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update process_task_inst_relation set status = 1, mtime = current_timestamp "
                     + "where id = ? and status = 0"
             )) {
            statement.setLong(1, relationId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot complete task relation", exception);
        }
    }

    private Optional<TaskRelation> findById(long id) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from process_task_inst_relation where id = ?"
             )) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRelation(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot find task relation: " + id, exception);
        }
    }

    private static TaskRelation mapRelation(ResultSet resultSet) throws SQLException {
        return new TaskRelation(
            resultSet.getLong("id"),
            resultSet.getLong("task_inst_id"),
            resultSet.getString("relation_type"),
            resultSet.getString("relation_inst_id"),
            resultSet.getInt("status") == 1
                ? TaskRelationStatus.COMPLETED
                : TaskRelationStatus.PENDING,
            0,
            JdbcSupport.instant(resultSet, "ctime"),
            JdbcSupport.instant(resultSet, "mtime")
        );
    }
}
