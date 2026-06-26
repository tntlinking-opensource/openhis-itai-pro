-- V92：定时任务执行历史（Quartz 标准表 whale_qrtz_fired_triggers 仅保留「正在跑」的瞬时行，跑完即删，无法存成功/失败历史）

CREATE TABLE IF NOT EXISTS whale_qrtz_job_execution_log (
    id BIGINT PRIMARY KEY,
    sched_name VARCHAR(120) NOT NULL DEFAULT 'WhaleScheduler',
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    job_bean_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    tenant_id BIGINT,
    execution_type VARCHAR(16) NOT NULL,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    duration_ms BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_job_exec_log_finished
    ON whale_qrtz_job_execution_log (finished_at DESC);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_job_exec_log_tenant
    ON whale_qrtz_job_execution_log (tenant_id);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_job_exec_log_job_key
    ON whale_qrtz_job_execution_log (sched_name, job_group, job_name);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_job_exec_log_success
    ON whale_qrtz_job_execution_log (success);
COMMENT ON TABLE whale_qrtz_job_execution_log IS 'Whale 扩展：定时任务执行历史（非 Quartz 内置 fired_triggers 替代品）';
COMMENT ON COLUMN whale_qrtz_job_execution_log.id IS '主键';
COMMENT ON COLUMN whale_qrtz_job_execution_log.sched_name IS '调度名称';
COMMENT ON COLUMN whale_qrtz_job_execution_log.job_name IS '任务名称';
COMMENT ON COLUMN whale_qrtz_job_execution_log.job_group IS '任务分组';
COMMENT ON COLUMN whale_qrtz_job_execution_log.job_bean_name IS '任务Bean名称';
COMMENT ON COLUMN whale_qrtz_job_execution_log.display_name IS '显示名称';
COMMENT ON COLUMN whale_qrtz_job_execution_log.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_qrtz_job_execution_log.execution_type IS '执行类型';
COMMENT ON COLUMN whale_qrtz_job_execution_log.success IS '成功';
COMMENT ON COLUMN whale_qrtz_job_execution_log.error_message IS '错误消息';
COMMENT ON COLUMN whale_qrtz_job_execution_log.duration_ms IS '耗时ms';
COMMENT ON COLUMN whale_qrtz_job_execution_log.started_at IS '开始时间';
COMMENT ON COLUMN whale_qrtz_job_execution_log.finished_at IS '结束时间';
