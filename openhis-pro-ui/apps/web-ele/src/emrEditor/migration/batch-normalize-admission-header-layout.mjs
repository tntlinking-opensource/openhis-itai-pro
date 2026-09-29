/**
 * whale-pro：入院记录 模版 + 历史
 * - 页眉：院徽+院名+标题 + 左姓名/病房/床位、右住院号
 * - 基础信息：无框两列表格 patientHeader（编辑器 CSS 去线；打印走既有无框两列）
 * - 行距 1.5；页边距 15mm
 * - 尾部居右两行：医师签名+上级医师签名 / 记录日期
 *
 * 用法：
 *   npx tsx src/emrEditor/migration/batch-normalize-admission-header-layout.mjs --db whale-pro --apply
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
const KEEP_CENTER = new Set(['hospitalTitle', 'formTitle', 'historyTitle', 'peTitle', 'pageHeaderHint']);

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
    attrs: {
      textAlign: 'left',
      lineHeight: '1.5',
      name: name ?? null,
      ...extraAttrs,
    },
    content: content?.length ? content : undefined,
  };
}

function nodeValue(node) {
  if (!node) return '';
  if (
    node.type === 'emrFreeText' ||
    node.type === 'emrDataElement' ||
    node.type === 'emrDateTime' ||
    node.type === 'emrMetadataChip'
  ) {
    return String(node.attrs?.value ?? '');
  }
  return '';
}

function collectText(node) {
  let s = '';
  walk(node, (n) => {
    if (n.type === 'text') s += n.text ?? '';
  });
  return s;
}

function extractLogoSrc(template) {
  const m = /\{\{emrImg(?:\s+(?:w|x|y)=\d+)*:([^}]+)\}\}/.exec(String(template ?? ''));
  return m?.[1]?.trim() || '';
}

/** 页眉：院徽+院名+标题 + 左姓名/病房/床位、右住院号 */
function restorePatientChrome(doc) {
  if (!doc.emrPageChrome) doc.emrPageChrome = {};
  const header = { ...(doc.emrPageChrome.header ?? {}) };
  const logo = extractLogoSrc(header.template);
  const img = logo ? `{{emrImg w=39 x=0 y=0:${logo}}}` : '';
  header.enabled = true;
  header.heightMm = 0;
  header.underline = false;
  header.template =
    `${img}<span style="font-size:22pt;font-weight:700">{orgName}</span>\n` +
    `<span style="font-size:16pt">{documentTitle}</span>\n` +
    `<span style="font-size:12pt;float:left">姓名：{patientName}　病房：{wardName}　床位：{bedNo}</span>` +
    `<span style="font-size:12pt;float:right">住院号：{inpatientNo}</span>` +
    `<span style="display:block;clear:both;height:0"></span>`;
  doc.emrPageChrome.header = header;
}

function extractHeaderFields(nodes) {
  const chips = {
    patientName: '',
    gender: '',
    age: '',
    occupation: '',
    ethnicity: '',
    maritalStatus: '',
    wardName: '',
    bedNo: '',
    inpatientNo: '',
    birthPlace: '',
    historyProvider: '',
    reliability: '',
  };
  let admitTime = '';
  let recordTime = '';
  let solarTerm = '';

  const takeFromNode = (n) => {
    if (n.type === 'emrMetadataChip' && n.attrs?.fieldKey) {
      const k = n.attrs.fieldKey;
      if (k in chips) chips[k] = nodeValue(n) || chips[k];
      if (k === 'bedNumber') chips.bedNo = nodeValue(n) || chips.bedNo;
      if (k === 'sex') chips.gender = nodeValue(n) || chips.gender;
    }
    if (n.type === 'emrFreeText' || n.type === 'emrDataElement' || n.type === 'emrDateTime') {
      const lb = String(n.attrs?.label ?? '');
      const v = nodeValue(n);
      if (lb.includes('入院时间')) admitTime = v || admitTime;
      else if (lb.includes('记录时间') || lb.includes('记录日期')) recordTime = v || recordTime;
      else if (lb.includes('节气')) solarTerm = v || solarTerm;
    }
  };

  for (const node of nodes || []) {
    if (node.type === 'table') {
      for (const row of node.content || []) {
        for (const cell of row.content || []) {
          const t = collectText(cell);
          let field = null;
          walk(cell, (n) => {
            takeFromNode(n);
            if (
              n.type === 'emrFreeText' ||
              n.type === 'emrDataElement' ||
              n.type === 'emrDateTime' ||
              n.type === 'emrMetadataChip'
            ) {
              field = n;
            }
          });
          if (!field) continue;
          const v = nodeValue(field);
          if (t.includes('姓名')) chips.patientName = v || chips.patientName;
          else if (t.includes('性别')) chips.gender = v || chips.gender;
          else if (t.includes('年龄')) chips.age = v || chips.age;
          else if (t.includes('职业')) chips.occupation = v || chips.occupation;
          else if (t.includes('民族')) chips.ethnicity = v || chips.ethnicity;
          else if (t.includes('婚姻')) chips.maritalStatus = v || chips.maritalStatus;
          else if (t.includes('病房')) chips.wardName = v || chips.wardName;
          else if (t.includes('床位') || t.includes('床号')) chips.bedNo = v || chips.bedNo;
          else if (t.includes('住院号')) chips.inpatientNo = v || chips.inpatientNo;
          else if (t.includes('入院时间')) admitTime = v || admitTime;
          else if (t.includes('记录时间')) recordTime = v || recordTime;
          else if (t.includes('出生地')) chips.birthPlace = v || chips.birthPlace;
          else if (t.includes('节气')) solarTerm = v || solarTerm;
          else if (t.includes('病史陈述')) chips.historyProvider = v || chips.historyProvider;
          else if (t.includes('可靠')) chips.reliability = v || chips.reliability;
        }
      }
      continue;
    }
    walk(node, takeFromNode);
  }

  return { chips, admitTime, recordTime, solarTerm };
}

