/**
 * 按 emr-template-migration Skill 验收 whale-pro 刷后结果
 *
 *   npx tsx src/emrEditor/migration/audit-pro-emr-skill.ts
 */
import pg from 'pg';

import type { JSONContent } from '@tiptap/core';

import { adaptTemplateToPlanBNodes } from './adapt-template-to-plan-b-nodes';
import { ensurePageChromeP0, isEmrContentDirty } from './ensure-page-chrome-p0';
import { migrateEmrTemplate } from './migrate-emr-template';
import { rebuildOutpatientFromRaw } from './outpatient-clean-doc';
import { refreshTemplateSkillP0 } from './refresh-template-skill-p0';

const { Client } = pg;

const DB = {
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  database: process.env.PRO_DB_NAME ?? 'whale-pro',
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
};

const OUTPATIENT = new Set(['OUTPATIENT_RECORD', 'OUTPATIENT_EMR']);

/** Skill §A4 常见中文 → 不得仍是 emrFreeText */
const A4_LABELS = [
  '姓名',
  '患者姓名',
  '性别',
  '年龄',
  '电话',
  '联系电话',
  '身份证号',
  '证件号',
  '病历号',
  '住院号',
  '就诊号',
  '病房',
  '病区',
  '床位',
  '床号',
  '费别',
  '医保',
  '费用性质',
  '职业',
  '民族',
  '婚姻',
  '婚姻状况',
  '出生地',
  '籍贯',
  '入院时间',
  '入院日期时间',
  '记录时间',
  '病史陈述者',
  '可靠程度',
  '节气',
  '医师',
  '书写医师',
  '主治医师',
  '科室',
  '医院名称',
];

type Issue = { kind: string; id: string; name: string; detail: string };

function walk(node: JSONContent | undefined, visit: (n: JSONContent) => void) {
  if (!node || typeof node !== 'object') return;
  visit(node);
  for (const c of node.content ?? []) walk(c, visit);
}

