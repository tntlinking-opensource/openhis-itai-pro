/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.groupcharge.service;

import com.zxplat.plugin.peis.application.group.support.GroupRosterEnrollmentExecutor;
import com.zxplat.plugin.peis.application.orchestration.result.RegisterPersonalCheckInResult;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupRoster;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckDomainService;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckQueryService;
import com.zxplat.domain.share.constants.PeisGroupRosterConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
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
