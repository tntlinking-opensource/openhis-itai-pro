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

import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.whale.core.application.annotation.Excel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 现库导出 Excel 行（IF-INV-DTL-06）；列序与 openhis {@code ProductDetailPageDto} 对齐（不含货位）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class InventoryDetailCurrentExportRowDto {

    @Excel(name = "药品编码", sort = 1)
    private String productBusNo;

    @Excel(name = "项目名称", sort = 2)
    private String name;

    @Excel(name = "商品名称", sort = 3)
    private String merchandiseName;

    @Excel(name = "项目类别", sort = 4)
    private String categoryLabel;

    @Excel(name = "仓库", sort = 5)
    private String warehouseName;

    @Excel(name = "规格", sort = 6)
    private String totalVolume;

    @Excel(name = "厂家/产地", sort = 7)
    private String manufacturer;

    @Excel(name = "生产批号", sort = 8)
    private String lotNumber;

    @Excel(name = "库存（包装单位）", sort = 9)
    private String packageStockText;

    @Excel(name = "库存（最小单位）", sort = 10)
    private String splitStockText;

    @Excel(name = "拆零换算比", sort = 11, scale = 0)
    private BigDecimal packageUnitRatio;

    @Excel(name = "采购价格", sort = 12, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal lotCostPrice;

    @Excel(name = "零售价", sort = 13, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal lotSalePrice;

    @Excel(name = "进价总额", sort = 14, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal lineCostTotal;

    @Excel(name = "零售价总额", sort = 15, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal linePriceTotal;

    @Excel(name = "剂型", sort = 16)
    private String doseFormLabel;

    @Excel(name = "国药准字", sort = 17)
    private String approvalNumber;

    @Excel(name = "供应商", sort = 18)
    private String supplierName;

    @Excel(name = "医保等级", sort = 19)
    private String chrgitmLvLabel;

    @Excel(name = "生产日期", sort = 20)
    private String productionDate;

    @Excel(name = "有效期至", sort = 21)
    private String expirationDate;

    @Excel(name = "剩余过期天数", sort = 22)
    private Integer remainingDays;
}
