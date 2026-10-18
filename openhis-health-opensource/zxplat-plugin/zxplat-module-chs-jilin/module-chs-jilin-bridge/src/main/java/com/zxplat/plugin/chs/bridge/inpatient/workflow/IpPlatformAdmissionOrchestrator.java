/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.workflow;

import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.zxplat.plugin.chs.domain.inpatient.queries.model.IpPlatformAdmissionQueryModel;
import com.zxplat.plugin.chs.domain.inpatient.service.YbInpatientPreSettleQueryService;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.zxplat.plugin.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
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
 * <p>读 yb_encounter_platform_map + 住院预结算参保地；本类不调 gateway。</p>
 */
@Component
@RequiredArgsConstructor
public class IpPlatformAdmissionOrchestrator {

    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;

    private final YbInpatientPreSettleQueryService inpatientPreSettleQueryService;

    /** 批量查询住院就诊的平台入院登记快照（含参保地）。 */
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
        // Pre：住院预结算参保地（同就诊取最新有效 ACTIVE/SETTLED）
        Map<Long, String> insuplcByEncounter = new HashMap<>();
        for (YbInpatientPreSettle pre : this.inpatientPreSettleQueryService.listByEncounterIds(unique, tenantId)) {
            if (pre == null || pre.getEncounterId() == null) {
                continue;
            }
            if (!this.isUsablePreSettleStatus(pre.getStatus())) {
                continue;
            }
            String insuplc = StringUtils.trimToNull(pre.getInsuplcAdmdvs());
            if (insuplc == null) {
                continue;
            }
            // 已按 createdAt 降序：首个有效即最新
            insuplcByEncounter.putIfAbsent(pre.getEncounterId(), insuplc);
        }
        List<IpPlatformAdmissionQueryModel> result = new ArrayList<>();
        for (Long encounterId : unique) {
            IpPlatformAdmissionQueryModel snap = new IpPlatformAdmissionQueryModel();
            snap.setEncounterId(encounterId);
            YbEncounterPlatformMap mapped = mapByEncounter.get(encounterId);
            if (mapped != null) {
                snap.setContractId(mapped.getContractId());
                snap.setMdtrtId(mapped.getMdtrtId());
                snap.setPsnNo(mapped.getPsnNo());
                snap.setPlatformAdmitted(Boolean.TRUE);
            } else {
                snap.setPlatformAdmitted(Boolean.FALSE);
            }
            snap.setInsuplcAdmdvs(insuplcByEncounter.get(encounterId));
            result.add(snap);
        }
        return result;
    }

    /**
     * 预结算状态是否可用于取参保地。
     *
     * @param status 快照状态
     * @return ACTIVE/SETTLED 为 true
     */
    private boolean isUsablePreSettleStatus(String status) {
        return YbInpatientPreSettle.STATUS_ACTIVE.equals(status)
            || YbInpatientPreSettle.STATUS_SETTLED.equals(status);
    }
}
