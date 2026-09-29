-- V6：文书书写科室/业务版本/归档元数据 + 文书编辑申请（DM-EMR-001 / DM-EMR-002）
-- 状态 code 统一大写：DRAFT / PENDING_ARCHIVE / ARCHIVED；编辑申请 DRAFTING / PENDING_REVIEW / APPROVED / REJECTED

ALTER TABLE emr_document
    ADD COLUMN IF NOT EXISTS organization_id BIGINT,
    ADD COLUMN IF NOT EXISTS version_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS archive_submitted_by BIGINT,
    ADD COLUMN IF NOT EXISTS archive_submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS archive_approved_by BIGINT,
    ADD COLUMN IF NOT EXISTS archive_approved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS archive_reject_reason VARCHAR(500);

COMMENT ON COLUMN emr_document.organization_id IS '书写时科室（创建写入；转科不改写）';
COMMENT ON COLUMN emr_document.version_no IS '业务版本：创建=1；首次归档不升；编辑审核通过+1';
COMMENT ON COLUMN emr_document.archive_submitted_by IS '提交归档人';
COMMENT ON COLUMN emr_document.archive_submitted_at IS '提交归档时间';
COMMENT ON COLUMN emr_document.archive_approved_by IS '归档审批同意人';
COMMENT ON COLUMN emr_document.archive_approved_at IS '归档审批同意时间';
COMMENT ON COLUMN emr_document.archive_reject_reason IS '归档驳回原因';

CREATE INDEX IF NOT EXISTS idx_emr_document_organization
    ON emr_document (organization_id);

-- 状态迁移：对齐 DRAFT / PENDING_ARCHIVE / ARCHIVED（大写；兼容历史大小写与别名）
UPDATE emr_document SET status = 'DRAFT'
WHERE status IS NULL OR UPPER(TRIM(status)) = 'DRAFT';
UPDATE emr_document SET status = 'PENDING_ARCHIVE'
WHERE UPPER(TRIM(status)) IN ('SUBMITTED', 'PENDING_ARCHIVE');
UPDATE emr_document SET status = 'ARCHIVED'
WHERE UPPER(TRIM(status)) IN ('LOCKED', 'ARCHIVED');
-- 兜底：其余未知态一律 DRAFT，保证跑完本脚本后仅剩三态大写
UPDATE emr_document SET status = 'DRAFT'
WHERE status IS NULL
   OR UPPER(TRIM(status)) NOT IN ('DRAFT', 'PENDING_ARCHIVE', 'ARCHIVED');

COMMENT ON COLUMN emr_document.status IS 'DRAFT / PENDING_ARCHIVE / ARCHIVED';

CREATE TABLE IF NOT EXISTS emr_document_edit_application (
    id                    BIGINT       NOT NULL PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP,
    created_by            BIGINT,
    updated_by            BIGINT,
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMP,
    deleted_by            BIGINT,
    document_id           BIGINT       NOT NULL,
    encounter_id          VARCHAR(64)  NOT NULL,
    organization_id       BIGINT       NOT NULL,
    encounter_class_code  VARCHAR(32),
    status_enum           VARCHAR(32)  NOT NULL,
    apply_reason          VARCHAR(500) NOT NULL,
    reject_reason         VARCHAR(500),
    from_version          INTEGER      NOT NULL,
    to_version            INTEGER,
    old_content_json      TEXT         NOT NULL,
    new_content_json      TEXT         NOT NULL,
    applicant_id          BIGINT       NOT NULL,
    applied_at            TIMESTAMP    NOT NULL,
    submitted_at          TIMESTAMP,
    reviewer_id           BIGINT,
    reviewed_at           TIMESTAMP
);

COMMENT ON TABLE emr_document_edit_application IS '文书编辑申请（EMR 域；与病案首页编辑申请分表）';
COMMENT ON COLUMN emr_document_edit_application.document_id IS 'emr_document.id';
COMMENT ON COLUMN emr_document_edit_application.status_enum IS 'DRAFTING / PENDING_REVIEW / APPROVED / REJECTED';
COMMENT ON COLUMN emr_document_edit_application.from_version IS '创建时文书 version_no';
COMMENT ON COLUMN emr_document_edit_application.to_version IS '交审后目标版本';
COMMENT ON COLUMN emr_document_edit_application.old_content_json IS '创建时文书快照';
COMMENT ON COLUMN emr_document_edit_application.new_content_json IS '工作稿';

CREATE UNIQUE INDEX IF NOT EXISTS uk_emr_doc_edit_active
    ON emr_document_edit_application (tenant_id, document_id)
    WHERE is_deleted = FALSE
      AND status_enum IN ('DRAFTING', 'PENDING_REVIEW');

CREATE INDEX IF NOT EXISTS idx_emr_doc_edit_review
    ON emr_document_edit_application (tenant_id, status_enum, submitted_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_emr_doc_edit_document
    ON emr_document_edit_application (document_id)
    WHERE is_deleted = FALSE;
