/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 费用明细合计块 DTO（IF-IP-CDT-02；明细/汇总两种模式均返回，净额口径 BR-IP-CDT-010）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院护士站费用合计块")
public class NurseStationCostDetailTotalsDto {

    /** 药品类净额。 */
    @Schema(description = "药品类净额")
    private BigDecimal medicationTotal;

    /** 诊疗类净额。 */
    @Schema(description = "诊疗类净额")
    private BigDecimal serviceTotal;

    /** 耗材类净额。 */
    @Schema(description = "耗材类净额")
    private BigDecimal deviceTotal;

    /** 按 category_code 净额小计。 */
    @Schema(description = "分类净额小计列表")
    private List<NurseStationCostCategoryTotalDto> categoryTotals;

    /** 总合计净额。 */
    @Schema(description = "总合计净额")
    private BigDecimal grandTotal;
}
