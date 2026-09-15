/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.support;

import com.openhis.health.application.peis.orchestration.PeisRegistrationOrchestrator;
import com.openhis.health.application.peis.orchestration.result.RegisterPersonalCheckInResult;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckDomainService;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 名册选定套餐 + 建单 / 到检前换套餐编排。
 */
@Component
@RequiredArgsConstructor
public class GroupRosterPackageSelectionSupport {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final PeisGroupCheckDomainService peisGroupCheckDomainService;
    private final GroupRosterEnrollmentExecutor groupRosterEnrollmentExecutor;
    private final GroupRosterPackageRebuildExecutor groupRosterPackageRebuildExecutor;

    /**
     * 选定套餐并建单（未建单路径）。
     *
     * @param rosterId            名册主键
     * @param packageActivityId   选定套餐
     * @param orgIdOverride       机构覆盖
     * @param operatorId          操作人
     * @return 建单结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public RegisterPersonalCheckInResult selectAndEnroll(
            Long rosterId, Long packageActivityId, Long orgIdOverride, Long operatorId) {
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(rosterId);
        if (PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())) {
            throw new BusinessException("ROSTER_ALREADY_ENROLLED", "该人员已建单，请使用换套餐");
        }
        peisGroupCheckDomainService.assignSelectedPackage(rosterId, packageActivityId, operatorId);
        return groupRosterEnrollmentExecutor.enrollOneRoster(rosterId, orgIdOverride, operatorId);
    }

    /**
     * 到检前更换选定套餐（已建单路径）。
     *
     * @param rosterId            名册主键
     * @param packageActivityId   新选定套餐
     * @param operatorId          操作人
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void changeSelectedPackageBeforeArrival(Long rosterId, Long packageActivityId, Long operatorId) {
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(rosterId);
        if (!PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())) {
            throw new BusinessException("NOT_ENROLLED", "请先完成团检建单");
        }
        if (PeisGroupRosterConstants.ARRIVAL_ARRIVED.equals(roster.getArrivalStatusCode())) {
            throw new BusinessException("ARRIVAL_ALREADY_CONFIRMED", "已到检，不可换套餐");
        }
        if (roster.getEncounterId() == null) {
            throw new BusinessException("ENCOUNTER_MISSING", "名册未关联体检就诊");
        }
        peisGroupCheckDomainService.assignSelectedPackage(rosterId, packageActivityId, operatorId);
        groupRosterPackageRebuildExecutor.rebuildEncounterPackage(rosterId, packageActivityId, operatorId);
    }
}
