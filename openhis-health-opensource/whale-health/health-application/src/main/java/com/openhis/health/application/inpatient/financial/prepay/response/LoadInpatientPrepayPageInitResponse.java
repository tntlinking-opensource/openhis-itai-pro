/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.prepay.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 住院预交金页初始化响应（IF-IP-PREP-00）。 */
@Data
@Accessors(chain = true)
@Schema(description = "住院预交金页初始化响应")
public class LoadInpatientPrepayPageInitResponse {

    /** 当前用户可选的支付渠道选项（ADR-PAY-001 语义码 ∩ 477 active）。 */
    @Schema(description = "支付渠道下拉选项")
    private List<OptionDto> paymentChannelOptions;
}
