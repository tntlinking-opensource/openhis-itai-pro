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
