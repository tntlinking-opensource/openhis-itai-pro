/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.infrastructure.integration.payment.dto.ClientRefundResultDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医保出院确认结算：按原预交金收款单挂接的 Win 退货回传。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算预交金原路退 Win 回传项")
public class InpatientSettleClientRefundItemDto {

    /** 原预交金收款支付单 ID。 */
    @Schema(description = "原预交金收款支付单 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalPaymentId;

    /** 银联退货 Win 回传。 */
    @Schema(description = "银联退货 Win 回传")
    private ClientRefundResultDto clientRefundResult;
}
