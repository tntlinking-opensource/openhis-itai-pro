-- adm_episode_of_care_encounter（建表）
-- Flyway V12

CREATE TABLE IF NOT EXISTS "public"."adm_episode_of_care_encounter" (
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
    "episode_of_care_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "join_time" timestamptz(6) NOT NULL,
    "leave_time" timestamptz(6),
    CONSTRAINT "adm_episode_of_care_encounter_pkey" PRIMARY KEY ("id")
);