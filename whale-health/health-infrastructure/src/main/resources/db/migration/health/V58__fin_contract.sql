-- fin_contract（建表 + 种子数据）
-- Flyway V58

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

-- seed: fin_contract
INSERT INTO "fin_contract" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "bus_no", "name", "category_enum", "status_enum", "active_flag", "org_id", "yb_flag", "display_order", "adm_vs", "py_str", "wb_str", "client_id", "client_secret", "username", "password", "cli_prv_key", "cli_pub_key", "server_pub_key", "scope", "grant_type", "fixmedins_code", "fixmedins_name")
VALUES
(3100000000000000001, 1, '2026-05-03 06:07:04.571+00', '2026-05-03 06:07:04.571+00', 1, 1, false, NULL, NULL, 0, 1, '自费', 1, 1, true, NULL, NULL, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
(3100000000000000002, 1, '2026-05-03 06:07:04.571+00', '2026-05-03 06:07:04.571+00', 1, 1, false, NULL, NULL, 0, 3, '省医保', 3, 1, true, NULL, NULL, 2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
(3100000000000000003, 1, '2026-05-03 06:07:04.571+00', '2026-05-03 06:07:04.571+00', 1, 1, false, NULL, NULL, 0, 4, '市医保', 4, 1, true, NULL, NULL, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
