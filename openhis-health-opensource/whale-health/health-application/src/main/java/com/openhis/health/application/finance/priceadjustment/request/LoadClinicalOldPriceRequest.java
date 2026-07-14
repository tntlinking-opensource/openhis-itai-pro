/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 诊疗调前售价加载请求（IF-PRC-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "诊疗调前售价加载请求", requiredProperties = {"itemTable", "itemId"})
public class LoadClinicalOldPriceRequest {

    /** 项目物理表。 */
    @NotBlank(message = "项目表不能为空")
    @Schema(description = "项目物理表", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    /** 项目定义主键。 */
    @NotNull(message = "项目标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "项目定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;
}
