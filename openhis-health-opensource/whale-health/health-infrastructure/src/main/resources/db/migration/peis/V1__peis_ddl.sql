-- PEIS ModularFlyway V1 · peis_* DDL（greenfield baseline · 最终态）
-- history: flyway_schema_history_peis
-- switch: openhis.modules.peis.enabled
-- CLN / AOL 仍在 health 核心，本脚本仅 peis_* 表
-- folded: V1 + V6（团检）+ V7（健康档案）+ V10（复查任务）+ V14–V17（合同多套餐/金额/支付状态）DDL
-- final: peis_encounter_ext.referrer_name / breakfast_flag；peis_patient_health_archive.tag_code

-- peis_service_request_ext（建表）
-- DM-PEIS-001

CREATE TABLE IF NOT EXISTS "public"."peis_service_request_ext" (
    "service_request_id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "exec_status_enum" varchar(64) NOT NULL DEFAULT 'pending',
    "lis_dispatch_enum" varchar(64) NOT NULL DEFAULT 'notSent',
    "pacs_dispatch_enum" varchar(64) NOT NULL DEFAULT 'notSent',
    "lis_fail_count" int4 NOT NULL DEFAULT 0,
    "lis_last_error" varchar(512),
    "pacs_fail_count" int4 NOT NULL DEFAULT 0,
    "pacs_last_error" varchar(512),
    CONSTRAINT "peis_service_request_ext_pkey" PRIMARY KEY ("service_request_id")
);

COMMENT ON TABLE peis_service_request_ext IS '体检 ServiceRequest 执行/外发扩展（无 barcode，D6）';
COMMENT ON COLUMN peis_service_request_ext.service_request_id IS '服务请求标识（主键）';
COMMENT ON COLUMN peis_service_request_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_service_request_ext.org_id IS '机构标识';
COMMENT ON COLUMN peis_service_request_ext.created_at IS '创建时间';
COMMENT ON COLUMN peis_service_request_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_service_request_ext.created_by IS '创建人';
COMMENT ON COLUMN peis_service_request_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_service_request_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_service_request_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_service_request_ext.deleted_by IS '删除人';
COMMENT ON COLUMN peis_service_request_ext.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_service_request_ext.exec_status_enum IS '执行状态枚举';
COMMENT ON COLUMN peis_service_request_ext.lis_dispatch_enum IS 'LIS 外发状态枚举';
COMMENT ON COLUMN peis_service_request_ext.pacs_dispatch_enum IS 'PACS 外发状态枚举';
COMMENT ON COLUMN peis_service_request_ext.lis_fail_count IS 'LIS 外发失败次数（人工补传成功后清零）';
COMMENT ON COLUMN peis_service_request_ext.lis_last_error IS 'LIS 最近一次外发失败原因摘要';
COMMENT ON COLUMN peis_service_request_ext.pacs_fail_count IS 'PACS 外发失败次数（人工补传成功后清零）';
COMMENT ON COLUMN peis_service_request_ext.pacs_last_error IS 'PACS 最近一次外发失败原因摘要';

CREATE INDEX IF NOT EXISTS idx_peis_sr_ext_lis_failed
    ON peis_service_request_ext (updated_at)
    WHERE is_deleted = false AND lis_dispatch_enum = 'failed';

CREATE INDEX IF NOT EXISTS idx_peis_sr_ext_pacs_failed
    ON peis_service_request_ext (updated_at)
    WHERE is_deleted = false AND pacs_dispatch_enum = 'failed';

-- peis_encounter_ext（建表）
-- DM-PEIS-007

CREATE TABLE IF NOT EXISTS "public"."peis_encounter_ext" (
    "encounter_id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "package_activity_id" int8,
    "package_snapshot_json" text,
    "group_flag" bool NOT NULL DEFAULT false,
    "company_id" int8,
    "contract_id" int8,
    "group_batch_id" int8,
    "booking_appointment_id" int8,
    "bus_no" varchar(255),
    "referrer_name" varchar(128),
    "breakfast_flag" boolean,
    CONSTRAINT "peis_encounter_ext_pkey" PRIMARY KEY ("encounter_id")
);
CREATE INDEX IF NOT EXISTS "idx_peis_enc_ext_bus_no"
    ON "public"."peis_encounter_ext" ("bus_no")
    WHERE "is_deleted" = false;

