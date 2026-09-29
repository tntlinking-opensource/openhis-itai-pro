/**
 * training 库：住院文书页眉补齐 病房/床位 float 行（normalizeEmrPageChrome）
 *
 * 用法：
 *   npx tsx src/emrEditor/migration/batch-repair-inpatient-chrome.ts --list
 *   npx tsx src/emrEditor/migration/batch-repair-inpatient-chrome.ts --apply
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import pg from 'pg';

import {
  cloneEmrPageChrome,
  createDefaultEmrPageChrome,
  normalizeEmrPageChrome,
  type EmrPageChrome,
} from '../components/simple-editor/emr-page-chrome';

const { Client } = pg;

const DB = {
  host: process.env.TRAINING_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.TRAINING_DB_PORT ?? 5432),
  database: process.env.TRAINING_DB_NAME ?? 'whale-training',
  user: process.env.TRAINING_DB_USER ?? 'postgres',
  password: process.env.TRAINING_DB_PASSWORD ?? 'root',
};

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

function needsWardBedRepair(content: string): boolean {
  if (!content.includes('emrPageChrome')) return false;
  if (!content.includes('{inpatientNo}') && !content.includes('住院号')) return false;
  return !content.includes('{wardName}') || !content.includes('{bedNo}');
}

function repairDocContent(raw: string): { changed: boolean; next: string } {
  const doc = JSON.parse(raw) as Record<string, unknown>;
  const chromeRaw = (doc.emrPageChrome as EmrPageChrome | undefined) ?? createDefaultEmrPageChrome();
  const before = String(chromeRaw.header?.template ?? '');
  const nextChrome = normalizeEmrPageChrome(cloneEmrPageChrome(chromeRaw));
  const after = String(nextChrome.header.template ?? '');
  if (before === after) return { changed: false, next: raw };
  doc.emrPageChrome = nextChrome;
  return { changed: true, next: JSON.stringify(doc) };
}

async function main() {
  const apply = process.argv.includes('--apply');
  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);

  const docs = await client.query<{ id: string; title: string; content: string; document_type_code: string }>(
    `SELECT id::text, title, content, document_type_code
     FROM emr_document
     WHERE COALESCE(is_deleted, false) = false
       AND document_type_code = ANY($1::text[])
     ORDER BY updated_at DESC NULLS LAST`,
    [INPATIENT_TYPES],
  );
  const dirty = docs.rows.filter((r) => needsWardBedRepair(String(r.content ?? '')));
  console.log(`inpatient docs=${docs.rows.length} need ward/bed chrome=${dirty.length}`);
  for (const r of dirty.slice(0, 15)) {
    console.log(`  ${r.id} | ${r.document_type_code} | ${r.title}`);
  }

  const tpls = await client.query<{ id: string; name: string; code: string; content: string }>(
    `SELECT id::text, name, code, content FROM emr_template
     WHERE document_type_code = ANY($1::text[])`,
    [INPATIENT_TYPES],
  );
  const dirtyTpl = tpls.rows.filter((r) => needsWardBedRepair(String(r.content ?? '')));
  console.log(`inpatient templates=${tpls.rows.length} need ward/bed chrome=${dirtyTpl.length}`);

  if (!apply) {
    console.log('dry-run; pass --apply to update');
    await client.end();
    return;
  }

  const backupDir = resolve('src/emrEditor/migration/_training-doc-backup/inpatient-chrome');
  mkdirSync(backupDir, { recursive: true });

  let ok = 0;
  let fail = 0;
  for (const row of dirty) {
    try {
      writeFileSync(resolve(backupDir, `${row.id}.before.json`), row.content, 'utf8');
      const { changed, next } = repairDocContent(row.content);
      if (!changed) continue;
      await client.query(`UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
        next,
        row.id,
      ]);
      writeFileSync(resolve(backupDir, `${row.id}.after.json`), `${JSON.stringify(JSON.parse(next), null, 2)}\n`, 'utf8');
      ok += 1;
      console.log(`✓ doc ${row.id} ${row.title}`);
    } catch (e) {
      fail += 1;
      console.error(`✗ doc ${row.id}:`, e instanceof Error ? e.message : e);
    }
  }

  let tplOk = 0;
  for (const row of dirtyTpl) {
    try {
      const { changed, next } = repairDocContent(row.content);
      if (!changed) continue;
      await client.query(`UPDATE emr_template SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
        next,
        row.id,
      ]);
      tplOk += 1;
      console.log(`✓ tpl ${row.name}`);
    } catch (e) {
      fail += 1;
      console.error(`✗ tpl ${row.name}:`, e instanceof Error ? e.message : e);
    }
  }

  await client.end();
  console.log(`\ndone docs=${ok} templates=${tplOk} fail=${fail}`);
  if (fail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
