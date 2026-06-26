-- adm_pr_assign（建表 + 种子数据）
-- Flyway V20

CREATE TABLE IF NOT EXISTS "public"."adm_pr_assign" (
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
    "name" varchar(255),
    "active_flag" bool NOT NULL DEFAULT true,
    "practitioner_id" int8 NOT NULL,
    "org_id" int8,
    "location_id" int8,
    "specialty_code" varchar(64),
    "type_code" int4,
    "role_id" int8 NOT NULL,
    CONSTRAINT "adm_pr_assign_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ck_adm_pr_assign_org_or_location_not_null" CHECK ("org_id" IS NOT NULL OR "location_id" IS NOT NULL),
    CONSTRAINT "fk_adm_pr_assign_whale_role" FOREIGN KEY ("role_id") REFERENCES whale_roles (id)
);

CREATE INDEX IF NOT EXISTS idx_adm_pr_assign_role_id ON adm_pr_assign (role_id);
COMMENT ON TABLE adm_pr_assign IS '管理域 pr assign';
COMMENT ON COLUMN adm_pr_assign.id IS '主键';
COMMENT ON COLUMN adm_pr_assign.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_pr_assign.created_at IS '创建时间';
COMMENT ON COLUMN adm_pr_assign.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_pr_assign.created_by IS '创建人';
COMMENT ON COLUMN adm_pr_assign.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_pr_assign.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_pr_assign.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_pr_assign.deleted_by IS '删除人';
COMMENT ON COLUMN adm_pr_assign.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_pr_assign.name IS '名称';
COMMENT ON COLUMN adm_pr_assign.active_flag IS '是否启用';
COMMENT ON COLUMN adm_pr_assign.practitioner_id IS '医护人员标识';
COMMENT ON COLUMN adm_pr_assign.org_id IS '机构标识';
COMMENT ON COLUMN adm_pr_assign.location_id IS '位置/科室标识';
COMMENT ON COLUMN adm_pr_assign.specialty_code IS 'specialty编码';
COMMENT ON COLUMN adm_pr_assign.type_code IS '类型编码';
COMMENT ON COLUMN adm_pr_assign.role_id IS '岗位分配聚合根（{ adm_pr_assign}）';

