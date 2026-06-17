-- 医保协同单（IP_SETTLE / OP_SETTLE 等异步 Worker）
-- Flyway V76

CREATE TABLE IF NOT EXISTS "public"."yb_coordination" (
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
    "biz_type" varchar(32) NOT NULL,
    "scheme_code" varchar(32) NOT NULL DEFAULT 'CHS_NATIONAL_JILIN',
    "state" varchar(32) NOT NULL,
    "biz_id" int8 NOT NULL,
    "account_id" int8,
    "contract_id" int8 NOT NULL,
    "parent_id" int8,
    "payment_id" int8,
    "payment_no" varchar(64),
    "encounter_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "msgid" varchar(64) NOT NULL,
    "infno" varchar(16),
    "platform_setl_id" varchar(64),
    "priority" varchar(8) NOT NULL DEFAULT 'P1',
    "blocking" bool NOT NULL DEFAULT true,
    "request_snapshot" text,
    "response_snapshot" text,
    "platform_amount" numeric(18, 2),
    "retry_count" int4 NOT NULL DEFAULT 0,
    "next_retry_at" timestamptz(6),
    "error_code" varchar(64),
    "error_msg" varchar(512),
    "manual_ticket" bool NOT NULL DEFAULT false,
    "operator_id" int8,
    CONSTRAINT "yb_coordination_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_yb_coord_active_settle"
    ON "public"."yb_coordination" ("payment_id", "account_id", "biz_type")
    WHERE "state" NOT IN ('COMPLETED', 'CANCELLED', 'COMPENSATED')
      AND "is_deleted" = false
      AND "biz_type" IN ('OP_SETTLE', 'IP_SETTLE');

CREATE INDEX IF NOT EXISTS "idx_yb_coord_state_retry"
    ON "public"."yb_coordination" ("state", "next_retry_at");

CREATE INDEX IF NOT EXISTS "idx_yb_coord_encounter"
    ON "public"."yb_coordination" ("encounter_id");

CREATE INDEX IF NOT EXISTS "idx_yb_coord_payment"
    ON "public"."yb_coordination" ("payment_id");

CREATE TABLE IF NOT EXISTS "public"."yb_coordination_step" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "coordination_id" int8 NOT NULL,
    "step_order" int4 NOT NULL,
    "infno" varchar(16) NOT NULL,
    "state" varchar(32) NOT NULL,
    "msgid" varchar(64) NOT NULL,
    "request_snapshot" text,
    "response_snapshot" text,
    "error_code" varchar(64),
    "error_msg" varchar(512),
    CONSTRAINT "yb_coordination_step_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "fk_yb_coord_step_coord" FOREIGN KEY ("coordination_id") REFERENCES "public"."yb_coordination" ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_yb_coord_step_order"
    ON "public"."yb_coordination_step" ("coordination_id", "step_order");

COMMENT ON TABLE yb_coordination IS '医保 coordination';

COMMENT ON COLUMN yb_coordination.id IS '主键';
COMMENT ON COLUMN yb_coordination.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_coordination.created_at IS '创建时间';
COMMENT ON COLUMN yb_coordination.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_coordination.created_by IS '创建人';
COMMENT ON COLUMN yb_coordination.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_coordination.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_coordination.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_coordination.deleted_by IS '删除人';
COMMENT ON COLUMN yb_coordination.version IS '乐观锁版本号';
COMMENT ON COLUMN yb_coordination.biz_type IS 'biz type';
COMMENT ON COLUMN yb_coordination.scheme_code IS 'scheme code';
COMMENT ON COLUMN yb_coordination.state IS 'state';
COMMENT ON COLUMN yb_coordination.biz_id IS 'biz id';
COMMENT ON COLUMN yb_coordination.account_id IS 'account id';
COMMENT ON COLUMN yb_coordination.contract_id IS 'contract id';
COMMENT ON COLUMN yb_coordination.parent_id IS 'parent id';
COMMENT ON COLUMN yb_coordination.payment_id IS 'payment id';
COMMENT ON COLUMN yb_coordination.payment_no IS 'payment no';
COMMENT ON COLUMN yb_coordination.encounter_id IS 'encounter id';
COMMENT ON COLUMN yb_coordination.patient_id IS 'patient id';
COMMENT ON COLUMN yb_coordination.msgid IS 'msgid';
COMMENT ON COLUMN yb_coordination.infno IS 'infno';
COMMENT ON COLUMN yb_coordination.platform_setl_id IS 'platform setl id';
COMMENT ON COLUMN yb_coordination.priority IS 'priority';
COMMENT ON COLUMN yb_coordination.blocking IS 'blocking';
COMMENT ON COLUMN yb_coordination.request_snapshot IS 'request snapshot';
COMMENT ON COLUMN yb_coordination.response_snapshot IS 'response snapshot';
COMMENT ON COLUMN yb_coordination.platform_amount IS 'platform amount';
COMMENT ON COLUMN yb_coordination.retry_count IS 'retry count';
COMMENT ON COLUMN yb_coordination.next_retry_at IS 'next retry at';
COMMENT ON COLUMN yb_coordination.error_code IS 'error code';
COMMENT ON COLUMN yb_coordination.error_msg IS 'error msg';
COMMENT ON COLUMN yb_coordination.manual_ticket IS 'manual ticket';
COMMENT ON COLUMN yb_coordination.operator_id IS 'operator id';

COMMENT ON TABLE yb_coordination_step IS '医保 coordination step';

COMMENT ON COLUMN yb_coordination_step.id IS '主键';
COMMENT ON COLUMN yb_coordination_step.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_coordination_step.created_at IS '创建时间';
COMMENT ON COLUMN yb_coordination_step.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_coordination_step.created_by IS '创建人';
COMMENT ON COLUMN yb_coordination_step.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_coordination_step.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_coordination_step.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_coordination_step.deleted_by IS '删除人';
COMMENT ON COLUMN yb_coordination_step.coordination_id IS 'coordination id';
COMMENT ON COLUMN yb_coordination_step.step_order IS 'step order';
COMMENT ON COLUMN yb_coordination_step.infno IS 'infno';
COMMENT ON COLUMN yb_coordination_step.state IS 'state';
COMMENT ON COLUMN yb_coordination_step.msgid IS 'msgid';
COMMENT ON COLUMN yb_coordination_step.request_snapshot IS 'request snapshot';
COMMENT ON COLUMN yb_coordination_step.response_snapshot IS 'response snapshot';
COMMENT ON COLUMN yb_coordination_step.error_code IS 'error code';
COMMENT ON COLUMN yb_coordination_step.error_msg IS 'error msg';

