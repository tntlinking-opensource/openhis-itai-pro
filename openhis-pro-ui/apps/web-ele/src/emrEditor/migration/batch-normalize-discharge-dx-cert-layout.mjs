/**
 * whale-pro：出院诊断书模版 + 历史病历
 * - 头部：左对齐两行段落（不用表格/两端拉开），入院/出院日期补到时分；去掉头部下横线
 * - 页眉保留院徽+院名+标题，去掉患者信息行
 * - 「出院病情摘要及诊疗经过」独立成段（从诊断段内拆出）
 * - 尾部居右两行：医师签名+主治医师 / 科室+日期，行距 1.5
 * - 页边距 15mm、段落 1.5
 *
 * 用法：
 *   npx tsx src/emrEditor/migration/batch-normalize-discharge-dx-cert-layout.mjs --db whale-pro --apply
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
const TEMPLATE_NAME = '出院诊断书';
const SUMMARY_LABEL = '出院病情摘要及诊疗经过';
const GUIDANCE_LABEL = '出院后要求及注意事项';
const DIAG_LABEL = '出院诊断';

const client = new pg.Client({
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
  database: DB_NAME,
});

function asDoc(raw) {
  if (!raw) throw new Error('content 为空');
  return typeof raw === 'string' ? JSON.parse(raw) : raw;
}

function walk(node, fn) {
  if (!node || typeof node !== 'object') return;
  fn(node);
  if (Array.isArray(node.content)) {
    for (const c of node.content) walk(c, fn);
  }
}

function clone(v) {
  return structuredClone(v);
}

function makeText(text, bold = false) {
  const n = { type: 'text', text };
  if (bold) n.marks = [{ type: 'bold' }];
  return n;
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

function makeChip(slotId, fieldKey, label, value = '', editable = false) {
  return {
    type: 'emrMetadataChip',
    attrs: {
      slotId,
      fieldKey,
      label,
      value: value == null ? '' : String(value),
      editable,
    },
  };
}

function makePara(name, content, extraAttrs = {}) {
  return {
    type: 'paragraph',
    attrs: { textAlign: 'left', lineHeight: '1.5', name: name ?? null, ...extraAttrs },
    content: content?.length ? content : undefined,
  };
}

function toDateTime(v) {
  const s = String(v ?? '').trim();
  if (!s) return '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return `${s} 00:00`;
  return s;
}

function extractLogoSrc(template) {
  const m = /\{\{emrImg(?:\s+(?:w|x|y)=\d+)*:([^}]+)\}\}/.exec(String(template ?? ''));
  return m?.[1]?.trim() || '';
}

function buildHeaderTemplate(logoSrc) {
  const img = logoSrc ? `{{emrImg w=39 x=0 y=0:${logoSrc}}}` : '';
  return (
    `${img}<span style="font-size:22pt;font-weight:700">{orgName}</span>\n` +
    `<span style="font-size:16pt">{documentTitle}</span>`
  );
}

function stripPatientHeader(doc) {
  if (!doc.emrPageChrome) doc.emrPageChrome = {};
  const header = { ...(doc.emrPageChrome.header ?? {}) };
  const logo = extractLogoSrc(header.template);
  header.enabled = true;
  header.heightMm = 0;
  header.template = buildHeaderTemplate(logo);
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

function nodeValue(node) {
  if (!node) return '';
  if (node.type === 'emrFreeText' || node.type === 'emrDataElement') {
    return String(node.attrs?.value ?? '');
  }
  if (node.type === 'emrMetadataChip') return String(node.attrs?.value ?? '');
  return '';
}

function findChip(nodes, fieldKey) {
  let hit = null;
  for (const n of nodes) {
    walk(n, (x) => {
      if (x.type === 'emrMetadataChip' && x.attrs?.fieldKey === fieldKey) hit = x;
    });
  }
  return hit;
}

function findFreeByLabel(nodes, label) {
  let hit = null;
  for (const n of nodes) {
    walk(n, (x) => {
      if (
        (x.type === 'emrFreeText' || x.type === 'emrDataElement') &&
        String(x.attrs?.label ?? '') === label
      ) {
        hit = x;
      }
    });
  }
  return hit;
}

function collectText(node) {
  let s = '';
  walk(node, (n) => {
    if (n.type === 'text') s += n.text ?? '';
  });
  return s;
}

/** 从混排段落拆出摘要正文（标签之后的纯文本） */
function extractAfterLabel(para, label) {
  if (!para?.content) return { before: [], afterText: '' };
  const before = [];
  let found = false;
  let afterText = '';
  for (const child of para.content) {
    if (!found && child.type === 'text' && String(child.text ?? '').includes(label)) {
      found = true;
      const idx = String(child.text).indexOf(label);
      const head = String(child.text).slice(0, idx);
      if (head) before.push({ ...child, text: head });
      const rest = String(child.text).slice(idx + label.length);
      if (rest) afterText += rest;
      continue;
    }
    if (!found) {
      before.push(child);
      continue;
    }
    if (child.type === 'text') afterText += child.text ?? '';
    else if (child.type === 'emrFreeText' || child.type === 'emrDataElement') {
      afterText += nodeValue(child);
    }
  }
  return { before, afterText: afterText.trim() };
}

