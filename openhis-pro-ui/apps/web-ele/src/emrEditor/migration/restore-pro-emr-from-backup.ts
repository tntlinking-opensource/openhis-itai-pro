/**
 * 从 emr_*_bak_20260830_oplayout 还原 whale-pro（本次门诊表格布局刷写前快照）
 *
 *   npx tsx src/emrEditor/migration/restore-pro-emr-from-backup.ts --dry-run
 *   npx tsx src/emrEditor/migration/restore-pro-emr-from-backup.ts --apply
 *
 * 也可设环境变量：PRO_TPL_BAK / PRO_DOC_BAK
 */
import pg from 'pg';

const { Client } = pg;

const DB = {
  host: process.env.PRO_DB_HOST ?? '172.16.101.2',
  port: Number(process.env.PRO_DB_PORT ?? 5432),
  database: process.env.PRO_DB_NAME ?? 'whale-pro',
  user: process.env.PRO_DB_USER ?? 'postgres',
  password: process.env.PRO_DB_PASSWORD ?? 'root',
};

const TPL_BAK = process.env.PRO_TPL_BAK ?? 'emr_template_bak_20260830_oplayout';
const DOC_BAK = process.env.PRO_DOC_BAK ?? 'emr_document_bak_20260830_oplayout';

async function tableExists(client: pg.Client, name: string): Promise<boolean> {
  const r = await client.query<{ exists: boolean }>(
    `SELECT EXISTS (
       SELECT 1 FROM information_schema.tables
       WHERE table_schema = 'public' AND table_name = $1
     ) AS exists`,
    [name],
  );
  return Boolean(r.rows[0]?.exists);
}

async function main() {
  const apply = process.argv.includes('--apply');
  const client = new Client(DB);
  await client.connect();
  console.log(`connected ${DB.host}:${DB.port}/${DB.database}`);

  for (const name of [TPL_BAK, DOC_BAK]) {
    if (!(await tableExists(client, name))) {
      console.error(`备份表不存在: ${name}`);
      process.exit(1);
    }
    const cnt = await client.query<{ n: number }>(`SELECT count(*)::int AS n FROM ${name}`);
    console.log(`${name}: ${cnt.rows[0]?.n} rows`);
  }

  const tplLive = await client.query<{ n: number }>(
    `SELECT count(*)::int AS n FROM emr_template WHERE COALESCE(is_deleted, false) = false`,
  );
  const docLive = await client.query<{ n: number }>(
    `SELECT count(*)::int AS n FROM emr_document WHERE COALESCE(is_deleted, false) = false`,
  );
  console.log(`live templates (not deleted): ${tplLive.rows[0]?.n}`);
  console.log(`live documents (not deleted): ${docLive.rows[0]?.n}`);

  if (!apply) {
    console.log('\ndry-run; pass --apply to restore from backup tables');
    await client.end();
    return;
  }

  console.log('\n=== restore emr_template ===');
  const tplRes = await client.query(`
    UPDATE emr_template t
    SET
      name = b.name,
      code = b.code,
      document_type_code = b.document_type_code,
      organization_id = b.organization_id,
      encounter_class_enum = b.encounter_class_enum,
      encounter_type_enum = b.encounter_type_enum,
      content = b.content,
      is_enabled = b.is_enabled,
      scope_level = b.scope_level,
      owner_practitioner_id = b.owner_practitioner_id,
      encounter_class_code = b.encounter_class_code,
      updated_at = b.updated_at,
      updated_by = b.updated_by,
      is_deleted = b.is_deleted,
      deleted_at = b.deleted_at,
      deleted_by = b.deleted_by,
      tenant_id = b.tenant_id
    FROM ${TPL_BAK} b
    WHERE t.id = b.id
  `);
  console.log(`updated emr_template rows: ${tplRes.rowCount ?? 0}`);

  console.log('\n=== restore emr_document ===');
  const docRes = await client.query(`
    UPDATE emr_document d
    SET
      patient_id = b.patient_id,
      encounter_id = b.encounter_id,
      document_type_code = b.document_type_code,
      title = b.title,
      content = b.content,
      status = b.status,
      source_template_id = b.source_template_id,
      updated_at = b.updated_at,
      updated_by = b.updated_by,
      is_deleted = b.is_deleted,
      deleted_at = b.deleted_at,
      deleted_by = b.deleted_by,
      tenant_id = b.tenant_id
    FROM ${DOC_BAK} b
    WHERE d.id = b.id
  `);
  console.log(`updated emr_document rows: ${docRes.rowCount ?? 0}`);

  await client.end();
  console.log('\nrestore done');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