function auditDoc(
  kind: 'tpl' | 'doc',
  id: string,
  name: string,
  typeCode: string,
  contentRaw: string | null,
): Issue[] {
  const issues: Issue[] = [];
  const push = (detail: string) => issues.push({ kind, id, name, detail });

  if (!contentRaw || !String(contentRaw).trim()) {
    push('content 为空');
    return issues;
  }

  const text = String(contentRaw);
  if (isEmrContentDirty(text, typeCode)) {
    push('isEmrContentDirty=true（假页眉/侧车/页眉占位不合规）');
  }

  let root: JSONContent & Record<string, unknown>;
  try {
    root = JSON.parse(text) as JSONContent & Record<string, unknown>;
  } catch {
    push('content 不是合法 JSON');
    return issues;
  }

  const isOp = OUTPATIENT.has(typeCode);
  const chrome = (root.emrPageChrome ?? {}) as Record<string, unknown>;
  const header = (chrome.header ?? {}) as Record<string, unknown>;
  const footer = (chrome.footer ?? {}) as Record<string, unknown>;
  const tpl = String(header.template ?? '');

  if (!root.emrPageChrome) push('缺少 emrPageChrome');
  if (!root.emrPageMargins) push('缺少 emrPageMargins');
  if (header.enabled === false) push('header.enabled=false');
  if (Number(header.heightMm ?? -1) !== 0) push(`header.heightMm=${String(header.heightMm)}（Skill 要求 0）`);
  if (!footer.template || !String(footer.template).includes('{page}')) push('footer 缺 {page}');

  if (isOp) {
    if (tpl.includes('{inpatientNo}') || tpl.includes('住院号')) push('门诊页眉仍含住院号');
    if (tpl.includes('float:left') || tpl.includes('{wardName}') || tpl.includes('{bedNo}')) {
      push('门诊页眉误用住院 float/病房床位');
    }
    if (tpl.includes('{documentTitle}')) push('门诊页眉仍用 {documentTitle}（应写死文书标题文案）');
  } else {
    if (!tpl.includes('{documentTitle}')) push('住院页眉缺 {documentTitle}');
    if (!tpl.includes('float:left')) push('住院页眉缺 float:left');
    if (!tpl.includes('{wardName}')) push('住院页眉缺 {wardName}');
    if (!tpl.includes('{bedNo}')) push('住院页眉缺 {bedNo}');
    if (!tpl.includes('{inpatientNo}')) push('住院页眉缺 {inpatientNo}');
    if (!tpl.includes('{patientName}')) push('住院页眉缺 {patientName}');
  }

  let fakeHeader = 0;
  let fillableMark = 0;
  let barePlaceholder = 0;
  let a4FreeText = 0;
  let atomWithContent = 0;
  let missingSlotId = 0;
  let breakThenFake = 0;
  let hospitalTitleCount = 0;

  const top = Array.isArray(root.content) ? root.content : [];
  for (let i = 0; i < top.length; i++) {
    const n = top[i]!;
    const nm = String(n.attrs?.name ?? '');
    if (nm === 'hospitalTitle' || nm === 'formTitle' || nm === 'pageHeader') fakeHeader += 1;
    if (nm === 'hospitalTitle') hospitalTitleCount += 1;
    if (n.type === 'emrPageBreak') {
      for (let j = i + 1; j < Math.min(i + 6, top.length); j++) {
        const nn = String(top[j]?.attrs?.name ?? '');
        if (nn === 'hospitalTitle' || nn === 'formTitle' || nn === 'pageHeader') breakThenFake += 1;
      }
    }
  }

  walk(root, (n) => {
    if (n.marks?.some((m) => m.type === 'emrFillable')) fillableMark += 1;

    const typ = n.type;
    if (typ === 'emrFreeText' || typ === 'emrMetadataChip' || typ === 'emrDataElement') {
      if (!n.attrs?.slotId) missingSlotId += 1;
      if (Array.isArray(n.content) && n.content.length > 0) atomWithContent += 1;
    }

    if (typ === 'emrFreeText') {
      const ph = String(n.attrs?.placeholder ?? '').trim();
      const label = String(n.attrs?.label ?? '').trim();
      if (!ph || ph === '填写' || ph === label) barePlaceholder += 1;
      const lab = label.replace(/[：:]\s*$/, '');
      // 精确命中 §A4 中文表（避免「患者入院…」误伤）
      if (A4_LABELS.includes(lab)) a4FreeText += 1;
    }
  });

  if (fakeHeader) push(`正文仍有假页眉块 name×${fakeHeader}`);
  if (hospitalTitleCount > 1) push(`hospitalTitle 出现 ${hospitalTitleCount} 次`);
  if (breakThenFake) push(`emrPageBreak 后仍有假页头 ×${breakThenFake}`);
  if (fillableMark) push(`仍有 emrFillable mark ×${fillableMark}`);
  if (barePlaceholder) push(`placeholder 裸写/与 label 相同 ×${barePlaceholder}`);
  if (a4FreeText) push(`§A4 系统可取仍为 emrFreeText ×${a4FreeText}`);
  if (atomWithContent) push(`atom 节点仍带 content[] ×${atomWithContent}`);
  if (missingSlotId > 0 && kind === 'tpl') push(`缺 slotId ×${missingSlotId}`);

  // 幂等：再跑一遍管线，结构关键字段应无 dirty
  try {
    let next: JSONContent;
    if (isOp) {
      next = rebuildOutpatientFromRaw(structuredClone(root) as JSONContent);
    } else {
      let doc = ensurePageChromeP0(structuredClone(root) as JSONContent & Record<string, unknown>, {
        documentTypeCode: typeCode,
      });
      doc = refreshTemplateSkillP0(doc).doc as JSONContent & Record<string, unknown>;
      doc = adaptTemplateToPlanBNodes(doc).doc as JSONContent & Record<string, unknown>;
      doc = refreshTemplateSkillP0(doc).doc as JSONContent & Record<string, unknown>;
      next = migrateEmrTemplate(doc).doc;
    }
    if (isEmrContentDirty(JSON.stringify(next), typeCode)) {
      push('二次迁移后仍 dirty（管线非幂等/未收敛）');
    }
  } catch (e) {
    push(`二次迁移失败: ${e instanceof Error ? e.message : String(e)}`);
  }

  return issues;
}

