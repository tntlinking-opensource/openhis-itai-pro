-- cln_condition_definition（建表 + 种子数据）
-- Flyway V26

CREATE TABLE IF NOT EXISTS "public"."cln_condition_definition" (
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
    "source_enum" varchar(255) NOT NULL,
    "condition_code" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "type_code" varchar(255),
    "description" varchar(255),
    "yb_flag" bool,
    "disease_code" varchar(255),
    "yb_no" varchar(255),
    "yb_match_flag" bool,
    "status_enum" varchar(255) NOT NULL,
    CONSTRAINT "cln_condition_definition_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cln_condition_definition IS '临床 condition definition';

COMMENT ON COLUMN cln_condition_definition.id IS '主键';
COMMENT ON COLUMN cln_condition_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_condition_definition.created_at IS '创建时间';
COMMENT ON COLUMN cln_condition_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_condition_definition.created_by IS '创建人';
COMMENT ON COLUMN cln_condition_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_condition_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_condition_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_condition_definition.deleted_by IS '删除人';
COMMENT ON COLUMN cln_condition_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_condition_definition.source_enum IS 'source enum';
COMMENT ON COLUMN cln_condition_definition.condition_code IS 'condition code';
COMMENT ON COLUMN cln_condition_definition.name IS 'name';
COMMENT ON COLUMN cln_condition_definition.py_str IS 'py str';
COMMENT ON COLUMN cln_condition_definition.wb_str IS 'wb str';
COMMENT ON COLUMN cln_condition_definition.type_code IS 'type code';
COMMENT ON COLUMN cln_condition_definition.description IS 'description';
COMMENT ON COLUMN cln_condition_definition.yb_flag IS 'yb flag';
COMMENT ON COLUMN cln_condition_definition.disease_code IS 'disease code';
COMMENT ON COLUMN cln_condition_definition.yb_no IS 'yb no';
COMMENT ON COLUMN cln_condition_definition.yb_match_flag IS 'yb match flag';
COMMENT ON COLUMN cln_condition_definition.status_enum IS 'status enum';

-- seed: cln_condition_definition
INSERT INTO "cln_condition_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "source_enum", "condition_code", "name", "py_str", "wb_str", "type_code", "description", "yb_flag", "disease_code", "yb_no", "yb_match_flag", "status_enum")
VALUES
('317932056501817344', '1', '2026-05-27 07:48:29.162+00', '2026-05-27 07:48:29.162+00', '1', '1', false, NULL, NULL, 0, 'general_dx', 'CDF00000003', '感冒', 'GM', 'DJ', '1', NULL, NULL, NULL, '1.002', NULL, 'ACTIVE')
ON CONFLICT ("id") DO NOTHING;
