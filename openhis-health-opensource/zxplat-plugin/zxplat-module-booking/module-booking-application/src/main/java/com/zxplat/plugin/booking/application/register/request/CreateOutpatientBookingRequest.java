/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建门诊预约请求（4.2 · 支持 virtualSlotKey lazy 物化）。
 */
@Getter
@Setter
public class CreateOutpatientBookingRequest {

    /** 患者主键。 */
    @NotNull
    private Long patientId;

    /** 号源主键（与 virtualSlotKey 二选一）。 */
    private Long slotId;

    /** 周模板虚拟号源键（fallback_bookable lazy 物化）。 */
    private String virtualSlotKey;

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 去向诊室（可选，缺省时从 Schedule LOCATION 参与者解析）。 */
    private Long destinationLocationId;

    /** 预约渠道（可选，默认 WINDOW）。 */
    private String originChannel;
}
