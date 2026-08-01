/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.support;

import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.share.constants.ServiceCatalogImportConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊疗目录导出行组装：基础读模型 + 维护态定价 → 导出列（首列编号 + 导入模板字段序）。
 *
 * <p>术语列写 label；金额为销售计价单位维护态价（PURCHASE/SALE/LIMIT，成本价对应 PURCHASE）。</p>
 */
@Component
public class ServiceCatalogExportRowAssembler {

    /**
     * 将单条诊疗基础信息与其定价行映射为导出行单元格。
     *
     * @param base             诊疗定义基础读模型
     * @param priceLines       该实例维护态定价行（可为 null/空）
     * @param terminologyIndex 已 {@link ServiceCatalogImportTerminologyIndex#load()} 的术语索引
     * @return 长度 = 1（编号）+ {@link ServiceCatalogImportConstants#COLUMN_HEADERS} 的单元格文案
     */
    public String[] toColumnValues(
        ActivityDefinitionBaseQueryModel base,
        List<ChargeItemDefinition> priceLines,
        ServiceCatalogImportTerminologyIndex terminologyIndex
    ) {
        String[] values = new String[ServiceCatalogImportConstants.COLUMN_HEADERS.length + 1];
        if (base == null) {
            return values;
        }
        PriceSnapshot prices = this.extractPrices(priceLines);
        DescriptionParts descriptionParts = this.splitDescription(base.getDescriptionText());

        // 导出专有：系统采番编号
        values[0] = this.textOrEmpty(base.getBusNo());
        // A 基础信息（导入 0～4 → 导出 1～5）
        values[1] = this.textOrEmpty(base.getName());
        values[2] = this.labelOrEmpty(
            terminologyIndex, ServiceCatalogImportTerminologyIndex.DICT_CATEGORY, base.getCategoryCode());
        values[3] = this.labelOrEmpty(
            terminologyIndex, ServiceCatalogImportTerminologyIndex.DICT_ACTIVITY_TYPE, base.getTypeEnum());
        values[4] = this.labelOrEmpty(
            terminologyIndex, ServiceCatalogImportTerminologyIndex.DICT_UNIT, base.getPackageUnitCode());
        values[5] = this.labelOrEmpty(
            terminologyIndex, ServiceCatalogImportTerminologyIndex.DICT_FIN_TYPE, prices.typeCode);

        // B 计价（导入 5～7 → 导出 6～8）
        values[6] = this.moneyText(prices.purchasePrice);
        values[7] = this.moneyText(prices.retailPrice);
        values[8] = this.moneyText(prices.maximumRetailPrice);

        // C 医保与说明（导入 8～15 → 导出 9～16）
        values[9] = this.yesNoLabel(base.getYbFlag());
        values[10] = this.textOrEmpty(base.getYbNo());
        values[11] = this.labelOrEmpty(
            terminologyIndex, ServiceCatalogImportTerminologyIndex.DICT_YB_TYPE, prices.ybType);
        values[12] = this.yesNoLabel(base.getYbMatchFlag());
        values[13] = this.resolveChrgitmLvLabel(terminologyIndex, base.getChrgitmLv());
        values[14] = this.labelOrEmpty(
            terminologyIndex,
            ServiceCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT,
            base.getMrSettlementTypeCode());
        values[15] = this.textOrEmpty(descriptionParts.legacyCode);
        values[16] = this.textOrEmpty(descriptionParts.descriptionText);
        return values;
    }

