-- cus_order_template（建表）
-- Flyway V34

CREATE TABLE IF NOT EXISTS "public"."cus_order_template" (
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
    "object_id" int8 NOT NULL,
    "binding_enum" varchar(255) NOT NULL,
    "scene_enum" varchar(255) NOT NULL,
    "template_code" varchar(255),
    "name" varchar(255) NOT NULL,
    "description" varchar(255),
    "sort_weight" int4,
    "status_enum" varchar(255) NOT NULL,
    "remark" varchar(255),
    "template_kind_enum" varchar(255) NOT NULL DEFAULT 'GENERAL',
    "default_chinese_herbs_dose_quantity" numeric(18, 2),
    "default_suffering_flag" bool,
    "template_source_enum" varchar(255) NOT NULL DEFAULT 'MAINTAINED',
    CONSTRAINT "cus_order_template_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_cus_order_template_source_list"
    ON "public"."cus_order_template" ("tenant_id", "object_id", "binding_enum", "template_source_enum", "template_kind_enum", "status_enum")
    WHERE "is_deleted" = false;
COMMENT ON TABLE cus_order_template IS '自定义 order template';
COMMENT ON COLUMN cus_order_template.id IS '主键';
COMMENT ON COLUMN cus_order_template.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_order_template.created_at IS '创建时间';
COMMENT ON COLUMN cus_order_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_order_template.created_by IS '创建人';
COMMENT ON COLUMN cus_order_template.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_order_template.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_order_template.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_order_template.deleted_by IS '删除人';
COMMENT ON COLUMN cus_order_template.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_order_template.object_id IS '组套模板头聚合根';
COMMENT ON COLUMN cus_order_template.binding_enum IS '绑定类型（个人/科室/全院）';
COMMENT ON COLUMN cus_order_template.scene_enum IS '适用场景（门诊/住院/通用）';
COMMENT ON COLUMN cus_order_template.template_code IS '模板编码（可空，非空时建议唯一）';
COMMENT ON COLUMN cus_order_template.name IS '模板名称';
COMMENT ON COLUMN cus_order_template.description IS '描述';
COMMENT ON COLUMN cus_order_template.sort_weight IS '列表排序权重（同层级内升序；数值越小越靠前）';
COMMENT ON COLUMN cus_order_template.status_enum IS '状态（草稿/启用/停用）';
COMMENT ON COLUMN cus_order_template.remark IS '备注';
COMMENT ON COLUMN cus_order_template.template_kind_enum IS '组套种类（GENERAL/TCM/PRICING）';
COMMENT ON COLUMN cus_order_template.default_chinese_herbs_dose_quantity IS 'TCM 组套默认中药付数';
COMMENT ON COLUMN cus_order_template.default_suffering_flag IS 'TCM 组套默认代煎标志';
COMMENT ON COLUMN cus_order_template.template_source_enum IS '模板来源（MAINTAINED=维护组套，FROM_ADVICE=医嘱模板/开嘱另存）';

-- ----- seed sync from DB (cus_order_template, 4 rows) -----

INSERT INTO "cus_order_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "object_id", "binding_enum", "scene_enum", "template_code", "name", "description", "sort_weight", "status_enum", "remark", "template_kind_enum", "default_chinese_herbs_dose_quantity", "default_suffering_flag", "template_source_enum") VALUES ('327719620186214400', '1', '2026-06-23 08:00:46.188+00', '2026-06-23 08:00:46.237+00', '1', '1', false, NULL, NULL, 0, '322663105141149696', 'HOSPITAL', 'OUTPATIENT', NULL, '感冒组套', NULL, 0, 'ACTIVE', NULL, 'GENERAL', NULL, NULL, 'MAINTAINED') ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "object_id", "binding_enum", "scene_enum", "template_code", "name", "description", "sort_weight", "status_enum", "remark", "template_kind_enum", "default_chinese_herbs_dose_quantity", "default_suffering_flag", "template_source_enum") VALUES ('327719980850221056', '1', '2026-06-23 08:02:12.177+00', '2026-06-23 08:02:12.216+00', '1', '1', false, NULL, NULL, 0, '322663105141149696', 'AGREEMENT', 'OUTPATIENT', NULL, '内补黄芪汤', NULL, 0, 'ACTIVE', NULL, 'TCM', '1.00', false, 'MAINTAINED') ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "object_id", "binding_enum", "scene_enum", "template_code", "name", "description", "sort_weight", "status_enum", "remark", "template_kind_enum", "default_chinese_herbs_dose_quantity", "default_suffering_flag", "template_source_enum") VALUES ('327720163520548864', '1', '2026-06-23 08:02:55.729+00', '2026-06-23 08:02:55.761+00', '1', '1', false, NULL, NULL, 0, '322663105141149696', 'AGREEMENT', 'OUTPATIENT', NULL, '四君子汤', NULL, 0, 'ACTIVE', NULL, 'TCM', '2.00', false, 'MAINTAINED') ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "object_id", "binding_enum", "scene_enum", "template_code", "name", "description", "sort_weight", "status_enum", "remark", "template_kind_enum", "default_chinese_herbs_dose_quantity", "default_suffering_flag", "template_source_enum") VALUES ('328101749022265344', '1', '2026-06-24 09:19:12.802+00', '2026-06-24 09:19:12.811+00', '322963602154721280', '322963602154721280', false, NULL, NULL, 0, '322663218269917184', 'DEPARTMENT', 'OUTPATIENT', NULL, '内科感冒', NULL, 0, 'ACTIVE', NULL, 'GENERAL', NULL, NULL, 'FROM_ADVICE') ON CONFLICT ("id") DO NOTHING;
