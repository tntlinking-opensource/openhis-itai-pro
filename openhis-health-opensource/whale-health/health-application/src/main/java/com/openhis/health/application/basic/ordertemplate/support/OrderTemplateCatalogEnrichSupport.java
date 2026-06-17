/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.ordertemplate.support;

import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateLineRowDto;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
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
import org.apache.commons.lang3.StringUtils;
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
public class OrderTemplateCatalogEnrichSupport {

    /** 目录批量加载。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /** 频次读侧。 */
    private final FrequencyQueryService frequencyQueryService;

    /** 机构名称批量解析。 */
    private final OrganizationQueryService organizationQueryService;

    /** 术语下拉：用法展示名。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 批量 enrich 组套模板行展示字段。
     *
     * @param lines 待 enrich 行列表
     * @param performOrgContextId 执行科室上下文（维护端详情传 null；医生站预览 IF-08 接入后再用于收窄执行科室候选）
     */
    public void enrichLines(List<OrderTemplateLineRowDto> lines, Long performOrgContextId) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<Long> frequencyIds = new HashSet<>();
        Set<Long> performOrgIds = new HashSet<>();
        for (OrderTemplateLineRowDto line : lines) {
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
        Map<String, ProductDefinitionDisplayDto> productMap = productDefinitionBatchLoader.loadAsMap(lookups);
        Map<Long, String> frequencyNameMap = loadFrequencyNames(frequencyIds);
        Map<Long, String> orgNameMap = organizationQueryService.mapNamesByIds(performOrgIds);
        Map<String, String> routeNameMap = loadRouteNameMap();
        for (OrderTemplateLineRowDto line : lines) {
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
            if (StringUtils.isNotBlank(line.getRouteCode())) {
                line.setRouteName(routeNameMap.get(line.getRouteCode().trim()));
            }
        }
    }

    /**
     * 按行类型构造目录批量 lookup。
     *
     * @param line 模板行
     * @return lookup，无法解析时 null
     */
    private ProductDefinitionBatchLookup buildLookup(OrderTemplateLineRowDto line) {
        OrderTemplateLineKind kind = EnumUtil.fromCode(OrderTemplateLineKind.class, line.getLineKindEnum());
        if (kind == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
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
     * 回填目录展示字段。
     *
     * @param line 模板行
     * @param productMap 目录展示 Map
     */
    private void applyProductDisplay(OrderTemplateLineRowDto line, Map<String, ProductDefinitionDisplayDto> productMap) {
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
        if (StringUtils.isBlank(line.getDefaultUnitCode()) && StringUtils.isNotBlank(display.getDefEncounterUnitCode())) {
            line.setDefaultUnitCode(display.getDefEncounterUnitCode());
        }
        if (StringUtils.isBlank(line.getDefaultUnitName()) && StringUtils.isNotBlank(display.getDefEncounterUnitCodeDictText())) {
            line.setDefaultUnitName(display.getDefEncounterUnitCodeDictText());
        }
    }

    /**
     * 批量加载频次名称。
     *
     * @param frequencyIds 频次 id 集合
     * @return id → 名称
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
     * 加载用法 code → 展示名映射。
     *
     * @return 用法映射
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
