-- Squashed V9 mrms_prt_ddl

-- from V73__prt_print.sql
-- V86：PRT 画布打印（建表/兼容 + 平台种子 + 菜单权限；合并原 V86/V87/V88）

CREATE TABLE IF NOT EXISTS "public"."prt_template" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "organization_id" int8,
    "bus_no" varchar(64) NOT NULL,
    "name" varchar(128) NOT NULL,
    "status" varchar(16) NOT NULL,
    "version" int4 NOT NULL,
    "print_json" jsonb NOT NULL,
    "schema_version" int4 NOT NULL DEFAULT 1,
    "sample_print_data" jsonb,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "scenario_code" varchar(64),
    CONSTRAINT "prt_template_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_prt_template_lookup"
    ON "public"."prt_template" ("tenant_id", "bus_no", "status", "version" DESC)
    WHERE "is_deleted" = false;

CREATE TABLE IF NOT EXISTS "public"."prt_print_event_log" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "organization_id" int8,
    "trace_id" varchar(64) NOT NULL,
    "bus_no" varchar(64) NOT NULL,
    "binding_code" varchar(64),
    "template_version" int4,
    "status" varchar(16) NOT NULL,
    "printer_name" varchar(128),
    "copies" int4,
    "content_hash" varchar(64),
    "error_code" varchar(64),
    "error_message" varchar(512),
    "biz_ref_json" jsonb,
    "client_type" varchar(32),
    "operator_id" int8,
    "operator_name" varchar(128),
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    CONSTRAINT "prt_print_event_log_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_prt_log_tenant_time"
    ON "public"."prt_print_event_log" ("tenant_id", "created_at" DESC)
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_prt_log_bus_no"
    ON "public"."prt_print_event_log" ("tenant_id", "bus_no", "created_at" DESC)
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_prt_log_trace"
    ON "public"."prt_print_event_log" ("trace_id")
    WHERE "is_deleted" = false;

CREATE TABLE IF NOT EXISTS "public"."prt_print_binding" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "organization_id" int8,
    "binding_code" varchar(64) NOT NULL,
    "bus_no" varchar(64) NOT NULL,
    "name" varchar(128),
    "enabled" bool NOT NULL DEFAULT true,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    CONSTRAINT "prt_print_binding_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "uk_prt_print_binding" UNIQUE ("tenant_id", "organization_id", "binding_code")
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_prt_template_ver"
    ON "public"."prt_template" ("tenant_id", "organization_id", "bus_no", "status", "version")
    WHERE "is_deleted" = false;

-- 场景码索引（非唯一）：允许多个 published 模板共用同一 scenario_code
CREATE INDEX IF NOT EXISTS "idx_prt_template_scenario"
    ON "public"."prt_template" ("tenant_id", "scenario_code", "status", "version" DESC)
    WHERE "is_deleted" = false AND "scenario_code" IS NOT NULL;

COMMENT ON TABLE prt_template IS '画布打印模板 PrintTemplate';
COMMENT ON COLUMN prt_template.id IS '主键';
COMMENT ON COLUMN prt_template.tenant_id IS '租户标识';
COMMENT ON COLUMN prt_template.organization_id IS '机构标识';
COMMENT ON COLUMN prt_template.bus_no IS '业务编号';
COMMENT ON COLUMN prt_template.name IS '名称';
COMMENT ON COLUMN prt_template.status IS '状态';
COMMENT ON COLUMN prt_template.version IS '乐观锁版本号';
COMMENT ON COLUMN prt_template.print_json IS '打印JSON';
COMMENT ON COLUMN prt_template.schema_version IS '结构版本';
COMMENT ON COLUMN prt_template.sample_print_data IS 'sample打印data';
COMMENT ON COLUMN prt_template.created_at IS '创建时间';
COMMENT ON COLUMN prt_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN prt_template.created_by IS '创建人';
COMMENT ON COLUMN prt_template.updated_by IS '最后更新人';
COMMENT ON COLUMN prt_template.is_deleted IS '软删标记';
COMMENT ON COLUMN prt_template.deleted_at IS '删除时间';
COMMENT ON COLUMN prt_template.deleted_by IS '删除人';
COMMENT ON COLUMN prt_template.scenario_code IS '业务场景码（如 op-rx-print）；published 行用于 preparePrint 按场景解析';

