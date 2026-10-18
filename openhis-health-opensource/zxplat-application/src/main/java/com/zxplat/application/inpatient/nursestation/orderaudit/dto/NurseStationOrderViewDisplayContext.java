/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderaudit.dto;

import com.zxplat.application.shared.advice.SkinTestOutcomeSnapshot;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
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

    /**
     * 国临手术定义 id → 术式名称（护士列表术式行展示；认 SR.procedure_definition_id）。
     */
    private Map<Long, String> procedureDefinitionNameById;

    /** 产品读模型复合键 → 产品行（展示名回落）。 */
    private Map<String, ProductDefinitionDisplayDto> productByKey;

    /** 计量单位 code → 展示名。 */
    private Map<String, String> unitLabelByCode;

    /** 用法 code → 展示名。 */
    private Map<String, String> methodLabelByCode;

    /** 频次 code → 展示名。 */
    private Map<String, String> rateLabelByCode;

    /**
     * 频次 code → 每日执行次数（仅日周期；供长期药「每日三片」）。
     */
    private Map<String, Integer> timesPerDayByRateCode;

    /**
     * 输液滴速 code → 展示名（术语 {@code speed_code}）。
     */
    private Map<String, String> speedLabelByCode;

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

    /**
     * 皮试药 requestId → 最新护士登记结果（companion SR → AllergyIntolerance，BR-IP-ORD-016）。
     */
    private Map<Long, SkinTestOutcomeSnapshot> skinTestOutcomeByMedicationRequestId;

    /**
     * 出院 ServiceRequest 主键 → 出院方式 code（EncounterHistory 离院里程碑；死亡出院展示名用）。
     */
    private Map<Long, String> dischargeOutWayCodeByServiceRequestId;
}
