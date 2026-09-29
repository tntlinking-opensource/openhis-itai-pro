/**
 * 刷「出院记录」模版 + 历史病历（DISCHARGE_SUMMARY）。
 * - 顶部有框 patientBar → 无框两列（复用已有 verifyBlock，不改渲染代码）
 * - 页边距 15mm、段落 1.5 倍行距
 *
 * 用法（apps/web-ele）：
 *   npx tsx src/emrEditor/migration/batch-normalize-discharge-summary-layout.mjs --db whale-pro
 *   npx tsx src/emrEditor/migration/batch-normalize-discharge-summary-layout.mjs --db whale-pro --apply
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

const MARGINS = { top: '15mm', right: '15mm', bottom: '15mm', left: '15mm' };
const KEEP_CENTER_NAMES = new Set(['hospitalTitle', 'formTitle']);

const client = new pg.Client({
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
  database: DB_NAME,
});

function asDoc(raw) {
  if (!raw) throw new Error('content 为空');
  if (typeof raw === 'string') return JSON.parse(raw);
  return raw;
}

function walk(node, fn) {
  if (!node || typeof node !== 'object') return;
  fn(node);
  if (Array.isArray(node.content)) {
    for (const child of node.content) walk(child, fn);
  }
}

/**
 * patientBar（有框头部表）→ verifyBlock（现有无框两列），保留科室/床号两列与元数据芯片。
 */
function normalizeDischarge(doc) {
  const next = structuredClone(doc);
  next.emrPageMargins = { ...MARGINS };

  walk(next, (node) => {
    if (node.type === 'table' && node.attrs?.name === 'patientBar') {
      node.attrs = { ...(node.attrs ?? {}), name: 'verifyBlock' };
      return;
    }
    if (node.type !== 'paragraph') return;
    const attrs = node.attrs ?? {};
    attrs.lineHeight = '1.5';
    const name = String(attrs.name ?? '');
    if (KEEP_CENTER_NAMES.has(name)) {
      attrs.textAlign = 'center';
    } else if (attrs.textAlign === 'right') {
      // keep
    } else if (!attrs.textAlign || attrs.textAlign === 'center') {
      attrs.textAlign = 'left';
    }
    node.attrs = attrs;
  });

  return next;
}

function sameJson(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}

function describeTopTables(doc) {
  const names = [];
  for (const n of doc.content || []) {
    if (n.type === 'table') names.push(n.attrs?.name ?? '(unnamed)');
  }
  return names.join(',') || '(none)';
}

await client.connect();
console.log('db', DB_NAME, APPLY ? 'APPLY' : 'DRY-RUN');

const tpls = await client.query(
  `SELECT id::text, name, document_type_code, content
   FROM emr_template
   WHERE document_type_code = 'DISCHARGE_SUMMARY'
   ORDER BY name`,
);
console.log('templates', tpls.rows.length);
let tplChanged = 0;
for (const row of tpls.rows) {
  const raw = asDoc(row.content);
  const next = normalizeDischarge(raw);
  const changed = !sameJson(raw, next);
  console.log(
    (changed ? '* ' : '  ') + row.id,
    row.name,
    raw.emrPageMargins?.left,
    '->',
    next.emrPageMargins.left,
    describeTopTables(raw),
    '=>',
    describeTopTables(next),
  );
  if (APPLY && changed) {
    await client.query(`UPDATE emr_template SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
      JSON.stringify(next),
      row.id,
    ]);
    tplChanged += 1;
  }
}

const docs = await client.query(
  `SELECT id::text, title, document_type_code, content
   FROM emr_document
   WHERE document_type_code = 'DISCHARGE_SUMMARY'
      OR title ILIKE '%出院记录%'
      OR title ILIKE '%出院小结%'`,
);
console.log('documents', docs.rows.length);
let docChanged = 0;
for (const row of docs.rows) {
  const raw = asDoc(row.content);
  const next = normalizeDischarge(raw);
  if (sameJson(raw, next)) continue;
  docChanged += 1;
  console.log(
    (APPLY ? '* ' : '  ') + row.id,
    row.title,
    raw.emrPageMargins?.left,
    '->',
    next.emrPageMargins.left,
    describeTopTables(raw),
    '=>',
    describeTopTables(next),
  );
  if (APPLY) {
    await client.query(`UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
      JSON.stringify(next),
      row.id,
    ]);
  }
}

console.log('tpl updates', APPLY ? tplChanged : '(dry-run)');
console.log('doc updates', APPLY ? docChanged : `(dry-run ${docChanged})`);

await client.end();
