/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.service;

import com.openhis.health.application.peis.orchestration.PeisRegistrationOrchestrator;
import com.openhis.health.application.peis.orchestration.command.RegisterGroupAfterPaidCommand;
import com.openhis.health.application.peis.orchestration.result.RegisterPersonalCheckInResult;
import com.openhis.health.domain.peis.group.entity.PeisGroupBatch;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckDomainService;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
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
    private final PeisRegistrationOrchestrator peisRegistrationOrchestrator;

    @Transactional(rollbackFor = Exception.class)
    public void settleOneRoster(Long rosterId, Long orgIdOverride, Long operatorId) {
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(rosterId);
        if (PeisGroupRosterConstants.PAY_PAID.equals(roster.getPayStatusCode())) {
            throw new BusinessException("ROSTER_ALREADY_PAID", "该人员已收费");
        }
        if (roster.getPatientId() == null) {
            throw new BusinessException("ROSTER_NO_PATIENT", "请先绑定患者档案");
        }
        PeisGroupBatch batch = peisGroupCheckQueryService.requireBatch(roster.getBatchId());
        PeisGroupContract contract = peisGroupCheckQueryService.requireContract(batch.getContractId());
        Long orgId = orgIdOverride != null ? orgIdOverride : batch.getOrgId();
        if (orgId == null) {
            orgId = contract.getOrgId();
        }
        if (orgId == null) {
            throw new ValidationException("批量收费失败：机构不能为空");
        }
        RegisterGroupAfterPaidCommand cmd = new RegisterGroupAfterPaidCommand();
        cmd.setPatientId(roster.getPatientId());
        cmd.setPackageActivityId(batch.getPackageActivityId());
        cmd.setOrgId(orgId);
        cmd.setOperatorId(operatorId);
        cmd.setCompanyId(batch.getCompanyId());
        cmd.setContractId(batch.getContractId());
        cmd.setGroupBatchId(batch.getId());
        RegisterPersonalCheckInResult result = peisRegistrationOrchestrator.registerGroupAfterPaid(cmd);
        peisGroupCheckDomainService.markPaid(rosterId, result.getEncounterId(), operatorId);
    }
}
