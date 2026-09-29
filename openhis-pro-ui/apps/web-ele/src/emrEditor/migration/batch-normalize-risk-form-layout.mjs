/**
 * training 库：住院病人风险评估表模版/文书改为 15mm 页边距。
 *
 * 用法（apps/web-ele）：
 *   npx tsx src/emrEditor/migration/batch-normalize-risk-form-layout.mjs
 *   npx tsx src/emrEditor/migration/batch-normalize-risk-form-layout.mjs --apply
 */
import { createRequire } from 'node:module';

const require = createRequire(
  'file:///D:/workSpace/openhis-pro-ui/node_modules/.pnpm/pg@8.23.0/node_modules/pg/package.json',
);
const pg = require('pg');

const APPLY = process.argv.includes('--apply');
const MARGINS = { top: '15mm', right: '15mm', bottom: '15mm', left: '15mm' };
const NAME = '住院病人风险评估表';

const client = new pg.Client({
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
  database: process.env.PRO_DB_NAME ?? 'whale-training',
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

/** 侧车 15mm；表内段落左对齐并标 1.5 倍行距（标题段保持居中）。 */
function normalizeLayout(doc) {
  const next = structuredClone(doc);
  next.emrPageMargins = { ...MARGINS };
  walk(next, (node) => {
    if (node.type !== 'paragraph') return;
    const attrs = node.attrs ?? {};
    attrs.lineHeight = '1.5';
    const name = String(attrs.name ?? '');
    if (name === 'hospitalTitle' || name === 'formTitle') {
      attrs.textAlign = 'center';
    } else if (attrs.textAlign === 'right') {
      // 保留右对齐
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

await client.connect();

const tpls = await client.query(
  `SELECT id::text, name, document_type_code, content
   FROM emr_template
   WHERE name = $1
   ORDER BY name`,
  [NAME],
);
console.log('templates', tpls.rows.length, APPLY ? 'APPLY' : 'DRY-RUN');
let tplChanged = 0;
for (const row of tpls.rows) {
  const raw = asDoc(row.content);
  const next = normalizeLayout(raw);
  const changed = !sameJson(raw, next);
  console.log(
    (changed ? '* ' : '  ') + row.id,
    row.name,
    raw.emrPageMargins?.left,
    '->',
    next.emrPageMargins.left,
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
  `SELECT id::text, title, content
   FROM emr_document
   WHERE title ILIKE $1`,
  [`%${NAME}%`],
);
console.log('documents', docs.rows.length);
let docChanged = 0;
for (const row of docs.rows) {
  const raw = asDoc(row.content);
  const next = normalizeLayout(raw);
  if (sameJson(raw, next)) continue;
  docChanged += 1;
  console.log((APPLY ? '* ' : '  ') + row.id, row.title, raw.emrPageMargins?.left, '->', next.emrPageMargins.left);
  if (APPLY) {
    await client.query(`UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
      JSON.stringify(next),
      row.id,
    ]);
  }
}
console.log('doc layout updates', APPLY ? docChanged : `(dry-run ${docChanged})`);
console.log('tpl updates', APPLY ? tplChanged : '(dry-run)');

await client.end();
