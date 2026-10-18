-- Campus ModularFlyway V1 · cmp_* DDL（文档真源，成码时复制到 campus-starter）
-- history: flyway_schema_history_campus
-- switch: zxplat.modules.campus.enabled
-- 目标路径（规划）：
--   zxplat-module-campus/campus-starter/src/main/resources/db/migration/campus/V1__campus_ddl.sql

-- ========== cmp_campus_site ==========
CREATE TABLE IF NOT EXISTS "public"."cmp_campus_site" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "campus_code" varchar(64) NOT NULL,
    "campus_name" varchar(128) NOT NULL,
    "short_name" varchar(64),
    "address" varchar(512),
    "is_primary" bool NOT NULL DEFAULT false,
    "active_flag" bool NOT NULL DEFAULT true,
    "sort_order" int4 NOT NULL DEFAULT 0,
    "remark" varchar(512),
    CONSTRAINT "cmp_campus_site_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_campus_site_code"
    ON "public"."cmp_campus_site" ("tenant_id", "campus_code")
    WHERE "is_deleted" = false;
COMMENT ON TABLE cmp_campus_site IS '校区主数据';

-- ========== cmp_campus_college ==========
CREATE TABLE IF NOT EXISTS "public"."cmp_campus_college" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "college_code" varchar(64) NOT NULL,
    "college_name" varchar(128) NOT NULL,
    "external_college_id" varchar(64),
    "campus_site_id" int8,
    "active_flag" bool NOT NULL DEFAULT true,
    "sort_order" int4 NOT NULL DEFAULT 0,
    "remark" varchar(512),
    CONSTRAINT "cmp_campus_college_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_campus_college_code"
    ON "public"."cmp_campus_college" ("tenant_id", "college_code")
    WHERE "is_deleted" = false;
CREATE INDEX IF NOT EXISTS "idx_cmp_college_site"
    ON "public"."cmp_campus_college" ("campus_site_id")
    WHERE "is_deleted" = false;
COMMENT ON TABLE cmp_campus_college IS '学院/部主数据（替代 peis_company_college）';

-- ========== cmp_campus_department ==========
CREATE TABLE IF NOT EXISTS "public"."cmp_campus_department" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "department_code" varchar(64) NOT NULL,
    "department_name" varchar(128) NOT NULL,
    "campus_site_id" int8 NOT NULL,
    "college_id" int8,
    "dept_type_enum" varchar(32),
    "active_flag" bool NOT NULL DEFAULT true,
    "sort_order" int4 NOT NULL DEFAULT 0,
    "remark" varchar(512),
    CONSTRAINT "cmp_campus_department_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_campus_dept_code"
    ON "public"."cmp_campus_department" ("tenant_id", "department_code")
    WHERE "is_deleted" = false;
CREATE INDEX IF NOT EXISTS "idx_cmp_dept_site_college"
    ON "public"."cmp_campus_department" ("campus_site_id", "college_id")
    WHERE "is_deleted" = false;
COMMENT ON TABLE cmp_campus_department IS '教职工部门/单位（非临床科室）';

-- ========== cmp_patient_student ==========
CREATE TABLE IF NOT EXISTS "public"."cmp_patient_student" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "patient_id" int8,
    "name" varchar(128) NOT NULL,
    "py_str" varchar(64),
    "wb_str" varchar(64),
    "gender_enum" varchar(32),
    "age" int4,
    "birthday" date,
    "student_no" varchar(64) NOT NULL,
    "id_card" varchar(32),
    "phone" varchar(32),
    "college_id" int8 NOT NULL,
    "college_name" varchar(128),
    "campus_code" varchar(64) NOT NULL,
    "major" varchar(128),
    "major_code" varchar(64),
    "edu_level_enum" varchar(32),
    "enroll_time" timestamptz(6),
    "leave_time" timestamptz(6),
    "grade" varchar(32),
    "class_name" varchar(128),
    "school_year" int4,
    "study_type_enum" varchar(32),
    "school_status_enum" varchar(32) NOT NULL DEFAULT 'IN_SCHOOL',
    "physical_exam_result_enum" varchar(32),
    "native_place" varchar(128),
    "counselor_name" varchar(128),
    "counselor_phone" varchar(32),
    "address" varchar(512),
    "doorplate" varchar(64),
    "active_flag" bool NOT NULL DEFAULT true,
    CONSTRAINT "cmp_patient_student_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_student_no"
    ON "public"."cmp_patient_student" ("tenant_id", "student_no")
    WHERE "is_deleted" = false;
