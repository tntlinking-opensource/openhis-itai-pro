/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.support;

import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.InpatientAutoRollNursingLevelQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.inpatient.nursestation.ATDManager.support.dto.InpatientPatientQueueNursingLevelDisplayRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院患者队列护理级别只读 enrich（AS-IP-NUR-001 / AS-IP-DOR-001 · BR-IP-NUR-015 / BR-IP-DOR-009）。
 *
 * <p>分步：ServiceRequest QS 批量解析 → ActivityDefinition QS 批量名称 → 内存合并（B.6.4）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientPatientQueueNursingLevelEnrichAssembler {

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * 批量解析当前页就诊的护理级别展示行。
     *
     * @param encounterIds  就诊主键集合
     * @param referenceDate 展示参考日（通常当日；与滚费口径对齐）
     * @return encounterId → 护理级别展示；无护理时为 empty Map
     */
    public Map<Long, InpatientPatientQueueNursingLevelDisplayRow> enrichByEncounterIds(
        Collection<Long> encounterIds,
        LocalDate referenceDate) {
        Map<Long, InpatientPatientQueueNursingLevelDisplayRow> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LocalDate billDate = referenceDate;
        if (billDate == null) {
            billDate = LocalDate.now();
        }
        // Pre：同聚合 QS 批量解析护理级别（category 26）
        Map<Long, InpatientAutoRollNursingLevelQueryModel> nursingByEncounter =
            serviceRequestQueryService.mapNursingLevelForPatientQueueByEncounterIds(encounterIds, billDate);
        if (nursingByEncounter.isEmpty()) {
            return result;
        }
        Set<Long> activityIds = new LinkedHashSet<>();
        for (InpatientAutoRollNursingLevelQueryModel model : nursingByEncounter.values()) {
            if (model != null && model.getActivityId() != null && model.getActivityId() > 0L) {
                activityIds.add(model.getActivityId());
            }
        }
        Map<Long, ActivityDefinition> activityById = new HashMap<>();
        if (!activityIds.isEmpty()) {
            List<ActivityDefinition> activities = activityDefinitionQueryService.findAllByIds(new ArrayList<>(activityIds));
            for (ActivityDefinition activity : activities) {
                if (activity != null && activity.getId() != null) {
                    activityById.put(activity.getId(), activity);
                }
            }
        }
        // Asm：合并 activity 展示名
        for (Map.Entry<Long, InpatientAutoRollNursingLevelQueryModel> entry : nursingByEncounter.entrySet()) {
            InpatientAutoRollNursingLevelQueryModel model = entry.getValue();
            if (model == null) {
                continue;
            }
            InpatientPatientQueueNursingLevelDisplayRow row = new InpatientPatientQueueNursingLevelDisplayRow();
            row.setServiceRequestId(model.getServiceRequestId());
            row.setActivityId(model.getActivityId());
            row.setStatusEnum(model.getStatusEnum());
            if (model.getActivityId() != null) {
                ActivityDefinition activity = activityById.get(model.getActivityId());
                if (activity != null) {
                    row.setActivityName(activity.getName());
                }
            }
            result.put(entry.getKey(), row);
        }
        return result;
    }
}
