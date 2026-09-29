-- V46：病案首页编辑申请 + 主档 status_code 收成归档三态（DRAFT / PENDING_ARCHIVE / ARCHIVED）
-- 质控/收案/退回态迁出主档（映射进归档三态）；质控以后另开字段/子域。

CREATE TABLE IF NOT EXISTS "public"."mrms_homepage_edit_application" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "case_record_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "status_enum" varchar(32) NOT NULL,
    "apply_reason" varchar(500) NOT NULL,
    "reject_reason" varchar(500),
    "from_version" int4 NOT NULL,
    "to_version" int4,
    "old_content_json" text NOT NULL,
    "new_content_json" text NOT NULL,
    "applicant_id" int8 NOT NULL,
    "applied_at" timestamptz(6) NOT NULL,
    "submitted_at" timestamptz(6),
    "reviewer_id" int8,
    "reviewed_at" timestamptz(6),
    CONSTRAINT "mrms_homepage_edit_application_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE mrms_homepage_edit_application IS '病案首页编辑申请（MRMS 域；与 EMR 文书编辑申请分表）';
COMMENT ON COLUMN mrms_homepage_edit_application.case_record_id IS '病案主档 id';
COMMENT ON COLUMN mrms_homepage_edit_application.encounter_id IS '住院就诊 id';
COMMENT ON COLUMN mrms_homepage_edit_application.status_enum IS 'DRAFTING / PENDING_REVIEW / APPROVED / REJECTED';
COMMENT ON COLUMN mrms_homepage_edit_application.apply_reason IS '申请修改原因';
COMMENT ON COLUMN mrms_homepage_edit_application.reject_reason IS '驳回原因（驳回时必填）';
COMMENT ON COLUMN mrms_homepage_edit_application.from_version IS '创建时首页 version_no';
COMMENT ON COLUMN mrms_homepage_edit_application.to_version IS '交审后目标版本=from+1';
COMMENT ON COLUMN mrms_homepage_edit_application.old_content_json IS '创建时首页可编辑分区全量快照';
COMMENT ON COLUMN mrms_homepage_edit_application.new_content_json IS '工作稿；同意后回写（跳过费用）';

CREATE UNIQUE INDEX IF NOT EXISTS "uk_mrms_hp_edit_active"
    ON "public"."mrms_homepage_edit_application" ("tenant_id", "case_record_id")
    WHERE "is_deleted" = false
      AND "status_enum" IN ('DRAFTING', 'PENDING_REVIEW');

CREATE INDEX IF NOT EXISTS "idx_mrms_hp_edit_review"
    ON "public"."mrms_homepage_edit_application" ("tenant_id", "status_enum", "submitted_at" DESC)
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_mrms_hp_edit_case"
    ON "public"."mrms_homepage_edit_application" ("case_record_id")
    WHERE "is_deleted" = false;

-- 主档归档三态归一（质控/收案/退回 → DRAFT；已交未审 → PENDING_ARCHIVE）
UPDATE mrms_case_record
SET status_code = 'ARCHIVED'
WHERE is_deleted = false AND LOWER(TRIM(status_code)) = 'archived';

UPDATE mrms_case_record
SET status_code = 'PENDING_ARCHIVE'
WHERE is_deleted = false AND LOWER(TRIM(status_code)) IN ('submitted', 'pending_archive');

UPDATE mrms_case_record
SET status_code = 'DRAFT'
WHERE is_deleted = false
  AND LOWER(TRIM(status_code)) IN ('draft', 'returned', 'received', 'qcing');

-- 兜底：仍非三态大写的存量一律 DRAFT（跑完本脚本后仅剩 DRAFT/PENDING_ARCHIVE/ARCHIVED）
UPDATE mrms_case_record
SET status_code = 'DRAFT'
WHERE is_deleted = false
  AND (status_code IS NULL
       OR status_code NOT IN ('DRAFT', 'PENDING_ARCHIVE', 'ARCHIVED'));

COMMENT ON COLUMN mrms_case_record.status_code IS '归档三态：DRAFT / PENDING_ARCHIVE / ARCHIVED（质控不占用本列）';