-- seed: adm_pr_assign
INSERT INTO "adm_pr_assign" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "active_flag", "practitioner_id", "org_id", "location_id", "specialty_code", "type_code", "role_id")
VALUES
('322965878747435008', '1', '2026-06-10 05:11:05.909+00', '2026-06-10 05:11:05.909+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', '322663218269917184', NULL, NULL, NULL, '14'),
('322965878747435009', '1', '2026-06-10 05:11:05.909+00', '2026-06-10 05:11:05.909+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', '322663308812357632', NULL, NULL, NULL, '14'),
('322965878827126784', '1', '2026-06-10 05:11:05.928+00', '2026-06-10 05:11:05.928+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', '322663218269917184', NULL, NULL, NULL, '10'),
('322965878831321088', '1', '2026-06-10 05:11:05.929+00', '2026-06-10 05:11:05.929+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', '322663308812357632', NULL, NULL, NULL, '10'),
('322965878902624256', '1', '2026-06-10 05:11:05.946+00', '2026-06-10 05:11:05.946+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322954857622802432', NULL, NULL, '13'),
('322965878902624257', '1', '2026-06-10 05:11:05.946+00', '2026-06-10 05:11:05.946+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322954885380706304', NULL, NULL, '13'),
('322965878902624258', '1', '2026-06-10 05:11:05.946+00', '2026-06-10 05:11:05.946+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322954919841107968', NULL, NULL, '13'),
('322965878961344512', '1', '2026-06-10 05:11:05.960+00', '2026-06-10 05:11:05.960+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322954949549363200', NULL, NULL, '12'),
('322965878961344513', '1', '2026-06-10 05:11:05.960+00', '2026-06-10 05:11:05.960+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322954984441778176', NULL, NULL, '12'),
('322965878961344514', '1', '2026-06-10 05:11:05.960+00', '2026-06-10 05:11:05.960+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322955039064199168', NULL, NULL, '12'),
('322965878961344515', '1', '2026-06-10 05:11:05.960+00', '2026-06-10 05:11:05.960+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322955009603407872', NULL, NULL, '12'),
('322965878965538816', '1', '2026-06-10 05:11:05.961+00', '2026-06-10 05:11:05.961+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322955075642724352', NULL, NULL, '12'),
('322965878965538817', '1', '2026-06-10 05:11:05.961+00', '2026-06-10 05:11:05.961+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322955098803671040', NULL, NULL, '12'),
('322965879007481856', '1', '2026-06-10 05:11:05.971+00', '2026-06-10 05:11:05.971+00', '1', '1', false, NULL, NULL, 0, 'admin', true, '308791351298756608', NULL, '322955305339588608', NULL, NULL, '11'),
('322972360645808128', '1', '2026-06-10 05:36:51.314+00', '2026-06-10 05:36:51.314+00', '1', '1', false, NULL, NULL, 0, '门诊医生', true, '322963602326687744', '322663218269917184', NULL, NULL, NULL, '14'),
('322972360708722688', '1', '2026-06-10 05:36:51.329+00', '2026-06-10 05:36:51.329+00', '1', '1', false, NULL, NULL, 0, '门诊医生', true, '322963602326687744', '322663218269917184', NULL, NULL, NULL, '10'),
('322972376974233600', '1', '2026-06-10 05:36:55.207+00', '2026-06-10 05:36:55.207+00', '1', '1', false, NULL, NULL, 0, '住院医生', true, '322935698173202432', '322663308812357632', NULL, NULL, NULL, '14'),
('322972377024565248', '1', '2026-06-10 05:36:55.219+00', '2026-06-10 05:36:55.219+00', '1', '1', false, NULL, NULL, 0, '住院医生', true, '322935698173202432', '322663308812357632', NULL, NULL, NULL, '10'),
('322977240106668032', '1', '2026-06-10 05:56:14.668+00', '2026-06-10 05:56:14.668+00', '1', '1', false, NULL, NULL, 0, '药房', true, '322932010683207680', NULL, '322954949549363200', NULL, NULL, '12'),
('322977240106668033', '1', '2026-06-10 05:56:14.668+00', '2026-06-10 05:56:14.668+00', '1', '1', false, NULL, NULL, 0, '药房', true, '322932010683207680', NULL, '322955009603407872', NULL, NULL, '12'),
('323016460792238080', '1', '2026-06-10 08:32:05.608+00', '2026-06-10 08:32:05.608+00', '1', '1', false, NULL, NULL, 0, '库管', true, '322932096108597248', NULL, '322954885380706304', NULL, NULL, '13'),
('323016460863541248', '1', '2026-06-10 08:32:05.625+00', '2026-06-10 08:32:05.625+00', '1', '1', false, NULL, NULL, 0, '库管', true, '322932096108597248', NULL, '322954949549363200', NULL, NULL, '12'),
('323016460863541249', '1', '2026-06-10 08:32:05.625+00', '2026-06-10 08:32:05.625+00', '1', '1', false, NULL, NULL, 0, '库管', true, '322932096108597248', NULL, '322955009603407872', NULL, NULL, '12'),
('323016460863541250', '1', '2026-06-10 08:32:05.625+00', '2026-06-10 08:32:05.625+00', '1', '1', false, NULL, NULL, 0, '库管', true, '322932096108597248', NULL, '322955075642724352', NULL, NULL, '12'),
('323021900276043776', '1', '2026-06-10 08:53:42.482+00', '2026-06-10 08:53:42.482+00', '1', '1', false, NULL, NULL, 0, '护士', true, '322965022887120896', NULL, '322954857622802432', NULL, NULL, '13'),
('323021900347346944', '1', '2026-06-10 08:53:42.499+00', '2026-06-10 08:53:42.499+00', '1', '1', false, NULL, NULL, 0, '护士', true, '322965022887120896', NULL, '322955098803671040', NULL, NULL, '12'),
('323021900347346945', '1', '2026-06-10 08:53:42.499+00', '2026-06-10 08:53:42.499+00', '1', '1', false, NULL, NULL, 0, '护士', true, '322965022887120896', NULL, '322955075642724352', NULL, NULL, '12'),
('323021900393484288', '1', '2026-06-10 08:53:42.510+00', '2026-06-10 08:53:42.510+00', '1', '1', false, NULL, NULL, 0, '护士', true, '322965022887120896', NULL, '322955305339588608', NULL, NULL, '11')
ON CONFLICT ("id") DO NOTHING;
