/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.support;

import com.openhis.health.application.peis.orchestration.PeisRegistrationOrchestrator;
import com.openhis.health.application.peis.orchestration.command.RegisterGroupAfterPaidCommand;
import com.openhis.health.application.peis.orchestration.result.RegisterPersonalCheckInResult;
import com.openhis.health.domain.peis.group.entity.PeisGroupBatch;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckDomainService;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.PeisExamKindConstants;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 团检名册建单执行器（免收费 P0-1；真实收费路径复用编排后 markPaid）。
 *
 * <p>登记台与筹备页批量建单均经本类调用 {@link PeisRegistrationOrchestrator#registerGroupAfterPaid}。</p>
 */
@Service
@RequiredArgsConstructor
public class GroupRosterEnrollmentExecutor {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final PeisGroupCheckDomainService peisGroupCheckDomainService;
    private final PeisRegistrationOrchestrator peisRegistrationOrchestrator;

    /**
     * 免收费建单：创建 PE Encounter 并标记 ENROLLED。
     *
     * @param rosterId      名册主键
     * @param orgIdOverride 机构覆盖（可选）
     * @param operatorId    操作人
     * @return 建单结果（含 encounterId）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public RegisterPersonalCheckInResult enrollOneRoster(Long rosterId, Long orgIdOverride, Long operatorId) {
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(rosterId);
        if (PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())) {
            throw new BusinessException("ROSTER_ALREADY_ENROLLED", "该人员已建单");
        }
        RegisterPersonalCheckInResult result = registerEncounterForRoster(roster, orgIdOverride, operatorId);
        peisGroupCheckDomainService.markEnrolled(rosterId, result.getEncounterId(), operatorId);
        return result;
    }

    /**
     * 仅创建 Encounter（供真实收费路径在 markPaid 前调用）。
     *
     * @param roster        名册行
     * @param orgIdOverride 机构覆盖（可选）
     * @param operatorId    操作人
     * @return 建单结果
     */
    public RegisterPersonalCheckInResult registerEncounterForRoster(
            PeisGroupRoster roster, Long orgIdOverride, Long operatorId) {
        if (roster.getPatientId() == null) {
            throw new BusinessException("ROSTER_NO_PATIENT", "请先绑定患者档案");
        }
        PeisGroupBatch batch = peisGroupCheckQueryService.requireBatch(roster.getBatchId());
        PeisGroupContract contract = peisGroupCheckQueryService.requireContract(batch.getContractId());
        Long orgId = orgIdOverride;
        if (orgId == null) {
            orgId = batch.getOrgId();
        }
        if (orgId == null) {
            orgId = contract.getOrgId();
        }
        if (orgId == null) {
            throw new ValidationException("团检建单失败：机构不能为空");
        }
        RegisterGroupAfterPaidCommand cmd = new RegisterGroupAfterPaidCommand();
        cmd.setPatientId(roster.getPatientId());
        cmd.setPackageActivityId(batch.getPackageActivityId());
        cmd.setOrgId(orgId);
        cmd.setOperatorId(operatorId);
        cmd.setCompanyId(batch.getCompanyId());
        cmd.setContractId(batch.getContractId());
        cmd.setGroupBatchId(batch.getId());
        cmd.setExamKind(PeisExamKindConstants.GROUP);
        return peisRegistrationOrchestrator.registerGroupAfterPaid(cmd);
    }
}
