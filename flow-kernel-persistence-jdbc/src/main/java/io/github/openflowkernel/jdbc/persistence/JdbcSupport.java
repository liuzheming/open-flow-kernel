package io.github.openflowkernel.jdbc.persistence;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class JdbcSupport {
    private JdbcSupport() {
    }

    static Connection connection(DataSource dataSource) {
        try {
            return dataSource.getConnection();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot get JDBC connection", exception);
        }
    }

    static long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new IllegalStateException("No generated key returned");
            }
            return keys.getLong(1);
        }
    }

    static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    static Map<String, String> loadData(
        Connection connection,
        String sql,
        long ownerId
    ) throws SQLException {
        Map<String, String> data = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    data.put(resultSet.getString("data_key"), resultSet.getString("data_value"));
                }
            }
        }
        return data;
    }

    static void upsertData(
        Connection connection,
        String updateSql,
        String insertSql,
        long ownerId,
        Map<String, String> data
    ) throws SQLException {
        for (Map.Entry<String, String> entry : data.entrySet()) {
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setString(1, entry.getValue());
                update.setLong(2, ownerId);
                update.setString(3, entry.getKey());
                int updated = update.executeUpdate();
                if (updated > 0) {
                    continue;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setLong(1, ownerId);
                insert.setString(2, entry.getKey());
                insert.setString(3, entry.getValue());
                insert.executeUpdate();
            }
        }
    }

    static PreparedStatement insertStatement(Connection connection, String sql)
        throws SQLException {
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }
}
