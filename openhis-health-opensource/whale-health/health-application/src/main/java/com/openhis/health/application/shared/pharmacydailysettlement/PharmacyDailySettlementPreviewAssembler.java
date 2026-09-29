/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.pharmacydailysettlement;

import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.enums.KeyDrugType;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 药房日结预览主区装配辅助（大类/品规/待交明细）。
 *
 * <p>大类/品规进/售净额经 {@link PharmacyDailySettlementPricingSupport} 批号价口径装配；
 * 品规分桶键含进/售单价（同价合并、不同价分行，DD §2.8.1）。</p>
 */
@Component
@RequiredArgsConstructor
public class PharmacyDailySettlementPreviewAssembler {

    /** 进/售读价与品规展示 enrich。 */
    private final PharmacyDailySettlementPricingSupport pharmacyDailySettlementPricingSupport;

    /** 固定大类展示顺序（DD §2.8.1：2/1/4/3）。 */
    private static final String[][] FIXED_CATEGORY_ROWS = {
        {DailySettlementSharedConstants.MEDICATION_CATEGORY_WESTERN_CODE, "西药"},
        {DailySettlementSharedConstants.MEDICATION_CATEGORY_PATENT_CHINESE_CODE, "中成药"},
        {DailySettlementSharedConstants.MEDICATION_CATEGORY_HERBAL_CODE, "中草药"},
        {DailySettlementSharedConstants.MEDICATION_CATEGORY_PURCHASED_CODE, "外购药品"}
    };

    /**
     * 装配固定四行大类汇总（无发生亦展示；进/售为窗内净额）。
     *
     * @param summaryDispenses 窗内展示口径发药（含已交）
     * @param categoryLabelByCode medication_category_code → 展示名；可空
     * @param inpatientMode 住院药房日结时为 true
     * @return 固定四行大类汇总
     */
    public List<PharmacyDailySettlementCategoryRowDto> assembleCategoryRows(
        List<MedicationDispense> summaryDispenses,
        Map<String, String> categoryLabelByCode,
        boolean inpatientMode) {
        PharmacyDailySettlementPricingSupport.PharmacyDailySettlementPurchaseSaleSnapshot snapshot =
            this.pharmacyDailySettlementPricingSupport.buildPurchaseSaleSnapshot(summaryDispenses, inpatientMode);
        return this.assembleCategoryRows(snapshot, categoryLabelByCode);
    }

