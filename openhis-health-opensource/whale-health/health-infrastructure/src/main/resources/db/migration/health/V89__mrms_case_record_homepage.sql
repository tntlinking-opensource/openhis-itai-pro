-- mrms_case_record_homepage（建表）
-- Flyway V89

CREATE TABLE IF NOT EXISTS mrms_case_record_homepage (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    tenant_id           BIGINT       NOT NULL,
    version_no          INTEGER      NOT NULL DEFAULT 1,
    frozen              BOOLEAN      NOT NULL DEFAULT FALSE,
    ipt_medcas_hmpg_sn  VARCHAR(30),
    field_sources_json  TEXT,
    extension_json      TEXT,
    psn_name            VARCHAR(100),
    gend_code           VARCHAR(16),
    brdy                DATE,
    ntly_code           VARCHAR(16),
    naty_code           VARCHAR(16),
    certno              VARCHAR(32),
    prfs_code           VARCHAR(16),
    mrg_stas_code       VARCHAR(16),
    curr_addr           VARCHAR(500),
    psn_tel             VARCHAR(32),
    coner_name          VARCHAR(100),
    coner_tel           VARCHAR(32),
    ipt_no              VARCHAR(64),
    adm_way_code        VARCHAR(16),
    adm_caty_code       VARCHAR(32),
    adm_caty_name       VARCHAR(100),
    adm_time            TIMESTAMP,
    dscg_caty_code      VARCHAR(32),
    dscg_caty_name      VARCHAR(100),
    dscg_time           TIMESTAMP,
    ipt_days            INTEGER,
    transfer_dept_summary VARCHAR(500),
    dscg_way_code       VARCHAR(16),
    dept_director_name  VARCHAR(50),
    chfdr_name          VARCHAR(50),
    atddr_name          VARCHAR(50),
    ipt_dr_name         VARCHAR(50),
    resp_nurs_name      VARCHAR(50),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    atddr_practitioner_id BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_homepage_case
    ON mrms_case_record_homepage (case_record_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_case_record_homepage IS 'mrms病案记录首页';
COMMENT ON COLUMN mrms_case_record_homepage.id IS '主键';
COMMENT ON COLUMN mrms_case_record_homepage.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_case_record_homepage.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_case_record_homepage.version_no IS 'version编号';
COMMENT ON COLUMN mrms_case_record_homepage.frozen IS '是否冻结';
COMMENT ON COLUMN mrms_case_record_homepage.ipt_medcas_hmpg_sn IS '住院病案首页流水号';
COMMENT ON COLUMN mrms_case_record_homepage.field_sources_json IS '字段sourcesJSON';
COMMENT ON COLUMN mrms_case_record_homepage.extension_json IS '扩展字段JSON';
COMMENT ON COLUMN mrms_case_record_homepage.psn_name IS 'psn名称';
COMMENT ON COLUMN mrms_case_record_homepage.gend_code IS 'gend编码';
COMMENT ON COLUMN mrms_case_record_homepage.brdy IS '字段：brdy';
COMMENT ON COLUMN mrms_case_record_homepage.ntly_code IS 'ntly编码';
COMMENT ON COLUMN mrms_case_record_homepage.naty_code IS 'naty编码';
COMMENT ON COLUMN mrms_case_record_homepage.certno IS '字段：certno';
COMMENT ON COLUMN mrms_case_record_homepage.prfs_code IS 'prfs编码';
COMMENT ON COLUMN mrms_case_record_homepage.mrg_stas_code IS 'mrgstas编码';
COMMENT ON COLUMN mrms_case_record_homepage.curr_addr IS '字段：curr addr';
COMMENT ON COLUMN mrms_case_record_homepage.psn_tel IS '字段：psn tel';
COMMENT ON COLUMN mrms_case_record_homepage.coner_name IS 'coner名称';
COMMENT ON COLUMN mrms_case_record_homepage.coner_tel IS '字段：coner tel';
COMMENT ON COLUMN mrms_case_record_homepage.ipt_no IS 'ipt编号';
COMMENT ON COLUMN mrms_case_record_homepage.adm_way_code IS 'admway编码';
COMMENT ON COLUMN mrms_case_record_homepage.adm_caty_code IS 'admcaty编码';
COMMENT ON COLUMN mrms_case_record_homepage.adm_caty_name IS 'admcaty名称';
COMMENT ON COLUMN mrms_case_record_homepage.adm_time IS 'adm时间';
COMMENT ON COLUMN mrms_case_record_homepage.dscg_caty_code IS 'dscgcaty编码';
COMMENT ON COLUMN mrms_case_record_homepage.dscg_caty_name IS 'dscgcaty名称';
COMMENT ON COLUMN mrms_case_record_homepage.dscg_time IS 'dscg时间';
COMMENT ON COLUMN mrms_case_record_homepage.ipt_days IS '住院days';
COMMENT ON COLUMN mrms_case_record_homepage.transfer_dept_summary IS 'transferdept摘要';
COMMENT ON COLUMN mrms_case_record_homepage.dscg_way_code IS 'dscgway编码';
COMMENT ON COLUMN mrms_case_record_homepage.dept_director_name IS 'deptdirector名称';
COMMENT ON COLUMN mrms_case_record_homepage.chfdr_name IS 'chfdr名称';
COMMENT ON COLUMN mrms_case_record_homepage.atddr_name IS 'atddr名称';
COMMENT ON COLUMN mrms_case_record_homepage.ipt_dr_name IS 'iptdr名称';
COMMENT ON COLUMN mrms_case_record_homepage.resp_nurs_name IS 'respnurs名称';
COMMENT ON COLUMN mrms_case_record_homepage.created_at IS '创建时间';
COMMENT ON COLUMN mrms_case_record_homepage.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_case_record_homepage.created_by IS '创建人';
COMMENT ON COLUMN mrms_case_record_homepage.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_case_record_homepage.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_case_record_homepage.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_case_record_homepage.deleted_by IS '删除人';
COMMENT ON COLUMN mrms_case_record_homepage.atddr_practitioner_id IS 'atddr医护人员标识';
