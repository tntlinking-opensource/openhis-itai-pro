/**
 * 发版窗口刷库：09 §5.1 布局 attrs + 08 §8 勾选。
 *
 * 扫全部 emr_template，以及未删除的 emr_document（已写文书不跟模板，必须一起写）。
 * 覆盖：门诊表头/病史/体征、会诊申请单、出院诊断书、风险评估行高、
 * 告知签字表、落款/核实表、入院表头与浊音界、风险评估/沟通/告知的纸质「□」。
 * 不改行距。用血单 A□、体检「□无水肿」对不上勾选组，不会转。
 * 门诊 patientField 不写 bareValue。院徽只补点名住院模板，不灌进病种副本。
 *
 * 用法（在 apps/web-ele 下，先装 pg）：
 *   npm install pg@8.23.0 --prefix %TEMP%\emr-pg
 *   set PRO_DB_HOST=目标库地址
 *   set PRO_DB_PORT=5432
 *   set PRO_DB_USER=postgres
 *   set PRO_DB_PASSWORD=库密码
 *   npx --yes -p tsx tsx src/emrEditor/migration/apply-print-layout-protocol.ts --db whale-training
 *   npx --yes -p tsx tsx src/emrEditor/migration/apply-print-layout-protocol.ts --db whale-training --apply
 *
 * 只刷模板、不动已写文书：加 --templates-only
 */
import { createRequire } from 'node:module';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

type PgClient = {
  connect: () => Promise<void>;
  end: () => Promise<void>;
  query: (
    sql: string,
    params?: unknown[],
  ) => Promise<{ rows: Array<Record<string, unknown>> }>;
};

/**
 * 解析 pg。本仓未声明 pg 依赖，刷库时从临时安装目录或 NODE_PATH 加载。
 */
function loadPg(): { Client: new (cfg: Record<string, unknown>) => PgClient } {
  const candidates: string[] = [];
  if (process.env.PG_MODULE_DIR) {
    candidates.push(path.join(process.env.PG_MODULE_DIR, 'pg', 'package.json'));
    candidates.push(path.join(process.env.PG_MODULE_DIR, 'node_modules', 'pg', 'package.json'));
  }
  for (const dir of String(process.env.NODE_PATH ?? '').split(path.delimiter)) {
    if (dir) {
      candidates.push(path.join(dir, 'pg', 'package.json'));
    }
  }
  candidates.push(
    path.join(process.env.TEMP ?? 'C:\\Users\\his\\AppData\\Local\\Temp', 'emr-pg', 'node_modules', 'pg', 'package.json'),
  );
  for (const pkg of candidates) {
    if (!existsSync(pkg)) {
      continue;
    }
    return createRequire(pathToFileURL(pkg).href)('pg');
  }
  throw new Error(
    '找不到 pg。先执行: npm install pg@8.23.0 --prefix %TEMP%\\emr-pg',
  );
}

const { Client } = loadPg();

const APPLY = process.argv.includes('--apply');
const TEMPLATES_ONLY = process.argv.includes('--templates-only');
const dbArgIdx = process.argv.indexOf('--db');
const DB_NAME =
  (dbArgIdx >= 0 ? process.argv[dbArgIdx + 1] : null) ||
  process.env.PRO_DB_NAME ||
  'whale-local';

/** 院徽只补这些住院模板，不按文书标题、不灌门诊病种副本。 */
const LOGO_TEMPLATE_NAMES = [
  '会诊申请单',
  '入院记录',
  '住院病人风险评估表',
  '医患沟通记录',
  '住院病人病情告知书',
  '出院诊断书',
];

const CHECKBOX_TEMPLATE_NAMES = new Set([
  '住院病人风险评估表',
  '医患沟通记录',
  '住院病人病情告知书',
]);

type JsonNode = {
  attrs?: Record<string, unknown>;
  content?: JsonNode[];
  marks?: unknown[];
  text?: string;
  type?: string;
};

type CellPatch = {
  align?: string;
  bareValue?: boolean;
  fillRemainShare?: number;
  minHeightMm?: number;
  nowrap?: boolean;
  paddingMm?: number | number[];
  valign?: string;
  wrap?: boolean;
};

