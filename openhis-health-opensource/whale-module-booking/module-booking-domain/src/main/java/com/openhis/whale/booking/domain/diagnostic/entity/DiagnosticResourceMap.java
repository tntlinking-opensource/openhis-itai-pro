/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.diagnostic.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "bkg_diagnostic_resource_map")
public class DiagnosticResourceMap extends BaseAuditedEntity {

    /** 机构标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 检查项目（ActivityDefinition）主键。 */
    @Column(name = "activity_definition_id")
    private Long activityDefinitionId;

    /** 服务类别编码。 */
    @Column(name = "service_category_code")
    private String serviceCategoryCode;

    /** 关联号源日程主键。 */
    @Column(name = "schedule_id")
    private Long scheduleId;

    /** 执行地点主键。 */
    @Column(name = "location_id")
    private Long locationId;

    /** 设备主键。 */
    @Column(name = "device_id")
    private Long deviceId;

    /** 展示名称。 */
    @Column(name = "resource_name")
    private String resourceName;

    /** 是否启用。 */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag = Boolean.TRUE;
}
