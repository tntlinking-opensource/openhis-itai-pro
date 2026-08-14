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
 * <p>优先与 IF-IP-DOR-01 列表一致：当前会话科室下 {@code IMP + IN_PROGRESS/ON_HOLD} 在院队列；
 * <strong>不</strong>按主治/责任医生等活动参与者收窄（与门诊 **BR-DOR-009** 不同）。</p>
 *
 * <p>若不在医生站在科队列（已出院/已结算、病案首页临床侧栏等），按就诊主键回落读取，
 * 仍校验 patientId 与读模型一致；写路径另有可写态门控（IN_PROGRESS/ON_HOLD）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientDoctorStationEncounterVisibilityResolver {

    /** 住院就诊上下文读侧（Q-IPI-006 医生站队列）。 */
    private final EncounterContextReader encounterContextReader;

    /** 患者与就诊一致性 Gate。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /**
     * 断言就诊可访问且患者卡片一致（本科室在科队列优先；否则按就诊主键回落）。
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
     * 断言就诊可访问且患者卡片一致（本科室在科队列优先；否则按就诊主键回落）。
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
        InpatientEncounterContextQueryModel encounterSnapshot = this.resolveAccessibleSnapshot(encounterId);
        // 就诊不存在或不可读
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
     * 断言就诊可访问（不校验 request.patientId）。
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
        InpatientEncounterContextQueryModel encounterSnapshot = this.resolveAccessibleSnapshot(encounterId);
        if (encounterSnapshot == null) {
            throw new BusinessException(errorCode, message);
        }
        return encounterSnapshot;
    }

    /**
     * 解析可访问就诊快照：优先医生站在科队列，否则按就诊主键回落（病案/出院场景）。
     *
     * @param encounterId 就诊主键
     * @return 快照；不存在时 null
     */
    private InpatientEncounterContextQueryModel resolveAccessibleSnapshot(Long encounterId) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setDoctorStationQueue(Boolean.TRUE);
        query.setEncounterIdsIn(Collections.singletonList(encounterId));
        // Pre：复用 Q-IPI-006 医生站队列谓词（科室全员可见，不按参与者收窄）
        List<InpatientEncounterContextQueryModel> visibleRows =
            this.encounterContextReader.listInpatientEncounterContexts(query);
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && encounterId.equals(row.getEncounterId())) {
                return row;
            }
        }
        // 不在在科队列：按就诊主键回落（已出院/病案首页临床侧栏）
        return this.encounterContextReader.getInpatientContextByEncounterId(encounterId).orElse(null);
    }
}
