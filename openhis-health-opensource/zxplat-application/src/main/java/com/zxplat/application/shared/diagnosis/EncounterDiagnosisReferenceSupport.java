/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.diagnosis;

import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 就诊诊断引用门禁（医嘱/账单 {@code encounter_diagnosis_ids}）。
 *
 * <p>跨聚合只读编排：分步调各请求/账单 QueryService 计数后汇总（B.6.4；禁止下沉 Encounter QS）。</p>
 */
@Component
@RequiredArgsConstructor
public class EncounterDiagnosisReferenceSupport {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final EncounterQueryService encounterQueryService;

    /**
     * 统计药品/诊疗/耗材请求与账单对指定就诊诊断行的引用总数。
     *
     * @param encounterDiagnosisId 就诊诊断行主键
     * @return 引用总数；入参空时 0
     */
    public int countAdviceOrChargeRef(Long encounterDiagnosisId) {
        if (encounterDiagnosisId == null) {
            return 0;
        }
        int total = 0;
        total += medicationRequestQueryService.countReferencingEncounterDiagnosisId(encounterDiagnosisId);
        total += serviceRequestQueryService.countReferencingEncounterDiagnosisId(encounterDiagnosisId);
        total += deviceRequestQueryService.countReferencingEncounterDiagnosisId(encounterDiagnosisId);
        total += chargeItemQueryService.countReferencingEncounterDiagnosisId(encounterDiagnosisId);
        return total;
    }

    /**
     * 引用存在时拒绝删除/改内容。
     *
     * @param encounterDiagnosisId 就诊诊断行主键
     * @throws BusinessException 仍被引用
     */
    public void assertNotReferenced(Long encounterDiagnosisId) {
        int refCnt = this.countAdviceOrChargeRef(encounterDiagnosisId);
        // 被医嘱或账单引用时阻断，避免结算/分方断链
        if (refCnt > 0) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_DIAGNOSIS_BINDING_REFERENCED,
                "该诊断已被医嘱或账单引用，不可修改或删除");
        }
    }

    /**
     * 西医全量保存前门禁：将被移除的行、以及目录定义发生变更的存量行，若仍被引用则拒绝。
     *
     * @param encounterId                     就诊主键
     * @param incomingEncounterDiagnosisIds   本批仍保留的就诊诊断行 id（可空表示新建）
     * @param incomingDefinitionIdByDxId      本批行 id → 新目录定义 id（仅含带 id 的更新项）
     */
    public void assertWesternBatchSaveAllowed(
        Long encounterId,
        Set<Long> incomingEncounterDiagnosisIds,
        Map<Long, Long> incomingDefinitionIdByDxId
    ) {
        if (encounterId == null) {
            return;
        }
        List<EncounterDiagnosisQueryModel> existing = encounterQueryService.listEncounterDiagnoses(encounterId);
        Set<Long> keepIds = incomingEncounterDiagnosisIds;
        if (keepIds == null) {
            keepIds = Set.of();
        }
        Map<Long, Long> defById = incomingDefinitionIdByDxId;
        if (defById == null) {
            defById = Map.of();
        }
        // Loop：存量西医行 — 未再出现则视为删除；定义变更则视为改内容
        for (EncounterDiagnosisQueryModel row : existing) {
            if (row == null || row.getDiagnosisId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            Long dxId = row.getDiagnosisId();
            boolean removed = !keepIds.contains(dxId);
            Long incomingDefId = defById.get(dxId);
            boolean contentChanged = false;
            if (incomingDefId != null && !Objects.equals(incomingDefId, row.getConditionDefinitionId())) {
                contentChanged = true;
            }
            if (!removed && !contentChanged) {
                continue;
            }
            this.assertNotReferenced(dxId);
        }
    }

    /**
     * 将可空集合规范为可变 HashSet（调用方组装入参用）。
     *
     * @param ids 可空
     * @return 非 null 集合
     */
    public static Set<Long> toMutableIdSet(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(ids);
    }

    /**
     * 将可空 map 规范为可变 HashMap。
     *
     * @param map 可空
     * @return 非 null map
     */
    public static Map<Long, Long> toMutableLongMap(Map<Long, Long> map) {
        if (map == null || map.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(map);
    }
}
