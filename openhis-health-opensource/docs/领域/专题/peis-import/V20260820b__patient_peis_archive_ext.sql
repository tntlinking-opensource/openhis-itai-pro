-- 已落地库手工执行：推荐人/早餐改挂 peis_encounter_ext；档案仅保留标签
-- 对应 Flyway：peis/V4（档案 tag + encounter 推荐人/早餐）；health/V13 学历 education_level_code

ALTER TABLE adm_patient
    ADD COLUMN IF NOT EXISTS education_level_code varchar(32);

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'adm_patient'
      AND column_name = 'education_level_enum'
  ) THEN
    EXECUTE $u$
      UPDATE adm_patient
      SET education_level_code = COALESCE(education_level_code, education_level_enum)
      WHERE education_level_enum IS NOT NULL
    $u$;
    ALTER TABLE adm_patient DROP COLUMN education_level_enum;
  END IF;
END $$;

COMMENT ON COLUMN adm_patient.education_level_code IS
    '学历层次字典编码（EducationLevel，如 PREPARATORY/UNDERGRADUATE/POSTGRADUATE/DOCTORAL）';

ALTER TABLE peis_patient_health_archive
    ADD COLUMN IF NOT EXISTS tag_code varchar(64);

COMMENT ON COLUMN peis_patient_health_archive.tag_code IS '体检标签（如 ADULT成人 / CHILD儿童）';

ALTER TABLE peis_encounter_ext
    ADD COLUMN IF NOT EXISTS referrer_name varchar(128);

ALTER TABLE peis_encounter_ext
    ADD COLUMN IF NOT EXISTS breakfast_flag boolean;

COMMENT ON COLUMN peis_encounter_ext.referrer_name IS '本次体检推荐人';
COMMENT ON COLUMN peis_encounter_ext.breakfast_flag IS '本次体检是否提供早餐（true有 / false无 / null未填）';

ALTER TABLE peis_patient_health_archive DROP COLUMN IF EXISTS referrer_name;
ALTER TABLE peis_patient_health_archive DROP COLUMN IF EXISTS breakfast_flag;
