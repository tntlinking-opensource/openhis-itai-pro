/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.support;

import com.openhis.health.application.basic.servicecatalog.dto.ImportServiceCatalogErrorItemDto;
import com.openhis.health.domain.share.constants.ServiceCatalogImportConstants;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.util.StringUtils;
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
 * 诊疗目录导入行校验器（模板 v1.0）：收集全量错误，不短路；通过后回填解析字段。
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalogImportRowValidator {

    /**
     * 校验全部导入行并收集错误；无错误时各行解析字段已填充。
     *
     * @param rows         解析行
     * @param orgId        机构主键（库内唯一键）
     * @param index        术语索引（须已 load）
     * @param queryService 诊疗定义读侧（库内重复键）
     * @return 错误列表；空表示通过
     */
    public List<ImportServiceCatalogErrorItemDto> validate(
        List<ServiceCatalogImportRow> rows,
        Long orgId,
        ServiceCatalogImportTerminologyIndex index,
        ActivityDefinitionQueryService queryService
    ) {
        List<ImportServiceCatalogErrorItemDto> errors = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            errors.add(this.batchError(
                ServiceCatalogImportConstants.ERR_NO_DATA,
                "导入诊疗目录失败：文件中没有有效数据行"));
            return errors;
        }
        if (rows.size() > ServiceCatalogImportConstants.IMPORT_MAX_ROWS) {
            errors.add(this.batchError(
                ServiceCatalogImportConstants.ERR_TOO_MANY_ROWS,
                "导入诊疗目录失败：有效行数超过上限 "
                    + ServiceCatalogImportConstants.IMPORT_MAX_ROWS));
            return errors;
        }
        Map<String, Integer> firstRowByToken = new HashMap<>();
        Set<String> duplicateTokensInFile = new HashSet<>();
        Set<String> allTokens = new HashSet<>();
        for (ServiceCatalogImportRow row : rows) {
            this.validateRequired(row, errors);
            this.validateNumbers(row, errors);
            this.validateTerminology(row, index, errors);
            this.validateLimitPrice(row, errors);
            // 术语解析成功后才用 name+categoryCode 建 token；未解析成功的行跳过查重
            String token = ServiceCatalogImportRowValidator.duplicateKeyToken(
                row.getName(), row.getCategoryCode());
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
        for (ServiceCatalogImportRow row : rows) {
            String token = ServiceCatalogImportRowValidator.duplicateKeyToken(
                row.getName(), row.getCategoryCode());
            if (StringUtils.isEmpty(token)) {
                continue;
            }
            if (duplicateTokensInFile.contains(token)) {
                errors.add(this.rowError(
                    row.getExcelRowNum(),
                    "诊疗名称",
                    ServiceCatalogImportConstants.ERR_DUP_FILE,
                    "文件内存在重复的诊疗项目（名称+目录分类）"));
            }
        }
        if (orgId != null && !allTokens.isEmpty()) {
            Set<String> existing = queryService.findExistingDuplicateKeyTokens(orgId, allTokens);
            for (ServiceCatalogImportRow row : rows) {
                String token = ServiceCatalogImportRowValidator.duplicateKeyToken(
                    row.getName(), row.getCategoryCode());
                if (StringUtils.isEmpty(token)) {
                    continue;
                }
                if (existing.contains(token)) {
                    errors.add(this.rowError(
                        row.getExcelRowNum(),
                        "诊疗名称",
                        ServiceCatalogImportConstants.ERR_DUP_DB,
                        "库内已存在相同诊疗项目（名称+目录分类）"));
                }
            }
        }
        return errors;
    }

    /**
     * 构造库内/文件内唯一键 token（名称 + 目录分类持久化码）。
     *
     * @param name         诊疗名称
     * @param categoryCode 目录分类编码（术语解析成功后）
     * @return token；名称或分类码为空时返回 {@code null}
     */
    public static String duplicateKeyToken(String name, String categoryCode) {
        String n = "";
        if (name != null) {
            n = name.trim();
        }
        String c = "";
        if (categoryCode != null) {
            c = categoryCode.trim();
        }
        if (n.isEmpty() || c.isEmpty()) {
            return null;
        }
        return n + '\u0001' + c;
    }

    /**
     * 校验必填列。
     */
    private void validateRequired(
        ServiceCatalogImportRow row,
        List<ImportServiceCatalogErrorItemDto> errors
    ) {
        this.assertRequired(row, row.getName(), "诊疗名称", errors);
        this.assertRequired(row, row.getCategory(), "目录分类", errors);
        this.assertRequired(row, row.getActivityType(), "业务类型", errors);
        this.assertRequired(row, row.getPackageUnit(), "销售/计价单位", errors);
        this.assertRequired(row, row.getFinType(), "财务类型", errors);
        this.assertRequired(row, row.getRetailPriceText(), "零售价", errors);
    }

    /**
     * 校验数值列并回填解析结果。
     */
    private void validateNumbers(ServiceCatalogImportRow row, List<ImportServiceCatalogErrorItemDto> errors) {
        BigDecimal retail = this.parseNonNegativeDecimal(row, row.getRetailPriceText(), "零售价", errors);
        if (retail != null) {
            row.setRetailPrice(retail);
        }
        if (StringUtils.isNotEmpty(row.getPurchasePriceText())) {
            BigDecimal purchase = this.parseNonNegativeDecimal(row, row.getPurchasePriceText(), "成本价", errors);
            if (purchase != null) {
                row.setPurchasePrice(purchase);
            }
        }
        if (StringUtils.isNotEmpty(row.getMaximumRetailPriceText())) {
            BigDecimal limit = this.parseNonNegativeDecimal(row, row.getMaximumRetailPriceText(), "最高零售价", errors);
            if (limit != null) {
                row.setMaximumRetailPrice(limit);
            }
        }
    }

    /**
     * 校验术语列并回填 code / 枚举 / 是否标志。
     */
    private void validateTerminology(
        ServiceCatalogImportRow row,
        ServiceCatalogImportTerminologyIndex index,
        List<ImportServiceCatalogErrorItemDto> errors
    ) {
        this.resolveTerm(row, index, ServiceCatalogImportTerminologyIndex.DICT_CATEGORY,
            row.getCategory(), "目录分类", errors, ResolvedField.CATEGORY);
        this.resolveActivityType(row, index, errors);
        this.resolveTerm(row, index, ServiceCatalogImportTerminologyIndex.DICT_UNIT,
            row.getPackageUnit(), "销售/计价单位", errors, ResolvedField.PACKAGE_UNIT);
        this.resolveTerm(row, index, ServiceCatalogImportTerminologyIndex.DICT_FIN_TYPE,
            row.getFinType(), "财务类型", errors, ResolvedField.FIN_TYPE);

        if (StringUtils.isNotEmpty(row.getYbType())) {
            this.resolveTerm(row, index, ServiceCatalogImportTerminologyIndex.DICT_YB_TYPE,
                row.getYbType(), "医保类别", errors, ResolvedField.YB_TYPE);
        }
        if (StringUtils.isNotEmpty(row.getChrgitmLvText())) {
            this.resolveChrgitmLv(row, index, errors);
        }
        if (StringUtils.isNotEmpty(row.getMrSettlementType())) {
            this.resolveTerm(row, index, ServiceCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT,
                row.getMrSettlementType(), "病案结算类型", errors, ResolvedField.MR_SETTLEMENT);
        }

        // 医保标志空默认 0；对码空默认 null
        row.setYbFlag(this.resolveYesNoDefaultZero(row, row.getYbFlagText(), "医保标志", errors));
        row.setYbMatchFlag(this.resolveYesNoOptional(row, row.getYbMatchFlagText(), "医保对码标记", errors));
    }

    /**
     * 解析业务类型为 typeEnum（ActivityType code → Integer）。
     */
    private void resolveActivityType(
        ServiceCatalogImportRow row,
        ServiceCatalogImportTerminologyIndex index,
        List<ImportServiceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(row.getActivityType())) {
            return;
        }
        String code = index.resolveCode(
            ServiceCatalogImportTerminologyIndex.DICT_ACTIVITY_TYPE, row.getActivityType().trim());
        if (code == null) {
            String normalized = this.normalizeNumericText(row.getActivityType().trim());
            code = index.resolveCode(ServiceCatalogImportTerminologyIndex.DICT_ACTIVITY_TYPE, normalized);
        }
        if (code == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "业务类型",
                ServiceCatalogImportConstants.ERR_TERMINOLOGY,
                "业务类型无法识别：" + row.getActivityType()));
            return;
        }
        try {
            row.setTypeEnum(Integer.valueOf(code.trim()));
        } catch (NumberFormatException ex) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "业务类型",
                ServiceCatalogImportConstants.ERR_TERMINOLOGY,
                "业务类型编码非法：" + code));
        }
    }

    /**
     * 解析医保等级为 Integer。
     */
    private void resolveChrgitmLv(
        ServiceCatalogImportRow row,
        ServiceCatalogImportTerminologyIndex index,
        List<ImportServiceCatalogErrorItemDto> errors
    ) {
        String code = index.resolveCode(
            ServiceCatalogImportTerminologyIndex.DICT_CHRGITM_LV, row.getChrgitmLvText());
        if (code == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "医保等级",
                ServiceCatalogImportConstants.ERR_TERMINOLOGY,
                "医保等级无法识别：" + row.getChrgitmLvText()));
            return;
        }
        try {
            row.setChrgitmLv(Integer.valueOf(code.trim()));
        } catch (NumberFormatException ex) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "医保等级",
                ServiceCatalogImportConstants.ERR_TERMINOLOGY,
                "医保等级编码非法：" + code));
        }
    }

    /**
     * 最高零售价不得低于零售价。
     */
    private void validateLimitPrice(ServiceCatalogImportRow row, List<ImportServiceCatalogErrorItemDto> errors) {
        if (row.getMaximumRetailPrice() == null || row.getRetailPrice() == null) {
            return;
        }
        if (row.getMaximumRetailPrice().compareTo(row.getRetailPrice()) < 0) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "最高零售价",
                ServiceCatalogImportConstants.ERR_LIMIT_PRICE,
                "最高零售价不能低于零售价"));
        }
    }

    /**
     * 必填断言。
     */
    private void assertRequired(
        ServiceCatalogImportRow row,
        String value,
        String columnName,
        List<ImportServiceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(value)) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                columnName,
                ServiceCatalogImportConstants.ERR_REQUIRED,
                columnName + "不能为空"));
        }
    }

    /**
     * 解析非负小数。
     */
    private BigDecimal parseNonNegativeDecimal(
        ServiceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportServiceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        BigDecimal value = this.toDecimal(raw);
        if (value == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, ServiceCatalogImportConstants.ERR_NUMBER,
                columnName + "格式非法"));
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, ServiceCatalogImportConstants.ERR_NUMBER,
                columnName + "不能为负数"));
            return null;
        }
        return value;
    }

    /**
     * 解析术语并回填。
     */
    private void resolveTerm(
        ServiceCatalogImportRow row,
        ServiceCatalogImportTerminologyIndex index,
        String dictKey,
        String raw,
        String columnName,
        List<ImportServiceCatalogErrorItemDto> errors,
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
                ServiceCatalogImportConstants.ERR_TERMINOLOGY,
                columnName + "无法识别：" + raw));
            return;
        }
        this.applyResolvedCode(row, field, code);
    }

    /**
     * 是/否：空白默认 0。
     */
    private Integer resolveYesNoDefaultZero(
        ServiceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportServiceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return Integer.valueOf(0);
        }
        return this.parseYesNoInteger(row, raw, columnName, errors);
    }

    /**
     * 是/否：空白保持 null（可选）。
     */
    private Integer resolveYesNoOptional(
        ServiceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportServiceCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        return this.parseYesNoInteger(row, raw, columnName, errors);
    }

    /**
     * 解析是/否文案为 Integer 1/0。
     */
    private Integer parseYesNoInteger(
        ServiceCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportServiceCatalogErrorItemDto> errors
    ) {
        String normalized = raw.trim();
        if ("是".equals(normalized) || "Y".equalsIgnoreCase(normalized) || "YES".equalsIgnoreCase(normalized)
            || "1".equals(normalized) || "true".equalsIgnoreCase(normalized)) {
            return Integer.valueOf(1);
        }
        if ("否".equals(normalized) || "N".equalsIgnoreCase(normalized) || "NO".equalsIgnoreCase(normalized)
            || "0".equals(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Integer.valueOf(0);
        }
        errors.add(this.rowError(
            row.getExcelRowNum(),
            columnName,
            ServiceCatalogImportConstants.ERR_TERMINOLOGY,
            columnName + "只能填写是或否"));
        return Integer.valueOf(0);
    }

    /**
     * 将解析到的 code 写入行模型。
     */
    private void applyResolvedCode(ServiceCatalogImportRow row, ResolvedField field, String code) {
        if (field == ResolvedField.CATEGORY) {
            row.setCategoryCode(code);
            return;
        }
        if (field == ResolvedField.PACKAGE_UNIT) {
            row.setPackageUnitCode(code);
            return;
        }
        if (field == ResolvedField.FIN_TYPE) {
            row.setItemTypeCode(code);
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
    private ImportServiceCatalogErrorItemDto batchError(String code, String message) {
        ImportServiceCatalogErrorItemDto item = new ImportServiceCatalogErrorItemDto();
        item.setCode(code);
        item.setMessage(message);
        return item;
    }

    /**
     * 构造行级错误。
     */
    private ImportServiceCatalogErrorItemDto rowError(
        Integer excelRowNum,
        String columnName,
        String code,
        String message
    ) {
        ImportServiceCatalogErrorItemDto item = new ImportServiceCatalogErrorItemDto();
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
        /** 目录分类 → categoryCode。 */
        CATEGORY,
        /** 销售/计价单位 → packageUnitCode。 */
        PACKAGE_UNIT,
        /** 财务类型 → itemTypeCode。 */
        FIN_TYPE,
        /** 病案结算类型。 */
        MR_SETTLEMENT,
        /** 医保类别。 */
        YB_TYPE
    }
}