function cell(children) {
  return {
    type: 'tableCell',
    attrs: { colspan: 1, rowspan: 1, colwidth: [90], name: 'patientField' },
    content: [makePara(null, children)],
  };
}

/**
 * 无框两列表格：编辑器靠 CSS 去线，打印走 patientHeader 既有无框两列逻辑。
 * （纯段落 admPh 在未部署配对打印前会打成单列，故改回表格。）
 */
function buildHeaderTable(fields, prefix) {
  const { chips, admitTime, recordTime, solarTerm } = fields;
  // 历史脏数据：节气值误写成「民族：」时清空
  let solar = solarTerm;
  if (solar === '民族：' || solar === '民族') solar = '';
  const row = (left, right) => ({
    type: 'tableRow',
    content: [cell(left), cell(right)],
  });
  return {
    type: 'table',
    attrs: { name: 'patientHeader' },
    content: [
      row(
        [
          makeText('姓名：'),
          makeChip(`${prefix}-pn`, 'patientName', '患者姓名', chips.patientName),
        ],
        [
          makeText('职业：'),
          makeChip(`${prefix}-occ`, 'occupation', '职业', chips.occupation, true),
        ],
      ),
      row(
        [
          makeText('性别：'),
          makeChip(`${prefix}-gender`, 'gender', '性别', chips.gender),
        ],
        [
          makeText('民族：'),
          makeChip(`${prefix}-eth`, 'ethnicity', '民族', chips.ethnicity),
        ],
      ),
      row(
        [makeText('年龄：'), makeChip(`${prefix}-age`, 'age', '年龄', chips.age)],
        [
          makeText('婚姻：'),
          makeChip(`${prefix}-mar`, 'maritalStatus', '婚姻', chips.maritalStatus),
        ],
      ),
      row(
        [
          makeText('入院时间：'),
          makeFreeText(`${prefix}-admit`, '入院时间', '请填写年-月-日 时:分', admitTime),
        ],
        [
          makeText('出生地：'),
          makeChip(`${prefix}-bp`, 'birthPlace', '出生地', chips.birthPlace, true),
        ],
      ),
      row(
        [
          makeText('记录时间：'),
          makeFreeText(`${prefix}-record`, '记录时间', '请填写年-月-日 时:分', recordTime),
        ],
        [
          makeText('病史陈述者：'),
          makeChip(`${prefix}-hp`, 'historyProvider', '病史陈述者', chips.historyProvider),
        ],
      ),
      row(
        [
          makeText('节气：'),
          makeFreeText(`${prefix}-solar`, '节气', '请在这里填写节气', solar),
        ],
        [
          makeText('可靠程度：'),
          makeChip(`${prefix}-rel`, 'reliability', '可靠程度', chips.reliability, true),
        ],
      ),
    ],
  };
}

