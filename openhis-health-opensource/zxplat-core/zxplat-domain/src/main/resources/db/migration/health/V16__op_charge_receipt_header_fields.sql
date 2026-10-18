-- 门诊收费小票抬头增加：门诊号、挂号时间、开方科室、开方医生。
-- 原费用明细及以下元素下移 64pt，避免与新行重叠。
-- 收费员、收费时间随原页脚一起下移；若模板里没有这两项，补在最下方。

UPDATE prt_template
SET print_json = (
        WITH elems AS (
            SELECT COALESCE(print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb) AS arr
        ),
        shifted AS (
            SELECT COALESCE(
                (
                    SELECT jsonb_agg(
                        CASE
                            WHEN (elem -> 'options' ->> 'top') ~ '^[0-9]+(\.[0-9]+)?$'
                                 AND (elem -> 'options' ->> 'top')::numeric >= 80
                            THEN jsonb_set(
                                elem,
                                '{options,top}',
                                to_jsonb((elem -> 'options' ->> 'top')::numeric + 64)
                            )
                            ELSE elem
                        END
                        ORDER BY ord
                    )
                    FROM jsonb_array_elements((SELECT arr FROM elems)) WITH ORDINALITY AS t(elem, ord)
                ),
                '[]'::jsonb
            ) AS arr
        ),
        extra AS (
            SELECT $json$[
                {"options":{"top":80,"left":5.7,"field":"encounterBusNo","title":"门诊号","width":215.4,"height":14,"fontSize":9,"textAlign":"left","fontWeight":"normal"},"printElementType":{"type":"text","title":"门诊号"}},
                {"options":{"top":96,"left":5.7,"field":"registerTimeText","title":"挂号时间","width":215.4,"height":14,"fontSize":9,"textAlign":"left","fontWeight":"normal"},"printElementType":{"type":"text","title":"挂号时间"}},
                {"options":{"top":112,"left":5.7,"field":"orderingDepartmentName","title":"开方科室","width":215.4,"height":14,"fontSize":9,"textAlign":"left","fontWeight":"normal"},"printElementType":{"type":"text","title":"开方科室"}},
                {"options":{"top":128,"left":5.7,"field":"orderingDoctorName","title":"开方医生","width":215.4,"height":14,"fontSize":9,"textAlign":"left","fontWeight":"normal"},"printElementType":{"type":"text","title":"开方医生"}}
            ]$json$::jsonb AS arr
        )
        SELECT jsonb_set(
            print_json,
            '{panels,0,printElements}',
            (SELECT arr FROM shifted) || (SELECT arr FROM extra)
        )
    ),
    sample_print_data = COALESCE(sample_print_data, '{}'::jsonb) || $json${
        "encounterBusNo":"MZ00001234",
        "registerTimeText":"2026-10-13 08:30:00",
        "orderingDepartmentName":"内科门诊",
        "orderingDoctorName":"李医生",
        "cashierName":"李收费",
        "chargeTimeText":"2026-10-13 09:15:00"
    }$json$::jsonb,
    version = COALESCE(version, 0) + 1,
    updated_at = TIMESTAMP '2026-10-13 12:20:00+08'
WHERE bus_no = 'PT202606260005'
  AND COALESCE(is_deleted, false) = false
  AND scenario_code = 'op-charge-print'
  AND print_json -> 'panels' -> 0 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM jsonb_array_elements(
          COALESCE(print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
      ) AS elem
      WHERE elem -> 'options' ->> 'field' = 'registerTimeText'
  );

-- 收费员、收费时间缺失时补在当前内容最下方（标准模板已有，本句不改坐标）
UPDATE prt_template AS tpl
SET print_json = jsonb_set(
        tpl.print_json,
        '{panels,0,printElements}',
        COALESCE(tpl.print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
            || COALESCE(missing.elems, '[]'::jsonb)
    ),
    version = COALESCE(tpl.version, 0) + 1,
    updated_at = TIMESTAMP '2026-10-13 12:20:00+08'
FROM (
    SELECT
        id,
        (
            SELECT jsonb_agg(elem ORDER BY ord)
            FROM (
                SELECT
                    1 AS ord,
                    jsonb_build_object(
                        'options', jsonb_build_object(
                            'top', base_top,
                            'left', 5.7,
                            'field', 'cashierName',
                            'title', '收费员',
                            'width', 215.4,
                            'height', 14,
                            'fontSize', 9,
                            'textAlign', 'left',
                            'fontWeight', 'normal'
                        ),
                        'printElementType', jsonb_build_object('type', 'text', 'title', '收费员')
                    ) AS elem
                WHERE NOT has_cashier
                UNION ALL
                SELECT
                    2 AS ord,
                    jsonb_build_object(
                        'options', jsonb_build_object(
                            'top', CASE WHEN has_cashier THEN base_top ELSE base_top + 16 END,
                            'left', 5.7,
                            'field', 'chargeTimeText',
                            'title', '收费时间',
                            'width', 215.4,
                            'height', 14,
                            'fontSize', 9,
                            'textAlign', 'left',
                            'fontWeight', 'normal'
                        ),
                        'printElementType', jsonb_build_object('type', 'text', 'title', '收费时间')
                    ) AS elem
                WHERE NOT has_charge_time
            ) AS rows
        ) AS elems
    FROM (
        SELECT
            id,
            COALESCE((
                SELECT MAX((elem -> 'options' ->> 'top')::numeric)
                FROM jsonb_array_elements(
                    COALESCE(print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
                ) AS elem
                WHERE (elem -> 'options' ->> 'top') ~ '^[0-9]+(\.[0-9]+)?$'
            ), 128) + 16 AS base_top,
            EXISTS (
                SELECT 1
                FROM jsonb_array_elements(
                    COALESCE(print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
                ) AS elem
                WHERE elem -> 'options' ->> 'field' = 'cashierName'
            ) AS has_cashier,
            EXISTS (
                SELECT 1
                FROM jsonb_array_elements(
                    COALESCE(print_json -> 'panels' -> 0 -> 'printElements', '[]'::jsonb)
                ) AS elem
                WHERE elem -> 'options' ->> 'field' = 'chargeTimeText'
            ) AS has_charge_time
        FROM prt_template
        WHERE bus_no = 'PT202606260005'
          AND COALESCE(is_deleted, false) = false
          AND scenario_code = 'op-charge-print'
          AND print_json -> 'panels' -> 0 IS NOT NULL
    ) AS flags
    WHERE NOT has_cashier OR NOT has_charge_time
) AS missing
WHERE tpl.id = missing.id
  AND missing.elems IS NOT NULL;
