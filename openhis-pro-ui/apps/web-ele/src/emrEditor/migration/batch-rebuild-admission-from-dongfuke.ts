/**
 * 按 whale-training 董复刻入院记录版式，刷库内全部仍含硬分页的入院记录。
 *
 * 用法（apps/web-ele）：
 *   npx tsx src/emrEditor/migration/batch-rebuild-admission-from-dongfuke.ts --list
 *   npx tsx src/emrEditor/migration/batch-rebuild-admission-from-dongfuke.ts --apply
 *   npx tsx src/emrEditor/migration/batch-rebuild-admission-from-dongfuke.ts --apply --force --db whale-pro
 *
 * 环境变量：PRO_DB_HOST / PRO_DB_PORT / PRO_DB_USER / PRO_DB_PASSWORD
 */
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';
import pg from 'pg';

import {
  clearAdmissionFillableValues,
  countEmrPageBreaks,
  rebuildAdmissionFromGold,
} from './rebuild-admission-from-gold';

const { Client } = pg;

const DB_BASE = {
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
};

const GOLD_DB = 'whale-training';
const GOLD_DOC_ID = '353077517918670848';
const TARGET_DBS = ['whale-training', 'whale-pro'] as const;
const BACKUP_STAMP = '20260901';

type DocRow = {
  content: unknown;
  id: string;
  status: string | null;
  title: string | null;
};

function asDoc(raw: unknown): JSONContent {
  if (!raw) throw new Error('content 为空');
  if (typeof raw === 'string') return JSON.parse(raw) as JSONContent;
  return raw as JSONContent;
}

function hintFromBag(doc: JSONContent): string {
  let chief = '';
  let signer = '';
  const visit = (node: JSONContent) => {
    const t = node.type ?? '';
    const label = String(node.attrs?.label ?? '');
    const val = String(node.attrs?.value ?? '').trim();
    if (t === 'emrFreeText' && label === '主诉' && val) chief = val.slice(0, 24);
    if (t === 'emrFreeText' && label === '核实人签名' && val) signer = val;
    for (const child of node.content ?? []) visit(child);
  };
  visit(doc);
  return [signer, chief].filter(Boolean).join(' / ') || '(无主诉)';
}

function contentToSql(doc: JSONContent): string {
  return JSON.stringify(doc);
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
      /* version 表可能无数据 */
    }
  }
}

async function loadGold(): Promise<JSONContent> {
  const client = new Client({
    ...DB_BASE,
    database: GOLD_DB,
    connectionTimeoutMillis: 8000,
  });
  await client.connect();
  const res = await client.query<{ content: unknown }>(
    `SELECT content FROM emr_document WHERE id = $1::bigint`,
    [GOLD_DOC_ID],
  );
  await client.end();
  const row = res.rows[0];
  if (!row) throw new Error(`找不到金标准入院记录 ${GOLD_DOC_ID}`);
  const gold = asDoc(row.content);
  if (countEmrPageBreaks(gold) !== 0) {
    throw new Error('金标准仍含 emrPageBreak，停止刷库');
  }
  return gold;
}

async function listAdmissionTemplates(database: string): Promise<DocRow[]> {
  const client = new Client({
    ...DB_BASE,
    database,
    connectionTimeoutMillis: 8000,
  });
  await client.connect();
  const res = await client.query<DocRow>(
    `SELECT id::text AS id, name AS title, code AS status, content
     FROM emr_template
     WHERE COALESCE(is_deleted, false) = false
       AND document_type_code = 'ADMISSION_RECORD'
     ORDER BY name`,
  );
  await client.end();
  return res.rows;
}

function emptyGoldTemplate(gold: JSONContent): JSONContent {
  const doc = structuredClone(gold);
  clearAdmissionFillableValues(doc);
  return doc;
}

async function listAdmissionDocs(database: string): Promise<DocRow[]> {
  const client = new Client({
    ...DB_BASE,
    database,
    connectionTimeoutMillis: 8000,
  });
  await client.connect();
  const res = await client.query<DocRow>(
    `SELECT id::text AS id, title, status, content
     FROM emr_document
     WHERE COALESCE(is_deleted, false) = false
       AND document_type_code = 'ADMISSION_RECORD'
     ORDER BY updated_at DESC NULLS LAST`,
  );
  await client.end();
  return res.rows;
}

function parseArg(flag: string): string | undefined {
  const idx = process.argv.indexOf(flag);
  if (idx === -1) return undefined;
  return process.argv[idx + 1];
}

