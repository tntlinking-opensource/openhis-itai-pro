/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.dto;

import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 住院护士站医嘱查看：批量预热后的展示只读上下文。
 */
@Data
@Accessors(chain = true)
public class NurseStationOrderViewDisplayContext {

    /** 药品定义 id → 实体。 */
    private Map<Long, MedicationDefinition> medicationDefinitionById;

    /** 诊疗活动定义 id → 实体。 */
    private Map<Long, ActivityDefinition> activityDefinitionById;

    /** 产品读模型复合键 → 产品行（展示名回落）。 */
    private Map<String, ProductDefinitionDisplayDto> productByKey;

    /** 计量单位 code → 展示名。 */
    private Map<String, String> unitLabelByCode;

    /** 用法 code → 展示名。 */
    private Map<String, String> methodLabelByCode;

    /** 频次 code → 展示名。 */
    private Map<String, String> rateLabelByCode;

    /** 剂量单位 code → 展示名。 */
    private Map<String, String> doseUnitLabelByCode;

    /** 医务人员 id → 姓名。 */
    private Map<Long, String> practitionerNameById;

    /** 库位 id → 名称。 */
    private Map<Long, String> locationNameById;

    /** 校对人主键 + 就诊主键（SOH 分隔）→ 校对科室展示名。 */
    private Map<String, String> performerCheckOrgDisplayByKey;

    /**
     * 药品申请主键 → 展示名（供皮试检查 companion 追加「关联：药名」）。
     */
    private Map<Long, String> medicationAdviceNameByRequestId;
}
