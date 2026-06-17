-- wkf_schedule（建表）
-- Flyway V65

CREATE TABLE IF NOT EXISTS "public"."wkf_schedule" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "organization_id" int8 NOT NULL,
    "name" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "planning_horizon_start" timestamptz(6),
    "planning_horizon_end" timestamptz(6),
    "service_type_concept_json" varchar(255),
    "healthcare_service_id" int8,
    "service_category_json" varchar(255),
    "specialty_json" varchar(255),
    "comment_markdown" varchar(255),
    "substitute_practitioner_id" int8,
    "substitute_reason" varchar(255),
    CONSTRAINT "wkf_schedule_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE wkf_schedule IS '号源日程（FHIR Schedule 语义）';

COMMENT ON COLUMN wkf_schedule.id IS '主键';
COMMENT ON COLUMN wkf_schedule.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_schedule.created_at IS '创建时间';
COMMENT ON COLUMN wkf_schedule.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_schedule.created_by IS '创建人';
COMMENT ON COLUMN wkf_schedule.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_schedule.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_schedule.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_schedule.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_schedule.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_schedule.organization_id IS '机构标识';
COMMENT ON COLUMN wkf_schedule.name IS '日程显示名称';
COMMENT ON COLUMN wkf_schedule.status_enum IS '业务状态（draft/active/inactive/retired）';
COMMENT ON COLUMN wkf_schedule.active_flag IS '启用标记';
COMMENT ON COLUMN wkf_schedule.planning_horizon_start IS '规划窗口起始时间';
COMMENT ON COLUMN wkf_schedule.planning_horizon_end IS '规划窗口结束时间';
COMMENT ON COLUMN wkf_schedule.service_type_concept_json IS '服务类型概念快照 JSON';
COMMENT ON COLUMN wkf_schedule.healthcare_service_id IS '医疗服务引用';
COMMENT ON COLUMN wkf_schedule.service_category_json IS '服务大类快照 JSON';
COMMENT ON COLUMN wkf_schedule.specialty_json IS '专科快照 JSON';
COMMENT ON COLUMN wkf_schedule.comment_markdown IS '可用性说明（Markdown）';
COMMENT ON COLUMN wkf_schedule.substitute_practitioner_id IS '替诊医师主键（顶班 B；原医师仍保留在 Actor）';
COMMENT ON COLUMN wkf_schedule.substitute_reason IS '替诊原因说明';

