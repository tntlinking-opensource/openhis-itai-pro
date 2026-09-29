/**
 * whale-pro：住院模版/病历 — 带院徽的页眉刷成金标准（logo+院名+标题+float 患者行）
 *
 * 用法（apps/web-ele）：
 *   npx tsx src/emrEditor/migration/batch-refresh-pro-inpatient-chrome.ts --list
 *   npx tsx src/emrEditor/migration/batch-refresh-pro-inpatient-chrome.ts --apply
 *
 * 环境变量：PRO_DB_HOST / PRO_DB_PORT / PRO_DB_NAME / PRO_DB_USER / PRO_DB_PASSWORD
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';
import pg from 'pg';

import {
  cloneEmrPageChrome,
  createDefaultEmrPageChrome,
  extractChromeLogoSrc,
  hasHospitalLogoInContent,
  refreshInpatientChromeHeaderFormat,
  type EmrPageChrome,
} from '../components/simple-editor/emr-page-chrome';
import { ensurePageChromeP0 } from './ensure-page-chrome-p0';

const { Client } = pg;

const DB = {
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  database: process.env.PRO_DB_NAME ?? 'whale-pro',
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
};

const OUTPATIENT_TYPES = new Set(['OUTPATIENT_RECORD', 'OUTPATIENT_EMR']);

const INPATIENT_TYPES = [
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

function isOutpatient(code: string | null | undefined): boolean {
  return OUTPATIENT_TYPES.has(String(code ?? ''));
}

function walkFindLogo(nodes: JSONContent[] | undefined): string | null {
  for (const n of nodes ?? []) {
    if (n.type === 'image' && n.attrs?.title === 'hospitalLogo') {
      return String(n.attrs.src ?? '').trim() || null;
    }
    const nested = walkFindLogo(n.content);
    if (nested) return nested;
  }
  return null;
}

function resolveLogoSrc(doc: Record<string, unknown>): string | null {
  const chrome = (doc.emrPageChrome as EmrPageChrome | undefined) ?? createDefaultEmrPageChrome();
  const fromChrome = extractChromeLogoSrc(String(chrome.header?.template ?? ''));
  if (fromChrome) return fromChrome;
  const fromBody = walkFindLogo(Array.isArray(doc.content) ? doc.content : []);
  if (fromBody) return fromBody;
  return extractChromeLogoSrc(JSON.stringify(doc));
}

function shouldRefreshInpatientContent(raw: string, documentTypeCode?: string): boolean {
  if (isOutpatient(documentTypeCode)) return false;
  return hasHospitalLogoInContent(raw);
}

function refreshInpatientContent(raw: string, documentTypeCode: string): { changed: boolean; next: string } {
  const doc = JSON.parse(raw) as JSONContent & Record<string, unknown>;
  const logoSrc = resolveLogoSrc(doc);
  if (!logoSrc) return { changed: false, next: raw };

  const beforeChrome = JSON.stringify(doc.emrPageChrome ?? null);
  let next = ensurePageChromeP0(structuredClone(doc), { documentTypeCode, scope: 'inpatient' });
  const chromeRaw =
    (next.emrPageChrome as EmrPageChrome | undefined) ?? createDefaultEmrPageChrome();
  next.emrPageChrome = refreshInpatientChromeHeaderFormat(cloneEmrPageChrome(chromeRaw), logoSrc);
  const after = JSON.stringify(next);
  const changed = beforeChrome !== JSON.stringify(next.emrPageChrome) || after !== raw;
  return { changed, next: after };
}

async function updateDocument(client: pg.Client, id: string, next: string) {
  await client.query(`UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
    next,
    id,
  ]);
}

async function main() {
  const apply = process.argv.includes('--apply');
  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);

  const tplRes = await client.query<{
    id: string;
    name: string;
    code: string;
    document_type_code: string | null;
    content: string;
  }>(
    `SELECT id::text, name, code, document_type_code, content
     FROM emr_template
     ORDER BY document_type_code, name`,
  );

  const tplRows = tplRes.rows.filter((r) => !isOutpatient(r.document_type_code));
  const dirtyTpl = tplRows.filter((r) =>
    shouldRefreshInpatientContent(String(r.content ?? ''), String(r.document_type_code ?? '')),
  );

  const docRes = await client.query<{
    id: string;
    title: string;
    document_type_code: string;
    content: string;
  }>(
    `SELECT id::text, title, document_type_code, content
     FROM emr_document
     WHERE COALESCE(is_deleted, false) = false
       AND document_type_code = ANY($1::text[])
     ORDER BY updated_at DESC NULLS LAST`,
    [INPATIENT_TYPES],
  );
  const dirtyDoc = docRes.rows.filter((r) =>
    shouldRefreshInpatientContent(String(r.content ?? ''), r.document_type_code),
  );

  console.log(`inpatient templates=${tplRows.length} with-logo need refresh=${dirtyTpl.length}`);
  for (const r of dirtyTpl.slice(0, 20)) {
    console.log(`  tpl ${r.code || r.id} | ${r.document_type_code} | ${r.name}`);
  }
  if (dirtyTpl.length > 20) console.log(`  ... +${dirtyTpl.length - 20} more`);

  console.log(`inpatient documents=${docRes.rows.length} with-logo need refresh=${dirtyDoc.length}`);

  if (!apply) {
    console.log('\ndry-run; pass --apply to backup + update');
    await client.end();
    return;
  }

  const backupDir = resolve('src/emrEditor/migration/_pro-backup/inpatient-chrome-refresh');
  mkdirSync(backupDir, { recursive: true });

  let tplOk = 0;
  let tplSkip = 0;
  let tplFail = 0;
  for (const row of dirtyTpl) {
    const key = row.code || row.id;
    try {
      writeFileSync(resolve(backupDir, `tpl-${key}.before.json`), row.content, 'utf8');
      const { changed, next } = refreshInpatientContent(
        row.content,
        String(row.document_type_code ?? 'PROGRESS_NOTE'),
      );
      if (!changed) {
        tplSkip += 1;
        continue;
      }
      await client.query(`UPDATE emr_template SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
        next,
        row.id,
      ]);
      writeFileSync(
        resolve(backupDir, `tpl-${key}.after.json`),
        `${JSON.stringify(JSON.parse(next), null, 2)}\n`,
        'utf8',
      );
      tplOk += 1;
      console.log(`✓ tpl ${row.name} (${row.code})`);
    } catch (e) {
      tplFail += 1;
      console.error(`✗ tpl ${row.name}:`, e instanceof Error ? e.message : e);
    }
  }

  let docOk = 0;
  let docSkip = 0;
  let docFail = 0;
  for (const row of dirtyDoc) {
    try {
      writeFileSync(resolve(backupDir, `doc-${row.id}.before.json`), row.content, 'utf8');
      const { changed, next } = refreshInpatientContent(row.content, row.document_type_code);
      if (!changed) {
        docSkip += 1;
        continue;
      }
      await updateDocument(client, row.id, next);
      writeFileSync(
        resolve(backupDir, `doc-${row.id}.after.json`),
        `${JSON.stringify(JSON.parse(next), null, 2)}\n`,
        'utf8',
      );
      docOk += 1;
      console.log(`✓ doc ${row.id} ${row.title}`);
    } catch (e) {
      docFail += 1;
      console.error(`✗ doc ${row.id}:`, e instanceof Error ? e.message : e);
    }
  }

  await client.end();
  console.log(
    `\ndone templates ok=${tplOk} skip=${tplSkip} fail=${tplFail}; documents ok=${docOk} skip=${docSkip} fail=${docFail}`,
  );
  if (tplFail + docFail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
