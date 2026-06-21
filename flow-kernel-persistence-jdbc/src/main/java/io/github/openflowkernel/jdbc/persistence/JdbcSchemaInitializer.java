package io.github.openflowkernel.jdbc.persistence;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class JdbcSchemaInitializer {
    private static final String SCHEMA = "/open-flow-kernel-jdbc-schema.sql";

    private final DataSource dataSource;

    public JdbcSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize() {
        String schema = readSchema();
        try (Connection connection = JdbcSupport.connection(dataSource);
             Statement statement = connection.createStatement()) {
            for (String sql : schema.split(";")) {
                String normalized = sql.trim();
                if (!normalized.isEmpty()) {
                    statement.execute(normalized);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot initialize JDBC schema", exception);
        }
    }

    private static String readSchema() {
        InputStream stream = JdbcSchemaInitializer.class.getResourceAsStream(SCHEMA);
        if (stream == null) {
            throw new IllegalStateException("Schema resource not found: " + SCHEMA);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            stream,
            StandardCharsets.UTF_8
        ))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read JDBC schema", exception);
        }
    }
}
