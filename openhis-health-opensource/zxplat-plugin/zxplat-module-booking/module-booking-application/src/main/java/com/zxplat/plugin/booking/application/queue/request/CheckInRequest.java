/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 报到入队请求（5.1）。
 */
@Getter
@Setter
public class CheckInRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 去向诊室（现场报到必填；预约报到可覆盖）。 */
    private Long destinationLocationId;

    /** 预约主键（预约患者报到时传）。 */
    private Long appointmentId;

    /** 预约单号（与 appointmentId 二选一）。 */
    private String appointmentBusNo;

    /** 就诊主键（现场报到 CHECK_IN_ONLY 时传）。 */
    private Long encounterId;

    /** 患者主键（现场报到时传）。 */
    private Long patientId;

    /** 渠道编码（现场报到可选）。 */
    private String originChannel;
}
