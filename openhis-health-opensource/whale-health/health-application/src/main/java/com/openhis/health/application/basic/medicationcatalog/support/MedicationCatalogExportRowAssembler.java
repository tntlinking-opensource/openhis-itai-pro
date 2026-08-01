/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.support;

import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionDetailQueryModel;
import com.openhis.health.domain.share.constants.MedicationCatalogImportConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.enums.OrderCalcRuleEnum;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 药品目录导出行组装：详情 + 维护态定价 → 导出列（首列编号 + 导入模板字段序）。
 *
 * <p>术语列写 label；金额为包装单位维护态价（PURCHASE/SALE/LIMIT）。</p>
 */
@Component
public class MedicationCatalogExportRowAssembler {

    /**
     * 将单条药品详情与其定价行映射为导出行单元格。
     *
     * @param detail 药品定义详情（本聚合读模型）
     * @param priceLines 该实例维护态定价行（可为 null/空）
     * @param terminologyIndex 已 {@link MedicationCatalogImportTerminologyIndex#load()} 的术语索引
     * @return 长度 = 1（编号）+ {@link MedicationCatalogImportConstants#COLUMN_HEADERS} 的单元格文案
     */
    public String[] toColumnValues(
        MedicationDefinitionDetailQueryModel detail,
        List<ChargeItemDefinition> priceLines,
        MedicationCatalogImportTerminologyIndex terminologyIndex
    ) {
        String[] values = new String[MedicationCatalogImportConstants.COLUMN_HEADERS.length + 1];
        if (detail == null) {
            return values;
        }
        PriceSnapshot prices = this.extractPrices(priceLines);
        DefinitionParts definitionParts = this.splitDefinition(detail.getDefinition());

        // 导出专有：系统采番编号（导入模板无此列）
        values[0] = this.textOrEmpty(detail.getBusNo());
        // A 基础信息（导入 0～11 → 导出 1～12）
        values[1] = this.textOrEmpty(detail.getName());
        values[2] = this.textOrEmpty(detail.getMerchandiseName());
        values[3] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_CATEGORY, detail.getCategoryCode());
        values[4] = this.textOrEmpty(detail.getManufacturerText());
        values[5] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_FIN_TYPE, prices.typeCode);
        values[6] = this.textOrEmpty(detail.getApprovalNumber());
        values[7] = this.textOrEmpty(detail.getNationalDrugCode());
        values[8] = this.textOrEmpty(detail.getYbNo());
        values[9] = this.resolveChrgitmLvLabel(terminologyIndex, detail.getChrgitmLv());
        values[10] = this.labelOrEmpty(
            terminologyIndex,
            MedicationCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT,
            detail.getMrSettlementTypeCode());
        values[11] = this.yesNoLabel(detail.getYbMatchFlag());
        values[12] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_YB_TYPE, prices.ybType);

        // B 临床与规格（导入 12～29 → 导出 13～30）
        values[13] = this.labelOrEmpty(
            terminologyIndex,
            MedicationCatalogImportTerminologyIndex.DICT_MEDICINE_PROPERTIES,
            detail.getPharmacologyCategoryCode());
        values[14] = this.textOrEmpty(detail.getTotalVolume());
        values[15] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_DOSE_UNIT, detail.getDoseUnitCode());
        values[16] = this.decimalText(detail.getDoseUnitRatio());
        values[17] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_DOSE_FORM, detail.getDoseFormCode());
        values[18] = this.yesNoLabel(detail.getSkinTestFlag());
        values[19] = this.yesNoLabel(detail.getInjectFlag());
        values[20] = this.decimalText(detail.getUsageLimit());
        values[21] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_METHOD, detail.getMethodCode());
        values[22] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_RATE, detail.getRateCode());
        values[23] = this.decimalText(detail.getDose());
        values[24] = this.decimalText(detail.getMinUnit());
        values[25] = this.decimalText(detail.getMaxUnit());
        values[26] = this.labelOrEmpty(
            terminologyIndex,
            MedicationCatalogImportTerminologyIndex.DICT_DOSAGE_INSTRUCTION,
            detail.getDosageInstruction());
        values[27] = this.textOrEmpty(detail.getRestrictedScope());
        values[28] = this.textOrEmpty(definitionParts.definitionText);
        // DDD 值：与导入一致按自由文字写出（库内 dddCode）
        values[29] = this.textOrEmpty(detail.getDddCode());
        values[30] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_DDD_UNIT, detail.getDddUnitCode());

        // C 库存与单位（导入 30～45 → 导出 31～46）
        values[31] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_UNIT, detail.getPurchaseUnitCode());
        values[32] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_UNIT, detail.getSplitUnitCode());
        values[33] = this.decimalText(detail.getPurchaseUnitRatio());
        values[34] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_UNIT, detail.getPackageUnitCode());
        values[35] = this.decimalText(detail.getPackageUnitRatio());
        values[36] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_UNIT, detail.getTraceUnitCode());
        values[37] = this.decimalText(detail.getTraceUnitRatio());
        values[38] = this.labelOrEmpty(
            terminologyIndex, MedicationCatalogImportTerminologyIndex.DICT_UNIT, detail.getStockUnitCode());
        values[39] = this.decimalText(detail.getStockUnitRatio());
        values[40] = this.moneyText(prices.purchasePrice);
        values[41] = this.moneyText(prices.retailPrice);
        values[42] = this.moneyText(prices.maximumRetailPrice);
        values[43] = this.orderCalcLabel(detail.getPartAttributeEnum());
        values[44] = this.orderCalcLabel(detail.getThoPartAttributeEnum());
        values[45] = this.decimalText(detail.getItemMinQuantity());
        values[46] = this.decimalText(detail.getItemMaxQuantity());

        // D 业务信息（导入 46～53 → 导出 47～54）
        values[47] = this.textOrEmpty(definitionParts.legacyCode);
        values[48] = this.textOrEmpty(detail.getDrug69Code());
        values[49] = this.yesNoLabel(detail.getBasicFlag());
        values[50] = this.yesNoLabel(detail.getChildrenFlag());
        values[51] = this.yesNoLabel(detail.getSelfFlag());
        values[52] = this.yesNoLabel(detail.getRestrictedFlag());
        values[53] = this.yesNoLabel(detail.getAntibioticFlag());
        values[54] = this.yesNoLabel(detail.getRxFlag());
        return values;
    }

    /**
     * 从定价行抽取维护态购入价/零售价/限价及财务类型、医保类别（§2.2.3 目录维护当前价读路径）。
     *
     * @param priceLines 实例维定价行
     * @return 价格快照；无行时字段均为空
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
            // 财务类型 / 医保类别取首条非空（与详情 enrich 一致）
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
     * 拆分 definition：剥离「院内旧编码=」行，剩余作为药品说明。
     *
     * @param definition 库内 definition 原文
     * @return 说明正文与院内旧编码
     */
    private DefinitionParts splitDefinition(String definition) {
        DefinitionParts parts = new DefinitionParts();
        if (StringUtils.isEmpty(definition)) {
            return parts;
        }
        String prefix = MedicationCatalogImportConstants.LEGACY_CODE_DEFINITION_PREFIX;
        List<String> textLines = new ArrayList<>();
        for (String line : definition.split("\\R")) {
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
            parts.definitionText = String.join("\n", textLines);
        }
        return parts;
    }

    /**
     * 医保等级 Integer → 术语 label（code 为数字字符串）。
     *
     * @param index     术语索引
     * @param chrgitmLv 医保等级
     * @return label；空等级返回空串
     */
    private String resolveChrgitmLvLabel(MedicationCatalogImportTerminologyIndex index, Integer chrgitmLv) {
        if (chrgitmLv == null) {
            return "";
        }
        return this.labelOrEmpty(
            index, MedicationCatalogImportTerminologyIndex.DICT_CHRGITM_LV, String.valueOf(chrgitmLv));
    }

    /**
     * 拆分属性枚举 code → 中文 display（与模板下拉一致）。
     *
     * @param code OrderCalcRuleEnum code
     * @return display；未知码回落原 code
     */
    private String orderCalcLabel(String code) {
        if (StringUtils.isEmpty(code)) {
            return "";
        }
        OrderCalcRuleEnum rule = EnumUtil.fromCode(OrderCalcRuleEnum.class, code.trim());
        if (rule == null) {
            return code.trim();
        }
        return rule.getDisplay();
    }

    /**
     * 术语 code → label；空白 code 返回空串。
     *
     * @param index   术语索引
     * @param dictKey 字典键
     * @param code    持久化 code
     * @return label 或空串
     */
    private String labelOrEmpty(MedicationCatalogImportTerminologyIndex index, String dictKey, String code) {
        String label = index.resolveLabel(dictKey, code);
        if (label == null) {
            return "";
        }
        return label;
    }

    /**
     * Boolean → 「是」/「否」；null 留空（与选填列口径一致）。
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
     * 非金额数值 → 去尾零明文；null 空串。
     *
     * @param value 数值
     * @return 单元格文案
     */
    private String decimalText(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 金额 → 行级刻度后去尾零明文（BR-MON-001；导入可再解析）。
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

    /** 维护态价格与类型快照（导出行临时结构）。 */
    private static final class PriceSnapshot {
        /** 财务类型 code。 */
        private String typeCode;
        /** 医保类别 code。 */
        private String ybType;
        /** 购入价（元/包装）。 */
        private BigDecimal purchasePrice;
        /** 零售价（元/包装）。 */
        private BigDecimal retailPrice;
        /** 最高零售价（元/包装）。 */
        private BigDecimal maximumRetailPrice;
    }

    /** definition 拆分结果。 */
    private static final class DefinitionParts {
        /** 药品说明正文（已剥离旧编码行）。 */
        private String definitionText;
        /** 院内旧编码。 */
        private String legacyCode;
    }
}