type TablePatch = {
  border?: boolean;
  colWidths8?: number[];
  colWidthsByCount?: Record<number, number[]>;
  colWidthsEqual?: boolean;
  firstColShare?: number;
  splitLate?: boolean;
};

const TABLE_PATCH: Record<string, TablePatch> = {
  admissionSafetySign: {
    border: false,
    colWidthsByCount: { 2: [6.2, 3.8] },
  },
  consultRequestForm: {
    colWidths8: [1.3, 1.6, 0.8, 1.4, 1.0, 0.8, 0.8, 2.3],
    colWidthsByCount: { 4: [2.4, 3.6, 2.0, 4.0] },
    splitLate: true,
  },
  dischargeDiagnosisForm: {
    colWidths8: [2.05, 1.95, 1.1, 1.05, 1.1, 1.0, 1.35, 2.2],
    splitLate: true,
  },
  outpatientHistory: {
    colWidthsByCount: { 2: [1.15, 8.85] },
  },
  outpatientVitals: {
    border: false,
  },
  pageHeader: { border: false, colWidthsEqual: true },
  patientHeader: { border: false, colWidthsEqual: true },
  relativeCardiacBorder: { colWidthsEqual: true },
  riskForm: { firstColShare: 1.55, splitLate: true },
  signBlock: { border: false, colWidthsEqual: true },
  verifyBlock: { border: false, colWidthsEqual: true },
};

const CELL_BY_NAME: Record<string, CellPatch> = {
  formLabel: { align: 'center', valign: 'middle', nowrap: true },
  formValue: { align: 'center', valign: 'middle', nowrap: true },
  midBody: { minHeightMm: 12, valign: 'top', wrap: true, paddingMm: 2.5 },
  noticeSignLeft: { align: 'left' },
  noticeSignRight: { align: 'right' },
  pageHeaderLeft: { align: 'left' },
  pageHeaderRight: { align: 'right' },
  purposeBody: {
    minHeightMm: 12,
    valign: 'middle',
    wrap: true,
    align: 'left',
    paddingMm: 2.1,
  },
  sectionLabel: { align: 'center', valign: 'middle' },
  sectionValue: { align: 'left', valign: 'top', wrap: true },
  signBlock: { minHeightMm: 12, valign: 'middle', nowrap: true, paddingMm: [2.1, 3.5, 2.1, 3.5] },
  signRight: { align: 'right' },
  tallBody: { minHeightMm: 42, valign: 'top', wrap: true, paddingMm: 2.5 },
};

const CONSULT_CELL_BY_NAME: Record<string, CellPatch> = {
  formLabel: { align: 'center', valign: 'middle', nowrap: false, wrap: true },
  formValue: { align: 'center', valign: 'middle', nowrap: false, wrap: true, bareValue: true },
  midBody: {
    fillRemainShare: 52,
    minHeightMm: 52,
    valign: 'top',
    wrap: true,
    paddingMm: 2.8,
  },
  purposeBody: {
    minHeightMm: 12,
    valign: 'middle',
    wrap: true,
    align: 'left',
    paddingMm: 2.1,
  },
  signBlock: {
    minHeightMm: 12,
    valign: 'middle',
    nowrap: true,
    bareValue: true,
    paddingMm: [2.1, 3.5, 2.1, 3.5],
  },
  tallBody: {
    fillRemainShare: 68,
    minHeightMm: 68,
    valign: 'top',
    wrap: true,
    paddingMm: 2.8,
  },
};

const RISK_HEIGHT_BY_LABEL: { height: number; match: (label: string) => boolean }[] = [
  { match: (label) => label === '病情简介', height: 48 },
  { match: (label) => label.includes('不良后果'), height: 24 },
  { match: (label) => label === '评估等级' || label.includes('护理等级'), height: 16 },
  { match: (label) => label.includes('签名'), height: 18 },
  { match: (label) => label.includes('收集资料'), height: 12 },
];

