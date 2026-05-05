package com.sqlrunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;

public class McpProtocol {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static int nextId = 1;

    public static JsonNode parseMessage(String line) {
        try {
            return MAPPER.readTree(line);
        } catch (Exception e) {
            return null;
        }
    }

    public static String writeMessage(Object node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static ObjectNode createResponse(Object id, JsonNode result) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id instanceof Number) {
            response.put("id", ((Number) id).longValue());
        } else if (id != null) {
            response.put("id", id.toString());
        }
        response.set("result", result);
        return response;
    }

    public static ObjectNode createErrorResponse(Object id, int code, String message) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id instanceof Number) {
            response.put("id", ((Number) id).longValue());
        } else if (id != null) {
            response.put("id", id.toString());
        }
        ObjectNode error = MAPPER.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return response;
    }

    public static ObjectNode createNotification(String method, JsonNode params) {
        ObjectNode notification = MAPPER.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", params);
        return notification;
    }

    public static ObjectNode buildInitializeResult(ObjectNode capabilities) {
        ObjectNode result = MAPPER.createObjectNode();
        ObjectNode serverInfo = MAPPER.createObjectNode();
        serverInfo.put("name", "sql-runner");
        serverInfo.put("version", "1.0.0");
        result.set("capabilities", capabilities);
        result.set("serverInfo", serverInfo);
        result.put("protocolVersion", "2024-11-05");
        return result;
    }

    public static ArrayNode buildToolsList() {
        ArrayNode tools = MAPPER.createArrayNode();

        tools.add(buildTool(
            "execute_query",
            "Execute a SQL query on the configured database. Use this tool to answer questions about data in the database by running SELECT queries. You can also run INSERT, UPDATE, DELETE and other DML/DDL statements. The database connection is configured in db-config.json. Supported databases: postgres, mysql, mariadb, h2.",
            Map.of(
                "sql", Map.of("type", "string", "description", "The SQL query to execute. Use parameterized values with $1, $2 etc. for postgres or ? for mysql when providing params."),
                "params", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Optional query parameters. Use for parameterized queries to prevent SQL injection.")
            ),
            List.of("sql")
        ));

        tools.add(buildTool(
            "list_tables",
            "List all tables in the configured database. Use this to discover what tables and schemas exist before writing queries.",
            Map.of(
                "schema", Map.of("type", "string", "description", "Optional schema name to filter tables (postgres/h2). Defaults to public for postgres.")
            ),
            List.of()
        ));

        tools.add(buildTool(
            "describe_table",
            "Get the column definitions (name, type, nullable, defaults) for a specific table. Use this before writing queries to understand the table structure.",
            Map.of(
                "table", Map.of("type", "string", "description", "The table name to describe."),
                "schema", Map.of("type", "string", "description", "Optional schema name (postgres/h2). Defaults to public for postgres.")
            ),
            List.of("table")
        ));

        tools.add(buildTool(
            "get_db_config",
            "Returns the current database configuration (type, address, port, database name). Passwords are masked for security.",
            Map.of(),
            List.of()
        ));

        return tools;
    }

    private static ObjectNode buildTool(String name, String description, Map<String, Object> properties, List<String> required) {
        ObjectNode tool = MAPPER.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);

        ObjectNode inputSchema = MAPPER.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode propsNode = MAPPER.createObjectNode();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                ObjectNode propNode = MAPPER.createObjectNode();
                for (Map.Entry<String, Object> propEntry : propDef.entrySet()) {
                    if (propEntry.getValue() instanceof String) {
                        propNode.put(propEntry.getKey(), (String) propEntry.getValue());
                    } else if (propEntry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nestedDef = (Map<String, Object>) propEntry.getValue();
                        ObjectNode nestedNode = MAPPER.createObjectNode();
                        for (Map.Entry<String, Object> ne : nestedDef.entrySet()) {
                            if (ne.getValue() instanceof String) {
                                nestedNode.put(ne.getKey(), (String) ne.getValue());
                            }
                        }
                        propNode.set(propEntry.getKey(), nestedNode);
                    }
                }
                propsNode.set(entry.getKey(), propNode);
            }
        }
        inputSchema.set("properties", propsNode);

        if (!required.isEmpty()) {
            ArrayNode requiredNode = MAPPER.createArrayNode();
            required.forEach(requiredNode::add);
            inputSchema.set("required", requiredNode);
        }

        tool.set("inputSchema", inputSchema);
        return tool;
    }

    public static ObjectNode buildTextContent(String text) {
        ObjectNode content = MAPPER.createObjectNode();
        content.put("type", "text");
        content.put("text", text);
        return content;
    }

    public static ObjectNode buildToolResult(ArrayNode content, boolean isError) {
        ObjectNode result = MAPPER.createObjectNode();
        result.set("content", content);
        if (isError) result.put("isError", true);
        return result;
    }
}
