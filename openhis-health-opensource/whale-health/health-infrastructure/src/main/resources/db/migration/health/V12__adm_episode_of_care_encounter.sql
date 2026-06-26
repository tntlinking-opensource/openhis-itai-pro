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
COMMENT ON TABLE adm_episode_of_care_encounter IS '管理域 episode of care encounter';
COMMENT ON COLUMN adm_episode_of_care_encounter.id IS '主键';
COMMENT ON COLUMN adm_episode_of_care_encounter.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_episode_of_care_encounter.created_at IS '创建时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.created_by IS '创建人';
COMMENT ON COLUMN adm_episode_of_care_encounter.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_episode_of_care_encounter.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_episode_of_care_encounter.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.deleted_by IS '删除人';
COMMENT ON COLUMN adm_episode_of_care_encounter.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_episode_of_care_encounter.episode_of_care_id IS '疗程-就诊关联实体：记录某次就诊加入/离开疗程的时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.encounter_id IS '就诊主键';
COMMENT ON COLUMN adm_episode_of_care_encounter.join_time IS '纳入疗程时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.leave_time IS '离开疗程时间（未离开时为空）';
