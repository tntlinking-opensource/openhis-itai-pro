/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.nursingrecord.support;

import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 住院护士站就诊可见性 Gate（AS-IP-NUR-001；体温单/护理记录等 Tab 共用）。
 *
 * <p>校验目标就诊须在护士站队列 + 管辖病区内，且 request.patientId 与读模型一致。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationEncounterVisibilityResolver {

    /** 住院就诊上下文加载。 */
    private final EncounterContextReader EncounterContextReader;

    /** 管辖病区解析。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;

    /** 患者与就诊一致性 Gate。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /**
     * 断言就诊对当前护士站可见且患者卡片一致（BR-IP-NUR-002 / BR-IP-TMP-001）。
     *
     * @param encounterId 目标就诊
     * @param patientId   左侧卡片患者 id
     * @param errorCode   不可见或不一致时业务码
     * @param message     用户提示
     * @return 可见就诊读模型快照
     * @throws BusinessException 不可见或不一致
     */
    public InpatientEncounterContextQueryModel assertVisible(
        Long encounterId,
        Long patientId,
        String errorCode,
        String message) {
        // Gate：就诊主键与患者主键缺一不可，防止空参穿透到读侧
        if (encounterId == null || patientId == null) {
            throw new BusinessException(errorCode, message);
        }
        List<Long> wardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setNurseStationQueue(Boolean.TRUE);
        query.setEncounterIdsIn(Collections.singletonList(encounterId));
        if (wardIds != null && !wardIds.isEmpty()) {
            query.setManagedWardIds(wardIds);
        }
        // Pre：按护士站队列 + 病区收窄加载就诊上下文
        List<InpatientEncounterContextQueryModel> visibleRows =
            EncounterContextReader.listInpatientEncounterContexts(query);
        InpatientEncounterContextQueryModel encounterSnapshot = null;
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && encounterId.equals(row.getEncounterId())) {
                encounterSnapshot = row;
                break;
            }
        }
        // 业务分支：不在可见队列内则拒绝，防止跨病区越权读写
        if (encounterSnapshot == null) {
            throw new BusinessException(errorCode, message);
        }
        // Gate：卡片 patientId 须与读模型一致（BR-VS-001）
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            patientId,
            encounterSnapshot.getPatientId(),
            errorCode,
            message);
        return encounterSnapshot;
    }
}