COMMENT ON TABLE peis_encounter_ext IS 'Encounter(PE) 体检扩展（个检/团检）';
COMMENT ON COLUMN peis_encounter_ext.encounter_id IS '就诊标识（主键）';
COMMENT ON COLUMN peis_encounter_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_encounter_ext.org_id IS '机构标识';
COMMENT ON COLUMN peis_encounter_ext.created_at IS '创建时间';
COMMENT ON COLUMN peis_encounter_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_encounter_ext.created_by IS '创建人';
COMMENT ON COLUMN peis_encounter_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_encounter_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_encounter_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_encounter_ext.deleted_by IS '删除人';
COMMENT ON COLUMN peis_encounter_ext.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_encounter_ext.package_activity_id IS '套餐活动定义标识';
COMMENT ON COLUMN peis_encounter_ext.package_snapshot_json IS '套餐快照 JSON';
COMMENT ON COLUMN peis_encounter_ext.group_flag IS '是否团检';
COMMENT ON COLUMN peis_encounter_ext.company_id IS '单位标识';
COMMENT ON COLUMN peis_encounter_ext.contract_id IS '合约标识';
COMMENT ON COLUMN peis_encounter_ext.group_batch_id IS '团检批次标识';
COMMENT ON COLUMN peis_encounter_ext.booking_appointment_id IS '预约队列标识';
COMMENT ON COLUMN peis_encounter_ext.bus_no IS '业务编号';
COMMENT ON COLUMN peis_encounter_ext.referrer_name IS '本次体检推荐人';
COMMENT ON COLUMN peis_encounter_ext.breakfast_flag IS '本次体检是否提供早餐（true有 / false无 / null未填）';

-- peis_common_result（建表）
-- DM-PEIS-003

CREATE TABLE IF NOT EXISTS "public"."peis_common_result" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "observation_definition_id" int8,
    "dept_org_id" int8,
    "display" varchar(512) NOT NULL,
    "value_string" text,
    "interpretation_code" varchar(64),
    "sort_num" int4 NOT NULL DEFAULT 0,
    "status_enum" varchar(64) NOT NULL DEFAULT 'active',
    CONSTRAINT "peis_common_result_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE peis_common_result IS '分科常见结果模板';
COMMENT ON COLUMN peis_common_result.id IS '主键';
COMMENT ON COLUMN peis_common_result.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_common_result.org_id IS '机构标识';
COMMENT ON COLUMN peis_common_result.created_at IS '创建时间';
COMMENT ON COLUMN peis_common_result.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_common_result.created_by IS '创建人';
COMMENT ON COLUMN peis_common_result.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_common_result.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_common_result.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_common_result.deleted_by IS '删除人';
COMMENT ON COLUMN peis_common_result.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_common_result.observation_definition_id IS '观察项定义标识';
COMMENT ON COLUMN peis_common_result.dept_org_id IS '科室机构标识';
COMMENT ON COLUMN peis_common_result.display IS '展示文本';
COMMENT ON COLUMN peis_common_result.value_string IS '结果文本';
COMMENT ON COLUMN peis_common_result.interpretation_code IS '解读编码';
COMMENT ON COLUMN peis_common_result.sort_num IS '排序号';
COMMENT ON COLUMN peis_common_result.status_enum IS '状态枚举';

-- peis_config（建表）
-- DM-PEIS-004

CREATE TABLE IF NOT EXISTS "public"."peis_config" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "config_key" varchar(255) NOT NULL,
    "config_value" text,
    CONSTRAINT "peis_config_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_peis_config_tenant_key"
    ON "public"."peis_config" ("tenant_id", "config_key")
    WHERE "is_deleted" = false;

COMMENT ON TABLE peis_config IS 'PEIS 模块 KV 参数（对齐 lis_config）';
COMMENT ON COLUMN peis_config.id IS '主键';
COMMENT ON COLUMN peis_config.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_config.org_id IS '机构标识';
COMMENT ON COLUMN peis_config.created_at IS '创建时间';
COMMENT ON COLUMN peis_config.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_config.created_by IS '创建人';
COMMENT ON COLUMN peis_config.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_config.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_config.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_config.deleted_by IS '删除人';
COMMENT ON COLUMN peis_config.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_config.config_key IS '配置键';
COMMENT ON COLUMN peis_config.config_value IS '配置值';

-- peis_report_grant_ext（建表）· P1
-- DM-PEIS-005

CREATE TABLE IF NOT EXISTS "public"."peis_report_grant_ext" (
    "diagnostic_report_id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "grant_status_enum" varchar(64) NOT NULL DEFAULT 'pending',
    "grant_time" timestamptz(6),
    "grant_by" int8,
    "recipient_name" varchar(255),
    CONSTRAINT "peis_report_grant_ext_pkey" PRIMARY KEY ("diagnostic_report_id")
);

