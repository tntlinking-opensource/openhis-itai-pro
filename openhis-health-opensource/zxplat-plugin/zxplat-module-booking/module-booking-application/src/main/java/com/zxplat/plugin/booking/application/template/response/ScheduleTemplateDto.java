/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.template.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * T2 排班日程模板 DTO。
 */
@Getter
@Setter
public class ScheduleTemplateDto {

    /** 主键。 */
    private Long id;

    /** 科室主键。 */
    private Long organizationId;

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 模式 MODE-A/B/C。 */
    private String scheduleMode;

    /** 医师主键。 */
    private Long practitionerId;

    /** 诊室主键。 */
    private Long locationId;

    /** 号源类型主键。 */
    private Long healthcareServiceId;

    /** T1 模板主键。 */
    private Long slotProvisionTemplateId;

    /** 放号策略主键。 */
    private Long defaultReleasePolicyId;

    /** 生效起。 */
    private LocalDate effectiveStart;

    /** 生效止。 */
    private LocalDate effectiveEnd;

    /** 优先级。 */
    private Integer priority;

    /** 来源岗位主键。 */
    private Long sourcePractitionerRoleId;

    /** 是否启用。 */
    private Boolean enabled;
}
