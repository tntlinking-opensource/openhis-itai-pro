/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.administration.product.service.ProductDefinitionQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 产品定义展示：术语码表预热与批量回填（B.6.1 / B.10.8.1）。
 */
@Component
@RequiredArgsConstructor
public class ProductDefinitionDisplayTerminologyAssembler {

    /** 产品定义读侧批量加载。 */
    private final ProductDefinitionQueryService productDefinitionQueryService;
    /** 术语码表预热（单位、类目、用法等）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 频次展示码表：cus_frequency 主数据。 */
    private final FrequencyOptionLoader frequencyOptionLoader;

    /** 选品术语映射软缓存（短 TTL，避免每页重建十余张码表）。 */
    private volatile ProductDefinitionSelectionTerminologyMaps cachedSelectionMaps;

    /** 软缓存写入时刻（epoch ms）。 */
    private volatile long cachedSelectionMapsAtMs;

    /**
     * 加载产品展示所需的术语映射（每请求/每页调用一次即可；进程内短窗复用）。
     *
     * @return 预热后的术语映射容器
     */
    public ProductDefinitionSelectionTerminologyMaps loadSelectionTerminologyMaps() {
        long nowMs = System.currentTimeMillis();
        ProductDefinitionSelectionTerminologyMaps cached = this.cachedSelectionMaps;
        if (cached != null
            && nowMs - this.cachedSelectionMapsAtMs
                < GeneralSharedConstants.PRODUCT_DEFINITION_SELECTION_TERMINOLOGY_CACHE_TTL_MS) {
            return cached;
        }
        synchronized (this) {
            cached = this.cachedSelectionMaps;
            nowMs = System.currentTimeMillis();
            if (cached != null
                && nowMs - this.cachedSelectionMapsAtMs
                    < GeneralSharedConstants.PRODUCT_DEFINITION_SELECTION_TERMINOLOGY_CACHE_TTL_MS) {
                return cached;
            }
            ProductDefinitionSelectionTerminologyMaps maps = this.buildSelectionTerminologyMaps();
            this.cachedSelectionMaps = maps;
            this.cachedSelectionMapsAtMs = System.currentTimeMillis();
            return maps;
        }
    }