/** 从注意事项段拆出签名字段（遇到「科室：」起） */
function splitGuidanceAndSign(para) {
  if (!para?.content) {
    return { guidanceChildren: [], sign: null };
  }
  const guidanceChildren = [];
  let signStart = -1;
  for (let i = 0; i < para.content.length; i++) {
    const c = para.content[i];
    if (c.type === 'text' && String(c.text ?? '').includes('科室：')) {
      signStart = i;
      break;
    }
  }
  if (signStart < 0) {
    return { guidanceChildren: para.content, sign: null };
  }
  for (let i = 0; i < signStart; i++) guidanceChildren.push(para.content[i]);
  const signNodes = para.content.slice(signStart);
  const sign = {
    dept: '',
    doctor: '',
    attending: '',
    date: '',
  };
  let mode = '';
  for (const n of signNodes) {
    if (n.type === 'text') {
      const t = String(n.text ?? '');
      if (t.includes('科室：')) mode = 'dept';
      else if (t.includes('医师签名：')) mode = 'doctor';
      else if (t.includes('主治医师：')) {
        mode = 'attending';
        const m = /主治医师：\s*([^\s　]+)/.exec(t);
        if (m) sign.attending = m[1];
        const dm = /日期：\s*(\S+)/.exec(t);
        if (dm) sign.date = dm[1];
      } else if (t.includes('日期：')) {
        mode = 'date';
        const dm = /日期：\s*(\S*)/.exec(t);
        if (dm && dm[1]) sign.date = dm[1];
      }
      continue;
    }
    if (n.type === 'emrMetadataChip') {
      const v = nodeValue(n);
      const key = n.attrs?.fieldKey;
      if (key === 'deptName' || mode === 'dept') sign.dept = v || sign.dept;
      else if (key === 'doctorName' || mode === 'doctor') sign.doctor = v || sign.doctor;
      else if (key === 'attendingPhysician' || mode === 'attending') {
        sign.attending = v || sign.attending;
      }
      continue;
    }
    if (n.type === 'emrFreeText' || n.type === 'emrDataElement') {
      if (mode === 'date' || String(n.attrs?.label ?? '').includes('日期')) {
        sign.date = nodeValue(n) || sign.date;
      }
    }
  }
  return { guidanceChildren, sign };
}

