# sql-runner — MCP Server

MCP server that connects to SQL databases and executes queries from natural language prompts via OpenCode CLI.

Built with **Java 21+**, **Maven**, and **GraalVM** for native `.exe` compilation.

## How It Works

When you ask a question in OpenCode referencing `@sql-runner`, the LLM translates your question into a SQL query and calls the appropriate tool on the MCP server. The server connects to the configured database, executes the query, and returns the results.

Example: `@sql-runner Quantos empregados temos na empresa de nome XX?` → the LLM calls `execute_query` with `SELECT COUNT(*) FROM employees WHERE company_name = 'XX'`.

## Supported Databases

| Type | Driver | Notes |
|------|--------|-------|
| **PostgreSQL** | `postgresql` | Full support via JDBC |
| **MySQL** | `mysql-connector-j` | Full support via JDBC |
| **MariaDB** | `mariadb-java-client` | Full support via JDBC |
| **H2** | `h2` | Uses H2 native JDBC (PostgreSQL compatibility mode) |

## MCP Tools

| Tool | Description |
|------|-------------|
| `execute_query` | Execute any SQL statement (SELECT, INSERT, UPDATE, DELETE, DDL) |
| `list_tables` | List all tables in the database |
| `describe_table` | Get column definitions for a table (name, type, nullable, default) |
| `get_db_config` | Return current database config (passwords masked) |

## Prerequisites

- **Java 21+** (or higher) — [Adoptium / Eclipse Temurin](https://adoptium.net/)
- **Maven 3.9+** — [maven.apache.org](https://maven.apache.org/)
- **GraalVM JDK 21+** — required only for native `.exe` compilation — [graalvm.org](https://www.graalvm.org/)

## Setup

### 1. Configure Database Connection

Copy the example config and edit with your database credentials:

```bash
copy db-config.example.json db-config.json
```

`db-config.json`:

```json
{
  "type": "postgres",
  "user": "postgres",
  "password": "your-password",
  "address": "localhost",
  "port": 5432,
  "database": "mydb",
  "ssl": false
}
```

For MySQL:

```json
{
  "type": "mysql",
  "user": "root",
  "password": "your-password",
  "address": "localhost",
  "port": 3306,
  "database": "mydb",
  "ssl": false
}
```

For MariaDB:

```json
{
  "type": "mariadb",
  "user": "root",
  "password": "your-password",
  "address": "localhost",
  "port": 3306,
  "database": "mydb",
  "ssl": false
}
```

For H2 (PostgreSQL compatibility mode):

```json
{
  "type": "h2",
  "user": "sa",
  "password": "",
  "address": "localhost",
  "port": 5435,
  "database": "test",
  "ssl": false
}
```

> **Important**: Never commit `db-config.json` with real credentials. It is already excluded via `.gitignore`.

### 2. Configure OpenCode

`opencode.json` (already included):

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "sql-runner": {
      "type": "local",
      "command": ["sql-runner.exe"],
      "enabled": true
    }
  }
}
```

> If not using the native `.exe`, replace `"sql-runner.exe"` with `["java", "-jar", "target/sql-runner.jar"]`.

## Compilation

### Option A: JAR (platform-independent)

```bash
mvn clean package
```

Output: `target/sql-runner.jar`

Run:

```bash
java -jar target/sql-runner.jar
```

### Option B: Native `.exe` (GraalVM)

Make sure `GRAALVM_HOME` is set and `native-image` is available:

```bash
# Verify GraalVM is installed
native-image --version

# Build fat JAR first
mvn clean package -DskipTests

# Build native executable
mvn native:compile-no-fork
```

Output: `target/sql-runner.exe`

Run:

```bash
.\target\sql-runner.exe
```

> **Note**: On Windows, you may need to install Visual Studio Build Tools with C++ workload for `native-image` to work. See [GraalVM Windows Requirements](https://www.graalvm.org/latest/docs/getting-started/windows/).

## Running the Server

### With JAR

```bash
java -jar target/sql-runner.jar
```

### With Native Executable

```bash
.\target\sql-runner.exe
```

You should see: `[sql-runner] Servidor MCP sql-runner rodando e aguardando requisições!`

### Use with OpenCode

```bash
opencode
```

Then ask questions:

- `@sql-runner Quantos empregados temos na empresa de nome Acme?`
- `@sql-runner Liste todas as tabelas do banco`
- `@sql-runner Descreva a estrutura da tabela employees`
- `@sql-runner Qual a configuração atual do banco?`

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SQL_RUNNER_CONFIG` | Path to database config JSON file | `./db-config.json` |

## Project Structure

```
├── pom.xml                                    # Maven project definition
├── db-config.json                             # Database connection config (DO NOT COMMIT)
├── db-config.example.json                     # Config template
├── opencode.json                              # OpenCode MCP integration config
├── docker-compose.yml                         # PostgreSQL Docker container
├── src/main/java/com/sqlrunner/
│   ├── SqlRunnerServer.java                   # MCP server entry point (stdio transport)
│   ├── McpProtocol.java                       # MCP JSON-RPC protocol handling
│   ├── ToolHandler.java                       # MCP tool call dispatch & execution
│   ├── DbConfig.java                          # Database configuration model (record)
│   ├── DatabaseConnectionFactory.java         # JDBC connection factory (PG, MySQL, MariaDB, H2)
│   ├── QueryExecutor.java                     # SQL query execution via JDBC
│   ├── QueryResult.java                       # Query result model (record)
│   ├── ResultFormatter.java                   # Markdown table result formatting
│   └── EnsureTestDatabase.java                # Test database creation & seed data
├── src/main/resources/META-INF/native-image/
│   ├── reflect-config.json                    # GraalVM reflection config
│   ├── resource-config.json                   # GraalVM resource config
│   └── native-image.properties                # GraalVM native image properties
└── README.md
```

## Docker

Start a PostgreSQL database for testing:

```bash
docker compose up -d
```

This starts a PostgreSQL 17 container on port 5432 with user `postgres`, password `CHANGEME`, and database `mydb`.

## Troubleshooting

**Build fails with "invalid source release":**
- Ensure Java 21+ is installed and `JAVA_HOME` points to it
- Run `java -version` to verify

**MCP server not starting:**
- Run `java -jar target/sql-runner.jar` directly and check for errors
- Verify `db-config.json` exists and is valid JSON

**Database connection errors:**
- Verify credentials in `db-config.json`
- Ensure the database server is running and accessible
- For H2: ensure H2 is started with TCP mode (`java -cp h2.jar org.h2.tools.Server -tcp`)

**Native image build fails:**
- Ensure GraalVM is installed and `GRAALVM_HOME` is set
- On Windows: install Visual Studio Build Tools with C++ workload
- Run `native-image --version` to verify

**Tool not found in OpenCode:**
- Verify `opencode.json` points to the correct executable path
- Restart OpenCode after config changes
