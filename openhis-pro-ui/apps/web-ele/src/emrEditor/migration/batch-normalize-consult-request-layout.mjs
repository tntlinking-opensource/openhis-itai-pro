/**
 * whale-pro：会诊申请单模版 + 历史病历
 * - 页眉：保留院名+文书标题，去掉患者信息行（姓名/病房/床位/住院号）
 * - 科主任后：年月日时分可填；末行：科室 + 会诊医师 + 年月日时分
 * - 页边距 15mm、段落 1.5
 *
 * 用法：
 *   npx tsx src/emrEditor/migration/batch-normalize-consult-request-layout.mjs --db whale-pro --apply
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
const TEMPLATE_NAME = '会诊申请单';
/** 院名+标题；不含患者 float 行 */
const CONSULT_HEADER_TEMPLATE =
  '<span style="font-size:22pt;font-weight:700">{orgName}</span>\n' +
  '<span style="font-size:16pt">{documentTitle}</span>';

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

function collectText(node) {
  let s = '';
  walk(node, (n) => {
    if (n.type === 'text') s += n.text ?? '';
  });
  return s;
}

function collectFreeTexts(node) {
  const list = [];
  walk(node, (n) => {
    if (n.type === 'emrFreeText') list.push(n);
  });
  return list;
}

function freeTextValueByLabel(freeTexts, label) {
  const hit = freeTexts.find((f) => String(f.attrs?.label ?? '') === label);
  return hit?.attrs?.value ?? '';
}

function makeFreeText(slotId, label, placeholder, value = '') {
  return {
    type: 'emrFreeText',
    attrs: {
      slotId,
      label,
      placeholder,
      value: value == null ? '' : String(value),
      required: false,
      requiredMessage: '',
    },
  };
}

function makeText(text) {
  return { type: 'text', text };
}

function rebuildApplicantSignRow(row, prefix) {
  const fts = collectFreeTexts(row);
  const applicant = freeTextValueByLabel(fts, '申请医师') || fts[0]?.attrs?.value || '';
  const director = freeTextValueByLabel(fts, '科主任') || fts[1]?.attrs?.value || '';
  return {
    type: 'tableRow',
    content: [
      {
        type: 'tableCell',
        attrs: { colspan: 8, rowspan: 1, colwidth: null, name: 'signBlock' },
        content: [
          {
            type: 'paragraph',
            attrs: { textAlign: 'left', lineHeight: '1.5' },
            content: [
              makeText('申请医师：'),
              makeFreeText(`${prefix}-applicant`, '申请医师', '请在这里填写申请医师', applicant),
              makeText('　　科主任：'),
              makeFreeText(`${prefix}-director`, '科主任', '请在这里填写科主任', director),
              // 仅 label，后留空白供手写，不放可填控件
              makeText('　　时间：　　　　　　　　　　'),
            ],
          },
        ],
      },
    ],
  };
}

function rebuildConsultPhysicianRow(row, prefix) {
  const fts = collectFreeTexts(row);
  const dept =
    freeTextValueByLabel(fts, '会诊科室') || freeTextValueByLabel(fts, '科室') || '';
  let physicianVal = freeTextValueByLabel(fts, '会诊医师');
  if (!physicianVal) {
    const hit = fts.find((f) => {
      const lb = String(f.attrs?.label ?? '');
      return (
        lb !== '会诊科室' &&
        lb !== '科室' &&
        lb !== '年月日时分' &&
        lb !== '会诊时间' &&
        lb !== '申请日期'
      );
    });
    physicianVal = hit?.attrs?.value || '';
  }
  return {
    type: 'tableRow',
    content: [
      {
        type: 'tableCell',
        attrs: { colspan: 8, rowspan: 1, colwidth: null, name: 'signBlock' },
        content: [
          {
            type: 'paragraph',
            attrs: { textAlign: 'left', lineHeight: '1.5' },
            content: [
              makeText('科室：'),
              makeFreeText(`${prefix}-consult-dept`, '会诊科室', '请在这里填写科室', dept),
              makeText('　会诊医师：'),
              makeFreeText(
                `${prefix}-consult-physician`,
                '会诊医师',
                '请在这里填写会诊医师',
                physicianVal,
              ),
              makeText('　　时间：　　　　　　　　　　'),
            ],
          },
        ],
      },
    ],
  };
}

