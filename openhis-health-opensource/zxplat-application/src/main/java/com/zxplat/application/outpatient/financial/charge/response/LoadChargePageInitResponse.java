/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.response;

import com.zxplat.framework.core.application.dto.OptionDto;
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

    /**
     * 是否已配置有效折扣内容（BR-OPC-027）：≥1 条未删 ACTIVE {@code cus_charge_discount_config}。
     * 配置本身即开关。false 时 UI 不展示折扣率/折扣列。
     */
    private Boolean discountFeatureEnabled;
}