COMMENT ON TABLE peis_report_grant_ext IS '体检报告发放/签收扩展（1:1 DiagnosticReport）';
COMMENT ON COLUMN peis_report_grant_ext.diagnostic_report_id IS '总检报告标识（主键）';
COMMENT ON COLUMN peis_report_grant_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_report_grant_ext.org_id IS '机构标识';
COMMENT ON COLUMN peis_report_grant_ext.created_at IS '创建时间';
COMMENT ON COLUMN peis_report_grant_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_report_grant_ext.created_by IS '创建人';
COMMENT ON COLUMN peis_report_grant_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_report_grant_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_report_grant_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_report_grant_ext.deleted_by IS '删除人';
COMMENT ON COLUMN peis_report_grant_ext.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_report_grant_ext.grant_status_enum IS '发放状态枚举';
COMMENT ON COLUMN peis_report_grant_ext.grant_time IS '发放时间';
COMMENT ON COLUMN peis_report_grant_ext.grant_by IS '发放人标识';
COMMENT ON COLUMN peis_report_grant_ext.recipient_name IS '签收人姓名';

-- peis_auto_express（建表）· P1
-- DM-PEIS-006

CREATE TABLE IF NOT EXISTS "public"."peis_auto_express" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "name" varchar(512) NOT NULL,
    "express_script" text NOT NULL,
    "target_condition_def_id" int8,
    "rule_type_code" varchar(64) NOT NULL,
    "status_enum" varchar(64) NOT NULL DEFAULT 'active',
    CONSTRAINT "peis_auto_express_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "idx_peis_auto_express_rule_type"
    ON "public"."peis_auto_express" ("tenant_id", "rule_type_code", "status_enum")
    WHERE "is_deleted" = false;

COMMENT ON TABLE peis_auto_express IS 'PEIS 自动诊断/危急值表达式规则';
COMMENT ON COLUMN peis_auto_express.id IS '主键';
COMMENT ON COLUMN peis_auto_express.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_auto_express.org_id IS '机构标识';
COMMENT ON COLUMN peis_auto_express.created_at IS '创建时间';
COMMENT ON COLUMN peis_auto_express.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_auto_express.created_by IS '创建人';
COMMENT ON COLUMN peis_auto_express.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_auto_express.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_auto_express.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_auto_express.deleted_by IS '删除人';
COMMENT ON COLUMN peis_auto_express.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_auto_express.name IS '规则名称';
COMMENT ON COLUMN peis_auto_express.express_script IS '表达式脚本';
COMMENT ON COLUMN peis_auto_express.target_condition_def_id IS '目标诊断定义标识';
COMMENT ON COLUMN peis_auto_express.rule_type_code IS '规则类型编码';
COMMENT ON COLUMN peis_auto_express.status_enum IS '状态枚举';

-- ========== folded from V6__peis_group_check_and_menu.sql (DDL only) ==========
-- PEIS V6：团检单位/合同/体检分组/名册
-- 主路径：按合同导入名册 → 选定套餐建单 → 到检 → 分科（PEIS-GRP-TREE-PKG-001）

