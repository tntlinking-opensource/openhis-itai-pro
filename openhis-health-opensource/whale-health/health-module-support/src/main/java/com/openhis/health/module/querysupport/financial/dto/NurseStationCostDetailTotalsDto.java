/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.querysupport.financial.dto;

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
