-- OPS: 门诊收费小票「医保个账 / 微信实收」文案分层热修
-- 等价 Flyway：health/V16__prt_op_charge_pay_label_clarify.sql
-- 场景：op-charge-print
--
-- 用法：1) 预览 → 2) 修复 → 3) 校验

-- ========== 1) 预览 ==========
SELECT
    t.id,
    t.bus_no,
    e.elem -> 'options' ->> 'field' AS field,
    e.elem -> 'options' ->> 'title' AS title
FROM prt_template t
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(t.print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
) AS e(elem)
WHERE COALESCE(t.is_deleted, false) = false
  AND t.scenario_code = 'op-charge-print'
  AND e.elem -> 'options' ->> 'field' IN (
      'accountPayText', 'cashPayText', 'wechatPayText', 'alipayPayText', 'accountBalanceText'
  )
ORDER BY t.id, e.elem -> 'options' ->> 'field';

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
                        WHEN t.elem -> 'options' ->> 'field' = 'accountPayText'
                            THEN jsonb_set(t.elem, '{options,title}', '"医保个人账户"'::jsonb)
                        WHEN t.elem -> 'options' ->> 'field' = 'cashPayText'
                            THEN jsonb_set(t.elem, '{options,title}', '"现金实收"'::jsonb)
                        WHEN t.elem -> 'options' ->> 'field' = 'wechatPayText'
                            THEN jsonb_set(t.elem, '{options,title}', '"微信实收"'::jsonb)
                        WHEN t.elem -> 'options' ->> 'field' = 'alipayPayText'
                            THEN jsonb_set(t.elem, '{options,title}', '"支付宝实收"'::jsonb)
                        WHEN t.elem -> 'options' ->> 'field' = 'accountBalanceText'
                            THEN jsonb_set(t.elem, '{options,title}', '"医保个账余额"'::jsonb)
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
  AND scenario_code = 'op-charge-print'
  AND print_json -> 'panels' -> 0 -> 'printElements' IS NOT NULL;

-- ========== 3) 校验 ==========
SELECT
    t.id,
    t.bus_no,
    e.elem -> 'options' ->> 'field' AS field,
    e.elem -> 'options' ->> 'title' AS title
FROM prt_template t
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(t.print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
) AS e(elem)
WHERE COALESCE(t.is_deleted, false) = false
  AND t.scenario_code = 'op-charge-print'
  AND e.elem -> 'options' ->> 'field' IN (
      'accountPayText', 'cashPayText', 'wechatPayText', 'alipayPayText', 'accountBalanceText'
  )
ORDER BY t.id, e.elem -> 'options' ->> 'field';
