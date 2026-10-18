/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeDerivedBindQueryModel;
import com.zxplat.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeGroupQueryModel;
import com.zxplat.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeLineQueryModel;
import com.zxplat.domain.custom.specimentubemerge.service.SpecimenTubeMergeQueryService;
import com.zxplat.domain.share.constants.SpecimenTubeMergeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
 * <p>合管配置优先于 {@code cus_service_device_template}；同一 {@code deviceId + groupNo} 下耗材只解析一份。
 * 采血费由开嘱 IF-DOR-23 同批跨设备/跨组全局去重为 1 份且不挂 based_on。
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
        return this.findMergeGroupRefByActivityId(activityId).map(SpecimenTubeMergeGroupQueryModel::getDeviceId);
    }

    /**
     * 按检验 activity 反查合管分组（设备 + 组序号）。
     *
     * @param activityId 检验 activity 主键
     * @return 合管分组；未配置或目标为采血费时 empty
     */
    public Optional<SpecimenTubeMergeGroupQueryModel> findMergeGroupRefByActivityId(Long activityId) {
        if (activityId == null || this.isBloodCollectionFeeActivityId(activityId)) {
            return Optional.empty();
        }
        return specimenTubeMergeQueryService.findGroupRefByActivityId(activityId);
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
        Map<Long, SpecimenTubeMergeGroupQueryModel> mapped = this.mapMergeGroupRefByActivityIds(activityIds);
        Map<Long, Long> result = new HashMap<>();
        for (Map.Entry<Long, SpecimenTubeMergeGroupQueryModel> entry : mapped.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getDeviceId() != null) {
                result.put(entry.getKey(), entry.getValue().getDeviceId());
            }
        }
        return result;
    }

    /**
     * 批量反查 activity → 合管分组（设备 + 组序号）。
     *
     * <p>结果中剔除采血费 activity。</p>
     *
     * @param activityIds 检验 activity 主键集合
     * @return activityId → 合管分组
     */
    public Map<Long, SpecimenTubeMergeGroupQueryModel> mapMergeGroupRefByActivityIds(Collection<Long> activityIds) {
        Map<Long, SpecimenTubeMergeGroupQueryModel> mapped =
            specimenTubeMergeQueryService.mapGroupRefByActivityIds(activityIds);
        Long bloodFeeActivityId = this.resolveBloodCollectionFeeActivityId();
        if (bloodFeeActivityId == null || mapped.isEmpty()) {
            return mapped;
        }
        // 采血费可跨组复用：不得作为合管分组/派生触发键
        Map<Long, SpecimenTubeMergeGroupQueryModel> result = new HashMap<>(mapped);
        result.remove(bloodFeeActivityId);
        return result;
    }

    /**
     * 判断检验 activity 是否已配置合管（归属某合管组）。
     *
     * @param activityId 检验 activity 主键
     * @return 已配置为 true（采血费恒为 false）
     */
    public boolean isActivityConfiguredForMerge(Long activityId) {
        return this.findMergeGroupRefByActivityId(activityId).isPresent();
    }

    /**
     * 是否为合管口径下的「采血费」诊疗项目。
     *
     * <p>采血费类目虽锁定为检验（22），仅计费、不打采血贴；护士站 TECH 检验例外与前端条码打印须排除。</p>
     *
     * @param activityId 诊疗活动主键
     * @return 名称精确匹配「采血费」且唯一解析成功时为 true
     */
    public boolean isBloodCollectionFeeActivityId(Long activityId) {
        if (activityId == null) {
            return false;
        }
        Long bloodFeeActivityId = this.resolveBloodCollectionFeeActivityId();
        return bloodFeeActivityId != null && bloodFeeActivityId.equals(activityId);
    }

    /**
     * 列出合管组下开嘱应带出的派生绑定（采血费 + 耗材；不含组内检验项目 activity）。
     *
     * @param deviceId 合管设备主键
     * @param groupNo  合管组序号
     * @return 派生绑定行（含展示 enrich）；未配置时返回空列表
     */
    public List<SpecimenTubeMergeDerivedBindQueryModel> listDerivedBindsByDeviceIdAndGroupNo(
        Long deviceId,
        Integer groupNo
    ) {
        if (deviceId == null || groupNo == null) {
            return List.of();
        }
        Long bloodFeeActivityId = this.resolveBloodCollectionFeeActivityId();
        List<SpecimenTubeMergeLineQueryModel> lines =
            specimenTubeMergeQueryService.listByDeviceIdAndGroupNo(deviceId, groupNo);
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
     * 兼容旧调用：按设备列出派生绑定（设备下多组时会合并各组，可能重复带出采血费）。
     *
     * <p>新路径请改用 {@link #listDerivedBindsByDeviceIdAndGroupNo}。</p>
     *
     * @param deviceId 合管设备主键
     * @return 派生绑定行
     * @deprecated 请按组调用 {@link #listDerivedBindsByDeviceIdAndGroupNo}
     */
    @Deprecated
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
        model.setGroupNo(line.getGroupNo());
        model.setTargetTable(line.getTargetTable());
        model.setTargetId(line.getTargetId());
        model.setQuantity(line.getQuantity());
        model.setUnitCode(line.getUnitCode());
        model.setDisplayOrder(line.getDisplayOrder());
        return model;
    }
}
