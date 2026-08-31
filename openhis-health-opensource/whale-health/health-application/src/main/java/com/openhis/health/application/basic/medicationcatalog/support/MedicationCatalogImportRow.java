/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.support;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品目录导入内部行模型（模板 v1.6，54 列；兼容 v1.4/v1.5 无追溯码列）。
 *
 * <p>术语列先存中文/原始文本，校验成功后由 {@link MedicationCatalogImportRowValidator} 回填 code 与数值/布尔。</p>
 */
@Getter
@Setter
public class MedicationCatalogImportRow {

    /** Excel 1-based 行号。 */
    private Integer excelRowNum;

    // —— A 基础信息（原始） ——
    private String name;
    private String merchandiseName;
    private String category;
    private String manufacturerText;
    private String finType;
    private String approvalNumber;
    private String nationalDrugCode;
    private String ybNo;
    private String chrgitmLvText;
    private String mrSettlementType;
    private String ybMatchFlagText;
    private String ybType;

    // —— B 临床与规格（原始） ——
    private String pharmacologyCategory;
    private String totalVolume;
    private String doseUnit;
    private String doseUnitRatioText;
    private String doseForm;
    private String skinTestFlagText;
    private String injectFlagText;
    private String usageLimitText;
    private String method;
    private String rate;
    private String doseText;
    private String minUnitText;
    private String maxUnitText;
    private String dosageInstruction;
    private String restrictedScope;
    private String definitionText;
    private String dddCodeText;
    private String dddUnit;

    // —— C 库存与单位（原始） ——
    private String purchaseUnit;
    private String splitUnit;
    private String purchaseUnitRatioText;
    private String packageUnit;
    private String packageUnitRatioText;
    private String traceUnit;
    private String traceUnitRatioText;
    private String stockUnit;
    private String stockUnitRatioText;
    private String purchasePriceText;
    private String retailPriceText;
    private String maximumRetailPriceText;
    private String partAttribute;
    private String thoPartAttribute;
    private String itemMinQuantityText;
    private String itemMaxQuantityText;

    // —— D 业务信息（原始） ——
    private String legacyCode;
    private String drug69Code;
    private String basicFlagText;
    private String childrenFlagText;
    private String selfFlagText;
    private String restrictedFlagText;
    private String antibioticFlagText;
    private String rxFlagText;
    private String nationalMonitorFlagText;
    private String centralPurchaseFlagText;

    // —— 解析结果 ——
    private String categoryCode;
    private String typeCode;
    private Integer chrgitmLv;
    private String mrSettlementTypeCode;
    private Boolean ybMatchFlag;
    private String ybTypeCode;
    private String pharmacologyCategoryCode;
    private String doseUnitCode;
    private BigDecimal doseUnitRatio;
    private String doseFormCode;
    private Boolean skinTestFlag;
    private Boolean injectFlag;
    private BigDecimal usageLimit;
    private String methodCode;
    private String rateCode;
    private BigDecimal dose;
    private BigDecimal minUnit;
    private BigDecimal maxUnit;
    private String dosageInstructionCode;
    private String dddCode;
    private String dddUnitCode;
    private String purchaseUnitCode;
    private String splitUnitCode;
    private BigDecimal purchaseUnitRatio;
    private String packageUnitCode;
    private BigDecimal packageUnitRatio;
    private String traceUnitCode;
    private BigDecimal traceUnitRatio;
    private String stockUnitCode;
    private BigDecimal stockUnitRatio;
    private BigDecimal purchasePrice;
    private BigDecimal retailPrice;
    private BigDecimal maximumRetailPrice;
    private String partAttributeEnum;
    private String thoPartAttributeEnum;
    private BigDecimal itemMinQuantity;
    private BigDecimal itemMaxQuantity;
    private Boolean basicFlag;
    private Boolean childrenFlag;
    private Boolean selfFlag;
    private Boolean restrictedFlag;
    private Boolean antibioticFlag;
    private Boolean rxFlag;
    private Boolean nationalMonitorFlag;
    private Boolean centralPurchaseFlag;

    /**
     * 库内已存在相同药品（名称+厂家+院内旧编码；无旧编码则名称+厂家+规格）：写段跳过，不报错（支持整文件重导）。
     */
    private boolean skipExisting;
}
