/**
 * training 库：入院记录 / 病程记录模版（及同类型文书侧车）改为 15mm 页边距 + 正文 1.5 倍行距。
 *
 * 用法（apps/web-ele）：
 *   npx tsx src/emrEditor/migration/batch-normalize-admission-progress-layout.mjs
 *   npx tsx src/emrEditor/migration/batch-normalize-admission-progress-layout.mjs --apply
 */
import { createRequire } from 'node:module';

const require = createRequire(
  'file:///D:/workSpace/openhis-pro-ui/node_modules/.pnpm/pg@8.23.0/node_modules/pg/package.json',
);
const pg = require('pg');

const APPLY = process.argv.includes('--apply');
const MARGINS = { top: '15mm', right: '15mm', bottom: '15mm', left: '15mm' };
const KEEP_CENTER_NAMES = new Set(['hospitalTitle', 'formTitle', 'historyTitle', 'peTitle', 'pageHeaderHint']);

const client = new pg.Client({
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
  database: 'whale-training',
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

/** 侧车 15mm；正文段落行距 1.5；非标题/非表头格改为左对齐。 */
function normalizeLayout(doc) {
  const next = structuredClone(doc);
  next.emrPageMargins = { ...MARGINS };
  walk(next, (node) => {
    if (node.type !== 'paragraph') return;
    const attrs = node.attrs ?? {};
    attrs.lineHeight = '1.5';
    const name = String(attrs.name ?? '');
    const inTableHeaderCell = name === '' && attrs.textAlign === 'center';
    if (KEEP_CENTER_NAMES.has(name)) {
      attrs.textAlign = 'center';
    } else if (attrs.textAlign === 'right') {
      // 签名行保持右对齐
    } else if (inTableHeaderCell) {
      // 相对浊音界等表头格保持居中
    } else if (attrs.textAlign === 'center') {
      attrs.textAlign = 'left';
    } else if (!attrs.textAlign) {
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
   WHERE name IN ('入院记录（标准）', '日常病程记录', '首次病程记录固定模版', '首次病程记录')
   ORDER BY name`,
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
    row.document_type_code,
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
  `SELECT id::text, title, document_type_code, content
   FROM emr_document
   WHERE document_type_code = 'ADMISSION_RECORD'
      OR title ILIKE '%病程记录%'`,
);
console.log('documents', docs.rows.length);
let docChanged = 0;
for (const row of docs.rows) {
  const raw = asDoc(row.content);
  const next = normalizeLayout(raw);
  if (sameJson(raw, next)) continue;
  docChanged += 1;
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
