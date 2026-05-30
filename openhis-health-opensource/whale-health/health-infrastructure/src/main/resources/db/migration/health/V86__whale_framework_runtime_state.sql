-- ========== whale_framework_runtime_state ==========
CREATE TABLE IF NOT EXISTS whale_framework_runtime_state (
    id BIGINT PRIMARY KEY,
    insight_code VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    runtime_version VARCHAR(64),
    operator_user_id BIGINT,
    session_started_at TIMESTAMP,
    org_category VARCHAR(128),
    org_name VARCHAR(256),
    deployment_fingerprint VARCHAR(128),
    license_customer VARCHAR(256),
    license_not_after TIMESTAMP,
    payload_json TEXT,
    observed_at TIMESTAMP,
    received_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fw_runtime_state_insight_code ON whale_framework_runtime_state(insight_code);

CREATE INDEX IF NOT EXISTS idx_fw_runtime_state_fingerprint ON whale_framework_runtime_state(deployment_fingerprint);

CREATE INDEX IF NOT EXISTS idx_fw_runtime_state_received_at ON whale_framework_runtime_state(received_at DESC);
