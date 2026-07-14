/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 门诊医嘱/处置列表：目录定义实体与类目术语展示映射的批量预热结果（护士站 IF-NUR-02、医生站 IF-DOR-10 等同构场景）。
 *
 * <p>由 {@link com.openhis.health.application.shared.advice.OrderRequestCatalogWarmAssembler} 填充；
 * 列表行装配时配合 {@link com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler} 解析展示列。</p>
 */
@Getter
@Setter
public class OrderRequestCatalogWarmContext {

    /** 诊疗活动定义主键 → 定义实体（列表行 itemName、serviceCategoryCode 来源）。 */
    private Map<Long, ActivityDefinition> activityDefinitionById = new HashMap<>();

    /** 药品定义主键 → 定义实体（列表行 itemName、medCategoryCode、规格文本来源）。 */
    private Map<Long, MedicationDefinition> medicationDefinitionById = new HashMap<>();

    /** 耗材定义主键 → 定义实体（列表行 itemName、deviceCategoryCode 来源）。 */
    private Map<Long, DeviceDefinition> deviceDefinitionById = new HashMap<>();

    /** 药品类目持久化 code → 术语展示名（{@code MEDICATION_CATEGORY_CODE}）。 */
    private Map<String, String> medicationCategoryLabelByCode = new HashMap<>();

    /** 诊疗类目持久化 code → 术语展示名（{@code ACTIVITY_CATEGORY_CODE}）。 */
    private Map<String, String> activityCategoryLabelByCode = new HashMap<>();

    /** 耗材类目持久化 code → 术语展示名（{@code DEVICE_CATEGORY_CODE}）。 */
    private Map<String, String> deviceCategoryLabelByCode = new HashMap<>();
}
