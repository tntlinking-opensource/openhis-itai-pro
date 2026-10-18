-- 一次性：体检 PEIS 项目补齐包装/计价单位（次）
-- 可与 health Flyway V14 二选一执行；重复执行安全。
-- 术语 unit_code：code=6 → 次。

UPDATE wkf_activity_definition
SET package_unit_code = '6',
    updated_at = NOW()
WHERE category_code = 'PEIS'
  AND is_deleted = FALSE
  AND (package_unit_code IS NULL OR btrim(package_unit_code) = '');

UPDATE wkf_activity_definition
SET def_encounter_unit_code = '6',
    updated_at = NOW()
WHERE category_code = 'PEIS'
  AND is_deleted = FALSE
  AND (def_encounter_unit_code IS NULL OR btrim(def_encounter_unit_code) = '');
