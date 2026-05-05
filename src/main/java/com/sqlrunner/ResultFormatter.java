package com.sqlrunner;

import java.util.List;
import java.util.Map;

public class ResultFormatter {

    public static String format(QueryResult result) {
        if (result.rows().isEmpty()) {
            return "Query executed successfully. No rows returned.";
        }

        StringBuilder sb = new StringBuilder();
        List<String> columns = result.columns();

        if (columns != null && !columns.isEmpty()) {
            sb.append(String.join(" | ", columns)).append("\n");
            sb.append(String.join(" | ", columns.stream().map(c -> "---").toList())).append("\n");
        }

        for (Map<String, Object> row : result.rows()) {
            List<String> values;
            if (columns != null && !columns.isEmpty()) {
                values = columns.stream()
                    .map(col -> String.valueOf(row.getOrDefault(col, "NULL")))
                    .toList();
            } else {
                values = row.values().stream()
                    .map(v -> String.valueOf(v == null ? "NULL" : v))
                    .toList();
            }
            sb.append(String.join(" | ", values)).append("\n");
        }

        sb.append("\n").append(result.rowCount()).append(" row(s) returned.");
        return sb.toString();
    }
}
