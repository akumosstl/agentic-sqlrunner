package com.sqlrunner;

import java.sql.*;
import java.util.*;

public class QueryExecutor {

    public static QueryResult executeQuery(DbConfig config, String sql, List<String> params) throws SQLException {
        try (Connection conn = DatabaseConnectionFactory.createConnection(config)) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setString(i + 1, params.get(i));
                }

                boolean hasResultSet = stmt.execute();

                if (hasResultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        return mapResultSet(rs);
                    }
                } else {
                    int updateCount = stmt.getUpdateCount();
                    return new QueryResult(List.of(), updateCount >= 0 ? updateCount : 0, List.of());
                }
            }
        }
    }

    public static QueryResult executeQuery(DbConfig config, String sql) throws SQLException {
        return executeQuery(config, sql, List.of());
    }

    private static QueryResult mapResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                Object value = rs.getObject(i);
                row.put(columns.get(i - 1), value);
            }
            rows.add(row);
        }

        return new QueryResult(rows, rows.size(), columns);
    }
}
