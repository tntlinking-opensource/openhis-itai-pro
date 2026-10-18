/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.orchestration.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 个检登记编排命令。
 */
@Getter
@Setter
public class RegisterPersonalCheckInCommand {

    /** 患者主键。 */
    private Long patientId;

    /** 套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;

    /** 体检中心机构主键。 */
    private Long orgId;

    /** 预约主键（可选）。 */
    private Long bookingAppointmentId;

    /** 操作人 practitioner 主键。 */
    private Long operatorId;

    /**
     * 体检类型：personal / group / freshman / annual / staff / other；
     * 空则默认 personal。
     */
    private String examKind;

    /** 团检单位（examKind=group 时必填）。 */
    private Long companyId;

    /** 团检分组/批次（examKind=group 时必填）。 */
    private Long groupBatchId;

    /** 本次体检推荐人。 */
    private String referrerName;

    /** 本次体检是否提供早餐。 */
    private Boolean breakfastFlag;
}
