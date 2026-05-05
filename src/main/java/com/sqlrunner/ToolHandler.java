package com.sqlrunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolHandler {

    private DbConfig activeConfig;

    public ToolHandler(DbConfig activeConfig) {
        this.activeConfig = activeConfig;
    }

    public void setActiveConfig(DbConfig config) {
        this.activeConfig = config;
    }

    public DbConfig getActiveConfig() {
        return activeConfig;
    }

    public ObjectNode handleCallTool(String toolName, JsonNode args) {
        try {
            DbConfig config = activeConfig;
            if (config == null) {
                return McpProtocol.buildToolResult(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode()
                        .add(McpProtocol.buildTextContent("Configuration error: No database configuration available")),
                    true
                );
            }

            return switch (toolName) {
                case "execute_query" -> handleExecuteQuery(config, args);
                case "list_tables" -> handleListTables(config, args);
                case "describe_table" -> handleDescribeTable(config, args);
                case "get_db_config" -> handleGetDbConfig(config);
                default -> McpProtocol.buildToolResult(
                    JsonNodeFactory.instance.arrayNode()
                        .add(McpProtocol.buildTextContent("Unknown tool: " + toolName)),
                    true
                );
            };
        } catch (Exception e) {
            System.err.println("[sql-runner] Error: " + e.getMessage());
            return McpProtocol.buildToolResult(
                JsonNodeFactory.instance.arrayNode()
                    .add(McpProtocol.buildTextContent("Database error: " + e.getMessage())),
                true
            );
        }
    }

    private ObjectNode handleExecuteQuery(DbConfig config, JsonNode args) throws Exception {
        JsonNode sqlNode = args.get("sql");
        if (sqlNode == null || sqlNode.asText().isEmpty()) {
            return McpProtocol.buildToolResult(
                JsonNodeFactory.instance.arrayNode()
                    .add(McpProtocol.buildTextContent("Error: 'sql' parameter is required.")),
                true
            );
        }

        String sql = sqlNode.asText();
        System.err.println("[sql-runner] Executing query: " + sql);

        List<String> params = new ArrayList<>();
        JsonNode paramsNode = args.get("params");
        if (paramsNode != null && paramsNode.isArray()) {
            for (JsonNode p : paramsNode) {
                params.add(p.asText());
            }
        }

        QueryResult result = QueryExecutor.executeQuery(config, sql, params);
        String formatted = ResultFormatter.format(result);

        return McpProtocol.buildToolResult(
            JsonNodeFactory.instance.arrayNode()
                .add(McpProtocol.buildTextContent(formatted)),
            false
        );
    }

    private ObjectNode handleListTables(DbConfig config, JsonNode args) throws Exception {
        String schema = "public";
        JsonNode schemaNode = args.get("schema");
        if (schemaNode != null && !schemaNode.asText().isEmpty()) {
            schema = schemaNode.asText();
        }

        String sql;
        List<String> queryParams = new ArrayList<>();

        if (config.isPgFamily()) {
            sql = "SELECT table_schema, table_name, table_type FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name";
            queryParams.add(schema);
        } else {
            sql = "SELECT table_schema AS table_schema, table_name, table_type FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name";
            queryParams.add(config.database());
        }

        QueryResult result = QueryExecutor.executeQuery(config, sql, queryParams);
        String formatted = ResultFormatter.format(result);

        return McpProtocol.buildToolResult(
            JsonNodeFactory.instance.arrayNode()
                .add(McpProtocol.buildTextContent(formatted)),
            false
        );
    }

    private ObjectNode handleDescribeTable(DbConfig config, JsonNode args) throws Exception {
        JsonNode tableNode = args.get("table");
        if (tableNode == null || tableNode.asText().isEmpty()) {
            return McpProtocol.buildToolResult(
                JsonNodeFactory.instance.arrayNode()
                    .add(McpProtocol.buildTextContent("Error: 'table' parameter is required.")),
                true
            );
        }

        String table = tableNode.asText();
        String schema = "public";
        JsonNode schemaNode = args.get("schema");
        if (schemaNode != null && !schemaNode.asText().isEmpty()) {
            schema = schemaNode.asText();
        }

        String sql;
        List<String> queryParams = new ArrayList<>();

        if (config.isPgFamily()) {
            sql = "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
            queryParams.add(schema);
            queryParams.add(table);
        } else {
            sql = "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
            queryParams.add(config.database());
            queryParams.add(table);
        }

        QueryResult result = QueryExecutor.executeQuery(config, sql, queryParams);
        String formatted = ResultFormatter.format(result);

        return McpProtocol.buildToolResult(
            JsonNodeFactory.instance.arrayNode()
                .add(McpProtocol.buildTextContent(formatted)),
            false
        );
    }

    private ObjectNode handleGetDbConfig(DbConfig config) {
        ObjectNode safeConfig = JsonNodeFactory.instance.objectNode();
        safeConfig.put("type", config.type());
        safeConfig.put("address", config.address());
        safeConfig.put("port", config.port());
        safeConfig.put("database", config.database());
        safeConfig.put("user", config.user());
        safeConfig.put("password", "********");
        safeConfig.put("ssl", config.getSslOrDefault());

        return McpProtocol.buildToolResult(
            JsonNodeFactory.instance.arrayNode()
                .add(McpProtocol.buildTextContent(McpProtocol.writeMessage(safeConfig))),
            false
        );
    }
}
