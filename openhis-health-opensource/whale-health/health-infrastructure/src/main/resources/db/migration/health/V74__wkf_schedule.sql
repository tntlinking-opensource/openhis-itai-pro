-- wkf_schedule（建表）
-- Flyway V74

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
    CONSTRAINT "wkf_schedule_pkey" PRIMARY KEY ("id")
);