    /**
     * 实际组装选品术语映射（单位/类目/用法/频次等）。
     *
     * @return 新映射容器
     */
    private ProductDefinitionSelectionTerminologyMaps buildSelectionTerminologyMaps() {
        ProductDefinitionSelectionTerminologyMaps maps = new ProductDefinitionSelectionTerminologyMaps();
        maps.setUnitLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE));
        maps.setDoseUnitLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT));
        maps.setMedicationCategoryLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE));
        maps.setActivityCategoryLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE));
        maps.setDeviceCategoryLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE));
        maps.setMethodLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE));
        maps.setRateLabelByCode(frequencyOptionLoader.buildRateLabelMap());
        maps.setDoseFormLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_FORM_CODE));
        maps.setDosageInstructionLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION));
        maps.setRestrictedEnumLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.RESTRICTED_ENUM));
        maps.setPartAttributeLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.PART_ATTRIBUTE_ENUM));
        maps.setThoPartAttributeLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.THO_PART_ATTRIBUTE_ENUM));
        maps.setChrgitmLvLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.CHRGITM_LV));
        return maps;
    }

    /**
     * L1 batch hydrate + 术语展示回填（替代原 {@code ProductDefinitionBatchLoader#loadAsMap}）。
     *
     * @param lookups 表名 + 定义主键列表；空或无效项跳过
     * @return 复合键 → {@link ProductDefinitionDisplayDto}；入参空时返回空 Map
     */
    public Map<String, ProductDefinitionDisplayDto> loadAsMap(List<ProductDefinitionBatchLookup> lookups) {
        Map<String, ProductDefinitionDisplayDto> result = productDefinitionQueryService.batchLoadByKeys(lookups);
        if (!result.isEmpty()) {
            this.enrichDisplayDtos(result.values());
        }
        return result;
    }

    /**
     * 批量回填 {@link ProductDefinitionDisplayDto} 各字典展示列。
     *
     * @param rows 展示行集合
     */
    public void enrichDisplayDtos(Collection<ProductDefinitionDisplayDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        ProductDefinitionSelectionTerminologyMaps maps = this.loadSelectionTerminologyMaps();
        enrichDisplayTerminologyAll(rows, maps);
    }

    /** 遍历集合，逐行回填字典展示列。 */
    private static void enrichDisplayTerminologyAll(
        Collection<ProductDefinitionDisplayDto> rows,
        ProductDefinitionSelectionTerminologyMaps maps) {
        if (rows == null || rows.isEmpty() || maps == null) {
            return;
        }
        for (ProductDefinitionDisplayDto row : rows) {
            enrichDisplayTerminology(row, maps);
        }
    }

    /** 单行展示 DTO：按产品表分流回填类目、单位、频次、剂型等字典文本。 */
    private static void enrichDisplayTerminology(
        ProductDefinitionDisplayDto row,
        ProductDefinitionSelectionTerminologyMaps maps) {
        if (row == null || maps == null) {
            return;
        }
        row.setCategoryCodeDictText(ProductDefinitionCatalogUnitBandsAssembler.resolveProductCategoryDictText(
            row.getProductTableName(), row.getCategoryCode(), maps));

        applyUnitDictText(row, maps.getUnitLabelByCode());

        // 医保等级文案：药品/诊疗/耗材共用 CHRGITM_LV，须在按表分流 return 前统一回填
        if (row.getChrgitmLv() == null
            || maps.getChrgitmLvLabelByCode() == null
            || maps.getChrgitmLvLabelByCode().isEmpty()) {
            row.setChrgitmLvText(null);
        } else {
            row.setChrgitmLvText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                maps.getChrgitmLvLabelByCode(), String.valueOf(row.getChrgitmLv())));
        }

        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getProductTableName())) {
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTableName())) {
                if (StringUtils.isEmpty(row.getCategoryCodeDictText())) {
                    row.setCategoryCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                        maps.getActivityCategoryLabelByCode(), row.getCategoryCode()));
                }
            }
            return;
        }

        row.setRateCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getRateLabelByCode(), row.getRateCode()));
        row.setMethodCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getMethodLabelByCode(), row.getMethodCode()));
        row.setDoseFormCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getDoseFormLabelByCode(), row.getDoseFormCode()));
        row.setDosageInstructionDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getDosageInstructionLabelByCode(), row.getDosageInstruction()));
        row.setRestrictedEnumDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getRestrictedEnumLabelByCode(), row.getRestrictedEnum()));
        row.setPartAttributeEnumDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getPartAttributeLabelByCode(), row.getPartAttributeEnum()));
        row.setThoPartAttributeEnumDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getThoPartAttributeLabelByCode(), row.getThoPartAttributeEnum()));

        if (StringUtils.isNotEmpty(row.getDoseUnitCode())) {
            String doseName = OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                maps.getDoseUnitLabelByCode(), row.getDoseUnitCode());
            if (StringUtils.isEmpty(doseName)) {
                doseName = OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                    maps.getUnitLabelByCode(), row.getDoseUnitCode());
            }
            row.setDoseUnitCodeDictText(doseName);
        }
    }

    /** 回填采购/拆零/包装/库存/默认开立等各槽单位展示名。 */
    private static void applyUnitDictText(
        ProductDefinitionDisplayDto row,
        java.util.Map<String, String> unitLabelByCode) {
        row.setPurchaseUnitCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getPurchaseUnitCode()));
        row.setSplitUnitCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getSplitUnitCode()));
        row.setPackageUnitCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getPackageUnitCode()));
        row.setStockUnitCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getStockUnitCode()));
        row.setDefEncounterUnitCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getDefEncounterUnitCode()));
        row.setDefInhospitalUnitCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getDefInhospitalUnitCode()));
        // 追溯码贴码单位与业务单位同属 UNIT_CODE 术语
        row.setTraceUnitCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getTraceUnitCode()));
    }

    /**
     * 库房/进货选品：对已组装的单位槽按 {@code UNIT_CODE} 术语回填 {@code unitName}（B.10.8.1）。
     *
     * @param bands       进货四槽列表
     * @param productRow  产品展示 DTO
     * @param maps        预热术语映射；为 null 时跳过
     */
    public void applyInboundUnitBandDisplayNames(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        ProductDefinitionDisplayDto productRow,
        ProductDefinitionSelectionTerminologyMaps maps
    ) {
        ProductDefinitionCatalogUnitBandsAssembler.applyInboundUnitBandDisplayNames(bands, productRow, maps);
    }
}
