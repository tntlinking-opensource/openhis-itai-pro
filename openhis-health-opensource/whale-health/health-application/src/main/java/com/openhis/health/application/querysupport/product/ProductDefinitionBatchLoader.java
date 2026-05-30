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

package com.openhis.health.application.querysupport.product;

import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按表名路由到各 DM {@code *QueryService} 批量加载产品定义（禁止 SQL/Repository）。
 */
@Component
@RequiredArgsConstructor
public class ProductDefinitionBatchLoader {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final ProductDefinitionSelectionTerminologySupport productDefinitionSelectionTerminologySupport;

    /**
     * 批量加载，返回复合键 → 展示 DTO（与 RM {@code batchGetProductByIds} 键规则一致）。
     *
     * @param lookups 表名 + 定义主键列表；空或无效项跳过
     * @return 复合键 → {@link ProductDefinitionDisplayDto}；入参空时返回空 Map
     */
    public Map<String, ProductDefinitionDisplayDto> loadAsMap(List<ProductDefinitionBatchLookup> lookups) {
        Map<String, ProductDefinitionDisplayDto> result = new HashMap<>();
        if (lookups == null || lookups.isEmpty()) {
            return result;
        }
        Map<String, Set<Long>> idsByTable = new LinkedHashMap<>();
        for (ProductDefinitionBatchLookup lookup : lookups) {
            if (lookup == null || lookup.getProductDefinitionId() == null || StringUtils.isBlank(lookup.getProductTableName())) {
                continue;
            }
            String table = lookup.getProductTableName().trim();
            idsByTable.computeIfAbsent(table, key -> new LinkedHashSet<>()).add(lookup.getProductDefinitionId());
        }
        for (Map.Entry<String, Set<Long>> entry : idsByTable.entrySet()) {
            String tableName = entry.getKey();
            List<Long> idList = new ArrayList<>(entry.getValue());
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(tableName)) {
                for (MedicationDefinition med : medicationDefinitionQueryService.findAllByIds(idList)) {
                    putMedication(result, med);
                }
            } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(tableName)) {
                for (ActivityDefinition act : activityDefinitionQueryService.findAllByIds(idList)) {
                    putActivity(result, act);
                }
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(tableName)) {
                for (var dev : deviceDefinitionQueryService.getAllDeviceDefinitions(idList)) {
                    putDevice(result, dev);
                }
            }
        }
        if (!result.isEmpty()) {
            // —— 主表映射不含字典展示列：批量加载后统一术语回填（B.10.8.1）。——
            this.productDefinitionSelectionTerminologySupport.enrichDisplayDtos(result.values());
        }
        return result;
    }

    /** 药品行写入结果 Map（键经 {@link com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil}）。 */
    private static void putMedication(Map<String, ProductDefinitionDisplayDto> result, MedicationDefinition med) {
        ProductDefinitionDisplayDto dto = ProductDefinitionDisplaySupport.fromMedication(med);
        if (dto == null) {
            return;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getProductTableName(), dto.getProductDefinitionId());
        if (key != null) {
            result.put(key, dto);
        }
    }

    /** 诊疗活动行写入结果 Map。 */
    private static void putActivity(Map<String, ProductDefinitionDisplayDto> result, ActivityDefinition act) {
        ProductDefinitionDisplayDto dto = ProductDefinitionDisplaySupport.fromActivity(act);
        if (dto == null) {
            return;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getProductTableName(), dto.getProductDefinitionId());
        if (key != null) {
            result.put(key, dto);
        }
    }

    /** 耗材行写入结果 Map。 */
    private static void putDevice(Map<String, ProductDefinitionDisplayDto> result,
                                com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition dev) {
        ProductDefinitionDisplayDto dto = ProductDefinitionDisplaySupport.fromDevice(dev);
        if (dto == null) {
            return;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getProductTableName(), dto.getProductDefinitionId());
        if (key != null) {
            result.put(key, dto);
        }
    }
}
