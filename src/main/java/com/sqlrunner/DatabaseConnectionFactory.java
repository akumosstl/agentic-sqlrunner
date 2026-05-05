package com.sqlrunner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnectionFactory {

    public static Connection createConnection(DbConfig config) throws SQLException {
        return switch (config.type().toLowerCase()) {
            case "postgres" -> createPostgresConnection(config);
            case "mysql" -> createMysqlConnection(config);
            case "mariadb" -> createMariadbConnection(config);
            case "h2" -> createH2Connection(config);
            default -> throw new SQLException(
                "Unsupported database type: " + config.type() + ". Supported types: postgres, mysql, mariadb, h2"
            );
        };
    }

    private static Connection createPostgresConnection(DbConfig config) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s", config.address(), config.port(), config.database());
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", config.user());
        props.setProperty("password", config.password());
        if (config.getSslOrDefault()) {
            props.setProperty("ssl", "true");
            props.setProperty("sslmode", "require");
        }
        return DriverManager.getConnection(url, props);
    }

    private static Connection createMysqlConnection(DbConfig config) throws SQLException {
        String url = String.format("jdbc:mysql://%s:%d/%s", config.address(), config.port(), config.database());
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", config.user());
        props.setProperty("password", config.password());
        if (config.getSslOrDefault()) {
            props.setProperty("useSSL", "true");
            props.setProperty("requireSSL", "true");
        } else {
            props.setProperty("useSSL", "false");
        }
        return DriverManager.getConnection(url, props);
    }

    private static Connection createMariadbConnection(DbConfig config) throws SQLException {
        String url = String.format("jdbc:mariadb://%s:%d/%s", config.address(), config.port(), config.database());
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", config.user());
        props.setProperty("password", config.password());
        if (config.getSslOrDefault()) {
            props.setProperty("useSSL", "true");
        } else {
            props.setProperty("useSSL", "false");
        }
        return DriverManager.getConnection(url, props);
    }

    private static Connection createH2Connection(DbConfig config) throws SQLException {
        String address = config.address() == null || config.address().isEmpty() ? "localhost" : config.address();
        int port = config.port() > 0 ? config.port() : 5435;
        String url = String.format("jdbc:h2:tcp://%s:%d/%s;MODE=PostgreSQL", address, port, config.database());
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", config.user());
        props.setProperty("password", config.password());
        return DriverManager.getConnection(url, props);
    }
}
