import pg from 'pg';

const c = new pg.Client({
  host: '172.16.101.2',
  port: 5432,
  user: 'postgres',
  password: 'root',
  database: 'whale-training',
});
await c.connect();

const today = await c.query(`
  SELECT e.id, e.status_enum AS enc_status, e.organization_id, e.bus_no, e.class_enum,
         ep.role_code, ep.status_enum AS part_status, ep.practitioner_id
  FROM adm_encounter e
  LEFT JOIN adm_encounter_participant ep ON ep.encounter_id = e.id AND ep.is_deleted = false
  WHERE e.start_time::date = CURRENT_DATE
    AND e.is_deleted = false
    AND e.class_enum = 'AMB'
  ORDER BY e.id DESC
`);
console.log('today', today.rows);

// Fix participant status to ACTIVE for REGISTRATION_DOCTOR on today's encounters
const upd = await c.query(`
  UPDATE adm_encounter_participant
  SET status_enum = 'ACTIVE', updated_at = NOW()
  WHERE encounter_id IN (
    SELECT id FROM adm_encounter
    WHERE start_time::date = CURRENT_DATE AND is_deleted = false AND class_enum = 'AMB'
  )
  AND role_code = 'REGISTRATION_DOCTOR'
  AND status_enum <> 'ACTIVE'
  RETURNING id, encounter_id, status_enum, practitioner_id
`);
console.log('updated parts', upd.rows);

await c.end();
