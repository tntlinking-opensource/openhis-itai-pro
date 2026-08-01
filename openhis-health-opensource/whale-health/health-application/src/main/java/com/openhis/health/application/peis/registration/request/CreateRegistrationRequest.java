/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.request;

import lombok.Data;

/**
 * 创建个检登记请求（REG-02）。
 */
@Data
public class CreateRegistrationRequest {

    /** 患者主键。 */
    private Long patientId;

    /** 套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;

    /** 体检中心机构主键。 */
    private Long orgId;

    /** 预约主键（可选）。 */
    private Long bookingAppointmentId;

    /**
     * 体检类型：freshman（新生）/ annual（在校年检）/ staff（教职工）/ other；
     * 空则后端按 Campus 身份与是否首检推断。
     */
    private String examKind;
}
