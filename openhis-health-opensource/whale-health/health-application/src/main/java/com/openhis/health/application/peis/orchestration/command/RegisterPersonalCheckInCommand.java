/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.orchestration.command;

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
     * 体检类型：freshman / annual / staff / other；空则按 Campus 身份与是否首检推断。
     */
    private String examKind;
}
