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
COMMENT ON COLUMN dia_body_structure.bus_no IS 'bus no';
COMMENT ON COLUMN dia_body_structure.name IS 'name';
COMMENT ON COLUMN dia_body_structure.status_enum IS 'status enum';
COMMENT ON COLUMN dia_body_structure.py_str IS 'py str';
COMMENT ON COLUMN dia_body_structure.wb_str IS 'wb str';
COMMENT ON COLUMN dia_body_structure.parent_id IS 'parent id';

