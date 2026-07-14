/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustmentdetail.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 查看调价项目详情请求（IF-PRC-DTL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查看调价项目详情请求", requiredProperties = {"id"})
public class GetPriceAdjustmentApprovedLineRequest {

    /** 调价项目主行主键。 */
    @NotNull(message = "调价行主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "调价项目主行主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
}
