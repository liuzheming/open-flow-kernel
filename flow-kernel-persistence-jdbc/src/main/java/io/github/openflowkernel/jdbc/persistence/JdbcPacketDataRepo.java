package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.packet.PacketValueRecord;
import io.github.openflowkernel.packet.PacketValueStatusEnum;
import io.github.openflowkernel.packet.repo.PacketDataRepo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcPacketDataRepo implements PacketDataRepo {
    private final DataSource dataSource;

    public JdbcPacketDataRepo(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PacketValueRecord> getLatestExpired(
        long processInstanceId,
        String taskCode
    ) {
        return findOne(
            "select * from process_inst_data_packet_value "
                + "where proc_inst_id = ? and task_code = ? and status = ? "
                + "order by id desc limit 1",
            statement -> {
                statement.setLong(1, processInstanceId);
                statement.setString(2, taskCode);
                statement.setInt(3, PacketValueStatusEnum.EXPIRED.getCode());
            }
        );
    }

    @Override
    public Optional<PacketValueRecord> getLatestFirstUnexpiredCommit(
        long processInstanceId,
        String taskCode
    ) {
        Optional<PacketValueRecord> latest = findOne(
            "select * from process_inst_data_packet_value "
                + "where proc_inst_id = ? and task_code = ? and status <> ? "
                + "order by id desc limit 1",
            statement -> {
                statement.setLong(1, processInstanceId);
                statement.setString(2, taskCode);
                statement.setInt(3, PacketValueStatusEnum.EXPIRED.getCode());
            }
        );
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        long latestTaskInstanceId = latest.get().processTaskInstanceId();
        return findOne(
            "select * from process_inst_data_packet_value "
                + "where proc_inst_id = ? and proc_task_inst_id = ? "
                + "order by id asc limit 1",
            statement -> {
                statement.setLong(1, processInstanceId);
                statement.setLong(2, latestTaskInstanceId);
            }
        );
    }

    @Override
    public void expireFromValueId(long processInstanceId, long valueId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update process_inst_data_packet_value set status = ?, "
                     + "mtime = current_timestamp where id >= ? and proc_inst_id = ?"
             )) {
            statement.setInt(1, PacketValueStatusEnum.EXPIRED.getCode());
            statement.setLong(2, valueId);
            statement.setLong(3, processInstanceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot expire packet values", exception);
        }
    }

    @Override
    public Optional<PacketValueRecord> getLatestUnexpiredValue(long processInstanceId) {
        return findOne(
            "select * from process_inst_data_packet_value "
                + "where proc_inst_id = ? and status <> ? order by id desc limit 1",
            statement -> {
                statement.setLong(1, processInstanceId);
                statement.setInt(2, PacketValueStatusEnum.EXPIRED.getCode());
            }
        );
    }

    @Override
    public Optional<PacketValueRecord> getPacketValue(long id) {
        if (id == 0) {
            return Optional.empty();
        }
        return findOne(
            "select * from process_inst_data_packet_value where id = ?",
            statement -> statement.setLong(1, id)
        );
    }

    @Override
    public List<PacketValueRecord> getPacketValueByProcInstId(long processInstanceId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from process_inst_data_packet_value "
                     + "where proc_inst_id = ? order by id"
             )) {
            statement.setLong(1, processInstanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PacketValueRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot list packet values", exception);
        }
    }

    @Override
    public Optional<PacketValueRecord> getPacketValue(
        long processInstanceId,
        long processTaskInstanceId
    ) {
        return findOne(
            "select * from process_inst_data_packet_value "
                + "where proc_inst_id = ? and proc_task_inst_id = ? "
                + "order by id desc limit 1",
            statement -> {
                statement.setLong(1, processInstanceId);
                statement.setLong(2, processTaskInstanceId);
            }
        );
    }

    @Override
    public long insertPacketValue(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode,
        String value,
        String source
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = JdbcSupport.insertStatement(
                 connection,
                 "insert into process_inst_data_packet_value("
                     + "proc_inst_id, proc_task_inst_id, task_code, init_value, "
                     + "`value`, init_source, source, status"
                     + ") values (?, ?, ?, ?, ?, ?, ?, ?)"
             )) {
            statement.setLong(1, processInstanceId);
            statement.setLong(2, processTaskInstanceId);
            statement.setString(3, taskCode);
            statement.setString(4, "");
            statement.setString(5, value);
            statement.setString(6, "");
            statement.setString(7, source);
            statement.setInt(8, PacketValueStatusEnum.COMPLETED.getCode());
            statement.executeUpdate();
            return JdbcSupport.generatedId(statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot insert packet value", exception);
        }
    }

    @Override
    public void updatePacketValue(long id, String value) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update process_inst_data_packet_value set `value` = ?, "
                     + "mtime = current_timestamp where id = ?"
             )) {
            statement.setString(1, value);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot update packet value", exception);
        }
    }

    @Override
    public void updatePacketValue(
        long processInstanceId,
        long processTaskInstanceId,
        String value
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update process_inst_data_packet_value set `value` = ?, "
                     + "mtime = current_timestamp "
                     + "where proc_inst_id = ? and proc_task_inst_id = ?"
             )) {
            statement.setString(1, value);
            statement.setLong(2, processInstanceId);
            statement.setLong(3, processTaskInstanceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot update packet value", exception);
        }
    }

    private Optional<PacketValueRecord> findOne(String sql, Binder binder) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot query packet value", exception);
        }
    }

    private static PacketValueRecord map(ResultSet resultSet) throws SQLException {
        return new PacketValueRecord(
            resultSet.getLong("id"),
            resultSet.getLong("proc_inst_id"),
            resultSet.getLong("proc_task_inst_id"),
            resultSet.getString("task_code"),
            resultSet.getString("init_value") == null ? "" : resultSet.getString("init_value"),
            resultSet.getString("value") == null ? "" : resultSet.getString("value"),
            resultSet.getString("init_source") == null
                ? ""
                : resultSet.getString("init_source"),
            resultSet.getString("source") == null ? "" : resultSet.getString("source"),
            PacketValueStatusEnum.valueByCode(resultSet.getInt("status")),
            JdbcSupport.instant(resultSet, "ctime"),
            JdbcSupport.instant(resultSet, "mtime")
        );
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
