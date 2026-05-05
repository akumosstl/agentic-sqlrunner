package com.sqlrunner;

import java.sql.*;
import java.util.*;

public class EnsureTestDatabase {

    private static final String[][] COMPANIES = {
        {"TechNova", "Tecnologia", "2015"},
        {"GreenLeaf", "Agronegócio", "2008"},
        {"BlueSky Logistics", "Logística", "2012"},
        {"MetalForge", "Siderurgia", "2001"},
        {"AquaPure", "Saneamento", "2019"},
    };

    private static final Object[][] EMPLOYEES = {
        {"Ana Silva", "Engenheira de Software", 12000.00, "TechNova"},
        {"Carlos Mendes", "Analista de Dados", 9500.00, "TechNova"},
        {"Fernanda Lima", "Agrônoma", 8700.00, "GreenLeaf"},
        {"Ricardo Souza", "Gerente de Operações", 11000.00, "BlueSky Logistics"},
        {"Patrícia Rocha", "Metalurgista", 7800.00, "MetalForge"},
        {"João Oliveira", "Técnico em Saneamento", 6500.00, "AquaPure"},
        {"Mariana Costa", "DevOps", 11500.00, "TechNova"},
    };

    public static DbConfig ensure(DbConfig config) throws SQLException {
        String testDbName = "test";
        boolean isAlreadyTest = testDbName.equals(config.database());
        DbConfig currentConfig = config;

        if (!isAlreadyTest) {
            log("Current database is \"" + config.database() + "\", creating/switching to \"" + testDbName + "\"...");

            if (config.isPgFamily()) {
                ensurePostgresDatabase(config, testDbName);
            } else if (config.isMyFamily()) {
                ensureMyDatabase(config, testDbName);
            }

            currentConfig = config.withDatabase(testDbName);
        }

        try (Connection conn = DatabaseConnectionFactory.createConnection(currentConfig)) {
            createTables(conn, currentConfig);
            seedIfEmpty(conn, currentConfig);
            log("Test database \"" + testDbName + "\" is ready with companies and employees tables");
        }

        return currentConfig;
    }

    private static void ensurePostgresDatabase(DbConfig config, String dbName) throws SQLException {
        DbConfig adminConfig = config.isH2()
            ? config.withDatabase(dbName)
            : new DbConfig("postgres", config.user(), config.password(), config.address(), config.port(), "postgres", config.ssl());

        try (Connection conn = DatabaseConnectionFactory.createConnection(adminConfig)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                ps.setString(1, dbName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.createStatement().executeUpdate("CREATE DATABASE " + dbName);
                        log("Database \"" + dbName + "\" created (postgres)");
                    }
                }
            }
        }
    }

    private static void ensureMyDatabase(DbConfig config, String dbName) throws SQLException {
        DbConfig adminConfig = new DbConfig(config.type(), config.user(), config.password(), config.address(), config.port(), null, config.ssl());
        String url = config.isMariadb()
            ? String.format("jdbc:mariadb://%s:%d", config.address(), config.port())
            : String.format("jdbc:mysql://%s:%d", config.address(), config.port());
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", config.user());
        props.setProperty("password", config.password());
        props.setProperty("useSSL", "false");

        try (Connection conn = DriverManager.getConnection(url, props)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT SCHEMA_NAME FROM information_schema.schemata WHERE SCHEMA_NAME = ?")) {
                ps.setString(1, dbName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.createStatement().executeUpdate("CREATE DATABASE `" + dbName + "`");
                        log("Database \"" + dbName + "\" created (mysql/mariadb)");
                    }
                }
            }
        }
    }

    private static void createTables(Connection conn, DbConfig config) throws SQLException {
        String autoPk = config.isPgFamily() ? "SERIAL PRIMARY KEY" : "INT AUTO_INCREMENT PRIMARY KEY";

        String createCompanies = "CREATE TABLE IF NOT EXISTS companies (" +
            "id " + autoPk + ", " +
            "name VARCHAR(255) NOT NULL, " +
            "industry VARCHAR(255), " +
            "founded_year INT)";

        String createEmployees = "CREATE TABLE IF NOT EXISTS employees (" +
            "id " + autoPk + ", " +
            "name VARCHAR(255) NOT NULL, " +
            "role VARCHAR(255), " +
            "salary DECIMAL(10,2), " +
            "company_id INT NOT NULL, " +
            "CONSTRAINT fk_company FOREIGN KEY (company_id) REFERENCES companies(id))";

        conn.createStatement().executeUpdate(createCompanies);
        conn.createStatement().executeUpdate(createEmployees);
    }

    private static void seedIfEmpty(Connection conn, DbConfig config) throws SQLException {
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS cnt FROM companies")) {
            if (rs.next() && rs.getInt("cnt") > 0) return;
        }

        log("Seeding test data...");

        String insertCompany = config.isPgFamily()
            ? "INSERT INTO companies (name, industry, founded_year) VALUES (?, ?, ?)"
            : "INSERT INTO companies (name, industry, founded_year) VALUES (?, ?, ?)";

        for (String[] company : COMPANIES) {
            try (PreparedStatement ps = conn.prepareStatement(insertCompany, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, company[0]);
                ps.setString(2, company[1]);
                ps.setInt(3, Integer.parseInt(company[2]));
                ps.executeUpdate();
            }
        }

        Map<String, Integer> companyIdMap = new LinkedHashMap<>();
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT id, name FROM companies")) {
            while (rs.next()) {
                companyIdMap.put(rs.getString("name"), rs.getInt("id"));
            }
        }

        String insertEmployee = "INSERT INTO employees (name, role, salary, company_id) VALUES (?, ?, ?, ?)";
        for (Object[] emp : EMPLOYEES) {
            try (PreparedStatement ps = conn.prepareStatement(insertEmployee)) {
                ps.setString(1, (String) emp[0]);
                ps.setString(2, (String) emp[1]);
                ps.setDouble(3, (Double) emp[2]);
                ps.setInt(4, companyIdMap.get((String) emp[3]));
                ps.executeUpdate();
            }
        }

        log("Seeded " + COMPANIES.length + " companies and " + EMPLOYEES.length + " employees");
    }

    private static void log(String msg) {
        System.err.println("[sql-runner] " + msg);
    }
}