async function main() {
  const apply = process.argv.includes('--apply');
  const force = process.argv.includes('--force');
  const onlyDb = parseArg('--db');
  const dbs = onlyDb ? TARGET_DBS.filter((d) => d === onlyDb) : [...TARGET_DBS];
  if (onlyDb && dbs.length === 0) {
    throw new Error(`未知 --db ${onlyDb}，可选 ${TARGET_DBS.join('|')}`);
  }
  const gold = await loadGold();
  console.log(
    `gold ${GOLD_DB}#${GOLD_DOC_ID} pageBreaks=${countEmrPageBreaks(gold)} force=${force} dbs=${dbs.join(',')}`,
  );

  const backupRoot = resolve(`src/emrEditor/migration/_admission-rebuild-${BACKUP_STAMP}`);

  let totalSkip = 0;
  let totalDo = 0;
  let totalOk = 0;
  let totalFail = 0;

  for (const database of dbs) {
    const rows = await listAdmissionDocs(database);
    console.log(`\n=== ${database} ADMISSION_RECORD x${rows.length} ===`);
    const todo: { hint: string; id: string; raw: JSONContent; title: string }[] = [];
    for (const row of rows) {
      if (database === GOLD_DB && row.id === GOLD_DOC_ID) {
        console.log(`  skip gold ${row.id}`);
        totalSkip += 1;
        continue;
      }
      const raw = asDoc(row.content);
      const pb = countEmrPageBreaks(raw);
      const hint = hintFromBag(raw);
      if (pb <= 0 && !force) {
        console.log(`  skip already-current pb=0 ${row.id} ${hint}`);
        totalSkip += 1;
        continue;
      }
      const preview = rebuildAdmissionFromGold(raw, gold);
      console.log(
        `  NEED ${row.id} pb=${pb}→0 bag=${preview.report.bagKeys} unmatched=${preview.report.unmatchedLabels.length} ${hint}`,
      );
      if (preview.report.unmatchedLabels.length > 0) {
        console.log(`    unmatched: ${preview.report.unmatchedLabels.join(', ')}`);
      }
      todo.push({ id: row.id, title: String(row.title ?? ''), raw, hint });
      totalDo += 1;
    }

    if (apply && todo.length > 0) {
      const client = new Client({
        ...DB_BASE,
        database,
        connectionTimeoutMillis: 8000,
      });
      await client.connect();
      const dir = resolve(backupRoot, database);
      mkdirSync(dir, { recursive: true });
      for (const item of todo) {
        try {
          const beforePath = resolve(dir, `${item.id}.before.json`);
          if (!existsSync(beforePath)) {
            writeFileSync(beforePath, JSON.stringify(item.raw), 'utf8');
          }
          const result = rebuildAdmissionFromGold(item.raw, gold);
          const next = contentToSql(result.doc);
          await updateDocument(client, item.id, next);
          writeFileSync(resolve(dir, `${item.id}.after.json`), `${JSON.stringify(result.doc, null, 2)}\n`, 'utf8');
          totalOk += 1;
          console.log(`  ✓ ${item.id} ${item.hint}`);
        } catch (e) {
          totalFail += 1;
          console.error(`  ✗ ${item.id}:`, e instanceof Error ? e.message : e);
        }
      }
      await client.end();
    }

    const tpls = await listAdmissionTemplates(database);
    console.log(`\n=== ${database} ADMISSION templates x${tpls.length} ===`);
    const tplTodo: { id: string; raw: JSONContent; title: string }[] = [];
    for (const row of tpls) {
      const raw = asDoc(row.content);
      const pb = countEmrPageBreaks(raw);
      if (pb <= 0) {
        console.log(`  skip tpl pb=0 ${row.title}`);
        totalSkip += 1;
        continue;
      }
      console.log(`  NEED tpl ${row.id} ${row.title} pb=${pb}→0`);
      tplTodo.push({ id: row.id, raw, title: String(row.title ?? '') });
      totalDo += 1;
    }
    if (apply && tplTodo.length > 0) {
      const client = new Client({
        ...DB_BASE,
        database,
        connectionTimeoutMillis: 8000,
      });
      await client.connect();
      const dir = resolve(backupRoot, `${database}-tpl`);
      mkdirSync(dir, { recursive: true });
      const empty = emptyGoldTemplate(gold);
      const next = contentToSql(empty);
      for (const item of tplTodo) {
        try {
          writeFileSync(resolve(dir, `${item.id}.before.json`), JSON.stringify(item.raw), 'utf8');
          await client.query(
            `UPDATE emr_template SET content = $1, updated_at = NOW() WHERE id = $2::bigint`,
            [next, item.id],
          );
          writeFileSync(resolve(dir, `${item.id}.after.json`), `${JSON.stringify(empty, null, 2)}\n`, 'utf8');
          totalOk += 1;
          console.log(`  ✓ tpl ${item.id} ${item.title}`);
        } catch (e) {
          totalFail += 1;
          console.error(`  ✗ tpl ${item.id}:`, e instanceof Error ? e.message : e);
        }
      }
      await client.end();
    }
  }

  console.log(
    `\n${apply ? 'apply' : 'dry-run'} skip=${totalSkip} need=${totalDo} ok=${totalOk} fail=${totalFail}`,
  );
  if (!apply) console.log('pass --apply to write');
  if (apply && totalFail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