const CHECKBOX_GROUPS: { fieldKey: string; labels: string[] }[] = [
  { fieldKey: 'paper:一般|病重|病危', labels: ['一般', '病重', '病危'] },
  {
    fieldKey: 'paper:特级护理|一级护理|二级护理|三级护理',
    labels: ['特级护理', '一级护理', '二级护理', '三级护理'],
  },
  { fieldKey: 'paper:同意上述诊疗方案|不同意', labels: ['同意上述诊疗方案', '不同意'] },
  { fieldKey: 'paper:同意|不同意', labels: ['同意', '不同意'] },
];

type Report = {
  checkboxes: number;
  logo: number;
  tables: number;
  wardBed: number;
};

function walk(node: JsonNode, fn: (n: JsonNode, parent?: JsonNode) => void, parent?: JsonNode): void {
  fn(node, parent);
  for (const child of node.content ?? []) {
    walk(child, fn, node);
  }
}

function collectText(node: JsonNode): string {
  let text = '';
  walk(node, (n) => {
    if (n.type === 'text') {
      text += n.text ?? '';
    }
  });
  return text;
}

function countRowColumns(row: JsonNode): number {
  let cols = 0;
  for (const cell of row.content ?? []) {
    if (cell.type !== 'tableCell' && cell.type !== 'tableHeader') {
      continue;
    }
    const span = Number(cell.attrs?.colspan ?? 1);
    cols += Number.isFinite(span) && span > 0 ? span : 1;
  }
  return cols;
}

function tableColCount(table: JsonNode): number {
  let max = 0;
  for (const row of table.content ?? []) {
    if (row.type !== 'tableRow') {
      continue;
    }
    max = Math.max(max, countRowColumns(row));
  }
  return max;
}

function equalColWidths(colCount: number): number[] {
  return Array.from({ length: colCount }, () => 1);
}

function resolveColWidths(patch: TablePatch, colCount: number): number[] | null {
  if (colCount <= 0) {
    return null;
  }
  if (colCount === 8 && patch.colWidths8) {
    return patch.colWidths8;
  }
  if (patch.colWidthsByCount?.[colCount]) {
    return patch.colWidthsByCount[colCount];
  }
  if (patch.firstColShare != null && colCount >= 2) {
    const widths = equalColWidths(colCount);
    widths[0] = patch.firstColShare;
    return widths;
  }
  if (patch.colWidthsEqual && colCount >= 2) {
    return equalColWidths(colCount);
  }
  return null;
}

function mergeCellAttrs(cell: JsonNode, patch: CellPatch): boolean {
  const attrs = { ...(cell.attrs ?? {}) };
  let changed = false;
  const assign = (key: string, value: unknown) => {
    if (value === undefined) {
      return;
    }
    if (JSON.stringify(attrs[key]) === JSON.stringify(value)) {
      return;
    }
    attrs[key] = value;
    changed = true;
  };
  assign('minHeightMm', patch.minHeightMm);
  assign('paddingMm', patch.paddingMm);
  assign('align', patch.align);
  assign('valign', patch.valign);
  assign('wrap', patch.wrap);
  assign('nowrap', patch.nowrap);
  assign('bareValue', patch.bareValue);
  assign('fillRemainShare', patch.fillRemainShare);
  if (changed) {
    cell.attrs = attrs;
  }
  return changed;
}

function firstRowLabel(row: JsonNode): string {
  for (const cell of row.content ?? []) {
    const text = collectText(cell).trim();
    if (text) {
      return text;
    }
  }
  return '';
}

function isBloodTypePaperBoxes(text: string): boolean {
  return /[ABO]\s*□/.test(text) || /□\s*[ABO]型/.test(text);
}

function patchConsultPurposeName(table: JsonNode): number {
  let n = 0;
  for (const row of table.content ?? []) {
    if (row.type !== 'tableRow') {
      continue;
    }
    const label = firstRowLabel(row);
    if (!label.includes('申请会诊目的') && !label.includes('会诊目的')) {
      continue;
    }
    for (const cell of row.content ?? []) {
      if (cell.attrs?.name === 'midBody') {
        cell.attrs = { ...cell.attrs, name: 'purposeBody' };
        n += 1;
      }
    }
  }
  return n;
}

