/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊医嘱/处置列表：诊疗·药品·耗材定义与类目术语的批量预热（护士站 IF-NUR-02、医生站 IF-DOR-10 等同构读路径）。
 *
 * <p>跨场景复用组件，包位 {@code module.common.assembler.order}（B.6.1）。一次就诊内
 * {@code findAllById}/{@code findByIdIn} 批量加载定义主数据，并经由 {@link TerminologyOptionLoader} 预热三类类目码表；
 * 禁止在列表循环内逐条仓储 I/O（A.9.1）。列表行展示名解析配合 {@link OrderCatalogDisplayAssembler}。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderRequestCatalogWarmAssembler {

    /** 药品定义批量只读查询。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    /** 诊疗活动定义批量只读查询。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 耗材定义批量只读查询。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    /** 类目术语码表预热（药品/诊疗/耗材）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 按处置单三列表请求行收集定义主键并批量预热（与护士站原 {@code prepare} 入参一致，行为不变）。
     *
     * @param serviceRows    诊疗申请行（可为空列表）
     * @param medicationRows 药品申请行（可为空列表）
     * @param deviceRows     耗材申请行（可为空列表）
     * @return 定义实体与类目展示映射；定义缺失时由调用方回落占位名
     */
    public OrderRequestCatalogWarmContext prepareFromTreatmentSheetRows(
        List<ServiceRequestListModel> serviceRows,
        List<MedicationRequestListQueryModel> medicationRows,
        List<DeviceRequestListModel> deviceRows) {
        Set<Long> activityDefIds = new HashSet<>();
        Set<Long> medicationDefIds = new HashSet<>();
        Set<Long> deviceDefIds = new HashSet<>();
        if (serviceRows != null) {
            for (ServiceRequestListModel serviceRow : serviceRows) {
                if (serviceRow.getActivityId() != null) {
                    activityDefIds.add(serviceRow.getActivityId());
                }
            }
        }
        if (medicationRows != null) {
            for (MedicationRequestListQueryModel medicationRow : medicationRows) {
                if (medicationRow.getMedicationId() != null) {
                    medicationDefIds.add(medicationRow.getMedicationId());
                }
            }
        }
        if (deviceRows != null) {
            for (DeviceRequestListModel deviceRow : deviceRows) {
                if (deviceRow.getDeviceDefId() != null) {
                    deviceDefIds.add(deviceRow.getDeviceDefId());
                }
            }
        }
        return prepareByDefinitionIds(activityDefIds, medicationDefIds, deviceDefIds);
    }

    /**
     * 按已收集的定义主键集合批量预热（供医生站等场景在应用层自行归集 id 后调用）。
     *
     * @param activityDefIds   诊疗活动定义主键集合
     * @param medicationDefIds 药品定义主键集合
     * @param deviceDefIds     耗材定义主键集合
     * @return 定义实体与类目展示映射
     */
    public OrderRequestCatalogWarmContext prepareByDefinitionIds(
        Set<Long> activityDefIds,
        Set<Long> medicationDefIds,
        Set<Long> deviceDefIds) {
        OrderRequestCatalogWarmContext warmContext = new OrderRequestCatalogWarmContext();
        warmContext.setActivityDefinitionById(loadActivityDefinitions(activityDefIds));
        warmContext.setMedicationDefinitionById(loadMedicationDefinitions(medicationDefIds));
        warmContext.setDeviceDefinitionById(loadDeviceDefinitions(deviceDefIds));
        warmContext.setMedicationCategoryLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE));
        warmContext.setActivityCategoryLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE));
        warmContext.setDeviceCategoryLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE));
        return warmContext;
    }

    /**
     * 批量加载诊疗活动定义（一次 I/O）。
     *
     * @param ids 定义主键集合
     * @return 定义主键 → 实体
     */
    private Map<Long, ActivityDefinition> loadActivityDefinitions(Set<Long> ids) {
        Map<Long, ActivityDefinition> definitionById = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return definitionById;
        }
        List<Long> idList = new ArrayList<>(ids);
        List<ActivityDefinition> loadedDefinitions = activityDefinitionQueryService.findAllByIds(idList);
        for (ActivityDefinition definition : loadedDefinitions) {
            definitionById.put(definition.getId(), definition);
        }
        return definitionById;
    }

    /**
     * 批量加载药品定义（一次 I/O）。
     *
     * @param ids 定义主键集合
     * @return 定义主键 → 实体
     */
    private Map<Long, MedicationDefinition> loadMedicationDefinitions(Set<Long> ids) {
        Map<Long, MedicationDefinition> definitionById = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return definitionById;
        }
        List<MedicationDefinition> loadedDefinitions = medicationDefinitionQueryService.findAllByIds(ids);
        for (MedicationDefinition definition : loadedDefinitions) {
            definitionById.put(definition.getId(), definition);
        }
        return definitionById;
    }

    /**
     * 批量加载耗材定义（一次 I/O）。
     *
     * @param ids 定义主键集合
     * @return 定义主键 → 实体
     */
    private Map<Long, DeviceDefinition> loadDeviceDefinitions(Set<Long> ids) {
        Map<Long, DeviceDefinition> definitionById = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return definitionById;
        }
        List<DeviceDefinition> loadedDefinitions = deviceDefinitionQueryService.getAllDeviceDefinitions(ids);
        for (DeviceDefinition definition : loadedDefinitions) {
            definitionById.put(definition.getId(), definition);
        }
        return definitionById;
    }
}
