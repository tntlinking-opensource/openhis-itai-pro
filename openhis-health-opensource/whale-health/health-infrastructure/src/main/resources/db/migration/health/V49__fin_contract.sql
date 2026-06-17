-- fin_contract（建表 + 种子数据）
-- Flyway V49

CREATE TABLE IF NOT EXISTS "public"."fin_contract" (
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
    "category_enum" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "org_id" int8,
    "yb_flag" bool,
    "display_order" int4,
    "adm_vs" varchar(255),
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "client_id" varchar(255),
    "client_secret" varchar(255),
    "username" varchar(255),
    "password" varchar(255),
    "cli_prv_key" varchar(255),
    "cli_pub_key" varchar(255),
    "server_pub_key" varchar(255),
    "scope" varchar(255),
    "grant_type" varchar(255),
    "fixmedins_code" varchar(255),
    "fixmedins_name" varchar(255),
    CONSTRAINT "fin_contract_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE fin_contract IS '财务 contract';

COMMENT ON COLUMN fin_contract.id IS '主键';
COMMENT ON COLUMN fin_contract.tenant_id IS '租户标识';
COMMENT ON COLUMN fin_contract.created_at IS '创建时间';
COMMENT ON COLUMN fin_contract.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_contract.created_by IS '创建人';
COMMENT ON COLUMN fin_contract.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_contract.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_contract.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_contract.deleted_by IS '删除人';
COMMENT ON COLUMN fin_contract.version IS '乐观锁版本号';
COMMENT ON COLUMN fin_contract.bus_no IS '费用性质聚合根';
COMMENT ON COLUMN fin_contract.name IS 'name';
COMMENT ON COLUMN fin_contract.category_enum IS 'category enum';
COMMENT ON COLUMN fin_contract.status_enum IS 'status enum';
COMMENT ON COLUMN fin_contract.active_flag IS 'active flag';
COMMENT ON COLUMN fin_contract.org_id IS 'org id';
COMMENT ON COLUMN fin_contract.yb_flag IS 'yb flag';
COMMENT ON COLUMN fin_contract.display_order IS 'display order';
COMMENT ON COLUMN fin_contract.adm_vs IS '院内/历史字段；CHS 统筹区划读 chs-contract.{bus_no}.adm_vs';
COMMENT ON COLUMN fin_contract.py_str IS 'py str';
COMMENT ON COLUMN fin_contract.wb_str IS 'wb str';
COMMENT ON COLUMN fin_contract.client_id IS 'client id';
COMMENT ON COLUMN fin_contract.client_secret IS 'client secret';
COMMENT ON COLUMN fin_contract.username IS 'username';
COMMENT ON COLUMN fin_contract.password IS 'password';
COMMENT ON COLUMN fin_contract.cli_prv_key IS 'cli prv key';
COMMENT ON COLUMN fin_contract.cli_pub_key IS 'cli pub key';
COMMENT ON COLUMN fin_contract.server_pub_key IS 'server pub key';
COMMENT ON COLUMN fin_contract.scope IS 'scope';
COMMENT ON COLUMN fin_contract.grant_type IS 'grant type';
COMMENT ON COLUMN fin_contract.fixmedins_code IS '院内/历史字段；CHS 定点机构编码读 chs-contract.{bus_no}.fixmedins_code';
COMMENT ON COLUMN fin_contract.fixmedins_name IS 'fixmedins name';

-- seed: fin_contract
INSERT INTO "fin_contract" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "bus_no", "name", "category_enum", "status_enum", "active_flag", "org_id", "yb_flag", "display_order", "adm_vs", "py_str", "wb_str", "client_id", "client_secret", "username", "password", "cli_prv_key", "cli_pub_key", "server_pub_key", "scope", "grant_type", "fixmedins_code", "fixmedins_name")
VALUES
('3100000000000000001', '1', '2026-05-03 06:07:04.571+00', '2026-05-03 06:07:04.571+00', '1', '1', false, NULL, NULL, 0, '1', '自费', '1', '1', true, NULL, NULL, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('3100000000000000002', '1', '2026-05-03 06:07:04.571+00', '2026-05-03 06:07:04.571+00', '1', '1', false, NULL, NULL, 0, '3', '省医保', '3', '1', true, NULL, NULL, 2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('3100000000000000003', '1', '2026-05-03 06:07:04.571+00', '2026-05-03 06:07:04.571+00', '1', '1', false, NULL, NULL, 0, '4', '市医保', '4', '1', true, NULL, NULL, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
