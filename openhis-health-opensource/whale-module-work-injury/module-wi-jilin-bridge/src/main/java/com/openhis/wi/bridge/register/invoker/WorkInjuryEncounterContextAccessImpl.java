/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.register.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import com.openhis.wi.domain.register.access.WorkInjuryEncounterContextAccess;
import com.openhis.wi.domain.register.result.WorkInjuryEncounterContextResult;
import com.openhis.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.openhis.wi.gateway.scheme.shared.model.WiQualificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 就诊维度工伤上下文查询端口实现。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class WorkInjuryEncounterContextAccessImpl implements WorkInjuryEncounterContextAccess {

    private final YbWiEncounterAnchorRepository encounterAnchorRepository;

    @Override
    public WorkInjuryEncounterContextResult resolveContext(Long encounterId) {
        WorkInjuryEncounterContextResult context = new WorkInjuryEncounterContextResult()
                .setWorkInjuryEncounter(false)
                .setQualificationValid(false);
        if (encounterId == null) {
            return context;
        }
        return encounterAnchorRepository.findByEncounterId(encounterId)
                .map(this::toEncounterContext)
                .orElse(context);
    }

    private WorkInjuryEncounterContextResult toEncounterContext(YbWiEncounterAnchor anchor) {
        boolean workInjury = isNotEmpty(anchor.getPsnNo());
        boolean valid = WiQualificationStatus.VALID.equals(anchor.getQualificationStatus())
                && isNotEmpty(anchor.getQualificationId());
        return new WorkInjuryEncounterContextResult()
                .setWorkInjuryEncounter(workInjury)
                .setQualificationValid(valid)
                .setApprovalStatusLabel(resolveApprovalLabel(anchor))
                .setContractId(anchor.getContractId())
                .setNeeds2301Upload(workInjury && !valid);
    }

    private String resolveApprovalLabel(YbWiEncounterAnchor anchor) {
        if (WiQualificationStatus.VALID.equals(anchor.getQualificationStatus())) {
            return "审批有效";
        }
        if (WiQualificationStatus.PENDING_9102.equals(anchor.getQualificationStatus())) {
            return "待9102确认";
        }
        if (isNotEmpty(anchor.getQualificationId())) {
            return "已选审批";
        }
        return "未审批";
    }
}
