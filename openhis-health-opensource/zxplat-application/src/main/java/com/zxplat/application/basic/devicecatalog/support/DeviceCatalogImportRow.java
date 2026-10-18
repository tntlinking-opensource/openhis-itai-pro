/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.devicecatalog.support;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 耗材目录导入内部行模型（模板 v1.0，32 列）。
 *
 * <p>术语列先存中文/原始文本，校验成功后由 {@link DeviceCatalogImportRowValidator} 回填 code 与数值/布尔。</p>
 */
@Getter
@Setter
public class DeviceCatalogImportRow {

    /** Excel 1-based 行号。 */
    private Integer excelRowNum;

    // —— A 基础信息（原始） ——
    /** 耗材名称。 */
    private String name;
    /** 商品名。 */
    private String merchandiseName;
    /** 耗材分类（中文或码）。 */
    private String category;
    /** 耗材种类（中文或码）。 */
    private String deviceType;
    /** 生产厂家名称。 */
    private String manufacturerText;
    /** 财务类型（中文或码）。 */
    private String finType;
    /** 包装规格。 */
    private String totalVolume;
    /** 批准文号。 */
    private String approvalNumber;
    /** 医保编码。 */
    private String ybNo;
    /** 医保等级原文。 */
    private String chrgitmLvText;
    /** 病案结算类型原文。 */
    private String mrSettlementType;
    /** 医保对码是/否原文。 */
    private String ybMatchFlagText;
    /** 医保类别原文。 */
    private String ybType;

    // —— B 库存与单位（原始） ——
    /** 采购单位原文。 */
    private String purchaseUnit;
    /** 拆零单位原文。 */
    private String splitUnit;
    /** 采购/拆零倍数原文。 */
    private String purchaseUnitRatioText;
    /** 包装单位原文。 */
    private String packageUnit;
    /** 包装/拆零倍数原文。 */
    private String packageUnitRatioText;
    /** 库存单位原文。 */
    private String stockUnit;
    /** 库存/拆零倍数原文。 */
    private String stockUnitRatioText;
    /** 购入价原文。 */
    private String purchasePriceText;
    /** 零售价原文。 */
    private String retailPriceText;
    /** 最高零售价原文。 */
    private String maximumRetailPriceText;
    /** 警戒线最低值原文。 */
    private String itemMinQuantityText;
    /** 警戒线最高值原文。 */
    private String itemMaxQuantityText;

    // —— C 业务信息（原始） ——
    /** 院内旧编码。 */
    private String legacyCode;
    /** 型号。 */
    private String modelNumber;
    /** 过敏标记原文。 */
    private String allergenFlagText;
    /** 医保标记原文。 */
    private String ybFlagText;
    /** 高值耗材标志原文。 */
    private String hvcmFlagText;
    /** 收费耗材原文（是/否；与 chargeableFlag 同向）。 */
    private String chargeableFlagText;
    /** 说明。 */
    private String descriptionText;

    // —— 解析结果 ——
    /** 耗材分类编码。 */
    private String categoryCode;
    /** 耗材种类编码。 */
    private String typeCode;
    /** 财务类型编码（写入定价 itemTypeCode）。 */
    private String itemTypeCode;
    /** 医保等级。 */
    private Integer chrgitmLv;
    /** 病案结算类型编码。 */
    private String mrSettlementTypeCode;
    /** 医保对码是否完成。 */
    private Boolean ybMatchFlag;
    /** 医保类别编码。 */
    private String ybTypeCode;
    /** 采购单位编码。 */
    private String purchaseUnitCode;
    /** 拆零单位编码。 */
    private String splitUnitCode;
    /** 采购相对拆零倍数。 */
    private BigDecimal purchaseUnitRatio;
    /** 包装单位编码。 */
    private String packageUnitCode;
    /** 包装相对拆零倍数。 */
    private BigDecimal packageUnitRatio;
    /** 库存单位编码。 */
    private String stockUnitCode;
    /** 库存相对拆零倍数。 */
    private BigDecimal stockUnitRatio;
    /** 购入价。 */
    private BigDecimal purchasePrice;
    /** 零售价。 */
    private BigDecimal retailPrice;
    /** 最高零售价。 */
    private BigDecimal maximumRetailPrice;
    /** 警戒线最低值。 */
    private BigDecimal itemMinQuantity;
    /** 警戒线最高值。 */
    private BigDecimal itemMaxQuantity;
    /** 过敏标记。 */
    private Boolean allergenFlag;
    /** 医保标记。 */
    private Boolean ybFlag;
    /** 高值耗材标志。 */
    private Boolean hvcmFlag;
    /** 收费耗材标记（true=收费；与界面「收费耗材」勾选同向）。 */
    private Boolean chargeableFlag;
}
