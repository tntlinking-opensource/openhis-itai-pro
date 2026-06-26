-- adm_specimen_definition（建表）
-- Flyway V74

CREATE TABLE IF NOT EXISTS "public"."adm_specimen_definition" (
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
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "type_enum" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    CONSTRAINT "adm_specimen_definition_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_adm_specimen_definition_bus_no"
    ON "public"."adm_specimen_definition" ("bus_no")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_adm_specimen_definition_type_enum"
    ON "public"."adm_specimen_definition" ("type_enum");

CREATE INDEX IF NOT EXISTS "idx_adm_specimen_definition_status_enum"
    ON "public"."adm_specimen_definition" ("status_enum");



-- ----- seed sync from DB (adm_specimen_definition, 1 rows) -----
COMMENT ON TABLE adm_specimen_definition IS '管理域 specimen definition';
COMMENT ON COLUMN adm_specimen_definition.id IS '主键';
COMMENT ON COLUMN adm_specimen_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_specimen_definition.created_at IS '创建时间';
COMMENT ON COLUMN adm_specimen_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_specimen_definition.created_by IS '创建人';
COMMENT ON COLUMN adm_specimen_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_specimen_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_specimen_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_specimen_definition.deleted_by IS '删除人';
COMMENT ON COLUMN adm_specimen_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_specimen_definition.bus_no IS '样本定义聚合根（{ adm_specimen_definition}，DM-ADM-014）';
COMMENT ON COLUMN adm_specimen_definition.name IS '面向临床与维护页的显示名称';
COMMENT ON COLUMN adm_specimen_definition.py_str IS '拼音检索码';
COMMENT ON COLUMN adm_specimen_definition.wb_str IS '五笔检索码';
COMMENT ON COLUMN adm_specimen_definition.type_enum IS '标本种类（存 { SpecimenType#getCode()}）';
COMMENT ON COLUMN adm_specimen_definition.status_enum IS '发布状态（存 { PublicationStatus#getCode()}）';

INSERT INTO "adm_specimen_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "bus_no", "name", "py_str", "wb_str", "type_enum", "status_enum")
VALUES ('328090253175296000', '1', '2026-06-24 08:33:31.979+00', '2026-06-24 08:33:31.979+00', '1', '1', false, NULL, NULL, 0, 'SPD0001', '血', 'X', 'T', 'BLOOD', 'ACTIVE')
ON CONFLICT ("id") DO NOTHING;
