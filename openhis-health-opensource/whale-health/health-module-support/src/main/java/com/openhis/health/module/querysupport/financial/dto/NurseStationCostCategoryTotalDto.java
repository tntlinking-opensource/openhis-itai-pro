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

/**
 * 费用分类净额小计 DTO（IF-IP-CDT-02 合计区）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "费用分类净额小计")
public class NurseStationCostCategoryTotalDto {

    /** 分类 code。 */
    @Schema(description = "分类 code")
    private String categoryCode;

    /** 分类展示名。 */
    @Schema(description = "分类展示名")
    private String categoryDisplay;

    /** 该分类净额合计（BR-IP-CDT-010）。 */
    @Schema(description = "分类净额")
    private BigDecimal amount;
}
