/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.peis.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 小程序体检号源下单请求。
 */
@Getter
@Setter
public class MiniappCreateHcBookingRequest {

    /** 会话患者（可空，由 BFF 回填）。 */
    private Long patientId;

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 套餐主键。 */
    @NotNull
    private Long packageId;

    /** 时段配额主键。 */
    @NotNull
    private Long daySlotId;

    /** 合同主键（散客可空）。 */
    private Long contractId;

    /** 合同类型编码。 */
    private String contractTypeCode;
}
