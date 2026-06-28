-- mrms_borrow_request（建表）
-- Flyway V97

CREATE TABLE IF NOT EXISTS mrms_borrow_request (
    id                          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id                   BIGINT       NOT NULL,
    case_record_id              BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    request_type_code           VARCHAR(16)  NOT NULL DEFAULT 'borrow',
    applicant_practitioner_id   BIGINT,
    applicant_name              VARCHAR(50),
    applicant_dept_name         VARCHAR(100),
    purpose                     VARCHAR(500),
    scope_desc                  VARCHAR(500),
    copy_count                  INTEGER,
    patient_authorization_info  VARCHAR(500),
    status_code                 VARCHAR(32)  NOT NULL,
    approve_comment             VARCHAR(500),
    approved_by                 BIGINT,
    approved_at                 TIMESTAMP,
    borrowed_at                 TIMESTAMP,
    due_return_at               TIMESTAMP,
    returned_at                 TIMESTAMP,
    created_at                  TIMESTAMP    NOT NULL,
    updated_at                  TIMESTAMP,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at                  TIMESTAMP,
    deleted_by                  BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_borrow_tenant_status
    ON mrms_borrow_request (tenant_id, status_code, created_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_borrow_case_record
    ON mrms_borrow_request (case_record_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_borrow_request IS '病案借阅复印申请';
COMMENT ON COLUMN mrms_borrow_request.id IS '主键';
COMMENT ON COLUMN mrms_borrow_request.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_borrow_request.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_borrow_request.request_type_code IS '申请类型编码';
COMMENT ON COLUMN mrms_borrow_request.applicant_practitioner_id IS '申请人医护人员标识';
COMMENT ON COLUMN mrms_borrow_request.applicant_name IS '申请人姓名';
COMMENT ON COLUMN mrms_borrow_request.applicant_dept_name IS '申请人科室名称';
COMMENT ON COLUMN mrms_borrow_request.purpose IS '借阅/复印目的';
COMMENT ON COLUMN mrms_borrow_request.scope_desc IS '范围说明';
COMMENT ON COLUMN mrms_borrow_request.copy_count IS '复印份数';
COMMENT ON COLUMN mrms_borrow_request.patient_authorization_info IS '患者授权信息';
COMMENT ON COLUMN mrms_borrow_request.status_code IS '申请状态编码';
COMMENT ON COLUMN mrms_borrow_request.approve_comment IS '审批意见';
COMMENT ON COLUMN mrms_borrow_request.approved_by IS '审批人标识';
COMMENT ON COLUMN mrms_borrow_request.approved_at IS '审批时间';
COMMENT ON COLUMN mrms_borrow_request.borrowed_at IS '借出时间';
COMMENT ON COLUMN mrms_borrow_request.due_return_at IS '应归还时间';
COMMENT ON COLUMN mrms_borrow_request.returned_at IS '归还时间';
COMMENT ON COLUMN mrms_borrow_request.created_at IS '创建时间';
COMMENT ON COLUMN mrms_borrow_request.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_borrow_request.created_by IS '创建人';
COMMENT ON COLUMN mrms_borrow_request.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_borrow_request.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_borrow_request.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_borrow_request.deleted_by IS '删除人';
