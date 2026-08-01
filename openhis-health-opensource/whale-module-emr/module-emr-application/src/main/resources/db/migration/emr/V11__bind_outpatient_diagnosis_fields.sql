-- 门诊病历「西医诊断 / 中医诊断」：由手工录入改为就诊诊断元数据预填
-- 套模板/打开文书时，字段为空则从 diagnosis.westernDisplay / diagnosis.tcmDisplay 带出；无诊断时保持空槽位
UPDATE emr_metadata_field_def
SET value_source = 'DIAGNOSIS',
    source_path  = 'diagnosis.westernDisplay',
    updated_at   = TIMESTAMP '2026-07-29 00:00:00+00',
    updated_by   = 1
WHERE field_key = 'westernDiagnosis'
  AND is_deleted = FALSE;

UPDATE emr_metadata_field_def
SET value_source = 'DIAGNOSIS',
    source_path  = 'diagnosis.tcmDisplay',
    updated_at   = TIMESTAMP '2026-07-29 00:00:00+00',
    updated_by   = 1
WHERE field_key = 'tcmDiagnosis'
  AND is_deleted = FALSE;
