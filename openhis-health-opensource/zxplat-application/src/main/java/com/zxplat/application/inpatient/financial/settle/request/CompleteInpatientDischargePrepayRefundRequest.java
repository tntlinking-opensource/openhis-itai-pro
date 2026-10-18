/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.financial.settle.dto.InpatientSettleClientRefundItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医保出院第二阶段：预交金原路退完成后完结就诊。
 *
 * <p>仅当确认结算返回 {@code prepayRefundPending=true} 时调用。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医保出院预交金原路退完结请求", requiredProperties = {
    "encounterId", "patientId"
})
public class CompleteInpatientDischargePrepayRefundRequest {

    /** 住院就诊 ID。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID。 */
    @NotNull(message = "患者标识不能为空")
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 按原收款单挂接的 Win 退货回传。 */
    @Schema(description = "医保出院预交金原路退 Win 回传列表")
    private List<InpatientSettleClientRefundItemDto> clientRefundResults;
}
