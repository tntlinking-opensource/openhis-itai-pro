/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.support;

import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel;
import com.zxplat.plugin.peis.domain.encounterext.service.PeisEncounterExtQueryService;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupRoster;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PeisGroupRosterConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 分科诊台就诊资格校验（BR-GRP-PAY-01 v1.3）。
 *
 * <p>团检：名册 {@code ENROLLED}（免收费建单）或 {@code PAID}（FIN-002）或收银结清后可录入；个检不在此收口。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisDeptDeskEligibilitySupport {

    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 校验就诊是否允许进入分科录入（DES-02～04 写前 Gate）。
     *
     * @param encounterId PE 就诊主键
     */
    public void assertEncounterEligibleForDeptDesk(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Optional<PeisEncounterExtModel> extOpt = peisEncounterExtQueryService.findOptionalByEncounterId(encounterId);
        // 非团检就诊：现行个检主路径不在此强制收银（REG 收费态由登记台引导）
        if (extOpt.isEmpty() || !Boolean.TRUE.equals(extOpt.get().getGroupFlag())) {
            return;
        }
        // 团检：名册已建单（免收费 ENROLLED 或收费 PAID）视为满足 BR-GRP-PAY-01
        Optional<PeisGroupRoster> rosterOpt = peisGroupCheckQueryService.findRosterByEncounterId(encounterId);
        if (rosterOpt.isPresent()
            && PeisGroupRosterConstants.isEnrolled(rosterOpt.get().getPayStatusCode())) {
            return;
        }
        // 团检 FIN-002 真实收费结清路径
        if (chargeItemQueryService.isEncounterPaid(encounterId)) {
            return;
        }
        throw new BusinessException("NOT_ENROLLED", "请先完成团检建单或收费");
    }
}
