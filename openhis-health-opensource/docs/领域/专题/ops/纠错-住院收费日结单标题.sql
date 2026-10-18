-- OPS: 收费日结单标题由写死「门诊」改为绑定 documentTitle
-- 等价 Flyway：health/V11__prt_daily_settlement_document_title.sql
-- 场景：op-dailySettlement-print（门诊/住院共用）
--
-- 用法：1) 预览 → 2) 修复 → 3) 校验
-- 前端需同步传入 documentTitle：门诊收费日结单 / 住院收费日结单

-- ========== 1) 预览 ==========
SELECT
    t.id,
    t.bus_no,
    t.name,
    e.elem -> 'options' ->> 'field' AS field,
    e.elem -> 'options' ->> 'title' AS title,
    e.elem -> 'options' ->> 'hideTitle' AS hide_title,
    e.elem -> 'options' ->> 'fontSize' AS font_size
FROM prt_template t
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(t.print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
) AS e(elem)
WHERE COALESCE(t.is_deleted, false) = false
  AND t.scenario_code = 'op-dailySettlement-print'
  AND (
      e.elem -> 'options' ->> 'field' = 'documentTitle'
      OR COALESCE(e.elem -> 'options' ->> 'title', '') LIKE '%日结%'
  )
ORDER BY t.id, (e.elem -> 'options' ->> 'top')::numeric NULLS LAST;

-- ========== 2) 修复 ==========
UPDATE prt_template
SET print_json = jsonb_set(
        print_json,
        '{panels,0,printElements}',
        (
            SELECT COALESCE(jsonb_agg(fixed_elem ORDER BY ord), '[]'::jsonb)
            FROM (
                SELECT
                    t.ord,
                    CASE
                        WHEN COALESCE(t.elem -> 'options' ->> 'field', '') IN ('', 'documentTitle')
                         AND (
                             COALESCE(t.elem -> 'options' ->> 'title', '') IN (
                                 '门诊收费日结单', '门诊费用日结单', '住院收费日结单', '单据标题'
                             )
                             OR COALESCE(t.elem -> 'options' ->> 'title', '') LIKE '%门诊%日结%'
                             OR t.elem -> 'options' ->> 'field' = 'documentTitle'
                         )
                            THEN jsonb_set(
                                jsonb_set(
                                    jsonb_set(
                                        jsonb_set(t.elem, '{options,field}', '"documentTitle"'::jsonb),
                                        '{options,hideTitle}',
                                        'true'::jsonb
                                    ),
                                    '{options,testData}',
                                    '"门诊收费日结单"'::jsonb
                                ),
                                '{options,title}',
                                '"单据标题"'::jsonb
                            )
                        ELSE t.elem
                    END AS fixed_elem
                FROM jsonb_array_elements(
                    COALESCE(print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
                ) WITH ORDINALITY AS t(elem, ord)
            ) s
        )
    ),
    updated_at = NOW()
WHERE COALESCE(is_deleted, false) = false
  AND scenario_code = 'op-dailySettlement-print'
  AND print_json -> 'panels' -> 0 -> 'printElements' IS NOT NULL;

-- ========== 3) 校验 ==========
SELECT
    t.id,
    t.bus_no,
    e.elem -> 'options' ->> 'field' AS field,
    e.elem -> 'options' ->> 'title' AS title,
    e.elem -> 'options' ->> 'hideTitle' AS hide_title
FROM prt_template t
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(t.print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
) AS e(elem)
WHERE COALESCE(t.is_deleted, false) = false
  AND t.scenario_code = 'op-dailySettlement-print'
  AND (
      e.elem -> 'options' ->> 'field' = 'documentTitle'
      OR COALESCE(e.elem -> 'options' ->> 'title', '') LIKE '%日结%'
  )
ORDER BY t.id;
