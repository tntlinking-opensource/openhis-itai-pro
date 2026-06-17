-- cus_frequency（建表 + 种子）
-- Flyway V33

CREATE TABLE IF NOT EXISTS "public"."cus_frequency" (
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
    "rate_code" varchar(255),
    "name" varchar(255) NOT NULL,
    "cycle_type" varchar(32) NOT NULL,
    "day_count" int4,
    "day_interval" int4,
    "day_times" varchar(255),
    "week_cycle_flag" bool,
    "total_execution_count" int4,
    "execution_period" int4,
    "execution_period_unit" varchar(255),
    "applicable_scenes" varchar(255),
    "allow_cross_day" varchar(32),
    "active_flag" bool NOT NULL,
    "sort_no" int4,
    CONSTRAINT "cus_frequency_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cus_frequency IS '自定义 frequency';

COMMENT ON COLUMN cus_frequency.id IS '主键';
COMMENT ON COLUMN cus_frequency.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_frequency.created_at IS '创建时间';
COMMENT ON COLUMN cus_frequency.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_frequency.created_by IS '创建人';
COMMENT ON COLUMN cus_frequency.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_frequency.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_frequency.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_frequency.deleted_by IS '删除人';
COMMENT ON COLUMN cus_frequency.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_frequency.rate_code IS '频次聚合根实体';
COMMENT ON COLUMN cus_frequency.name IS '频次名称';
COMMENT ON COLUMN cus_frequency.cycle_type IS '周期类型（{ com.openhis.health.domain.share.enums.FrequencyCycleType#getCode()}）';
COMMENT ON COLUMN cus_frequency.day_count IS '日计数';
COMMENT ON COLUMN cus_frequency.day_interval IS '日间隔';
COMMENT ON COLUMN cus_frequency.day_times IS '日执行时点串';
COMMENT ON COLUMN cus_frequency.week_cycle_flag IS '周周期标记';
COMMENT ON COLUMN cus_frequency.total_execution_count IS '总执行次数';
COMMENT ON COLUMN cus_frequency.execution_period IS '执行周期';
COMMENT ON COLUMN cus_frequency.execution_period_unit IS '执行周期单位';
COMMENT ON COLUMN cus_frequency.applicable_scenes IS '适用场景';
COMMENT ON COLUMN cus_frequency.allow_cross_day IS '允许跨日（{ com.openhis.health.domain.share.enums.FrequencyAllowCrossDay#getCode()}）';
COMMENT ON COLUMN cus_frequency.active_flag IS '启用标记';
COMMENT ON COLUMN cus_frequency.sort_no IS '排序号';

-- seed: cus_frequency
INSERT INTO "cus_frequency" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "rate_code", "name", "cycle_type", "day_count", "day_interval", "day_times", "week_cycle_flag", "total_execution_count", "execution_period", "execution_period_unit", "applicable_scenes", "allow_cross_day", "active_flag", "sort_no")
VALUES
('320010001000000001', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'QD', '每日一次', 'daily', NULL, NULL, '08:00', NULL, NULL, NULL, NULL, 'all', 'same-day', true, 10),
('320010001000000002', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'BID', '每日两次', 'daily', NULL, NULL, '08:00,18:00', NULL, NULL, NULL, NULL, 'all', 'same-day', true, 20),
('320010001000000003', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'TID', '每日三次', 'daily', NULL, NULL, '08:00,12:00,18:00', NULL, NULL, NULL, NULL, 'all', 'same-day', true, 30),
('320010001000000004', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'QID', '每日四次', 'daily', NULL, NULL, '06:00,12:00,18:00,22:00', NULL, NULL, NULL, NULL, 'all', 'same-day', true, 40),
('320010001000000005', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'QAM', '每晨一次', 'daily', NULL, NULL, '08:00', NULL, NULL, NULL, NULL, 'all', 'same-day', true, 50),
('320010001000000006', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'QN', '每晚一次', 'daily', NULL, NULL, '22:00', NULL, NULL, NULL, NULL, 'all', 'same-day', true, 60),
('320010001000000007', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'HS', '临睡时', 'daily', NULL, NULL, '22:00', NULL, NULL, NULL, NULL, 'all', 'same-day', true, 70),
('320010001000000008', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'AC', '饭前', 'daily', NULL, NULL, '07:30,11:30,17:30', NULL, NULL, NULL, NULL, 'outpatient,inpatient', 'same-day', true, 80),
('320010001000000009', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'PC', '饭后', 'daily', NULL, NULL, '08:30,12:30,18:30', NULL, NULL, NULL, NULL, 'outpatient,inpatient', 'same-day', true, 90),
('320010001000000010', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'QOD', '隔日一次', 'daily', NULL, 2, '08:00', NULL, NULL, NULL, NULL, 'all', 'same-day', true, 100),
('320010001000000011', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'QW', '每周一次', 'weekly', NULL, NULL, '08:00', true, NULL, NULL, NULL, 'all', 'same-day', true, 110),
('320010001000000012', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'BIW', '每周两次', 'weekly', NULL, NULL, '08:00,18:00', true, NULL, NULL, NULL, 'inpatient', 'same-day', true, 120),
('320010001000000013', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'TIW', '每周三次', 'weekly', NULL, NULL, '08:00,12:00,18:00', true, NULL, NULL, NULL, 'inpatient', 'same-day', true, 130),
('320010001000000014', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'Q12H', '每12小时一次', 'interval', NULL, NULL, NULL, NULL, NULL, 12, 'h', 'all', 'cross-day', true, 140),
('320010001000000015', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'Q8H', '每8小时一次', 'interval', NULL, NULL, NULL, NULL, NULL, 8, 'h', 'all', 'cross-day', true, 150),
('320010001000000016', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'Q6H', '每6小时一次', 'interval', NULL, NULL, NULL, NULL, NULL, 6, 'h', 'inpatient,nursing', 'cross-day', true, 160),
('320010001000000017', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'Q4H', '每4小时一次', 'interval', NULL, NULL, NULL, NULL, NULL, 4, 'h', 'inpatient,nursing', 'cross-day', true, 170),
('320010001000000018', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'Q3H', '每3小时一次', 'interval', NULL, NULL, NULL, NULL, NULL, 3, 'h', 'inpatient,nursing', 'cross-day', true, 180),
('320010001000000019', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'Q2H', '每2小时一次', 'interval', NULL, NULL, NULL, NULL, NULL, 2, 'h', 'inpatient,nursing', 'cross-day', true, 190),
('320010001000000020', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'Q1H', '每小时一次', 'interval', NULL, NULL, NULL, NULL, NULL, 1, 'h', 'inpatient,nursing', 'cross-day', true, 200),
('320010001000000021', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:38:48.435+00', '1', '1', false, NULL, NULL, 0, 'PRN', '必要时', 'as-needed', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'all', 'same-day', true, 9),
('320010001000000022', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'SOS', '需要时', 'as-needed', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'all', NULL, true, 220),
('320010001000000023', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:39:05.116+00', '1', '1', false, NULL, NULL, 0, 'ST', '立即', 'fixed-count', NULL, NULL, NULL, NULL, 1, NULL, NULL, 'all', 'same-day', true, 8),
('320010001000000024', '1', '2026-06-08 11:37:51.131+00', '2026-06-08 11:37:51.131+00', '1', '1', false, NULL, NULL, 0, 'STAT', '立即执行', 'fixed-count', NULL, NULL, NULL, NULL, 1, NULL, NULL, 'inpatient', NULL, true, 240)
ON CONFLICT ("id") DO NOTHING;
