-- cus_service_device_template（建表）
-- Flyway V37

CREATE TABLE IF NOT EXISTS "public"."cus_service_device_template" (
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
    "range_code" varchar(255) NOT NULL,
    "type_code" varchar(255) NOT NULL,
    "item_table" varchar(255) NOT NULL,
    "item_id" varchar(255) NOT NULL,
    "dev_act_table" varchar(255) NOT NULL,
    "dev_act_id" int8 NOT NULL,
    "quantity" numeric(18,2) NOT NULL,
    "unit_code" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    CONSTRAINT "cus_service_device_template_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_service_device_template IS '自定义 service device template';
COMMENT ON COLUMN cus_service_device_template.id IS '主键';
COMMENT ON COLUMN cus_service_device_template.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_service_device_template.created_at IS '创建时间';
COMMENT ON COLUMN cus_service_device_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_service_device_template.created_by IS '创建人';
COMMENT ON COLUMN cus_service_device_template.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_service_device_template.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_service_device_template.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_service_device_template.deleted_by IS '删除人';
COMMENT ON COLUMN cus_service_device_template.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_service_device_template.range_code IS '诊疗耗材绑定聚合根';
COMMENT ON COLUMN cus_service_device_template.type_code IS '绑定类型编码（1 用法/2 诊疗/3 号源）';
COMMENT ON COLUMN cus_service_device_template.item_table IS '来源表名';
COMMENT ON COLUMN cus_service_device_template.item_id IS '来源项标识（跨表主键类型不一，持久化为 String）';
COMMENT ON COLUMN cus_service_device_template.dev_act_table IS '目标表名（诊疗定义表或耗材定义表）';
COMMENT ON COLUMN cus_service_device_template.dev_act_id IS '目标项标识（诊疗或耗材定义主键）';
COMMENT ON COLUMN cus_service_device_template.quantity IS '绑定数量（> 0）';
COMMENT ON COLUMN cus_service_device_template.unit_code IS '单位编码';
COMMENT ON COLUMN cus_service_device_template.status_enum IS '发布状态（active/retired）';

-- seed: cus_service_device_template
INSERT INTO "cus_service_device_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "range_code", "type_code", "item_table", "item_id", "dev_act_table", "dev_act_id", "quantity", "unit_code", "status_enum")
VALUES
('323023695509983232', '1', '2026-06-10 09:00:50.499+00', '2026-06-10 09:00:50.499+00', '1', '1', false, NULL, NULL, 0, 'HOSPITAL', 'USAGE', 'ter_concept', '405', 'wkf_activity_definition', '323001764504670208', '1.00', '6', 'ACTIVE'),
('323023732398886912', '1', '2026-06-10 09:00:59.294+00', '2026-06-10 09:00:59.294+00', '1', '1', false, NULL, NULL, 0, 'HOSPITAL', 'USAGE', 'ter_concept', '405', 'adm_device_definition', '322999285465485312', '1.00', '9', 'ACTIVE')
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_service_device_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "range_code", "type_code", "item_table", "item_id", "dev_act_table", "dev_act_id", "quantity", "unit_code", "status_enum") VALUES ('327692469915357184', '1', '2026-06-23 06:12:53.059+00', '2026-06-23 06:12:53.059+00', '1', '1', false, NULL, NULL, 0, 'HOSPITAL', 'USAGE', 'ter_concept', '404', 'wkf_activity_definition', '323001764504670208', '1.00', '6', 'ACTIVE') ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_service_device_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "range_code", "type_code", "item_table", "item_id", "dev_act_table", "dev_act_id", "quantity", "unit_code", "status_enum") VALUES ('327692504744857600', '1', '2026-06-23 06:13:01.363+00', '2026-06-23 06:13:01.363+00', '1', '1', false, NULL, NULL, 0, 'HOSPITAL', 'USAGE', 'ter_concept', '404', 'adm_device_definition', '323000109906268160', '1.00', '9', 'ACTIVE') ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_service_device_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "range_code", "type_code", "item_table", "item_id", "dev_act_table", "dev_act_id", "quantity", "unit_code", "status_enum") VALUES ('327692607735992320', '1', '2026-06-23 06:13:25.918+00', '2026-06-23 06:13:25.918+00', '1', '1', false, NULL, NULL, 0, 'HOSPITAL', 'ACTIVITY', 'wkf_activity_definition', '323001764504670208', 'adm_device_definition', '322999285465485312', '1.00', '9', 'ACTIVE') ON CONFLICT ("id") DO NOTHING;

-- ----- seed sync from DB (cus_service_device_template, 1 rows) -----

INSERT INTO "cus_service_device_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "range_code", "type_code", "item_table", "item_id", "dev_act_table", "dev_act_id", "quantity", "unit_code", "status_enum")
VALUES ('328094684596932608', '1', '2026-06-24 08:51:08.512+00', '2026-06-24 08:51:08.512+00', '1', '1', false, NULL, NULL, 0, 'HOSPITAL', 'ACTIVITY', 'wkf_activity_definition', '323004165299113984', 'adm_device_definition', '328091173040689152', '1.00', '9', 'ACTIVE')
ON CONFLICT ("id") DO NOTHING;
