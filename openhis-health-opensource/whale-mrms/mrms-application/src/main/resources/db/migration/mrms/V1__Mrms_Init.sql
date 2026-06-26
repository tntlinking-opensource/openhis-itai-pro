-- MRMS Wave 1 初始化表结构
-- Flyway history: flyway_schema_history_mrms

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
    archived_at TIMESTAMP,
    archived_by BIGINT,
    quality_grade_code VARCHAR(16)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_case_encounter
    ON mrms_case_record (tenant_id, encounter_id)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_case_medcasno
    ON mrms_case_record (tenant_id, medcasno)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_case_list
    ON mrms_case_record (tenant_id, dscg_time, status_code, adm_caty_name);

CREATE TABLE IF NOT EXISTS mrms_medcasno_sequence (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    year_value          INTEGER      NOT NULL,
    last_seq            INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_medcasno_seq
    ON mrms_medcasno_sequence (tenant_id, year_value)
    WHERE is_deleted = FALSE;

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

CREATE TABLE IF NOT EXISTS mrms_homepage_diagnosis (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    tenant_id           BIGINT       NOT NULL,
    diag_type_code      VARCHAR(32)  NOT NULL,
    sort_no             INTEGER      NOT NULL DEFAULT 0,
    diag_code           VARCHAR(32),
    diag_name           VARCHAR(200),
    adm_cond_code       VARCHAR(16),
    maindiag_flag       VARCHAR(3),
    vali_flag           VARCHAR(3)   NOT NULL DEFAULT '1',
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_homepage_diag_case
    ON mrms_homepage_diagnosis (case_record_id, diag_type_code);

CREATE TABLE IF NOT EXISTS mrms_homepage_procedure (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    tenant_id           BIGINT       NOT NULL,
    sort_no             INTEGER      NOT NULL DEFAULT 0,
    oprn_oprt_code      VARCHAR(32),
    oprn_oprt_name      VARCHAR(200),
    oprn_date           DATE,
    main_oprn_flag      VARCHAR(3),
    vali_flag           VARCHAR(3)   NOT NULL DEFAULT '1',
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_homepage_proc_case
    ON mrms_homepage_procedure (case_record_id);
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
COMMENT ON COLUMN mrms_case_record.created_at IS '创建时间';
COMMENT ON COLUMN mrms_case_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_case_record.created_by IS '创建人';
COMMENT ON COLUMN mrms_case_record.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_case_record.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_case_record.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_case_record.deleted_by IS '删除人';
COMMENT ON COLUMN mrms_case_record.atddr_practitioner_id IS 'atddr医护人员标识';
COMMENT ON COLUMN mrms_case_record.archived_at IS 'archived时间';
COMMENT ON COLUMN mrms_case_record.archived_by IS '归档人';
COMMENT ON COLUMN mrms_case_record.quality_grade_code IS '质控等级编码';

COMMENT ON TABLE mrms_medcasno_sequence IS 'mrms病案号序号';
COMMENT ON COLUMN mrms_medcasno_sequence.id IS '主键';
COMMENT ON COLUMN mrms_medcasno_sequence.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_medcasno_sequence.year_value IS '年份';
COMMENT ON COLUMN mrms_medcasno_sequence.last_seq IS '末位序号';
COMMENT ON COLUMN mrms_medcasno_sequence.created_at IS '创建时间';
COMMENT ON COLUMN mrms_medcasno_sequence.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_medcasno_sequence.created_by IS '创建人';
COMMENT ON COLUMN mrms_medcasno_sequence.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_medcasno_sequence.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_medcasno_sequence.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_medcasno_sequence.deleted_by IS '删除人';

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

COMMENT ON TABLE mrms_homepage_diagnosis IS 'mrms首页诊断';
COMMENT ON COLUMN mrms_homepage_diagnosis.id IS '主键';
COMMENT ON COLUMN mrms_homepage_diagnosis.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_homepage_diagnosis.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_homepage_diagnosis.diag_type_code IS 'diag类型编码';
COMMENT ON COLUMN mrms_homepage_diagnosis.sort_no IS '排序号';
COMMENT ON COLUMN mrms_homepage_diagnosis.diag_code IS 'diag编码';
COMMENT ON COLUMN mrms_homepage_diagnosis.diag_name IS 'diag名称';
COMMENT ON COLUMN mrms_homepage_diagnosis.adm_cond_code IS 'admcond编码';
COMMENT ON COLUMN mrms_homepage_diagnosis.maindiag_flag IS 'maindiag标志位';
COMMENT ON COLUMN mrms_homepage_diagnosis.vali_flag IS 'vali标志位';
COMMENT ON COLUMN mrms_homepage_diagnosis.created_at IS '创建时间';
COMMENT ON COLUMN mrms_homepage_diagnosis.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_homepage_diagnosis.created_by IS '创建人';
COMMENT ON COLUMN mrms_homepage_diagnosis.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_homepage_diagnosis.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_homepage_diagnosis.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_homepage_diagnosis.deleted_by IS '删除人';

COMMENT ON TABLE mrms_homepage_procedure IS 'mrms首页处置';
COMMENT ON COLUMN mrms_homepage_procedure.id IS '主键';
COMMENT ON COLUMN mrms_homepage_procedure.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_homepage_procedure.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_homepage_procedure.sort_no IS '排序号';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_oprt_code IS 'oprnoprt编码';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_oprt_name IS 'oprnoprt名称';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_date IS 'oprn日期';
COMMENT ON COLUMN mrms_homepage_procedure.main_oprn_flag IS 'mainoprn标志位';
COMMENT ON COLUMN mrms_homepage_procedure.vali_flag IS 'vali标志位';
COMMENT ON COLUMN mrms_homepage_procedure.created_at IS '创建时间';
COMMENT ON COLUMN mrms_homepage_procedure.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_homepage_procedure.created_by IS '创建人';
COMMENT ON COLUMN mrms_homepage_procedure.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_homepage_procedure.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_homepage_procedure.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_homepage_procedure.deleted_by IS '删除人';
