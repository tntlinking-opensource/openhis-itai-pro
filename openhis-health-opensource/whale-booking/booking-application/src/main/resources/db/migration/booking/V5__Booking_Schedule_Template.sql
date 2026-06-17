-- =============================================================================
-- V5 · 排班模板与日历规则
-- T1 放号时段、T2 日模板、T3 日历例外、TW 周模板、停诊预览冻结
-- 详见 whale-doc/booking/07-门诊排班业务规则与模板.md
-- =============================================================================

-- ---------------------------------------------------------------------------
-- bkg_slot_provision_template · T1 放号时段模板
-- 一行 JSON 表达多时段（含午间休息）；capacity 可覆盖放号策略默认值
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_slot_provision_template (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    slot_templates_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bkg_slot_provision_tpl_org
    ON bkg_slot_provision_template (organization_id, template_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_slot_provision_template IS 'T1 放号时段模板（07 §5.2）';
COMMENT ON COLUMN bkg_slot_provision_template.id IS '主键';
COMMENT ON COLUMN bkg_slot_provision_template.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_slot_provision_template.organization_id IS '机构/科室；空=全院';
COMMENT ON COLUMN bkg_slot_provision_template.template_code IS '模板编码';
COMMENT ON COLUMN bkg_slot_provision_template.template_name IS '模板名称';
COMMENT ON COLUMN bkg_slot_provision_template.slot_templates_json IS '时段 JSON：[{startTime,endTime,capacity}]';
COMMENT ON COLUMN bkg_slot_provision_template.enabled IS '是否启用';
COMMENT ON COLUMN bkg_slot_provision_template.created_at IS '创建时间';
COMMENT ON COLUMN bkg_slot_provision_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_slot_provision_template.created_by IS '创建人';
COMMENT ON COLUMN bkg_slot_provision_template.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_slot_provision_template.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_slot_provision_template.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_slot_provision_template.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_slot_provision_template.version IS '乐观锁版本号';

-- ---------------------------------------------------------------------------
-- bkg_schedule_template · T2 排班日模板
-- 关联 T1、默认放号策略；用于 generateFromTemplate(DAY) 与 TW 明细引用
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_schedule_template (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    schedule_mode VARCHAR(16) NOT NULL DEFAULT 'MODE-A',
    practitioner_id BIGINT,
    location_id BIGINT,
    healthcare_service_id BIGINT,
    slot_provision_template_id BIGINT,
    default_release_policy_id BIGINT,
    effective_start DATE,
    effective_end DATE,
    priority INT NOT NULL DEFAULT 0,
    source_practitioner_role_id BIGINT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bkg_schedule_tpl_org
    ON bkg_schedule_template (organization_id, template_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_schedule_template IS 'T2 排班日模板（07 §5.3）';
COMMENT ON COLUMN bkg_schedule_template.id IS '主键';
COMMENT ON COLUMN bkg_schedule_template.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_schedule_template.organization_id IS '机构/科室；空=全院';
COMMENT ON COLUMN bkg_schedule_template.template_code IS '模板编码';
COMMENT ON COLUMN bkg_schedule_template.template_name IS '模板名称';
COMMENT ON COLUMN bkg_schedule_template.schedule_mode IS '排班模式：MODE-A / MODE-B / MODE-C';
COMMENT ON COLUMN bkg_schedule_template.practitioner_id IS '默认医师主键（可选）';
COMMENT ON COLUMN bkg_schedule_template.location_id IS '默认诊室主键（可选）';
COMMENT ON COLUMN bkg_schedule_template.healthcare_service_id IS '号源类型主键';
COMMENT ON COLUMN bkg_schedule_template.slot_provision_template_id IS '关联 T1 放号时段模板';
COMMENT ON COLUMN bkg_schedule_template.default_release_policy_id IS '默认放号策略主键';
COMMENT ON COLUMN bkg_schedule_template.effective_start IS '生效日期起';
COMMENT ON COLUMN bkg_schedule_template.effective_end IS '生效日期止';
COMMENT ON COLUMN bkg_schedule_template.priority IS '匹配优先级（数值越大越优先）';
COMMENT ON COLUMN bkg_schedule_template.source_practitioner_role_id IS '来源岗位主键（导入追溯）';
COMMENT ON COLUMN bkg_schedule_template.enabled IS '是否启用';
COMMENT ON COLUMN bkg_schedule_template.created_at IS '创建时间';
COMMENT ON COLUMN bkg_schedule_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_schedule_template.created_by IS '创建人';
COMMENT ON COLUMN bkg_schedule_template.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_schedule_template.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_schedule_template.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_schedule_template.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_schedule_template.version IS '乐观锁版本号';

-- ---------------------------------------------------------------------------
-- bkg_calendar_exception · T3 日历例外
-- 节假日、调休、临时停诊或覆盖日模板
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_calendar_exception (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    exception_date DATE NOT NULL,
    range_end DATE,
    exception_type VARCHAR(16) NOT NULL,
    override_schedule_template_id BIGINT,
    reason VARCHAR(255),
    auto_apply_on_generate BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bkg_calendar_exc_org_date
    ON bkg_calendar_exception (organization_id, exception_date, range_end)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_calendar_exception IS 'T3 日历例外（07 §5.4）';
COMMENT ON COLUMN bkg_calendar_exception.id IS '主键';
COMMENT ON COLUMN bkg_calendar_exception.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_calendar_exception.organization_id IS '机构/科室；空=全院';
COMMENT ON COLUMN bkg_calendar_exception.exception_date IS '例外日期或区间起点';
COMMENT ON COLUMN bkg_calendar_exception.range_end IS '区间终点；空=单日';
COMMENT ON COLUMN bkg_calendar_exception.exception_type IS '类型：CLOSED / REDUCED / OVERRIDE / EXTRA';
COMMENT ON COLUMN bkg_calendar_exception.override_schedule_template_id IS 'REDUCED/OVERRIDE/EXTRA 时关联 T2';
COMMENT ON COLUMN bkg_calendar_exception.reason IS '原因说明';
COMMENT ON COLUMN bkg_calendar_exception.auto_apply_on_generate IS '从模板生成 Schedule 时是否自动应用';
COMMENT ON COLUMN bkg_calendar_exception.created_at IS '创建时间';
COMMENT ON COLUMN bkg_calendar_exception.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_calendar_exception.created_by IS '创建人';
COMMENT ON COLUMN bkg_calendar_exception.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_calendar_exception.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_calendar_exception.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_calendar_exception.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_calendar_exception.version IS '乐观锁版本号';

-- ---------------------------------------------------------------------------
-- bkg_week_schedule_template · TW 周排班模板
-- 科室默认周模板 + fallback_bookable：无 Schedule 时周历展示与虚拟可约
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_week_schedule_template (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    fallback_bookable BOOLEAN NOT NULL DEFAULT FALSE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    effective_start DATE,
    effective_end DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_week_schedule_template_item (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    week_schedule_template_id BIGINT NOT NULL,
    weekday INTEGER NOT NULL,
    day_schedule_template_id BIGINT NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bkg_week_sched_tpl_org
    ON bkg_week_schedule_template (organization_id, template_code)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_bkg_week_sched_tpl_org_default
    ON bkg_week_schedule_template (organization_id, is_default)
    WHERE is_deleted = FALSE AND is_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_bkg_week_sched_tpl_item_week
    ON bkg_week_schedule_template_item (week_schedule_template_id, weekday, sort_no)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_week_schedule_template IS 'TW 周排班模板（07 §5.3b / §5.7）';
COMMENT ON COLUMN bkg_week_schedule_template.id IS '主键';
COMMENT ON COLUMN bkg_week_schedule_template.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_week_schedule_template.organization_id IS '机构/科室；空=全院';
COMMENT ON COLUMN bkg_week_schedule_template.template_code IS '模板编码';
COMMENT ON COLUMN bkg_week_schedule_template.template_name IS '模板名称';
COMMENT ON COLUMN bkg_week_schedule_template.fallback_bookable IS '无 Schedule 时模板态是否允许预约（虚拟可约）';
COMMENT ON COLUMN bkg_week_schedule_template.is_default IS '科室默认周模板（同科室仅一条 is_default=true）';
COMMENT ON COLUMN bkg_week_schedule_template.enabled IS '是否启用';
COMMENT ON COLUMN bkg_week_schedule_template.effective_start IS '生效日期起';
COMMENT ON COLUMN bkg_week_schedule_template.effective_end IS '生效日期止';
COMMENT ON COLUMN bkg_week_schedule_template.created_at IS '创建时间';
COMMENT ON COLUMN bkg_week_schedule_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_week_schedule_template.created_by IS '创建人';
COMMENT ON COLUMN bkg_week_schedule_template.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_week_schedule_template.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_week_schedule_template.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_week_schedule_template.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_week_schedule_template.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_week_schedule_template_item IS 'TW 明细：weekday → T2 日模板';
COMMENT ON COLUMN bkg_week_schedule_template_item.id IS '主键';
COMMENT ON COLUMN bkg_week_schedule_template_item.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_week_schedule_template_item.week_schedule_template_id IS '所属 TW 主键';
COMMENT ON COLUMN bkg_week_schedule_template_item.weekday IS '星期：1=周一 … 7=周日（ISO）';
COMMENT ON COLUMN bkg_week_schedule_template_item.day_schedule_template_id IS '关联 bkg_schedule_template（T2）';
COMMENT ON COLUMN bkg_week_schedule_template_item.sort_no IS '同 weekday 多行时的排序';
COMMENT ON COLUMN bkg_week_schedule_template_item.enabled IS '是否启用';
COMMENT ON COLUMN bkg_week_schedule_template_item.created_at IS '创建时间';
COMMENT ON COLUMN bkg_week_schedule_template_item.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_week_schedule_template_item.created_by IS '创建人';
COMMENT ON COLUMN bkg_week_schedule_template_item.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_week_schedule_template_item.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_week_schedule_template_item.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_week_schedule_template_item.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_week_schedule_template_item.version IS '乐观锁版本号';

-- ---------------------------------------------------------------------------
-- bkg_stop_clinic_slot_freeze · 停诊预览源号源冻结
-- 停诊向导预览期间锁定源 Slot，TTL 到期自动释放（07 §4.2 BR-EXT-003/004）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_stop_clinic_slot_freeze (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bkg_stop_clinic_slot_freeze_slot
    ON bkg_stop_clinic_slot_freeze (slot_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_bkg_stop_clinic_slot_freeze_schedule
    ON bkg_stop_clinic_slot_freeze (schedule_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_bkg_stop_clinic_slot_freeze_expires
    ON bkg_stop_clinic_slot_freeze (expires_at)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_stop_clinic_slot_freeze IS '停诊预览期间源号源冻结（防并发新约）';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.id IS '主键';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.schedule_id IS '被停诊日程主键';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.slot_id IS '被冻结的源号源主键';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.expires_at IS '冻结过期时间（TTL 自动释放）';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.created_at IS '创建时间';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.created_by IS '创建人';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_stop_clinic_slot_freeze.version IS '乐观锁版本号';
