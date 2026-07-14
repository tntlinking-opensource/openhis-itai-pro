/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.support;

import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.basic.ordertemplate.support.dto.OrderTemplateLineEnrichRow;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.OrderTemplateLineKind;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组套模板行目录/术语/执行科室展示 enrich（IF-BAS-OTL-03/08 读侧装配）。
 */
@Component
@RequiredArgsConstructor
public class OrderTemplateLineCatalogEnrichAssembler {

    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final FrequencyQueryService frequencyQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 批量 enrich 组套模板行展示字段。
     *
     * @param lines 待 enrich 行列表
     * @param performOrgContextId 执行科室上下文（维护端详情传 null）
     */
    public void enrichLines(List<OrderTemplateLineEnrichRow> lines, Long performOrgContextId) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // Pre：收集产品定义键、频次 ID、执行科室 ID 供批量读
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<Long> frequencyIds = new HashSet<>();
        Set<Long> performOrgIds = new HashSet<>();
        for (OrderTemplateLineEnrichRow line : lines) {
            if (line == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = buildLookup(line);
            if (lookup != null) {
                lookups.add(lookup);
            }
            if (line.getFrequencyId() != null) {
                frequencyIds.add(line.getFrequencyId());
            }
            if (line.getDefaultPerformOrgId() != null) {
                performOrgIds.add(line.getDefaultPerformOrgId());
            }
        }
        // Pre：经 ProductDefinitionDisplayTerminologyAssembler 批量读产品展示（B.6.4 跨聚合）
        Map<String, ProductDefinitionDisplayDto> productMap = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        Map<Long, String> frequencyNameMap = loadFrequencyNames(frequencyIds);
        // Pre：经 Organization QS 批量读科室名（B.6.4）
        Map<Long, String> orgNameMap = organizationQueryService.mapNamesByIds(performOrgIds);
        Map<String, String> routeNameMap = loadRouteNameMap();
        // Asm：逐行回填产品/频次/科室/途径展示
        for (OrderTemplateLineEnrichRow line : lines) {
            if (line == null) {
                continue;
            }
            applyProductDisplay(line, productMap);
            if (line.getFrequencyId() != null) {
                line.setFrequencyText(frequencyNameMap.get(line.getFrequencyId()));
            }
            if (line.getDefaultPerformOrgId() != null) {
                line.setDefaultPerformOrgName(orgNameMap.get(line.getDefaultPerformOrgId()));
            }
            if (StringUtils.isNotEmpty(line.getRouteCode())) {
                line.setRouteName(routeNameMap.get(line.getRouteCode().trim()));
            }
        }
    }

    /**
     * 按组套行种类映射到产品定义批量查询键（药品/诊疗/耗材）。
     *
     * @param line 待 enrich 模板行
     * @return 产品定义查询键；种类未知或主键缺失时 null
     */
    private ProductDefinitionBatchLookup buildLookup(OrderTemplateLineEnrichRow line) {
        OrderTemplateLineKind kind = EnumUtil.fromCode(OrderTemplateLineKind.class, line.getLineKindEnum());
        if (kind == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        // 按行种类映射到对应产品定义表
        if (kind == OrderTemplateLineKind.MEDICATION) {
            if (line.getMedicationDefinitionId() == null) {
                return null;
            }
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(line.getMedicationDefinitionId());
            return lookup;
        }
        if (kind == OrderTemplateLineKind.SERVICE) {
            if (line.getActivityDefinitionId() == null) {
                return null;
            }
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(line.getActivityDefinitionId());
            return lookup;
        }
        if (kind == OrderTemplateLineKind.DEVICE) {
            if (line.getDeviceDefinitionId() == null) {
                return null;
            }
            lookup.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
            lookup.setProductDefinitionId(line.getDeviceDefinitionId());
            return lookup;
        }
        return null;
    }

    /**
     * 将批量读到的产品展示回填到模板行（名称、规格、类目、默认单位）。
     *
     * @param line       待回填行（就地修改）
     * @param productMap 产品复合键 → 展示 DTO
     */
    private void applyProductDisplay(OrderTemplateLineEnrichRow line, Map<String, ProductDefinitionDisplayDto> productMap) {
        String tableName = line.getProductTableName();
        Long definitionId = null;
        OrderTemplateLineKind kind = EnumUtil.fromCode(OrderTemplateLineKind.class, line.getLineKindEnum());
        if (kind == OrderTemplateLineKind.MEDICATION) {
            definitionId = line.getMedicationDefinitionId();
        } else if (kind == OrderTemplateLineKind.SERVICE) {
            definitionId = line.getActivityDefinitionId();
        } else if (kind == OrderTemplateLineKind.DEVICE) {
            definitionId = line.getDeviceDefinitionId();
        }
        if (tableName == null || definitionId == null) {
            return;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(tableName, definitionId);
        ProductDefinitionDisplayDto display = productMap.get(key);
        if (display == null) {
            return;
        }
        line.setProductTableName(display.getProductTableName());
        line.setProductName(display.getProductName());
        line.setProductSpec(display.getTotalVolume());
        line.setCategoryCode(display.getCategoryCode());
        if (StringUtils.isEmpty(line.getDefaultUnitCode()) && StringUtils.isNotEmpty(display.getDefEncounterUnitCode())) {
            // 行未指定默认单位时回填产品定义默认就诊单位
            line.setDefaultUnitCode(display.getDefEncounterUnitCode());
        }
        if (StringUtils.isEmpty(line.getDefaultUnitName()) && StringUtils.isNotEmpty(display.getDefEncounterUnitCodeDictText())) {
            line.setDefaultUnitName(display.getDefEncounterUnitCodeDictText());
        }
    }

    /**
     * 批量解析频次 ID → 展示名；已删频次跳过不阻断整单 enrich。
     *
     * @param frequencyIds 模板行引用的频次主键集合
     * @return frequencyId → 名称
     */
    private Map<Long, String> loadFrequencyNames(Set<Long> frequencyIds) {
        Map<Long, String> result = new HashMap<>();
        if (frequencyIds == null || frequencyIds.isEmpty()) {
            return result;
        }
        for (Long frequencyId : frequencyIds) {
            if (frequencyId == null) {
                continue;
            }
            try {
                // 经 Frequency QS 逐条读名称（B.6.4）；已删频次跳过不阻断整单
                Frequency frequency = frequencyQueryService.findById(frequencyId);
                if (frequency != null && frequency.getName() != null) {
                    result.put(frequencyId, frequency.getName());
                }
            } catch (BusinessException ex) {
                // 频次已删除时不阻断整单 enrich
            }
        }
        return result;
    }

    /**
     * 加载给药途径术语（METHOD_CODE）码 → 展示名，供模板行 route 回填。
     *
     * @return routeCode → 术语展示名
     */
    private Map<String, String> loadRouteNameMap() {
        Map<String, String> routeNameMap = new HashMap<>();
        List<OptionDto> options = terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.METHOD_CODE);
        for (OptionDto option : options) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            routeNameMap.put(option.getValue(), option.getLabel());
        }
        return routeNameMap;
    }
}
