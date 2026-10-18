-- Miniapp ModularFlyway V1 · map_* 身份层 DDL（DM-MAP-AUTH）
-- history: flyway_schema_history_miniapp
-- switch: zxplat.modules.miniapp.enabled

-- ========== map_wechat_account ==========
CREATE TABLE IF NOT EXISTS "public"."map_wechat_account" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "app_id" varchar(64) NOT NULL,
    "openid" varchar(64) NOT NULL,
    "union_id" varchar(64),
    "status_enum" varchar(32) NOT NULL DEFAULT 'active',
    "last_login_at" timestamptz(6),
    CONSTRAINT "map_wechat_account_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_map_wechat_account_openid"
    ON "public"."map_wechat_account" ("tenant_id", "app_id", "openid")
    WHERE "is_deleted" = false;
CREATE UNIQUE INDEX IF NOT EXISTS "uk_map_wechat_account_union"
    ON "public"."map_wechat_account" ("tenant_id", "app_id", "union_id")
    WHERE "union_id" IS NOT NULL AND "is_deleted" = false;
COMMENT ON TABLE map_wechat_account IS '小程序微信账号（openid 唯一落点）';

-- ========== map_session ==========
CREATE TABLE IF NOT EXISTS "public"."map_session" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "wechat_account_id" int8 NOT NULL,
    "token_hash" varchar(128) NOT NULL,
    "current_patient_id" int8,
    "current_practitioner_id" int8,
    "client_type_enum" varchar(32) NOT NULL DEFAULT 'miniapp',
    "expires_at" timestamptz(6) NOT NULL,
    "revoked_at" timestamptz(6),
    CONSTRAINT "map_session_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_map_session_token_hash"
    ON "public"."map_session" ("token_hash")
    WHERE "revoked_at" IS NULL AND "is_deleted" = false;
CREATE INDEX IF NOT EXISTS "idx_map_session_account_expires"
    ON "public"."map_session" ("wechat_account_id", "expires_at");
COMMENT ON TABLE map_session IS '小程序登录会话';

-- ========== map_patient_binding ==========
CREATE TABLE IF NOT EXISTS "public"."map_patient_binding" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "wechat_account_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "bind_channel_enum" varchar(32) NOT NULL,
    "campus_ref_type" varchar(32),
    "campus_ref_id" int8,
    "campus_biz_no" varchar(64),
    "is_default" bool NOT NULL DEFAULT false,
    "bound_at" timestamptz(6) NOT NULL,
    "unbound_at" timestamptz(6),
    CONSTRAINT "map_patient_binding_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_map_patient_binding_active"
    ON "public"."map_patient_binding" ("tenant_id", "wechat_account_id", "patient_id")
    WHERE "unbound_at" IS NULL AND "is_deleted" = false;
CREATE INDEX IF NOT EXISTS "idx_map_patient_binding_account"
    ON "public"."map_patient_binding" ("wechat_account_id")
    WHERE "unbound_at" IS NULL AND "is_deleted" = false;
COMMENT ON TABLE map_patient_binding IS '微信账号 ↔ 就诊人绑定';

-- ========== map_practitioner_binding ==========
CREATE TABLE IF NOT EXISTS "public"."map_practitioner_binding" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "wechat_account_id" int8 NOT NULL,
    "practitioner_id" int8 NOT NULL,
    "bind_channel_enum" varchar(32) NOT NULL,
    "employee_no" varchar(64),
    "is_default" bool NOT NULL DEFAULT false,
    "bound_at" timestamptz(6) NOT NULL,
    "unbound_at" timestamptz(6),
    CONSTRAINT "map_practitioner_binding_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_map_practitioner_binding_active"
    ON "public"."map_practitioner_binding" ("tenant_id", "wechat_account_id", "practitioner_id")
    WHERE "unbound_at" IS NULL AND "is_deleted" = false;
CREATE INDEX IF NOT EXISTS "idx_map_practitioner_binding_account"
    ON "public"."map_practitioner_binding" ("wechat_account_id")
    WHERE "unbound_at" IS NULL AND "is_deleted" = false;
COMMENT ON TABLE map_practitioner_binding IS '微信账号 ↔ 医疗参与者绑定';
