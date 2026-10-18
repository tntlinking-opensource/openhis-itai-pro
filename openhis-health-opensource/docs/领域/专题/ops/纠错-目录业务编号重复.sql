-- 目录 bus_no 重复排查与修复（手工执行，勿并入自动 Flyway）
-- 口径：同租户 bus_no 唯一（不是机构内）；目录 org_id 统一挂医院
-- 策略：同 bus_no 保留 id 最小的一条，其余改发新号；再把 org_id 归一到医院
-- 执行前请先备份

-- ========== 1. 排查重复（租户内全局） ==========
SELECT 'MD' AS kind, bus_no, COUNT(*) AS cnt, array_agg(id ORDER BY id) AS ids
FROM med_medication_definition
WHERE NOT is_deleted AND bus_no IS NOT NULL AND btrim(bus_no) <> ''
GROUP BY bus_no
HAVING COUNT(*) > 1

UNION ALL
SELECT 'AD', bus_no, COUNT(*), array_agg(id ORDER BY id)
FROM wkf_activity_definition
WHERE NOT is_deleted AND bus_no IS NOT NULL AND btrim(bus_no) <> ''
GROUP BY bus_no
HAVING COUNT(*) > 1

UNION ALL
SELECT 'DD', bus_no, COUNT(*), array_agg(id ORDER BY id)
FROM adm_device_definition
WHERE NOT is_deleted AND bus_no IS NOT NULL AND btrim(bus_no) <> ''
GROUP BY bus_no
HAVING COUNT(*) > 1
ORDER BY kind, bus_no;

-- ========== 2. 修复药品（同租户全局唯一） ==========
-- 请将下方 HOSPITAL_ID 替换为当前医院 adm_organization.id（type_enum=HOSPITAL）
-- 朝阳中医院示例：1784931337878001

WITH dup AS (
    SELECT id, bus_no,
           ROW_NUMBER() OVER (PARTITION BY bus_no ORDER BY id) AS rn
    FROM med_medication_definition
    WHERE NOT is_deleted AND bus_no IS NOT NULL AND btrim(bus_no) <> ''
),
victims AS (
    SELECT id FROM dup WHERE rn > 1
),
max_suf AS (
    SELECT COALESCE(MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)), 0) AS m
    FROM med_medication_definition
    WHERE NOT is_deleted AND bus_no ~ '^MD[0-9]+$'
),
numbered AS (
    SELECT v.id, ROW_NUMBER() OVER (ORDER BY v.id) AS seq
    FROM victims v
)
UPDATE med_medication_definition d
SET bus_no = 'MD' || lpad((max_suf.m + numbered.seq)::text, 8, '0'),
    updated_at = CURRENT_TIMESTAMP
FROM numbered, max_suf
WHERE d.id = numbered.id;

-- ========== 3. 修复诊疗 ==========
WITH dup AS (
    SELECT id, bus_no,
           ROW_NUMBER() OVER (PARTITION BY bus_no ORDER BY id) AS rn
    FROM wkf_activity_definition
    WHERE NOT is_deleted AND bus_no IS NOT NULL AND btrim(bus_no) <> ''
),
victims AS (
    SELECT id FROM dup WHERE rn > 1
),
max_suf AS (
    SELECT COALESCE(MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)), 0) AS m
    FROM wkf_activity_definition
    WHERE NOT is_deleted AND bus_no ~ '^AD[0-9]+$'
),
numbered AS (
    SELECT v.id, ROW_NUMBER() OVER (ORDER BY v.id) AS seq
    FROM victims v
)
UPDATE wkf_activity_definition d
SET bus_no = 'AD' || lpad((max_suf.m + numbered.seq)::text, 8, '0'),
    updated_at = CURRENT_TIMESTAMP
FROM numbered, max_suf
WHERE d.id = numbered.id;

-- ========== 4. 修复耗材 ==========
WITH dup AS (
    SELECT id, bus_no,
           ROW_NUMBER() OVER (PARTITION BY bus_no ORDER BY id) AS rn
    FROM adm_device_definition
    WHERE NOT is_deleted AND bus_no IS NOT NULL AND btrim(bus_no) <> ''
),
victims AS (
    SELECT id FROM dup WHERE rn > 1
),
max_suf AS (
    SELECT COALESCE(MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)), 0) AS m
    FROM adm_device_definition
    WHERE NOT is_deleted AND bus_no ~ '^DD[0-9]+$'
),
numbered AS (
    SELECT v.id, ROW_NUMBER() OVER (ORDER BY v.id) AS seq
    FROM victims v
)
UPDATE adm_device_definition d
SET bus_no = 'DD' || lpad((max_suf.m + numbered.seq)::text, 8, '0'),
    updated_at = CURRENT_TIMESTAMP
FROM numbered, max_suf
WHERE d.id = numbered.id;

-- ========== 5. 目录 org_id 统一挂医院 ==========
-- 将 HOSPITAL_ID 替换为医院 id
UPDATE med_medication_definition
SET org_id = 1784931337878001,
    updated_at = CURRENT_TIMESTAMP
WHERE NOT is_deleted
  AND (org_id IS DISTINCT FROM 1784931337878001);

UPDATE wkf_activity_definition
SET org_id = 1784931337878001,
    updated_at = CURRENT_TIMESTAMP
WHERE NOT is_deleted
  AND (org_id IS NULL OR org_id IS DISTINCT FROM 1784931337878001);

UPDATE adm_device_definition
SET org_id = 1784931337878001,
    updated_at = CURRENT_TIMESTAMP
WHERE NOT is_deleted
  AND (org_id IS NULL OR org_id IS DISTINCT FROM 1784931337878001);

-- ========== 6. 改号后抬升 whale_sequence ==========
INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':MD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT tenant_id, MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM med_medication_definition
    WHERE NOT is_deleted AND tenant_id IS NOT NULL AND bus_no ~ '^MD[0-9]+$'
    GROUP BY tenant_id
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT 'MD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM med_medication_definition
    WHERE NOT is_deleted AND bus_no ~ '^MD[0-9]+$'
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':AD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT tenant_id, MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM wkf_activity_definition
    WHERE NOT is_deleted AND tenant_id IS NOT NULL AND bus_no ~ '^AD[0-9]+$'
    GROUP BY tenant_id
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT 'AD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM wkf_activity_definition
    WHERE NOT is_deleted AND bus_no ~ '^AD[0-9]+$'
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT tenant_id || ':DD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT tenant_id, MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM adm_device_definition
    WHERE NOT is_deleted AND tenant_id IS NOT NULL AND bus_no ~ '^DD[0-9]+$'
    GROUP BY tenant_id
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO whale_sequence (seq_key, current_value, updated_at)
SELECT 'DD', max_suffix, CURRENT_TIMESTAMP
FROM (
    SELECT MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) AS max_suffix
    FROM adm_device_definition
    WHERE NOT is_deleted AND bus_no ~ '^DD[0-9]+$'
) s WHERE max_suffix > 0
ON CONFLICT (seq_key) DO UPDATE
SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
    updated_at = CURRENT_TIMESTAMP;
