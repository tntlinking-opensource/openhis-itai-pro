/**
 * whale-pro：门诊历史文书 — 仅刷右下角日期为日期控件，保留原日期值
 *
 * 用法（apps/web-ele）：
 *   npx tsx src/emrEditor/migration/batch-refresh-pro-outpatient-docs-sign-date.ts --list
 *   npx tsx src/emrEditor/migration/batch-refresh-pro-outpatient-docs-sign-date.ts --apply
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';
import pg from 'pg';

import { patchOutpatientSignDateToDateWidget } from './patch-outpatient-sign-date';

const { Client } = pg;

const DB = {
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  database: process.env.PRO_DB_NAME ?? 'whale-pro',
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
};

const OUTPATIENT_TYPES = ['OUTPATIENT_RECORD', 'OUTPATIENT_EMR'];

function parseContent(raw: string | null): JSONContent {
  if (!raw || !String(raw).trim()) throw new Error('content 为空');
  return JSON.parse(String(raw).trim()) as JSONContent;
}

async function listDocuments(client: pg.Client) {
  const res = await client.query<{
    id: string;
    title: string;
    status: string;
    content: string;
  }>(
    `SELECT id::text AS id, title, status, content
     FROM emr_document
     WHERE COALESCE(is_deleted, false) = false
       AND document_type_code = ANY($1::text[])
     ORDER BY updated_at DESC NULLS LAST`,
    [OUTPATIENT_TYPES],
  );
  return res.rows;
}

async function updateDocument(client: pg.Client, id: string, next: string) {
  await client.query(`UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
    next,
    id,
  ]);
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
      [next, id],
    );
  } catch {
    try {
      await client.query(
        `UPDATE emr_document_version SET content = $1
         WHERE document_id = $2::bigint
           AND id = (
             SELECT id FROM emr_document_version
             WHERE document_id = $2::bigint
             ORDER BY version_number DESC NULLS LAST, id DESC
             LIMIT 1
           )`,
        [next, id],
      );
    } catch {
      /* version 表可能无数据 */
    }
  }
}

async function main() {
  const apply = process.argv.includes('--apply');
  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);

  const documents = await listDocuments(client);
  console.log(`outpatient documents: ${documents.length}`);

  let wouldChange = 0;
  let wouldSkip = 0;
  let noSignDate = 0;

  for (const row of documents) {
    try {
      const raw = parseContent(row.content);
      const result = patchOutpatientSignDateToDateWidget(raw);
      if (result.skipped) wouldSkip += 1;
      else if (result.changed) wouldChange += 1;
      else noSignDate += 1;
    } catch {
      noSignDate += 1;
    }
  }

  console.log(`preview: change=${wouldChange} skip(already date)=${wouldSkip} no-signDate=${noSignDate}`);

  if (!apply) {
    console.log('\ndry-run; pass --apply to update DB');
    await client.end();
    return;
  }

  const docBackup = resolve('src/emrEditor/migration/_pro-backup/outpatient-docs-sign-date');
  mkdirSync(docBackup, { recursive: true });

  let ok = 0;
  let skipped = 0;
  let fail = 0;

  for (const row of documents) {
    try {
      const raw = parseContent(row.content);
      const result = patchOutpatientSignDateToDateWidget(raw);
      if (result.skipped) {
        skipped += 1;
        continue;
      }
      if (!result.changed) {
        skipped += 1;
        continue;
      }

      writeFileSync(resolve(docBackup, `${row.id}.before.json`), row.content ?? '', 'utf8');
      const next = JSON.stringify(result.doc);
      await updateDocument(client, row.id, next);
      writeFileSync(
        resolve(docBackup, `${row.id}.after.json`),
        `${JSON.stringify(result.doc, null, 2)}\n`,
        'utf8',
      );
      ok += 1;
      if (ok % 200 === 0) console.log(`  ... docs ${ok}/${wouldChange}`);
    } catch (e) {
      fail += 1;
      console.error(`✗ doc ${row.id} ${row.title}:`, e instanceof Error ? e.message : e);
    }
  }

  await client.end();
  console.log(`\ndone: updated=${ok} skipped=${skipped} fail=${fail} backup=${docBackup}`);
  if (fail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
