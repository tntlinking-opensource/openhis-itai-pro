/**
 * 刷 whale-training.emr_document：截图文书类型下、仍含假页眉的已开立文书
 * 用法：
 *   npx tsx src/emrEditor/migration/batch-refresh-training-documents.ts --list
 *   npx tsx src/emrEditor/migration/batch-refresh-training-documents.ts --apply
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';
import pg from 'pg';

import { adaptTemplateToPlanBNodes } from './adapt-template-to-plan-b-nodes';
import { ensurePageChromeP0, isEmrContentDirty } from './ensure-page-chrome-p0';
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

function migrateOne(raw: unknown, documentTypeCode: string) {
  let doc = ensurePageChromeP0(structuredClone(raw) as JSONContent & Record<string, unknown>, {
    documentTypeCode,
  });
  doc = refreshTemplateSkillP0(doc).doc as JSONContent & Record<string, unknown>;
  doc = adaptTemplateToPlanBNodes(doc).doc as JSONContent & Record<string, unknown>;
  doc = refreshTemplateSkillP0(doc).doc as JSONContent & Record<string, unknown>;
  return migrateEmrTemplate(doc);
}

async function main() {
  const mode = process.argv.includes('--apply') ? 'apply' : 'list';
  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);

  const res = await client.query<{
    id: string;
    title: string;
    document_type_code: string;
    status: string;
    content: string;
    source_template_id: string | null;
  }>(
    `SELECT id::text AS id, title, document_type_code, status, content, source_template_id::text
     FROM emr_document
     WHERE COALESCE(is_deleted, false) = false
       AND document_type_code = ANY($1::text[])
     ORDER BY updated_at DESC NULLS LAST`,
    [TARGET_DOCUMENT_TYPE_CODES],
  );

  const dirty = res.rows.filter((r) => isEmrContentDirty(String(r.content ?? ''), r.document_type_code));
  console.log(`documents=${res.rows.length} dirty=${dirty.length}`);

  // 优先打出疑似截图那份
  const shot = dirty.filter(
    (r) =>
      String(r.content).includes('赵桂英') ||
      String(r.content).includes('ZY2026-08-040001') ||
      String(r.title ?? '').includes('首次病程'),
  );
  console.log(`shot-like dirty: ${shot.length}`);
  for (const r of shot.slice(0, 10)) {
    console.log(`  ${r.id} | ${r.document_type_code} | ${r.title} | ${r.status}`);
  }
  for (const r of dirty.slice(0, 20)) {
    console.log(`  dirty ${r.id} | ${r.document_type_code} | ${r.title} | status=${r.status}`);
  }

  if (mode === 'list') {
    await client.end();
    return;
  }

  const backupDir = resolve('src/emrEditor/migration/_training-doc-backup');
  mkdirSync(backupDir, { recursive: true });

  let ok = 0;
  let fail = 0;
  for (const row of dirty) {
    try {
      writeFileSync(resolve(backupDir, `${row.id}.before.json`), row.content ?? '', 'utf8');
      const raw = JSON.parse(String(row.content));
      const result = migrateOne(raw, row.document_type_code);
      const next = JSON.stringify(result.doc);
      await client.query(`UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
        next,
        row.id,
      ]);
      // 同步最新 version（若有）
      try {
        await client.query(
          `UPDATE emr_document_version SET content = $1
           WHERE document_id = $2::bigint
             AND id = (
               SELECT id FROM emr_document_version
               WHERE document_id = $2::bigint
               ORDER BY version_no DESC NULLS LAST, id DESC
               LIMIT 1
             )`,
          [next, row.id],
        );
      } catch {
        /* version 表结构可能不同，忽略 */
      }
      writeFileSync(resolve(backupDir, `${row.id}.after.json`), `${JSON.stringify(result.doc, null, 2)}\n`, 'utf8');
      ok += 1;
      console.log(`✓ doc ${row.id} ${row.title} fillable=${result.report.fillableNodeCount}`);
    } catch (e) {
      fail += 1;
      console.error(`✗ doc ${row.id}:`, e instanceof Error ? e.message : e);
    }
  }

  await client.end();
  console.log(`\ndone docs: ok=${ok} fail=${fail}`);
  if (fail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
