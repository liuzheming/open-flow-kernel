package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.core.process.ProcessInstanceDataInitRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JdbcProcessInstanceDataInitRepository
    implements ProcessInstanceDataInitRepository {
    private final DataSource dataSource;

    public JdbcProcessInstanceDataInitRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void init(long processDefinitionId, List<String> cityCodes, String key, String value) {
        if (cityCodes == null || cityCodes.isEmpty()) {
            return;
        }
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            for (String cityCode : cityCodes) {
                if (exists(connection, processDefinitionId, cityCode, key)) {
                    continue;
                }
                try (PreparedStatement statement = connection.prepareStatement(
                    "insert into process_inst_data_init_config(proc_def_id, city_code, `key`, `value`) "
                        + "values (?, ?, ?, ?)"
                )) {
                    statement.setLong(1, processDefinitionId);
                    statement.setString(2, cityCode);
                    statement.setString(3, key);
                    statement.setString(4, value);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot init process data config", exception);
        }
    }

    @Override
    public Map<String, String> get(long processDefinitionId, String cityCode) {
        try (Connection connection = JdbcSupport.connection(dataSource);
             PreparedStatement statement = connection.prepareStatement(
                 "select `key`, `value` from process_inst_data_init_config "
                     + "where proc_def_id = ? and city_code = ? order by id"
             )) {
            statement.setLong(1, processDefinitionId);
            statement.setString(2, cityCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, String> result = new LinkedHashMap<>();
                while (resultSet.next()) {
                    result.putIfAbsent(
                        resultSet.getString("key"),
                        resultSet.getString("value")
                    );
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot get process data config", exception);
        }
    }

    @Override
    public void delete(long processDefinitionId, List<String> cityCodes, List<String> keys) {
        if (cityCodes == null || keys == null) {
            return;
        }
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            for (String cityCode : cityCodes) {
                for (String key : keys) {
                    try (PreparedStatement statement = connection.prepareStatement(
                        "delete from process_inst_data_init_config "
                            + "where proc_def_id = ? and city_code = ? and `key` = ?"
                    )) {
                        statement.setLong(1, processDefinitionId);
                        statement.setString(2, cityCode);
                        statement.setString(3, key);
                        statement.executeUpdate();
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot delete process data config", exception);
        }
    }

    @Override
    public void update(long processDefinitionId, List<String> cityCodes, String key, String value) {
        if (cityCodes == null) {
            return;
        }
        try (Connection connection = JdbcSupport.connection(dataSource)) {
            for (String cityCode : cityCodes) {
                try (PreparedStatement statement = connection.prepareStatement(
                    "update process_inst_data_init_config set `value` = ? "
                        + "where proc_def_id = ? and city_code = ? and `key` = ?"
                )) {
                    statement.setString(1, value);
                    statement.setLong(2, processDefinitionId);
                    statement.setString(3, cityCode);
                    statement.setString(4, key);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot update process data config", exception);
        }
    }

    private static boolean exists(
        Connection connection,
        long processDefinitionId,
        String cityCode,
        String key
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "select id from process_inst_data_init_config "
                + "where proc_def_id = ? and city_code = ? and `key` = ?"
        )) {
            statement.setLong(1, processDefinitionId);
            statement.setString(2, cityCode);
            statement.setString(3, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
