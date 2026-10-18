/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.refund.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 执行前可退门禁请求（IF-REF-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "执行前可退门禁请求")
public class VerifyRefundableBeforeExecutionRequest {

    /** 就诊主键。 */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 拟退收费明细主键集合。 */
    @NotEmpty(message = "refundingChargeItemIds 不能为空")
    /** 拟退收费明细主键集合。 */
    @Schema(description = "拟退收费明细主键集合", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> refundingChargeItemIds;
}
