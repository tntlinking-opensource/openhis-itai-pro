/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.encountermanage.support;

import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.InpatientDoctorStationConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 住院医生站就诊可见性 Gate（AS-IP-DOR-001 **BR-IP-DOR-010**；诊断/医嘱/中医等 Tab 共用）。
 *
 * <p>可见范围与 IF-IP-DOR-01 列表一致：当前会话科室（须在岗位出诊管辖内）下
 * {@code IMP + IN_PROGRESS/ON_HOLD} 的全部在院患者；<strong>不</strong>按主治/责任医生、
 * 转科发起人等活动参与者收窄（与门诊 **BR-DOR-009** 不同）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientDoctorStationEncounterVisibilityResolver {

    /** 住院就诊上下文读侧（Q-IPI-006 医生站队列）。 */
    private final EncounterContextReader encounterContextReader;

    /** 患者与就诊一致性 Gate。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /**
     * 断言就诊对当前住院医生站可见且患者卡片一致（**BR-IP-DOR-010**）。
     *
     * @param encounterId 目标就诊主键
     * @param patientId   左侧卡片患者主键
     * @return 可见就诊读模型快照
     * @throws BusinessException 不可见或患者不一致时抛出 {@link InpatientDoctorStationConstants#EX_IP_DOR_103}
     */
    public InpatientEncounterContextQueryModel assertVisible(Long encounterId, Long patientId) {
        return this.assertVisible(
            encounterId,
            patientId,
            InpatientDoctorStationConstants.EX_IP_DOR_103,
            "当前患者不在本科室可见范围，请刷新左侧列表");
    }

    /**
     * 断言就诊对当前住院医生站可见且患者卡片一致（**BR-IP-DOR-010**）。
     *
     * @param encounterId 目标就诊主键
     * @param patientId   左侧卡片患者主键
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
        // Gate：就诊与患者主键缺一不可，防止空参穿透到读侧
        if (encounterId == null || patientId == null) {
            throw new BusinessException(errorCode, message);
        }
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setDoctorStationQueue(Boolean.TRUE);
        query.setEncounterIdsIn(Collections.singletonList(encounterId));
        // Pre：复用 Q-IPI-006 医生站队列谓词（科室全员可见，不按参与者收窄）
        List<InpatientEncounterContextQueryModel> visibleRows =
            this.encounterContextReader.listInpatientEncounterContexts(query);
        InpatientEncounterContextQueryModel encounterSnapshot = null;
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && encounterId.equals(row.getEncounterId())) {
                encounterSnapshot = row;
                break;
            }
        }
        // 业务分支：不在医生站可见队列内则拒绝，防止跨科室越权读写
        if (encounterSnapshot == null) {
            throw new BusinessException(errorCode, message);
        }
        // Gate：卡片 patientId 须与读模型一致
        this.patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            patientId,
            encounterSnapshot.getPatientId(),
            errorCode,
            message);
        return encounterSnapshot;
    }

    /**
     * 断言就诊对当前住院医生站可见（**BR-IP-DOR-010**；不校验 request.patientId）。
     *
     * <p>仅用于删除等请求未携带 {@code patientId} 的写路径；其它 IF 须用
     * {@link #assertVisible(Long, Long)}。</p>
     *
     * @param encounterId 目标就诊主键
     * @param errorCode   不可见时业务码
     * @param message     用户提示
     * @return 可见就诊读模型快照
     * @throws BusinessException 不可见
     */
    public InpatientEncounterContextQueryModel assertVisibleEncounter(
        Long encounterId,
        String errorCode,
        String message) {
        if (encounterId == null) {
            throw new BusinessException(errorCode, message);
        }
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setDoctorStationQueue(Boolean.TRUE);
        query.setEncounterIdsIn(Collections.singletonList(encounterId));
        List<InpatientEncounterContextQueryModel> visibleRows =
            this.encounterContextReader.listInpatientEncounterContexts(query);
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && encounterId.equals(row.getEncounterId())) {
                return row;
            }
        }
        throw new BusinessException(errorCode, message);
    }
}
