package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.candidate.CandidateRelationRecord;
import io.github.openflowkernel.core.candidate.CandidateRelationRepository;
import io.github.openflowkernel.core.candidate.TaskCandidate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JdbcCandidateRelationRepository implements CandidateRelationRepository {
    private final DataSource dataSource;

    public JdbcCandidateRelationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<TaskCandidate> list(String relateInstanceId, int type) {
        return listEntities(relateInstanceId, type).stream()
            .map(record -> new TaskCandidate(record.ucid(), record.code(), record.name()))
            .toList();
    }

    @Override
    public List<CandidateRelationRecord> listEntities(String relateInstanceId, int type) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from candidate_inst_relation where relate_inst_id = ? "
                     + "and `type` = ? and deleted = 0 order by id"
             )) {
            statement.setString(1, relateInstanceId);
            statement.setInt(2, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CandidateRelationRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(row(resultSet));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot list candidate relation", exception);
        }
    }

    @Override
    public List<CandidateRelationRecord> listByUcId(String ucid) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from candidate_inst_relation where ucid = ? "
                     + "and deleted = 0 order by id"
             )) {
            statement.setString(1, ucid);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CandidateRelationRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(row(resultSet));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot list candidate relation by ucid", exception);
        }
    }

    @Override
    public void addCandidates(
        long processInstanceId,
        String relateInstanceId,
        String relateInstanceName,
        int type,
        List<TaskCandidate> candidates,
        int status
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            for (TaskCandidate candidate : candidates) {
                try (PreparedStatement statement = connection.prepareStatement(
                    "insert into candidate_inst_relation(proc_inst_id, relate_inst_id, ucid, "
                        + "code, name, `type`, status, task_name) values (?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    statement.setLong(1, processInstanceId);
                    statement.setString(2, relateInstanceId);
                    statement.setString(3, candidate.getUcid());
                    statement.setString(4, candidate.getUserCode());
                    statement.setString(5, candidate.getUsername());
                    statement.setInt(6, type);
                    statement.setInt(7, status);
                    statement.setString(8, relateInstanceName == null ? "" : relateInstanceName);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot add candidate relation", exception);
        }
    }

    @Override
    public void updateCandidateStatus(String relateInstanceId, int type, int status) {
        updateByRelateInstance(
            "update candidate_inst_relation set status = ? where relate_inst_id = ? and `type` = ?",
            status,
            relateInstanceId,
            type
        );
    }

    @Override
    public void updateCandidateDeleted(String relateInstanceId, int type, int deleted) {
        updateByRelateInstance(
            "update candidate_inst_relation set deleted = ? where relate_inst_id = ? and `type` = ?",
            deleted,
            relateInstanceId,
            type
        );
    }

    @Override
    public void deleteCandidates(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            for (Long id : ids) {
                try (PreparedStatement statement = connection.prepareStatement(
                    "update candidate_inst_relation set deleted = 1 where id = ?"
                )) {
                    statement.setLong(1, id);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot delete candidate relation", exception);
        }
    }

    @Override
    public List<TaskCandidate> distinctCandidateByActionItemNos(
        List<String> relateInstanceIds,
        int type
    ) {
        if (relateInstanceIds == null || relateInstanceIds.isEmpty()) {
            return List.of();
        }
        Map<String, TaskCandidate> result = new LinkedHashMap<>();
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            for (String relateInstanceId : relateInstanceIds) {
                for (CandidateRelationRecord record : listEntities(connection, relateInstanceId, type)) {
                    result.putIfAbsent(
                        record.ucid(),
                        new TaskCandidate(record.ucid(), record.code(), record.name())
                    );
                }
            }
            return new ArrayList<>(result.values());
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Cannot distinct candidate relation",
                exception
            );
        }
    }

    private List<CandidateRelationRecord> listEntities(
        Connection connection,
        String relateInstanceId,
        int type
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "select * from candidate_inst_relation where relate_inst_id = ? "
                + "and `type` = ? and deleted = 0 order by id"
        )) {
            statement.setString(1, relateInstanceId);
            statement.setInt(2, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CandidateRelationRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(row(resultSet));
                }
                return result;
            }
        }
    }

    private void updateByRelateInstance(
        String sql,
        int value,
        String relateInstanceId,
        int type
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, value);
            statement.setString(2, relateInstanceId);
            statement.setInt(3, type);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot update candidate relation", exception);
        }
    }

    private static CandidateRelationRecord row(ResultSet resultSet) throws SQLException {
        return new CandidateRelationRecord(
            resultSet.getLong("id"),
            resultSet.getLong("proc_inst_id"),
            resultSet.getString("relate_inst_id"),
            resultSet.getString("ucid"),
            resultSet.getString("code"),
            resultSet.getString("name"),
            resultSet.getInt("deleted"),
            resultSet.getInt("type"),
            resultSet.getInt("status"),
            resultSet.getString("task_name")
        );
    }
}