async function main() {
  const client = new Client(DB);
  await client.connect();
  console.log(`audit ${DB.host}/${DB.database}`);

  const tpls = await client.query<{
    id: string;
    name: string;
    code: string;
    document_type_code: string;
    content: string;
  }>(
    `SELECT id::text AS id, name, code, document_type_code, content
     FROM emr_template
     WHERE COALESCE(is_deleted, false) = false`,
  );
  const docs = await client.query<{
    id: string;
    title: string;
    document_type_code: string;
    content: string;
  }>(
    `SELECT id::text AS id, title, document_type_code, content
     FROM emr_document
     WHERE COALESCE(is_deleted, false) = false`,
  );

  const allIssues: Issue[] = [];
  let tplOk = 0;
  let docOk = 0;

  for (const r of tpls.rows) {
    const iss = auditDoc('tpl', r.id, `${r.name}(${r.code})`, r.document_type_code, r.content);
    if (iss.length === 0) tplOk += 1;
    else allIssues.push(...iss);
  }
  for (const r of docs.rows) {
    const iss = auditDoc('doc', r.id, String(r.title ?? r.id), r.document_type_code, r.content);
    if (iss.length === 0) docOk += 1;
    else allIssues.push(...iss);
  }

  // 汇总
  const byDetail = new Map<string, number>();
  for (const i of allIssues) {
    const key = `${i.kind}:${i.detail.replace(/×\d+/g, '×N')}`;
    byDetail.set(key, (byDetail.get(key) ?? 0) + 1);
  }

  console.log(`\ntemplates: ok=${tplOk}/${tpls.rows.length}`);
  console.log(`documents: ok=${docOk}/${docs.rows.length}`);
  console.log(`issues: ${allIssues.length}`);
  console.log('\n--- issue summary ---');
  for (const [k, n] of [...byDetail.entries()].sort((a, b) => b[1] - a[1])) {
    console.log(`  ${n}\t${k}`);
  }

  // 抽样打印前 30 条明细
  console.log('\n--- sample details (max 30) ---');
  for (const i of allIssues.slice(0, 30)) {
    console.log(`  [${i.kind}] ${i.name} | ${i.detail}`);
  }
  if (allIssues.length > 30) console.log(`  ... and ${allIssues.length - 30} more`);

  // 金标准对照：入院记录模版
  const adm = tpls.rows.find((r) => r.document_type_code === 'ADMISSION_RECORD');
  if (adm) {
    const root = JSON.parse(adm.content) as JSONContent & Record<string, unknown>;
    const header = ((root.emrPageChrome as any)?.header ?? {}) as Record<string, unknown>;
    console.log('\n--- ADMISSION_RECORD template chrome ---');
    console.log(`  name=${adm.name} code=${adm.code}`);
    console.log(`  heightMm=${header.heightMm}`);
    console.log(`  has ward/bed/float/docTitle=${[
      String(header.template).includes('{wardName}'),
      String(header.template).includes('{bedNo}'),
      String(header.template).includes('float:left'),
      String(header.template).includes('{documentTitle}'),
    ].join('/')}`);
    console.log(`  dirty=${isEmrContentDirty(adm.content, 'ADMISSION_RECORD')}`);
  }

  const opStd = tpls.rows.find((r) => r.code === 'OP_RECORD_STD');
  if (opStd) {
    console.log('\n--- OP_RECORD_STD chrome ---');
    const root = JSON.parse(opStd.content) as any;
    const tpl = String(root.emrPageChrome?.header?.template ?? '');
    console.log(`  heightMm=${root.emrPageChrome?.header?.heightMm}`);
    console.log(`  template snippet=${tpl.slice(0, 120).replace(/\n/g, '\\n')}...`);
    console.log(`  dirty=${isEmrContentDirty(opStd.content, 'OUTPATIENT_RECORD')}`);
  }

  await client.end();
  if (allIssues.length > 0) process.exit(2);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
