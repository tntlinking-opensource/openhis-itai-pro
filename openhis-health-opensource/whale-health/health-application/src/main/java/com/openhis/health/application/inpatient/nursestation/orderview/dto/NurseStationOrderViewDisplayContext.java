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

package com.openhis.health.application.inpatient.nursestation.orderview.dto;

import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
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
}