CREATE TABLE IF NOT EXISTS peis_group_company (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT,
    parent_id BIGINT,
    name VARCHAR(128) NOT NULL,
    campus_college_id BIGINT,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_peis_group_company_parent
    ON peis_group_company (parent_id)
    WHERE is_deleted = false;

COMMENT ON COLUMN peis_group_company.parent_id IS '上级单位节点；空表示根节点';

CREATE TABLE IF NOT EXISTS peis_group_contract (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT,
    company_id BIGINT NOT NULL,
    package_activity_id BIGINT,
    contract_name VARCHAR(128),
    valid_from DATE,
    valid_to DATE,
    contract_amount NUMERIC(18, 2),
    pay_status_code VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    contract_type_code VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON COLUMN peis_group_contract.package_activity_id IS '兼容字段：默认套餐（取关联表第一项）';
COMMENT ON COLUMN peis_group_contract.contract_amount IS '合同金额（元）';
COMMENT ON COLUMN peis_group_contract.pay_status_code IS '合同支付状态：UNPAID/PAID';
COMMENT ON COLUMN peis_group_contract.contract_type_code IS '合同类型编码，对齐 peis_contract_type.type_code；散客无合同行';

CREATE TABLE IF NOT EXISTS peis_group_contract_package (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    package_activity_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_peis_group_contract_pkg
    ON peis_group_contract_package (contract_id, package_activity_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_peis_group_contract_pkg_contract
    ON peis_group_contract_package (contract_id)
    WHERE is_deleted = false;

COMMENT ON TABLE peis_group_contract_package IS '团检合同可选套餐（一个合同可绑多个套餐）';

CREATE TABLE IF NOT EXISTS peis_group_batch (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT,
    company_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    batch_name VARCHAR(128) NOT NULL,
    package_activity_id BIGINT NOT NULL,
    booking_date DATE,
    status_code VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON COLUMN peis_group_batch.package_activity_id IS '兼容字段：本体检分组默认套餐（取 peis_group_batch_package 第一项）';

CREATE TABLE IF NOT EXISTS peis_group_batch_package (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    package_activity_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_peis_group_batch_pkg
    ON peis_group_batch_package (batch_id, package_activity_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_peis_group_batch_pkg_batch
    ON peis_group_batch_package (batch_id)
    WHERE is_deleted = false;

COMMENT ON TABLE peis_group_batch_package IS '体检分组可选套餐（须 ⊆ 合同套餐池）';

CREATE TABLE IF NOT EXISTS peis_group_roster (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT,
    batch_id BIGINT NOT NULL,
    company_id BIGINT,
    patient_id BIGINT,
    full_name VARCHAR(128) NOT NULL,
    id_card_no VARCHAR(32),
    student_no VARCHAR(64),
    employee_no VARCHAR(64),
    college_id BIGINT,
    class_name VARCHAR(128),
    mobile_phone VARCHAR(32),
    encounter_id BIGINT,
    selected_package_activity_id BIGINT,
    pay_status_code VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    arrival_status_code VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON COLUMN peis_group_roster.company_id IS '受检者所在单位树节点（可与合同挂点不同）';
COMMENT ON COLUMN peis_group_roster.selected_package_activity_id IS '已选定套餐 AD；建单前可为空';

CREATE TABLE IF NOT EXISTS peis_group_roster_package (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    roster_id BIGINT NOT NULL,
    package_activity_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_peis_group_roster_pkg
    ON peis_group_roster_package (roster_id, package_activity_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_peis_group_roster_pkg_roster
    ON peis_group_roster_package (roster_id)
    WHERE is_deleted = false;

COMMENT ON TABLE peis_group_roster_package IS '名册可选套餐（须 ⊆ 所属分组套餐）';

CREATE INDEX IF NOT EXISTS idx_peis_group_roster_batch ON peis_group_roster (batch_id) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_peis_group_roster_pay ON peis_group_roster (batch_id, pay_status_code) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_peis_group_roster_patient ON peis_group_roster (patient_id) WHERE is_deleted = false;

-- ========== folded from V7__peis_patient_health_archive.sql ==========
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
    tag_code varchar(64),
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
COMMENT ON COLUMN peis_patient_health_archive.tag_code IS '体检标签（如 ADULT成人 / CHILD儿童）';

-- ========== folded from V10__seed_report_print_grant_recheck_menus.sql (DDL only) ==========
-- PEIS V10：复查任务表（AS-PEIS-REC-001）

CREATE TABLE IF NOT EXISTS peis_recheck_task (
  id int8 PRIMARY KEY,
  tenant_id int8,
  org_id int8,
  created_at timestamptz NOT NULL,
  updated_at timestamptz,
  created_by int8,
  updated_by int8,
  is_deleted bool NOT NULL DEFAULT false,
  deleted_at timestamptz,
  deleted_by int8,
  version int4 NOT NULL DEFAULT 0,
  encounter_id int8 NOT NULL,
  patient_id int8,
  status_enum varchar(64) NOT NULL DEFAULT 'requested',
  reason varchar(512),
  diagnostic_report_id int8,
  completed_at timestamptz,
  completed_by int8
);

CREATE INDEX IF NOT EXISTS idx_peis_recheck_task_encounter
  ON peis_recheck_task (encounter_id, is_deleted);

COMMENT ON TABLE peis_recheck_task IS '体检复查任务（Peis 04 §4.5 方案 A 本地 Task 语义，待 WKF Task 迁入）';

-- ========== folded from V5__contract_type_dict_and_column.sql (DDL) ==========
CREATE TABLE IF NOT EXISTS peis_contract_type (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    type_code VARCHAR(32) NOT NULL,
    type_name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_peis_contract_type_code
    ON peis_contract_type (type_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE peis_contract_type IS '团检合同类型字典（号源分池维度；booking 只读）';
COMMENT ON COLUMN peis_contract_type.type_code IS '类型编码：STUDENT/STAFF/ENTERPRISE/WALK_IN 等';
COMMENT ON COLUMN peis_contract_type.type_name IS '显示名称';
COMMENT ON COLUMN peis_contract_type.sort_order IS '排序号，越小越靠前';
COMMENT ON COLUMN peis_contract_type.enabled_flag IS '是否启用';
