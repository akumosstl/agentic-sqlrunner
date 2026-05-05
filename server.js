import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import pg from "pg";
import mysql from "mysql2/promise";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { ensureTestDatabase } from "./ensure-test-db.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const CONFIG_PATH = process.env.SQL_RUNNER_CONFIG || path.join(__dirname, "db-config.json");

function loadConfig() {
  if (!fs.existsSync(CONFIG_PATH)) {
    throw new Error(`Configuration file not found: ${CONFIG_PATH}`);
  }
  return JSON.parse(fs.readFileSync(CONFIG_PATH, "utf-8"));
}

let activeConfig = null;

async function connectPostgres(config) {
  const client = new pg.Client({
    host: config.address,
    port: config.port,
    user: config.user,
    password: config.password,
    database: config.database,
    ssl: config.ssl ?? false,
  });
  await client.connect();
  return client;
}

async function connectMysql(config) {
  const connection = await mysql.createConnection({
    host: config.address,
    port: config.port,
    user: config.user,
    password: config.password,
    database: config.database,
    ssl: config.ssl ?? undefined,
  });
  return connection;
}

async function connectH2(config) {
  const { JdbcConnection } = await import("./h2-connection.js");
  return new JdbcConnection(config);
}

async function createConnection(config) {
  switch (config.type) {
    case "postgres":
      return await connectPostgres(config);
    case "mysql":
    case "mariadb":
      return await connectMysql(config);
    case "h2":
      return await connectH2(config);
    default:
      throw new Error(`Unsupported database type: ${config.type}. Supported types: postgres, mysql, mariadb, h2`);
  }
}

async function executeQuery(config, sql, params = []) {
  let connection;
  try {
    connection = await createConnection(config);
  if (config.type === "postgres" || config.type === "h2") {
    const result = await connection.query(sql, params);
    return {
      rows: result.rows,
      rowCount: result.rowCount ?? result.rows?.length ?? 0,
      columns: result.fields ? result.fields.map((f) => f.name) : result.columns || [],
    };
  }
  if (config.type === "mysql" || config.type === "mariadb") {
      const [rows, fields] = await connection.execute(sql, params);
      return {
        rows: Array.isArray(rows) ? rows : [rows],
        rowCount: Array.isArray(rows) ? rows.length : 1,
        columns: fields ? fields.map((f) => f.name) : [],
      };
    }
  } finally {
    if (connection) {
      try {
        await connection.end();
      } catch {}
    }
  }
}

function formatResults(result) {
  const { rows, rowCount, columns } = result;
  if (!rows || rows.length === 0) {
    return "Query executed successfully. No rows returned.";
  }

  let output = "";
  if (columns && columns.length > 0) {
    output += columns.join(" | ") + "\n";
    output += columns.map(() => "---").join(" | ") + "\n";
  }
  for (const row of rows) {
    const values = columns && columns.length > 0
      ? columns.map((col) => String(row[col] ?? "NULL"))
      : Object.values(row).map((v) => String(v ?? "NULL"));
    output += values.join(" | ") + "\n";
  }
  output += `\n${rowCount} row(s) returned.`;
  return output;
}

