/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.support;

import com.zxplat.application.basic.medicationcatalog.dto.ImportMedicationCatalogErrorItemDto;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.MedicationCatalogImportConstants;
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
 * 药品目录导入行校验器（模板 v1.0）：收集全量错误，不短路；通过后回填解析字段。
 * <p>DDD 值为自由文字。文件内同键只保留首行；库内同键标记跳过（不报错、不更新）。</p>
 */
@Service
@RequiredArgsConstructor
public class MedicationCatalogImportRowValidator {

    /** 按 Excel 厂家文案批量对齐生产厂家主键，供库内名称键比对。 */
    private final SupplierQueryService supplierQueryService;

    /**
     * 校验全部导入行并收集错误；无错误时各行解析字段已填充。
     *
     * @param rows         解析行
     * @param orgId        机构主键（库内唯一键）
     * @param index        术语索引（须已 load）
     * @param queryService 药品定义读侧（库内重复键）
     * @return 错误列表；空表示通过
     */
    public List<ImportMedicationCatalogErrorItemDto> validate(
        List<MedicationCatalogImportRow> rows,
        Long orgId,
        MedicationCatalogImportTerminologyIndex index,
        MedicationDefinitionQueryService queryService
    ) {
        List<ImportMedicationCatalogErrorItemDto> errors = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            errors.add(this.batchError(
                MedicationCatalogImportConstants.ERR_NO_DATA,
                "导入药品目录失败：文件中没有有效数据行"));
            return errors;
        }
        if (rows.size() > MedicationCatalogImportConstants.IMPORT_MAX_ROWS) {
            errors.add(this.batchError(
                MedicationCatalogImportConstants.ERR_TOO_MANY_ROWS,
                "导入药品目录失败：有效行数超过上限 "
                    + MedicationCatalogImportConstants.IMPORT_MAX_ROWS));
            return errors;
        }
        for (MedicationCatalogImportRow row : rows) {
            this.validateRequired(row, errors);
            this.validateNumbers(row, errors);
            this.validateTerminology(row, index, errors);
            this.validateRatioRules(row);
            this.validateLimitPrice(row, errors);
        }
        this.markSkipExisting(rows, orgId, queryService);
        return errors;
    }

    /**
     * 同键行标记跳过：文件内只保留第一行；库内已有同键也不新建。已有目录的字段和价格不改。
     *
     * <p>有院内旧编码时旧编码即同键。没有旧编码时，文件内按厂家文案，库内按厂家主键
     * （文案先对齐主数据）。名称键在有旧编码时仍然参与，避免第二次导入漏填旧编码又开一条。</p>
     *
     * @param rows         解析行
     * @param orgId        机构主键；空则库内比对不按机构收窄
     * @param queryService 药品定义读侧
     */
    private void markSkipExisting(
        List<MedicationCatalogImportRow> rows,
        Long orgId,
        MedicationDefinitionQueryService queryService
    ) {
        Set<String> seenFileTokens = new HashSet<>();
        List<MedicationCatalogImportRow> pendingDb = new ArrayList<>();
        for (MedicationCatalogImportRow row : rows) {
            if (row == null) {
                continue;
            }
            if (this.consumeFileDuplicate(row, seenFileTokens)) {
                row.setSkipExisting(true);
                continue;
            }
            pendingDb.add(row);
        }
        Map<String, Long> manufacturerIds = this.loadManufacturerIds(pendingDb);
        Set<String> queryTokens = new HashSet<>();
        for (MedicationCatalogImportRow row : pendingDb) {
            for (String token : this.databaseDuplicateTokens(row, manufacturerIds)) {
                queryTokens.add(token);
            }
        }
        if (queryTokens.isEmpty()) {
            return;
        }
        Set<String> existing = queryService.findExistingDuplicateKeyTokens(orgId, queryTokens);
        for (MedicationCatalogImportRow row : pendingDb) {
            for (String token : this.databaseDuplicateTokens(row, manufacturerIds)) {
                if (existing.contains(token)) {
                    row.setSkipExisting(true);
                    break;
                }
            }
        }
    }

    /**
     * 文件内同键：已出现过则本行跳过，否则把本行的键记入已见集合。
     *
     * @param row            当前行
     * @param seenFileTokens 本文件已保留行的键（就地写入）
     * @return true 表示本行与更早的行同键，应跳过
     */
    private boolean consumeFileDuplicate(MedicationCatalogImportRow row, Set<String> seenFileTokens) {
        List<String> tokens = this.fileDuplicateTokens(row);
        boolean duplicated = false;
        for (String token : tokens) {
            if (seenFileTokens.contains(token)) {
                duplicated = true;
                break;
            }
        }
        if (duplicated) {
            return true;
        }
        for (String token : tokens) {
            seenFileTokens.add(token);
        }
        return false;
    }

    /**
     * 文件内判重键。旧编码键与名称键（厂家用 Excel 文案）都加入，任一命中即视为同一行。
     *
     * @param row 导入行
     * @return 本行文件内键；皆空时为空列表
     */
    private List<String> fileDuplicateTokens(MedicationCatalogImportRow row) {
        List<String> tokens = new ArrayList<>();
        String legacyToken = MedicationCatalogImportConstants.legacyDuplicateToken(row.getLegacyCode());
        if (legacyToken != null) {
            tokens.add("F:" + legacyToken);
        }
        String nameToken = MedicationCatalogImportConstants.nameDuplicateToken(
            row.getName(), row.getManufacturerText(), row.getTotalVolume());
        if (nameToken != null) {
            tokens.add("F:" + nameToken);
        }
        return tokens;
    }

    /**
     * 库内判重键。旧编码键与名称键（厂家用主键）都加入。厂家文案尚未建档时不发名称键，避免空主键误撞。
     *
     * @param row             导入行
     * @param manufacturerIds 厂家文案 → 主键
     * @return 本行库内键
     */
    private List<String> databaseDuplicateTokens(
        MedicationCatalogImportRow row,
        Map<String, Long> manufacturerIds
    ) {
        List<String> tokens = new ArrayList<>();
        String legacyToken = MedicationCatalogImportConstants.legacyDuplicateToken(row.getLegacyCode());
        if (legacyToken != null) {
            tokens.add(legacyToken);
        }
        String manufacturerSegment = this.manufacturerIdSegment(row.getManufacturerText(), manufacturerIds);
        if (manufacturerSegment == null) {
            return tokens;
        }
        String nameToken = MedicationCatalogImportConstants.nameDuplicateToken(
            row.getName(), manufacturerSegment, row.getTotalVolume());
        if (nameToken != null) {
            tokens.add(nameToken);
        }
        return tokens;
    }

    /**
     * 厂家文案转库内名称键的厂家段。
     *
     * @param manufacturerText  Excel 厂家文案
     * @param manufacturerIds   已解析的厂家主键
     * @return 空文案返回空串（对齐 manufacturerId 为空的目录）；有文案但主数据没有该厂家时返回 null
     */
    private String manufacturerIdSegment(String manufacturerText, Map<String, Long> manufacturerIds) {
        String trimmed = "";
        if (manufacturerText != null) {
            trimmed = manufacturerText.trim();
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        Long manufacturerId = null;
        if (manufacturerIds != null) {
            manufacturerId = manufacturerIds.get(trimmed);
        }
        if (manufacturerId == null) {
            return null;
        }
        return String.valueOf(manufacturerId);
    }

    /**
     * 收集待比对行的厂家文案并一次性对齐主键。
     *
     * @param rows 尚未因文件内重复跳过的行
     * @return 厂家文案 → 主键；没有厂家文案时为空 Map
     */
    private Map<String, Long> loadManufacturerIds(List<MedicationCatalogImportRow> rows) {
        Set<String> names = new HashSet<>();
        for (MedicationCatalogImportRow row : rows) {
            if (row == null || row.getManufacturerText() == null) {
                continue;
            }
            String trimmed = row.getManufacturerText().trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        if (names.isEmpty()) {
            return new HashMap<>();
        }
        return this.supplierQueryService.findManufacturerIdsByExactNames(names);
    }

    private void validateRequired(
        MedicationCatalogImportRow row,
        List<ImportMedicationCatalogErrorItemDto> errors
    ) {
        this.assertRequired(row, row.getName(), "药品名称", errors);
        this.assertRequired(row, row.getMerchandiseName(), "商品名", errors);
        this.assertRequired(row, row.getCategory(), "药品分类", errors);
        this.assertRequired(row, row.getManufacturerText(), "生产厂家", errors);
        this.assertRequired(row, row.getFinType(), "财务类型", errors);
        this.assertRequired(row, row.getPharmacologyCategory(), "药品性质", errors);
        this.assertRequired(row, row.getTotalVolume(), "规格", errors);
        this.assertRequired(row, row.getDoseUnit(), "剂量单位", errors);
        this.assertRequired(row, row.getDoseUnitRatioText(), "剂量/拆零倍数", errors);
        this.assertRequired(row, row.getDoseForm(), "剂型", errors);
        this.assertRequired(row, row.getPurchaseUnit(), "采购单位", errors);
        this.assertRequired(row, row.getSplitUnit(), "拆零单位", errors);
        this.assertRequired(row, row.getPurchaseUnitRatioText(), "采购/拆零倍数", errors);
        this.assertRequired(row, row.getPackageUnit(), "包装单位", errors);
        this.assertRequired(row, row.getPackageUnitRatioText(), "包装/拆零倍数", errors);
        this.assertRequired(row, row.getTraceUnit(), "追溯码单位", errors);
        this.assertRequired(row, row.getTraceUnitRatioText(), "追溯码/拆零倍数", errors);
        this.assertRequired(row, row.getStockUnit(), "库存单位", errors);
        this.assertRequired(row, row.getStockUnitRatioText(), "库存/拆零倍数", errors);
        this.assertRequired(row, row.getPurchasePriceText(), "购入价", errors);
        this.assertRequired(row, row.getRetailPriceText(), "零售价", errors);
        this.assertRequired(row, row.getPartAttribute(), "门诊拆分属性", errors);
        this.assertRequired(row, row.getThoPartAttribute(), "住院临时医嘱拆分属性", errors);
    }

    private void validateNumbers(MedicationCatalogImportRow row, List<ImportMedicationCatalogErrorItemDto> errors) {
        BigDecimal doseRatio = this.parsePositiveDecimal(row, row.getDoseUnitRatioText(), "剂量/拆零倍数", errors);
        if (doseRatio != null) {
            row.setDoseUnitRatio(doseRatio);
        }
        BigDecimal purchaseRatio = this.parsePositiveDecimal(row, row.getPurchaseUnitRatioText(), "采购/拆零倍数", errors);
        if (purchaseRatio != null) {
            row.setPurchaseUnitRatio(purchaseRatio);
        }
        BigDecimal packageRatio = this.parsePositiveDecimal(row, row.getPackageUnitRatioText(), "包装/拆零倍数", errors);
        if (packageRatio != null) {
            row.setPackageUnitRatio(packageRatio);
        }
        BigDecimal traceRatio = this.parsePositiveDecimal(row, row.getTraceUnitRatioText(), "追溯码/拆零倍数", errors);
        if (traceRatio != null) {
            row.setTraceUnitRatio(traceRatio);
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
        if (StringUtils.isNotEmpty(row.getDoseText())) {
            BigDecimal dose = this.parseNonNegativeDecimal(row, row.getDoseText(), "单次剂量", errors);
            if (dose != null) {
                row.setDose(dose);
            }
        }
        if (StringUtils.isNotEmpty(row.getMinUnitText())) {
            BigDecimal minUnit = this.parseNonNegativeDecimal(row, row.getMinUnitText(), "单次最小剂量", errors);
            if (minUnit != null) {
                row.setMinUnit(minUnit);
            }
        }
        if (StringUtils.isNotEmpty(row.getMaxUnitText())) {
            BigDecimal maxUnit = this.parseNonNegativeDecimal(row, row.getMaxUnitText(), "单次最大剂量", errors);
            if (maxUnit != null) {
                row.setMaxUnit(maxUnit);
            }
        }
        if (StringUtils.isNotEmpty(row.getUsageLimitText())) {
            BigDecimal usageLimit = this.parseNonNegativeDecimal(row, row.getUsageLimitText(), "用量限定", errors);
            if (usageLimit != null) {
                row.setUsageLimit(usageLimit);
            }
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

    private void validateTerminology(
        MedicationCatalogImportRow row,
        MedicationCatalogImportTerminologyIndex index,
        List<ImportMedicationCatalogErrorItemDto> errors
    ) {
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_CATEGORY,
            row.getCategory(), "药品分类", errors, ResolvedField.CATEGORY);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_FIN_TYPE,
            row.getFinType(), "财务类型", errors, ResolvedField.FIN_TYPE);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_MEDICINE_PROPERTIES,
            row.getPharmacologyCategory(), "药品性质", errors, ResolvedField.PHARMACOLOGY);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_DOSE_UNIT,
            row.getDoseUnit(), "剂量单位", errors, ResolvedField.DOSE_UNIT);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_DOSE_FORM,
            row.getDoseForm(), "剂型", errors, ResolvedField.DOSE_FORM);
        // 采购/拆零/包装/库存：统一解析到 unit_code
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT,
            row.getPurchaseUnit(), "采购单位", errors, ResolvedField.PURCHASE_UNIT);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT,
            row.getSplitUnit(), "拆零单位", errors, ResolvedField.SPLIT_UNIT);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT,
            row.getPackageUnit(), "包装单位", errors, ResolvedField.PACKAGE_UNIT);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT,
            row.getTraceUnit(), "追溯码单位", errors, ResolvedField.TRACE_UNIT);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT,
            row.getStockUnit(), "库存单位", errors, ResolvedField.STOCK_UNIT);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_ORDER_CALC_RULE,
            row.getPartAttribute(), "门诊拆分属性", errors, ResolvedField.PART_ATTR);
        this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_ORDER_CALC_RULE,
            row.getThoPartAttribute(), "住院临时医嘱拆分属性", errors, ResolvedField.THO_PART_ATTR);

        if (StringUtils.isNotEmpty(row.getChrgitmLvText())) {
            this.resolveChrgitmLv(row, index, errors);
        }
        if (StringUtils.isNotEmpty(row.getMrSettlementType())) {
            this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT,
                row.getMrSettlementType(), "病案结算类型", errors, ResolvedField.MR_SETTLEMENT);
        }
        if (StringUtils.isNotEmpty(row.getYbType())) {
            this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_YB_TYPE,
                row.getYbType(), "医保类别", errors, ResolvedField.YB_TYPE);
        }
        if (StringUtils.isNotEmpty(row.getMethod())) {
            this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_METHOD,
                row.getMethod(), "默认用法", errors, ResolvedField.METHOD);
        }
        if (StringUtils.isNotEmpty(row.getRate())) {
            this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_RATE,
                row.getRate(), "默认频次", errors, ResolvedField.RATE);
        }
        if (StringUtils.isNotEmpty(row.getDosageInstruction())) {
            this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_DOSAGE_INSTRUCTION,
                row.getDosageInstruction(), "用法说明", errors, ResolvedField.DOSAGE_INSTR);
        }
        // DDD值：与维护页 Input 一致，自由文字直写入 dddCode，不走码表
        if (StringUtils.isNotEmpty(row.getDddCodeText())) {
            row.setDddCode(this.normalizeNumericText(row.getDddCodeText().trim()));
        }
        if (StringUtils.isNotEmpty(row.getDddUnit())) {
            this.resolveTerm(row, index, MedicationCatalogImportTerminologyIndex.DICT_DDD_UNIT,
                row.getDddUnit(), "DDD单位", errors, ResolvedField.DDD_UNIT);
        }

        row.setYbMatchFlag(this.resolveYesNoOptional(row, row.getYbMatchFlagText(), "医保对码", errors));
        row.setSkinTestFlag(this.resolveYesNoDefaultFalse(row, row.getSkinTestFlagText(), "皮试判别", errors));
        row.setInjectFlag(this.resolveYesNoDefaultFalse(row, row.getInjectFlagText(), "输液标记", errors));
        row.setBasicFlag(this.resolveYesNoDefaultFalse(row, row.getBasicFlagText(), "基药标识", errors));
        row.setChildrenFlag(this.resolveYesNoDefaultFalse(row, row.getChildrenFlagText(), "儿童用药标志", errors));
        row.setSelfFlag(this.resolveYesNoDefaultFalse(row, row.getSelfFlagText(), "自制药标识", errors));
        row.setRestrictedFlag(this.resolveYesNoDefaultFalse(row, row.getRestrictedFlagText(), "限制使用", errors));
        row.setAntibioticFlag(this.resolveYesNoDefaultFalse(row, row.getAntibioticFlagText(), "抗生素", errors));
        row.setRxFlag(this.resolveYesNoDefaultFalse(row, row.getRxFlagText(), "处方药标志", errors));
        row.setNationalMonitorFlag(this.resolveYesNoDefaultFalse(row, row.getNationalMonitorFlagText(), "国家重点监控药品", errors));
        row.setCentralPurchaseFlag(this.resolveYesNoDefaultFalse(row, row.getCentralPurchaseFlagText(), "集采药品", errors));
    }

    private void resolveChrgitmLv(
        MedicationCatalogImportRow row,
        MedicationCatalogImportTerminologyIndex index,
        List<ImportMedicationCatalogErrorItemDto> errors
    ) {
        String code = index.resolveCode(
            MedicationCatalogImportTerminologyIndex.DICT_CHRGITM_LV, row.getChrgitmLvText());
        if (code == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "医保等级",
                MedicationCatalogImportConstants.ERR_TERMINOLOGY,
                "医保等级无法识别：" + row.getChrgitmLvText()));
            return;
        }
        try {
            row.setChrgitmLv(Integer.valueOf(code.trim()));
        } catch (NumberFormatException ex) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                "医保等级",
                MedicationCatalogImportConstants.ERR_TERMINOLOGY,
                "医保等级编码非法：" + code));
        }
    }

    /**
     * 同单位倍数：按 Excel 原文 label 判定（勿用跨码表 code 字符串比较，避免 piece/box 等同码误伤）。
     * 相同时若倍数≠1，按导入口径自动纠正为 1（以文件单位为准，不阻断导入）。
     */
    private void validateRatioRules(MedicationCatalogImportRow row) {
        this.normalizeSameUnitRatioOne(
            row.getPurchaseUnit(), row.getSplitUnit(), row.getPurchaseUnitRatio(), row::setPurchaseUnitRatio);
        this.normalizeSameUnitRatioOne(
            row.getPackageUnit(), row.getSplitUnit(), row.getPackageUnitRatio(), row::setPackageUnitRatio);
        this.normalizeSameUnitRatioOne(
            row.getTraceUnit(), row.getSplitUnit(), row.getTraceUnitRatio(), row::setTraceUnitRatio);
        this.normalizeSameUnitRatioOne(
            row.getStockUnit(), row.getSplitUnit(), row.getStockUnitRatio(), row::setStockUnitRatio);
        this.normalizeSameUnitRatioOne(
            row.getDoseUnit(), row.getSplitUnit(), row.getDoseUnitRatio(), row::setDoseUnitRatio);
    }

    private void normalizeSameUnitRatioOne(
        String unitLabel,
        String splitLabel,
        BigDecimal ratio,
        java.util.function.Consumer<BigDecimal> ratioSetter
    ) {
        if (StringUtils.isEmpty(unitLabel) || StringUtils.isEmpty(splitLabel) || ratio == null) {
            return;
        }
        if (!unitLabel.trim().equals(splitLabel.trim())) {
            return;
        }
        if (ratio.compareTo(BigDecimal.ONE) != 0) {
            ratioSetter.accept(BigDecimal.ONE);
        }
    }

    /**
     * 最高零售价不得低于零售价；填 0 视为未填（与空单元格同口径）。
     */
    private void validateLimitPrice(MedicationCatalogImportRow row, List<ImportMedicationCatalogErrorItemDto> errors) {
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
                MedicationCatalogImportConstants.ERR_LIMIT_PRICE,
                "最高零售价不能低于零售价"));
        }
    }

    private void assertRequired(
        MedicationCatalogImportRow row,
        String value,
        String columnName,
        List<ImportMedicationCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(value)) {
            errors.add(this.rowError(
                row.getExcelRowNum(),
                columnName,
                MedicationCatalogImportConstants.ERR_REQUIRED,
                columnName + "不能为空"));
        }
    }

    private BigDecimal parsePositiveDecimal(
        MedicationCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportMedicationCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        BigDecimal value = this.toDecimal(raw);
        if (value == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, MedicationCatalogImportConstants.ERR_NUMBER,
                columnName + "格式非法"));
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, MedicationCatalogImportConstants.ERR_NUMBER,
                columnName + "须大于 0"));
            return null;
        }
        return value;
    }

    private BigDecimal parseNonNegativeDecimal(
        MedicationCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportMedicationCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        BigDecimal value = this.toDecimal(raw);
        if (value == null) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, MedicationCatalogImportConstants.ERR_NUMBER,
                columnName + "格式非法"));
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(this.rowError(
                row.getExcelRowNum(), columnName, MedicationCatalogImportConstants.ERR_NUMBER,
                columnName + "不能为负数"));
            return null;
        }
        return value;
    }

    private void resolveTerm(
        MedicationCatalogImportRow row,
        MedicationCatalogImportTerminologyIndex index,
        String dictKey,
        String raw,
        String columnName,
        List<ImportMedicationCatalogErrorItemDto> errors,
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
                MedicationCatalogImportConstants.ERR_TERMINOLOGY,
                columnName + "无法识别：" + raw));
            return;
        }
        this.applyResolvedCode(row, field, code);
    }

    private Boolean resolveYesNoDefaultFalse(
        MedicationCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportMedicationCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return Boolean.FALSE;
        }
        return this.parseYesNo(row, raw, columnName, errors);
    }

    private Boolean resolveYesNoOptional(
        MedicationCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportMedicationCatalogErrorItemDto> errors
    ) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        return this.parseYesNo(row, raw, columnName, errors);
    }

    private Boolean parseYesNo(
        MedicationCatalogImportRow row,
        String raw,
        String columnName,
        List<ImportMedicationCatalogErrorItemDto> errors
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
            MedicationCatalogImportConstants.ERR_TERMINOLOGY,
            columnName + "只能填写是或否"));
        return Boolean.FALSE;
    }

    private void applyResolvedCode(MedicationCatalogImportRow row, ResolvedField field, String code) {
        if (field == ResolvedField.CATEGORY) {
            row.setCategoryCode(code);
            return;
        }
        if (field == ResolvedField.FIN_TYPE) {
            row.setTypeCode(code);
            return;
        }
        if (field == ResolvedField.PHARMACOLOGY) {
            row.setPharmacologyCategoryCode(code);
            return;
        }
        if (field == ResolvedField.DOSE_UNIT) {
            row.setDoseUnitCode(code);
            return;
        }
        if (field == ResolvedField.DOSE_FORM) {
            row.setDoseFormCode(code);
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
        if (field == ResolvedField.TRACE_UNIT) {
            row.setTraceUnitCode(code);
            return;
        }
        if (field == ResolvedField.STOCK_UNIT) {
            row.setStockUnitCode(code);
            return;
        }
        if (field == ResolvedField.PART_ATTR) {
            row.setPartAttributeEnum(code);
            return;
        }
        if (field == ResolvedField.THO_PART_ATTR) {
            row.setThoPartAttributeEnum(code);
            return;
        }
        if (field == ResolvedField.MR_SETTLEMENT) {
            row.setMrSettlementTypeCode(code);
            return;
        }
        if (field == ResolvedField.YB_TYPE) {
            row.setYbTypeCode(code);
            return;
        }
        if (field == ResolvedField.METHOD) {
            row.setMethodCode(code);
            return;
        }
        if (field == ResolvedField.RATE) {
            row.setRateCode(code);
            return;
        }
        if (field == ResolvedField.DOSAGE_INSTR) {
            row.setDosageInstructionCode(code);
            return;
        }
        if (field == ResolvedField.DDD_CODE) {
            row.setDddCode(code);
            return;
        }
        if (field == ResolvedField.DDD_UNIT) {
            row.setDddUnitCode(code);
        }
    }

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

    private ImportMedicationCatalogErrorItemDto batchError(String code, String message) {
        ImportMedicationCatalogErrorItemDto item = new ImportMedicationCatalogErrorItemDto();
        item.setCode(code);
        item.setMessage(message);
        return item;
    }

    private ImportMedicationCatalogErrorItemDto rowError(
        Integer excelRowNum,
        String columnName,
        String code,
        String message
    ) {
        ImportMedicationCatalogErrorItemDto item = new ImportMedicationCatalogErrorItemDto();
        item.setExcelRowNum(excelRowNum);
        item.setColumnName(columnName);
        item.setCode(code);
        item.setMessage(message);
        return item;
    }

    private enum ResolvedField {
        CATEGORY,
        FIN_TYPE,
        PHARMACOLOGY,
        DOSE_UNIT,
        DOSE_FORM,
        PURCHASE_UNIT,
        SPLIT_UNIT,
        PACKAGE_UNIT,
        TRACE_UNIT,
        STOCK_UNIT,
        PART_ATTR,
        THO_PART_ATTR,
        MR_SETTLEMENT,
        YB_TYPE,
        METHOD,
        RATE,
        DOSAGE_INSTR,
        DDD_CODE,
        DDD_UNIT
    }
}
