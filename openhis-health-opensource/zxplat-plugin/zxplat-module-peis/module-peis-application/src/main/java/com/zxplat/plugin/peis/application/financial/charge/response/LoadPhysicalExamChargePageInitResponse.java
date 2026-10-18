/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 体检收费页初始化响应（IF-PE-CHG-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检收费页初始化响应")
public class LoadPhysicalExamChargePageInitResponse {

    /** 收费项状态下拉。 */
    @Schema(description = "收费项状态下拉")
    private List<OptionDto> chargeItemStatusOptions;

    /** 支付渠道下拉。 */
    @Schema(description = "支付渠道下拉")
    private List<OptionDto> paymentChannelOptions;
}
