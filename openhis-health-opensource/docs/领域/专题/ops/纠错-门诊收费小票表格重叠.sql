-- OPS: 门诊收费小票项目名换行压横线 / 账户余额列宽热修
-- 等价 Flyway：health/V15__prt_op_charge_table_overlap_fix.sql
-- 场景：op-charge-print
--
-- 用法：
--   1) 跑「1) 预览」
--   2) 跑「2) 修复」
--   3) 跑「3) 校验」

-- ========== 1) 预览 ==========
SELECT
    t.id,
    t.bus_no,
    t.print_json -> 'panels' -> 0 ->> 'paperMode' AS paper_mode,
    e.elem -> 'options' ->> 'field' AS field,
    e.elem -> 'options' ->> 'top' AS top,
    e.elem -> 'options' ->> 'left' AS left,
    e.elem -> 'options' ->> 'width' AS width,
    e.elem -> 'options' ->> 'tableBodyRowHeight' AS body_row_h,
    e.elem -> 'options' -> 'columns' -> 0 AS columns0
FROM prt_template t
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(t.print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
) AS e(elem)
WHERE COALESCE(t.is_deleted, false) = false
  AND t.scenario_code = 'op-charge-print'
  AND (
      e.elem -> 'options' ->> 'field' IN ('chargeItemLines', 'accountBalanceText')
      OR e.elem -> 'printElementType' ->> 'type' = 'hline'
  )
ORDER BY t.id, (e.elem -> 'options' ->> 'top')::numeric NULLS LAST;

-- ========== 2) 修复（与 V15 同口径） ==========
UPDATE prt_template
SET print_json = (
        WITH panel0 AS (
            SELECT COALESCE(print_json -> 'panels' -> 0, '{}'::jsonb) AS p
        ),
        panel_fixed AS (
            SELECT
                jsonb_set(
                    jsonb_set(
                        p,
                        '{paperMode}',
                        '"continuous"'::jsonb,
                        true
                    ),
                    '{continuousOptions}',
                    '{"tailPaddingMm":8,"maxSliceHeightMm":0,"repeatTableHeaderOnSlice":false}'::jsonb,
                    true
                ) AS p
            FROM panel0
        ),
        elems_fixed AS (
            SELECT COALESCE(
                (
                    SELECT jsonb_agg(fixed_elem ORDER BY ord)
                    FROM (
                        SELECT
                            t.ord,
                            CASE
                                WHEN t.elem -> 'options' ->> 'field' = 'chargeItemLines'
                                    AND t.elem -> 'printElementType' ->> 'type' = 'table'
                                    THEN jsonb_set(
                                        jsonb_set(
                                            jsonb_set(
                                                t.elem,
                                                '{options,tableBodyRowHeight}',
                                                '20'::jsonb
                                            ),
                                            '{options,lineHeight}',
                                            '16'::jsonb
                                        ),
                                        '{options,columns}',
                                        CASE
                                            WHEN jsonb_typeof(t.elem -> 'options' -> 'columns' -> 0) = 'array'
                                                THEN jsonb_build_array(
                                                    (
                                                        SELECT jsonb_agg(
                                                            CASE
                                                                WHEN col ->> 'field' = 'itemName'
                                                                    THEN jsonb_set(col, '{width}', '120'::jsonb)
                                                                WHEN col ->> 'field' = 'quantityText'
                                                                    THEN jsonb_set(col, '{width}', '28'::jsonb)
                                                                WHEN col ->> 'field' = 'amountText'
                                                                    THEN jsonb_set(col, '{width}', '40'::jsonb)
                                                                WHEN col ->> 'field' = 'chargeCategoryText'
                                                                    THEN jsonb_set(col, '{width}', '27.4'::jsonb)
                                                                ELSE col
                                                            END
                                                            ORDER BY c.ord
                                                        )
                                                        FROM jsonb_array_elements(
                                                            t.elem -> 'options' -> 'columns' -> 0
                                                        ) WITH ORDINALITY AS c(col, ord)
                                                    )
                                                )
                                            ELSE t.elem -> 'options' -> 'columns'
                                        END
                                    )
                                WHEN t.elem -> 'options' ->> 'field' = 'accountBalanceText'
                                    THEN jsonb_set(
                                        t.elem,
                                        '{options,width}',
                                        '107'::jsonb
                                    )
                                ELSE t.elem
                            END AS fixed_elem
                        FROM jsonb_array_elements(
                            COALESCE(print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
                        ) WITH ORDINALITY AS t(elem, ord)
                    ) s
                ),
                '[]'::jsonb
            ) AS elems
        )
        SELECT jsonb_set(
            print_json,
            '{panels,0}',
            jsonb_set(
                (SELECT p FROM panel_fixed),
                '{printElements}',
                (SELECT elems FROM elems_fixed)
            )
        )
    ),
    updated_at = NOW()
WHERE COALESCE(is_deleted, false) = false
  AND scenario_code = 'op-charge-print'
  AND print_json -> 'panels' -> 0 IS NOT NULL;

-- ========== 3) 校验（期望：paperMode=continuous；itemName width≈120；账户余额 width=107） ==========
SELECT
    t.id,
    t.bus_no,
    t.print_json -> 'panels' -> 0 ->> 'paperMode' AS paper_mode,
    e.elem -> 'options' ->> 'field' AS field,
    e.elem -> 'options' ->> 'width' AS width,
    e.elem -> 'options' ->> 'tableBodyRowHeight' AS body_row_h,
    (
        SELECT col ->> 'width'
        FROM jsonb_array_elements(e.elem -> 'options' -> 'columns' -> 0) AS col
        WHERE col ->> 'field' = 'itemName'
        LIMIT 1
    ) AS item_name_width
FROM prt_template t
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(t.print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
) AS e(elem)
WHERE COALESCE(t.is_deleted, false) = false
  AND t.scenario_code = 'op-charge-print'
  AND e.elem -> 'options' ->> 'field' IN ('chargeItemLines', 'accountBalanceText')
ORDER BY t.id, e.elem -> 'options' ->> 'field';
