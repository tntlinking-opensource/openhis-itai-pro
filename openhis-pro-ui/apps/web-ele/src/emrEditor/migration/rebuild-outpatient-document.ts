/**
 * 门诊病历历史文书：重建整洁版式并回写 emr_document
 *
 * 用法：
 *   npx tsx src/emrEditor/migration/rebuild-outpatient-document.ts --doc-id 347641235705958400 --apply
 *   npx tsx src/emrEditor/migration/rebuild-outpatient-document.ts --id-card 220322200001012564 --apply
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';
import pg from 'pg';

import { rebuildOutpatientFromRaw, walkCollect, type FieldBag } from './outpatient-clean-doc';

const { Client } = pg;

const DB = {
  host: process.env.TRAINING_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.TRAINING_DB_PORT ?? 5432),
  database: process.env.TRAINING_DB_NAME ?? 'whale-training',
  user: process.env.TRAINING_DB_USER ?? 'postgres',
  password: process.env.TRAINING_DB_PASSWORD ?? 'root',
};

const DOC_ID = '347641235705958400';
const ID_CARD = '220322200001012564';

function parseArg(flag: string): string | undefined {
  const idx = process.argv.indexOf(flag);
  if (idx === -1) return undefined;
  return process.argv[idx + 1];
}

async function main() {
  const apply = process.argv.includes('--apply');
  const docId = parseArg('--doc-id') ?? DOC_ID;
  const idCard = parseArg('--id-card') ?? ID_CARD;

  const client = new Client(DB);
  await client.connect();

  let targetId = docId;
  if (parseArg('--id-card')) {
    const p = await client.query<{ id: string }>(
      `SELECT d.id::text FROM emr_document d
       JOIN adm_patient p ON p.id::text = d.patient_id
       WHERE p.id_card = $1 AND COALESCE(d.is_deleted, false) = false
         AND d.document_type_code = 'OUTPATIENT_RECORD'
       ORDER BY d.updated_at DESC LIMIT 1`,
      [idCard],
    );
    targetId = p.rows[0]?.id ?? docId;
  }

  const res = await client.query<{ id: string; title: string; content: string }>(
    `SELECT id::text, title, content FROM emr_document WHERE id = $1::bigint`,
    [targetId],
  );
  const row = res.rows[0];
  if (!row) {
    console.error('document not found', targetId);
    process.exit(1);
  }

  const raw = JSON.parse(String(row.content)) as JSONContent & Record<string, unknown>;
  const bag: FieldBag = {};
  walkCollect(raw, bag);
  const nextDoc = rebuildOutpatientFromRaw(raw);

  const backupDir = resolve('src/emrEditor/migration/_training-doc-backup/rebuild-outpatient');
  mkdirSync(backupDir, { recursive: true });
  writeFileSync(resolve(backupDir, `${targetId}.before-rebuild.json`), row.content, 'utf8');
  writeFileSync(
    resolve(backupDir, `${targetId}.after-rebuild.json`),
    `${JSON.stringify(nextDoc, null, 2)}\n`,
    'utf8',
  );

  console.log(`doc ${targetId} | ${row.title}`);
  console.log(`fields preserved: ${Object.keys(bag).length}`);
  console.log(`written ${backupDir}`);

  if (!apply) {
    console.log('dry-run only; pass --apply to update DB');
    await client.end();
    return;
  }

  const next = JSON.stringify(nextDoc);
  await client.query(`UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
    next,
    targetId,
  ]);
  console.log('✓ updated emr_document');
  try {
    await client.query(
      `UPDATE emr_document_version SET content = $1
       WHERE id = (
         SELECT id FROM emr_document_version
         WHERE document_id = $2::bigint AND COALESCE(is_deleted, false) = false
         ORDER BY version_no DESC NULLS LAST, updated_at DESC NULLS LAST
         LIMIT 1
       )`,
      [next, targetId],
    );
    console.log('✓ updated emr_document_version (latest)');
  } catch {
    console.log('(no emr_document_version update)');
  }
  await client.end();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
