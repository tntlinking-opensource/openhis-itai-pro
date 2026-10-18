/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.template.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * T2 排班日程模板（bkg_schedule_template，07 §5.3）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_schedule_template")
public class ScheduleTemplate extends BaseAuditedEntity {

    /** 科室主键。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 模板编码。 */
    @Column(name = "template_code", nullable = false)
    private String templateCode;

    /** 模板名称。 */
    @Column(name = "template_name", nullable = false)
    private String templateName;

    /** 模式：MODE-A/B/C。 */
    @Column(name = "schedule_mode", nullable = false)
    private String scheduleMode = "MODE-A";

    /** 医师主键（可选）。 */
    @Column(name = "practitioner_id")
    private Long practitionerId;

    /** 诊室主键（可选）。 */
    @Column(name = "location_id")
    private Long locationId;

    /** 号源类型主键。 */
    @Column(name = "healthcare_service_id")
    private Long healthcareServiceId;

    /** 关联 T1 放号时段模板。 */
    @Column(name = "slot_provision_template_id")
    private Long slotProvisionTemplateId;

    /** 默认放号策略。 */
    @Column(name = "default_release_policy_id")
    private Long defaultReleasePolicyId;

    /** 生效起。 */
    @Column(name = "effective_start")
    private LocalDate effectiveStart;

    /** 生效止。 */
    @Column(name = "effective_end")
    private LocalDate effectiveEnd;

    /** 优先级（高优先）。 */
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    /** 来源岗位主键（导入追溯）。 */
    @Column(name = "source_practitioner_role_id")
    private Long sourcePractitionerRoleId;

    /** 是否启用。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
