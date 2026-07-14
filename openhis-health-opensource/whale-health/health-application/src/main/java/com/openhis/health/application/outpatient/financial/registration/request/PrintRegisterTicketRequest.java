/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 补打挂号单请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "补打挂号单请求")
public class PrintRegisterTicketRequest {

    /** 就诊ID（业务字段）。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 挂号单号。 */
    @Schema(description = "挂号单号")
    private String slipNo;

    /** 打印份数。 */
    @Schema(description = "打印份数")
    private Integer printCopies;

    /** 支付流水主键（已结算挂号时传入，用于回补支付明细）。 */
    @Schema(description = "支付流水ID")
    private Long paymentId;
}