CREATE INDEX IF NOT EXISTS "idx_cmp_student_patient"
    ON "public"."cmp_patient_student" ("patient_id")
    WHERE "is_deleted" = false;
CREATE INDEX IF NOT EXISTS "idx_cmp_student_college_campus"
    ON "public"."cmp_patient_student" ("college_id", "campus_code")
    WHERE "is_deleted" = false;
COMMENT ON TABLE cmp_patient_student IS '学生学籍档案（Campus 真源；非 adm_patient_student）';
COMMENT ON COLUMN cmp_patient_student.student_no IS '学号（← peis student_id）';
COMMENT ON COLUMN cmp_patient_student.campus_code IS '所属校区编码';
COMMENT ON COLUMN cmp_patient_student.college_id IS '所属学院 → cmp_campus_college.id';

-- ========== cmp_patient_staff ==========
CREATE TABLE IF NOT EXISTS "public"."cmp_patient_staff" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "patient_id" int8,
    "name" varchar(128) NOT NULL,
    "py_str" varchar(64),
    "wb_str" varchar(64),
    "gender_enum" varchar(32),
    "id_card" varchar(32),
    "phone" varchar(32),
    "employee_no" varchar(64) NOT NULL,
    "medical_code" varchar(64),
    "department_id" int8 NOT NULL,
    "campus_site_id" int8 NOT NULL,
    "staff_type_enum" varchar(32),
    "valid_from" timestamptz(6),
    "valid_to" timestamptz(6),
    "job_status_enum" varchar(32) NOT NULL DEFAULT 'ACTIVE',
    "active_flag" bool NOT NULL DEFAULT true,
    CONSTRAINT "cmp_patient_staff_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_staff_employee"
    ON "public"."cmp_patient_staff" ("tenant_id", "employee_no")
    WHERE "is_deleted" = false;
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_staff_medical"
    ON "public"."cmp_patient_staff" ("tenant_id", "medical_code")
    WHERE "is_deleted" = false AND "medical_code" IS NOT NULL;
CREATE INDEX IF NOT EXISTS "idx_cmp_staff_patient"
    ON "public"."cmp_patient_staff" ("patient_id")
    WHERE "is_deleted" = false;
COMMENT ON TABLE cmp_patient_staff IS '教职工档案（工号主键；医疗证可空）';
COMMENT ON COLUMN cmp_patient_staff.employee_no IS '工号（← peis work_id）';
COMMENT ON COLUMN cmp_patient_staff.medical_code IS '医疗证号（可空；有则唯一）';

-- ========== cmp_pe_profile ==========
CREATE TABLE IF NOT EXISTS "public"."cmp_pe_profile" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "subject_type_enum" varchar(32) NOT NULL,
    "subject_id" int8 NOT NULL,
    "patient_id" int8,
    "card_no" varchar(64),
    "medical_code" varchar(64),
    "gf_status_enum" varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    "category_enum" varchar(32),
    "valid_from" timestamptz(6),
    "valid_to" timestamptz(6),
    "eligible_flag" bool NOT NULL DEFAULT true,
    "remark" varchar(512),
    CONSTRAINT "cmp_pe_profile_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_pe_subject"
    ON "public"."cmp_pe_profile" ("tenant_id", "subject_type_enum", "subject_id")
    WHERE "is_deleted" = false;
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_pe_card"
    ON "public"."cmp_pe_profile" ("tenant_id", "card_no")
    WHERE "is_deleted" = false AND "card_no" IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_pe_medical"
    ON "public"."cmp_pe_profile" ("tenant_id", "medical_code")
    WHERE "is_deleted" = false AND "medical_code" IS NOT NULL;
