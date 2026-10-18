-- Squashed V4 fin_ddl

-- from V42__fin_contract.sql
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
COMMENT ON COLUMN fin_contract.name IS '名称';
COMMENT ON COLUMN fin_contract.category_enum IS 'category枚举值';
COMMENT ON COLUMN fin_contract.status_enum IS '状态枚举值';
COMMENT ON COLUMN fin_contract.active_flag IS '是否启用';
COMMENT ON COLUMN fin_contract.org_id IS '机构标识';
COMMENT ON COLUMN fin_contract.yb_flag IS '医保标志位';
COMMENT ON COLUMN fin_contract.display_order IS '显示顺序';
COMMENT ON COLUMN fin_contract.adm_vs IS '院内/历史字段；CHS 统筹区划读 chs-contract.{bus_no}.adm_vs';
COMMENT ON COLUMN fin_contract.py_str IS '拼音码';
COMMENT ON COLUMN fin_contract.wb_str IS '五笔码';
COMMENT ON COLUMN fin_contract.client_id IS '客户端标识';
COMMENT ON COLUMN fin_contract.client_secret IS '客户端secret';
COMMENT ON COLUMN fin_contract.username IS '用户名';
COMMENT ON COLUMN fin_contract.password IS '密码';
COMMENT ON COLUMN fin_contract.cli_prv_key IS '客户端私钥';
COMMENT ON COLUMN fin_contract.cli_pub_key IS 'cli公钥key';
COMMENT ON COLUMN fin_contract.server_pub_key IS 'server公钥key';
COMMENT ON COLUMN fin_contract.scope IS '授权范围';
COMMENT ON COLUMN fin_contract.grant_type IS '授权类型';
COMMENT ON COLUMN fin_contract.fixmedins_code IS '院内/历史字段；CHS 定点机构编码读 chs-contract.{bus_no}.fixmedins_code';
COMMENT ON COLUMN fin_contract.fixmedins_name IS 'fixmedins名称';

-- seed: fin_contract（bus_no 与 chs-contract.properties 配置段键一致，供 yb_invoke_log.contract_id 解析）

-- from V43__fin_payment_rec_detail.sql
-- fin_payment_rec_detail（建表）
-- Flyway V50