function extractDxHeaderFields(srcNodes) {
  const chips = {
    patientName: '',
    age: '',
    gender: '',
    occupation: '',
    inpatientNo: '',
  };
  let admit = '';
  let discharge = '';
  let days = '';

  const sources = Array.isArray(srcNodes) ? srcNodes : srcNodes ? [srcNodes] : [];
  for (const src of sources) {
    if (!src) continue;
    if (src.type === 'table') {
      for (const row of src.content || []) {
        for (const cell of row.content || []) {
          const t = collectText(cell);
          let field = null;
          walk(cell, (n) => {
            if (
              n.type === 'emrFreeText' ||
              n.type === 'emrDataElement' ||
              n.type === 'emrMetadataChip'
            ) {
              field = n;
            }
          });
          if (!field) continue;
          const v = nodeValue(field);
          if (t.includes('姓名')) chips.patientName = v;
          else if (t.includes('年龄')) chips.age = v;
          else if (t.includes('性别')) chips.gender = v;
          else if (t.includes('职业')) chips.occupation = v;
          else if (t.includes('住院号')) chips.inpatientNo = v;
          else if (t.includes('入院日期')) admit = v;
          else if (t.includes('出院日期')) discharge = v;
          else if (t.includes('住院')) days = v;
        }
      }
      continue;
    }
    // 已刷成段落时再取值
    walk(src, (n) => {
      if (n.type === 'emrMetadataChip' && n.attrs?.fieldKey) {
        const k = n.attrs.fieldKey;
        if (k in chips) chips[k] = nodeValue(n);
      }
      if (n.type === 'emrFreeText' || n.type === 'emrDataElement') {
        const lb = String(n.attrs?.label ?? '');
        const v = nodeValue(n);
        if (lb.includes('入院日期')) admit = v;
        else if (lb.includes('出院日期')) discharge = v;
        else if (lb.includes('住院天数') || lb === '住院') days = v;
      }
    });
  }
  return { chips, admit, discharge, days };
}

/** 头部两行左对齐段落，不用表格两端拉开 */
function buildDxHeaderParas(srcNodes, prefix) {
  const { chips, admit, discharge, days } = extractDxHeaderFields(srcNodes);
  return [
    makePara('dxHeaderLine1', [
      makeText('姓名：', true),
      makeChip(`${prefix}-pn`, 'patientName', '患者姓名', chips.patientName),
      makeText('　　年龄：', true),
      makeChip(`${prefix}-age`, 'age', '年龄', chips.age),
      makeText('　　性别：', true),
      makeChip(`${prefix}-gender`, 'gender', '性别', chips.gender),
      makeText('　　职业：', true),
      makeChip(`${prefix}-occ`, 'occupation', '职业', chips.occupation),
      makeText('　　住院号：', true),
      makeChip(`${prefix}-ino`, 'inpatientNo', '住院号', chips.inpatientNo),
    ]),
    makePara('dxHeaderLine2', [
      makeText('入院日期：', true),
      makeFreeText(
        `${prefix}-admit`,
        '入院日期',
        '请填写年-月-日 时:分',
        toDateTime(admit),
      ),
      makeText('　　出院日期：', true),
      makeFreeText(
        `${prefix}-discharge`,
        '出院日期',
        '请填写年-月-日 时:分',
        toDateTime(discharge),
      ),
      makeText('　　住院：', true),
      makeFreeText(`${prefix}-days`, '住院天数', '请在这里填写住院天数', days),
      makeText(' 天'),
    ]),
  ];
}

/** 尾部居右两行：医师签名+主治医师 / 科室+日期 */
function buildSignParas(sign, prefix) {
  const s = sign || { dept: '', doctor: '', attending: '', date: '' };
  const right = (name, children) =>
    makePara(name, children, { textAlign: 'right' });
  return [
    right('signLineDoctors', [
      makeText('医师签名：'),
      makeChip(`${prefix}-doc`, 'doctorName', '接诊医生', s.doctor || ''),
      makeText('　　主治医师：'),
      makeChip(
        `${prefix}-att`,
        'attendingPhysician',
        '主治医师',
        s.attending || '',
        true,
      ),
    ]),
    right('signLineDeptDate', [
      makeText('科室：'),
      makeChip(`${prefix}-dept`, 'deptName', '科室', s.dept || ''),
      makeText('　　日期：'),
      makeFreeText(
        `${prefix}-date`,
        '签署日期',
        '请填写年-月-日 时:分',
        toDateTime(s.date || ''),
      ),
    ]),
  ];
}

