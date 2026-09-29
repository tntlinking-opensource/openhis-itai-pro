/**
 * 按患者身份证号刷 whale-training.emr_document 历史病历
 *
 * 用法：
 *   npx tsx src/emrEditor/migration/refresh-patient-documents.ts --id-card 220322200001012564 --list
 *   npx tsx src/emrEditor/migration/refresh-patient-documents.ts --id-card 220322200001012564 --apply
 *   npx tsx src/emrEditor/migration/refresh-patient-documents.ts --id-card ... --apply --restore-before
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
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

function parseArg(flag: string): string | undefined {
  const idx = process.argv.indexOf(flag);
  if (idx === -1) return undefined;
  return process.argv[idx + 1];
}

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
  const idCard = parseArg('--id-card')?.trim();
  if (!idCard) {
    console.error('用法: --id-card <身份证号> [--list|--apply] [--restore-before]');
    process.exit(1);
  }
  const mode = process.argv.includes('--apply') ? 'apply' : 'list';
  const restoreBefore = process.argv.includes('--restore-before');

  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);

  const patientRes = await client.query<{ id: string; name: string }>(
    `SELECT id::text AS id, name FROM adm_patient
     WHERE id_card = $1 AND COALESCE(is_deleted, false) = false
     LIMIT 1`,
    [idCard],
  );
  const patient = patientRes.rows[0];
  if (!patient) {
    console.error(`未找到身份证 ${idCard} 对应患者`);
    await client.end();
    process.exit(1);
  }
  console.log(`patient: ${patient.name} (${patient.id})`);

  const res = await client.query<{
    id: string;
    title: string;
    document_type_code: string;
    status: string;
    content: string;
  }>(
    `SELECT id::text AS id, title, document_type_code, status, content
     FROM emr_document
     WHERE COALESCE(is_deleted, false) = false
       AND patient_id = $1
     ORDER BY updated_at DESC NULLS LAST`,
    [patient.id],
  );

  const backupDir = resolve(`src/emrEditor/migration/_training-doc-backup/patient-${patient.id}`);

  const rows = res.rows.map((r) => {
    const beforePath = resolve(backupDir, `${r.id}.before.json`);
    if (restoreBefore && existsSync(beforePath)) {
      return { ...r, content: readFileSync(beforePath, 'utf8'), source: 'before-backup' as const };
    }
    return { ...r, source: 'db' as const };
  });

  const dirty = rows.filter((r) => isEmrContentDirty(String(r.content ?? ''), r.document_type_code));
  console.log(`documents=${rows.length} dirty=${dirty.length}${restoreBefore ? ' (restore-before)' : ''}`);
  for (const r of rows) {
    const d = isEmrContentDirty(String(r.content ?? ''), r.document_type_code);
    console.log(
      `  ${d ? 'dirty' : 'ok  '} ${r.id} | ${r.document_type_code} | ${r.status} | ${r.title} | src=${r.source}`,
    );
  }

  if (mode === 'list') {
    await client.end();
    return;
  }

  if (dirty.length === 0) {
    console.log('无需刷写');
    await client.end();
    return;
  }

  mkdirSync(backupDir, { recursive: true });

  let ok = 0;
  let fail = 0;
  for (const row of dirty) {
    try {
      const beforePath = resolve(backupDir, `${row.id}.before.json`);
      if (!existsSync(beforePath)) {
        writeFileSync(beforePath, row.content ?? '', 'utf8');
      }
      const raw = JSON.parse(String(row.content));
      const result = migrateOne(raw, row.document_type_code);
      const next = JSON.stringify(result.doc);
      await client.query(`UPDATE emr_document SET content = $1, updated_at = NOW() WHERE id = $2::bigint`, [
        next,
        row.id,
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
          [next, row.id],
        );
      } catch {
        /* ignore */
      }
      writeFileSync(resolve(backupDir, `${row.id}.after.json`), `${JSON.stringify(result.doc, null, 2)}\n`, 'utf8');
      ok += 1;
      const headerTpl = String(
        (result.doc as Record<string, unknown>).emrPageChrome &&
          ((result.doc as Record<string, unknown>).emrPageChrome as Record<string, unknown>).header &&
          (((result.doc as Record<string, unknown>).emrPageChrome as Record<string, unknown>).header as Record<
            string,
            unknown
          >).template,
      );
      const afterDirty = isEmrContentDirty(next, row.document_type_code);
      const hasPatientName =
        next.includes('"fieldKey":"patientName"') || next.includes('"elementId":"patientName"');
      console.log(
        `✓ doc ${row.id} ${row.title} fillable=${result.report.fillableNodeCount} afterDirty=${afterDirty} patientNameInBody=${hasPatientName}`,
      );
      console.log(`  header: ${headerTpl.slice(0, 120)}${headerTpl.length > 120 ? '…' : ''}`);
    } catch (e) {
      fail += 1;
      console.error(`✗ doc ${row.id}:`, e instanceof Error ? e.message : e);
    }
  }

  await client.end();
  console.log(`\ndone: ok=${ok} fail=${fail}`);
  if (fail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
