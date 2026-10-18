/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交调价审批响应（IF-PRC-10）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交调价审批响应")
public class SubmitPriceAdjustmentResponse {

    /** 调价单号。 */
    @Schema(description = "调价单号")
    private String busNo;

    /** 审批状态 code。 */
    @Schema(description = "审批状态 code")
    private String statusEnum;
}
