/**
 * whale-pro：门诊模版 + 门诊历史病历 → template.sjon 表格版式（只改排版，保留字段值）
 *
 * 用法（apps/web-ele）：
 *   npx tsx src/emrEditor/migration/batch-refresh-pro-outpatient.ts
 *   npx tsx src/emrEditor/migration/batch-refresh-pro-outpatient.ts --apply
 *   npx tsx src/emrEditor/migration/batch-refresh-pro-outpatient.ts --apply --templates-only
 *
 * 环境变量（可选）：PRO_DB_HOST / PRO_DB_PORT / PRO_DB_NAME / PRO_DB_USER / PRO_DB_PASSWORD
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';
import pg from 'pg';

import { rebuildOutpatientFromRaw } from './outpatient-clean-doc';

const { Client } = pg;

const DB = {
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  database: process.env.PRO_DB_NAME ?? 'whale-pro',
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
};

const OUTPATIENT_TYPES = ['OUTPATIENT_RECORD', 'OUTPATIENT_EMR'];

/** 本次仅门诊布局刷写备份戳 */
const BACKUP_STAMP = '20260830_oplayout';
const TPL_BAK = `emr_template_bak_${BACKUP_STAMP}`;
const DOC_BAK = `emr_document_bak_${BACKUP_STAMP}`;

function parseContent(raw: string | null): JSONContent {
  if (!raw || !String(raw).trim()) {
    throw new Error('content 为空');
  }
  return JSON.parse(String(raw).trim()) as JSONContent;
}

async function ensureTableBackup(client: pg.Client, bakName: string, source: string) {
  const exists = await client.query<{ exists: boolean }>(
    `SELECT EXISTS (
       SELECT 1 FROM information_schema.tables
       WHERE table_schema = 'public' AND table_name = $1
     ) AS exists`,
    [bakName],
  );
  if (exists.rows[0]?.exists) {
    const cnt = await client.query<{ n: number }>(`SELECT count(*)::int AS n FROM ${bakName}`);
    console.log(`backup table ${bakName} already exists (rows=${cnt.rows[0]?.n})`);
    return;
  }
  await client.query(`CREATE TABLE ${bakName} AS TABLE ${source}`);
  const cnt = await client.query<{ n: number }>(`SELECT count(*)::int AS n FROM ${bakName}`);
  console.log(`✓ created ${bakName} (rows=${cnt.rows[0]?.n})`);
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
    }>(
      sql.replace(
        'WHERE document_type_code',
        'WHERE COALESCE(is_deleted, false) = false AND document_type_code',
      ),
      [OUTPATIENT_TYPES],
    );
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
      /* version 表可能无数据或结构不同 */
    }
  }
}

async function main() {
  const apply = process.argv.includes('--apply');
  const templatesOnly = process.argv.includes('--templates-only');
  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);
  console.log(`layout gold: src/template.sjon (patientHeader + outpatientHistory)`);

  const templates = await listTemplates(client);
  const documents = templatesOnly ? [] : await listDocuments(client);
  console.log(`outpatient templates: ${templates.length}`);
  console.log(`outpatient documents: ${documents.length}${templatesOnly ? ' (skipped --templates-only)' : ''}`);

  for (const t of templates.slice(0, 10)) {
    console.log(`  tpl ${t.id} | ${t.code} | ${t.name}`);
  }
  if (templates.length > 10) console.log(`  ... and ${templates.length - 10} more templates`);

  if (!apply) {
    console.log('\ndry-run; pass --apply to backup tables + update DB');
    await client.end();
    return;
  }

  console.log('\n=== 1) table backup ===');
  await ensureTableBackup(client, TPL_BAK, 'emr_template');
  await ensureTableBackup(client, DOC_BAK, 'emr_document');

  const tplBackup = resolve('src/emrEditor/migration/_pro-backup/outpatient-layout-templates');
  const docBackup = resolve('src/emrEditor/migration/_pro-backup/outpatient-layout-documents');
  mkdirSync(tplBackup, { recursive: true });
  mkdirSync(docBackup, { recursive: true });

  let tplOk = 0;
  let tplFail = 0;
  console.log('\n=== 2) refresh outpatient templates ===');
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
  console.log('\n=== 3) refresh outpatient documents ===');
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
  console.log(`\ndone templates: ok=${tplOk} fail=${tplFail}`);
  console.log(`done documents: ok=${docOk} fail=${docFail}`);
  console.log(`table backup: ${TPL_BAK}, ${DOC_BAK}`);
  console.log(`file backup: ${tplBackup} / ${docBackup}`);
  if (tplFail > 0 || docFail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
