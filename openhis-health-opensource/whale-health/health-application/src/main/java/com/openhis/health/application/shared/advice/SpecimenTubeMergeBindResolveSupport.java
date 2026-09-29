/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeDerivedBindQueryModel;
import com.openhis.health.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeLineQueryModel;
import com.openhis.health.domain.custom.specimentubemerge.service.SpecimenTubeMergeQueryService;
import com.openhis.health.domain.share.constants.SpecimenTubeMergeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 标本合管派生绑定解析（Phase 2：开嘱/打印查询）。
 *
 * <p>合管配置优先于 {@code cus_service_device_template}；同一 {@code deviceId} 下采血费与耗材各只解析一份。
 * 跨聚合读经 {@link SpecimenTubeMergeQueryService}、{@link ActivityDefinitionQueryService}（B.6.4）。</p>
 */
@Component
@RequiredArgsConstructor
public class SpecimenTubeMergeBindResolveSupport {

    private final SpecimenTubeMergeQueryService specimenTubeMergeQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 按检验 activity 反查合管设备主键。
     *
     * <p>采血费 activity 即使只绑一台设备也不作为合管触发源（避免单开采血费再带一遍耗材）。</p>
     *
     * @param activityId 检验 activity 主键
     * @return 合管设备主键；未配置或目标为采血费时 empty
     */
    public Optional<Long> findMergeDeviceIdByActivityId(Long activityId) {
        if (activityId == null || this.isBloodCollectionFeeActivityId(activityId)) {
            return Optional.empty();
        }
        return specimenTubeMergeQueryService.findDeviceIdByActivityId(activityId);
    }

    /**
     * 批量反查 activity → 合管设备主键。
     *
     * <p>结果中剔除采血费 activity，避免单开/回落路径误触发合管派生耗材。</p>
     *
     * @param activityIds 检验 activity 主键集合
     * @return activityId → deviceId
     */
    public Map<Long, Long> mapMergeDeviceIdByActivityIds(Collection<Long> activityIds) {
        Map<Long, Long> mapped = specimenTubeMergeQueryService.mapDeviceIdByActivityIds(activityIds);
        Long bloodFeeActivityId = this.resolveBloodCollectionFeeActivityId();
        if (bloodFeeActivityId == null || mapped.isEmpty()) {
            return mapped;
        }
        // 采血费可跨设备复用：不得作为合管分组/派生触发键
        Map<Long, Long> result = new HashMap<>(mapped);
        result.remove(bloodFeeActivityId);
        return result;
    }

    /**
     * 判断检验 activity 是否已配置合管（归属某台设备）。
     *
     * @param activityId 检验 activity 主键
     * @return 已配置为 true（采血费恒为 false）
     */
    public boolean isActivityConfiguredForMerge(Long activityId) {
        return this.findMergeDeviceIdByActivityId(activityId).isPresent();
    }

    /**
     * 是否为合管口径下的「采血费」诊疗项目。
     *
     * @param activityId 诊疗活动主键
     * @return 名称精确匹配「采血费」且唯一解析成功时为 true
     */
    private boolean isBloodCollectionFeeActivityId(Long activityId) {
        if (activityId == null) {
            return false;
        }
        Long bloodFeeActivityId = this.resolveBloodCollectionFeeActivityId();
        return bloodFeeActivityId != null && bloodFeeActivityId.equals(activityId);
    }

    /**
     * 列出设备下开嘱应带出的派生绑定（采血费 + 耗材；不含组内检验项目 activity）。
     *
     * @param deviceId 合管设备主键
     * @return 派生绑定行（含展示 enrich）；未配置时返回空列表
     */
    public List<SpecimenTubeMergeDerivedBindQueryModel> listDerivedBindsByDeviceId(Long deviceId) {
        if (deviceId == null) {
            return List.of();
        }
        Long bloodFeeActivityId = this.resolveBloodCollectionFeeActivityId();
        List<SpecimenTubeMergeLineQueryModel> lines = specimenTubeMergeQueryService.listByDeviceId(deviceId);
        List<SpecimenTubeMergeDerivedBindQueryModel> derived = new ArrayList<>();
        for (SpecimenTubeMergeLineQueryModel line : lines) {
            if (line == null || StringUtils.isEmpty(line.getTargetTable()) || line.getTargetId() == null) {
                continue;
            }
            String targetTable = line.getTargetTable().trim();
            if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(targetTable)) {
                derived.add(this.toDerivedModel(line));
                continue;
            }
            if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(targetTable)) {
                continue;
            }
            // 组内检验项目 activity 不作为派生绑定；仅采血费 activity 可带出
            if (bloodFeeActivityId != null && bloodFeeActivityId.equals(line.getTargetId())) {
                derived.add(this.toDerivedModel(line));
            }
        }
        if (derived.isEmpty()) {
            return derived;
        }
        this.enrichDerivedBindRows(derived);
        return derived;
    }

    /**
     * 解析采血费 activity 主键（名称精确匹配「采血费」）。
     *
     * @return 采血费 activity 主键；未配置或名称不唯一时 null
     */
    public Long resolveBloodCollectionFeeActivityId() {
        try {
            return activityDefinitionQueryService.requireIdByExactName(
                SpecimenTubeMergeConstants.BLOOD_COLLECTION_FEE_ACTIVITY_NAME);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 批量 enrich 派生绑定展示名与单位文案。
     *
     * @param rows 派生绑定行
     */
    public void enrichDerivedBindRows(List<SpecimenTubeMergeDerivedBindQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        for (SpecimenTubeMergeDerivedBindQueryModel row : rows) {
            if (row == null || row.getTargetId() == null || StringUtils.isEmpty(row.getTargetTable())) {
                continue;
            }
            String table = row.getTargetTable().trim();
            String key = table + ":" + row.getTargetId();
            if (!dedupe.add(key)) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(table);
            lookup.setProductDefinitionId(row.getTargetId());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        Map<String, String> unitLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        for (SpecimenTubeMergeDerivedBindQueryModel row : rows) {
            if (row == null || row.getTargetId() == null || StringUtils.isEmpty(row.getTargetTable())) {
                continue;
            }
            String table = row.getTargetTable().trim();
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(table, row.getTargetId());
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product != null) {
                row.setTargetName(product.getProductName());
                row.setTargetBusNo(product.getProductBusNo());
                if (StringUtils.isNotEmpty(product.getTotalVolume())) {
                    row.setSpecificationText(product.getTotalVolume().trim());
                }
            }
            if (StringUtils.isNotEmpty(row.getUnitCode())) {
                row.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    unitLabels, row.getUnitCode(), product));
            }
        }
    }

    /**
     * 配置行 → 派生绑定读模型（尚未 enrich 展示字段）。
     *
     * @param line 合管配置行
     * @return 派生绑定读模型
     */
    private SpecimenTubeMergeDerivedBindQueryModel toDerivedModel(SpecimenTubeMergeLineQueryModel line) {
        SpecimenTubeMergeDerivedBindQueryModel model = new SpecimenTubeMergeDerivedBindQueryModel();
        model.setMergeConfigLineId(line.getId());
        model.setDeviceId(line.getDeviceId());
        model.setTargetTable(line.getTargetTable());
        model.setTargetId(line.getTargetId());
        model.setQuantity(line.getQuantity());
        model.setUnitCode(line.getUnitCode());
        model.setDisplayOrder(line.getDisplayOrder());
        return model;
    }
}
