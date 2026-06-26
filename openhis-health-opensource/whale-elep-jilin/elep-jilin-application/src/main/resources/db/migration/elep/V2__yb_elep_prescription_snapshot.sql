-- 电子处方快照：再开真源

CREATE TABLE IF NOT EXISTS yb_elep_prescription_snapshot (
    prescription_id         BIGINT       NOT NULL PRIMARY KEY,
    upload_request_json     TEXT,
    upload_response_json    TEXT,
    sign_response_json      TEXT,
    upload_confirm_json     TEXT,
    pdf_file_path           VARCHAR(512),
    pdf_file_hash           VARCHAR(128),

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);
COMMENT ON TABLE yb_elep_prescription_snapshot IS '医保电子处方处方快照';
COMMENT ON COLUMN yb_elep_prescription_snapshot.prescription_id IS '处方标识';
COMMENT ON COLUMN yb_elep_prescription_snapshot.upload_request_json IS '上传申请JSON';
COMMENT ON COLUMN yb_elep_prescription_snapshot.upload_response_json IS '上传responseJSON';
COMMENT ON COLUMN yb_elep_prescription_snapshot.sign_response_json IS '体征responseJSON';
COMMENT ON COLUMN yb_elep_prescription_snapshot.upload_confirm_json IS '上传confirmJSON';
COMMENT ON COLUMN yb_elep_prescription_snapshot.pdf_file_path IS 'PDF文件路径';
COMMENT ON COLUMN yb_elep_prescription_snapshot.pdf_file_hash IS 'PDFfile哈希';
COMMENT ON COLUMN yb_elep_prescription_snapshot.created_at IS '创建时间';
COMMENT ON COLUMN yb_elep_prescription_snapshot.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_elep_prescription_snapshot.created_by IS '创建人';
COMMENT ON COLUMN yb_elep_prescription_snapshot.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_elep_prescription_snapshot.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_elep_prescription_snapshot.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_elep_prescription_snapshot.deleted_by IS '删除人';
COMMENT ON COLUMN yb_elep_prescription_snapshot.tenant_id IS '租户标识';
