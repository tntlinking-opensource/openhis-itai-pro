/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.request;

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
     * 体检类型：personal（个人）/ group（团体）/ freshman（新生）/ annual（在校年检）/
     * staff（教职工）/ other；空则默认 personal。
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
