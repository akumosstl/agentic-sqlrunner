const COMPANIES = [
  { name: "TechNova", industry: "Tecnologia", founded_year: 2015 },
  { name: "GreenLeaf", industry: "Agronegócio", founded_year: 2008 },
  { name: "BlueSky Logistics", industry: "Logística", founded_year: 2012 },
  { name: "MetalForge", industry: "Siderurgia", founded_year: 2001 },
  { name: "AquaPure", industry: "Saneamento", founded_year: 2019 },
];

const EMPLOYEES = [
  { name: "Ana Silva", role: "Engenheira de Software", salary: 12000.00, company_name: "TechNova" },
  { name: "Carlos Mendes", role: "Analista de Dados", salary: 9500.00, company_name: "TechNova" },
  { name: "Fernanda Lima", role: "Agrônoma", salary: 8700.00, company_name: "GreenLeaf" },
  { name: "Ricardo Souza", role: "Gerente de Operações", salary: 11000.00, company_name: "BlueSky Logistics" },
  { name: "Patrícia Rocha", role: "Metalurgista", salary: 7800.00, company_name: "MetalForge" },
  { name: "João Oliveira", role: "Técnico em Saneamento", salary: 6500.00, company_name: "AquaPure" },
  { name: "Mariana Costa", role: "DevOps", salary: 11500.00, company_name: "TechNova" },
];

function isPg(config) {
  return config.type === "postgres" || config.type === "h2";
}

function isMy(config) {
  return config.type === "mysql" || config.type === "mariadb";
}

const SER_AUTO = "SERIAL PRIMARY KEY";
const INT_AUTO = "INT AUTO_INCREMENT PRIMARY KEY";

function getCreateCompanies(config) {
  const auto = isPg(config) ? SER_AUTO : INT_AUTO;
  return `CREATE TABLE IF NOT EXISTS companies (
    id ${auto},
    name VARCHAR(255) NOT NULL,
    industry VARCHAR(255),
    founded_year INT
  )`;
}

function getCreateEmployees(config) {
  const auto = isPg(config) ? SER_AUTO : INT_AUTO;
  return `CREATE TABLE IF NOT EXISTS employees (
    id ${auto},
    name VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    salary DECIMAL(10,2),
    company_id INT NOT NULL,
    CONSTRAINT fk_company FOREIGN KEY (company_id) REFERENCES companies(id)
  )`;
}

export async function ensureTestDatabase(config) {
  const testDbName = "test";
  const isAlreadyTest = config.database === testDbName;
  let currentConfig = { ...config };

  if (!isAlreadyTest) {
    console.error(`[sql-runner] Current database is "${config.database}", creating/switching to "${testDbName}"...`);

    if (isPg(config)) {
      const pg = (await import("pg")).default;
      const admin = new pg.Client({
        host: config.address,
        port: config.port,
        user: config.user,
        password: config.password,
        database: "postgres",
        ssl: config.ssl ?? false,
      });
      await admin.connect();
      const { rows } = await admin.query(`SELECT 1 FROM pg_database WHERE datname = $1`, [testDbName]);
      if (rows.length === 0) {
        await admin.query(`CREATE DATABASE ${testDbName}`);
        console.error(`[sql-runner] Database "${testDbName}" created (postgres)`);
      }
      await admin.end();
    }

    if (isMy(config)) {
      const mysql = (await import("mysql2/promise")).default;
      const admin = await mysql.createConnection({
        host: config.address,
        port: config.port,
        user: config.user,
        password: config.password,
      });
      const [rows] = await admin.execute(`SELECT SCHEMA_NAME FROM information_schema.schemata WHERE SCHEMA_NAME = ?`, [testDbName]);
      if (rows.length === 0) {
        await admin.execute(`CREATE DATABASE \`${testDbName}\``);
        console.error(`[sql-runner] Database "${testDbName}" created (mysql/mariadb)`);
      }
      await admin.end();
    }

    currentConfig = { ...config, database: testDbName };
  }

  const connection = await (async () => {
    if (isPg(currentConfig)) {
      const pg = (await import("pg")).default;
      const client = new pg.Client({
        host: currentConfig.address,
        port: currentConfig.port,
        user: currentConfig.user,
        password: currentConfig.password,
        database: testDbName,
        ssl: currentConfig.ssl ?? false,
      });
      await client.connect();
      return client;
    }
    if (isMy(currentConfig)) {
      const mysql = (await import("mysql2/promise")).default;
      return await mysql.createConnection({
        host: currentConfig.address,
        port: currentConfig.port,
        user: currentConfig.user,
        password: currentConfig.password,
        database: testDbName,
      });
    }
    const { JdbcConnection } = await import("./h2-connection.js");
    const conn = new JdbcConnection({ ...currentConfig, database: testDbName });
    await conn.connect();
    return conn;
  })();

  try {
    await connection.query(getCreateCompanies(currentConfig));
    await connection.query(getCreateEmployees(currentConfig));

    if (isPg(currentConfig)) {
      const { rows } = await connection.query("SELECT COUNT(*) AS cnt FROM companies");
      if (Number(rows[0].cnt) === 0) await seedData(connection, currentConfig);
    } else if (isMy(currentConfig)) {
      const [rows] = await connection.execute("SELECT COUNT(*) AS cnt FROM companies");
      if (Number(rows[0].cnt) === 0) await seedData(connection, currentConfig);
    } else {
      const result = await connection.query("SELECT COUNT(*) AS cnt FROM companies");
      if (Number(result.rows?.[0]?.cnt ?? result[0]?.cnt ?? 0) === 0) {
        await seedData(connection, currentConfig);
      }
    }

    console.error(`[sql-runner] Test database "${testDbName}" is ready with companies and employees tables`);
  } finally {
    await connection.end();
  }

  return currentConfig;
}

async function seedData(connection, config) {
  console.error("[sql-runner] Seeding test data...");

  for (const c of COMPANIES) {
    if (isPg(config)) {
      await connection.query(
        `INSERT INTO companies (name, industry, founded_year) VALUES ($1, $2, $3)`,
        [c.name, c.industry, c.founded_year]
      );
    } else {
      await connection.execute(
        `INSERT INTO companies (name, industry, founded_year) VALUES (?, ?, ?)`,
        [c.name, c.industry, c.founded_year]
      );
    }
  }

  const companyIdMap = {};
  if (isPg(config)) {
    const { rows } = await connection.query("SELECT id, name FROM companies");
    for (const r of rows) companyIdMap[r.name] = r.id;
  } else {
    const [rows] = await connection.execute("SELECT id, name FROM companies");
    for (const r of rows) companyIdMap[r.name] = r.id;
  }

  for (const e of EMPLOYEES) {
    const companyId = companyIdMap[e.company_name];
    if (isPg(config)) {
      await connection.query(
        `INSERT INTO employees (name, role, salary, company_id) VALUES ($1, $2, $3, $4)`,
        [e.name, e.role, e.salary, companyId]
      );
    } else {
      await connection.execute(
        `INSERT INTO employees (name, role, salary, company_id) VALUES (?, ?, ?, ?)`,
        [e.name, e.role, e.salary, companyId]
      );
    }
  }

  console.error(`[sql-runner] Seeded ${COMPANIES.length} companies and ${EMPLOYEES.length} employees`);
}
