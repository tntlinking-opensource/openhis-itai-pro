-- dia_body_structure（建表）
-- Flyway V42

CREATE TABLE IF NOT EXISTS "public"."dia_body_structure" (
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
    "bus_no" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "parent_id" int8,
    CONSTRAINT "dia_body_structure_pkey" PRIMARY KEY ("id")
);



-- ----- seed sync from DB (dia_body_structure, 3 rows) -----
COMMENT ON TABLE dia_body_structure IS '诊断 body structure';
COMMENT ON COLUMN dia_body_structure.id IS '主键';
COMMENT ON COLUMN dia_body_structure.tenant_id IS '租户标识';
COMMENT ON COLUMN dia_body_structure.created_at IS '创建时间';
COMMENT ON COLUMN dia_body_structure.updated_at IS '最后更新时间';
COMMENT ON COLUMN dia_body_structure.created_by IS '创建人';
COMMENT ON COLUMN dia_body_structure.updated_by IS '最后更新人';
COMMENT ON COLUMN dia_body_structure.is_deleted IS '软删标记';
COMMENT ON COLUMN dia_body_structure.deleted_at IS '删除时间';
COMMENT ON COLUMN dia_body_structure.deleted_by IS '删除人';
COMMENT ON COLUMN dia_body_structure.version IS '乐观锁版本号';
COMMENT ON COLUMN dia_body_structure.bus_no IS '业务编号';
COMMENT ON COLUMN dia_body_structure.name IS '名称';
COMMENT ON COLUMN dia_body_structure.status_enum IS '状态枚举值';
COMMENT ON COLUMN dia_body_structure.py_str IS '拼音码';
COMMENT ON COLUMN dia_body_structure.wb_str IS '五笔码';
COMMENT ON COLUMN dia_body_structure.parent_id IS 'parent标识';

INSERT INTO "dia_body_structure" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "bus_no", "name", "status_enum", "py_str", "wb_str", "parent_id")
VALUES ('328090067094999040', '1', '2026-06-24 08:32:47.614+00', '2026-06-24 08:32:47.614+00', '1', '1', false, NULL, NULL, 0, 'BS0001', '头', 'ACTIVE', '头', '头', NULL)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "dia_body_structure" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "bus_no", "name", "status_enum", "py_str", "wb_str", "parent_id")
VALUES ('328098821287972864', '1', '2026-06-24 09:07:34.776+00', '2026-06-24 09:07:34.776+00', '1', '1', false, NULL, NULL, 0, 'BS0002', '下肢', 'ACTIVE', '下肢', '下肢', NULL)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "dia_body_structure" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "bus_no", "name", "status_enum", "py_str", "wb_str", "parent_id")
VALUES ('328098846499934208', '1', '2026-06-24 09:07:40.787+00', '2026-06-24 09:07:40.787+00', '1', '1', false, NULL, NULL, 0, 'BS0003', '小腿关节', 'ACTIVE', '小腿关节', '小腿关节', '328098821287972864')
ON CONFLICT ("id") DO NOTHING;
