-- adm_pr_assign_service（建表 + 种子数据）
-- Flyway V22

CREATE TABLE IF NOT EXISTS "public"."adm_pr_assign_service" (
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

    "assignment_id" int8 NOT NULL,
    "healthcare_service_id" int8 NOT NULL,

    CONSTRAINT "adm_pr_assign_service_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE adm_pr_assign_service IS '管理域 pr assign service';

COMMENT ON COLUMN adm_pr_assign_service.id IS '主键';
COMMENT ON COLUMN adm_pr_assign_service.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_pr_assign_service.created_at IS '创建时间';
COMMENT ON COLUMN adm_pr_assign_service.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_pr_assign_service.created_by IS '创建人';
COMMENT ON COLUMN adm_pr_assign_service.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_pr_assign_service.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_pr_assign_service.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_pr_assign_service.deleted_by IS '删除人';
COMMENT ON COLUMN adm_pr_assign_service.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_pr_assign_service.assignment_id IS '岗位服务项子实体，对应表 { adm_pr_assign_service}';
COMMENT ON COLUMN adm_pr_assign_service.healthcare_service_id IS '医疗服务标识，关联 { adm_healthcare_service.id}';

-- seed: adm_pr_assign_service
INSERT INTO "adm_pr_assign_service" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "assignment_id", "healthcare_service_id")
VALUES
('322965878831321088', '1', '2026-06-10 05:11:05.929+00', '2026-06-10 05:11:05.929+00', '1', '1', false, NULL, NULL, 0, '322965878827126784', '322957224354254848'),
('322965878831321089', '1', '2026-06-10 05:11:05.929+00', '2026-06-10 05:11:05.929+00', '1', '1', false, NULL, NULL, 0, '322965878827126784', '322957264586018816'),
('322965878831321090', '1', '2026-06-10 05:11:05.929+00', '2026-06-10 05:11:05.929+00', '1', '1', false, NULL, NULL, 0, '322965878827126784', '322957296852799488'),
('322965878831321091', '1', '2026-06-10 05:11:05.929+00', '2026-06-10 05:11:05.929+00', '1', '1', false, NULL, NULL, 0, '322965878831321088', '322957224354254848'),
('322965878831321092', '1', '2026-06-10 05:11:05.929+00', '2026-06-10 05:11:05.929+00', '1', '1', false, NULL, NULL, 0, '322965878831321088', '322957264586018816'),
('322965878831321093', '1', '2026-06-10 05:11:05.929+00', '2026-06-10 05:11:05.929+00', '1', '1', false, NULL, NULL, 0, '322965878831321088', '322957296852799488'),
('322972360708722688', '1', '2026-06-10 05:36:51.329+00', '2026-06-10 05:36:51.329+00', '1', '1', false, NULL, NULL, 0, '322972360708722688', '322957224354254848'),
('322972360708722689', '1', '2026-06-10 05:36:51.329+00', '2026-06-10 05:36:51.329+00', '1', '1', false, NULL, NULL, 0, '322972360708722688', '322957264586018816'),
('322972360708722690', '1', '2026-06-10 05:36:51.329+00', '2026-06-10 05:36:51.329+00', '1', '1', false, NULL, NULL, 0, '322972360708722688', '322957296852799488'),
('322972377024565248', '1', '2026-06-10 05:36:55.219+00', '2026-06-10 05:36:55.219+00', '1', '1', false, NULL, NULL, 0, '322972377024565248', '322957224354254848'),
('322972377024565249', '1', '2026-06-10 05:36:55.219+00', '2026-06-10 05:36:55.219+00', '1', '1', false, NULL, NULL, 0, '322972377024565248', '322957264586018816'),
('322972377024565250', '1', '2026-06-10 05:36:55.219+00', '2026-06-10 05:36:55.219+00', '1', '1', false, NULL, NULL, 0, '322972377024565248', '322957296852799488')
ON CONFLICT ("id") DO NOTHING;
