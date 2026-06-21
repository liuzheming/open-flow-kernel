package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.enums.ProcStatusEnum;
import io.github.openflowkernel.core.process.ProcessInstance;
import io.github.openflowkernel.core.process.ProcessInstanceRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcProcessInstanceRepository implements ProcessInstanceRepository {
    private final DataSource dataSource;

    public JdbcProcessInstanceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ProcessInstance create(String definitionKey, String name, Map<String, String> data) {
        return create(definitionKey, name, 0, 0, data);
    }

    @Override
    public ProcessInstance createSubProcess(
        String definitionKey,
        String name,
        long relateProcessInstanceId,
        long relateTaskInstanceId,
        Map<String, String> data
    ) {
        return create(
            definitionKey,
            name,
            relateProcessInstanceId,
            relateTaskInstanceId,
            data
        );
    }

    private ProcessInstance create(
        String definitionKey,
        String name,
        long relateProcessInstanceId,
        long relateTaskInstanceId,
        Map<String, String> data
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            connection.setAutoCommit(false);
            long processDefinitionId = processDefinitionId(connection, definitionKey);
            long id;
            try (PreparedStatement statement = JdbcSupport.insertStatement(
                connection,
                "insert into process_inst(proc_def_id, proc_name, relate_proc_inst_id, "
                    + "relate_task_inst_id, status) values (?, ?, ?, ?, ?)"
            )) {
                statement.setLong(1, processDefinitionId);
                statement.setString(2, name);
                statement.setLong(3, relateProcessInstanceId);
                statement.setLong(4, relateTaskInstanceId);
                statement.setInt(5, ProcStatusEnum.INIT.getStatus());
                statement.executeUpdate();
                id = JdbcSupport.generatedId(statement);
            }
            JdbcSupport.upsertData(
                connection,
                "update process_inst_data set `value` = ? where proc_inst_id = ? and `key` = ?",
                "insert into process_inst_data(proc_inst_id, `key`, `value`) values (?, ?, ?)",
                id,
                data
            );
            connection.commit();
            return findById(id).orElseThrow();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot create process instance", exception);
        }
    }

    @Override
    public Optional<ProcessInstance> findById(long processInstanceId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select pi.id, pd.proc_code, pi.proc_name, pi.status, "
                     + "pi.relate_proc_inst_id, pi.relate_task_inst_id, pi.ctime, pi.mtime "
                     + "from process_inst pi join process_def pd on pi.proc_def_id = pd.id where pi.id = ?"
             )) {
            statement.setLong(1, processInstanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapProcess(connection, resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Cannot find process instance: " + processInstanceId,
                exception
            );
        }
    }

    @Override
    public List<ProcessInstance> findSubProcesses(long relateProcessInstanceId) {
        return findByRelation(
            "select pi.id, pd.proc_code, pi.proc_name, pi.status, "
                + "pi.relate_proc_inst_id, pi.relate_task_inst_id, pi.ctime, pi.mtime "
                + "from process_inst pi join process_def pd on pi.proc_def_id = pd.id "
                + "where pi.relate_proc_inst_id = ? order by pi.id",
            relateProcessInstanceId
        );
    }

    @Override
    public List<ProcessInstance> findSubProcesses(
        long relateProcessInstanceId,
        long relateTaskInstanceId
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select pi.id, pd.proc_code, pi.proc_name, pi.status, "
                     + "pi.relate_proc_inst_id, pi.relate_task_inst_id, pi.ctime, pi.mtime "
                     + "from process_inst pi join process_def pd on pi.proc_def_id = pd.id "
                     + "where pi.relate_proc_inst_id = ? and pi.relate_task_inst_id = ? "
                     + "order by pi.id"
             )) {
            statement.setLong(1, relateProcessInstanceId);
            statement.setLong(2, relateTaskInstanceId);
            return mapProcesses(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot list subprocesses", exception);
        }
    }

    @Override
    public boolean compareAndSetStatus(
        long processInstanceId,
        ProcStatusEnum expected,
        ProcStatusEnum target
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update process_inst set status = ?, mtime = current_timestamp "
                     + "where id = ? and status = ?"
             )) {
            statement.setInt(1, target.getStatus());
            statement.setLong(2, processInstanceId);
            statement.setInt(3, expected.getStatus());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot update process status", exception);
        }
    }

    @Override
    public void mergeData(long processInstanceId, Map<String, String> data) {
        if (data.isEmpty()) {
            return;
        }
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            JdbcSupport.upsertData(
                connection,
                "update process_inst_data set `value` = ? where proc_inst_id = ? and `key` = ?",
                "insert into process_inst_data(proc_inst_id, `key`, `value`) values (?, ?, ?)",
                processInstanceId,
                data
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot merge process data", exception);
        }
    }

    private static long processDefinitionId(Connection connection, String definitionKey)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "select id from process_def where proc_code = ? and is_delete = 0"
        )) {
            statement.setString(1, definitionKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                        "Process definition not found: " + definitionKey
                    );
                }
                return resultSet.getLong("id");
            }
        }
    }

    private List<ProcessInstance> findByRelation(String sql, long relationId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, relationId);
            return mapProcesses(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot list subprocesses", exception);
        }
    }

    private static List<ProcessInstance> mapProcesses(
        Connection connection,
        PreparedStatement statement
    ) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            List<ProcessInstance> result = new ArrayList<>();
            while (resultSet.next()) {
                result.add(mapProcess(connection, resultSet));
            }
            return result;
        }
    }

    private static ProcessInstance mapProcess(Connection connection, ResultSet resultSet)
        throws SQLException {
        long id = resultSet.getLong("id");
        Map<String, String> data = JdbcSupport.loadData(
            connection,
            "select `key` as data_key, `value` as data_value from process_inst_data "
                + "where proc_inst_id = ? order by id",
            id
        );
        return new ProcessInstance(
            id,
            resultSet.getString("proc_code"),
            resultSet.getString("proc_name"),
            ProcStatusEnum.valueByStatus(resultSet.getInt("status")),
            resultSet.getLong("relate_proc_inst_id"),
            resultSet.getLong("relate_task_inst_id"),
            data,
            0,
            JdbcSupport.instant(resultSet, "ctime"),
            JdbcSupport.instant(resultSet, "mtime")
        );
    }
}
