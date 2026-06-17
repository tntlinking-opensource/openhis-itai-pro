-- 用户登录后会话：租户 / 医院 / 科室选择（跨进程重启持久化）
CREATE TABLE IF NOT EXISTS whale_user_login_session (
    user_id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    hospital_organization_id BIGINT,
    organization_unit_id BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_whale_user_login_session_user FOREIGN KEY (user_id) REFERENCES whale_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_whale_user_login_session_tenant
    ON whale_user_login_session(tenant_id);

COMMENT ON TABLE whale_user_login_session IS '用户登录后的服务端会话：租户 / 医院 / 科室选择（跨重启持久化）';

COMMENT ON COLUMN whale_user_login_session.user_id IS 'user id';
COMMENT ON COLUMN whale_user_login_session.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_user_login_session.hospital_organization_id IS 'hospital organization id';
COMMENT ON COLUMN whale_user_login_session.organization_unit_id IS 'organization unit id';
COMMENT ON COLUMN whale_user_login_session.updated_at IS '最后更新时间';