function tableHasRole(table: JsonNode, role: string): boolean {
  for (const row of table.content ?? []) {
    for (const cell of row.content ?? []) {
      if (cell.attrs?.name === role) {
        return true;
      }
    }
  }
  return false;
}

function patchNamedTable(table: JsonNode): boolean {
  const name = String(table.attrs?.name ?? '');
  const preset = TABLE_PATCH[name];
  if (!preset) {
    return false;
  }
  let changed = false;
  if (name === 'consultRequestForm') {
    if (patchConsultPurposeName(table) > 0) {
      changed = true;
    }
  }
  const colCount = tableColCount(table);
  const widths = resolveColWidths(preset, colCount);
  const nextAttrs: Record<string, unknown> = { ...(table.attrs ?? {}) };
  if (widths) {
    if (JSON.stringify(nextAttrs.colWidths) !== JSON.stringify(widths)) {
      nextAttrs.colWidths = widths;
      changed = true;
    }
  }
  if (preset.border === false && nextAttrs.border !== false) {
    nextAttrs.border = false;
    changed = true;
  }
  if (preset.splitLate === true && nextAttrs.splitLate !== true) {
    nextAttrs.splitLate = true;
    changed = true;
  }
  table.attrs = nextAttrs;

  const byName = name === 'consultRequestForm' ? CONSULT_CELL_BY_NAME : CELL_BY_NAME;
  for (const row of table.content ?? []) {
    if (row.type !== 'tableRow') {
      continue;
    }
    const rowLabel = firstRowLabel(row);
    for (const cell of row.content ?? []) {
      if (cell.type !== 'tableCell' && cell.type !== 'tableHeader') {
        continue;
      }
      const cellName = String(cell.attrs?.name ?? '');
      let patch = byName[cellName];
      if (name === 'riskForm') {
        patch = riskCellPatch(cellName, rowLabel);
      }
      if (name === 'patientHeader') {
        const outpatient = tableHasRole(table, 'patientField');
        patch = outpatient
          ? {
              align: 'left',
              valign: 'middle',
              nowrap: true,
              wrap: false,
              paddingMm: [1, 2.1, 1, 2.1],
            }
          : {
              align: 'left',
              valign: 'middle',
              nowrap: true,
              bareValue: true,
              wrap: false,
              paddingMm: [1, 2.1, 1, 2.1],
            };
      }
      if (name === 'outpatientVitals') {
        patch = { align: 'left', valign: 'middle', nowrap: true };
      }
      if (name === 'relativeCardiacBorder') {
        patch = {
          align: 'center',
          valign: 'middle',
          wrap: false,
          paddingMm: [1, 2.8, 1, 2.8],
        };
      }
      if (name === 'dischargeDiagnosisForm' && cellName === 'tallBody') {
        patch = { ...CELL_BY_NAME.tallBody };
      }
      if (patch && mergeCellAttrs(cell, patch)) {
        changed = true;
      }
    }
  }
  return changed;
}

/**
 * 风险评估格：行高写入 minHeightMm，不再靠打印时猜汉字。
 */
function riskCellPatch(cellName: string, rowLabel: string): CellPatch {
  let height = 10;
  for (const rule of RISK_HEIGHT_BY_LABEL) {
    if (rule.match(rowLabel)) {
      height = rule.height;
      break;
    }
  }
  const isLabel = cellName === 'formLabel';
  return {
    minHeightMm: height,
    paddingMm: 2.5,
    wrap: true,
    align: isLabel ? 'center' : 'left',
    valign: isLabel || height <= 18 ? 'middle' : 'top',
  };
}

function makeCheckbox(fieldKey: string, label: string): JsonNode {
  return {
    type: 'emrCheckboxField',
    attrs: { fieldKey, label, checked: false },
  };
}

function makeText(text: string): JsonNode {
  return { type: 'text', text };
}

/**
 * 把「□一般　□病重」拆成 emrCheckboxField。血型 A□ 不转。
 */
