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
COMMENT ON COLUMN cln_condition_definition.source_enum IS '来源枚举值';
COMMENT ON COLUMN cln_condition_definition.condition_code IS '病情编码';
COMMENT ON COLUMN cln_condition_definition.name IS '名称';
COMMENT ON COLUMN cln_condition_definition.py_str IS '拼音码';
COMMENT ON COLUMN cln_condition_definition.wb_str IS '五笔码';
COMMENT ON COLUMN cln_condition_definition.type_code IS '类型编码';
COMMENT ON COLUMN cln_condition_definition.description IS '描述';
COMMENT ON COLUMN cln_condition_definition.yb_flag IS '医保标志位';
COMMENT ON COLUMN cln_condition_definition.disease_code IS 'disease编码';
COMMENT ON COLUMN cln_condition_definition.yb_no IS '医保编号';
COMMENT ON COLUMN cln_condition_definition.yb_match_flag IS '医保match标志位';
COMMENT ON COLUMN cln_condition_definition.status_enum IS '状态枚举值';

-- seed: cln_condition_definition
INSERT INTO "cln_condition_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "source_enum", "condition_code", "name", "py_str", "wb_str", "type_code", "description", "yb_flag", "disease_code", "yb_no", "yb_match_flag", "status_enum")
VALUES
('317932056501817344', '1', '2026-05-27 07:48:29.162+00', '2026-05-27 07:48:29.162+00', '1', '1', false, NULL, NULL, 0, 'general_dx', 'CDF00000003', '感冒', 'GM', 'DJ', '1', NULL, NULL, NULL, '1.002', NULL, 'ACTIVE')
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cln_condition_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "source_enum", "condition_code", "name", "py_str", "wb_str", "type_code", "description", "yb_flag", "disease_code", "yb_no", "yb_match_flag", "status_enum") VALUES ('326944736955338752', '1', '2026-06-21 04:41:39.629+00', '2026-06-21 04:41:39.629+00', '1', '1', false, NULL, NULL, 0, 'tcm_syndrome', 'CDF00000001', '气血两虚证', 'QXLXZ', 'RTGHY', '3', NULL, NULL, NULL, NULL, NULL, 'ACTIVE') ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cln_condition_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "source_enum", "condition_code", "name", "py_str", "wb_str", "type_code", "description", "yb_flag", "disease_code", "yb_no", "yb_match_flag", "status_enum") VALUES ('326944883521097728', '1', '2026-06-21 04:42:14.573+00', '2026-06-21 04:42:14.573+00', '1', '1', false, NULL, NULL, 0, 'tcm_dx', 'CDF00000002', '眩晕', 'XY', 'HJ', '2', NULL, NULL, NULL, NULL, NULL, 'ACTIVE') ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cln_condition_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "source_enum", "condition_code", "name", "py_str", "wb_str", "type_code", "description", "yb_flag", "disease_code", "yb_no", "yb_match_flag", "status_enum") VALUES ('327720266247442432', '1', '2026-06-23 08:03:20.221+00', '2026-06-23 08:03:20.221+00', '1', '1', false, NULL, NULL, 0, 'general_dx', 'CDF00000003', '头疼', 'TT', 'UU', '1', NULL, NULL, NULL, NULL, NULL, 'ACTIVE') ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cln_condition_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "source_enum", "condition_code", "name", "py_str", "wb_str", "type_code", "description", "yb_flag", "disease_code", "yb_no", "yb_match_flag", "status_enum") VALUES ('327720343745597440', '1', '2026-06-23 08:03:38.698+00', '2026-06-23 08:03:38.698+00', '1', '1', false, NULL, NULL, 0, 'general_dx', 'CDF00000004', '高血压', 'GXY', 'YTD', '1', NULL, NULL, NULL, NULL, NULL, 'ACTIVE') ON CONFLICT ("id") DO NOTHING;
