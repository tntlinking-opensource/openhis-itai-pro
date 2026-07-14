/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientAutoRollEncounterQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院滚费候选就诊上下文加载（AS-IP-NUR-013；B.6.4 多聚合只读编排）。
 *
 * <p>分步调 {@link EncounterQueryService} 与 {@link AccountQueryService}，合并 accountId 与责任护士。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientAutoRollEncounterContextLoader {

    private final EncounterQueryService encounterQueryService;
    private final AccountQueryService accountQueryService;

    /**
     * 列出已分床且未清床的 IMP 滚费候选，并补齐默认个人现金账户 id 与责任护士 id。
     *
     * @return 滚费候选列表；无命中时空列表
     */
    public List<InpatientAutoRollEncounterQueryModel> listInpatientAutoRollEncounterContexts() {
        List<InpatientAutoRollEncounterQueryModel> base =
            this.encounterQueryService.listInpatientAutoRollEncounterBaseContexts();
        if (base == null || base.isEmpty()) {
            return List.of();
        }
        Set<Long> encounterIds = new LinkedHashSet<>();
        for (InpatientAutoRollEncounterQueryModel row : base) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Account> accountByEncounterId =
            this.accountQueryService.mapRegistrationAccountsByEncounterIds(encounterIds);
        Map<Long, Long> primaryNurseIdByEncounterId = this.loadPrimaryNurseIdByEncounterId(encounterIds);
        List<InpatientAutoRollEncounterQueryModel> result = new ArrayList<>(base.size());
        for (InpatientAutoRollEncounterQueryModel row : base) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            Account account = accountByEncounterId.get(row.getEncounterId());
            if (account != null) {
                row.setAccountId(account.getId());
            }
            row.setPrimaryNurseId(primaryNurseIdByEncounterId.get(row.getEncounterId()));
            result.add(row);
        }
        return result;
    }

    /**
     * 批量解析责任护士主键（PRIMARY_NURSE 活动参与者）。
     *
     * @param encounterIds 就诊主键集合
     * @return encounterId → primaryNurseId
     */
    private Map<Long, Long> loadPrimaryNurseIdByEncounterId(Set<Long> encounterIds) {
        List<EncounterParticipant> participants =
            this.encounterQueryService.listActiveParticipantEntitiesByEncounterIds(encounterIds);
        Map<Long, Long> primaryNurseIdByEncounterId = new HashMap<>();
        if (participants == null) {
            return primaryNurseIdByEncounterId;
        }
        for (EncounterParticipant participant : participants) {
            if (participant == null || participant.getEncounterId() == null || participant.getPractitionerId() == null) {
                continue;
            }
            if (EncounterParticipantRole.PRIMARY_NURSE.getCode().equals(participant.getRoleCode())) {
                primaryNurseIdByEncounterId.putIfAbsent(participant.getEncounterId(), participant.getPractitionerId());
            }
        }
        return primaryNurseIdByEncounterId;
    }
}
