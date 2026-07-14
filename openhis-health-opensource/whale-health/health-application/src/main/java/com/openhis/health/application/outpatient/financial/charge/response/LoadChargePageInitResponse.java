/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.response;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 收费页初始化响应。
 */
@Data
@Accessors(chain = true)
public class LoadChargePageInitResponse {

    /** 收费项状态下拉选项。 */
    private List<OptionDto> chargeItemStatusOptions;

    /** 支付渠道选项（477 ∩ PaymentChannel）。 */
    private List<OptionDto> paymentChannelOptions;
}