COMMENT ON TABLE prt_print_event_log IS '打印审计日志 PrintLog';
COMMENT ON COLUMN prt_print_event_log.id IS '主键';
COMMENT ON COLUMN prt_print_event_log.tenant_id IS '租户标识';
COMMENT ON COLUMN prt_print_event_log.organization_id IS '机构标识';
COMMENT ON COLUMN prt_print_event_log.trace_id IS '追踪标识';
COMMENT ON COLUMN prt_print_event_log.bus_no IS '业务编号';
COMMENT ON COLUMN prt_print_event_log.binding_code IS '绑定编码';
COMMENT ON COLUMN prt_print_event_log.template_version IS '模板version';
COMMENT ON COLUMN prt_print_event_log.status IS '状态';
COMMENT ON COLUMN prt_print_event_log.printer_name IS '打印机名称';
COMMENT ON COLUMN prt_print_event_log.copies IS '份数';
COMMENT ON COLUMN prt_print_event_log.content_hash IS 'content哈希';
COMMENT ON COLUMN prt_print_event_log.error_code IS '错误编码';
COMMENT ON COLUMN prt_print_event_log.error_message IS '错误消息';
COMMENT ON COLUMN prt_print_event_log.biz_ref_json IS '业务引用JSON';
COMMENT ON COLUMN prt_print_event_log.client_type IS '客户端类型';
COMMENT ON COLUMN prt_print_event_log.operator_id IS '操作人标识';
COMMENT ON COLUMN prt_print_event_log.operator_name IS '操作人名称';
COMMENT ON COLUMN prt_print_event_log.created_at IS '创建时间';
COMMENT ON COLUMN prt_print_event_log.updated_at IS '最后更新时间';
COMMENT ON COLUMN prt_print_event_log.created_by IS '创建人';
COMMENT ON COLUMN prt_print_event_log.updated_by IS '最后更新人';
COMMENT ON COLUMN prt_print_event_log.is_deleted IS '软删标记';
COMMENT ON COLUMN prt_print_event_log.deleted_at IS '删除时间';
COMMENT ON COLUMN prt_print_event_log.deleted_by IS '删除人';

COMMENT ON TABLE prt_print_binding IS '业务场景模板绑定 PrintBinding';
COMMENT ON COLUMN prt_print_binding.id IS '主键';
COMMENT ON COLUMN prt_print_binding.tenant_id IS '租户标识';
COMMENT ON COLUMN prt_print_binding.organization_id IS '机构标识';
COMMENT ON COLUMN prt_print_binding.binding_code IS '绑定编码';
COMMENT ON COLUMN prt_print_binding.bus_no IS '业务编号';
COMMENT ON COLUMN prt_print_binding.name IS '名称';
COMMENT ON COLUMN prt_print_binding.enabled IS '是否启用';
COMMENT ON COLUMN prt_print_binding.created_at IS '创建时间';
COMMENT ON COLUMN prt_print_binding.updated_at IS '最后更新时间';
COMMENT ON COLUMN prt_print_binding.created_by IS '创建人';
COMMENT ON COLUMN prt_print_binding.updated_by IS '最后更新人';
COMMENT ON COLUMN prt_print_binding.is_deleted IS '软删标记';
COMMENT ON COLUMN prt_print_binding.deleted_at IS '删除时间';
COMMENT ON COLUMN prt_print_binding.deleted_by IS '删除人';

-- from V86__mrms_case_record.sql
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
    settle_upload_status_code VARCHAR(8),
    setl_list_id        VARCHAR(64),
    settle_upload_at    TIMESTAMP,
    settle_upload_error VARCHAR(500),
    unarchive_reason    VARCHAR(500),
    certno              VARCHAR(32)
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
COMMENT ON COLUMN mrms_case_record.settle_upload_status_code IS '结算清单上传：0未传/1已传未提交/2已提交';
COMMENT ON COLUMN mrms_case_record.setl_list_id IS '4101A 平台返回清单流水号 setl_list_id';
COMMENT ON COLUMN mrms_case_record.settle_upload_at IS '最近结算清单上传/提交时间';
COMMENT ON COLUMN mrms_case_record.settle_upload_error IS '最近结算清单上传错误摘要';
COMMENT ON COLUMN mrms_case_record.unarchive_reason IS '解档原因';
COMMENT ON COLUMN mrms_case_record.certno IS '列表冗余：证件号码（同步自首页 certno）';
COMMENT ON COLUMN mrms_case_record.created_at IS '创建时间';
COMMENT ON COLUMN mrms_case_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_case_record.created_by IS '创建人';
COMMENT ON COLUMN mrms_case_record.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_case_record.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_case_record.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_case_record.deleted_by IS '删除人';

