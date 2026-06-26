/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.charge.response;

import com.openhis.health.application.outpatient.financial.charge.dto.ChargeReceiptSlipDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印门诊收费小票响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印门诊收费小票响应")
public class PrintChargeReceiptResponse {

    @Schema(description = "收费小票数据")
    private ChargeReceiptSlipDto slip;
}
