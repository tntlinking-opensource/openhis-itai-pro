/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.support;

import com.zxplat.domain.administration.encounter.service.EncounterDomainService;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupRoster;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckDomainService;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckQueryService;
import com.zxplat.domain.share.constants.PeisGroupRosterConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 团检名册逆向操作执行器（撤销到检 / 撤销建单）。
 */
@Service
@RequiredArgsConstructor
public class GroupRosterReversalExecutor {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final PeisGroupCheckDomainService peisGroupCheckDomainService;
    private final EncounterDomainService encounterDomainService;

    /**
     * 撤销到检。
     *
     * @param rosterId   名册主键
     * @param operatorId 操作人
     * @return 更新后的名册
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PeisGroupRoster cancelArrival(Long rosterId, Long operatorId) {
        return peisGroupCheckDomainService.cancelArrival(rosterId, operatorId);
    }

    /**
     * 撤销免收费建单：作废在检 Encounter 并回退名册。
     *
     * @param rosterId   名册主键
     * @param operatorId 操作人
     * @return 更新后的名册
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PeisGroupRoster revertEnrollment(Long rosterId, Long operatorId) {
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(rosterId);
        if (!PeisGroupRosterConstants.PAY_ENROLLED.equals(roster.getPayStatusCode())) {
            throw new BusinessException("ENROLLMENT_NOT_REVERSIBLE", "仅免收费建单可撤销；已收费请走退费流程");
        }
        Long encounterId = roster.getEncounterId();
        if (encounterId != null) {
            encounterDomainService.voidPhysicalExamEncounter(encounterId);
        }
        return peisGroupCheckDomainService.revertEnrollment(rosterId, operatorId);
    }
}
