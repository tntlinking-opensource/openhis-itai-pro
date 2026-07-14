/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 价格调整页初始化响应（IF-PRC-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "价格调整页初始化响应")
public class InitPriceAdjustmentResponse {

    /** 调价原因下拉。 */
    @Schema(description = "调价原因下拉")
    private List<OptionDto> adjustmentReasonOptions;

    /** 审批状态筛选项。 */
    @Schema(description = "审批状态筛选项")
    private List<OptionDto> statusFilterOptions;

    /** 项目表 Tab 选项。 */
    @Schema(description = "项目表 Tab 选项")
    private List<OptionDto> itemTableTabOptions;

    /** 医嘱定价来源策略。 */
    @Schema(description = "医嘱定价来源策略")
    private String orderPricingSource;
}
