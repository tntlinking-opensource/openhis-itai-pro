/**
 * whale-pro：先表级备份，再刷全部模版 + 门诊/住院历史病历
 *
 * 用法（apps/web-ele）：
 *   npx tsx src/emrEditor/migration/batch-refresh-pro-all.ts --list
 *   npx tsx src/emrEditor/migration/batch-refresh-pro-all.ts --apply
 *
 * 环境变量（可选）：PRO_DB_HOST / PRO_DB_PORT / PRO_DB_NAME / PRO_DB_USER / PRO_DB_PASSWORD
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';
import pg from 'pg';

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

const OUTPATIENT_TYPES = new Set(['OUTPATIENT_RECORD', 'OUTPATIENT_EMR']);

/** 每次全量刷库请改日期戳，避免沿用旧备份表 */
const BACKUP_STAMP = '20260830';
const TPL_BAK = `emr_template_bak_${BACKUP_STAMP}`;
const DOC_BAK = `emr_document_bak_${BACKUP_STAMP}`;

type TplRow = {
  id: string;
  name: string;
  code: string;
  document_type_code: string | null;
  content: string | null;
};

type DocRow = {
  id: string;
  title: string | null;
  document_type_code: string;
  status: string | null;
  content: string | null;
};

function isOutpatient(code: string | null | undefined): boolean {
  return OUTPATIENT_TYPES.has(String(code ?? ''));
}

function parseContent(raw: string | null): unknown {
  if (!raw || !String(raw).trim()) throw new Error('content 为空');
  return JSON.parse(String(raw).trim()) as unknown;
}