-- from V87__mrms_medcasno_sequence.sql
-- mrms_medcasno_sequence（建表）
-- Flyway V88

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

-- from V88__mrms_case_record_homepage.sql
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
    coner_rlts_code     VARCHAR(16),
    coner_addr          VARCHAR(500),
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
    trt_type_code       VARCHAR(16),
    spga_nurscare_days  INTEGER,
    lv1_nurscare_days   INTEGER,
    scd_nurscare_days   INTEGER,
    lv3_nurscare_days   INTEGER,
    otp_wm_dise         VARCHAR(200),
    wm_dise_code        VARCHAR(32),
    otp_tcm_dise        VARCHAR(200),
    tcm_dise_code       VARCHAR(32),
    brn_damg_bfadm_coma_dura VARCHAR(32),
    brn_damg_afadm_coma_dura VARCHAR(32),
    vent_used_dura      VARCHAR(32),
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
COMMENT ON COLUMN mrms_case_record_homepage.coner_rlts_code IS '联系人关系字典码（4401 coner_rlts）';
COMMENT ON COLUMN mrms_case_record_homepage.coner_addr IS '联系人地址（4401 coner_addr）';
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
COMMENT ON COLUMN mrms_case_record_homepage.trt_type_code IS '治疗类别字典码（4401 trt_type）';
COMMENT ON COLUMN mrms_case_record_homepage.spga_nurscare_days IS '特级护理天数（4401 spga_nurscare_days）';
COMMENT ON COLUMN mrms_case_record_homepage.lv1_nurscare_days IS '一级护理天数（4401 lv1_nurscare_days）';
COMMENT ON COLUMN mrms_case_record_homepage.scd_nurscare_days IS '二级护理天数（4401 scd_nurscare_days）';
COMMENT ON COLUMN mrms_case_record_homepage.lv3_nurscare_days IS '三级护理天数（4401 lv3_nurscare_days）';
COMMENT ON COLUMN mrms_case_record_homepage.otp_wm_dise IS '门急诊西医诊断名称（4401 otp_wm_dise）';
COMMENT ON COLUMN mrms_case_record_homepage.wm_dise_code IS '门急诊西医诊断编码（4401 wm_dise_code）';
COMMENT ON COLUMN mrms_case_record_homepage.otp_tcm_dise IS '门急诊中医诊断名称（4401 otp_tcm_dise）';
COMMENT ON COLUMN mrms_case_record_homepage.tcm_dise_code IS '门急诊中医诊断编码（4401 tcm_dise_code）';
COMMENT ON COLUMN mrms_case_record_homepage.brn_damg_bfadm_coma_dura IS '入院前颅脑损伤昏迷时长（天/时/分文本，4401 brn_damg_bfadm_coma_dura）';
COMMENT ON COLUMN mrms_case_record_homepage.brn_damg_afadm_coma_dura IS '入院后颅脑损伤昏迷时长（天/时/分文本，4401 brn_damg_afadm_coma_dura）';
COMMENT ON COLUMN mrms_case_record_homepage.vent_used_dura IS '呼吸机使用时长（天/时/分文本，4401 vent_used_dura）';
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

-- from V89__mrms_homepage_diagnosis.sql
-- mrms_homepage_diagnosis（建表）
-- Flyway V90

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

-- from V90__mrms_homepage_procedure.sql
-- mrms_homepage_procedure（建表）
-- Flyway V91