function convertPaperCheckboxes(doc: JsonNode): number {
  let converted = 0;
  walk(doc, (node) => {
    if (node.type !== 'paragraph' || !node.content) {
      return;
    }
    const full = collectText(node);
    if (!full.includes('□')) {
      return;
    }
    if (isBloodTypePaperBoxes(full)) {
      return;
    }
    if (node.content.some((c) => c.type === 'emrCheckboxField')) {
      return;
    }
    const group = CHECKBOX_GROUPS.find((g) => g.labels.every((label) => full.includes(`□${label}`) || full.includes(`□ ${label}`)));
    if (!group) {
      return;
    }
    const next: JsonNode[] = [];
    for (const child of node.content) {
      if (child.type !== 'text' || typeof child.text !== 'string') {
        next.push(child);
        continue;
      }
      let rest = child.text;
      const pieces: JsonNode[] = [];
      while (rest.length > 0) {
        let hit: { index: number; label: string } | null = null;
        for (const label of group.labels) {
          const token = `□${label}`;
          const alt = `□ ${label}`;
          const i1 = rest.indexOf(token);
          const i2 = rest.indexOf(alt);
          let index = -1;
          if (i1 >= 0) {
            index = i1;
          }
          if (i2 >= 0 && (index < 0 || i2 < index)) {
            index = i2;
          }
          if (index >= 0 && (hit == null || index < hit.index)) {
            hit = { index, label };
          }
        }
        if (!hit) {
          if (rest) {
            pieces.push({ ...child, text: rest });
          }
          break;
        }
        const before = rest.slice(0, hit.index);
        if (before) {
          pieces.push({ ...child, text: before });
        }
        pieces.push(makeCheckbox(group.fieldKey, hit.label));
        converted += 1;
        const remain = rest.slice(hit.index);
        const spaced = `□ ${hit.label}`;
        let consume = `□${hit.label}`.length;
        if (remain.startsWith(spaced)) {
          consume = spaced.length;
        }
        rest = rest.slice(hit.index + consume);
      }
      next.push(...pieces);
    }
    node.content = next;
  });
  return converted;
}

function stripEmptyGradeIslands(doc: JsonNode): number {
  let n = 0;
  walk(doc, (node) => {
    if (node.type !== 'paragraph' || !node.content) {
      return;
    }
    const before = node.content.length;
    node.content = node.content.filter((child) => {
      if (child.type !== 'emrFreeText' && child.type !== 'emrDataElement') {
        return true;
      }
      const ph = String(child.attrs?.placeholder ?? '');
      const label = String(child.attrs?.label ?? '');
      const hit =
        ph.includes('评估等级') ||
        ph.includes('护理等级') ||
        label.includes('评估等级') ||
        label.includes('护理等级');
      if (!hit) {
        return true;
      }
      const value = String(child.attrs?.value ?? '').trim();
      if (value) {
        return true;
      }
      n += 1;
      return false;
    });
    if (node.content.length !== before && node.content.length === 0) {
      node.content = [makeText('')];
    }
  });
  return n;
}

function makeChip(slotId: string, fieldKey: string, label: string): JsonNode {
  return {
    type: 'emrMetadataChip',
    attrs: {
      slotId,
      fieldKey,
      label,
      value: '',
      editable: false,
    },
  };
}

/**
 * 入院 patientHeader：姓名后补病房、床位（B12）。已有则跳过。
 */
function ensureAdmissionWardBed(doc: JsonNode): number {
  let n = 0;
  walk(doc, (table) => {
    if (table.type !== 'table' || table.attrs?.name !== 'patientHeader') {
      return;
    }
    const all = collectText(table);
    if (all.includes('病房') && (all.includes('床位') || all.includes('床号'))) {
      return;
    }
    const firstRow = table.content?.find((row) => row.type === 'tableRow');
    const firstCell = firstRow?.content?.[0];
    if (!firstCell?.content?.[0]) {
      return;
    }
    const para = firstCell.content[0];
    if (para.type !== 'paragraph' || !para.content) {
      return;
    }
    const paraText = collectText(para);
    if (!paraText.includes('姓名')) {
      return;
    }
    para.content.push(makeText('　病房：'));
    para.content.push(makeChip('adm-ward', 'wardName', '病房'));
    para.content.push(makeText('　床位：'));
    para.content.push(makeChip('adm-bed', 'bedNo', '床位'));
    n += 1;
  });
  return n;
}

