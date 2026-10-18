-- Squashed bkg DDL (V2–V7/V9)

-- from V2__Booking_Policy.sql
-- =============================================================================
-- V2 · 院点策略
-- 放号策略、挂号衔接策略、停诊与预约处置策略（07 §4.2）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- bkg_release_policy · 放号策略
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_release_policy (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    policy_code VARCHAR(64) NOT NULL,
    policy_name VARCHAR(128) NOT NULL,
    advance_days INT NOT NULL DEFAULT 7,
    slot_minutes INT NOT NULL DEFAULT 30,
    capacity_per_slot INT NOT NULL DEFAULT 10,
    channel_quota_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_registration_policy (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    walk_in_slot_mode VARCHAR(32) NOT NULL DEFAULT 'WALK_IN_WITH_SLOT',
    auto_check_in_on_register BOOLEAN NOT NULL DEFAULT FALSE,
    on_cancel_policy VARCHAR(32) NOT NULL DEFAULT 'RELEASE_SLOT_ONLY',
    allow_edit_practitioner_from_appointment BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_schedule_change_policy (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    on_practitioner_stop_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    transfer_match_rule VARCHAR(32) NOT NULL DEFAULT 'SAME_SERVICE_SAME_SLOT',
    transfer_require_same_service BOOLEAN NOT NULL DEFAULT TRUE,
    transfer_allow_cross_practitioner BOOLEAN NOT NULL DEFAULT TRUE,
    transfer_patient_confirm_mode VARCHAR(32) NOT NULL DEFAULT 'NONE',
    block_deactivate_if_unhandled BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_transfer BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_cancel_stop BOOLEAN NOT NULL DEFAULT TRUE,
    freeze_source_slot_on_preview BOOLEAN NOT NULL DEFAULT TRUE,
    freeze_ttl_minutes INT NOT NULL DEFAULT 15,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bkg_schedule_change_policy_org
    ON bkg_schedule_change_policy (organization_id)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE bkg_release_policy IS '放号策略：提前天数、时段粒度、每段容量、渠道配额';
COMMENT ON COLUMN bkg_release_policy.id IS '主键';
COMMENT ON COLUMN bkg_release_policy.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_release_policy.organization_id IS '机构标识；空=租户默认';
COMMENT ON COLUMN bkg_release_policy.policy_code IS '策略编码（机构内唯一）';
COMMENT ON COLUMN bkg_release_policy.policy_name IS '策略名称';
COMMENT ON COLUMN bkg_release_policy.advance_days IS '可预约提前天数';
COMMENT ON COLUMN bkg_release_policy.slot_minutes IS '默认时段粒度（分钟）';
COMMENT ON COLUMN bkg_release_policy.capacity_per_slot IS '默认每时段容量';
COMMENT ON COLUMN bkg_release_policy.channel_quota_json IS '渠道配额 JSON，如 {"WINDOW":60,"APP":40}';
COMMENT ON COLUMN bkg_release_policy.created_at IS '创建时间';
COMMENT ON COLUMN bkg_release_policy.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_release_policy.created_by IS '创建人';
COMMENT ON COLUMN bkg_release_policy.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_release_policy.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_release_policy.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_release_policy.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_release_policy.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_registration_policy IS '挂号衔接策略：现场取号、取消处置、医师变更等';
COMMENT ON COLUMN bkg_registration_policy.id IS '主键';
COMMENT ON COLUMN bkg_registration_policy.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_registration_policy.organization_id IS '机构标识；空=租户默认';
COMMENT ON COLUMN bkg_registration_policy.walk_in_slot_mode IS '现场取号模式：WALK_IN_WITH_SLOT（默认）/ APPOINTMENT_FIRST / WALK_IN_NO_SLOT';
COMMENT ON COLUMN bkg_registration_policy.auto_check_in_on_register IS '挂号时是否自动签到';
COMMENT ON COLUMN bkg_registration_policy.on_cancel_policy IS '取消预约处置：RELEASE_SLOT_ONLY / KEEP_SLOT 等';
COMMENT ON COLUMN bkg_registration_policy.allow_edit_practitioner_from_appointment IS '是否允许从预约修改接诊医师';
COMMENT ON COLUMN bkg_registration_policy.created_at IS '创建时间';
COMMENT ON COLUMN bkg_registration_policy.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_registration_policy.created_by IS '创建人';
COMMENT ON COLUMN bkg_registration_policy.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_registration_policy.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_registration_policy.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_registration_policy.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_registration_policy.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_schedule_change_policy IS '停诊与预约处置院点策略（07 §4.2）';
COMMENT ON COLUMN bkg_schedule_change_policy.id IS '主键';
COMMENT ON COLUMN bkg_schedule_change_policy.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_schedule_change_policy.organization_id IS '机构标识；空=租户默认';
COMMENT ON COLUMN bkg_schedule_change_policy.on_practitioner_stop_mode IS '停诊默认处置：TRANSFER / RESCHEDULE / CANCEL / MANUAL';
COMMENT ON COLUMN bkg_schedule_change_policy.transfer_match_rule IS '转交匹配规则';
COMMENT ON COLUMN bkg_schedule_change_policy.transfer_require_same_service IS '转交是否要求同号源类型';
COMMENT ON COLUMN bkg_schedule_change_policy.transfer_allow_cross_practitioner IS '是否允许跨医师转交';
COMMENT ON COLUMN bkg_schedule_change_policy.transfer_patient_confirm_mode IS '转交患者确认：NONE / SMS_CONFIRM / WINDOW_CONFIRM';
COMMENT ON COLUMN bkg_schedule_change_policy.block_deactivate_if_unhandled IS '存在未处置预约时禁止完成停诊';
COMMENT ON COLUMN bkg_schedule_change_policy.notify_on_transfer IS '转交后是否通知患者';
COMMENT ON COLUMN bkg_schedule_change_policy.notify_on_cancel_stop IS '停诊取消预约时是否通知';
COMMENT ON COLUMN bkg_schedule_change_policy.freeze_source_slot_on_preview IS '停诊预览后冻结源号源，防并发新约';
COMMENT ON COLUMN bkg_schedule_change_policy.freeze_ttl_minutes IS '冻结超时分钟数（TTL 自动释放）';
COMMENT ON COLUMN bkg_schedule_change_policy.created_at IS '创建时间';
COMMENT ON COLUMN bkg_schedule_change_policy.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_schedule_change_policy.created_by IS '创建人';
COMMENT ON COLUMN bkg_schedule_change_policy.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_schedule_change_policy.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_schedule_change_policy.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_schedule_change_policy.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_schedule_change_policy.version IS '乐观锁版本号';

-- from V3__Booking_Business_Ext.sql
-- =============================================================================
-- V3 · P1 业务扩展
-- 医技资源映射、床位预留、体检套餐与团检
-- =============================================================================

-- ---------------------------------------------------------------------------
-- bkg_diagnostic_resource_map · 医技资源映射
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_diagnostic_resource_map (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    activity_definition_id BIGINT,
    service_category_code VARCHAR(64),
    schedule_id BIGINT,
    location_id BIGINT,
    device_id BIGINT,
    resource_name VARCHAR(128),
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_bed_reservation_ext (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    appointment_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    patient_id BIGINT,
    ward_location_id BIGINT,
    intended_admission_date DATE,
    remark VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_health_check_package (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    package_code VARCHAR(64) NOT NULL,
    package_name VARCHAR(128) NOT NULL,
    applicable_gender VARCHAR(16),
    applicable_age_min INT,
    applicable_age_max INT,
    estimated_amount NUMERIC(12,2),
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_health_check_package_item (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    package_id BIGINT NOT NULL,
    activity_definition_id BIGINT,
    item_name VARCHAR(128),
    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_group_checkup_org (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    org_code VARCHAR(64) NOT NULL,
    org_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    settlement_mode VARCHAR(32),
    contract_year VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_group_checkup_batch (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    org_id BIGINT NOT NULL,
    batch_name VARCHAR(128) NOT NULL,
    package_id BIGINT,
    schedule_id BIGINT,
    booking_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    capacity_limit INT NOT NULL DEFAULT 0,
    booked_count INT NOT NULL DEFAULT 0,
    status_code VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_group_checkup_roster (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    batch_id BIGINT NOT NULL,
    patient_id BIGINT,
    full_name VARCHAR(64) NOT NULL,
    id_card_no VARCHAR(32),
    student_no VARCHAR(32),
    mobile_phone VARCHAR(32),
    appointment_id BIGINT,
    booking_status_code VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    import_status_code VARCHAR(32) NOT NULL DEFAULT 'IMPORTED',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_health_check_booking_ext (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    appointment_id BIGINT NOT NULL,
    package_id BIGINT,
    batch_id BIGINT,
    package_snapshot_json TEXT,
    estimated_amount NUMERIC(12,2),
    contract_id BIGINT,
    contract_type_code VARCHAR(32),
    campus_code VARCHAR(64),
    day_slot_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);
COMMENT ON TABLE bkg_diagnostic_resource_map IS '医技预约：检查项目与 Schedule/设备/诊室映射';
COMMENT ON COLUMN bkg_diagnostic_resource_map.id IS '主键';
COMMENT ON COLUMN bkg_diagnostic_resource_map.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_diagnostic_resource_map.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_diagnostic_resource_map.activity_definition_id IS '检查项目（ActivityDefinition）主键';
COMMENT ON COLUMN bkg_diagnostic_resource_map.service_category_code IS '服务类别编码';
COMMENT ON COLUMN bkg_diagnostic_resource_map.schedule_id IS '关联号源日程主键';
COMMENT ON COLUMN bkg_diagnostic_resource_map.location_id IS '执行地点主键';
COMMENT ON COLUMN bkg_diagnostic_resource_map.device_id IS '设备主键';
COMMENT ON COLUMN bkg_diagnostic_resource_map.resource_name IS '展示名称';
COMMENT ON COLUMN bkg_diagnostic_resource_map.active_flag IS '是否启用';
COMMENT ON COLUMN bkg_diagnostic_resource_map.created_at IS '创建时间';
COMMENT ON COLUMN bkg_diagnostic_resource_map.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_diagnostic_resource_map.created_by IS '创建人';
COMMENT ON COLUMN bkg_diagnostic_resource_map.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_diagnostic_resource_map.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_diagnostic_resource_map.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_diagnostic_resource_map.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_diagnostic_resource_map.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_bed_reservation_ext IS '床位预约扩展：关联 Appointment，不占用 Slot';
COMMENT ON COLUMN bkg_bed_reservation_ext.id IS '主键';
COMMENT ON COLUMN bkg_bed_reservation_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_bed_reservation_ext.appointment_id IS '关联预约主键（wkf_appointment_queue）';
COMMENT ON COLUMN bkg_bed_reservation_ext.location_id IS '床位/房间 Location 主键';
COMMENT ON COLUMN bkg_bed_reservation_ext.patient_id IS '患者主键';
COMMENT ON COLUMN bkg_bed_reservation_ext.ward_location_id IS '病区 Location 主键';
COMMENT ON COLUMN bkg_bed_reservation_ext.intended_admission_date IS '拟入院日期';
COMMENT ON COLUMN bkg_bed_reservation_ext.remark IS '备注';
COMMENT ON COLUMN bkg_bed_reservation_ext.created_at IS '创建时间';
COMMENT ON COLUMN bkg_bed_reservation_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_bed_reservation_ext.created_by IS '创建人';
COMMENT ON COLUMN bkg_bed_reservation_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_bed_reservation_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_bed_reservation_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_bed_reservation_ext.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_bed_reservation_ext.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_health_check_package IS '体检套餐定义（个检/团检共用）';
COMMENT ON COLUMN bkg_health_check_package.id IS '主键';
COMMENT ON COLUMN bkg_health_check_package.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_health_check_package.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_health_check_package.package_code IS '套餐编码';
COMMENT ON COLUMN bkg_health_check_package.package_name IS '套餐名称';
COMMENT ON COLUMN bkg_health_check_package.applicable_gender IS '适用性别：M/F/ALL';
COMMENT ON COLUMN bkg_health_check_package.applicable_age_min IS '适用年龄下限';
COMMENT ON COLUMN bkg_health_check_package.applicable_age_max IS '适用年龄上限';
COMMENT ON COLUMN bkg_health_check_package.estimated_amount IS '参考金额';
COMMENT ON COLUMN bkg_health_check_package.active_flag IS '是否启用';
COMMENT ON COLUMN bkg_health_check_package.created_at IS '创建时间';
COMMENT ON COLUMN bkg_health_check_package.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_health_check_package.created_by IS '创建人';
COMMENT ON COLUMN bkg_health_check_package.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_health_check_package.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_health_check_package.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_health_check_package.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_health_check_package.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_health_check_package_item IS '体检套餐检查项目明细';
COMMENT ON COLUMN bkg_health_check_package_item.id IS '主键';
COMMENT ON COLUMN bkg_health_check_package_item.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_health_check_package_item.package_id IS '所属套餐主键';
COMMENT ON COLUMN bkg_health_check_package_item.activity_definition_id IS '检查项目主键';
COMMENT ON COLUMN bkg_health_check_package_item.item_name IS '项目名称（冗余展示）';
COMMENT ON COLUMN bkg_health_check_package_item.required_flag IS '是否必检';
COMMENT ON COLUMN bkg_health_check_package_item.sort_order IS '排序号';
COMMENT ON COLUMN bkg_health_check_package_item.created_at IS '创建时间';
COMMENT ON COLUMN bkg_health_check_package_item.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_health_check_package_item.created_by IS '创建人';
COMMENT ON COLUMN bkg_health_check_package_item.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_health_check_package_item.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_health_check_package_item.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_health_check_package_item.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_health_check_package_item.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_group_checkup_org IS '团检签约单位';
COMMENT ON COLUMN bkg_group_checkup_org.id IS '主键';
COMMENT ON COLUMN bkg_group_checkup_org.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_group_checkup_org.org_code IS '单位编码';
COMMENT ON COLUMN bkg_group_checkup_org.org_name IS '单位名称';
COMMENT ON COLUMN bkg_group_checkup_org.contact_name IS '联系人';
COMMENT ON COLUMN bkg_group_checkup_org.contact_phone IS '联系电话';
COMMENT ON COLUMN bkg_group_checkup_org.settlement_mode IS '结算方式';
COMMENT ON COLUMN bkg_group_checkup_org.contract_year IS '合同年度';
COMMENT ON COLUMN bkg_group_checkup_org.created_at IS '创建时间';
COMMENT ON COLUMN bkg_group_checkup_org.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_group_checkup_org.created_by IS '创建人';
COMMENT ON COLUMN bkg_group_checkup_org.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_group_checkup_org.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_group_checkup_org.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_group_checkup_org.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_group_checkup_org.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_group_checkup_batch IS '团检批次：日期、容量、关联 Schedule';
COMMENT ON COLUMN bkg_group_checkup_batch.id IS '主键';
COMMENT ON COLUMN bkg_group_checkup_batch.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_group_checkup_batch.org_id IS '团检单位主键';
COMMENT ON COLUMN bkg_group_checkup_batch.batch_name IS '批次名称';
COMMENT ON COLUMN bkg_group_checkup_batch.package_id IS '默认套餐主键';
COMMENT ON COLUMN bkg_group_checkup_batch.schedule_id IS '关联号源日程';
COMMENT ON COLUMN bkg_group_checkup_batch.booking_date IS '体检日期';
COMMENT ON COLUMN bkg_group_checkup_batch.start_time IS '时段开始';
COMMENT ON COLUMN bkg_group_checkup_batch.end_time IS '时段结束';
COMMENT ON COLUMN bkg_group_checkup_batch.capacity_limit IS '容量上限';
COMMENT ON COLUMN bkg_group_checkup_batch.booked_count IS '已预约人数';
COMMENT ON COLUMN bkg_group_checkup_batch.status_code IS '状态：DRAFT / OPEN / CLOSED 等';
COMMENT ON COLUMN bkg_group_checkup_batch.created_at IS '创建时间';
COMMENT ON COLUMN bkg_group_checkup_batch.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_group_checkup_batch.created_by IS '创建人';
COMMENT ON COLUMN bkg_group_checkup_batch.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_group_checkup_batch.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_group_checkup_batch.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_group_checkup_batch.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_group_checkup_batch.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_group_checkup_roster IS '团检人员名单及预约状态';
COMMENT ON COLUMN bkg_group_checkup_roster.id IS '主键';
COMMENT ON COLUMN bkg_group_checkup_roster.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_group_checkup_roster.batch_id IS '所属批次主键';
COMMENT ON COLUMN bkg_group_checkup_roster.patient_id IS '患者主键（建档后回填）';
COMMENT ON COLUMN bkg_group_checkup_roster.full_name IS '姓名';
COMMENT ON COLUMN bkg_group_checkup_roster.id_card_no IS '身份证号';
COMMENT ON COLUMN bkg_group_checkup_roster.student_no IS '学号/工号';
COMMENT ON COLUMN bkg_group_checkup_roster.mobile_phone IS '手机号';
COMMENT ON COLUMN bkg_group_checkup_roster.appointment_id IS '关联预约主键';
COMMENT ON COLUMN bkg_group_checkup_roster.booking_status_code IS '预约状态：PENDING / BOOKED / CANCELLED 等';
COMMENT ON COLUMN bkg_group_checkup_roster.import_status_code IS '导入状态';
COMMENT ON COLUMN bkg_group_checkup_roster.created_at IS '创建时间';
COMMENT ON COLUMN bkg_group_checkup_roster.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_group_checkup_roster.created_by IS '创建人';
COMMENT ON COLUMN bkg_group_checkup_roster.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_group_checkup_roster.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_group_checkup_roster.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_group_checkup_roster.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_group_checkup_roster.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_health_check_booking_ext IS '体检预约扩展：套餐快照、金额';
COMMENT ON COLUMN bkg_health_check_booking_ext.id IS '主键';
COMMENT ON COLUMN bkg_health_check_booking_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_health_check_booking_ext.appointment_id IS '关联预约主键';
COMMENT ON COLUMN bkg_health_check_booking_ext.package_id IS '套餐主键';
COMMENT ON COLUMN bkg_health_check_booking_ext.batch_id IS '团检批次主键（个检为空）';
COMMENT ON COLUMN bkg_health_check_booking_ext.package_snapshot_json IS '下单时套餐快照 JSON';
COMMENT ON COLUMN bkg_health_check_booking_ext.estimated_amount IS '参考金额';
COMMENT ON COLUMN bkg_health_check_booking_ext.contract_id IS '团检合同主键；散客可空';
COMMENT ON COLUMN bkg_health_check_booking_ext.contract_type_code IS '进池合同类型编码';
COMMENT ON COLUMN bkg_health_check_booking_ext.campus_code IS '校区编码；无校区模块可空';
COMMENT ON COLUMN bkg_health_check_booking_ext.day_slot_id IS '占用的 bkg_hc_day_slot 主键';
COMMENT ON COLUMN bkg_health_check_booking_ext.created_at IS '创建时间';
COMMENT ON COLUMN bkg_health_check_booking_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_health_check_booking_ext.created_by IS '创建人';
COMMENT ON COLUMN bkg_health_check_booking_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_health_check_booking_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_health_check_booking_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_health_check_booking_ext.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_health_check_booking_ext.version IS '乐观锁版本号';

-- from V4__Booking_Channel_Queue.sql
-- =============================================================================
-- V4 · 渠道、叫号与资源画像
-- =============================================================================

-- ---------------------------------------------------------------------------
-- bkg_channel_config · 预约渠道配置
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_channel_config (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    channel_code VARCHAR(32) NOT NULL,
    channel_name VARCHAR(64) NOT NULL,
    enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
    quota_percent INT NOT NULL DEFAULT 100,
    notify_template_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_queue_display_config (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    location_id BIGINT,
    display_title VARCHAR(128),
    refresh_seconds INT NOT NULL DEFAULT 5,
    voice_enabled_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bkg_resource_profile (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    resource_type_code VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    profile_name VARCHAR(128),
    profile_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);
COMMENT ON TABLE bkg_channel_config IS '预约渠道：窗口、APP、第三方等配额与通知模板';
COMMENT ON COLUMN bkg_channel_config.id IS '主键';
COMMENT ON COLUMN bkg_channel_config.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_channel_config.organization_id IS '机构标识；空=租户默认';
COMMENT ON COLUMN bkg_channel_config.channel_code IS '渠道编码：WINDOW / APP / WECHAT 等';
COMMENT ON COLUMN bkg_channel_config.channel_name IS '渠道名称';
COMMENT ON COLUMN bkg_channel_config.enabled_flag IS '是否启用';
COMMENT ON COLUMN bkg_channel_config.quota_percent IS '号源配额百分比（0-100）';
COMMENT ON COLUMN bkg_channel_config.notify_template_code IS '通知模板编码';
COMMENT ON COLUMN bkg_channel_config.created_at IS '创建时间';
COMMENT ON COLUMN bkg_channel_config.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_channel_config.created_by IS '创建人';
COMMENT ON COLUMN bkg_channel_config.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_channel_config.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_channel_config.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_channel_config.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_channel_config.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_queue_display_config IS '候诊叫号大屏：刷新间隔、语音播报';
COMMENT ON COLUMN bkg_queue_display_config.id IS '主键';
COMMENT ON COLUMN bkg_queue_display_config.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_queue_display_config.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_queue_display_config.location_id IS '候诊区 Location 主键';
COMMENT ON COLUMN bkg_queue_display_config.display_title IS '大屏标题';
COMMENT ON COLUMN bkg_queue_display_config.refresh_seconds IS '数据刷新间隔（秒）';
COMMENT ON COLUMN bkg_queue_display_config.voice_enabled_flag IS '是否启用语音叫号';
COMMENT ON COLUMN bkg_queue_display_config.created_at IS '创建时间';
COMMENT ON COLUMN bkg_queue_display_config.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_queue_display_config.created_by IS '创建人';
COMMENT ON COLUMN bkg_queue_display_config.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_queue_display_config.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_queue_display_config.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_queue_display_config.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_queue_display_config.version IS '乐观锁版本号';

COMMENT ON TABLE bkg_resource_profile IS '预约资源扩展画像（医师/设备/诊室等 JSON 属性）';
COMMENT ON COLUMN bkg_resource_profile.id IS '主键';
COMMENT ON COLUMN bkg_resource_profile.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_resource_profile.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_resource_profile.resource_type_code IS '资源类型：PRACTITIONER / DEVICE / LOCATION 等';
COMMENT ON COLUMN bkg_resource_profile.resource_id IS '资源主键';
COMMENT ON COLUMN bkg_resource_profile.profile_name IS '画像名称';
COMMENT ON COLUMN bkg_resource_profile.profile_json IS '扩展属性 JSON';
COMMENT ON COLUMN bkg_resource_profile.created_at IS '创建时间';
COMMENT ON COLUMN bkg_resource_profile.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_resource_profile.created_by IS '创建人';
COMMENT ON COLUMN bkg_resource_profile.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_resource_profile.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_resource_profile.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_resource_profile.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_resource_profile.version IS '乐观锁版本号';

-- from V5__Booking_Schedule_Template.sql
-- =============================================================================
-- V5 · 排班模板与日历规则
-- T1 放号时段、T2 日模板、T3 日历例外、TW 周模板、停诊预览冻结
-- 详见 docs/模块/zxplat-module-booking/07-门诊排班业务规则与模板.md
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

-- from V7__Booking_Waitlist.sql
-- =============================================================================
-- V7 · 候补队列（BR-EXT-011）
-- 号源满时入候补；释放号源后按 queue_position 自动提升为正式预约
-- =============================================================================

CREATE TABLE IF NOT EXISTS bkg_waitlist_entry (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    queue_position INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    promoted_appointment_id BIGINT,
    cancel_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_waitlist_slot_status ON bkg_waitlist_entry (slot_id, status)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_waitlist_patient ON bkg_waitlist_entry (patient_id, status)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE bkg_waitlist_entry IS '号源候补队列：满号时不占 slot，释放后按序号提升';
COMMENT ON COLUMN bkg_waitlist_entry.id IS '主键';
COMMENT ON COLUMN bkg_waitlist_entry.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_waitlist_entry.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_waitlist_entry.patient_id IS '患者主键';
COMMENT ON COLUMN bkg_waitlist_entry.slot_id IS '候补号源主键';
COMMENT ON COLUMN bkg_waitlist_entry.schedule_id IS '所属日程主键';
COMMENT ON COLUMN bkg_waitlist_entry.queue_position IS '同 slot 下排队序号，从 1 递增';
COMMENT ON COLUMN bkg_waitlist_entry.status IS '状态';
COMMENT ON COLUMN bkg_waitlist_entry.promoted_appointment_id IS '提升为正式预约后关联 appointmentId';
COMMENT ON COLUMN bkg_waitlist_entry.cancel_reason IS '取消原因';
COMMENT ON COLUMN bkg_waitlist_entry.created_at IS '创建时间';
COMMENT ON COLUMN bkg_waitlist_entry.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_waitlist_entry.created_by IS '创建人';
COMMENT ON COLUMN bkg_waitlist_entry.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_waitlist_entry.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_waitlist_entry.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_waitlist_entry.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_waitlist_entry.version IS '乐观锁版本号';

-- from V9__Bed_Reservation_Unique.sql
-- =============================================================================
-- V9 · 床位预留同日同床唯一（GAP-BKG-003 / GAP-ATD-007）
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_bkg_bed_reservation_ext_bed_date_active
    ON bkg_bed_reservation_ext (location_id, intended_admission_date)
    WHERE is_deleted = false;

-- =============================================================================
-- folded from V3__health_checkup_slot_calendar.sql（终态含 V6 partial unique）
-- =============================================================================

CREATE TABLE IF NOT EXISTS bkg_hc_slot_template (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    slot_minutes INT,
    am_start TIME,
    am_end TIME,
    pm_start TIME,
    pm_end TIME,
    capacity_json TEXT,
    rest_weekdays VARCHAR(32),
    campus_codes_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bkg_hc_slot_template_org
    ON bkg_hc_slot_template (organization_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_hc_slot_template IS '体检号源时段模板（按机构记住上次配置）';
COMMENT ON COLUMN bkg_hc_slot_template.capacity_json IS '各合同类型默认容量 JSON，如 {"STUDENT":0,"STAFF":0}；默认全 0';
COMMENT ON COLUMN bkg_hc_slot_template.campus_codes_json IS '生成时展开的校区编码列表 JSON；无校区模块可空';
COMMENT ON COLUMN bkg_hc_slot_template.rest_weekdays IS '模板休息日 ISO 1-7 逗号分隔';

CREATE TABLE IF NOT EXISTS bkg_hc_day_plan (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    plan_date DATE NOT NULL,
    rest_flag BOOLEAN NOT NULL DEFAULT FALSE,
    remark VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bkg_hc_day_plan_org_date
    ON bkg_hc_day_plan (organization_id, plan_date)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_hc_day_plan IS '体检号源日计划';
COMMENT ON COLUMN bkg_hc_day_plan.rest_flag IS '整日休息时所有类型不可约';

CREATE TABLE IF NOT EXISTS bkg_hc_day_type (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    day_plan_id BIGINT NOT NULL,
    contract_type_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bkg_hc_day_type_plan_code
    ON bkg_hc_day_type (day_plan_id, contract_type_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_hc_day_type IS '日开放合同类型（含散客 WALK_IN）';

CREATE TABLE IF NOT EXISTS bkg_hc_day_slot (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    day_plan_id BIGINT NOT NULL,
    plan_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    contract_type_code VARCHAR(32) NOT NULL,
    campus_code VARCHAR(64),
    capacity INT NOT NULL DEFAULT 0,
    booked_count INT NOT NULL DEFAULT 0,
    open_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bkg_hc_day_slot_pool
    ON bkg_hc_day_slot (organization_id, plan_date, start_time, end_time, contract_type_code, COALESCE(campus_code, ''))
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_bkg_hc_day_slot_org_date
    ON bkg_hc_day_slot (organization_id, plan_date)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_hc_day_slot IS '日时段配额：类型×校区分池；capacity=0 不可约';
COMMENT ON COLUMN bkg_hc_day_slot.booked_count IS '已约人数（真实占号累计）';
COMMENT ON COLUMN bkg_hc_day_slot.open_flag IS '档位是否开放';
