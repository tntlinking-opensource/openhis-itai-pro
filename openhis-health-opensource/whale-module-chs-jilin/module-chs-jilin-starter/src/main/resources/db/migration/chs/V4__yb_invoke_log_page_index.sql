-- 医保调用日志分页：租户 + 时间倒序（列表默认筛时间窗）
CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_tenant_created
    ON yb_invoke_log (tenant_id, created_at DESC)
    WHERE is_deleted = FALSE;

-- 按交易编号 + 时间筛选
CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_tenant_infno_created
    ON yb_invoke_log (tenant_id, infno, created_at DESC)
    WHERE is_deleted = FALSE;