function migrateInpatient(raw: unknown, documentTypeCode: string) {
  let doc = ensurePageChromeP0(structuredClone(raw) as JSONContent & Record<string, unknown>, {
    documentTypeCode,
  });
  doc = refreshTemplateSkillP0(doc).doc as JSONContent & Record<string, unknown>;
  doc = adaptTemplateToPlanBNodes(doc).doc as JSONContent & Record<string, unknown>;
  doc = refreshTemplateSkillP0(doc).doc as JSONContent & Record<string, unknown>;
  return migrateEmrTemplate(doc);
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
           ORDER BY version_number DESC NULLS LAST, id DESC
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
             ORDER BY version_no DESC NULLS LAST, id DESC
             LIMIT 1
           )`,
        [next, id],
      );
    } catch {
      /* version 表可能无数据或结构不同 */
    }
  }
}

async function listTemplates(client: pg.Client): Promise<TplRow[]> {
  try {
    const res = await client.query<TplRow>(
      `SELECT id::text AS id, name, code, document_type_code, content
       FROM emr_template
       WHERE COALESCE(is_deleted, false) = false
       ORDER BY document_type_code, name`,
    );
    return res.rows;
  } catch {
    const res = await client.query<TplRow>(
      `SELECT id::text AS id, name, code, document_type_code, content
       FROM emr_template
       ORDER BY document_type_code, name`,
    );
    return res.rows;
  }
}

async function listDocuments(client: pg.Client): Promise<DocRow[]> {
  const res = await client.query<DocRow>(
    `SELECT id::text AS id, title, document_type_code, status, content
     FROM emr_document
     WHERE COALESCE(is_deleted, false) = false
     ORDER BY document_type_code, updated_at DESC NULLS LAST`,
  );
  return res.rows;
}

async function main() {
  const apply = process.argv.includes('--apply');
  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);

  const templates = await listTemplates(client);
  const documents = await listDocuments(client);
  const tplOp = templates.filter((r) => isOutpatient(r.document_type_code));
  const tplIp = templates.filter((r) => !isOutpatient(r.document_type_code));
  const docOp = documents.filter((r) => isOutpatient(r.document_type_code));
  const docIp = documents.filter((r) => !isOutpatient(r.document_type_code));
  const dirtyIp = docIp.filter((r) => isEmrContentDirty(String(r.content ?? ''), r.document_type_code));

  console.log(`templates total=${templates.length} inpatient=${tplIp.length} outpatient=${tplOp.length}`);
  console.log(
    `documents total=${documents.length} inpatient=${docIp.length} (dirty=${dirtyIp.length}) outpatient=${docOp.length}`,
  );

  if (!apply) {
    console.log('\ndry-run; pass --apply to backup tables + migrate');
    await client.end();
    return;
  }

  console.log('\n=== 1) table backup ===');
  await ensureTableBackup(client, TPL_BAK, 'emr_template');
  await ensureTableBackup(client, DOC_BAK, 'emr_document');

  const root = resolve('src/emrEditor/migration/_pro-backup');
  const tplDir = resolve(root, 'templates');
  const docDir = resolve(root, 'documents');
  mkdirSync(tplDir, { recursive: true });
  mkdirSync(docDir, { recursive: true });

  let tplOk = 0;
  let tplFail = 0;
  console.log('\n=== 2) refresh templates (inpatient pipeline) ===');
  for (const row of tplIp) {
    const key = row.code || row.id;
    try {
      writeFileSync(resolve(tplDir, `${key}.before.json`), row.content ?? '', 'utf8');
      const raw = parseContent(row.content);
      const result = migrateInpatient(raw, String(row.document_type_code ?? ''));
      const next = JSON.stringify(result.doc);
      await client.query(`UPDATE emr_template SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
        next,
        row.id,
      ]);
      writeFileSync(resolve(tplDir, `${key}.after.json`), `${JSON.stringify(result.doc, null, 2)}\n`, 'utf8');
      tplOk += 1;
      console.log(`✓ tpl-ip ${row.name} (${row.code}) fillable=${result.report.fillableNodeCount}`);
    } catch (e) {
      tplFail += 1;
      console.error(`✗ tpl-ip ${row.name}:`, e instanceof Error ? e.message : e);
    }
  }

  console.log('\n=== 3) refresh templates (outpatient rebuild) ===');
  for (const row of tplOp) {
    const key = row.code || row.id;
    try {
      writeFileSync(resolve(tplDir, `op-${key}.before.json`), row.content ?? '', 'utf8');
      const raw = parseContent(row.content) as JSONContent;
      const nextDoc = rebuildOutpatientFromRaw(raw);
      const next = JSON.stringify(nextDoc);
      await client.query(`UPDATE emr_template SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
        next,
        row.id,
      ]);
      writeFileSync(resolve(tplDir, `op-${key}.after.json`), `${JSON.stringify(nextDoc, null, 2)}\n`, 'utf8');
      tplOk += 1;
      console.log(`✓ tpl-op ${row.name} (${row.code})`);
    } catch (e) {
      tplFail += 1;
      console.error(`✗ tpl-op ${row.name}:`, e instanceof Error ? e.message : e);
    }
  }

  let docOk = 0;
  let docFail = 0;
  console.log('\n=== 4) refresh documents (inpatient, all) ===');
  for (const row of docIp) {
    try {
      writeFileSync(resolve(docDir, `${row.id}.before.json`), row.content ?? '', 'utf8');
      const raw = parseContent(row.content);
      const result = migrateInpatient(raw, row.document_type_code);
      const next = JSON.stringify(result.doc);
      await updateDocument(client, row.id, next);
      writeFileSync(resolve(docDir, `${row.id}.after.json`), `${JSON.stringify(result.doc, null, 2)}\n`, 'utf8');
      docOk += 1;
      console.log(`✓ doc-ip ${row.id} ${row.title ?? ''} fillable=${result.report.fillableNodeCount}`);
    } catch (e) {
      docFail += 1;
      console.error(`✗ doc-ip ${row.id}:`, e instanceof Error ? e.message : e);
    }
  }

  console.log('\n=== 5) refresh documents (outpatient rebuild, all) ===');
  for (const row of docOp) {
    try {
      writeFileSync(resolve(docDir, `op-${row.id}.before.json`), row.content ?? '', 'utf8');
      const raw = parseContent(row.content) as JSONContent;
      const nextDoc = rebuildOutpatientFromRaw(raw);
      const next = JSON.stringify(nextDoc);
      await updateDocument(client, row.id, next);
      writeFileSync(resolve(docDir, `op-${row.id}.after.json`), `${JSON.stringify(nextDoc, null, 2)}\n`, 'utf8');
      docOk += 1;
      if (docOk % 100 === 0) console.log(`  ... docs ok=${docOk}`);
    } catch (e) {
      docFail += 1;
      console.error(`✗ doc-op ${row.id} ${row.title ?? ''}:`, e instanceof Error ? e.message : e);
    }
  }

  await client.end();
  console.log(`\ndone templates: ok=${tplOk} fail=${tplFail}`);
  console.log(`done documents: ok=${docOk} fail=${docFail}`);
  console.log(`table backup: ${TPL_BAK}, ${DOC_BAK}`);
  console.log(`file backup: ${root}`);
  if (tplFail > 0 || docFail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
