package com.sqlrunner;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DbConfig(
    String type,
    String user,
    String password,
    String address,
    int port,
    String database,
    Boolean ssl
) {
    public boolean isPostgres() {
        return "postgres".equalsIgnoreCase(type);
    }

    public boolean isMysql() {
        return "mysql".equalsIgnoreCase(type);
    }

    public boolean isMariadb() {
        return "mariadb".equalsIgnoreCase(type);
    }

    public boolean isH2() {
        return "h2".equalsIgnoreCase(type);
    }

    public boolean isPgFamily() {
        return isPostgres() || isH2();
    }

    public boolean isMyFamily() {
        return isMysql() || isMariadb();
    }

    public DbConfig withDatabase(String newDatabase) {
        return new DbConfig(type, user, password, address, port, newDatabase, ssl);
    }

    public boolean getSslOrDefault() {
        return ssl != null && ssl;
    }
}
