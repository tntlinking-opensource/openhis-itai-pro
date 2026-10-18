/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.support;

import com.zxplat.plugin.peis.application.orchestration.PeisRegistrationOrchestrator;
import com.zxplat.plugin.peis.application.financial.charge.support.PhysicalExamPlannedChargeEnsureSupport;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupRoster;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 到检前换套餐：重建 SR/快照及未结 draft 挂费。
 */
@Component
@RequiredArgsConstructor
public class GroupRosterPackageRebuildExecutor {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final PeisRegistrationOrchestrator peisRegistrationOrchestrator;
    private final PhysicalExamPlannedChargeEnsureSupport physicalExamPlannedChargeEnsureSupport;

    /**
     * 按新选定套餐重建就诊项目（流水号不变）。
     *
     * @param rosterId            名册主键
     * @param newPackageActivityId 新套餐 AD
     * @param operatorId          操作人
     */
    public void rebuildEncounterPackage(Long rosterId, Long newPackageActivityId, Long operatorId) {
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(rosterId);
        peisRegistrationOrchestrator.rebuildGroupEncounterPackage(
            roster.getEncounterId(), newPackageActivityId, operatorId);
        physicalExamPlannedChargeEnsureSupport.ensureForEncounter(roster.getEncounterId(), operatorId);
    }
}