function isHeaderParaName(name) {
  return (
    String(name || '').startsWith('admHeaderLine') ||
    String(name || '').startsWith('admPh') ||
    String(name || '').startsWith('admHeaderRow')
  );
}

function isSignParaName(name) {
  return (
    name === 'signBlock' ||
    name === 'signLineDoctors' ||
    name === 'signLineDate' ||
    name === 'signLine1' ||
    name === 'signLine2'
  );
}

function extractSignFields(nodes) {
  const sign = { doctor: '', attending: '', recordDate: '' };
  for (const n of nodes) {
    const name = n.attrs?.name;
    const text = collectText(n);
    const looksLikeSign =
      text.includes('医师签名') || text.includes('记录日期') || isSignParaName(name);
    if (!looksLikeSign) continue;

    walk(n, (x) => {
      if (x.type === 'emrMetadataChip') {
        const k = x.attrs?.fieldKey;
        const v = nodeValue(x);
        if (k === 'doctorName') sign.doctor = v || sign.doctor;
        if (k === 'attendingPhysician') sign.attending = v || sign.attending;
      }
      if (x.type === 'emrFreeText' || x.type === 'emrDataElement' || x.type === 'emrDateTime') {
        const lb = String(x.attrs?.label ?? '');
        const v = nodeValue(x);
        if (!v) return;
        if (lb.includes('记录') || lb.includes('日期') || text.includes('记录日期')) {
          sign.recordDate = v || sign.recordDate;
        }
      }
    });
    const dm = /医师签名：\s*([^\s　上级]+)/.exec(text);
    if (dm && !sign.doctor) sign.doctor = dm[1];
    const am = /上级医师签名：\s*([^\s　]+)/.exec(text);
    if (am && !sign.attending) sign.attending = am[1];
    const rd = /记录日期：\s*(\S+)/.exec(text);
    if (rd && !sign.recordDate) sign.recordDate = rd[1];
  }
  return sign;
}

function buildSignParas(sign, prefix) {
  const s = sign || { doctor: '', attending: '', recordDate: '' };
  const right = (name, children) => makePara(name, children, { textAlign: 'right' });
  return [
    right('signLineDoctors', [
      makeText('医师签名：'),
      makeChip(`${prefix}-doc`, 'doctorName', '接诊医生', s.doctor || ''),
      makeText('　　上级医师签名：'),
      makeChip(`${prefix}-att`, 'attendingPhysician', '上级医师', s.attending || '', true),
    ]),
    right('signLineDate', [
      makeText('记录日期：'),
      makeFreeText(`${prefix}-rdate`, '记录日期', '请填写年-月-日 时:分', s.recordDate || ''),
    ]),
  ];
}

function stripSignFromDiagnosisPara(para) {
  if (!para?.content) return para;
  const text = collectText(para);
  if (!text.includes('医师签名')) return para;
  const nextContent = [];
  for (const c of para.content) {
    if (c.type === 'text' && String(c.text ?? '').includes('医师签名')) {
      const idx = String(c.text).indexOf('医师签名');
      const head = String(c.text).slice(0, idx).replace(/\s+$/, '');
      if (head) nextContent.push({ ...c, text: head });
      break;
    }
    nextContent.push(c);
  }
  return { ...para, content: nextContent.length ? nextContent : undefined };
}

