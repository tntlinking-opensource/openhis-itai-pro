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

function asDoc(raw) {
  return typeof raw === 'string' ? JSON.parse(raw) : raw;
}

function walk(node, fn) {
  if (!node || typeof node !== 'object') return;
  fn(node);
  if (Array.isArray(node.content)) {
    for (const c of node.content) walk(c, fn);
  }
}

function collectText(node) {
  let s = '';
  walk(node, (n) => {
    if (n.type === 'text') s += n.text ?? '';
    if (n.attrs?.label) s += String(n.attrs.label);
  });
  return s;
}

function isMrmsNode(node) {
  if (!node || typeof node !== 'object') return false;
  const name = String(node.attrs?.name ?? '');
  if (name === 'admissionDiagnosisStructured' || /MRMS|结构化/.test(name)) return true;
  const label = String(node.attrs?.label ?? '');
  if (/入院诊断结构化|MRMS/.test(label)) return true;
  const t = collectText(node);
  if (/入院诊断结构化|MRMS/.test(t)) return true;
  // freeText / dataElement 自身
  if (
    (node.type === 'emrFreeText' || node.type === 'emrDataElement') &&
    /入院诊断结构化|MRMS/.test(String(node.attrs?.label ?? '') + String(node.attrs?.placeholder ?? ''))
  ) {
    return true;
  }
  return false;
}

/** 去掉含 MRMS/入院诊断结构化 的顶层段落（及其空壳） */
function stripMrms(doc) {
  const next = structuredClone(doc);
  const before = (next.content || []).length;
  next.content = (next.content || []).filter((n) => !isMrmsNode(n));
  // 段内若夹带该控件也清掉
  walk(next, (n) => {
    if (!Array.isArray(n.content)) return;
    n.content = n.content.filter((c) => !isMrmsNode(c));
  });
  const after = (next.content || []).length;
  return { next, removed: before - after, changed: JSON.stringify(doc) !== JSON.stringify(next) };
}

await client.connect();
console.log('db', DB_NAME, APPLY ? 'APPLY' : 'DRY-RUN');

const tpls = await client.query(
  `SELECT id::text, name, content FROM emr_template
   WHERE name = '入院记录' OR name = '入院记录（标准）'`,
);
console.log('templates', tpls.rows.length);
let tplChanged = 0;
for (const row of tpls.rows) {
  const raw = asDoc(row.content);
  const { next, removed, changed } = stripMrms(raw);
  console.log((changed ? '* ' : '  ') + row.id, row.name, 'removedTop', removed);
  if (APPLY && changed) {
    await client.query(
      `UPDATE emr_template SET content = $1, updated_at = NOW() WHERE id = $2::bigint`,
      [JSON.stringify(next), row.id],
    );
    tplChanged += 1;
  }
}

const docs = await client.query(
  `SELECT id::text, title, content FROM emr_document
   WHERE title ILIKE '%入院记录%' OR document_type_code = 'ADMISSION_RECORD'`,
);
console.log('documents', docs.rows.length);
let docChanged = 0;
for (const row of docs.rows) {
  const raw = asDoc(row.content);
  const { next, changed } = stripMrms(raw);
  if (!changed) continue;
  docChanged += 1;
  console.log((APPLY ? '* ' : '  ') + row.id, row.title);
  if (APPLY) {
    await client.query(
      `UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`,
      [JSON.stringify(next), row.id],
    );
  }
}

console.log('tpl updates', APPLY ? tplChanged : '(dry-run)');
console.log('doc updates', APPLY ? docChanged : `(dry-run ${docChanged})`);
await client.end();
