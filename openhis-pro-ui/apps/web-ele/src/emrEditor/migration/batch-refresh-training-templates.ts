/**
 * 列出 / 批量刷 whale-training.emr_template（排除门诊病历）
 *
 * 用法：
 *   npx tsx src/emrEditor/migration/batch-refresh-training-templates.ts --list
 *   npx tsx src/emrEditor/migration/batch-refresh-training-templates.ts --apply
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';
import pg from 'pg';

import { adaptTemplateToPlanBNodes } from './adapt-template-to-plan-b-nodes';
import { migrateEmrTemplate } from './migrate-emr-template';
import { refreshTemplateSkillP0 } from './refresh-template-skill-p0';

const { Client } = pg;

const DB = {
  host: process.env.TRAINING_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.TRAINING_DB_PORT ?? 5432),
  database: process.env.TRAINING_DB_NAME ?? 'whale-training',
  user: process.env.TRAINING_DB_USER ?? 'postgres',
  password: process.env.TRAINING_DB_PASSWORD ?? 'root',
};

/**
 * 截图 = 文书类型列表（排除门诊病历）。
 * 刷写这些 document_type_code 下**全部 scope**（HOSPITAL / DEPARTMENT / PERSONAL）。
 * 上次漏刷 PERSONAL，导致医生站仍打开旧「首次病程」结构。
 */
const TARGET_DOCUMENT_TYPE_CODES = [
  'PROGRESS_NOTE',
  'ADMISSION_RECORD',
  'DISCHARGE_SUMMARY',
  'NOTICE',
  'TWENTY_FOUR_HOUR_RECORD',
  'SURGERY_HANDOVER',
  'PREOP_SUMMARY',
  'PREOP_DISCUSSION',
  'PASTE_SHEET',
];

type TemplateRow = {
  id: string;
  name: string;
  code: string;
  document_type_code: string | null;
  scope_level: string | null;
  content: string | null;
  updated_at: Date | null;
};

function isTargetTemplate(row: TemplateRow): boolean {
  return TARGET_DOCUMENT_TYPE_CODES.includes(String(row.document_type_code ?? ''));
}

function parseContent(raw: string | null): unknown {
  if (!raw || !String(raw).trim()) {
    throw new Error('content 为空');
  }
  const text = String(raw).trim();
  return JSON.parse(text) as unknown;
}

function migrateOne(raw: unknown): {
  doc: JSONContent;
  p0: ReturnType<typeof refreshTemplateSkillP0>['report'];
  adapt: ReturnType<typeof adaptTemplateToPlanBNodes>['report'];
  migrate: ReturnType<typeof migrateEmrTemplate>['report'];
} {
  // 1) Skill P0：假页眉已在 refresh 内处理的部分 + 控件/占位/§A4
  // 结构假页眉由 ensurePageChromeP0 兜底
  let doc = ensurePageChromeP0(structuredClone(raw) as JSONContent & Record<string, unknown>);
  const p0 = refreshTemplateSkillP0(doc);
  doc = p0.doc as JSONContent & Record<string, unknown>;
  const adapt = adaptTemplateToPlanBNodes(doc);
  doc = adapt.doc as JSONContent & Record<string, unknown>;
  // adapt 后可能把系统可取项转成 freeText；再跑一遍 P0 纠 §A4
  const p0b = refreshTemplateSkillP0(doc);
  doc = p0b.doc as JSONContent & Record<string, unknown>;
  const migrate = migrateEmrTemplate(doc);
  return {
    doc: migrate.doc,
    p0: {
      chipToDate: p0.report.chipToDate + p0b.report.chipToDate,
      freeTextToNumber: p0.report.freeTextToNumber + p0b.report.freeTextToNumber,
      freeTextToChip: p0.report.freeTextToChip + p0b.report.freeTextToChip,
      dataElementToChip: p0.report.dataElementToChip + p0b.report.dataElementToChip,
      placeholdersFixed: p0.report.placeholdersFixed + p0b.report.placeholdersFixed,
    },
    adapt: adapt.report,
    migrate: migrate.report,
  };
}

