import pg from 'pg';

const encId = '352328663248670720';
const staffId = '1784931477878999';
const orgId = '1784931337878002';

const c = new pg.Client({
  host: '172.16.101.2',
  port: 5432,
  user: 'postgres',
  password: 'root',
  database: 'whale-training',
});
await c.connect();

const partCols = await c.query(
  `SELECT column_name FROM information_schema.columns WHERE table_name = 'adm_encounter_participant' ORDER BY ordinal_position`,
);
console.log('part cols', partCols.rows.map((r) => r.column_name));

const parts = await c.query(
  `SELECT * FROM adm_encounter_participant WHERE encounter_id = $1`,
  [encId],
);
console.log('parts for enc', parts.rows);

// All participants today for 内科
const todayParts = await c.query(
  `SELECT ep.*, e.status_enum, e.bus_no
   FROM adm_encounter_participant ep
   JOIN adm_encounter e ON e.id = ep.encounter_id
   WHERE e.organization_id = $1
     AND e.start_time::date = CURRENT_DATE
     AND e.is_deleted = false`,
  [orgId],
);
console.log('today parts', todayParts.rows);

// Check if admin has assignment to 内科
const assignTables = await c.query(`
  SELECT table_name FROM information_schema.tables
  WHERE table_schema='public'
    AND (table_name ILIKE '%practitioner%org%' OR table_name ILIKE '%position%' OR table_name ILIKE '%assignment%' OR table_name ILIKE '%prac%')
  ORDER BY 1 LIMIT 40
`);
console.log('assign tables', assignTables.rows.map((r) => r.table_name));

await c.end();
