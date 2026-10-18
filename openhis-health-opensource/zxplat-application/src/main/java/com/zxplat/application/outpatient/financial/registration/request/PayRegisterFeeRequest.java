/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.request;

import com.zxplat.application.outpatient.financial.registration.dto.PaymentDetailDto;
import com.zxplat.domain.infrastructure.integration.payment.dto.ClientScanResultDto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 挂号支付请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号支付请求")
public class PayRegisterFeeRequest {

    /** 支付草稿ID（业务字段）。 */
    @Schema(description = "支付草稿ID")
    private Long paymentDraftId;

    /** 支付明细列表。 */
    @Schema(description = "支付明细")
    private List<PaymentDetailDto> paymentDetails;

    /** 关联预约主键（缴费成功后回填 encounter）。 */
    @Schema(description = "预约ID")
    private Long appointmentId;

    /** 银联被扫 Win 回传（扫码 settle 时必填）。 */
    @Schema(description = "银联被扫 Win 回传")
    private ClientScanResultDto clientScanResult;
}
