/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.service;

import com.openhis.health.application.peis.group.support.GroupRosterEnrollmentExecutor;
import com.openhis.health.application.peis.orchestration.result.RegisterPersonalCheckInResult;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckDomainService;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单条名册结算执行器（独立事务，供批量收费逐行调用）。
 */
@Service
@RequiredArgsConstructor
class GroupChargeSettlementExecutor {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final PeisGroupCheckDomainService peisGroupCheckDomainService;
    private final GroupRosterEnrollmentExecutor groupRosterEnrollmentExecutor;

    @Transactional(rollbackFor = Exception.class)
    public void settleOneRoster(Long rosterId, Long orgIdOverride, Long operatorId) {
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(rosterId);
        if (PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())) {
            throw new BusinessException("ROSTER_ALREADY_ENROLLED", "该人员已建单");
        }
        RegisterPersonalCheckInResult result =
            groupRosterEnrollmentExecutor.registerEncounterForRoster(roster, orgIdOverride, operatorId);
        peisGroupCheckDomainService.markPaid(rosterId, result.getEncounterId(), operatorId);
    }
}
