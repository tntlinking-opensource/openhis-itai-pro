/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.specimentubemerge.assembler;

import com.zxplat.application.basic.specimentubemerge.dto.SpecimenTubeMergeBindingDto;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeLineQueryModel;
import com.zxplat.domain.share.constants.SpecimenTubeMergeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标本合管绑定展示 enrich。
 */
@Component
@RequiredArgsConstructor
public class SpecimenTubeMergeDisplayAssembler {

    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * enrich 绑定行目标名与单位文案。
     *
     * @param rows 绑定 DTO
     */
    public void enrichRows(List<SpecimenTubeMergeBindingDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        Set<String> productDedupe = new LinkedHashSet<>();
        Set<Long> activityIds = new HashSet<>();
        for (SpecimenTubeMergeBindingDto row : rows) {
            if (row == null || row.getTargetId() == null || StringUtils.isEmpty(row.getTargetTable())) {
                continue;
            }
            String table = row.getTargetTable().trim();
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)) {
                activityIds.add(row.getTargetId());
            }
            String dedupeKey = table + ":" + row.getTargetId();
            if (!productDedupe.add(dedupeKey)) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(table);
            lookup.setProductDefinitionId(row.getTargetId());
            productLookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(productLookups);
        Map<Long, String> activityNameById = this.buildActivityNameMap(activityIds);
        Map<String, String> unitLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        for (SpecimenTubeMergeBindingDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getTargetId() != null && StringUtils.isNotEmpty(row.getTargetTable())) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(row.getTargetTable().trim(), row.getTargetId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product != null) {
                    row.setTargetName(product.getProductName());
                    row.setTargetBusNo(product.getProductBusNo());
                }
            }
            if (StringUtils.isEmpty(row.getTargetName()) && row.getTargetId() != null
                && TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getTargetTable())) {
                row.setTargetName(activityNameById.get(row.getTargetId()));
            }
            if (StringUtils.isNotEmpty(row.getUnitCode())) {
                row.setUnitCodeText(unitLabels.get(row.getUnitCode().trim()));
            }
        }
    }

    /**
     * 批量加载 activity 名称映射，供 enrich 回退展示。
     *
     * @param activityIds activity 主键集合
     * @return id → 名称
     */
    private Map<Long, String> buildActivityNameMap(Set<Long> activityIds) {
        Map<Long, String> result = new HashMap<>();
        if (activityIds == null || activityIds.isEmpty()) {
            return result;
        }
        List<ActivityDefinition> definitions =
            activityDefinitionQueryService.findAllByIds(new ArrayList<>(activityIds));
        for (ActivityDefinition definition : definitions) {
            if (definition != null && definition.getId() != null) {
                result.put(definition.getId(), definition.getName());
            }
        }
        return result;
    }

    /**
     * 解析采血费诊疗项目主键（名称精确匹配 {@link SpecimenTubeMergeConstants#BLOOD_COLLECTION_FEE_ACTIVITY_NAME}）。
     *
     * @return 采血费 activity 主键；未配置或名称不唯一时返回 null
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
     * 将读模型投影为绑定 DTO。
     *
     * @param model 绑定读模型
     * @return 绑定 DTO
     */
    public SpecimenTubeMergeBindingDto toBindingDto(SpecimenTubeMergeLineQueryModel model) {
        if (model == null) {
            return null;
        }
        SpecimenTubeMergeBindingDto dto = new SpecimenTubeMergeBindingDto();
        dto.setId(model.getId());
        dto.setTargetTable(model.getTargetTable());
        dto.setTargetId(model.getTargetId());
        dto.setQuantity(model.getQuantity());
        dto.setUnitCode(model.getUnitCode());
        dto.setDisplayOrder(model.getDisplayOrder());
        return dto;
    }

    /**
     * 将绑定读模型列表拆分为检验项目与耗材两组 DTO。
     *
     * @param models 绑定读模型
     * @param bloodFeeActivityId 采血费 activity 主键
     * @return 长度为 2 的列表：activityBindings、consumableBindings
     */
    public List<List<SpecimenTubeMergeBindingDto>> splitBindingDtos(
        List<SpecimenTubeMergeLineQueryModel> models,
        Long bloodFeeActivityId
    ) {
        List<SpecimenTubeMergeBindingDto> activityBindings = new ArrayList<>();
        List<SpecimenTubeMergeBindingDto> consumableBindings = new ArrayList<>();
        for (SpecimenTubeMergeLineQueryModel model : models) {
            SpecimenTubeMergeBindingDto dto = this.toBindingDto(model);
            if (dto == null) {
                continue;
            }
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(model.getTargetTable())) {
                if (bloodFeeActivityId != null && bloodFeeActivityId.equals(model.getTargetId())) {
                    continue;
                }
                activityBindings.add(dto);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(model.getTargetTable())) {
                consumableBindings.add(dto);
            }
        }
        this.enrichRows(activityBindings);
        this.enrichRows(consumableBindings);
        List<List<SpecimenTubeMergeBindingDto>> result = new ArrayList<>();
        result.add(activityBindings);
        result.add(consumableBindings);
        return result;
    }
}
