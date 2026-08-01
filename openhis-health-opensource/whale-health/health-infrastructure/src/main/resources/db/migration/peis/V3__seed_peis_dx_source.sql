-- 体检诊断目录来源：condition_definition_source / peis_dx
-- 与院内诊断 hospital_dx 隔离，不纳入医生站西医检索默认集合

INSERT INTO "ter_concept" (
    "id", "code_system_id", "code", "display", "definition", "parent_id", "sort_order",
    "is_active", "css_class", "list_class", "is_default", "tenant_id",
    "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by"
)
VALUES
(
    '900006034',
    '279125561775755266',
    'peis_dx',
    '体检诊断',
    'PEIS 体检诊断建议库（不进医生站开诊断候选）',
    NULL,
    '10',
    true,
    NULL,
    'default',
    NULL,
    '1',
    TIMESTAMP '2026-07-19 00:00:00+00',
    TIMESTAMP '2026-07-19 00:00:00+00',
    '1',
    '1',
    false,
    NULL,
    NULL
)
ON CONFLICT ("id") DO UPDATE SET
    code = EXCLUDED.code,
    display = EXCLUDED.display,
    definition = EXCLUDED.definition,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;
