-- Whale MRMS Wave 1：病案主档、首页、诊断/手术行、病案号序列表
-- 独立 history 表 flyway_schema_history_mrms，版本号在本模块内从 V1 递增

CREATE TABLE IF NOT EXISTS mrms_case_record (
    id                          BIGINT PRIMARY KEY,
    encounter_id                BIGINT       NOT NULL,
    patient_id                  BIGINT       NOT NULL,
    medcasno                    VARCHAR(40)  NOT NULL,
    status_code                 VARCHAR(32)  NOT NULL DEFAULT 'draft',
    patn_ipt_cnt                INTEGER,
    inpatient_no                VARCHAR(32),
    patient_name                VARCHAR(100),
    gender_code                 VARCHAR(16),
    admission_at                TIMESTAMP,
    discharge_at                TIMESTAMP,
    dept_code                   VARCHAR(64),
    dept_name                   VARCHAR(200),
    attending_practitioner_id   BIGINT,
    attending_practitioner_name VARCHAR(100),
    primary_diagnosis_text      VARCHAR(500),
    return_reason               VARCHAR(500),
    archived_at                 TIMESTAMP,
    archived_by                 BIGINT,
    quality_grade_code          VARCHAR(16),
    tenant_id                   BIGINT       NOT NULL,
    created_at                  TIMESTAMP    NOT NULL,
    updated_at                  TIMESTAMP,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at                  TIMESTAMP,
    deleted_by                  BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_case_encounter
    ON mrms_case_record (tenant_id, encounter_id)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_case_medcasno
    ON mrms_case_record (tenant_id, medcasno)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_case_list
    ON mrms_case_record (tenant_id, discharge_at, status_code, dept_code);

CREATE TABLE IF NOT EXISTS mrms_medcasno_sequence (
    id              BIGINT PRIMARY KEY,
    year_no         INTEGER      NOT NULL,
    last_value      BIGINT       NOT NULL DEFAULT 0,
    tenant_id       BIGINT       NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP,
    UNIQUE (tenant_id, year_no)
);

CREATE TABLE IF NOT EXISTS mrms_case_record_homepage (
    id                      BIGINT PRIMARY KEY,
    case_record_id          BIGINT       NOT NULL UNIQUE REFERENCES mrms_case_record(id),
    version_no              INTEGER      NOT NULL DEFAULT 1,
    frozen                  BOOLEAN      NOT NULL DEFAULT FALSE,
    psn_name                VARCHAR(100),
    gend_code               VARCHAR(16),
    brdy                    DATE,
    certno                  VARCHAR(50),
    ntly_code               VARCHAR(16),
    naty_code               VARCHAR(16),
    prfs_code               VARCHAR(16),
    mrg_stas_code           VARCHAR(16),
    curr_addr               VARCHAR(500),
    psn_tel                 VARCHAR(50),
    coner_name              VARCHAR(100),
    coner_tel               VARCHAR(50),
    coner_addr              VARCHAR(500),
    ipt_no                  VARCHAR(32),
    adm_way_code            VARCHAR(16),
    adm_caty_code           VARCHAR(64),
    adm_caty_name           VARCHAR(200),
    dscg_caty_code          VARCHAR(64),
    dscg_caty_name          VARCHAR(200),
    adm_time                TIMESTAMP,
    dscg_time               TIMESTAMP,
    act_ipt_days            INTEGER,
    dscg_way_code           VARCHAR(16),
    transfer_dept_summary   VARCHAR(1000),
    dept_director_name      VARCHAR(100),
    chfdr_name              VARCHAR(100),
    atddr_name              VARCHAR(100),
    ipt_dr_name             VARCHAR(100),
    resp_nurs_name          VARCHAR(100),
    field_sources_json      TEXT,
    extension_json          TEXT,
    tenant_id               BIGINT       NOT NULL,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT
);

CREATE TABLE IF NOT EXISTS mrms_homepage_diagnosis (
    id                  BIGINT PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    diag_type_code      VARCHAR(32)  NOT NULL,
    sort_no             INTEGER      NOT NULL DEFAULT 0,
    diag_code           VARCHAR(32),
    diag_name           VARCHAR(500),
    adm_cond_code       VARCHAR(16),
    tenant_id           BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_mrms_hpg_diag_cr
    ON mrms_homepage_diagnosis (case_record_id, diag_type_code, sort_no);

COMMENT ON TABLE mrms_case_record IS '病案 case record';

COMMENT ON COLUMN mrms_case_record.id IS '主键';
COMMENT ON COLUMN mrms_case_record.encounter_id IS 'encounter id';
COMMENT ON COLUMN mrms_case_record.patient_id IS 'patient id';
COMMENT ON COLUMN mrms_case_record.medcasno IS 'medcasno';
COMMENT ON COLUMN mrms_case_record.status_code IS 'status code';
COMMENT ON COLUMN mrms_case_record.patn_ipt_cnt IS 'patn ipt cnt';
COMMENT ON COLUMN mrms_case_record.inpatient_no IS 'inpatient no';
COMMENT ON COLUMN mrms_case_record.patient_name IS 'patient name';
COMMENT ON COLUMN mrms_case_record.gender_code IS 'gender code';
COMMENT ON COLUMN mrms_case_record.admission_at IS 'admission at';
COMMENT ON COLUMN mrms_case_record.discharge_at IS 'discharge at';
COMMENT ON COLUMN mrms_case_record.dept_code IS 'dept code';
COMMENT ON COLUMN mrms_case_record.dept_name IS 'dept name';
COMMENT ON COLUMN mrms_case_record.attending_practitioner_id IS 'attending practitioner id';
COMMENT ON COLUMN mrms_case_record.attending_practitioner_name IS 'attending practitioner name';
COMMENT ON COLUMN mrms_case_record.primary_diagnosis_text IS 'primary diagnosis text';
COMMENT ON COLUMN mrms_case_record.return_reason IS 'return reason';
COMMENT ON COLUMN mrms_case_record.archived_at IS 'archived at';
COMMENT ON COLUMN mrms_case_record.archived_by IS 'archived by';
COMMENT ON COLUMN mrms_case_record.quality_grade_code IS 'quality grade code';
COMMENT ON COLUMN mrms_case_record.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_case_record.created_at IS '创建时间';
COMMENT ON COLUMN mrms_case_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_case_record.created_by IS '创建人';
COMMENT ON COLUMN mrms_case_record.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_case_record.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_case_record.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_case_record.deleted_by IS '删除人';

COMMENT ON TABLE mrms_medcasno_sequence IS '病案 medcasno sequence';

COMMENT ON COLUMN mrms_medcasno_sequence.id IS '主键';
COMMENT ON COLUMN mrms_medcasno_sequence.year_no IS 'year no';
COMMENT ON COLUMN mrms_medcasno_sequence.last_value IS 'last value';
COMMENT ON COLUMN mrms_medcasno_sequence.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_medcasno_sequence.created_at IS '创建时间';
COMMENT ON COLUMN mrms_medcasno_sequence.updated_at IS '最后更新时间';

COMMENT ON TABLE mrms_case_record_homepage IS '病案 case record homepage';

COMMENT ON COLUMN mrms_case_record_homepage.id IS '主键';
COMMENT ON COLUMN mrms_case_record_homepage.case_record_id IS 'case record id';
COMMENT ON COLUMN mrms_case_record_homepage.version_no IS 'version no';
COMMENT ON COLUMN mrms_case_record_homepage.frozen IS 'frozen';
COMMENT ON COLUMN mrms_case_record_homepage.psn_name IS 'psn name';
COMMENT ON COLUMN mrms_case_record_homepage.gend_code IS 'gend code';
COMMENT ON COLUMN mrms_case_record_homepage.brdy IS 'brdy';
COMMENT ON COLUMN mrms_case_record_homepage.certno IS 'certno';
COMMENT ON COLUMN mrms_case_record_homepage.ntly_code IS 'ntly code';
COMMENT ON COLUMN mrms_case_record_homepage.naty_code IS 'naty code';
COMMENT ON COLUMN mrms_case_record_homepage.prfs_code IS 'prfs code';
COMMENT ON COLUMN mrms_case_record_homepage.mrg_stas_code IS 'mrg stas code';
COMMENT ON COLUMN mrms_case_record_homepage.curr_addr IS 'curr addr';
COMMENT ON COLUMN mrms_case_record_homepage.psn_tel IS 'psn tel';
COMMENT ON COLUMN mrms_case_record_homepage.coner_name IS 'coner name';
COMMENT ON COLUMN mrms_case_record_homepage.coner_tel IS 'coner tel';
COMMENT ON COLUMN mrms_case_record_homepage.coner_addr IS 'coner addr';
COMMENT ON COLUMN mrms_case_record_homepage.ipt_no IS 'ipt no';
COMMENT ON COLUMN mrms_case_record_homepage.adm_way_code IS 'adm way code';
COMMENT ON COLUMN mrms_case_record_homepage.adm_caty_code IS 'adm caty code';
COMMENT ON COLUMN mrms_case_record_homepage.adm_caty_name IS 'adm caty name';
COMMENT ON COLUMN mrms_case_record_homepage.dscg_caty_code IS 'dscg caty code';
COMMENT ON COLUMN mrms_case_record_homepage.dscg_caty_name IS 'dscg caty name';
COMMENT ON COLUMN mrms_case_record_homepage.adm_time IS 'adm time';
COMMENT ON COLUMN mrms_case_record_homepage.dscg_time IS 'dscg time';
COMMENT ON COLUMN mrms_case_record_homepage.act_ipt_days IS 'act ipt days';
COMMENT ON COLUMN mrms_case_record_homepage.dscg_way_code IS 'dscg way code';
COMMENT ON COLUMN mrms_case_record_homepage.transfer_dept_summary IS 'transfer dept summary';
COMMENT ON COLUMN mrms_case_record_homepage.dept_director_name IS 'dept director name';
COMMENT ON COLUMN mrms_case_record_homepage.chfdr_name IS 'chfdr name';
COMMENT ON COLUMN mrms_case_record_homepage.atddr_name IS 'atddr name';
COMMENT ON COLUMN mrms_case_record_homepage.ipt_dr_name IS 'ipt dr name';
COMMENT ON COLUMN mrms_case_record_homepage.resp_nurs_name IS 'resp nurs name';
COMMENT ON COLUMN mrms_case_record_homepage.field_sources_json IS 'field sources json';
COMMENT ON COLUMN mrms_case_record_homepage.extension_json IS 'extension json';
COMMENT ON COLUMN mrms_case_record_homepage.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_case_record_homepage.created_at IS '创建时间';
COMMENT ON COLUMN mrms_case_record_homepage.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_case_record_homepage.created_by IS '创建人';
COMMENT ON COLUMN mrms_case_record_homepage.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_case_record_homepage.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_case_record_homepage.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_case_record_homepage.deleted_by IS '删除人';

COMMENT ON TABLE mrms_homepage_diagnosis IS '病案 homepage diagnosis';

COMMENT ON COLUMN mrms_homepage_diagnosis.id IS '主键';
COMMENT ON COLUMN mrms_homepage_diagnosis.case_record_id IS 'case record id';
COMMENT ON COLUMN mrms_homepage_diagnosis.diag_type_code IS 'diag type code';
COMMENT ON COLUMN mrms_homepage_diagnosis.sort_no IS 'sort no';
COMMENT ON COLUMN mrms_homepage_diagnosis.diag_code IS 'diag code';
COMMENT ON COLUMN mrms_homepage_diagnosis.diag_name IS 'diag name';
COMMENT ON COLUMN mrms_homepage_diagnosis.adm_cond_code IS 'adm cond code';
COMMENT ON COLUMN mrms_homepage_diagnosis.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_homepage_diagnosis.created_at IS '创建时间';
COMMENT ON COLUMN mrms_homepage_diagnosis.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_homepage_diagnosis.is_deleted IS '软删标记';

CREATE TABLE IF NOT EXISTS mrms_homepage_procedure (
    id                  BIGINT PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    sort_no             INTEGER      NOT NULL DEFAULT 0,
    oprn_oprt_code      VARCHAR(32),
    oprn_oprt_name      VARCHAR(500),
    oprn_date           DATE,
    oprn_level_code     VARCHAR(16),
    oprn_incision_code  VARCHAR(16),
    heal_grade_code     VARCHAR(16),
    anst_way_code       VARCHAR(16),
    tenant_id           BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
)

;

COMMENT ON TABLE mrms_homepage_procedure IS '病案 homepage procedure';

COMMENT ON COLUMN mrms_homepage_procedure.id IS '主键';
COMMENT ON COLUMN mrms_homepage_procedure.case_record_id IS 'case record id';
COMMENT ON COLUMN mrms_homepage_procedure.sort_no IS 'sort no';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_oprt_code IS 'oprn oprt code';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_oprt_name IS 'oprn oprt name';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_date IS 'oprn date';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_level_code IS 'oprn level code';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_incision_code IS 'oprn incision code';
COMMENT ON COLUMN mrms_homepage_procedure.heal_grade_code IS 'heal grade code';
COMMENT ON COLUMN mrms_homepage_procedure.anst_way_code IS 'anst way code';
COMMENT ON COLUMN mrms_homepage_procedure.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_homepage_procedure.created_at IS '创建时间';
COMMENT ON COLUMN mrms_homepage_procedure.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_homepage_procedure.is_deleted IS '软删标记';

