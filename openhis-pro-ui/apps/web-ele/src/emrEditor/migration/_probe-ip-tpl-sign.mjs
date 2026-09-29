import { createRequire } from 'node:module';

const require = createRequire(
  'file:///D:/workSpace/openhis-pro-ui/node_modules/.pnpm/pg@8.23.0/node_modules/pg/package.json',
);
const pg = require('pg');
const client = new pg.Client({
  host: '172.16.101.2',
  port: 5432,
  user: 'postgres',
  password: 'root',
  database: 'whale-pro',
});
await client.connect();

const b = await client.query(
  `SELECT name, document_type_code, encounter_class_code
   FROM emr_template
   WHERE encounter_class_code = 'IMP'
   ORDER BY document_type_code, name`,
);
console.log('IMP templates', b.rows.length);
for (const r of b.rows) {
  console.log(r.document_type_code, r.name);
}

const c = await client.query(
  `SELECT name, document_type_code
   FROM emr_template
   WHERE encounter_class_code = 'IMP'
     AND content::text LIKE '%"value":"admin"%'`,
);
console.log('IMP with admin value:', c.rows.length);
c.rows.forEach((r) => console.log(r.document_type_code, r.name));

// sample one sign chip
const one = await client.query(
  `SELECT name, content::text AS c FROM emr_template
   WHERE encounter_class_code = 'IMP' AND name LIKE '%入院%' LIMIT 1`,
);
if (one.rows[0]) {
  const doc = JSON.parse(one.rows[0].c);
  const hits = [];
  const walk = (n) => {
    if (!n || typeof n !== 'object') return;
    const a = n.attrs || {};
    if (
      (a.fieldKey === 'doctorName' || a.fieldKey === 'attendingPhysician' || /签名|签字/.test(String(a.label || ''))) &&
      String(a.value || '').trim()
    ) {
      hits.push({ fieldKey: a.fieldKey, label: a.label, value: String(a.value).slice(0, 30) });
    }
    if (Array.isArray(n.content)) n.content.forEach(walk);
  };
  walk(doc);
  console.log('sample', one.rows[0].name, 'nonempty sign-ish', hits.slice(0, 15));
}

await client.end();
