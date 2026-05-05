package com.sqlrunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SqlRunnerServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static ToolHandler toolHandler;

    public static void main(String[] args) {
        System.err.println("[sql-runner] Iniciando servidor MCP...");

        DbConfig startupConfig = loadConfig();
        DbConfig activeConfig;

        try {
            activeConfig = EnsureTestDatabase.ensure(startupConfig);
        } catch (Exception e) {
            System.err.println("[sql-runner] Could not ensure test database on startup: " + e.getMessage());
            System.err.println("[sql-runner] Server will start anyway. Tools will attempt to connect on each request.");
            activeConfig = startupConfig;
        }

        toolHandler = new ToolHandler(activeConfig);

        runServer();
    }

    private static DbConfig loadConfig() {
        String configPath = System.getenv("SQL_RUNNER_CONFIG");
        if (configPath == null || configPath.isEmpty()) {
            configPath = Paths.get("db-config.json").toAbsolutePath().toString();
        }

        if (!Files.exists(Paths.get(configPath))) {
            throw new RuntimeException("Configuration file not found: " + configPath);
        }

        try {
            String content = Files.readString(Paths.get(configPath), StandardCharsets.UTF_8);
            return MAPPER.readValue(content, DbConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load configuration: " + e.getMessage(), e);
        }
    }

    private static void runServer() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        OutputStream out = System.out;

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                try {
                    JsonNode message = McpProtocol.parseMessage(line);
                    if (message == null) continue;

                    JsonNode methodNode = message.get("method");
                    if (methodNode == null) continue;

                    String method = methodNode.asText();
                    JsonNode id = message.get("id");
                    JsonNode params = message.get("params");

                    JsonNode response = switch (method) {
                        case "initialize" -> handleInitialize(id, params);
                        case "notifications/initialized" -> null;
                        case "tools/list" -> handleToolsList(id, params);
                        case "tools/call" -> handleToolsCall(id, params);
                        case "ping" -> handlePing(id);
                        default -> null;
                    };

                    if (response != null) {
                        String responseStr = McpProtocol.writeMessage(response);
                        synchronized (out) {
                            out.write(responseStr.getBytes(StandardCharsets.UTF_8));
                            out.write('\n');
                            out.flush();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[sql-runner] Error processing message: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[sql-runner] Conexão fechada");
        }
    }

    private static JsonNode handleInitialize(JsonNode id, JsonNode params) {
        ObjectNode capabilities = MAPPER.createObjectNode();
        ObjectNode toolsCap = MAPPER.createObjectNode();
        capabilities.set("tools", toolsCap);

        ObjectNode result = McpProtocol.buildInitializeResult(capabilities);
        return McpProtocol.createResponse(id, result);
    }

    private static JsonNode handleToolsList(JsonNode id, JsonNode params) {
        ObjectNode result = MAPPER.createObjectNode();
        result.set("tools", McpProtocol.buildToolsList());
        return McpProtocol.createResponse(id, result);
    }

    private static JsonNode handleToolsCall(JsonNode id, JsonNode params) {
        if (params == null || !params.has("name")) {
            return McpProtocol.createErrorResponse(id, -32602, "Invalid params: missing tool name");
        }

        String toolName = params.get("name").asText();
        JsonNode args = params.has("arguments") ? params.get("arguments") : MAPPER.createObjectNode();

        DbConfig config = loadConfigSafe();
        if (config != null && toolHandler != null) {
            toolHandler.setActiveConfig(config);
        }

        ObjectNode toolResult = toolHandler.handleCallTool(toolName, args);
        return McpProtocol.createResponse(id, toolResult);
    }

    private static JsonNode handlePing(JsonNode id) {
        ObjectNode result = MAPPER.createObjectNode();
        return McpProtocol.createResponse(id, result);
    }

    private static DbConfig loadConfigSafe() {
        try {
            return loadConfig();
        } catch (Exception e) {
            return null;
        }
    }
}
