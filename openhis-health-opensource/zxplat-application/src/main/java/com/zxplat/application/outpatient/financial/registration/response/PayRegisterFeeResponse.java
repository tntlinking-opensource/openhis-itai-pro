/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 挂号支付响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号支付响应")
public class PayRegisterFeeResponse {

    /** 支付ID（业务字段）。 */
    @Schema(description = "支付ID")
    private Long paymentId;

    /** 发票 ID。 */
    @Schema(description = "发票ID")
    private Long invoiceId;

    /** 支付状态。 */
    @Schema(description = "状态")
    private String status;

    /** 票据状态（DRAFT/ISSUED/CANCELLED）。 */
    @Schema(description = "票据状态")
    private String invoiceStatus;

    /** 就诊主键（挂号预结算/缴费后可用于补打小票）。 */
    @Schema(description = "就诊ID")
    private Long encounterId;
}
