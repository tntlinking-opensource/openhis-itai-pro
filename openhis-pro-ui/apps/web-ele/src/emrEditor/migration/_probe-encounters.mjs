import pg from 'pg';

const c = new pg.Client({
  host: '172.16.101.2',
  port: 5432,
  user: 'postgres',
  password: 'root',
  database: 'whale-app',
});
await c.connect();

const tables = await c.query(`
  SELECT table_name
  FROM information_schema.tables
  WHERE table_schema = 'public'
    AND (
      table_name ILIKE '%encounter%'
      OR table_name ILIKE 'enc_%'
      OR table_name ILIKE '%patient%'
    )
  ORDER BY 1
`);
console.log('tables', tables.rows.map((r) => r.table_name));

const candidates = tables.rows
  .map((r) => r.table_name)
  .filter((n) => /encounter/i.test(n) && !/history|audit|link/i.test(n));

for (const t of candidates.slice(0, 8)) {
  const cols = await c.query(
    `SELECT column_name FROM information_schema.columns WHERE table_name = $1 ORDER BY ordinal_position`,
    [t],
  );
  console.log('\\n==', t, 'cols', cols.rows.map((r) => r.column_name).slice(0, 30));
  try {
    const rows = await c.query(
      `SELECT * FROM ${t} WHERE id::text IN ('352328663248670720','352328560324644864') OR cast(id as text) LIKE '352328%' ORDER BY id DESC LIMIT 5`,
    );
    console.log('rows', JSON.stringify(rows.rows, null, 2).slice(0, 2000));
  } catch (e) {
    console.log('query fail', e.message);
  }
}

await c.end();
