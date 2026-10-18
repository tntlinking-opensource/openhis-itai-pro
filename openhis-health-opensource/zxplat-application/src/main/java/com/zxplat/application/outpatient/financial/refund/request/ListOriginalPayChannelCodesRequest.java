/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.refund.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按原支付主单查询落库渠道码（退费前判定是否可能银联原路退）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询原支付渠道码请求")
public class ListOriginalPayChannelCodesRequest {

    /** 原支付主单主键。 */
    @NotNull(message = "paymentId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "原支付主单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long paymentId;
}