/** 最小结构 P0：抽出 hospitalTitle 进侧车，删除假页眉块 */
function ensurePageChromeP0(doc: JSONContent & Record<string, unknown>): JSONContent & Record<string, unknown> {
  const content = Array.isArray(doc.content) ? [...doc.content] : [];

  function walkFindLogo(nodes: JSONContent[] | undefined): string | null {
    for (const n of nodes ?? []) {
      if (n.type === 'image' && n.attrs?.title === 'hospitalLogo') {
        return String(n.attrs.src ?? '');
      }
      const nested = walkFindLogo(n.content);
      if (nested) return nested;
    }
    return null;
  }

  function collectFieldIds(node: JSONContent, ids: string[]): void {
    if (node.type === 'emrDataElement') ids.push(String(node.attrs?.elementId ?? ''));
    if (node.type === 'emrMetadataChip') ids.push(String(node.attrs?.fieldKey ?? ''));
    for (const c of node.content ?? []) collectFieldIds(c, ids);
  }

  /** 居中段且含院徽 / 机构名 → 视为假页眉（即使无 name） */
  function isLikelyHospitalTitle(n: JSONContent): boolean {
    if (n.type !== 'paragraph') return false;
    if (String(n.attrs?.name ?? '') === 'hospitalTitle') return true;
    if (String(n.attrs?.textAlign ?? '') !== 'center') return false;
    let hasLogo = false;
    let hasOrg = false;
    const walk = (node: JSONContent) => {
      if (node.type === 'image' && node.attrs?.title === 'hospitalLogo') hasLogo = true;
      if (node.type === 'emrDataElement' && node.attrs?.elementId === 'orgName') hasOrg = true;
      if (node.type === 'emrMetadataChip' && node.attrs?.fieldKey === 'orgName') hasOrg = true;
      for (const c of node.content ?? []) walk(c);
    };
    walk(n);
    return hasLogo || hasOrg;
  }

  function isLikelyFormTitle(n: JSONContent): boolean {
    if (String(n.attrs?.name ?? '') === 'formTitle') return true;
    if (n.type !== 'paragraph') return false;
    if (String(n.attrs?.textAlign ?? '') !== 'center') return false;
    const texts: string[] = [];
    const walk = (node: JSONContent) => {
      if (node.type === 'text') texts.push(String(node.text ?? ''));
      for (const c of node.content ?? []) walk(c);
    };
    walk(n);
    const t = texts.join('').replace(/\s+/g, '');
    return /记录|小结|告知|知情|讨论|交接|粘贴/.test(t) && t.length <= 20;
  }

  const logoSrc = walkFindLogo(content);

  // 1) 删假页眉（有 name 或启发式）
  doc.content = content.filter((n) => {
    const name = String(n.attrs?.name ?? '');
    if (name === 'hospitalTitle' || name === 'formTitle' || name === 'pageHeader') return false;
    if (isLikelyHospitalTitle(n) || isLikelyFormTitle(n)) return false;
    return true;
  });

  // 2) patientHeader / patientBar：始终去掉与页眉重复的姓名、住院号
  doc.content = (doc.content as JSONContent[]).map((n) => {
    if (n.type !== 'table') return n;
    const tname = String(n.attrs?.name ?? '');
    if (tname !== 'patientHeader' && tname !== 'patientBar') return n;
    const rows = (n.content ?? []).map((row) => {
      if (row.type !== 'tableRow') return row;
      const cells = (row.content ?? []).filter((cell) => {
        const ids: string[] = [];
        collectFieldIds(cell, ids);
        return !ids.includes('patientName') && !ids.includes('inpatientNo');
      });
      return { ...row, content: cells };
    });
    return { ...n, content: rows };
  });

  const chrome = (doc.emrPageChrome ?? {}) as Record<string, unknown>;
  const header = (chrome.header ?? {}) as Record<string, unknown>;
  const existingTpl = String(header.template ?? '');
  const needChrome =
    !existingTpl ||
    !existingTpl.includes('{documentTitle}') ||
    !existingTpl.includes('float:left') ||
    !existingTpl.includes('{wardName}') ||
    !existingTpl.includes('{bedNo}') ||
    (logoSrc && !existingTpl.includes('emrImg'));

  if (needChrome) {
    const imgPart = logoSrc
      ? `{{emrImg w=39:${logoSrc}}}`
      : existingTpl.match(/\{\{emrImg[^}]+\}\}/)?.[0] ?? '';
    const nl = '\n';
    header.template =
      `${imgPart}<span style="font-size:22pt;font-weight:700">{orgName}</span>${nl}` +
      `<span style="font-size:16pt">{documentTitle}</span>${nl}` +
      `<span style="font-size:12pt;float:left">姓名：{patientName}　病房：{wardName}　床位：{bedNo}</span>` +
      `<span style="font-size:12pt;float:right">住院号：{inpatientNo}</span>` +
      `<span style="display:block;clear:both;height:0"></span>`;
  }
  header.enabled = true;
  header.align = header.align ?? 'center';
  header.heightMm = 0;
  header.bold = header.bold ?? false;
  header.italic = header.italic ?? false;
  header.underline = header.underline ?? false;
  chrome.header = header;

  const footer = (chrome.footer ?? {}) as Record<string, unknown>;
  footer.enabled = footer.enabled ?? true;
  footer.align = footer.align ?? 'center';
  footer.heightMm = footer.heightMm ?? 12;
  footer.template = footer.template || '第 {page} 页 (共 {total} 页)';
  footer.bold = footer.bold ?? false;
  footer.italic = footer.italic ?? false;
  footer.underline = footer.underline ?? false;
  chrome.footer = footer;
  doc.emrPageChrome = chrome;

  if (!doc.emrPageMargins) {
    doc.emrPageMargins = {
      top: '25.4mm',
      right: '31.8mm',
      bottom: '25.4mm',
      left: '31.8mm',
    };
  }

  return doc;
}

