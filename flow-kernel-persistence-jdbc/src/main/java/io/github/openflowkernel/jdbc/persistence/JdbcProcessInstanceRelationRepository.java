package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.process.ProcessInstanceRelationRecord;
import io.github.openflowkernel.core.process.ProcessInstanceRelationRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcProcessInstanceRelationRepository
    implements ProcessInstanceRelationRepository {
    private final DataSource dataSource;

    public JdbcProcessInstanceRelationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void addRelation(
        long processInstanceId,
        String relationType,
        String relationCode,
        String relationInstanceId
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "insert into process_inst_relation(process_inst_id, relation_code, "
                     + "relation_type, relation_inst_id) values (?, ?, ?, ?)"
             )) {
            statement.setLong(1, processInstanceId);
            statement.setString(2, relationCode == null ? "" : relationCode);
            statement.setString(3, relationType == null ? "" : relationType);
            statement.setString(4, relationInstanceId == null ? "" : relationInstanceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot add process relation", exception);
        }
    }

    @Override
    public List<ProcessInstanceRelationRecord> queryRelationList(
        String relationType,
        String relationCode,
        String relationInstanceId,
        int limit
    ) {
        StringBuilder sql = new StringBuilder(
            "select * from process_inst_relation where relation_type = ?"
        );
        List<String> args = new ArrayList<>();
        args.add(relationType);
        if (relationCode != null && !relationCode.isBlank()) {
            sql.append(" and relation_code = ?");
            args.add(relationCode);
        }
        if (relationInstanceId != null && !relationInstanceId.isBlank()) {
            sql.append(" and relation_inst_id = ?");
            args.add(relationInstanceId);
        }
        sql.append(" order by id desc limit ?");
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < args.size(); index++) {
                statement.setString(index + 1, args.get(index));
            }
            statement.setInt(args.size() + 1, limit <= 0 ? 20 : limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ProcessInstanceRelationRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(row(resultSet));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot query process relation", exception);
        }
    }

    @Override
    public Optional<ProcessInstanceRelationRecord> getProcessInstRelationEntity(
        long processInstanceId
    ) {
        return getRelationEntitiesByInstId(processInstanceId).stream().findFirst();
    }

    @Override
    public List<ProcessInstanceRelationRecord> getRelationEntitiesByInstId(
        long processInstanceId
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from process_inst_relation where process_inst_id = ? order by id"
             )) {
            statement.setLong(1, processInstanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ProcessInstanceRelationRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(row(resultSet));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot get process relation", exception);
        }
    }

    private static ProcessInstanceRelationRecord row(ResultSet resultSet)
        throws SQLException {
        return new ProcessInstanceRelationRecord(
            resultSet.getLong("id"),
            resultSet.getLong("process_inst_id"),
            resultSet.getString("relation_code"),
            resultSet.getString("relation_type"),
            resultSet.getString("relation_inst_id"),
            resultSet.getLong("process_def_id")
        );
    }
}
