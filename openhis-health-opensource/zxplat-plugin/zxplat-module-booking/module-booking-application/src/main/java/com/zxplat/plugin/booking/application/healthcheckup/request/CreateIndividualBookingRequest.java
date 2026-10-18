/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建个检预约请求（6.x 旧版 API 别名，对应 CreateIndividualHealthCheckupBookingRequest）。
 */
@Getter
@Setter
public class CreateIndividualBookingRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 患者主键。 */
    @NotNull
    private Long patientId;

    /** 体检套餐主键。 */
    @NotNull
    private Long packageId;

    /** 号源主键。 */
    @NotNull
    private Long slotId;

    /** 预约来源渠道。 */
    private String originChannel;
}
