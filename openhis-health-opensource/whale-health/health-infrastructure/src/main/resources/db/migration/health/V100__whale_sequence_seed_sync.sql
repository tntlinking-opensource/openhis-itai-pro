-- 种子数据 bus_no / condition_code 与 whale_sequence 同步
-- Flyway V100（须在 V1–V99 全部种子之后执行）
-- 直接 INSERT 种子不会走 BusinessSequenceService，必须按表里已有编码抬升序号下限，
-- 否则页面新增仍从 1 起（如 AD00000001）与种子冲突报错。

CREATE OR REPLACE FUNCTION _flyway_sync_whale_sequence_from_column(
    p_table  TEXT,
    p_column TEXT DEFAULT 'bus_no'
) RETURNS VOID
    LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format(
        'INSERT INTO whale_sequence (seq_key, current_value, updated_at)
         SELECT s.tenant_id || '':'' || s.prefix, s.max_suffix, CURRENT_TIMESTAMP
         FROM (
             SELECT tenant_id,
                    regexp_replace(%I, ''[0-9]+$'', '''') AS prefix,
                    MAX(CAST(substring(%I FROM ''[0-9]+$'') AS BIGINT)) AS max_suffix
             FROM %I
             WHERE NOT is_deleted
               AND %I IS NOT NULL
               AND btrim(%I) <> ''''
               AND %I ~ ''^[A-Za-z]+[0-9]+$''
             GROUP BY tenant_id, regexp_replace(%I, ''[0-9]+$'', '''')
         ) s
         WHERE s.max_suffix > 0
         ON CONFLICT (seq_key) DO UPDATE
             SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
                 updated_at    = CURRENT_TIMESTAMP',
        p_column, p_column, p_table, p_column, p_column, p_column, p_column
    );
END;
$$;

SELECT _flyway_sync_whale_sequence_from_column('wkf_activity_definition');
SELECT _flyway_sync_whale_sequence_from_column('adm_device_definition');
SELECT _flyway_sync_whale_sequence_from_column('med_medication_definition');
SELECT _flyway_sync_whale_sequence_from_column('adm_specimen_definition');
SELECT _flyway_sync_whale_sequence_from_column('dia_body_structure');
SELECT _flyway_sync_whale_sequence_from_column('adm_patient');
SELECT _flyway_sync_whale_sequence_from_column('adm_practitioner');
SELECT _flyway_sync_whale_sequence_from_column('adm_organization');
SELECT _flyway_sync_whale_sequence_from_column('adm_location');
SELECT _flyway_sync_whale_sequence_from_column('cln_condition_definition', 'condition_code');
SELECT _flyway_sync_whale_sequence_from_column('cus_supplier');

DROP FUNCTION _flyway_sync_whale_sequence_from_column(TEXT, TEXT);
