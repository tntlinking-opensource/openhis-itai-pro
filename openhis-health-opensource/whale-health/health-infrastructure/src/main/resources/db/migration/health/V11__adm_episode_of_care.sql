-- adm_episode_of_care（建表）
-- Flyway V11

CREATE TABLE IF NOT EXISTS "public"."adm_episode_of_care" (
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
    "patient_id" int8 NOT NULL,
    "organization_id" int8 NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "care_team_id" int8,
    "care_type" varchar(255),
    "care_goal" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    CONSTRAINT "adm_episode_of_care_pkey" PRIMARY KEY ("id")
);

-- DM-ADM-013：治疗周期 status_enum 由历史数字串迁移为语义 code（PLANNED/ACTIVE/…）
UPDATE adm_episode_of_care SET status_enum = 'PLANNED' WHERE status_enum = '1';
UPDATE adm_episode_of_care SET status_enum = 'ACTIVE' WHERE status_enum = '2';
UPDATE adm_episode_of_care SET status_enum = 'ON_HOLD' WHERE status_enum = '3';
UPDATE adm_episode_of_care SET status_enum = 'COMPLETED' WHERE status_enum = '4';
UPDATE adm_episode_of_care SET status_enum = 'CANCELLED' WHERE status_enum = '5';
UPDATE adm_episode_of_care SET status_enum = 'ENTERED_IN_ERROR' WHERE status_enum = '9';