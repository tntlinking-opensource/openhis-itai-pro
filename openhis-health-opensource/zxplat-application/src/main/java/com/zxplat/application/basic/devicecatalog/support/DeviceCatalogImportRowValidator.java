/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.devicecatalog.support;

import com.zxplat.application.basic.devicecatalog.dto.ImportDeviceCatalogErrorItemDto;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.share.constants.DeviceCatalogImportConstants;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 耗材目录导入行校验器（模板 v1.0）：收集全量错误，不短路；通过后回填解析字段。
 */
@Service
@RequiredArgsConstructor
public class DeviceCatalogImportRowValidator {

    /**
     * 校验全部导入行并收集错误；无错误时各行解析字段已填充。
     *
     * @param rows         解析行
     * @param orgId        机构主键（库内唯一键）
     * @param index        术语索引（须已 load）
     * @param queryService 耗材定义读侧（库内重复键）
     * @return 错误列表；空表示通过
     */
    public List<ImportDeviceCatalogErrorItemDto> validate(
        List<DeviceCatalogImportRow> rows,
        Long orgId,
        DeviceCatalogImportTerminologyIndex index,
        DeviceDefinitionQueryService queryService
    ) {
        List<ImportDeviceCatalogErrorItemDto> errors = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            errors.add(this.batchError(
                DeviceCatalogImportConstants.ERR_NO_DATA,
                "导入耗材目录失败：文件中没有有效数据行"));
            return errors;
        }
        if (rows.size() > DeviceCatalogImportConstants.IMPORT_MAX_ROWS) {
            errors.add(this.batchError(
                DeviceCatalogImportConstants.ERR_TOO_MANY_ROWS,
                "导入耗材目录失败：有效行数超过上限 "
                    + DeviceCatalogImportConstants.IMPORT_MAX_ROWS));
            return errors;
        }
        Map<String, Integer> firstRowByToken = new HashMap<>();
        Set<String> duplicateTokensInFile = new HashSet<>();
        Set<String> allTokens = new HashSet<>();
        for (DeviceCatalogImportRow row : rows) {
            this.validateRequired(row, errors);
            this.validateNumbers(row, errors);
            this.validateTerminology(row, index, errors);
            this.validateRatioRules(row, errors);
            this.validateLimitPrice(row, errors);
            String token = DeviceCatalogImportRowValidator.duplicateKeyToken(
                row.getName(), row.getTotalVolume(), row.getManufacturerText());
            if (StringUtils.isNotEmpty(token)) {
                allTokens.add(token);
                Integer firstRow = firstRowByToken.get(token);
                if (firstRow == null) {
                    firstRowByToken.put(token, row.getExcelRowNum());
                } else {
                    duplicateTokensInFile.add(token);
                }
            }
        }
        for (DeviceCatalogImportRow row : rows) {
            String token = DeviceCatalogImportRowValidator.duplicateKeyToken(
                row.getName(), row.getTotalVolume(), row.getManufacturerText());
            if (duplicateTokensInFile.contains(token)) {
                errors.add(this.rowError(
                    row.getExcelRowNum(),
                    "耗材名称",
                    DeviceCatalogImportConstants.ERR_DUP_FILE,
                    "文件内存在重复的耗材（名称+包装规格+生产厂家）"));
            }
        }
        if (orgId != null && !allTokens.isEmpty()) {
            Set<String> existing = queryService.findExistingDuplicateKeyTokens(orgId, allTokens);
            for (DeviceCatalogImportRow row : rows) {
                String token = DeviceCatalogImportRowValidator.duplicateKeyToken(
                    row.getName(), row.getTotalVolume(), row.getManufacturerText());
                if (existing.contains(token)) {
                    errors.add(this.rowError(
                        row.getExcelRowNum(),
                        "耗材名称",
                        DeviceCatalogImportConstants.ERR_DUP_DB,
                        "库内已存在相同耗材（名称+包装规格+生产厂家）"));
                }
            }
        }
        return errors;
    }

    /**
     * 构造库内/文件内唯一键 token。
     *
     * @param name             耗材名称
     * @param totalVolume      包装规格
     * @param manufacturerText 生产厂家
     * @return token；三者皆空返回 {@code null}
     */
    public static String duplicateKeyToken(String name, String totalVolume, String manufacturerText) {
        String n = "";
        if (name != null) {
            n = name.trim();
        }
        String v = "";
        if (totalVolume != null) {
            v = totalVolume.trim();
        }
        String m = "";
        if (manufacturerText != null) {
            m = manufacturerText.trim();
        }
        if (n.isEmpty() && v.isEmpty() && m.isEmpty()) {
            return null;
        }
        return n + '\u0001' + v + '\u0001' + m;
    }

    /**
     * 校验必填列。
     */
    private void validateRequired(
        DeviceCatalogImportRow row,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        this.assertRequired(row, row.getName(), "耗材名称", errors);
        this.assertRequired(row, row.getCategory(), "耗材分类", errors);
        this.assertRequired(row, row.getDeviceType(), "耗材种类", errors);
        this.assertRequired(row, row.getFinType(), "财务类型", errors);
        this.assertRequired(row, row.getTotalVolume(), "包装规格", errors);
        this.assertRequired(row, row.getPurchaseUnit(), "采购单位", errors);
        this.assertRequired(row, row.getSplitUnit(), "拆零单位", errors);
        this.assertRequired(row, row.getPurchaseUnitRatioText(), "采购/拆零倍数", errors);
        this.assertRequired(row, row.getPackageUnit(), "包装单位", errors);
        this.assertRequired(row, row.getPackageUnitRatioText(), "包装/拆零倍数", errors);
        this.assertRequired(row, row.getStockUnit(), "库存单位", errors);
        this.assertRequired(row, row.getStockUnitRatioText(), "库存/拆零倍数", errors);
        this.assertRequired(row, row.getPurchasePriceText(), "购入价", errors);
        this.assertRequired(row, row.getRetailPriceText(), "零售价", errors);
    }

    /**
     * 校验数值列并回填解析结果。
     */
    private void validateNumbers(DeviceCatalogImportRow row, List<ImportDeviceCatalogErrorItemDto> errors) {
        BigDecimal purchaseRatio = this.parsePositiveDecimal(row, row.getPurchaseUnitRatioText(), "采购/拆零倍数", errors);
        if (purchaseRatio != null) {
            row.setPurchaseUnitRatio(purchaseRatio);
        }
        BigDecimal packageRatio = this.parsePositiveDecimal(row, row.getPackageUnitRatioText(), "包装/拆零倍数", errors);
        if (packageRatio != null) {
            row.setPackageUnitRatio(packageRatio);
        }
        BigDecimal stockRatio = this.parsePositiveDecimal(row, row.getStockUnitRatioText(), "库存/拆零倍数", errors);
        if (stockRatio != null) {
            row.setStockUnitRatio(stockRatio);
        }
        BigDecimal retail = this.parseNonNegativeDecimal(row, row.getRetailPriceText(), "零售价", errors);
        if (retail != null) {
            row.setRetailPrice(retail);
        }
        BigDecimal purchase = this.parseNonNegativeDecimal(row, row.getPurchasePriceText(), "购入价", errors);
        if (purchase != null) {
            row.setPurchasePrice(purchase);
        }
        if (StringUtils.isNotEmpty(row.getMaximumRetailPriceText())) {
            BigDecimal limit = this.parseNonNegativeDecimal(row, row.getMaximumRetailPriceText(), "最高零售价", errors);
            if (limit != null) {
                row.setMaximumRetailPrice(limit);
            }
        }
        if (StringUtils.isNotEmpty(row.getItemMinQuantityText())) {
            BigDecimal minQty = this.parseNonNegativeDecimal(row, row.getItemMinQuantityText(), "警戒线最低值", errors);
            if (minQty != null) {
                row.setItemMinQuantity(minQty);
            }
        }
        if (StringUtils.isNotEmpty(row.getItemMaxQuantityText())) {
            BigDecimal maxQty = this.parseNonNegativeDecimal(row, row.getItemMaxQuantityText(), "警戒线最高值", errors);
            if (maxQty != null) {
                row.setItemMaxQuantity(maxQty);
            }
        }
    }

    /**
     * 校验术语列并回填 code / 布尔。
     */
    private void validateTerminology(
        DeviceCatalogImportRow row,
        DeviceCatalogImportTerminologyIndex index,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        this.resolveTerm(row, index, DeviceCatalogImportTerminologyIndex.DICT_CATEGORY,
            row.getCategory(), "耗材分类", errors, ResolvedField.CATEGORY);
        // 种类与分类同为维护必填；解析失败时由 resolveTerm 写入行错误
        this.resolveTerm(row, index, DeviceCatalogImportTerminologyIndex.DICT_DEVICE_TYPE,
            row.getDeviceType(), "耗材种类", errors, ResolvedField.DEVICE_TYPE);
        this.resolveTerm(row, index, DeviceCatalogImportTerminologyIndex.DICT_FIN_TYPE,
            row.getFinType(), "财务类型", errors, ResolvedField.FIN_TYPE);
        // 采购/拆零/包装/库存均解析自统一计量单位术语 unit_code
        this.resolveTerm(row, index, DeviceCatalogImportTerminologyIndex.DICT_UNIT,
            row.getPurchaseUnit(), "采购单位", errors, ResolvedField.PURCHASE_UNIT);
        this.resolveTerm(row, index, DeviceCatalogImportTerminologyIndex.DICT_UNIT,
            row.getSplitUnit(), "拆零单位", errors, ResolvedField.SPLIT_UNIT);
        this.resolveTerm(row, index, DeviceCatalogImportTerminologyIndex.DICT_UNIT,
            row.getPackageUnit(), "包装单位", errors, ResolvedField.PACKAGE_UNIT);
        this.resolveTerm(row, index, DeviceCatalogImportTerminologyIndex.DICT_UNIT,
            row.getStockUnit(), "库存单位", errors, ResolvedField.STOCK_UNIT);

        if (StringUtils.isNotEmpty(row.getChrgitmLvText())) {
            this.resolveChrgitmLv(row, index, errors);
        }
        if (StringUtils.isNotEmpty(row.getMrSettlementType())) {
            this.resolveTerm(row, index, DeviceCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT,
                row.getMrSettlementType(), "病案结算类型", errors, ResolvedField.MR_SETTLEMENT);
        }
        if (StringUtils.isNotEmpty(row.getYbType())) {
            this.resolveTerm(row, index, DeviceCatalogImportTerminologyIndex.DICT_YB_TYPE,
                row.getYbType(), "医保类别", errors, ResolvedField.YB_TYPE);
        }

        row.setYbMatchFlag(this.resolveYesNoOptional(row, row.getYbMatchFlagText(), "医保对码", errors));
        row.setAllergenFlag(this.resolveYesNoDefaultFalse(row, row.getAllergenFlagText(), "过敏标记", errors));
        row.setYbFlag(this.resolveYesNoDefaultFalse(row, row.getYbFlagText(), "医保标记", errors));
        row.setHvcmFlag(this.resolveYesNoDefaultFalse(row, row.getHvcmFlagText(), "高值耗材标志", errors));
        // 「收费耗材」与 chargeableFlag 同向；空白默认 true（对齐新建默认勾选）
        row.setChargeableFlag(this.resolveYesNoDefaultTrue(row, row.getChargeableFlagText(), "收费耗材", errors));
    }

    /**
     * 解析医保等级为 Integer。
     */
    private void resolveChrgitmLv(
        DeviceCatalogImportRow row,
        DeviceCatalogImportTerminologyIndex index,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        String code = index.resolveCode(
            DeviceCatalogImportTerminologyIndex.DICT_CHRGITM_LV, row.getChrgitmLvText());
        if (code == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "医保等级",
                DeviceCatalogImportConstants.ERR_TERMINOLOGY,
                "医保等级无法识别：" + row.getChrgitmLvText()));
            return;
        }
        try {
            row.setChrgitmLv(Integer.valueOf(code.trim()));
        } catch (NumberFormatException ex) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "医保等级",
                DeviceCatalogImportConstants.ERR_TERMINOLOGY,
                "医保等级编码非法：" + code));
        }
    }

    /**
     * 同单位时倍数须为 1（采购/包装/库存相对拆零，无剂量）。
     */
    private void validateRatioRules(DeviceCatalogImportRow row, List<ImportDeviceCatalogErrorItemDto> errors) {
        this.assertSameUnitRatioOne(
            row, row.getPurchaseUnitCode(), row.getSplitUnitCode(), row.getPurchaseUnitRatio(),
            "采购/拆零倍数", "采购单位与拆零单位相同时，采购/拆零倍数必须为 1", errors);
        this.assertSameUnitRatioOne(
            row, row.getPackageUnitCode(), row.getSplitUnitCode(), row.getPackageUnitRatio(),
            "包装/拆零倍数", "包装单位与拆零单位相同时，包装/拆零倍数必须为 1", errors);
        this.assertSameUnitRatioOne(
            row, row.getStockUnitCode(), row.getSplitUnitCode(), row.getStockUnitRatio(),
            "库存/拆零倍数", "库存单位与拆零单位相同时，库存/拆零倍数必须为 1", errors);
    }

    /**
     * 单位相同则倍数必须为 1。
     */
    private void assertSameUnitRatioOne(
        DeviceCatalogImportRow row,
        String unitCode,
        String splitUnitCode,
        BigDecimal ratio,
        String columnName,
        String message,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(unitCode) || StringUtils.isEmpty(splitUnitCode) || ratio == null) {
            return;
        }
        if (!unitCode.equals(splitUnitCode)) {
            return;
        }
        if (ratio.compareTo(BigDecimal.ONE) != 0) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                columnName,
                DeviceCatalogImportConstants.ERR_RATIO,
                message));
        }
    }

    /**
     * 最高零售价不得低于零售价；填 0 视为未填（与空单元格同口径）。
     */
    private void validateLimitPrice(DeviceCatalogImportRow row, List<ImportDeviceCatalogErrorItemDto> errors) {
        // 最高零售价可选；Excel 空单元格常被写成 0，按未填处理，避免误拦「限价 0 < 零售价」
        if (row.getMaximumRetailPrice() == null
            || row.getMaximumRetailPrice().compareTo(BigDecimal.ZERO) == 0) {
            row.setMaximumRetailPrice(null);
            return;
        }
        if (row.getRetailPrice() == null) {
            return;
        }
        if (row.getMaximumRetailPrice().compareTo(row.getRetailPrice()) < 0) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "最高零售价",
                DeviceCatalogImportConstants.ERR_LIMIT_PRICE,
                "最高零售价不能低于零售价"));
        }
    }

    /**
     * 必填断言。
     */
    private void assertRequired(
        DeviceCatalogImportRow row,
        String value,
        String columnName,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(value)) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                columnName,
                DeviceCatalogImportConstants.ERR_REQUIRED,
                columnName + "不能为空"));
        }
    }

    /**
     * 解析大于 0 的小数。
     */
    private BigDecimal parsePositiveDecimal(
        DeviceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        BigDecimal value = this.toDecimal(raw);
        if (value == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, DeviceCatalogImportConstants.ERR_NUMBER,
                columnName + "格式非法"));
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, DeviceCatalogImportConstants.ERR_NUMBER,
                columnName + "须大于 0"));
            return null;
        }
        return value;
    }

    /**
     * 解析非负小数。
     */
    private BigDecimal parseNonNegativeDecimal(
        DeviceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        BigDecimal value = this.toDecimal(raw);
        if (value == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, DeviceCatalogImportConstants.ERR_NUMBER,
                columnName + "格式非法"));
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, DeviceCatalogImportConstants.ERR_NUMBER,
                columnName + "不能为负数"));
            return null;
        }
        return value;
    }

    /**
     * 解析术语并回填。
     */
    private void resolveTerm(
        DeviceCatalogImportRow row,
        DeviceCatalogImportTerminologyIndex index,
        String dictKey,
        String raw,
        String columnName,
        List<ImportDeviceCatalogErrorItemDto> errors,
        ResolvedField field
    ) {
        if (StringUtils.isEmpty(raw)) {
            return;
        }
        String normalized = this.normalizeNumericText(raw.trim());
        String code = index.resolveCode(dictKey, normalized);
        if (code == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                columnName,
                DeviceCatalogImportConstants.ERR_TERMINOLOGY,
                columnName + "无法识别：" + raw));
            return;
        }
        this.applyResolvedCode(row, field, code);
    }

    /**
     * 是/否：空白默认 false。
     */
    private Boolean resolveYesNoDefaultFalse(
        DeviceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return Boolean.FALSE;
        }
        return this.parseYesNo(row, raw, columnName, errors);
    }

    /**
     * 是/否：空白默认 true（用于「收费耗材」等默认勾选字段）。
     */
    private Boolean resolveYesNoDefaultTrue(
        DeviceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return Boolean.TRUE;
        }
        return this.parseYesNo(row, raw, columnName, errors);
    }

    /**
     * 是/否：空白保持 null（可选）。
     */
    private Boolean resolveYesNoOptional(
        DeviceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        return this.parseYesNo(row, raw, columnName, errors);
    }

    /**
     * 解析是/否文案。
     */
    private Boolean parseYesNo(
        DeviceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportDeviceCatalogErrorItemDto> errors
    ) {
        String normalized = raw.trim();
        if ("是".equals(normalized) || "Y".equalsIgnoreCase(normalized) || "YES".equalsIgnoreCase(normalized)
            || "1".equals(normalized) || "true".equalsIgnoreCase(normalized)) {
            return Boolean.TRUE;
        }
        if ("否".equals(normalized) || "N".equalsIgnoreCase(normalized) || "NO".equalsIgnoreCase(normalized)
            || "0".equals(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Boolean.FALSE;
        }
        errors.add(this.rowError(
            row.getExcelRowNum(),
            columnName,
            DeviceCatalogImportConstants.ERR_TERMINOLOGY,
            columnName + "只能填写是或否"));
        return Boolean.FALSE;
    }

    /**
     * 将解析到的 code 写入行模型。
     */
    private void applyResolvedCode(DeviceCatalogImportRow row, ResolvedField field, String code) {
        if (field == ResolvedField.CATEGORY) {
            row.setCategoryCode(code);
            return;
        }
        if (field == ResolvedField.DEVICE_TYPE) {
            row.setTypeCode(code);
            return;
        }
        if (field == ResolvedField.FIN_TYPE) {
            row.setItemTypeCode(code);
            return;
        }
        if (field == ResolvedField.PURCHASE_UNIT) {
            row.setPurchaseUnitCode(code);
            return;
        }
        if (field == ResolvedField.SPLIT_UNIT) {
            row.setSplitUnitCode(code);
            return;
        }
        if (field == ResolvedField.PACKAGE_UNIT) {
            row.setPackageUnitCode(code);
            return;
        }
        if (field == ResolvedField.STOCK_UNIT) {
            row.setStockUnitCode(code);
            return;
        }
        if (field == ResolvedField.MR_SETTLEMENT) {
            row.setMrSettlementTypeCode(code);
            return;
        }
        if (field == ResolvedField.YB_TYPE) {
            row.setYbTypeCode(code);
        }
    }

    /**
     * 全角数字转半角。
     */
    private String normalizeNumericText(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '０' && ch <= '９') {
                builder.append((char) ('0' + (ch - '０')));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString().trim();
    }

    /**
     * 字符串转 BigDecimal。
     */
    private BigDecimal toDecimal(String raw) {
        String normalized = this.normalizeNumericText(raw);
        if (StringUtils.isEmpty(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 构造批级错误。
     */
    private ImportDeviceCatalogErrorItemDto batchError(String code, String message) {
        ImportDeviceCatalogErrorItemDto item = new ImportDeviceCatalogErrorItemDto();
        item.setCode(code);
        item.setMessage(message);
        return item;
    }

    /**
     * 构造行级错误。
     */
    private ImportDeviceCatalogErrorItemDto rowError(
        Integer excelRowNum,
        String columnName,
        String code,
        String message
    ) {
        ImportDeviceCatalogErrorItemDto item = new ImportDeviceCatalogErrorItemDto();
        item.setExcelRowNum(excelRowNum);
        item.setColumnName(columnName);
        item.setCode(code);
        item.setMessage(message);
        return item;
    }

    /**
     * 术语回填目标字段。
     */
    private enum ResolvedField {
        /** 耗材分类 → categoryCode。 */
        CATEGORY,
        /** 耗材种类 → typeCode。 */
        DEVICE_TYPE,
        /** 财务类型 → itemTypeCode。 */
        FIN_TYPE,
        /** 采购单位。 */
        PURCHASE_UNIT,
        /** 拆零单位。 */
        SPLIT_UNIT,
        /** 包装单位。 */
        PACKAGE_UNIT,
        /** 库存单位。 */
        STOCK_UNIT,
        /** 病案结算类型。 */
        MR_SETTLEMENT,
        /** 医保类别。 */
        YB_TYPE
    }
}
