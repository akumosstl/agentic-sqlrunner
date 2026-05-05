export class JdbcConnection {
  constructor(config) {
    this.config = config;
    this.client = null;
  }

  async connect() {
    const { default: pg } = await import("pg");
    const address = this.config.address || "localhost";
    const port = this.config.port || 5435;
    this.client = new pg.Client({
      host: address,
      port,
      user: this.config.user,
      password: this.config.password,
      database: this.config.database,
    });
    await this.client.connect();
  }

  async query(sql, params = []) {
    if (!this.client) await this.connect();
    return this.client.query(sql, params);
  }

  async end() {
    if (this.client) {
      await this.client.end();
      this.client = null;
    }
  }
}
