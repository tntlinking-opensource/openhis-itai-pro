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

-- ---------------------------------------------------------------------------
-- bkg_bed_reservation_ext · 床位预留扩展
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- bkg_health_check_package · 体检套餐
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- bkg_health_check_package_item · 体检套餐明细
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- bkg_group_checkup_org · 团检单位
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- bkg_group_checkup_batch · 团检批次
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- bkg_group_checkup_roster · 团检名单
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- bkg_health_check_booking_ext · 个检/团检预约扩展
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_health_check_booking_ext (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    appointment_id BIGINT NOT NULL,
    package_id BIGINT,
    batch_id BIGINT,
    package_snapshot_json TEXT,
    estimated_amount NUMERIC(12,2),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE bkg_health_check_booking_ext IS '体检预约扩展：套餐快照、金额';
COMMENT ON COLUMN bkg_health_check_booking_ext.id IS '主键';
COMMENT ON COLUMN bkg_health_check_booking_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_health_check_booking_ext.appointment_id IS '关联预约主键';
COMMENT ON COLUMN bkg_health_check_booking_ext.package_id IS '套餐主键';
COMMENT ON COLUMN bkg_health_check_booking_ext.batch_id IS '团检批次主键（个检为空）';
COMMENT ON COLUMN bkg_health_check_booking_ext.package_snapshot_json IS '下单时套餐快照 JSON';
COMMENT ON COLUMN bkg_health_check_booking_ext.estimated_amount IS '参考金额';
COMMENT ON COLUMN bkg_health_check_booking_ext.created_at IS '创建时间';
COMMENT ON COLUMN bkg_health_check_booking_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_health_check_booking_ext.created_by IS '创建人';
COMMENT ON COLUMN bkg_health_check_booking_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_health_check_booking_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_health_check_booking_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_health_check_booking_ext.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_health_check_booking_ext.version IS '乐观锁版本号';