COMMENT ON TABLE cmp_pe_profile IS '公费资格档案';
COMMENT ON COLUMN cmp_pe_profile.card_no IS '公费卡号/二维码（← peis student.bus_no；非 Patient.bus_no）';
COMMENT ON COLUMN cmp_pe_profile.gf_status_enum IS 'NOT_ELIGIBLE/ELIGIBLE/UNKNOWN（← gf_status 0|1/2）';

-- ========== cmp_archive_transfer（调动/转院） ==========
CREATE TABLE IF NOT EXISTS "public"."cmp_archive_transfer" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "created_at" timestamptz(6) NOT NULL,
    "created_by" int8,
    "updated_at" timestamptz(6),
    "updated_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "subject_type_enum" varchar(32) NOT NULL,
    "subject_id" int8 NOT NULL,
    "patient_id" int8,
    "transfer_type_enum" varchar(32) NOT NULL,
    "effective_date" date NOT NULL,
    "from_campus_code" varchar(64),
    "to_campus_code" varchar(64),
    "from_college_id" int8,
    "to_college_id" int8,
    "from_department_id" int8,
    "to_department_id" int8,
    "reason" varchar(512),
    "external_ref" varchar(128),
    "transfer_source_enum" varchar(32) NOT NULL,
    "operator_id" int8,
    "operator_name" varchar(128),
    "operate_time" timestamptz(6) NOT NULL,
    "change_log_batch_id" varchar(64),
    CONSTRAINT "cmp_archive_transfer_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "idx_cmp_xfr_subject"
    ON "public"."cmp_archive_transfer" ("tenant_id", "subject_type_enum", "subject_id", "effective_date" DESC);
CREATE INDEX IF NOT EXISTS "idx_cmp_xfr_campus"
    ON "public"."cmp_archive_transfer" ("tenant_id", "to_campus_code", "effective_date" DESC);
COMMENT ON TABLE cmp_archive_transfer IS '学生转院/教职工调岗记录（查看时间线）';

-- ========== cmp_archive_change_log ==========
CREATE TABLE IF NOT EXISTS "public"."cmp_archive_change_log" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "created_at" timestamptz(6) NOT NULL,
    "created_by" int8,
    "subject_type_enum" varchar(32) NOT NULL,
    "subject_id" int8 NOT NULL,
    "change_type_enum" varchar(32) NOT NULL,
    "field_name" varchar(128) NOT NULL,
    "old_value" text,
    "new_value" text,
    "operator_id" int8,
    "operator_name" varchar(128),
    "change_time" timestamptz(6) NOT NULL,
    "change_source_enum" varchar(32) NOT NULL,
    "campus_code" varchar(64),
    "batch_id" varchar(64) NOT NULL,
    "remark" varchar(512),
    CONSTRAINT "cmp_archive_change_log_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "idx_cmp_chg_subject"
    ON "public"."cmp_archive_change_log" ("tenant_id", "subject_type_enum", "subject_id", "change_time" DESC);
CREATE INDEX IF NOT EXISTS "idx_cmp_chg_batch"
    ON "public"."cmp_archive_change_log" ("batch_id");
COMMENT ON TABLE cmp_archive_change_log IS '学生/教职工档案字段级变更';

-- ========== 公费配置 / 比例 ==========
CREATE TABLE IF NOT EXISTS "public"."cmp_gf_switch" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "switch_key" varchar(64) NOT NULL,
    "switch_value" varchar(512) NOT NULL,
    CONSTRAINT "cmp_gf_switch_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_gf_switch"
    ON "public"."cmp_gf_switch" ("tenant_id", "switch_key")
    WHERE "is_deleted" = false;

