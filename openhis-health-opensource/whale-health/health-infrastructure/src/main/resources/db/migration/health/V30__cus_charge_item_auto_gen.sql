-- cus_charge_item_auto_gen（建表）
-- Flyway V30

CREATE TABLE IF NOT EXISTS "public"."cus_charge_item_auto_gen" (
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
    "rule_name" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "trigger_source_table" varchar(255) NOT NULL,
    "trigger_source_id" int8 NOT NULL,
    "charge_definition_id" int8 NOT NULL,
    "performing_org_id" int8,
    "quantity_value" numeric(18,2) NOT NULL,
    "priority" int4 NOT NULL,
    "remark" varchar(255),
    "encounter_id" int8 NOT NULL,
    CONSTRAINT "cus_charge_item_auto_gen_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS idx_cus_charge_item_auto_gen_encounter_id
    ON cus_charge_item_auto_gen (encounter_id)
    WHERE is_deleted = false;
COMMENT ON TABLE cus_charge_item_auto_gen IS '自定义 charge item auto gen';
COMMENT ON COLUMN cus_charge_item_auto_gen.id IS '主键';
COMMENT ON COLUMN cus_charge_item_auto_gen.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_charge_item_auto_gen.created_at IS '创建时间';
COMMENT ON COLUMN cus_charge_item_auto_gen.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_charge_item_auto_gen.created_by IS '创建人';
COMMENT ON COLUMN cus_charge_item_auto_gen.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_charge_item_auto_gen.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_charge_item_auto_gen.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_charge_item_auto_gen.deleted_by IS '删除人';
COMMENT ON COLUMN cus_charge_item_auto_gen.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_charge_item_auto_gen.rule_name IS '收费明细自动生成规则聚合根';
COMMENT ON COLUMN cus_charge_item_auto_gen.status_enum IS '状态枚举（发布状态）';
COMMENT ON COLUMN cus_charge_item_auto_gen.trigger_source_table IS '触发来源表名（落库字符串须与来源聚合主表表名一致）';
COMMENT ON COLUMN cus_charge_item_auto_gen.trigger_source_id IS '触发来源主键';
COMMENT ON COLUMN cus_charge_item_auto_gen.charge_definition_id IS '收费项定价标识（adm_charge_item_definition.id）';
COMMENT ON COLUMN cus_charge_item_auto_gen.performing_org_id IS '执行科室标识（为空表示全局规则）';
COMMENT ON COLUMN cus_charge_item_auto_gen.quantity_value IS '默认数量（大于 0）';
COMMENT ON COLUMN cus_charge_item_auto_gen.priority IS '优先级（数值越小越靠前）';
COMMENT ON COLUMN cus_charge_item_auto_gen.remark IS '备注';
COMMENT ON COLUMN cus_charge_item_auto_gen.encounter_id IS '住院就诊标识（adm_encounter.id）';
