/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 诊疗调前售价加载响应（IF-PRC-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "诊疗调前售价加载响应")
public class LoadClinicalOldPriceResponse {

    /** 建议调前售价。 */
    @Schema(description = "建议调前售价")
    private BigDecimal oldSellingAmount;
}
