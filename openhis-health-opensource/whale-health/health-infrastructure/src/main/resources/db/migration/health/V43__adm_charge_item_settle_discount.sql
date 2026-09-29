-- DM-ADM-011：收费/结算时刻折扣快照列（能力门控开启且本行参与打折时写入）
ALTER TABLE "public"."adm_charge_item"
    ADD COLUMN IF NOT EXISTS "settle_discount_rate" numeric(8, 4);

ALTER TABLE "public"."adm_charge_item"
    ADD COLUMN IF NOT EXISTS "settle_discount_source_enum" varchar(32);

COMMENT ON COLUMN adm_charge_item.settle_discount_rate IS '结算折扣率快照：折后占原价 (0,1]；未打折为空';
COMMENT ON COLUMN adm_charge_item.settle_discount_source_enum IS '结算折扣来源：NONE/CONFIG/CASHIER；未参与打折为空或 NONE';
