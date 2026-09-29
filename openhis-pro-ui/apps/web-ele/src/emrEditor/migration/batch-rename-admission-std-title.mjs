/**
 * whale-pro：入院记录名头去「标准」
 * - 模版名「入院记录（标准）」→「入院记录」
 * - 历史文书 title 去掉（标准）/标准
 *
 * 用法：
 *   npx tsx src/emrEditor/migration/batch-rename-admission-std-title.mjs --db whale-pro --apply
 */
import { createRequire } from 'node:module';

const require = createRequire(
  'file:///D:/workSpace/openhis-pro-ui/node_modules/.pnpm/pg@8.23.0/node_modules/pg/package.json',
);
const pg = require('pg');

const APPLY = process.argv.includes('--apply');
const dbArgIdx = process.argv.indexOf('--db');
const DB_NAME =
  (dbArgIdx >= 0 ? process.argv[dbArgIdx + 1] : null) ||
  process.env.PRO_DB_NAME ||
  'whale-pro';

const client = new pg.Client({
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
  database: DB_NAME,
});

function cleanName(name) {
  return String(name ?? '')
    .replace(/[（(]\s*标准\s*[）)]/g, '')
    .replace(/标准$/u, '')
    .replace(/\s+/g, ' ')
    .trim();
}

await client.connect();
console.log('db', DB_NAME, APPLY ? 'APPLY' : 'DRY-RUN');

const tpls = await client.query(
  `SELECT id::text, name FROM emr_template
   WHERE name ILIKE '%入院记录%'`,
);
let tplChanged = 0;
for (const row of tpls.rows) {
  const next = cleanName(row.name);
  if (!next || next === row.name) {
    console.log('  tpl', row.id, row.name);
    continue;
  }
  console.log('* tpl', row.id, row.name, '->', next);
  if (APPLY) {
    await client.query(
      `UPDATE emr_template SET name = $1, updated_at = NOW() WHERE id = $2::bigint`,
      [next, row.id],
    );
    tplChanged += 1;
  }
}

const docs = await client.query(
  `SELECT id::text, title FROM emr_document
   WHERE title ILIKE '%入院记录%' OR document_type_code = 'ADMISSION_RECORD'`,
);
let docChanged = 0;
for (const row of docs.rows) {
  // 保留时间后缀：只清「入院记录（标准）」中的标准
  const m = String(row.title ?? '').match(/^(.*?)(-\d{4}-\d{2}-\d{2}.*)?$/);
  const head = cleanName(m?.[1] ?? row.title);
  const suffix = m?.[2] ?? '';
  const next = `${head}${suffix}`;
  if (!head || next === row.title) {
    continue;
  }
  console.log((APPLY ? '* ' : '  ') + 'doc', row.id, row.title, '->', next);
  docChanged += 1;
  if (APPLY) {
    await client.query(
      `UPDATE emr_document SET title = $1, updated_at = NOW() WHERE id = $2::bigint`,
      [next, row.id],
    );
  }
}

console.log('tpl updates', APPLY ? tplChanged : '(dry-run)');
console.log('doc updates', APPLY ? docChanged : `(dry-run ${docChanged})`);
await client.end();
