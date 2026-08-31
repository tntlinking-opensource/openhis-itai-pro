-- whale_sequence seed sync (squashed baseline)
-- Replaces dynamic function from V99__whale_sequence_seed_sync.sql
-- Values computed for dev seed max bus_no; ON CONFLICT uses GREATEST

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':' || prefix, max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT tenant_id, regexp_replace(bus_no, '[0-9]+$', '') AS prefix,
           MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM adm_patient WHERE NOT is_deleted AND bus_no ~ '^[A-Za-z]+[0-9]+$' GROUP BY 1, 2
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
    SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
        updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':' || prefix, max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT tenant_id, regexp_replace(bus_no, '[0-9]+$', '') AS prefix,
           MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM adm_practitioner WHERE NOT is_deleted AND bus_no IS NOT NULL AND btrim(bus_no) <> '' AND bus_no ~ '^[A-Za-z]+[0-9]+$' GROUP BY 1, 2
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
    SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
        updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':' || prefix, max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT tenant_id, regexp_replace(bus_no, '[0-9]+$', '') AS prefix,
           MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM wkf_activity_definition WHERE NOT is_deleted AND bus_no ~ '^[A-Za-z]+[0-9]+$' GROUP BY 1, 2
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
    SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
        updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':' || prefix, max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT tenant_id, regexp_replace(condition_code, '[0-9]+$', '') AS prefix,
           MAX(CAST(substring(condition_code FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM cln_condition_definition WHERE NOT is_deleted AND condition_code ~ '^[A-Za-z]+[0-9]+$' GROUP BY 1, 2
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
    SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
        updated_at = CURRENT_TIMESTAMP;

-- med_medication_definition bus_no 序号（MD）
INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':MD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT
        tenant_id,
        MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM med_medication_definition
    WHERE NOT is_deleted
      AND tenant_id IS NOT NULL
      AND bus_no ~ '^MD[0-9]+$'
    GROUP BY tenant_id
) s
WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT 'MD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM med_medication_definition
    WHERE NOT is_deleted
      AND bus_no ~ '^MD[0-9]+$'
) s
WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

-- 机构/位置级联 bus_no 序号（OR / LO）：各段数字全局最大
INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':OR', max_seg, CURRENT_TIMESTAMP
FROM (
    SELECT
        o.tenant_id,
        MAX(CAST(substring(seg FROM '[0-9]+$') AS BIGINT)) AS max_seg
    FROM adm_organization o
    CROSS JOIN LATERAL unnest(string_to_array(o.bus_no, '.')) AS seg
    WHERE NOT o.is_deleted
      AND o.tenant_id IS NOT NULL
      AND o.bus_no IS NOT NULL
      AND btrim(o.bus_no) <> ''
      AND seg ~ '^OR[0-9]+$'
    GROUP BY o.tenant_id
) s
WHERE max_seg > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT 'OR', max_seg, CURRENT_TIMESTAMP
FROM (
    SELECT MAX(CAST(substring(seg FROM '[0-9]+$') AS BIGINT)) AS max_seg
    FROM adm_organization o
    CROSS JOIN LATERAL unnest(string_to_array(o.bus_no, '.')) AS seg
    WHERE NOT o.is_deleted
      AND o.bus_no IS NOT NULL
      AND btrim(o.bus_no) <> ''
      AND seg ~ '^OR[0-9]+$'
) s
WHERE max_seg > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':LO', max_seg, CURRENT_TIMESTAMP
FROM (
    SELECT
        l.tenant_id,
        MAX(CAST(substring(seg FROM '[0-9]+$') AS BIGINT)) AS max_seg
    FROM adm_location l
    CROSS JOIN LATERAL unnest(string_to_array(l.bus_no, '.')) AS seg
    WHERE NOT l.is_deleted
      AND l.tenant_id IS NOT NULL
      AND l.bus_no IS NOT NULL
      AND btrim(l.bus_no) <> ''
      AND seg ~ '^LO[0-9]+$'
    GROUP BY l.tenant_id
) s
WHERE max_seg > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT 'LO', max_seg, CURRENT_TIMESTAMP
FROM (
    SELECT MAX(CAST(substring(seg FROM '[0-9]+$') AS BIGINT)) AS max_seg
    FROM adm_location l
    CROSS JOIN LATERAL unnest(string_to_array(l.bus_no, '.')) AS seg
    WHERE NOT l.is_deleted
      AND l.bus_no IS NOT NULL
      AND btrim(l.bus_no) <> ''
      AND seg ~ '^LO[0-9]+$'
) s
WHERE max_seg > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

-- 同步诊疗/耗材定义 bus_no 序号（原 V110 回并）：避免导入时报业务编码冲突或落库重复
-- 迁移/直写 AD*、DD* 时不会推进 whale_sequence；上方 activity 基线扫过后导入仍可能落后；DD 此前未覆盖

-- AD：tenant 隔离键
INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':AD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT
        tenant_id,
        MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM wkf_activity_definition
    WHERE NOT is_deleted
      AND tenant_id IS NOT NULL
      AND bus_no ~ '^AD[0-9]+$'
    GROUP BY tenant_id
) s
WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

-- AD：无租户前缀键
INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT 'AD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM wkf_activity_definition
    WHERE NOT is_deleted
      AND bus_no ~ '^AD[0-9]+$'
) s
WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

-- DD：tenant 隔离键
INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':DD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT
        tenant_id,
        MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM adm_device_definition
    WHERE NOT is_deleted
      AND tenant_id IS NOT NULL
      AND bus_no ~ '^DD[0-9]+$'
    GROUP BY tenant_id
) s
WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

-- DD：无租户前缀键
INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT 'DD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM adm_device_definition
    WHERE NOT is_deleted
      AND bus_no ~ '^DD[0-9]+$'
) s
WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

-- SP：供应商/生产厂家共用前缀（含 health-dev 种子 SP0000201+）
INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':SP', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT
        tenant_id,
        MAX(CAST(substring(bus_no FROM 3) AS BIGINT)) AS max_suffix
    FROM cus_supplier
    WHERE NOT is_deleted
      AND tenant_id IS NOT NULL
      AND bus_no ~ '^SP[0-9]+$'
    GROUP BY tenant_id
) s
WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT 'SP', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT MAX(CAST(substring(bus_no FROM 3) AS BIGINT)) AS max_suffix
    FROM cus_supplier
    WHERE NOT is_deleted
      AND bus_no ~ '^SP[0-9]+$'
) s
WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;
