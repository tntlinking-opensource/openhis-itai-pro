-- PEIS V7：学生健康档案（BAS-05 / AS-PEIS-ARCH-001）
CREATE TABLE IF NOT EXISTS peis_patient_health_archive (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT,
    patient_id BIGINT NOT NULL,
    past_history TEXT,
    family_history TEXT,
    habit_summary TEXT,
    allergy_summary TEXT,
    remark TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_peis_health_archive_patient
    ON peis_patient_health_archive (tenant_id, patient_id)
    WHERE is_deleted = false;
COMMENT ON TABLE peis_patient_health_archive IS '体检健康档案（既往史/习惯等）';
