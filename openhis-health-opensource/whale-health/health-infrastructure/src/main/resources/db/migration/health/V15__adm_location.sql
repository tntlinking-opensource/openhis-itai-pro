-- adm_location（建表）
-- Flyway V15

CREATE TABLE IF NOT EXISTS "public"."adm_location" (
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
    "name" varchar(255) NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "operational_enum" varchar(255),
    "form_enum" varchar(255) NOT NULL,
    "mode_enum" varchar(255) NOT NULL,
    "type_json" varchar(255),
    "organization_id" int8 NOT NULL,
    "display_order" int4,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "parent_id" int8,
    CONSTRAINT "adm_location_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_location IS '管理域 location';
COMMENT ON COLUMN adm_location.id IS '主键';
COMMENT ON COLUMN adm_location.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_location.created_at IS '创建时间';
COMMENT ON COLUMN adm_location.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_location.created_by IS '创建人';
COMMENT ON COLUMN adm_location.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_location.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_location.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_location.deleted_by IS '删除人';
COMMENT ON COLUMN adm_location.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_location.name IS '位置聚合根实体';
COMMENT ON COLUMN adm_location.bus_no IS 'bus 编号';
COMMENT ON COLUMN adm_location.status_enum IS 'status 枚举值';
COMMENT ON COLUMN adm_location.operational_enum IS 'operational 枚举值';
COMMENT ON COLUMN adm_location.form_enum IS 'form 枚举值';
COMMENT ON COLUMN adm_location.mode_enum IS 'mode 枚举值';
COMMENT ON COLUMN adm_location.type_json IS 'type JSON 快照';
COMMENT ON COLUMN adm_location.organization_id IS 'organization 标识';
COMMENT ON COLUMN adm_location.display_order IS '显示顺序';
COMMENT ON COLUMN adm_location.py_str IS '拼音码';
COMMENT ON COLUMN adm_location.wb_str IS '五笔码';
COMMENT ON COLUMN adm_location.parent_id IS 'parent 标识';

-- seed: adm_location
INSERT INTO "adm_location" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "status_enum", "operational_enum", "form_enum", "mode_enum", "type_json", "organization_id", "display_order", "py_str", "wb_str", "parent_id")
VALUES
('322954857622802432', '1', '2026-06-10 04:27:18.268+00', '2026-06-10 04:27:18.268+00', '1', '1', false, NULL, NULL, 0, '耗材库', 'LO0019', 'ACTIVE', 'NOT_IN_USE', 'WAREHOUSE', 'INSTANCE', '{}', '322663105141149696', 0, NULL, NULL, NULL),
('322954885380706304', '1', '2026-06-10 04:27:24.886+00', '2026-06-10 04:27:24.886+00', '1', '1', false, NULL, NULL, 0, '西药库', 'LO0020', 'ACTIVE', 'NOT_IN_USE', 'WAREHOUSE', 'INSTANCE', '{}', '322663105141149696', 0, NULL, NULL, NULL),
('322954919841107968', '1', '2026-06-10 04:27:33.102+00', '2026-06-10 04:27:33.102+00', '1', '1', false, NULL, NULL, 0, '中药库', 'LO0021', 'ACTIVE', 'NOT_IN_USE', 'WAREHOUSE', 'INSTANCE', '{}', '322663105141149696', 0, NULL, NULL, NULL),
('322954949549363200', '1', '2026-06-10 04:27:40.185+00', '2026-06-10 04:27:40.185+00', '1', '1', false, NULL, NULL, 0, '门诊西药房', 'LO0022', 'ACTIVE', 'NOT_IN_USE', 'PHARMACY', 'INSTANCE', '{}', '322663105141149696', 0, NULL, NULL, NULL),
('322954984441778176', '1', '2026-06-10 04:27:48.505+00', '2026-06-10 04:27:48.505+00', '1', '1', false, NULL, NULL, 0, '门诊中药房', 'LO0023', 'ACTIVE', 'NOT_IN_USE', 'PHARMACY', 'INSTANCE', '{}', '322663105141149696', 0, NULL, NULL, NULL),
('322955009603407872', '1', '2026-06-10 04:27:54.503+00', '2026-06-10 04:27:54.503+00', '1', '1', false, NULL, NULL, 0, '住院西药房', 'LO0024', 'ACTIVE', 'NOT_IN_USE', 'PHARMACY', 'INSTANCE', '{}', '322663105141149696', 0, NULL, NULL, NULL),
('322955039064199168', '1', '2026-06-10 04:28:01.527+00', '2026-06-10 04:28:01.527+00', '1', '1', false, NULL, NULL, 0, '住院中药房', 'LO0025', 'ACTIVE', 'NOT_IN_USE', 'PHARMACY', 'INSTANCE', '{}', '322663105141149696', 0, NULL, NULL, NULL),
('322955075642724352', '1', '2026-06-10 04:28:10.248+00', '2026-06-10 04:28:10.248+00', '1', '1', false, NULL, NULL, 0, '门诊耗材房', 'LO0026', 'ACTIVE', 'NOT_IN_USE', 'PHARMACY', 'INSTANCE', '{}', '322663105141149696', 0, NULL, NULL, NULL),
('322955098803671040', '1', '2026-06-10 04:28:15.770+00', '2026-06-10 04:28:15.770+00', '1', '1', false, NULL, NULL, 0, '住院耗材房', 'LO0027', 'ACTIVE', 'NOT_IN_USE', 'PHARMACY', 'INSTANCE', '{}', '322663105141149696', 0, NULL, NULL, NULL),
('322955305339588608', '1', '2026-06-10 04:29:05.012+00', '2026-06-10 04:29:20.543+00', '1', '1', false, NULL, NULL, 0, '内科疗区', 'LO0028', 'ACTIVE', 'NOT_IN_USE', 'WARD', 'INSTANCE', NULL, '322663308812357632', NULL, NULL, NULL, NULL),
('322955414072725504', '1', '2026-06-10 04:29:30.936+00', '2026-06-10 06:13:38.711+00', '1', '1', false, NULL, NULL, 0, '301', 'LO0028.LO0029', 'ACTIVE', 'NOT_IN_USE', 'HOUSE', 'INSTANCE', NULL, '322663308812357632', NULL, NULL, NULL, '322955305339588608'),
('322955435841163264', '1', '2026-06-10 04:29:36.126+00', '2026-06-10 06:13:46.171+00', '1', '1', false, NULL, NULL, 0, '302', 'LO0028.LO0030', 'ACTIVE', 'NOT_IN_USE', 'HOUSE', 'INSTANCE', NULL, '322663308812357632', NULL, NULL, NULL, '322955305339588608'),
('322955450206654464', '1', '2026-06-10 04:29:39.551+00', '2026-06-10 06:13:52.009+00', '1', '1', false, NULL, NULL, 0, '303', 'LO0028.LO0031', 'ACTIVE', 'NOT_IN_USE', 'HOUSE', 'INSTANCE', NULL, '322663308812357632', NULL, NULL, NULL, '322955305339588608'),
('322955479021522944', '1', '2026-06-10 04:29:46.421+00', '2026-06-10 04:29:46.421+00', '1', '1', false, NULL, NULL, 0, '1号床', 'LO0028.LO0029.LO0032', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955414072725504'),
('322955492829171712', '1', '2026-06-10 04:29:49.713+00', '2026-06-10 04:29:49.713+00', '1', '1', false, NULL, NULL, 0, '2号床', 'LO0028.LO0029.LO0033', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955414072725504'),
('322955505781182464', '1', '2026-06-10 04:29:52.802+00', '2026-06-10 04:29:52.802+00', '1', '1', false, NULL, NULL, 0, '3号床', 'LO0028.LO0029.LO0034', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955414072725504'),
('322955532368875520', '1', '2026-06-10 04:29:59.140+00', '2026-06-10 04:29:59.140+00', '1', '1', false, NULL, NULL, 0, '4号床', 'LO0028.LO0029.LO0035', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955414072725504'),
('322955544607854592', '1', '2026-06-10 04:30:02.058+00', '2026-06-10 04:30:02.058+00', '1', '1', false, NULL, NULL, 0, '1号床', 'LO0028.LO0030.LO0036', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955435841163264'),
('322955557694083072', '1', '2026-06-10 04:30:05.178+00', '2026-06-10 04:30:05.178+00', '1', '1', false, NULL, NULL, 0, '2号床', 'LO0028.LO0030.LO0037', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955435841163264'),
('322955569337470976', '1', '2026-06-10 04:30:07.954+00', '2026-06-10 04:30:07.954+00', '1', '1', false, NULL, NULL, 0, '3号床', 'LO0028.LO0030.LO0038', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955435841163264'),
('322955581840691200', '1', '2026-06-10 04:30:10.935+00', '2026-06-10 04:30:10.935+00', '1', '1', false, NULL, NULL, 0, '4号床', 'LO0028.LO0030.LO0039', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955435841163264'),
('322955603542020096', '1', '2026-06-10 04:30:16.109+00', '2026-06-10 04:30:16.109+00', '1', '1', false, NULL, NULL, 0, '1号床', 'LO0028.LO0031.LO0040', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955450206654464'),
('322955616741494784', '1', '2026-06-10 04:30:19.256+00', '2026-06-10 04:30:19.256+00', '1', '1', false, NULL, NULL, 0, '2号床', 'LO0028.LO0031.LO0041', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955450206654464'),
('322955629387321344', '1', '2026-06-10 04:30:22.271+00', '2026-06-10 04:30:22.271+00', '1', '1', false, NULL, NULL, 0, '3号床', 'LO0028.LO0031.LO0042', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955450206654464'),
('322955643350159360', '1', '2026-06-10 04:30:25.601+00', '2026-06-10 04:30:25.601+00', '1', '1', false, NULL, NULL, 0, '4号床', 'LO0028.LO0031.LO0043', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', '322663308812357632', NULL, NULL, NULL, '322955450206654464')
ON CONFLICT ("id") DO NOTHING;
