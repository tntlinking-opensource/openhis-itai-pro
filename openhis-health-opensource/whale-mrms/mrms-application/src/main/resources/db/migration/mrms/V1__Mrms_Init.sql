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
);