function findLogoSrc(doc: JsonNode): string | null {
  const raw = JSON.stringify(doc);
  if (hasHospitalLogoInContent(raw)) {
    return extractChromeLogoSrc(raw);
  }
  return null;
}

/** 从页眉 template 或正文 JSON 提取院徽 src */
function extractChromeLogoSrc(raw: string): string | null {
  const s = String(raw ?? '');
  const fromToken = /\{\{emrImg(?:\s+(?:w|x|y)=\d+)*:([^}]+)\}\}/.exec(s);
  if (fromToken?.[1]?.trim()) {
    return fromToken[1].trim();
  }
  const fromBody = /"title"\s*:\s*"hospitalLogo"[\s\S]*?"src"\s*:\s*"([^"]+)"/.exec(s);
  return fromBody?.[1]?.trim() ?? null;
}

function hasHospitalLogoInContent(raw: string): boolean {
  const s = String(raw ?? '');
  return (
    /\{\{emrImg[\s\S]*?:[^}]+\}\}/.test(s) ||
    /"title"\s*:\s*"hospitalLogo"/.test(s) ||
    /hospitalLogo/.test(s)
  );
}

/**
 * 页眉缺院徽时把 {{emrImg}} 插到院名前面。会诊页眉不补患者 float 行。
 */
function ensureChromeLogo(doc: JsonNode & { emrPageChrome?: { header?: { template?: string } } }, logoSrc: string): boolean {
  const chrome = doc.emrPageChrome;
  if (!chrome?.header) {
    return false;
  }
  const template = String(chrome.header.template ?? '');
  if (hasHospitalLogoInContent(template) || hasHospitalLogoInContent(JSON.stringify(doc))) {
    if (extractChromeLogoSrc(template)) {
      return false;
    }
  }
  if (!logoSrc) {
    return false;
  }
  if (template.includes('{{emrImg')) {
    return false;
  }
  const img = `{{emrImg w=39 x=0 y=0:${logoSrc}}}`;
  chrome.header.template = `${img}${template}`;
  return true;
}

function labelHits(label: string, names: readonly string[]): boolean {
  return names.some((name) => label === name || label.startsWith(`${name}-`) || label.startsWith(`${name} `));
}

function brushLabel(templateName: string, title: string): string {
  const names = [...LOGO_TEMPLATE_NAMES, ...CHECKBOX_TEMPLATE_NAMES, '入院记录'];
  const fromTemplate = templateName.trim();
  const fromTitle = title.trim();
  if (labelHits(fromTemplate, names)) {
    return fromTemplate;
  }
  if (labelHits(fromTitle, names)) {
    return fromTitle;
  }
  return fromTemplate || fromTitle;
}

export function applyPrintLayoutProtocol(
  doc: JsonNode,
  opts?: { logoSrc?: string | null; templateName?: string; withLogo?: boolean },
): {
  doc: JsonNode;
  report: Report;
} {
  const next = structuredClone(doc) as JsonNode;
  const report: Report = { tables: 0, checkboxes: 0, wardBed: 0, logo: 0 };
  walk(next, (node) => {
    if (node.type === 'table' && patchNamedTable(node)) {
      report.tables += 1;
    }
  });
  const templateName = opts?.templateName ?? '';
  const checkboxLabel = labelHits(templateName, [...CHECKBOX_TEMPLATE_NAMES]);
  const paperHint = collectText(next);
  if (
    checkboxLabel ||
    paperHint.includes('□一般') ||
    paperHint.includes('□特级护理') ||
    paperHint.includes('□同意')
  ) {
    report.checkboxes += convertPaperCheckboxes(next);
    stripEmptyGradeIslands(next);
  }
  if (labelHits(templateName, ['入院记录'])) {
    report.wardBed += ensureAdmissionWardBed(next);
  }
  if (opts?.withLogo) {
    const logo = opts.logoSrc ?? findLogoSrc(next);
    if (logo && ensureChromeLogo(next as JsonNode & { emrPageChrome?: { header?: { template?: string } } }, logo)) {
      report.logo += 1;
    }
  }
  return { doc: next, report };
}

