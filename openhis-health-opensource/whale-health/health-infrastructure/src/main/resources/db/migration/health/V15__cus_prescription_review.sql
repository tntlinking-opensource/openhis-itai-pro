-- DM-CUS-015 处方点评（方级一对一有效行；撤销软删后可重评）
CREATE TABLE IF NOT EXISTS "public"."cus_prescription_review" (
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
    "bus_no" varchar(64) NOT NULL,
    "prescription_no" varchar(64) NOT NULL,
    "reviewer_id" int8 NOT NULL,
    "reviewed_at" timestamptz(6) NOT NULL,
    "reasonable_flag" bool NOT NULL,
    "status_enum" varchar(32) NOT NULL,
    "reason" varchar(1000),
    "remark" varchar(1000),
    CONSTRAINT "cus_prescription_review_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cus_prescription_review IS '处方点评（门诊方级；有效行一对一）';
COMMENT ON COLUMN cus_prescription_review.id IS '主键';
COMMENT ON COLUMN cus_prescription_review.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_prescription_review.created_at IS '创建时间';
COMMENT ON COLUMN cus_prescription_review.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_prescription_review.created_by IS '创建人';
COMMENT ON COLUMN cus_prescription_review.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_prescription_review.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_prescription_review.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_prescription_review.deleted_by IS '删除人';
COMMENT ON COLUMN cus_prescription_review.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_prescription_review.bus_no IS '点评业务号（本聚合日序，前缀 PRV）';
COMMENT ON COLUMN cus_prescription_review.prescription_no IS '处方号（有效行唯一锚点）';
COMMENT ON COLUMN cus_prescription_review.reviewer_id IS '点评人';
COMMENT ON COLUMN cus_prescription_review.reviewed_at IS '点评时间';
COMMENT ON COLUMN cus_prescription_review.reasonable_flag IS '是否合理';
COMMENT ON COLUMN cus_prescription_review.status_enum IS '点评状态（有效行 REVIEWED）';
COMMENT ON COLUMN cus_prescription_review.reason IS '原因（不合理必填）';
COMMENT ON COLUMN cus_prescription_review.remark IS '备注';

CREATE UNIQUE INDEX IF NOT EXISTS uk_cus_prescription_review_rx_active
    ON cus_prescription_review (tenant_id, prescription_no)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_cus_prescription_review_reviewed_at
    ON cus_prescription_review (reviewed_at);

CREATE INDEX IF NOT EXISTS idx_cus_prescription_review_reviewer_id
    ON cus_prescription_review (reviewer_id)
    WHERE is_deleted = false;

-- ---------------------------------------------------------------------------
-- 菜单：发药管理 → 处方点评记录（AS-OP-PHA-007）
-- ---------------------------------------------------------------------------
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('890020000000000020', 'Navigation.PrescriptionReview', '处方点评记录', 'Navigation',
     '菜单入口: PrescriptionReview — 发药管理·门诊处方点评记录', NULL,
     TIMESTAMP '2026-08-29 00:00:00+00', TIMESTAMP '2026-08-29 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name          = EXCLUDED.name,
    display_name  = EXCLUDED.display_name,
    parent_name   = EXCLUDED.parent_name,
    description   = EXCLUDED.description,
    is_deleted    = FALSE,
    deleted_at    = NULL,
    deleted_by    = NULL,
    updated_at    = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('890010000000000020', 'PrescriptionReview', 'PrescriptionReview', '处方点评记录', 'lucide:clipboard-check',
     '/report/prescription-review', '/report/prescription-review/index', 'Navigation.PrescriptionReview',
     '310400000000000060', 11, TRUE, FALSE, FALSE, '', 'menu',
     'AS-OP-PHA-007 · OutpatientPharmacyPrescriptionReviewAppService', NULL,
     TIMESTAMP '2026-08-29 00:00:00+00', TIMESTAMP '2026-08-29 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('890030000000000036', 'Navigation.PrescriptionReview', 'R', '1', NULL,
     TIMESTAMP '2026-08-29 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('890030000000000037', 'Navigation.PrescriptionReview', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-08-29 00:00:00+00', TIMESTAMP '2026-08-29 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;
