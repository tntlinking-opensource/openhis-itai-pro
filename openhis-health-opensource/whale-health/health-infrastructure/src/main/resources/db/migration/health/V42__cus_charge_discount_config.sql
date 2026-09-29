-- V42：收费折扣配置（DM-CUS-016 / AS-BAS-CFG-002）
-- 大类/单项由 category_code↔product_id 互斥推断；固定率/款员定由 discount_rate 有无推断；无 scope/mode 列。

CREATE TABLE IF NOT EXISTS "public"."cus_charge_discount_config" (
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
    "product_table" varchar(255) NOT NULL,
    "category_code" varchar(255),
    "product_id" int8,
    "discount_rate" numeric(8, 4),
    "status_enum" varchar(64) NOT NULL,
    "remark" varchar(512),
    "org_id" int8,
    CONSTRAINT "cus_charge_discount_config_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cus_charge_discount_config IS '收费折扣配置：三大目录可打折大类/单项；能力门控统计 ACTIVE 未删行';
COMMENT ON COLUMN cus_charge_discount_config.product_table IS '三大目录表名：med_medication_definition / wkf_activity_definition / adm_device_definition';
COMMENT ON COLUMN cus_charge_discount_config.category_code IS '大类行必填；单项行必须空；码表随 product_table';
COMMENT ON COLUMN cus_charge_discount_config.product_id IS '单项行必填；大类行必须空';
COMMENT ON COLUMN cus_charge_discount_config.discount_rate IS '折后占原价比例 (0,1]；有值=固定率；空=款员定率';
COMMENT ON COLUMN cus_charge_discount_config.status_enum IS 'ACTIVE / INACTIVE；仅 ACTIVE 参与命中与能力门控';
COMMENT ON COLUMN cus_charge_discount_config.remark IS '配置说明';
COMMENT ON COLUMN cus_charge_discount_config.org_id IS '预留院区；首版可空=租户全院';

-- 大类唯一：未删且 product_id 空
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cus_cdc_category_active"
    ON "public"."cus_charge_discount_config" ("product_table", "category_code")
    WHERE "is_deleted" = false AND "product_id" IS NULL AND "category_code" IS NOT NULL;

-- 单项唯一：未删且 category_code 空
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cus_cdc_product_active"
    ON "public"."cus_charge_discount_config" ("product_table", "product_id")
    WHERE "is_deleted" = false AND "category_code" IS NULL AND "product_id" IS NOT NULL;

CREATE INDEX IF NOT EXISTS "idx_cus_cdc_status_active"
    ON "public"."cus_charge_discount_config" ("status_enum")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_cus_cdc_product_table"
    ON "public"."cus_charge_discount_config" ("product_table")
    WHERE "is_deleted" = false;
