/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.dto;

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

    /** 药品编码。 */
    @Excel(name = "药品编码", sort = 1)
    private String productBusNo;

    /** 项目名称。 */
    @Excel(name = "项目名称", sort = 2)
    private String name;

    /** 商品名称。 */
    @Excel(name = "商品名称", sort = 3)
    private String merchandiseName;

    /** 项目类别。 */
    @Excel(name = "项目类别", sort = 4)
    private String categoryLabel;

    /** 仓库。 */
    @Excel(name = "仓库", sort = 5)
    private String warehouseName;

    /** 规格。 */
    @Excel(name = "规格", sort = 6)
    private String totalVolume;

    /** 厂家/产地。 */
    @Excel(name = "厂家/产地", sort = 7)
    private String manufacturer;

    /** 生产批号。 */
    @Excel(name = "生产批号", sort = 8)
    private String lotNumber;

    /** 库存（包装单位）。 */
    @Excel(name = "库存（包装单位）", sort = 9)
    private String packageStockText;

    /** 库存（最小单位）。 */
    @Excel(name = "库存（最小单位）", sort = 10)
    private String splitStockText;

    /** 拆零换算比。 */
    @Excel(name = "拆零换算比", sort = 11, scale = 0)
    private BigDecimal packageUnitRatio;

    /** 采购价格。 */
    @Excel(name = "采购价格", sort = 12, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal lotCostPrice;

    /** 零售价。 */
    @Excel(name = "零售价", sort = 13, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal lotSalePrice;

    /** 进价总额。 */
    @Excel(name = "进价总额", sort = 14, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal lineCostTotal;

    /** 零售价总额。 */
    @Excel(name = "零售价总额", sort = 15, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE)
    private BigDecimal linePriceTotal;

    /** 剂型。 */
    @Excel(name = "剂型", sort = 16)
    private String doseFormLabel;

    /** 国药准字。 */
    @Excel(name = "国药准字", sort = 17)
    private String approvalNumber;

    /** 供应商。 */
    @Excel(name = "供应商", sort = 18)
    private String supplierName;

    /** 医保等级。 */
    @Excel(name = "医保等级", sort = 19)
    private String chrgitmLvLabel;

    /** 生产日期。 */
    @Excel(name = "生产日期", sort = 20)
    private String productionDate;

    /** 有效期至。 */
    @Excel(name = "有效期至", sort = 21)
    private String expirationDate;

    /** 剩余过期天数。 */
    @Excel(name = "剩余过期天数", sort = 22)
    private Integer remainingDays;
}