async function listTemplates(client: pg.Client): Promise<TemplateRow[]> {
  const sql = `
    SELECT id::text AS id, name, code, document_type_code, scope_level, content, updated_at
    FROM emr_template
    WHERE COALESCE(delete_flag, false) = false
    ORDER BY document_type_code, name
  `;
  try {
    const res = await client.query<TemplateRow>(sql);
    return res.rows;
  } catch {
    const res = await client.query<TemplateRow>(`
      SELECT id::text AS id, name, code, document_type_code, scope_level, content, updated_at
      FROM emr_template
      ORDER BY document_type_code, name
    `);
    return res.rows;
  }
}

async function main() {
  const mode = process.argv.includes('--apply') ? 'apply' : 'list';
  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);

  const all = await listTemplates(client);
  console.log(`total templates: ${all.length}`);
  const targets = all.filter((r) => isTargetTemplate(r));
  for (const r of targets) {
    console.log(
      `  ✓ ${r.name} | ${r.code} | type=${r.document_type_code ?? ''} | content=${r.content?.length ?? 0}`,
    );
  }
  console.log(`\ntargets (all scopes, exclude OUTPATIENT_RECORD): ${targets.length}`);

  if (mode === 'list') {
    await client.end();
    return;
  }

  const backupDir = resolve('src/emrEditor/migration/_training-backup');
  mkdirSync(backupDir, { recursive: true });

  let ok = 0;
  let fail = 0;
  for (const row of targets) {
    const backupPath = resolve(backupDir, `${row.code || row.id}.before.json`);
    try {
      writeFileSync(backupPath, row.content ?? '', 'utf8');
      const raw = parseContent(row.content);
      const result = migrateOne(raw);
      const nextContent = JSON.stringify(result.doc);
      await client.query(
        `UPDATE emr_template SET content = $1, updated_at = NOW() WHERE id = $2::bigint`,
        [nextContent, row.id],
      );
      const outPath = resolve(backupDir, `${row.code || row.id}.after.json`);
      writeFileSync(outPath, `${JSON.stringify(result.doc, null, 2)}\n`, 'utf8');
      ok += 1;
      console.log(
        `✓ ${row.name} (${row.code}) fillable=${result.migrate.fillableNodeCount}` +
          ` p0.chip=${result.p0.freeTextToChip}+${result.p0.dataElementToChip}` +
          ` date=${result.p0.chipToDate} ph=${result.p0.placeholdersFixed}` +
          ` warn=${result.migrate.warnings.length}`,
      );
    } catch (e) {
      fail += 1;
      console.error(`✗ ${row.name} (${row.code}):`, e instanceof Error ? e.message : e);
    }
  }

  await client.end();
  console.log(`\ndone: ok=${ok} fail=${fail} backup=${backupDir}`);
  if (fail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
