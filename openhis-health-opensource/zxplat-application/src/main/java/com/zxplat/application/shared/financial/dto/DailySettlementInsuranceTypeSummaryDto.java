/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 日结下半区：医保合同下按 {@code yb_settlement_record.insutype} 汇总的子块。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结险种类型汇总")
public class DailySettlementInsuranceTypeSummaryDto {

    /** 险种类型码（平台 insutype）。 */
    @Schema(description = "险种类型码")
    private String insutype;

    /** 险种展示名（1901 字典；未命中时回落码值）。 */
    @Schema(description = "险种展示名")
    private String insutypeName;

    /** 人次（patientId 去重）。 */
    @Schema(description = "人次")
    private Integer personCount;

    /** 现金。 */
    @Schema(description = "现金")
    private String cashAmount;

    /** 统筹。 */
    @Schema(description = "医保统筹")
    private String fundAmount;

    /** 个账。 */
    @Schema(description = "个账")
    private String accountAmount;

    /** 基金（统筹口径合计）。 */
    @Schema(description = "基金")
    private String fundTotalAmount;

    /** 总额（统筹+个账）。 */
    @Schema(description = "总额")
    private String totalAmount;
}
