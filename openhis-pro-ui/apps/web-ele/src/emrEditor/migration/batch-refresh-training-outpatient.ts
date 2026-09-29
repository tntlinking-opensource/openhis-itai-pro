/**
 * training 库：门诊病历模版 + 历史文书，按定稿版式批量重建
 *
 * 用法：
 *   npx tsx src/emrEditor/migration/batch-refresh-training-outpatient.ts --list
 *   npx tsx src/emrEditor/migration/batch-refresh-training-outpatient.ts --apply
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';
import pg from 'pg';

import { rebuildOutpatientFromRaw } from './outpatient-clean-doc';

const { Client } = pg;

const DB = {
  host: process.env.TRAINING_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.TRAINING_DB_PORT ?? 5432),
  database: process.env.TRAINING_DB_NAME ?? 'whale-training',
  user: process.env.TRAINING_DB_USER ?? 'postgres',
  password: process.env.TRAINING_DB_PASSWORD ?? 'root',
};

const OUTPATIENT_TYPES = ['OUTPATIENT_RECORD', 'OUTPATIENT_EMR'];

function parseContent(raw: string | null): JSONContent {
  if (!raw || !String(raw).trim()) {
    throw new Error('content 为空');
  }
  return JSON.parse(String(raw).trim()) as JSONContent;
}

async function listTemplates(client: pg.Client) {
  const sql = `SELECT id::text AS id, name, code, document_type_code, content
     FROM emr_template
     WHERE document_type_code = ANY($1::text[])
     ORDER BY name`;
  try {
    const res = await client.query<{
      id: string;
      name: string;
      code: string;
      document_type_code: string;
      content: string | null;
    }>(sql.replace('WHERE document_type_code', 'WHERE COALESCE(is_deleted, false) = false AND document_type_code'), [
      OUTPATIENT_TYPES,
    ]);
    return res.rows;
  } catch {
    const res = await client.query<{
      id: string;
      name: string;
      code: string;
      document_type_code: string;
      content: string | null;
    }>(sql, [OUTPATIENT_TYPES]);
    return res.rows;
  }
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
    /* version 表可能无数据或结构不同 */
  }
}

async function main() {
  const apply = process.argv.includes('--apply');
  const templatesOnly = process.argv.includes('--templates-only');
  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);

  const templates = await listTemplates(client);
  const documents = templatesOnly ? [] : await listDocuments(client);
  console.log(`outpatient templates: ${templates.length}`);
  console.log(`outpatient documents: ${documents.length}${templatesOnly ? ' (skipped --templates-only)' : ''}`);

  for (const t of templates.slice(0, 15)) {
    console.log(`  tpl ${t.id} | ${t.code} | ${t.name}`);
  }
  if (templates.length > 15) console.log(`  ... and ${templates.length - 15} more templates`);
  for (const d of documents.slice(0, 10)) {
    console.log(`  doc ${d.id} | ${d.status} | ${d.title}`);
  }
  if (documents.length > 10) console.log(`  ... and ${documents.length - 10} more documents`);

  if (!apply) {
    console.log('\ndry-run; pass --apply to update DB');
    await client.end();
    return;
  }

  const tplBackup = resolve('src/emrEditor/migration/_training-backup/outpatient-templates');
  const docBackup = resolve('src/emrEditor/migration/_training-doc-backup/outpatient-rebuild');
  mkdirSync(tplBackup, { recursive: true });
  mkdirSync(docBackup, { recursive: true });

  let tplOk = 0;
  let tplFail = 0;
  for (const row of templates) {
    const key = row.code || row.id;
    try {
      writeFileSync(resolve(tplBackup, `${key}.before.json`), row.content ?? '', 'utf8');
      const raw = parseContent(row.content);
      const nextDoc = rebuildOutpatientFromRaw(raw);
      const next = JSON.stringify(nextDoc);
      await client.query(`UPDATE emr_template SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
        next,
        row.id,
      ]);
      writeFileSync(resolve(tplBackup, `${key}.after.json`), `${JSON.stringify(nextDoc, null, 2)}\n`, 'utf8');
      tplOk += 1;
      console.log(`✓ tpl ${row.name} (${row.code})`);
    } catch (e) {
      tplFail += 1;
      console.error(`✗ tpl ${row.name}:`, e instanceof Error ? e.message : e);
    }
  }

  let docOk = 0;
  let docFail = 0;
  for (const row of documents) {
    try {
      writeFileSync(resolve(docBackup, `${row.id}.before.json`), row.content ?? '', 'utf8');
      const raw = parseContent(row.content);
      const nextDoc = rebuildOutpatientFromRaw(raw);
      const next = JSON.stringify(nextDoc);
      await updateDocument(client, row.id, next);
      writeFileSync(resolve(docBackup, `${row.id}.after.json`), `${JSON.stringify(nextDoc, null, 2)}\n`, 'utf8');
      docOk += 1;
      if (docOk % 100 === 0) console.log(`  ... docs ${docOk}/${documents.length}`);
    } catch (e) {
      docFail += 1;
      console.error(`✗ doc ${row.id} ${row.title}:`, e instanceof Error ? e.message : e);
    }
  }

  await client.end();
  console.log(`\ndone templates: ok=${tplOk} fail=${tplFail} backup=${tplBackup}`);
  console.log(`done documents: ok=${docOk} fail=${docFail} backup=${docBackup}`);
  if (tplFail > 0 || docFail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
