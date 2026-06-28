-- mrms_case_record（建表 · 最终态）
-- Flyway V87
-- Flyway history: flyway_schema_history (health)

CREATE TABLE IF NOT EXISTS mrms_case_record (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL,
    encounter_id        BIGINT       NOT NULL,
    medcasno            VARCHAR(32)  NOT NULL,
    status_code         VARCHAR(32)  NOT NULL,
    patn_ipt_cnt        INTEGER,
    psn_name            VARCHAR(100),
    ipt_no              VARCHAR(64),
    gend_code           VARCHAR(16),
    adm_time            TIMESTAMP,
    dscg_time           TIMESTAMP,
    adm_caty_name       VARCHAR(100),
    dscg_caty_name      VARCHAR(100),
    chfdr_name          VARCHAR(50),
    main_diag_name      VARCHAR(200),
    return_reason       VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    atddr_practitioner_id BIGINT,
    atddr_name          VARCHAR(50),
    archived_at         TIMESTAMP,
    archived_by         BIGINT,
    quality_grade_code  VARCHAR(16),
    chs_upload_status_code VARCHAR(32),
    chs_upload_at       TIMESTAMP,
    chs_upload_error    VARCHAR(500),
    unarchive_reason    VARCHAR(500)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_case_encounter
    ON mrms_case_record (tenant_id, encounter_id)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_case_medcasno
    ON mrms_case_record (tenant_id, medcasno)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_case_list
    ON mrms_case_record (tenant_id, dscg_time, status_code, adm_caty_name);


CREATE INDEX IF NOT EXISTS idx_mrms_case_list_atddr
    ON mrms_case_record (tenant_id, atddr_practitioner_id, dscg_time)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_case_record IS 'mrms病案记录';
COMMENT ON COLUMN mrms_case_record.id IS '主键';
COMMENT ON COLUMN mrms_case_record.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_case_record.patient_id IS '患者标识';
COMMENT ON COLUMN mrms_case_record.encounter_id IS '就诊标识';
COMMENT ON COLUMN mrms_case_record.medcasno IS '病案号';
COMMENT ON COLUMN mrms_case_record.status_code IS '状态编码';
COMMENT ON COLUMN mrms_case_record.patn_ipt_cnt IS 'patnipt数量';
COMMENT ON COLUMN mrms_case_record.psn_name IS 'psn名称';
COMMENT ON COLUMN mrms_case_record.ipt_no IS 'ipt编号';
COMMENT ON COLUMN mrms_case_record.gend_code IS 'gend编码';
COMMENT ON COLUMN mrms_case_record.adm_time IS 'adm时间';
COMMENT ON COLUMN mrms_case_record.dscg_time IS 'dscg时间';
COMMENT ON COLUMN mrms_case_record.adm_caty_name IS 'admcaty名称';
COMMENT ON COLUMN mrms_case_record.dscg_caty_name IS 'dscgcaty名称';
COMMENT ON COLUMN mrms_case_record.chfdr_name IS 'chfdr名称';
COMMENT ON COLUMN mrms_case_record.main_diag_name IS 'maindiag名称';
COMMENT ON COLUMN mrms_case_record.return_reason IS 'return原因';
COMMENT ON COLUMN mrms_case_record.atddr_practitioner_id IS 'atddr医护人员标识';
COMMENT ON COLUMN mrms_case_record.atddr_name IS 'atddr名称';
COMMENT ON COLUMN mrms_case_record.archived_at IS 'archived时间';
COMMENT ON COLUMN mrms_case_record.archived_by IS '归档人';
COMMENT ON COLUMN mrms_case_record.quality_grade_code IS '质控等级编码';
COMMENT ON COLUMN mrms_case_record.chs_upload_status_code IS '4401上传状态冗余';
COMMENT ON COLUMN mrms_case_record.chs_upload_at IS '最近4401上传时间';
COMMENT ON COLUMN mrms_case_record.chs_upload_error IS '最近4401上传错误摘要';
COMMENT ON COLUMN mrms_case_record.unarchive_reason IS '解档原因';
COMMENT ON COLUMN mrms_case_record.created_at IS '创建时间';
COMMENT ON COLUMN mrms_case_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_case_record.created_by IS '创建人';
COMMENT ON COLUMN mrms_case_record.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_case_record.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_case_record.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_case_record.deleted_by IS '删除人';


