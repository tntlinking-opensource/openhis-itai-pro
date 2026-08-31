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
 * 备份库存导出 Excel 行（IF-INV-DTL-07）；列序与现库导出同构（不含货位）。
 *
 * <p>包装/最小库存数量与各自单位分列，禁止合并「数量+单位」单一文本列。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class InventoryDetailBackupExportRowDto {

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

    /** 库存数量（包装单位，满包整数；可求和）。 */
    @Excel(name = "库存数量（包装）", sort = 9, scale = 0)
    private BigDecimal packageQuantity;

    /** 包装单位名称（对应包装库存数量）。 */
    @Excel(name = "包装单位", sort = 10)
    private String packageUnitName;

    /** 库存数量（最小/拆零单位；可求和）。 */
    @Excel(name = "库存数量（最小）", sort = 11, scale = 0)
    private BigDecimal minUnitQuantity;

    /** 最小单位名称（对应最小库存数量）。 */
    @Excel(name = "最小单位", sort = 12)
    private String minUnitName;

    /** 拆零换算比。 */
    @Excel(name = "拆零换算比", sort = 13, scale = 0)
    private BigDecimal packageUnitRatio;

    /** 采购价格。 */
    @Excel(name = "采购价格", sort = 14, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal lotCostPrice;

    /** 零售价。 */
    @Excel(name = "零售价", sort = 15, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal lotSalePrice;

    /** 进价总额。 */
    @Excel(name = "进价总额", sort = 16, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal lineCostTotal;

    /** 零售价总额。 */
    @Excel(name = "零售价总额", sort = 17, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal linePriceTotal;

    /** 剂型。 */
    @Excel(name = "剂型", sort = 18)
    private String doseFormLabel;

    /** 国药准字。 */
    @Excel(name = "国药准字", sort = 19)
    private String approvalNumber;

    /** 供应商。 */
    @Excel(name = "供应商", sort = 20)
    private String supplierName;

    /** 医保等级。 */
    @Excel(name = "医保等级", sort = 21)
    private String chrgitmLvLabel;

    /** 生产日期。 */
    @Excel(name = "生产日期", sort = 22)
    private String productionDate;

    /** 有效期至。 */
    @Excel(name = "有效期至", sort = 23)
    private String expirationDate;

    /** 剩余过期天数。 */
    @Excel(name = "剩余过期天数", sort = 24)
    private Integer remainingDays;
}