CREATE TABLE IF NOT EXISTS "public"."fin_payment_rec_detail" (
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
    "reconciliation_id" int8 NOT NULL,
    "predecessor_id" int8,
    "target_enum" varchar(255),
    "account_id" int8 NOT NULL,
    "account_code" varchar(255),
    "pay_enum" varchar(255) NOT NULL,
    "pay_level_enum" varchar(255),
    "amount" numeric(19,6) NOT NULL,
    "return_amount" numeric(19,6),
    "charge_amount" numeric(19,6),
    "pay_trans_no" varchar(255),
    "pay_trans_text" varchar(255),
    "pay_trans_date" timestamptz(6),
    "before_balance" numeric(19,6),
    "after_balance" numeric(19,6),
    "result_enum" varchar(255) NOT NULL,
    CONSTRAINT "fin_payment_rec_detail_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE fin_payment_rec_detail IS '财务 payment rec detail';
COMMENT ON COLUMN fin_payment_rec_detail.id IS '主键';
COMMENT ON COLUMN fin_payment_rec_detail.tenant_id IS '租户标识';
COMMENT ON COLUMN fin_payment_rec_detail.created_at IS '创建时间';
COMMENT ON COLUMN fin_payment_rec_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_payment_rec_detail.created_by IS '创建人';
COMMENT ON COLUMN fin_payment_rec_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_payment_rec_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_payment_rec_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_payment_rec_detail.deleted_by IS '删除人';
COMMENT ON COLUMN fin_payment_rec_detail.version IS '乐观锁版本号';
COMMENT ON COLUMN fin_payment_rec_detail.reconciliation_id IS '对账标识';
COMMENT ON COLUMN fin_payment_rec_detail.predecessor_id IS 'predecessor标识';
COMMENT ON COLUMN fin_payment_rec_detail.target_enum IS 'target枚举值';
COMMENT ON COLUMN fin_payment_rec_detail.account_id IS '账户标识';
COMMENT ON COLUMN fin_payment_rec_detail.account_code IS '账户编码';
COMMENT ON COLUMN fin_payment_rec_detail.pay_enum IS '支付枚举值';
COMMENT ON COLUMN fin_payment_rec_detail.pay_level_enum IS '支付level枚举值';
COMMENT ON COLUMN fin_payment_rec_detail.amount IS '金额';
COMMENT ON COLUMN fin_payment_rec_detail.return_amount IS 'return金额';
COMMENT ON COLUMN fin_payment_rec_detail.charge_amount IS '收费金额';
COMMENT ON COLUMN fin_payment_rec_detail.pay_trans_no IS '支付trans编号';
COMMENT ON COLUMN fin_payment_rec_detail.pay_trans_text IS '支付trans文本';
COMMENT ON COLUMN fin_payment_rec_detail.pay_trans_date IS '支付trans日期';
COMMENT ON COLUMN fin_payment_rec_detail.before_balance IS 'before余额';
COMMENT ON COLUMN fin_payment_rec_detail.after_balance IS 'after余额';
COMMENT ON COLUMN fin_payment_rec_detail.result_enum IS 'result枚举值';

CREATE INDEX IF NOT EXISTS idx_fin_payment_rec_detail_reconciliation_id
    ON fin_payment_rec_detail (reconciliation_id);

-- from V44__fin_payment_rec_static.sql
-- fin_payment_rec_static（建表）
-- Flyway V51

CREATE TABLE IF NOT EXISTS "public"."fin_payment_rec_static" (
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
    "reconciliation_id" int8 NOT NULL,
    "method_code" varchar(255) NOT NULL,
    "type_code" varchar(255) NOT NULL,
    "type_name" varchar(255),
    "amount" numeric(19,6) NOT NULL,
    "charge_item_ids" text,
    CONSTRAINT "fin_payment_rec_static_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE fin_payment_rec_static IS '财务 payment rec static';
COMMENT ON COLUMN fin_payment_rec_static.id IS '主键';
COMMENT ON COLUMN fin_payment_rec_static.tenant_id IS '租户标识';
COMMENT ON COLUMN fin_payment_rec_static.created_at IS '创建时间';
COMMENT ON COLUMN fin_payment_rec_static.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_payment_rec_static.created_by IS '创建人';
COMMENT ON COLUMN fin_payment_rec_static.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_payment_rec_static.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_payment_rec_static.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_payment_rec_static.deleted_by IS '删除人';
COMMENT ON COLUMN fin_payment_rec_static.version IS '乐观锁版本号';
COMMENT ON COLUMN fin_payment_rec_static.reconciliation_id IS '对账标识';
COMMENT ON COLUMN fin_payment_rec_static.method_code IS '支付分类统计快照';
COMMENT ON COLUMN fin_payment_rec_static.type_code IS '类型编码';
COMMENT ON COLUMN fin_payment_rec_static.type_name IS '类型名称';
COMMENT ON COLUMN fin_payment_rec_static.amount IS '金额';
COMMENT ON COLUMN fin_payment_rec_static.charge_item_ids IS '收费项ids';

CREATE INDEX IF NOT EXISTS idx_fin_payment_rec_static_reconciliation_id
    ON fin_payment_rec_static (reconciliation_id)
    WHERE is_deleted = FALSE;

-- from V45__fin_payment_reconciliation.sql
-- fin_payment_reconciliation（建表）
-- Flyway V52

CREATE TABLE IF NOT EXISTS "public"."fin_payment_reconciliation" (
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
    "status_enum" varchar(255) NOT NULL,
    "relation_id" int8,
    "payment_no" varchar(255) NOT NULL,
    "payment_enum" varchar(255) NOT NULL,
    "kind_enum" varchar(255) NOT NULL,
    "enterer_id" int8 NOT NULL,
    "issuer_enum" varchar(255),
    "patient_id" int8 NOT NULL,
    "practitioner_id" int8,
    "outcome_enum" varchar(255),
    "location_id" int8,
    "expiration_date" timestamptz(6),
    "tendered_amount" numeric(19,6) NOT NULL,
    "returned_amount" numeric(19,6),
    "display_amount" numeric(19,6) NOT NULL,
    "print_count" int4 NOT NULL,
    "contract_no" varchar(255),
    "charge_item_ids" text NOT NULL,
    "encounter_id" int8 NOT NULL,
    "bill_date" timestamptz(6),
    "invoice_id" int8,
    "account_id" int8,
    "yb_clear_flag" bool,
    "cashier_settlement_id" int8,
    CONSTRAINT "fin_payment_reconciliation_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS idx_fin_payment_cashier_settlement
    ON fin_payment_reconciliation (enterer_id, bill_date, cashier_settlement_id)
    WHERE is_deleted = FALSE;

-- 日结/交账挂载现算读侧：按 cashier_settlement_id 反查 payment
CREATE INDEX IF NOT EXISTS idx_fin_payment_reconciliation_cashier_settlement_id
    ON fin_payment_reconciliation (cashier_settlement_id)
    WHERE is_deleted = FALSE AND cashier_settlement_id IS NOT NULL;
COMMENT ON TABLE fin_payment_reconciliation IS '财务 payment reconciliation';
COMMENT ON COLUMN fin_payment_reconciliation.id IS '主键';
COMMENT ON COLUMN fin_payment_reconciliation.tenant_id IS '租户标识';
COMMENT ON COLUMN fin_payment_reconciliation.created_at IS '创建时间';
COMMENT ON COLUMN fin_payment_reconciliation.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_payment_reconciliation.created_by IS '创建人';
COMMENT ON COLUMN fin_payment_reconciliation.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_payment_reconciliation.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_payment_reconciliation.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_payment_reconciliation.deleted_by IS '删除人';
COMMENT ON COLUMN fin_payment_reconciliation.version IS '乐观锁版本号';
COMMENT ON COLUMN fin_payment_reconciliation.status_enum IS '支付流水聚合根';
COMMENT ON COLUMN fin_payment_reconciliation.relation_id IS 'relation 标识';
COMMENT ON COLUMN fin_payment_reconciliation.payment_no IS 'payment 编号';
COMMENT ON COLUMN fin_payment_reconciliation.payment_enum IS 'payment 枚举值';
COMMENT ON COLUMN fin_payment_reconciliation.kind_enum IS 'kind 枚举值';
COMMENT ON COLUMN fin_payment_reconciliation.enterer_id IS 'enterer 标识';
COMMENT ON COLUMN fin_payment_reconciliation.issuer_enum IS 'issuer 枚举值';
COMMENT ON COLUMN fin_payment_reconciliation.patient_id IS 'patient 标识';
COMMENT ON COLUMN fin_payment_reconciliation.practitioner_id IS 'practitioner 标识';
COMMENT ON COLUMN fin_payment_reconciliation.outcome_enum IS 'outcome 枚举值';
COMMENT ON COLUMN fin_payment_reconciliation.location_id IS 'location 标识';
COMMENT ON COLUMN fin_payment_reconciliation.expiration_date IS 'expiration日期';
COMMENT ON COLUMN fin_payment_reconciliation.tendered_amount IS 'tendered 金额（元，与库 numeric(19,6) 一致）';
COMMENT ON COLUMN fin_payment_reconciliation.returned_amount IS 'returned 金额';
COMMENT ON COLUMN fin_payment_reconciliation.display_amount IS 'display 金额';
COMMENT ON COLUMN fin_payment_reconciliation.print_count IS 'print 数量';
COMMENT ON COLUMN fin_payment_reconciliation.contract_no IS 'contract 编号';
COMMENT ON COLUMN fin_payment_reconciliation.charge_item_ids IS '收费项ids';
COMMENT ON COLUMN fin_payment_reconciliation.encounter_id IS 'encounter 标识';
COMMENT ON COLUMN fin_payment_reconciliation.bill_date IS 'bill日期';
COMMENT ON COLUMN fin_payment_reconciliation.invoice_id IS 'invoice 标识';
COMMENT ON COLUMN fin_payment_reconciliation.account_id IS 'account 标识';
COMMENT ON COLUMN fin_payment_reconciliation.yb_clear_flag IS 'ybClear 标志位';
COMMENT ON COLUMN fin_payment_reconciliation.cashier_settlement_id IS '收费员交账头 ID；非空表示已交账';

-- from V46__inventory_lock.sql
-- inventory_lock（建表）
-- Flyway V53

CREATE TABLE IF NOT EXISTS "public"."inventory_lock" (
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
    "inventory_id" int8 NOT NULL,
    "lock_flag" bool NOT NULL,
    "status" varchar(255) NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "order_flag" int4 NOT NULL,
    CONSTRAINT "inventory_lock_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE inventory_lock IS '库存锁定';
COMMENT ON COLUMN inventory_lock.id IS '主键';
COMMENT ON COLUMN inventory_lock.tenant_id IS '租户标识';
COMMENT ON COLUMN inventory_lock.created_at IS '创建时间';
COMMENT ON COLUMN inventory_lock.updated_at IS '最后更新时间';
COMMENT ON COLUMN inventory_lock.created_by IS '创建人';
COMMENT ON COLUMN inventory_lock.updated_by IS '最后更新人';
COMMENT ON COLUMN inventory_lock.is_deleted IS '软删标记';
COMMENT ON COLUMN inventory_lock.deleted_at IS '删除时间';
COMMENT ON COLUMN inventory_lock.deleted_by IS '删除人';
COMMENT ON COLUMN inventory_lock.version IS '乐观锁版本号';
COMMENT ON COLUMN inventory_lock.inventory_id IS '库存锁定记录（DM-WKF-008 §3.2）';
COMMENT ON COLUMN inventory_lock.lock_flag IS '锁定标识：true 已锁定、false 未锁定';
COMMENT ON COLUMN inventory_lock.status IS '锁定状态：见 LockStatus';
COMMENT ON COLUMN inventory_lock.bus_no IS '业务单号';
COMMENT ON COLUMN inventory_lock.order_flag IS '同一业务单内的顺序号';

-- from V71__fin_payment_yb_settlement.sql
-- V83：支付/医保结算扩展 + 收费员交账（合并原 V83/V84）

CREATE TABLE IF NOT EXISTS fin_payment_yb_settlement (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    reconciliation_id       BIGINT       NOT NULL,
    setl_id                 VARCHAR(30)  NOT NULL,
    chrg_bchno              VARCHAR(30),
    mdtrt_id                VARCHAR(30),
    settlement_record_id    BIGINT,
    account_id              BIGINT,
    status                  VARCHAR(16)  NOT NULL DEFAULT 'CONFIRMED',

    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMPTZ,
    deleted_by              BIGINT,
    tenant_id               BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fin_payment_yb_settlement_payment_setl
    ON fin_payment_yb_settlement (reconciliation_id, setl_id, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_fin_payment_yb_settlement_payment
    ON fin_payment_yb_settlement (reconciliation_id, status)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE fin_payment_yb_settlement IS '支付流水关联的医保正式结算凭证（一行一个 setl_id）';
COMMENT ON COLUMN fin_payment_yb_settlement.id IS '主键';
COMMENT ON COLUMN fin_payment_yb_settlement.reconciliation_id IS '对账标识';
COMMENT ON COLUMN fin_payment_yb_settlement.setl_id IS '平台 setl_id';
COMMENT ON COLUMN fin_payment_yb_settlement.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN fin_payment_yb_settlement.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN fin_payment_yb_settlement.settlement_record_id IS 'yb_settlement_record.id（CHS 侧）';
COMMENT ON COLUMN fin_payment_yb_settlement.account_id IS '账户标识';
COMMENT ON COLUMN fin_payment_yb_settlement.status IS '状态';
COMMENT ON COLUMN fin_payment_yb_settlement.created_at IS '创建时间';
COMMENT ON COLUMN fin_payment_yb_settlement.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_payment_yb_settlement.created_by IS '创建人';
COMMENT ON COLUMN fin_payment_yb_settlement.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_payment_yb_settlement.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_payment_yb_settlement.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_payment_yb_settlement.deleted_by IS '删除人';
COMMENT ON COLUMN fin_payment_yb_settlement.tenant_id IS '租户标识';

-- fin_cashier_settlement：交账头终态（审批流列）
CREATE TABLE IF NOT EXISTS fin_cashier_settlement (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT,
    created_at          TIMESTAMPTZ(6)  NOT NULL,
    updated_at          TIMESTAMPTZ(6),
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ(6),
    deleted_by          BIGINT,
    version             INT4            NOT NULL DEFAULT 0,
    bus_no              VARCHAR(64)     NOT NULL,
    type_enum           VARCHAR(32)     NOT NULL,
    status_enum         VARCHAR(32)     NOT NULL,
    org_id              BIGINT,
    location_id         BIGINT,
    date_from           TIMESTAMPTZ(6),
    date_to             TIMESTAMPTZ(6),
    payment_count       INT4,
    total_income        NUMERIC(19, 6),
    practitioner_ids    TEXT,
    submitter_id        INT8            NOT NULL,
    submitted_at        TIMESTAMPTZ(6)  NOT NULL,
    approver_id         INT8,
    approved_at         TIMESTAMPTZ(6),
    CONSTRAINT fin_cashier_settlement_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fin_cashier_settlement_bus_no
    ON fin_cashier_settlement (tenant_id, bus_no)
    WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_fin_cashier_settlement_type_submitted
    ON fin_cashier_settlement (tenant_id, type_enum, submitted_at DESC)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE fin_cashier_settlement IS '收费员交账头表（交账批次；审批流）';
COMMENT ON COLUMN fin_cashier_settlement.id IS '主键';
COMMENT ON COLUMN fin_cashier_settlement.tenant_id IS '租户标识';
COMMENT ON COLUMN fin_cashier_settlement.created_at IS '创建时间';
COMMENT ON COLUMN fin_cashier_settlement.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_cashier_settlement.created_by IS '创建人';
COMMENT ON COLUMN fin_cashier_settlement.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_cashier_settlement.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_cashier_settlement.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_cashier_settlement.deleted_by IS '删除人';
COMMENT ON COLUMN fin_cashier_settlement.version IS '乐观锁版本号';
COMMENT ON COLUMN fin_cashier_settlement.bus_no IS '交账单号';
COMMENT ON COLUMN fin_cashier_settlement.type_enum IS '交账类型：OP_PAYMENT/IP_PAYMENT/OP_PHARMACY/IP_PHARMACY';
COMMENT ON COLUMN fin_cashier_settlement.status_enum IS '审批状态：SUBMITTED/APPROVED/REJECTED/WITHDRAWN';
COMMENT ON COLUMN fin_cashier_settlement.org_id IS '交账所属科室（收款必填，药房可空）';
COMMENT ON COLUMN fin_cashier_settlement.location_id IS '交账药房/窗口（药房必填，收款侧可选）';
COMMENT ON COLUMN fin_cashier_settlement.date_from IS '交账业务日窗起';
COMMENT ON COLUMN fin_cashier_settlement.date_to IS '交账业务日窗止';
COMMENT ON COLUMN fin_cashier_settlement.payment_count IS '本次挂载笔数快照（费用=payment；药房=dispense 行数）';
COMMENT ON COLUMN fin_cashier_settlement.total_income IS '本次交账总收入净额快照';
COMMENT ON COLUMN fin_cashier_settlement.practitioner_ids IS '窗内收费人/发药人快照，逗号分隔 Long';
COMMENT ON COLUMN fin_cashier_settlement.submitter_id IS '交账提交人';
COMMENT ON COLUMN fin_cashier_settlement.submitted_at IS '交账提交时间';
COMMENT ON COLUMN fin_cashier_settlement.approver_id IS '审批人';
COMMENT ON COLUMN fin_cashier_settlement.approved_at IS '审批时间';

-- =============================================================================
-- V13 squash: fin report indexes
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_rpt_fin_payment_bill
    ON fin_payment_reconciliation (kind_enum, bill_date, status_enum)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_fin_payment_encounter
    ON fin_payment_reconciliation (encounter_id, bill_date)
    WHERE is_deleted = FALSE AND encounter_id IS NOT NULL;
