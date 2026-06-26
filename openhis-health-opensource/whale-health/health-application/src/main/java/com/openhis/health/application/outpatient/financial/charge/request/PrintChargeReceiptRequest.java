/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.charge.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印/补打门诊收费小票请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印门诊收费小票请求")
public class PrintChargeReceiptRequest {

    @Schema(description = "就诊ID")
    private Long encounterId;

    @Schema(description = "支付流水ID（已结算收费时传入，用于回补支付明细与收费项）")
    private Long paymentId;
}
