-- cus_terminology_code_mapping（建表）
-- Flyway V40

CREATE TABLE IF NOT EXISTS "public"."cus_terminology_code_mapping" (
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
    "source_item_table" varchar(255) NOT NULL,
    "source_item_no" varchar(255) NOT NULL,
    "target_item_no" varchar(255) NOT NULL,
    "target_name" varchar(255),
    "active_flag" bool NOT NULL,
    CONSTRAINT "cus_terminology_code_mapping_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cus_terminology_code_mapping IS '自定义 terminology code mapping';

COMMENT ON COLUMN cus_terminology_code_mapping.id IS '主键';
COMMENT ON COLUMN cus_terminology_code_mapping.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_terminology_code_mapping.created_at IS '创建时间';
COMMENT ON COLUMN cus_terminology_code_mapping.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_terminology_code_mapping.created_by IS '创建人';
COMMENT ON COLUMN cus_terminology_code_mapping.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_terminology_code_mapping.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_terminology_code_mapping.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_terminology_code_mapping.deleted_by IS '删除人';
COMMENT ON COLUMN cus_terminology_code_mapping.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_terminology_code_mapping.source_item_table IS 'source item table';
COMMENT ON COLUMN cus_terminology_code_mapping.source_item_no IS 'source item no';
COMMENT ON COLUMN cus_terminology_code_mapping.target_item_no IS 'target item no';
COMMENT ON COLUMN cus_terminology_code_mapping.target_name IS 'target name';
COMMENT ON COLUMN cus_terminology_code_mapping.active_flag IS 'active flag';

