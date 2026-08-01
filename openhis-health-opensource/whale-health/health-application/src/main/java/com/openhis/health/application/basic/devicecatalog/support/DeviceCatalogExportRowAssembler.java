/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.support;

import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.share.constants.DeviceCatalogImportConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 耗材目录导出行组装：实体 + 维护态定价 → 导出列（首列编号 + 导入模板字段序）。
 *
 * <p>术语列写 label；金额为包装单位维护态价（PURCHASE/SALE/LIMIT）。</p>
 */
@Component
public class DeviceCatalogExportRowAssembler {

    /**
     * 将单条耗材定义与其定价行映射为导出行单元格。
     *
     * @param entity           耗材定义实体
     * @param priceLines       该实例维护态定价行（可为 null/空）
     * @param terminologyIndex 已 {@link DeviceCatalogImportTerminologyIndex#load()} 的术语索引
     * @return 长度 = 1（编号）+ {@link DeviceCatalogImportConstants#COLUMN_HEADERS} 的单元格文案
     */
    public String[] toColumnValues(
        DeviceDefinition entity,
        List<ChargeItemDefinition> priceLines,
        DeviceCatalogImportTerminologyIndex terminologyIndex
    ) {
        String[] values = new String[DeviceCatalogImportConstants.COLUMN_HEADERS.length + 1];
        if (entity == null) {
            return values;
        }
        PriceSnapshot prices = this.extractPrices(priceLines);
        DescriptionParts descriptionParts = this.splitDescription(entity.getDescription());

        // 导出专有：系统采番编号（导入模板无此列）
        values[0] = this.textOrEmpty(entity.getBusNo());
        // A 基础信息（导入 0～12 → 导出 1～13）
        values[1] = this.textOrEmpty(entity.getName());
        values[2] = this.textOrEmpty(entity.getMerchandiseName());
        values[3] = this.labelOrEmpty(
            terminologyIndex, DeviceCatalogImportTerminologyIndex.DICT_CATEGORY, entity.getCategoryCode());
        values[4] = this.labelOrEmpty(
            terminologyIndex, DeviceCatalogImportTerminologyIndex.DICT_DEVICE_TYPE, entity.getTypeCode());
        values[5] = this.textOrEmpty(entity.getManufacturerText());
        values[6] = this.labelOrEmpty(
            terminologyIndex, DeviceCatalogImportTerminologyIndex.DICT_FIN_TYPE, prices.typeCode);
        values[7] = this.textOrEmpty(entity.getTotalVolume());
        values[8] = this.textOrEmpty(entity.getApprovalNumber());
        values[9] = this.textOrEmpty(entity.getYbNo());
        values[10] = this.resolveChrgitmLvLabel(terminologyIndex, entity.getChrgitmLv());
        values[11] = this.labelOrEmpty(
            terminologyIndex,
            DeviceCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT,
            entity.getMrSettlementTypeCode());
        values[12] = this.yesNoLabel(entity.getYbMatchFlag());
        values[13] = this.labelOrEmpty(
            terminologyIndex, DeviceCatalogImportTerminologyIndex.DICT_YB_TYPE, prices.ybType);

        // B 库存与单位（导入 13～24 → 导出 14～25）
        values[14] = this.labelOrEmpty(
            terminologyIndex, DeviceCatalogImportTerminologyIndex.DICT_UNIT, entity.getPurchaseUnitCode());
        values[15] = this.labelOrEmpty(
            terminologyIndex, DeviceCatalogImportTerminologyIndex.DICT_UNIT, entity.getSplitUnitCode());
        values[16] = this.decimalText(entity.getPurchaseUnitRatio());
        values[17] = this.labelOrEmpty(
            terminologyIndex, DeviceCatalogImportTerminologyIndex.DICT_UNIT, entity.getPackageUnitCode());
        values[18] = this.decimalText(entity.getPackageUnitRatio());
        values[19] = this.labelOrEmpty(
            terminologyIndex, DeviceCatalogImportTerminologyIndex.DICT_UNIT, entity.getStockUnitCode());
        values[20] = this.decimalText(entity.getStockUnitRatio());
        values[21] = this.moneyText(prices.purchasePrice);
        values[22] = this.moneyText(prices.retailPrice);
        values[23] = this.moneyText(prices.maximumRetailPrice);
        values[24] = this.decimalText(entity.getItemMinQuantity());
        values[25] = this.decimalText(entity.getItemMaxQuantity());

        // C 业务信息（导入 25～31 → 导出 26～32）
        values[26] = this.textOrEmpty(descriptionParts.legacyCode);
        values[27] = this.textOrEmpty(entity.getModelNumber());
        values[28] = this.yesNoLabel(entity.getAllergenFlag());
        values[29] = this.yesNoLabel(entity.getYbFlag());
        values[30] = this.yesNoLabel(entity.getHvcmFlag());
        values[31] = this.yesNoLabel(entity.getChargeableFlag());
        values[32] = this.textOrEmpty(descriptionParts.descriptionText);
        return values;
    }

    /**
     * 从定价行抽取维护态购入价/零售价/限价及财务类型、医保类别。
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
     * 拆分 description：剥离「院内旧编码=」行，剩余作为说明。
     *
     * @param description 库内 description 原文
     * @return 说明正文与院内旧编码
     */
    private DescriptionParts splitDescription(String description) {
        DescriptionParts parts = new DescriptionParts();
        if (StringUtils.isEmpty(description)) {
            return parts;
        }
        String prefix = DeviceCatalogImportConstants.LEGACY_CODE_DEFINITION_PREFIX;
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
    private String resolveChrgitmLvLabel(DeviceCatalogImportTerminologyIndex index, Integer chrgitmLv) {
        if (chrgitmLv == null) {
            return "";
        }
        return this.labelOrEmpty(
            index, DeviceCatalogImportTerminologyIndex.DICT_CHRGITM_LV, String.valueOf(chrgitmLv));
    }

    /**
     * 术语 code → label；空白 code 返回空串。
     *
     * @param index   术语索引
     * @param dictKey 字典键
     * @param code    持久化 code
     * @return label 或空串
     */
    private String labelOrEmpty(DeviceCatalogImportTerminologyIndex index, String dictKey, String code) {
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
