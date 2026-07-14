/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.workflow;

import com.openhis.chs.domain.inpatient.queries.model.IpPlatformAdmissionQueryModel;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 住院平台入院登记快照查询编排。
 *
 * <p>读 yb_encounter_platform_map 组装查询模型；本类不调 gateway。</p>
 */
@Component
@RequiredArgsConstructor
public class IpPlatformAdmissionOrchestrator {

    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;

    /** 批量查询住院就诊的平台入院登记快照。 */
    public List<IpPlatformAdmissionQueryModel> listSnapshots(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        List<YbEncounterPlatformMap> maps =
            encounterPlatformMapQueryService.listByEncounterIds(unique, tenantId);
        Map<Long, YbEncounterPlatformMap> mapByEncounter = new HashMap<>();
        for (YbEncounterPlatformMap row : maps) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            mapByEncounter.putIfAbsent(row.getEncounterId(), row);
        }
        List<IpPlatformAdmissionQueryModel> result = new ArrayList<>();
        for (Long encounterId : unique) {
            IpPlatformAdmissionQueryModel snap = new IpPlatformAdmissionQueryModel();
            snap.setEncounterId(encounterId);
            YbEncounterPlatformMap mapped = mapByEncounter.get(encounterId);
            if (mapped != null) {
                snap.setContractId(mapped.getContractId());
                snap.setMdtrtId(mapped.getMdtrtId());
                snap.setPlatformAdmitted(Boolean.TRUE);
            } else {
                snap.setPlatformAdmitted(Boolean.FALSE);
            }
            result.add(snap);
        }
        return result;
    }
}
