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
COMMENT ON TABLE adm_episode_of_care IS '管理域 episode of care';
COMMENT ON COLUMN adm_episode_of_care.id IS '主键';
COMMENT ON COLUMN adm_episode_of_care.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_episode_of_care.created_at IS '创建时间';
COMMENT ON COLUMN adm_episode_of_care.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_episode_of_care.created_by IS '创建人';
COMMENT ON COLUMN adm_episode_of_care.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_episode_of_care.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_episode_of_care.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_episode_of_care.deleted_by IS '删除人';
COMMENT ON COLUMN adm_episode_of_care.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_episode_of_care.patient_id IS 'patient 标识';
COMMENT ON COLUMN adm_episode_of_care.organization_id IS 'organization 标识';
COMMENT ON COLUMN adm_episode_of_care.bus_no IS 'bus 编号';
COMMENT ON COLUMN adm_episode_of_care.care_team_id IS 'careTeam 标识';
COMMENT ON COLUMN adm_episode_of_care.care_type IS 'care类型';
COMMENT ON COLUMN adm_episode_of_care.care_goal IS '照护目标';
COMMENT ON COLUMN adm_episode_of_care.status_enum IS 'status 枚举值';
COMMENT ON COLUMN adm_episode_of_care.active_flag IS 'active 标志位';
COMMENT ON COLUMN adm_episode_of_care.start_time IS 'start 时间';
COMMENT ON COLUMN adm_episode_of_care.end_time IS 'end 时间';
