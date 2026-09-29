-- 诊疗目录类别 activity_category_code=53「诊查费」
-- 与 ActivityDefinition.category_code、吉林省医保诊查费目录导入对齐；
-- 幂等：先按业务键 (code_system_id, code) 判重插入，再纠偏文案；样式对齐 V10「嘱托」种子。

INSERT INTO "ter_concept" (
    "id", "code_system_id", "code", "display", "definition", "parent_id", "sort_order",
    "is_active", "css_class", "list_class", "is_default",
    "tenant_id", "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "deleted_at", "deleted_by"
)
SELECT
    '3682', '255', '53', '诊查费', '门诊/急诊/住院/互联网诊查费目录类别', NULL, '32',
    true, NULL, 'default', NULL,
    '1', '2026-09-25 02:00:00.000+00', NULL, '1', '1',
    false, NULL, NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM "ter_concept" c
    WHERE c."code_system_id" = 255
      AND c."code" = '53'
      AND c."is_deleted" = false
);

-- 纠偏：已存在（含 id≠3682 的手工行）统一 display / 启用态
UPDATE "ter_concept"
SET "display" = '诊查费',
    "definition" = '门诊/急诊/住院/互联网诊查费目录类别',
    "sort_order" = 32,
    "is_active" = true,
    "is_deleted" = false,
    "deleted_at" = NULL,
    "deleted_by" = NULL,
    "updated_at" = CURRENT_TIMESTAMP,
    "updated_by" = 1
WHERE "code_system_id" = 255
  AND "code" = '53';
