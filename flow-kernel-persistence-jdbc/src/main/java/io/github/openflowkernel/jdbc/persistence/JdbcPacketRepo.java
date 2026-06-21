package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.packet.PacketRecord;
import io.github.openflowkernel.packet.PacketStatusEnum;
import io.github.openflowkernel.packet.PacketValueRecord;
import io.github.openflowkernel.packet.repo.PacketDataRepo;
import io.github.openflowkernel.packet.repo.PacketRepo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class JdbcPacketRepo implements PacketRepo {
    private final DataSource dataSource;
    private final PacketDataRepo packetDataRepo;

    public JdbcPacketRepo(DataSource dataSource, PacketDataRepo packetDataRepo) {
        this.dataSource = dataSource;
        this.packetDataRepo = packetDataRepo;
    }

    @Override
    public Optional<PacketRecord> getPacket(long processInstanceId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select * from process_inst_data_packet where proc_inst_id = ?"
             )) {
            statement.setLong(1, processInstanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot query packet", exception);
        }
    }

    @Override
    public PacketRecord insert(long processInstanceId) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = JdbcSupport.insertStatement(
                 connection,
                 "insert into process_inst_data_packet("
                     + "proc_inst_id, data_packet_value_id, status"
                     + ") values (?, ?, ?)"
             )) {
            statement.setLong(1, processInstanceId);
            statement.setLong(2, 0);
            statement.setInt(3, PacketStatusEnum.IN_PROGRESS.getCode());
            statement.executeUpdate();
            return getPacket(processInstanceId).orElseThrow();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot insert packet", exception);
        }
    }

    @Override
    public void expire(long processInstanceId, String taskCode, long oldPacketValueId) {
        Optional<PacketValueRecord> latest =
            packetDataRepo.getLatestFirstUnexpiredCommit(processInstanceId, taskCode);
        if (latest.isEmpty()) {
            return;
        }
        packetDataRepo.expireFromValueId(processInstanceId, latest.get().id());
        long targetValueId = packetDataRepo.getLatestUnexpiredValue(processInstanceId)
            .map(PacketValueRecord::id)
            .orElse(0L);
        updatePointer(processInstanceId, oldPacketValueId, targetValueId, "任务并行变更");
    }

    @Override
    public long commit(
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode,
        String value,
        String source,
        long oldPacketValueId
    ) {
        long newValueId = packetDataRepo.insertPacketValue(
            processInstanceId,
            processTaskInstanceId,
            taskCode,
            value,
            source
        );
        updatePointer(processInstanceId, oldPacketValueId, newValueId, "任务并行提交");
        return newValueId;
    }

    @Override
    public boolean updatePacketStatus(
        long processInstanceId,
        PacketStatusEnum beforeStatus,
        PacketStatusEnum afterStatus
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update process_inst_data_packet set status = ?, "
                     + "mtime = current_timestamp where proc_inst_id = ? and status = ?"
             )) {
            statement.setInt(1, afterStatus.getCode());
            statement.setLong(2, processInstanceId);
            statement.setInt(3, beforeStatus.getCode());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot update packet status", exception);
        }
    }

    private void updatePointer(
        long processInstanceId,
        long oldPacketValueId,
        long targetValueId,
        String message
    ) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "update process_inst_data_packet set data_packet_value_id = ?, "
                     + "mtime = current_timestamp "
                     + "where proc_inst_id = ? and data_packet_value_id = ?"
             )) {
            statement.setLong(1, targetValueId);
            statement.setLong(2, processInstanceId);
            statement.setLong(3, oldPacketValueId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalStateException(
                    message + ", procInstId=" + processInstanceId
                        + ", oldValueId=" + oldPacketValueId
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot update packet pointer", exception);
        }
    }

    private static PacketRecord map(ResultSet resultSet) throws SQLException {
        return new PacketRecord(
            resultSet.getLong("id"),
            resultSet.getLong("proc_inst_id"),
            resultSet.getLong("data_packet_value_id"),
            PacketStatusEnum.valueByCode(resultSet.getInt("status")),
            JdbcSupport.instant(resultSet, "ctime"),
            JdbcSupport.instant(resultSet, "mtime")
        );
    }
}
