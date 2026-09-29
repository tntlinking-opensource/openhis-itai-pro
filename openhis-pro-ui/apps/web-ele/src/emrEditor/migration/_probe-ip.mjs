import pg from 'pg';

const c = new pg.Client({
  host: '172.16.101.2',
  port: 5432,
  user: 'postgres',
  password: 'root',
  database: 'whale-training',
});
await c.connect();

const orgs = await c.query(`
  SELECT id, name, type_enum, bus_no
  FROM adm_organization
  WHERE is_deleted = false
    AND (name ILIKE '%住院%' OR name ILIKE '%病区%' OR name ILIKE '%内科%' OR type_enum::text ILIKE '%WARD%' OR type_enum::text ILIKE '%DEPT%')
  ORDER BY name
  LIMIT 40
`);
console.log('orgs', orgs.rows);

const locs = await c.query(`
  SELECT table_name FROM information_schema.tables
  WHERE table_schema='public' AND (table_name ILIKE '%location%' OR table_name ILIKE '%bed%' OR table_name ILIKE '%ward%')
  ORDER BY 1 LIMIT 40
`);
console.log('loc tables', locs.rows.map((r) => r.table_name));

const imp = await c.query(`
  SELECT id, status_enum, organization_id, patient_id, bus_no, class_enum, start_time
  FROM adm_encounter
  WHERE class_enum = 'IMP' AND is_deleted = false
  ORDER BY id DESC LIMIT 10
`);
console.log('existing IMP', imp.rows);

await c.end();