CREATE TABLE IF NOT EXISTS mrms_homepage_procedure (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    tenant_id           BIGINT       NOT NULL,
    sort_no             INTEGER      NOT NULL DEFAULT 0,
    oprn_oprt_code      VARCHAR(32),
    oprn_oprt_name      VARCHAR(200),
    oprn_date           DATE,
    main_oprn_flag      VARCHAR(3),
    oprn_level_code     VARCHAR(16),
    oprn_incision_code  VARCHAR(16),
    heal_grade_code     VARCHAR(16),
    anst_way_code       VARCHAR(16),
    oper_dr_name        VARCHAR(50),
    anst_dr_name        VARCHAR(50),
    oper_dr_code        VARCHAR(32),
    anst_dr_code        VARCHAR(32),
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

COMMENT ON TABLE mrms_homepage_procedure IS 'mrms首页处置';
COMMENT ON COLUMN mrms_homepage_procedure.id IS '主键';
COMMENT ON COLUMN mrms_homepage_procedure.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_homepage_procedure.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_homepage_procedure.sort_no IS '排序号';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_oprt_code IS '手术操作编码';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_oprt_name IS '手术操作名称';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_date IS '手术日期';
COMMENT ON COLUMN mrms_homepage_procedure.main_oprn_flag IS '是否主手术';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_level_code IS '手术级别字典码（4401 oprn_level）';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_incision_code IS '切口愈合类别/切口字典码（4401 oprn_incision）';
COMMENT ON COLUMN mrms_homepage_procedure.heal_grade_code IS '愈合等级字典码（4401 heal_grade）';
COMMENT ON COLUMN mrms_homepage_procedure.anst_way_code IS '麻醉方式字典码（4401 anst_way）';
COMMENT ON COLUMN mrms_homepage_procedure.oper_dr_name IS '术者姓名（4401 oper_dr_name）';
COMMENT ON COLUMN mrms_homepage_procedure.anst_dr_name IS '麻醉医师姓名（4401 anst_dr_name）';
COMMENT ON COLUMN mrms_homepage_procedure.oper_dr_code IS '术者医保医师代码（4101A oper_dr_code）';
COMMENT ON COLUMN mrms_homepage_procedure.anst_dr_code IS '麻醉医师医保医师代码（4101A anst_dr_code）';
COMMENT ON COLUMN mrms_homepage_procedure.vali_flag IS '有效标志';
COMMENT ON COLUMN mrms_homepage_procedure.created_at IS '创建时间';
COMMENT ON COLUMN mrms_homepage_procedure.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_homepage_procedure.created_by IS '创建人';
COMMENT ON COLUMN mrms_homepage_procedure.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_homepage_procedure.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_homepage_procedure.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_homepage_procedure.deleted_by IS '删除人';

-- from V91__mrms_qc_rule.sql
-- mrms_qc_rule（建表 + 种子）
-- Flyway V92

CREATE TABLE IF NOT EXISTS mrms_qc_rule (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    rule_code           VARCHAR(64)  NOT NULL,
    rule_name           VARCHAR(200) NOT NULL,
    severity_code       VARCHAR(16)  NOT NULL,
    category_code       VARCHAR(32)  NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    expression_json     TEXT,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_qc_rule_tenant_code
    ON mrms_qc_rule (tenant_id, rule_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_qc_rule IS '病案质控规则';
COMMENT ON COLUMN mrms_qc_rule.id IS '主键';
COMMENT ON COLUMN mrms_qc_rule.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_qc_rule.rule_code IS '规则编码';
COMMENT ON COLUMN mrms_qc_rule.rule_name IS '规则名称';
COMMENT ON COLUMN mrms_qc_rule.severity_code IS '严重级别编码';
COMMENT ON COLUMN mrms_qc_rule.category_code IS '规则分类编码';
COMMENT ON COLUMN mrms_qc_rule.enabled IS '是否启用';
COMMENT ON COLUMN mrms_qc_rule.expression_json IS '规则表达式 JSON';
COMMENT ON COLUMN mrms_qc_rule.created_at IS '创建时间';
COMMENT ON COLUMN mrms_qc_rule.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_qc_rule.created_by IS '创建人';
COMMENT ON COLUMN mrms_qc_rule.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_qc_rule.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_qc_rule.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_qc_rule.deleted_by IS '删除人';

-- seed: mrms_qc_rule（tenant_id=0 全局默认）

-- from V92__mrms_qc_defect.sql
-- mrms_qc_defect（建表）
-- Flyway V93

CREATE TABLE IF NOT EXISTS mrms_qc_defect (
    id                          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id                   BIGINT       NOT NULL,
    case_record_id              BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    rule_code                   VARCHAR(64),
    defect_message              VARCHAR(1000) NOT NULL,
    severity_code               VARCHAR(16)  NOT NULL,
    status_code                 VARCHAR(16)  NOT NULL,
    responsible_practitioner_id BIGINT,
    rectified_at                TIMESTAMP,
    closed_at                   TIMESTAMP,
    created_at                  TIMESTAMP    NOT NULL,
    updated_at                  TIMESTAMP,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at                  TIMESTAMP,
    deleted_by                  BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_qc_defect_cr_status
    ON mrms_qc_defect (case_record_id, status_code)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_qc_defect_cr_rule
    ON mrms_qc_defect (case_record_id, rule_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_qc_defect IS '病案质控缺陷';
COMMENT ON COLUMN mrms_qc_defect.id IS '主键';
COMMENT ON COLUMN mrms_qc_defect.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_qc_defect.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_qc_defect.rule_code IS '关联规则编码';
COMMENT ON COLUMN mrms_qc_defect.defect_message IS '缺陷描述';
COMMENT ON COLUMN mrms_qc_defect.severity_code IS '严重级别编码';
COMMENT ON COLUMN mrms_qc_defect.status_code IS '缺陷状态编码';
COMMENT ON COLUMN mrms_qc_defect.responsible_practitioner_id IS '责任医护人员标识';
COMMENT ON COLUMN mrms_qc_defect.rectified_at IS '整改完成时间';
COMMENT ON COLUMN mrms_qc_defect.closed_at IS '关闭时间';
COMMENT ON COLUMN mrms_qc_defect.created_at IS '创建时间';
COMMENT ON COLUMN mrms_qc_defect.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_qc_defect.created_by IS '创建人';
COMMENT ON COLUMN mrms_qc_defect.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_qc_defect.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_qc_defect.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_qc_defect.deleted_by IS '删除人';

-- from V93__mrms_qc_run.sql
-- mrms_qc_run（建表）
-- Flyway V94

CREATE TABLE IF NOT EXISTS mrms_qc_run (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    trigger_code        VARCHAR(32)  NOT NULL,
    defect_open_count   INTEGER      NOT NULL DEFAULT 0,
    defect_warn_count   INTEGER      NOT NULL DEFAULT 0,
    passed              BOOLEAN      NOT NULL,
    finished_at         TIMESTAMP    NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_qc_run_cr
    ON mrms_qc_run (case_record_id, finished_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_qc_run IS '病案质控执行批次';
COMMENT ON COLUMN mrms_qc_run.id IS '主键';
COMMENT ON COLUMN mrms_qc_run.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_qc_run.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_qc_run.trigger_code IS '触发方式编码';
COMMENT ON COLUMN mrms_qc_run.defect_open_count IS '未关闭缺陷数';
COMMENT ON COLUMN mrms_qc_run.defect_warn_count IS '警告级缺陷数';
COMMENT ON COLUMN mrms_qc_run.passed IS '是否通过';
COMMENT ON COLUMN mrms_qc_run.finished_at IS '执行完成时间';
COMMENT ON COLUMN mrms_qc_run.created_at IS '创建时间';
COMMENT ON COLUMN mrms_qc_run.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_qc_run.created_by IS '创建人';
COMMENT ON COLUMN mrms_qc_run.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_qc_run.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_qc_run.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_qc_run.deleted_by IS '删除人';

-- from V94__mrms_required_doc_type.sql
-- mrms_required_doc_type（建表 + 种子）
-- Flyway V95

CREATE TABLE IF NOT EXISTS mrms_required_doc_type (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL,
    document_type_code      VARCHAR(64)  NOT NULL,
    encounter_class_code    VARCHAR(16),
    required                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_required_doc_type
    ON mrms_required_doc_type (tenant_id, document_type_code, COALESCE(encounter_class_code, ''))
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_required_doc_type IS '必写文书类型配置';
COMMENT ON COLUMN mrms_required_doc_type.id IS '主键';
COMMENT ON COLUMN mrms_required_doc_type.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_required_doc_type.document_type_code IS '文书类型编码';
COMMENT ON COLUMN mrms_required_doc_type.encounter_class_code IS '就诊类别编码';
COMMENT ON COLUMN mrms_required_doc_type.required IS '是否必写';
COMMENT ON COLUMN mrms_required_doc_type.created_at IS '创建时间';
COMMENT ON COLUMN mrms_required_doc_type.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_required_doc_type.created_by IS '创建人';
COMMENT ON COLUMN mrms_required_doc_type.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_required_doc_type.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_required_doc_type.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_required_doc_type.deleted_by IS '删除人';

-- from V95__mrms_overdue_notify_log.sql
-- mrms_overdue_notify_log（建表）
-- Flyway V96

CREATE TABLE IF NOT EXISTS mrms_overdue_notify_log (
    id                  BIGINT PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL,
    notify_type_code    VARCHAR(32)  NOT NULL,
    notify_date         DATE         NOT NULL,
    tenant_id           BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    created_by          BIGINT,
    updated_at          TIMESTAMP,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_overdue_notify_day
    ON mrms_overdue_notify_log (case_record_id, notify_type_code, notify_date)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_overdue_notify_cr
    ON mrms_overdue_notify_log (case_record_id, notify_date DESC);

COMMENT ON TABLE mrms_overdue_notify_log IS '逾期通知幂等日志';
COMMENT ON COLUMN mrms_overdue_notify_log.id IS '主键';
COMMENT ON COLUMN mrms_overdue_notify_log.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_overdue_notify_log.notify_type_code IS '通知类型编码';
COMMENT ON COLUMN mrms_overdue_notify_log.notify_date IS '通知日期';
COMMENT ON COLUMN mrms_overdue_notify_log.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_overdue_notify_log.created_at IS '创建时间';
COMMENT ON COLUMN mrms_overdue_notify_log.created_by IS '创建人';
COMMENT ON COLUMN mrms_overdue_notify_log.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_overdue_notify_log.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_overdue_notify_log.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_overdue_notify_log.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_overdue_notify_log.deleted_by IS '删除人';

-- from V96__mrms_borrow_request.sql
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

-- from V97__mrms_upload_log.sql
-- mrms_upload_log（建表）
-- Flyway V98

CREATE TABLE IF NOT EXISTS mrms_upload_log (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    contract_bus_no     VARCHAR(64),
    contract_id         BIGINT,
    infno               VARCHAR(16)  NOT NULL DEFAULT '4401',
    status_code         VARCHAR(32)  NOT NULL,
    msgid               VARCHAR(64),
    request_digest      VARCHAR(128),
    response_snapshot   TEXT,
    err_msg             VARCHAR(500),
    infcode             VARCHAR(16),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_upload_log_cr
    ON mrms_upload_log (case_record_id, created_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_upload_log_tenant_status
    ON mrms_upload_log (tenant_id, status_code, created_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_upload_log IS '4401 病案首页上传日志';
COMMENT ON COLUMN mrms_upload_log.id IS '主键';
COMMENT ON COLUMN mrms_upload_log.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_upload_log.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_upload_log.contract_bus_no IS '合约业务号';
COMMENT ON COLUMN mrms_upload_log.contract_id IS '合约标识';
COMMENT ON COLUMN mrms_upload_log.infno IS '医保接口号';
COMMENT ON COLUMN mrms_upload_log.status_code IS '上传状态编码';
COMMENT ON COLUMN mrms_upload_log.msgid IS '报文 msgid';
COMMENT ON COLUMN mrms_upload_log.request_digest IS '请求摘要';
COMMENT ON COLUMN mrms_upload_log.response_snapshot IS '响应快照';
COMMENT ON COLUMN mrms_upload_log.err_msg IS '错误信息';
COMMENT ON COLUMN mrms_upload_log.infcode IS '医保 infcode';
COMMENT ON COLUMN mrms_upload_log.created_at IS '创建时间';
COMMENT ON COLUMN mrms_upload_log.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_upload_log.created_by IS '创建人';
COMMENT ON COLUMN mrms_upload_log.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_upload_log.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_upload_log.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_upload_log.deleted_by IS '删除人';