function normalizeAdmission(doc, idHint) {
  const next = clone(doc);
  next.emrPageMargins = { ...MARGINS };
  restorePatientChrome(next);

  const prefix = `adm-${idHint}`;
  const nodes = Array.isArray(next.content) ? next.content : [];
  const fields = extractHeaderFields(nodes);
  const sign = extractSignFields(nodes);

  const body = [];
  let headerInserted = false;

  for (let i = 0; i < nodes.length; i++) {
    const n = nodes[i];
    const name = n.attrs?.name;

    if (
      (n.type === 'table' && (name === 'patientHeader' || name === 'verifyBlock')) ||
      (n.type === 'paragraph' && isHeaderParaName(name))
    ) {
      if (!headerInserted) {
        body.push(buildHeaderTable(fields, prefix));
        headerInserted = true;
      }
      if (nodes[i + 1]?.type === 'horizontalRule') i += 1;
      continue;
    }

    if (n.type === 'horizontalRule' && !headerInserted) {
      continue;
    }

    if (n.type === 'paragraph' && isSignParaName(name)) {
      continue;
    }

    if (n.type === 'paragraph' && collectText(n).includes('医师签名') && name !== 'verifyBlock') {
      const cleaned = stripSignFromDiagnosisPara(n);
      if (collectText(cleaned).trim() || cleaned.content?.length) body.push(cleaned);
      continue;
    }

    // 去掉「入院诊断结构化（MRMS）」占位段
    if (
      name === 'admissionDiagnosisStructured' ||
      /入院诊断结构化|MRMS/.test(collectText(n) + String(n.attrs?.label ?? ''))
    ) {
      continue;
    }

    body.push(n);
  }

  if (!headerInserted) {
    body.unshift(buildHeaderTable(fields, prefix));
  }

  while (body.length) {
    const last = body[body.length - 1];
    if (isSignParaName(last?.attrs?.name)) {
      body.pop();
      continue;
    }
    if (last?.type === 'paragraph') {
      const hasField = last.content?.some(
        (c) =>
          c.type === 'emrFreeText' ||
          c.type === 'emrMetadataChip' ||
          c.type === 'emrDataElement' ||
          c.type === 'emrDateTime',
      );
      if (!hasField && !collectText(last).trim()) {
        body.pop();
        continue;
      }
    }
    break;
  }

  body.push(...buildSignParas(sign, prefix));
  next.content = body;

  walk(next, (node) => {
    if (node.type !== 'paragraph') return;
    const attrs = node.attrs ?? {};
    attrs.lineHeight = '1.5';
    const nm = String(attrs.name ?? '');
    if (KEEP_CENTER.has(nm)) attrs.textAlign = 'center';
    else if (attrs.textAlign === 'right') {
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

function outline(doc) {
  return (doc.content || [])
    .slice(0, 6)
    .map((n) => `${n.type}:${n.attrs?.name ?? ''}`)
    .join('|');
}

await client.connect();
console.log('db', DB_NAME, APPLY ? 'APPLY' : 'DRY-RUN');

const tpls = await client.query(
  `SELECT id::text, name, content
   FROM emr_template
   WHERE name = '入院记录' OR name = '入院记录（标准）'`,
);
console.log('templates', tpls.rows.length);
let tplChanged = 0;
for (const row of tpls.rows) {
  const raw = asDoc(row.content);
  const next = normalizeAdmission(raw, row.id);
  const changed = !sameJson(raw, next);
  const hasPatient = /\{patientName\}/.test(next.emrPageChrome?.header?.template ?? '');
  console.log(
    (changed ? '* ' : '  ') + row.id,
    row.name,
    raw.emrPageMargins?.left,
    '->',
    next.emrPageMargins.left,
    hasPatient ? 'CHROME_OK' : 'NO_CHROME_PATIENT',
    outline(next),
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
   WHERE title ILIKE '%入院记录%' OR document_type_code = 'ADMISSION_RECORD'`,
);
console.log('documents', docs.rows.length);
let docChanged = 0;
for (const row of docs.rows) {
  const raw = asDoc(row.content);
  const next = normalizeAdmission(raw, row.id);
  if (sameJson(raw, next)) continue;
  docChanged += 1;
  console.log((APPLY ? '* ' : '  ') + row.id, row.title, outline(next));
  if (APPLY) {
    await client.query(`UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
      JSON.stringify(next),
      row.id,
    ]);
  }
}

console.log('tpl updates', APPLY ? tplChanged : '(dry-run)');
console.log('doc updates', APPLY ? docChanged : `(dry-run ${docChanged})`);

if (!APPLY && docs.rows[0]) {
  const sample = docs.rows.find((r) => r.id === '345427617526583296') || docs.rows[0];
  const next = normalizeAdmission(asDoc(sample.content), sample.id);
  console.log('\n[sample]', sample.id);
  console.log(
    'chrome',
    String(next.emrPageChrome?.header?.template || '')
      .replace(/data:image[^}]+/, 'IMG')
      .slice(0, 280),
  );
  for (const n of (next.content || []).filter((x) => String(x.attrs?.name ?? '').startsWith('admPh'))) {
    let t = '';
    walk(n, (x) => {
      if (x.type === 'text') t += x.text || '';
      if (x.type === 'emrMetadataChip') t += `[${x.attrs.fieldKey}=${x.attrs.value}]`;
      if (x.type === 'emrFreeText') t += `[${x.attrs.label}=${x.attrs.value}]`;
    });
    console.log(' ', n.attrs.name, t);
  }
  console.log(' hasTable', (next.content || []).some((n) => n.type === 'table' && n.attrs?.name === 'patientHeader'));
}

await client.end();