const server = new Server(
  {
    name: "sql-runner",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "execute_query",
        description:
          "Execute a SQL query on the configured database. Use this tool to answer questions about data in the database by running SELECT queries. You can also run INSERT, UPDATE, DELETE and other DML/DDL statements. The database connection is configured in db-config.json. Supported databases: postgres, mysql, mariadb, h2.",
        inputSchema: {
          type: "object",
          properties: {
            sql: {
              type: "string",
              description: "The SQL query to execute. Use parameterized values with $1, $2 etc. for postgres or ? for mysql when providing params.",
            },
            params: {
              type: "array",
              items: { type: "string" },
              description: "Optional query parameters. Use for parameterized queries to prevent SQL injection.",
            },
          },
          required: ["sql"],
        },
      },
      {
        name: "list_tables",
        description:
          "List all tables in the configured database. Use this to discover what tables and schemas exist before writing queries.",
        inputSchema: {
          type: "object",
          properties: {
            schema: {
              type: "string",
              description: "Optional schema name to filter tables (postgres/h2). Defaults to public for postgres.",
            },
          },
        },
      },
      {
        name: "describe_table",
        description:
          "Get the column definitions (name, type, nullable, defaults) for a specific table. Use this before writing queries to understand the table structure.",
        inputSchema: {
          type: "object",
          properties: {
            table: {
              type: "string",
              description: "The table name to describe.",
            },
            schema: {
              type: "string",
              description: "Optional schema name (postgres/h2). Defaults to public for postgres.",
            },
          },
          required: ["table"],
        },
      },
      {
        name: "get_db_config",
        description:
          "Returns the current database configuration (type, address, port, database name). Passwords are masked for security.",
        inputSchema: {
          type: "object",
          properties: {},
        },
      },
    ],
  };
});

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  let config;
  try {
    config = loadConfig();
  } catch (error) {
    return {
      content: [{ type: "text", text: `Configuration error: ${error.message}` }],
      isError: true,
    };
  }

  const resolvedConfig = activeConfig || config;

  try {
    switch (name) {
      case "execute_query": {
        const { sql, params } = args;
        if (!sql) {
          return {
            content: [{ type: "text", text: "Error: 'sql' parameter is required." }],
            isError: true,
          };
        }
            console.error(`[sql-runner] Executing query: ${sql}`);
            const result = await executeQuery(resolvedConfig, sql, params || []);
        const formatted = formatResults(result);
        return {
          content: [{ type: "text", text: formatted }],
        };
      }

      case "list_tables": {
        const schema = args.schema || "public";
        let sql;
        let queryParams = [];

        switch (resolvedConfig.type) {
          case "postgres":
            sql = `SELECT table_schema, table_name, table_type FROM information_schema.tables WHERE table_schema = $1 ORDER BY table_name`;
            queryParams = [schema];
            break;
          case "mysql":
          case "mariadb":
            sql = `SELECT table_schema AS table_schema, table_name, table_type FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name`;
            queryParams = [resolvedConfig.database];
            break;
          case "h2":
            sql = `SELECT table_schema, table_name, table_type FROM information_schema.tables WHERE table_schema = $1 ORDER BY table_name`;
            queryParams = [schema];
            break;
          default:
            throw new Error(`Unsupported database type: ${resolvedConfig.type}`);
        }

        const result = await executeQuery(resolvedConfig, sql, queryParams);
        const formatted = formatResults(result);
        return {
          content: [{ type: "text", text: formatted }],
        };
      }

      case "describe_table": {
        const { table, schema: rawSchema } = args;
        const schema = rawSchema || "public";
        let sql;
        let queryParams = [];

        switch (resolvedConfig.type) {
          case "postgres":
            sql = `SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2 ORDER BY ordinal_position`;
            queryParams = [schema, table];
            break;
          case "mysql":
          case "mariadb":
            sql = `SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position`;
            queryParams = [resolvedConfig.database, table];
            break;
          case "h2":
            sql = `SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2 ORDER BY ordinal_position`;
            queryParams = [schema, table];
            break;
          default:
            throw new Error(`Unsupported database type: ${resolvedConfig.type}`);
        }

        const result = await executeQuery(resolvedConfig, sql, queryParams);
        const formatted = formatResults(result);
        return {
          content: [{ type: "text", text: formatted }],
        };
      }

      case "get_db_config": {
        const safeConfig = {
          type: resolvedConfig.type,
          address: resolvedConfig.address,
          port: resolvedConfig.port,
          database: resolvedConfig.database,
          user: resolvedConfig.user,
          password: "********",
          ssl: resolvedConfig.ssl ?? false,
        };
        return {
          content: [{ type: "text", text: JSON.stringify(safeConfig, null, 2) }],
        };
      }

      default:
        return {
          content: [{ type: "text", text: `Unknown tool: ${name}` }],
          isError: true,
        };
    }
  } catch (error) {
    console.error(`[sql-runner] Error: ${error.message}`);
    return {
      content: [{ type: "text", text: `Database error: ${error.message}` }],
      isError: true,
    };
  }
});

console.error("[sql-runner] Iniciando servidor MCP...");

const startupConfig = loadConfig();
try {
  activeConfig = await ensureTestDatabase(startupConfig);
} catch (error) {
  console.error(`[sql-runner] Could not ensure test database on startup: ${error.message}`);
  console.error("[sql-runner] Server will start anyway. Tools will attempt to connect on each request.");
  activeConfig = startupConfig;
}

const transport = new StdioServerTransport();

transport.onclose = () => {
  console.error("[sql-runner] Conexão fechada");
};

transport.onerror = (error) => {
  console.error(`[sql-runner] Erro no transporte: ${error.message}`);
};

await server.connect(transport);
console.error("[sql-runner] Servidor MCP sql-runner rodando e aguardando requisições!");