CREATE TABLE IF NOT EXISTS "public"."cmp_gf_free_percent" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "class_enum" varchar(32) NOT NULL,
    "self_pay_ratio" numeric(10, 6) NOT NULL,
    "charge_mode_enum" varchar(32) NOT NULL,
    CONSTRAINT "cmp_gf_free_percent_pkey" PRIMARY KEY ("id")
);
-- charge_mode_enum 必填：SELF_PAY_GF | AFTER_YB_GF（ADR-CMP-001 D7）
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_gf_free_percent"
    ON "public"."cmp_gf_free_percent" ("tenant_id", "class_enum", "charge_mode_enum")
    WHERE "is_deleted" = false;
COMMENT ON COLUMN cmp_gf_free_percent.charge_mode_enum IS 'SELF_PAY_GF | AFTER_YB_GF';

CREATE TABLE IF NOT EXISTS "public"."cmp_gf_charge_ratio" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "catalog_code" varchar(64) NOT NULL,
    "charge_mode_enum" varchar(32) NOT NULL,
    "ratio" numeric(10, 6) NOT NULL,
    "effective_from" timestamptz(6),
    "effective_to" timestamptz(6),
    CONSTRAINT "cmp_gf_charge_ratio_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_gf_catalog_mode"
    ON "public"."cmp_gf_charge_ratio" ("tenant_id", "catalog_code", "charge_mode_enum")
    WHERE "is_deleted" = false;
COMMENT ON COLUMN cmp_gf_charge_ratio.charge_mode_enum IS 'SELF_PAY_GF | AFTER_YB_GF';

CREATE TABLE IF NOT EXISTS "public"."cmp_gf_charge_ratio_item" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "item_code" varchar(64) NOT NULL,
    "charge_mode_enum" varchar(32) NOT NULL,
    "ratio" numeric(10, 6) NOT NULL,
    "effective_from" timestamptz(6),
    "effective_to" timestamptz(6),
    CONSTRAINT "cmp_gf_charge_ratio_item_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_gf_item_mode"
    ON "public"."cmp_gf_charge_ratio_item" ("tenant_id", "item_code", "charge_mode_enum")
    WHERE "is_deleted" = false;
COMMENT ON COLUMN cmp_gf_charge_ratio_item.charge_mode_enum IS 'SELF_PAY_GF | AFTER_YB_GF';

CREATE TABLE IF NOT EXISTS "public"."cmp_gf_ratio_request" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "request_no" varchar(64) NOT NULL,
    "charge_mode_enum" varchar(32) NOT NULL,
    "item_code" varchar(64) NOT NULL,
    "old_ratio" numeric(10, 6),
    "new_ratio" numeric(10, 6) NOT NULL,
    "status_enum" varchar(32) NOT NULL,
    "flow_instance_id" varchar(128),
    "applicant_id" int8,
    "applicant_name" varchar(128),
    "approve_time" timestamptz(6),
    "remark" varchar(512),
    CONSTRAINT "cmp_gf_ratio_request_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cmp_gf_request_no"
    ON "public"."cmp_gf_ratio_request" ("tenant_id", "request_no")
    WHERE "is_deleted" = false;
CREATE INDEX IF NOT EXISTS "idx_cmp_gf_request_mode"
    ON "public"."cmp_gf_ratio_request" ("tenant_id", "charge_mode_enum", "status_enum")
    WHERE "is_deleted" = false;
COMMENT ON TABLE cmp_gf_ratio_request IS '公费比例调整申请（按收费模式分存）';
COMMENT ON COLUMN cmp_gf_ratio_request.charge_mode_enum IS 'SELF_PAY_GF | AFTER_YB_GF';
