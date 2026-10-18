/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.refund.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊退费页初始化响应（IF-REF-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊退费页初始化响应")
public class LoadOutpatientRefundPageInitResponse {

    /** 收费项状态筛选项（业务字段）。 */
    @Schema(description = "收费项状态筛选项")
    private List<OptionDto> chargeItemStatusOptions;

    /** 当前用户可选的支付渠道选项（ADR-PAY-001 语义码 ∩ 477 active）。 */
    @Schema(description = "支付渠道下拉选项")
    private List<OptionDto> paymentChannelOptions;
}
