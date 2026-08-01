/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.support;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 诊疗目录导入内部行模型（模板 v1.0，16 列）。
 *
 * <p>术语列先存中文/原始文本，校验成功后由 {@link ServiceCatalogImportRowValidator} 回填 code 与数值。</p>
 */
@Getter
@Setter
public class ServiceCatalogImportRow {

    /** Excel 1-based 行号。 */
    private Integer excelRowNum;

    // —— A 基础信息（原始） ——
    /** 诊疗名称。 */
    private String name;
    /** 目录分类（中文或码）。 */
    private String category;
    /** 业务类型（中文或码）。 */
    private String activityType;
    /** 销售/计价单位（中文或码）。 */
    private String packageUnit;
    /** 财务类型（中文或码）。 */
    private String finType;

    // —— B 计价（原始） ——
    /** 成本价原文。 */
    private String purchasePriceText;
    /** 零售价原文。 */
    private String retailPriceText;
    /** 最高零售价原文。 */
    private String maximumRetailPriceText;

    // —— C 医保与说明（原始） ——
    /** 医保标志是/否原文。 */
    private String ybFlagText;
    /** 医保编号。 */
    private String ybNo;
    /** 医保类别原文。 */
    private String ybType;
    /** 医保对码是/否原文。 */
    private String ybMatchFlagText;
    /** 医保等级原文。 */
    private String chrgitmLvText;
    /** 病案结算类型原文。 */
    private String mrSettlementType;
    /** 院内旧编码。 */
    private String legacyCode;
    /** 描述。 */
    private String descriptionText;

    // —— 解析结果 ——
    /** 目录分类编码。 */
    private String categoryCode;
    /** 业务类型枚举值（ActivityType code 转 Integer）。 */
    private Integer typeEnum;
    /** 销售/计价单位编码。 */
    private String packageUnitCode;
    /** 财务类型编码（写入定价 itemTypeCode）。 */
    private String itemTypeCode;
    /** 成本价。 */
    private BigDecimal purchasePrice;
    /** 零售价。 */
    private BigDecimal retailPrice;
    /** 最高零售价。 */
    private BigDecimal maximumRetailPrice;
    /** 医保标志（1=是，0=否）。 */
    private Integer ybFlag;
    /** 医保类别编码。 */
    private String ybTypeCode;
    /** 医保对码标记（1=是，0=否；可选可为 null）。 */
    private Integer ybMatchFlag;
    /** 医保等级。 */
    private Integer chrgitmLv;
    /** 病案结算类型编码。 */
    private String mrSettlementTypeCode;
}