    /**
     * 从定价行抽取维护态成本价/零售价/限价及财务类型、医保类别。
     *
     * @param priceLines 实例维定价行
     * @return 价格快照
     */
    private PriceSnapshot extractPrices(List<ChargeItemDefinition> priceLines) {
        PriceSnapshot snapshot = new PriceSnapshot();
        if (priceLines == null || priceLines.isEmpty()) {
            return snapshot;
        }
        for (ChargeItemDefinition row : priceLines) {
            if (row == null) {
                continue;
            }
            if (StringUtils.isEmpty(snapshot.typeCode) && StringUtils.isNotEmpty(row.getTypeCode())) {
                snapshot.typeCode = row.getTypeCode().trim();
            }
            if (StringUtils.isEmpty(snapshot.ybType) && StringUtils.isNotEmpty(row.getYbType())) {
                snapshot.ybType = row.getYbType().trim();
            }
            if (PriceType.PURCHASE.getCode().equals(row.getPriceTypeCode())) {
                snapshot.purchasePrice = row.getPriceValue();
                continue;
            }
            if (PriceType.LIMIT.getCode().equals(row.getPriceTypeCode())) {
                snapshot.maximumRetailPrice = row.getPriceValue();
                continue;
            }
            if (PriceType.SALE.getCode().equals(row.getPriceTypeCode())) {
                snapshot.retailPrice = row.getPriceValue();
            }
        }
        return snapshot;
    }

    /**
     * 拆分 description：剥离「院内旧编码=」行。
     *
     * @param description 库内 description 原文
     * @return 说明正文与院内旧编码
     */
    private DescriptionParts splitDescription(String description) {
        DescriptionParts parts = new DescriptionParts();
        if (StringUtils.isEmpty(description)) {
            return parts;
        }
        String prefix = ServiceCatalogImportConstants.LEGACY_CODE_DEFINITION_PREFIX;
        List<String> textLines = new ArrayList<>();
        for (String line : description.split("\\R")) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                String legacy = trimmed.substring(prefix.length()).trim();
                if (StringUtils.isNotEmpty(legacy) && StringUtils.isEmpty(parts.legacyCode)) {
                    parts.legacyCode = legacy;
                }
                continue;
            }
            if (StringUtils.isNotEmpty(trimmed)) {
                textLines.add(trimmed);
            }
        }
        if (!textLines.isEmpty()) {
            parts.descriptionText = String.join("\n", textLines);
        }
        return parts;
    }

    /**
     * 医保等级 Integer → 术语 label。
     *
     * @param index     术语索引
     * @param chrgitmLv 医保等级
     * @return label；空等级返回空串
     */
    private String resolveChrgitmLvLabel(ServiceCatalogImportTerminologyIndex index, Integer chrgitmLv) {
        if (chrgitmLv == null) {
            return "";
        }
        return this.labelOrEmpty(
            index, ServiceCatalogImportTerminologyIndex.DICT_CHRGITM_LV, String.valueOf(chrgitmLv));
    }

    /**
     * 术语 code → label；空白 code 返回空串。
     *
     * @param index   术语索引
     * @param dictKey 字典键
     * @param code    持久化 code
     * @return label 或空串
     */
    private String labelOrEmpty(ServiceCatalogImportTerminologyIndex index, String dictKey, String code) {
        String label = index.resolveLabel(dictKey, code);
        if (label == null) {
            return "";
        }
        return label;
    }

    /**
     * Boolean → 「是」/「否」；null 留空。
     *
     * @param flag 布尔标志
     * @return 是/否/空
     */
    private String yesNoLabel(Boolean flag) {
        if (flag == null) {
            return "";
        }
        if (Boolean.TRUE.equals(flag)) {
            return "是";
        }
        return "否";
    }

    /**
     * 金额 → 行级刻度后去尾零明文。
     *
     * @param value 金额
     * @return 单元格文案
     */
    private String moneyText(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return MonetaryAmountSupport.scaleCalc(value).stripTrailingZeros().toPlainString();
    }

    /**
     * null/空白安全转空串。
     *
     * @param value 原文
     * @return trim 后文案或空串
     */
    private String textOrEmpty(String value) {
        if (StringUtils.isEmpty(value)) {
            return "";
        }
        return value.trim();
    }

    /** 维护态价格与类型快照。 */
    private static final class PriceSnapshot {
        private String typeCode;
        private String ybType;
        private BigDecimal purchasePrice;
        private BigDecimal retailPrice;
        private BigDecimal maximumRetailPrice;
    }

    /** description 拆分结果。 */
    private static final class DescriptionParts {
        private String descriptionText;
        private String legacyCode;
    }
}
