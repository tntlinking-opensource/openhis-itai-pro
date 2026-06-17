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

package com.openhis.health.application.inventory.warehouse.inventorydetail.dto;

import com.openhis.whale.core.application.annotation.Excel;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 现库导出 Excel 行（IF-INV-DTL-06 / BR-INV-DTL-012）；列序与表头由 {@link Excel} 注解声明。
 */
@Getter
@Setter
@Accessors(chain = true)
public class InventoryDetailCurrentExportRowDto {

    @Excel(name = "名称", sort = 1)
    private String name;

    @Excel(name = "批号", sort = 2)
    private String lotNumber;

    @Excel(name = "数量", sort = 3)
    private String quantityText;

    @Excel(name = "库房", sort = 4)
    private String warehouseName;

    @Excel(name = "供应商", sort = 5)
    private String supplierName;

    @Excel(name = "效期", sort = 6)
    private String expirationDate;

    @Excel(name = "供应状态", sort = 7)
    private String inventoryStatusText;

    @Excel(name = "进价总额", sort = 8, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal lineCostTotal;

    @Excel(name = "售价总额", sort = 9, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal linePriceTotal;
}