function freeTextFromPara(para, fallbackLabel) {
  let ft = null;
  walk(para, (n) => {
    if (n.type === 'emrFreeText' || n.type === 'emrDataElement') ft = n;
  });
  return ft ? nodeValue(ft) : '';
}

function normalizeDxCert(doc, idHint) {
  const next = clone(doc);
  next.emrPageMargins = { ...MARGINS };
  stripPatientHeader(next);

  const prefix = `dx-${idHint}`;
  const nodes = Array.isArray(next.content) ? next.content : [];

  const srcHeaderNodes = nodes.filter(
    (n) =>
      (n.type === 'table' &&
        (n.attrs?.name === 'dxHeader' || n.attrs?.name === 'verifyBlock')) ||
      (n.type === 'paragraph' &&
        (n.attrs?.name === 'dxHeaderLine1' || n.attrs?.name === 'dxHeaderLine2')),
  );

  let diagnosisValue = '';
  let summaryValue = '';
  let guidanceValue = '';
  let sign = { dept: '', doctor: '', attending: '', date: '' };

  for (const n of nodes) {
    const name = n.attrs?.name;
    if (
      name === 'dxHeaderLine1' ||
      name === 'dxHeaderLine2' ||
      name === 'signLine1' ||
      name === 'signLine2' ||
      name === 'signLineDoctor' ||
      name === 'signLineAttending' ||
      name === 'signLineDept' ||
      name === 'signLineDate' ||
      name === 'signLineDoctors' ||
      name === 'signLineDeptDate'
    ) {
      if (String(name).startsWith('signLine')) {
        walk(n, (x) => {
          if (x.type === 'emrMetadataChip') {
            const k = x.attrs?.fieldKey;
            const v = nodeValue(x);
            if (k === 'deptName') sign.dept = v || sign.dept;
            if (k === 'doctorName') sign.doctor = v || sign.doctor;
            if (k === 'attendingPhysician') sign.attending = v || sign.attending;
          }
          if (
            (x.type === 'emrFreeText' || x.type === 'emrDataElement') &&
            String(x.attrs?.label ?? '').includes('日期')
          ) {
            sign.date = nodeValue(x) || sign.date;
          }
        });
      }
      continue;
    }
    if (name === 'dischargeDiagnosis' || (n.type === 'paragraph' && collectText(n).startsWith('出院诊断'))) {
      const split = extractAfterLabel(n, SUMMARY_LABEL + '：') ;
      // also try without fullwidth colon variants
      const split2 =
        split.afterText || extractAfterLabel(n, SUMMARY_LABEL).afterText
          ? split.afterText
            ? split
            : extractAfterLabel(n, SUMMARY_LABEL)
          : extractAfterLabel(n, SUMMARY_LABEL + ':');
      const used = split.afterText ? split : split2;
      // diagnosis from freeText in before part
      const diagNode = { type: 'paragraph', content: used.before.length ? used.before : n.content };
      diagnosisValue = freeTextFromPara(diagNode) || freeTextFromPara(n);
      if (used.afterText) summaryValue = used.afterText;
      // if label was inside same freeText value
      if (diagnosisValue.includes(SUMMARY_LABEL)) {
        const idx = diagnosisValue.indexOf(SUMMARY_LABEL);
        summaryValue = (summaryValue || diagnosisValue.slice(idx + SUMMARY_LABEL.length).replace(/^：/, '')).trim();
        diagnosisValue = diagnosisValue.slice(0, idx).trim();
      }
      continue;
    }
    if (name === 'dischargeConditionSummary') {
      summaryValue = freeTextFromPara(n) || summaryValue;
      continue;
    }
    if (name === 'dischargeGuidance') {
      const { guidanceChildren, sign: extracted } = splitGuidanceAndSign(n);
      const gPara = { type: 'paragraph', content: guidanceChildren };
      guidanceValue = freeTextFromPara(gPara) || freeTextFromPara(n);
      if (extracted) {
        sign = { ...sign, ...Object.fromEntries(Object.entries(extracted).filter(([, v]) => v)) };
      }
      // guidance freeText may contain sign text
      if (guidanceValue.includes('科室：')) {
        const idx = guidanceValue.indexOf('科室：');
        guidanceValue = guidanceValue.slice(0, idx).trim();
      }
      continue;
    }
    if (name === 'signLine' || (n.type === 'table' && n.attrs?.name === 'signBlock')) {
      if (n.type === 'table') {
        walk(n, (x) => {
          if (x.type === 'emrMetadataChip') {
            const k = x.attrs?.fieldKey;
            const v = nodeValue(x);
            if (k === 'deptName') sign.dept = v;
            if (k === 'doctorName') sign.doctor = v;
            if (k === 'attendingPhysician') sign.attending = v;
          }
          if (x.type === 'emrFreeText' || x.type === 'emrDataElement') {
            const lb = String(x.attrs?.label ?? '');
            if (lb.includes('日期')) sign.date = nodeValue(x);
          }
        });
      } else {
        const { sign: extracted } = splitGuidanceAndSign(n);
        if (extracted) {
          sign = { ...sign, ...Object.fromEntries(Object.entries(extracted).filter(([, v]) => v)) };
        }
        // also chips directly
        walk(n, (x) => {
          if (x.type === 'emrMetadataChip') {
            const k = x.attrs?.fieldKey;
            const v = nodeValue(x);
            if (k === 'deptName') sign.dept = v || sign.dept;
            if (k === 'doctorName') sign.doctor = v || sign.doctor;
            if (k === 'attendingPhysician') sign.attending = v || sign.attending;
          }
          if ((x.type === 'emrFreeText' || x.type === 'emrDataElement') && String(x.attrs?.label ?? '').includes('日期')) {
            sign.date = nodeValue(x) || sign.date;
          }
        });
        const t = collectText(n);
        const am = /主治医师：\s*([^\s　]+)/.exec(t);
        if (am && !sign.attending) sign.attending = am[1];
        const dm = /日期：\s*(\S+)/.exec(t);
        if (dm && !sign.date) sign.date = dm[1];
      }
    }
  }

  // 旧版正文 hospitalTitle/formTitle 若存在可丢掉（已有 chrome）
  const printFooter =
    nodes.find((n) => n.attrs?.name === 'printFooter') ||
    makePara('printFooter', [
      makeText('注意：无公章及无医生章无效、如有涂改处须加盖医生章。'),
    ]);

  next.content = [
    ...buildDxHeaderParas(srcHeaderNodes, prefix),
    makePara('dischargeDiagnosis', [
      makeText(`${DIAG_LABEL}：`, true),
      makeFreeText(`${prefix}-diag`, DIAG_LABEL, `请在这里填写${DIAG_LABEL}`, diagnosisValue),
    ]),
    makePara('dischargeConditionSummary', [
      makeText(`${SUMMARY_LABEL}：`, true),
      makeFreeText(
        `${prefix}-summary`,
        SUMMARY_LABEL,
        `请在这里填写${SUMMARY_LABEL}`,
        summaryValue,
      ),
    ]),
    makePara('dischargeGuidance', [
      makeText(`${GUIDANCE_LABEL}：`, true),
      makeFreeText(
        `${prefix}-guidance`,
        GUIDANCE_LABEL,
        `请在这里填写${GUIDANCE_LABEL}`,
        guidanceValue,
      ),
    ]),
    ...buildSignParas(sign, prefix),
    printFooter.attrs?.name === 'printFooter'
      ? { ...printFooter, attrs: { ...(printFooter.attrs || {}), lineHeight: '1.5', textAlign: 'left' } }
      : printFooter,
  ];

  // 统一段落行距
  walk(next, (node) => {
    if (node.type !== 'paragraph') return;
    const attrs = node.attrs ?? {};
    attrs.lineHeight = '1.5';
    if (!attrs.textAlign) attrs.textAlign = 'left';
    node.attrs = attrs;
  });

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
   WHERE name = $1`,
  [TEMPLATE_NAME],
);
console.log('templates', tpls.rows.length);
let tplChanged = 0;
for (const row of tpls.rows) {
  const raw = asDoc(row.content);
  const next = normalizeDxCert(raw, row.id);
  const changed = !sameJson(raw, next);
  const patient = /\{patientName\}/.test(next.emrPageChrome?.header?.template ?? '');
  console.log(
    (changed ? '* ' : '  ') + row.id,
    row.name,
    raw.emrPageMargins?.left,
    '->',
    next.emrPageMargins.left,
    patient ? 'HAS_PATIENT' : 'NO_PATIENT',
    (next.content || []).map((n) => `${n.type}:${n.attrs?.name ?? ''}`).join('|'),
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
   WHERE title ILIKE '%出院诊断书%'`,
);
console.log('documents', docs.rows.length);
let docChanged = 0;
for (const row of docs.rows) {
  const raw = asDoc(row.content);
  const next = normalizeDxCert(raw, row.id);
  if (sameJson(raw, next)) continue;
  docChanged += 1;
  console.log(
    (APPLY ? '* ' : '  ') + row.id,
    row.title,
    (next.content || []).map((n) => `${n.type}:${n.attrs?.name ?? ''}`).join('|'),
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

// dry-run 时抽查混排文书字段是否拆对
if (!APPLY) {
  const sample = docs.rows.find((r) => r.id === '346588067756773376') || docs.rows[0];
  if (sample) {
    const next = normalizeDxCert(asDoc(sample.content), sample.id);
    const getFt = (name) => {
      const p = (next.content || []).find((n) => n.attrs?.name === name);
      let v = '';
      walk(p, (n) => {
        if (n.type === 'emrFreeText') v = n.attrs?.value ?? '';
      });
      return String(v).slice(0, 80);
    };
    console.log('\n[sample]', sample.id, sample.title);
    console.log('  diag', getFt('dischargeDiagnosis'));
    console.log('  summary', getFt('dischargeConditionSummary'));
    console.log('  guidance', getFt('dischargeGuidance'));
    const signBits = [];
    for (const p of (next.content || []).filter((n) =>
      String(n.attrs?.name ?? '').startsWith('signLine'),
    )) {
      signBits.push(`${p.attrs.name}(${p.attrs.textAlign})`);
      walk(p, (n) => {
        if (n.type === 'text') signBits.push(n.text);
        if (n.type === 'emrMetadataChip') signBits.push(`[${n.attrs.fieldKey}=${n.attrs.value}]`);
        if (n.type === 'emrFreeText') signBits.push(`[date=${n.attrs.value}]`);
      });
      signBits.push(' | ');
    }
    console.log('  sign', signBits.join(''));
    const dates = [];
    for (const p of (next.content || []).filter(
      (n) => n.attrs?.name === 'dxHeaderLine1' || n.attrs?.name === 'dxHeaderLine2',
    )) {
      walk(p, (n) => {
        if (n.type === 'emrFreeText') dates.push(`${n.attrs.label}=${n.attrs.value}`);
      });
    }
    console.log('  header dates', dates.join(' | '));
    console.log(
      '  hasHR',
      (next.content || []).some((n) => n.type === 'horizontalRule'),
    );
  }
}

await client.end();
