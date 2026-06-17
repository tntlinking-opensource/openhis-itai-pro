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

package com.openhis.health.application.basic.servicedevicebinding.assembler;

import com.openhis.health.application.basic.servicedevicebinding.dto.ServiceDeviceBindingDto;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.converter.TextValueConverter;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.ServiceDeviceTemplateTypeCode;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionEnabledQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 诊疗耗材绑定列表展示 enrich 与初始化下拉装配（WH-BND001）。
 *
 * <p>批量 I/O：用法来源名经术语体系一次加载；诊疗/目标项经 QueryService 或 {@link ProductDefinitionBatchLoader} 批量解析。</p>
 */
@Component
@RequiredArgsConstructor
public class ServiceDeviceBindingDisplayAssembler {

    private final TerminologyOptionLoader terminologyOptionLoader;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 批量 enrich 绑定列表行：来源项名、目标项名、单位/状态/类型/目标表展示文案。
     *
     * @param rows 待 enrich 的 DTO 列表
     */
    public void enrichRows(List<ServiceDeviceBindingDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        Set<String> productDedupe = new LinkedHashSet<>();
        Set<Long> activitySourceIds = new HashSet<>();

        // 第一遍：收集各来源类型与目标项的批量查询键
        for (ServiceDeviceBindingDto row : rows) {
            if (row == null || StringUtils.isBlank(row.getItemNo())) {
                continue;
            }
            String typeCode = row.getTypeCode();
            if (ServiceDeviceTemplateTypeCode.ACTIVITY.getCode().equals(typeCode)) {
                Long activityId = TextValueConverter.toNullableLong(row.getItemNo());
                if (activityId != null) {
                    activitySourceIds.add(activityId);
                }
            }
            if (row.getDevActId() == null || StringUtils.isBlank(row.getDevActTable())) {
                continue;
            }
            String table = row.getDevActTable().trim();
            if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)
                && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
                continue;
            }
            String dedupeKey =
                table + GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR + row.getDevActId();
            if (!productDedupe.add(dedupeKey)) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(table);
            lookup.setProductDefinitionId(row.getDevActId());
            productLookups.add(lookup);
        }

        Map<String, ProductDefinitionDisplayDto> productByKey = productDefinitionBatchLoader.loadAsMap(productLookups);
        Map<String, String> methodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<Long, String> activitySourceNameById = this.buildActivityDefinitionNameMap(activitySourceIds);
        Map<String, String> unitLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);

        // 第二遍：回填各展示列
        for (ServiceDeviceBindingDto row : rows) {
            if (row == null) {
                continue;
            }
            ServiceDeviceTemplateTypeCode type = EnumUtil.fromCode(ServiceDeviceTemplateTypeCode.class, row.getTypeCode());
            if (type != null) {
                row.setTypeName(type.getDisplay());
            } else {
                row.setTypeName(row.getTypeCode());
            }
            PublicationStatus status = EnumUtil.fromCode(PublicationStatus.class, row.getStatusEnum());
            if (status != null) {
                row.setStatusName(status.getDisplay());
            } else {
                row.setStatusName(row.getStatusEnum());
            }
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getDevActTable())) {
                row.setDevActTableName(CustomConstants.ServiceDeviceBindingDisplayLabel.TARGET_TABLE_ACTIVITY);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getDevActTable())) {
                row.setDevActTableName(CustomConstants.ServiceDeviceBindingDisplayLabel.TARGET_TABLE_DEVICE);
            } else {
                row.setDevActTableName(row.getDevActTable());
            }
            if (StringUtils.isNotBlank(row.getUnitCode())) {
                row.setUnitName(unitLabels.get(row.getUnitCode().trim()));
            }
            row.setItemName(this.resolveSourceItemName(
                row, methodLabelByCode, activitySourceNameById, productByKey));
            if (row.getDevActId() != null && StringUtils.isNotBlank(row.getDevActTable())) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(row.getDevActTable().trim(), row.getDevActId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product != null) {
                    row.setDevActName(product.getProductName());
                }
            }
        }
    }

    /**
     * 加载可选诊疗活动定义列表为下拉项。
     *
     * @return 诊疗来源候选
     */
    public List<OptionDto> buildActivitySourceOptions() {
        List<ActivityDefinitionEnabledQueryModel> activityList =
            activityDefinitionQueryService.diseaseTreatmentList(null, null);
        List<OptionDto> options = new ArrayList<>();
        for (ActivityDefinitionEnabledQueryModel item : activityList) {
            if (item.getId() == null) {
                continue;
            }
            options.add(new OptionDto(0L, String.valueOf(item.getId()), item.getName()));
        }
        return options;
    }

    /**
     * 加载设备/耗材定义初始化列表为下拉项。
     *
     * @return 耗材目标候选
     */
    public List<OptionDto> buildDeviceDefOptions() {
        List<DeviceDefinition> deviceList = deviceDefinitionQueryService.getDeviceManageInit();
        List<OptionDto> options = new ArrayList<>();
        for (DeviceDefinition item : deviceList) {
            if (item.getId() == null) {
                continue;
            }
            String label = item.getName();
            if (StringUtils.isNotBlank(item.getBusNo())) {
                label = item.getBusNo() + " " + item.getName();
            }
            options.add(new OptionDto(0L, String.valueOf(item.getId()), label));
        }
        return options;
    }

    /**
     * 构建绑定目标物理表固定下拉。
     *
     * @return 目标表选项
     */
    public List<OptionDto> buildTargetTableOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(
            1L,
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            CustomConstants.ServiceDeviceBindingDisplayLabel.TARGET_TABLE_ACTIVITY));
        options.add(new OptionDto(
            2L,
            TableNameConstants.ADM_DEVICE_DEFINITION,
            CustomConstants.ServiceDeviceBindingDisplayLabel.TARGET_TABLE_DEVICE));
        return options;
    }

    /**
     * 批量加载诊疗活动定义名称。
     *
     * @param activityIds 诊疗活动主键集合
     * @return id → 名称
     */
    private Map<Long, String> buildActivityDefinitionNameMap(Set<Long> activityIds) {
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
     * 解析单行来源项展示名（用法 method_code / 诊疗活动定义）。
     */
    private String resolveSourceItemName(
        ServiceDeviceBindingDto row,
        Map<String, String> methodLabelByCode,
        Map<Long, String> activitySourceNameById,
        Map<String, ProductDefinitionDisplayDto> productByKey
    ) {
        if (StringUtils.isBlank(row.getItemNo())) {
            return null;
        }
        String itemKey = row.getItemNo().trim();
        String typeCode = row.getTypeCode();
        if (ServiceDeviceTemplateTypeCode.USAGE.getCode().equals(typeCode)) {
            String label = methodLabelByCode.get(itemKey);
            if (StringUtils.isNotBlank(label)) {
                return label;
            }
            return itemKey;
        }
        if (ServiceDeviceTemplateTypeCode.ACTIVITY.getCode().equals(typeCode)) {
            Long activityId = TextValueConverter.toNullableLong(itemKey);
            if (activityId == null) {
                return null;
            }
            String fromActivityMap = activitySourceNameById.get(activityId);
            if (fromActivityMap != null) {
                return fromActivityMap;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
            ProductDefinitionDisplayDto product = productByKey.get(key);
            if (product != null) {
                return product.getProductName();
            }
        }
        return null;
    }
}