function findConsultTable(doc) {
  if (!Array.isArray(doc.content)) return null;
  return doc.content.find((n) => n.type === 'table' && n.attrs?.name === 'consultRequestForm') ?? null;
}

function stripPatientFromHeader(doc) {
  if (!doc.emrPageChrome) doc.emrPageChrome = {};
  const header = { ...(doc.emrPageChrome.header ?? {}) };
  header.enabled = true;
  header.heightMm = 0;
  // 保留院名+标题，去掉姓名/病房/床位/住院号
  header.template = CONSULT_HEADER_TEMPLATE;
  header.underline = false;
  doc.emrPageChrome.header = header;
  if (!doc.emrPageChrome.footer) {
    doc.emrPageChrome.footer = {
      align: 'center',
      bold: false,
      enabled: true,
      heightMm: 12,
      italic: false,
      template: '第 {page} 页 (共 {total} 页)',
      underline: false,
    };
  }
}

function normalizeParagraphs(doc) {
  walk(doc, (node) => {
    if (node.type !== 'paragraph') return;
    const attrs = node.attrs ?? {};
    attrs.lineHeight = '1.5';
    if (!attrs.textAlign) attrs.textAlign = 'left';
    node.attrs = attrs;
  });
}

function normalizeConsultDoc(doc, idHint) {
  const next = structuredClone(doc);
  next.emrPageMargins = { ...MARGINS };
  stripPatientFromHeader(next);
  normalizeParagraphs(next);

  const table = findConsultTable(next);
  if (!table || !Array.isArray(table.content)) return next;

  for (let i = 0; i < table.content.length; i++) {
    const row = table.content[i];
    const text = collectText(row);
    if (text.includes('申请医师') && text.includes('科主任')) {
      table.content[i] = rebuildApplicantSignRow(row, `consult-${idHint}-r${i}`);
      continue;
    }
    if (text.includes('会诊医师') && !text.includes('邀请')) {
      table.content[i] = rebuildConsultPhysicianRow(row, `consult-${idHint}-r${i}`);
    }
  }

  return next;
}

function sameJson(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}

await client.connect();
console.log('db', DB_NAME, APPLY ? 'APPLY' : 'DRY-RUN');

const tpls = await client.query(
  `SELECT id::text, name, document_type_code, content
   FROM emr_template
   WHERE name = $1
   ORDER BY name`,
  [TEMPLATE_NAME],
);
console.log('templates', tpls.rows.length);
let tplChanged = 0;
for (const row of tpls.rows) {
  const raw = asDoc(row.content);
  const next = normalizeConsultDoc(raw, row.id);
  const changed = !sameJson(raw, next);
  const hdr = /\{orgName\}/.test(String(next.emrPageChrome?.header?.template ?? ''))
    ? 'ORG+TITLE'
    : 'NO_ORG';
  const hasPatient = /\{patientName\}/.test(String(next.emrPageChrome?.header?.template ?? ''));
  console.log(
    (changed ? '* ' : '  ') + row.id,
    row.name,
    raw.emrPageMargins?.left,
    '->',
    next.emrPageMargins.left,
    hdr,
    hasPatient ? 'STILL_PATIENT' : 'NO_PATIENT',
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
   WHERE title ILIKE '%会诊申请单%'`,
);
console.log('documents', docs.rows.length);
let docChanged = 0;
for (const row of docs.rows) {
  const raw = asDoc(row.content);
  const next = normalizeConsultDoc(raw, row.id);
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

console.log('tpl updates', APPLY ? tplChanged : '(dry-run)');
console.log('doc updates', APPLY ? docChanged : `(dry-run ${docChanged})`);
await client.end();