function sameJson(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

function asDoc(raw: unknown): JsonNode {
  if (!raw) {
    throw new Error('content 为空');
  }
  if (typeof raw === 'string') {
    return JSON.parse(raw) as JsonNode;
  }
  return raw as JsonNode;
}

type BrushRow = { content: unknown; id: string; label: string; withLogo: boolean };

async function brushRows(
  client: PgClient,
  table: 'emr_document' | 'emr_template',
  rows: BrushRow[],
  donorLogo: string | null,
): Promise<number> {
  let changedCount = 0;
  let printed = 0;
  for (const row of rows) {
    const raw = asDoc(row.content);
    const { doc, report } = applyPrintLayoutProtocol(raw, {
      logoSrc: donorLogo,
      templateName: row.label,
      withLogo: row.withLogo,
    });
    if (sameJson(raw, doc)) {
      continue;
    }
    changedCount += 1;
    if (printed < 40) {
      printed += 1;
      console.log(
        '*',
        table,
        row.id,
        row.label,
        'tables',
        report.tables,
        'cb',
        report.checkboxes,
        'ward',
        report.wardBed,
        'logo',
        report.logo,
      );
    }
    if (APPLY) {
      await client.query(`UPDATE ${table} SET content = $1::jsonb, updated_at = NOW() WHERE id = $2::bigint`, [
        JSON.stringify(doc),
        row.id,
      ]);
    }
  }
  if (changedCount > printed) {
    console.log('...', table, '其余', changedCount - printed, '条未逐条打印');
  }
  return changedCount;
}

async function main(): Promise<void> {
  const client = new Client({
    host: process.env.PRO_DB_HOST ?? '172.16.101.2',
    port: Number(process.env.PRO_DB_PORT ?? 5432),
    user: process.env.PRO_DB_USER ?? 'postgres',
    password: process.env.PRO_DB_PASSWORD ?? 'root',
    database: DB_NAME,
  });
  await client.connect();
  console.log('db', DB_NAME, APPLY ? 'APPLY' : 'DRY-RUN', TEMPLATES_ONLY ? 'templates-only' : 'templates+documents');

  const tpls = await client.query(
    `SELECT id::text, name, content FROM emr_template ORDER BY name, id`,
  );
  console.log('templates', tpls.rows.length);

  let donorLogo: string | null = null;
  for (const row of tpls.rows) {
    if (!labelHits(String(row.name), LOGO_TEMPLATE_NAMES)) {
      continue;
    }
    const logo = findLogoSrc(asDoc(row.content));
    if (logo) {
      donorLogo = logo;
      break;
    }
  }

  if (APPLY) {
    await client.query('BEGIN');
  }
  const templateRows: BrushRow[] = tpls.rows.map((row) => ({
    id: String(row.id),
    label: String(row.name),
    content: row.content,
    withLogo: labelHits(String(row.name), LOGO_TEMPLATE_NAMES),
  }));
  const templateChanged = await brushRows(client, 'emr_template', templateRows, donorLogo);

  let documentChanged = 0;
  if (!TEMPLATES_ONLY) {
    const docs = await client.query(
      `SELECT d.id::text, d.title, coalesce(t.name, '') AS template_name, d.content
       FROM emr_document d
       LEFT JOIN emr_template t ON t.id = d.source_template_id
       WHERE d.is_deleted = false
       ORDER BY d.id`,
    );
    console.log('documents', docs.rows.length);
    const documentRows: BrushRow[] = docs.rows.map((row) => ({
      id: String(row.id),
      label: brushLabel(String(row.template_name || ''), String(row.title || '')),
      content: row.content,
      withLogo: false,
    }));
    documentChanged = await brushRows(client, 'emr_document', documentRows, null);
  }
  if (APPLY) {
    await client.query('COMMIT');
  }
  console.log(APPLY ? 'templates updated' : 'templates would-update', templateChanged);
  console.log(APPLY ? 'documents updated' : 'documents would-update', documentChanged);
  await client.end();
}

const isDirectRun = process.argv[1]?.includes('apply-print-layout-protocol');
if (isDirectRun) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