    /**
     * 由已算好的进/售快照装配固定四行大类汇总（与品规共用一次读价）。
     *
     * @param snapshot            进/售汇总快照
     * @param categoryLabelByCode medication_category_code → 展示名；可空
     * @return 固定四行大类汇总
     */
    public List<PharmacyDailySettlementCategoryRowDto> assembleCategoryRows(
        PharmacyDailySettlementPricingSupport.PharmacyDailySettlementPurchaseSaleSnapshot snapshot,
        Map<String, String> categoryLabelByCode) {
        List<PharmacyDailySettlementCategoryRowDto> rows = new ArrayList<>();
        PharmacyDailySettlementPricingSupport.PharmacyDailySettlementPurchaseSaleSnapshot safeSnapshot = snapshot;
        if (safeSnapshot == null) {
            safeSnapshot = new PharmacyDailySettlementPricingSupport.PharmacyDailySettlementPurchaseSaleSnapshot();
        }
        for (String[] fixedRow : FIXED_CATEGORY_ROWS) {
            String categoryCode = fixedRow[0];
            String defaultLabel = fixedRow[1];
            PharmacyDailySettlementCategoryRowDto row = new PharmacyDailySettlementCategoryRowDto();
            row.setCategoryCode(categoryCode);
            row.setCategoryLabel(this.resolveCategoryLabel(categoryCode, defaultLabel, categoryLabelByCode));
            PharmacyDailySettlementPricingSupport.CategoryPurchaseSaleBucket bucket =
                safeSnapshot.getCategoryBuckets().get(categoryCode);
            if (bucket != null) {
                row.setNetQuantity(this.orZero(bucket.getNetQuantity()));
                row.setPurchaseAmount(this.orZero(bucket.getPurchaseAmount()));
                row.setSaleAmount(this.orZero(bucket.getSaleAmount()));
            } else {
                row.setNetQuantity(BigDecimal.ZERO);
                row.setPurchaseAmount(BigDecimal.ZERO);
                row.setSaleAmount(BigDecimal.ZERO);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 装配品规汇总行：同药且进/售单价一致才合并；一发一退净 0 仍展示。
     *
     * @param summaryDispenses 窗内展示口径发药
     * @param inpatientMode 住院药房日结时为 true
     * @return 品规汇总行
     */
    public List<PharmacyDailySettlementProductRowDto> assembleProductRows(
        List<MedicationDispense> summaryDispenses,
        boolean inpatientMode) {
        PharmacyDailySettlementPricingSupport.PharmacyDailySettlementPurchaseSaleSnapshot snapshot =
            this.pharmacyDailySettlementPricingSupport.buildPurchaseSaleSnapshot(summaryDispenses, inpatientMode);
        return this.assembleProductRows(snapshot);
    }

    /**
     * 由已算好的进/售快照装配品规汇总行（与大类共用一次读价）。
     *
     * @param snapshot 进/售汇总快照
     * @return 品规汇总行
     */
    public List<PharmacyDailySettlementProductRowDto> assembleProductRows(
        PharmacyDailySettlementPricingSupport.PharmacyDailySettlementPurchaseSaleSnapshot snapshot) {
        List<PharmacyDailySettlementProductRowDto> rows = new ArrayList<>();
        if (snapshot == null) {
            return rows;
        }
        for (PharmacyDailySettlementPricingSupport.ProductPurchaseSaleBucket bucket
            : snapshot.getProductBuckets().values()) {
            if (bucket == null || bucket.getMedicationId() == null) {
                continue;
            }
            // Asm：有发药事实即出一行（含净数量/进售均为 0）
            PharmacyDailySettlementProductRowDto row = new PharmacyDailySettlementProductRowDto();
            row.setMedicationId(bucket.getMedicationId());
            row.setNetQuantity(MonetaryAmountSupport.scaleCalc(bucket.getNetQuantity()));
            row.setPurchaseUnitPrice(bucket.getPurchaseUnitPrice());
            row.setSaleUnitPrice(bucket.getSaleUnitPrice());
            row.setPurchaseAmount(this.orZero(bucket.getPurchaseAmount()));
            row.setSaleAmount(this.orZero(bucket.getSaleAmount()));
            row.setMedicationName(bucket.getMedicationName());
            row.setBusNo(bucket.getBusNo());
            row.setSpecification(bucket.getSpecification());
            row.setManufacturer(bucket.getManufacturer());
            // Asm：重点药标识供品规销售排名按类型筛选（与 AS-RPT-011 KeyDrugType 同口径）
            row.setBasicFlag(bucket.getBasicFlag());
            row.setNationalMonitorFlag(bucket.getNationalMonitorFlag());
            row.setCentralPurchaseFlag(bucket.getCentralPurchaseFlag());
            row.setAntibioticFlag(bucket.getAntibioticFlag());
            row.setPharmacologyCategoryCode(bucket.getPharmacologyCategoryCode());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 装配重点药类型下拉（仅基药/监控/集采/抗生素）；麻/精见 {@link #buildMedicinePropertyOptions}。
     *
     * @return 固定四项 OptionDto
     */
    public List<OptionDto> buildKeyDrugTypeOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toKeyDrugTypeOption(KeyDrugType.BASIC));
        options.add(this.toKeyDrugTypeOption(KeyDrugType.NATIONAL_MONITOR));
        options.add(this.toKeyDrugTypeOption(KeyDrugType.CENTRAL_PURCHASE));
        options.add(this.toKeyDrugTypeOption(KeyDrugType.ANTIBIOTIC));
        return options;
    }

    /**
     * 装配药品性质下拉（麻醉/精一/精二）；与重点药类型分框。
     *
     * @return 固定三项 OptionDto
     */
    public List<OptionDto> buildMedicinePropertyOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toKeyDrugTypeOption(KeyDrugType.NARCOTIC));
        options.add(this.toKeyDrugTypeOption(KeyDrugType.PSYCH_ONE));
        options.add(this.toKeyDrugTypeOption(KeyDrugType.PSYCH_TWO));
        return options;
    }

    /**
     * KeyDrugType → OptionDto（value=code，label=display）。
     *
     * @param keyDrugType 重点药类型枚举
     * @return 下拉选项
     */
    private OptionDto toKeyDrugTypeOption(KeyDrugType keyDrugType) {
        OptionDto option = new OptionDto();
        option.setValue(keyDrugType.getCode());
        option.setLabel(keyDrugType.getDisplay());
        return option;
    }

    /**
     * 未交账发药实体 → 弹窗明细 DTO（含品规/进售/状态文案）。
     *
     * @param unsettledDispenses 未交账发药实体（顺序即弹窗顺序）
     * @param inpatientMode      住院药房日结时为 true
     * @param unitLabels         unit_code → 展示文案；可空
     * @return 待交明细
     */
    public List<PharmacyDailySettlementDispenseItemDto> toDispenseItems(
        List<MedicationDispense> unsettledDispenses,
        boolean inpatientMode,
        Map<String, String> unitLabels) {
        return this.toDispenseItems(unsettledDispenses, inpatientMode, unitLabels, null);
    }

    /**
     * 未交账发药实体 → 弹窗明细 DTO；可复用主区进/售快照行价。
     *
     * @param unsettledDispenses 未交账发药实体（顺序即弹窗顺序）
     * @param inpatientMode      住院药房日结时为 true
     * @param unitLabels         unit_code → 展示文案；可空
     * @param purchaseSaleSnapshot 主区进/售快照；可空
     * @return 待交明细
     */
    public List<PharmacyDailySettlementDispenseItemDto> toDispenseItems(
        List<MedicationDispense> unsettledDispenses,
        boolean inpatientMode,
        Map<String, String> unitLabels,
        PharmacyDailySettlementPricingSupport.PharmacyDailySettlementPurchaseSaleSnapshot purchaseSaleSnapshot) {
        List<PharmacyDailySettlementDispenseItemDto> items = new ArrayList<>();
        if (unsettledDispenses == null || unsettledDispenses.isEmpty()) {
            return items;
        }
        // Pre：优先复用主区快照行价，避免二次批号读价
        Map<Long, PharmacyDailySettlementHandoverLineEnrichment> enrichments =
            this.pharmacyDailySettlementPricingSupport.buildHandoverLineEnrichments(
                unsettledDispenses, inpatientMode, purchaseSaleSnapshot);
        for (MedicationDispense dispense : unsettledDispenses) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            PharmacyDailySettlementDispenseItemDto item = new PharmacyDailySettlementDispenseItemDto();
            item.setDispenseId(dispense.getId());
            item.setBusNo(dispense.getBusNo());
            if (dispense.getStatusEnum() != null) {
                item.setStatusEnum(dispense.getStatusEnum().getCode());
                item.setStatusEnumText(dispense.getStatusEnum().getDisplay());
            }
            if (dispense.getDispenseEnum() != null) {
                item.setDispenseEnum(dispense.getDispenseEnum().getCode());
                // 弹窗类型列：退药单独展示，其余正向发药统一「发药」
                if (MedicationDispenseType.REFUND.equals(dispense.getDispenseEnum())) {
                    item.setDispenseEnumText("退药");
                } else {
                    item.setDispenseEnumText("发药");
                }
            }
            item.setMedicationId(dispense.getMedicationId());
            item.setLocationId(dispense.getLocationId());
            item.setPractitionerId(dispense.getPractitionerId());
            item.setDispenseTime(dispense.getDispenseTime());
            item.setDispenseQuantity(dispense.getDispenseQuantity());
            item.setUnitCode(dispense.getUnitCode());
            if (unitLabels != null && !StringUtils.isEmpty(dispense.getUnitCode())) {
                String unitText = unitLabels.get(dispense.getUnitCode());
                if (!StringUtils.isEmpty(unitText)) {
                    item.setUnitText(unitText);
                } else {
                    item.setUnitText(dispense.getUnitCode());
                }
            } else if (!StringUtils.isEmpty(dispense.getUnitCode())) {
                item.setUnitText(dispense.getUnitCode());
            }
            PharmacyDailySettlementHandoverLineEnrichment enrichment = enrichments.get(dispense.getId());
            if (enrichment != null) {
                item.setMedicationBusNo(enrichment.getMedicationBusNo());
                item.setMedicationName(enrichment.getMedicationName());
                item.setSpecification(enrichment.getSpecification());
                item.setManufacturer(enrichment.getManufacturer());
                item.setPurchaseUnitPrice(enrichment.getPurchaseUnitPrice());
                item.setPurchaseAmount(enrichment.getPurchaseAmount());
                item.setSaleUnitPrice(enrichment.getSaleUnitPrice());
                item.setSaleAmount(enrichment.getSaleAmount());
            }
            items.add(item);
        }
        return items;
    }

    /**
     * 汇总待交明细实发数量（弹窗合计；退药取负）。
     *
     * @param unsettledDispenses 未交账发药实体
     * @return 数量合计；无明细时为 0
     */
    public BigDecimal sumHandoverTotalQuantity(List<MedicationDispense> unsettledDispenses) {
        BigDecimal total = BigDecimal.ZERO;
        if (unsettledDispenses == null) {
            return total;
        }
        for (MedicationDispense dispense : unsettledDispenses) {
            if (dispense == null || dispense.getDispenseQuantity() == null) {
                continue;
            }
            BigDecimal signedQuantity = this.signedDispenseQuantity(
                dispense.getDispenseEnum(),
                dispense.getDispenseQuantity());
            total = total.add(signedQuantity);
        }
        return MonetaryAmountSupport.scaleCalc(total);
    }

    /**
     * 药房实体 → OptionDto。
     *
     * @param pharmacies 药房列表
     * @return 下拉选项
     */
    public List<OptionDto> toPharmacyOptions(List<Location> pharmacies) {
        List<OptionDto> options = new ArrayList<>();
        if (pharmacies == null) {
            return options;
        }
        for (Location pharmacy : pharmacies) {
            if (pharmacy == null || pharmacy.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(pharmacy.getId()));
            option.setLabel(pharmacy.getName());
            options.add(option);
        }
        return options;
    }

    /**
     * null 金额回落为 0（固定大类行展示）。
     *
     * @param amount 可空金额
     * @return 非空金额
     */
    private BigDecimal orZero(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount;
    }

    /**
     * 退药行数量取负，正向发药取正（实体枚举口径）。
     *
     * @param dispenseEnum     发放类型
     * @param dispenseQuantity 实发数量
     * @return 带符号数量
     */
    private BigDecimal signedDispenseQuantity(MedicationDispenseType dispenseEnum, BigDecimal dispenseQuantity) {
        if (dispenseQuantity == null) {
            return BigDecimal.ZERO;
        }
        if (MedicationDispenseType.REFUND.equals(dispenseEnum)) {
            return dispenseQuantity.negate();
        }
        return dispenseQuantity;
    }

    /**
     * 退药行数量取负，正向发药取正（预览行 String code 口径）。
     *
     * @param dispenseEnum     发放类型 code
     * @param dispenseQuantity 实发数量
     * @return 带符号数量
     */
    private BigDecimal signedDispenseQuantity(String dispenseEnum, BigDecimal dispenseQuantity) {
        if (dispenseQuantity == null) {
            return BigDecimal.ZERO;
        }
        if (MedicationDispenseType.REFUND.getCode().equals(dispenseEnum)) {
            return dispenseQuantity.negate();
        }
        return dispenseQuantity;
    }

    /**
     * 解析大类展示名：术语映射优先，否则默认文案。
     *
     * @param categoryCode        大类 code
     * @param defaultLabel        默认展示名
     * @param categoryLabelByCode 术语映射
     * @return 展示名
     */
    private String resolveCategoryLabel(
        String categoryCode,
        String defaultLabel,
        Map<String, String> categoryLabelByCode) {
        if (categoryLabelByCode != null && !StringUtils.isEmpty(categoryCode)) {
            String mapped = categoryLabelByCode.get(categoryCode);
            if (!StringUtils.isEmpty(mapped)) {
                return mapped;
            }
        }
        return defaultLabel;
    }
}
