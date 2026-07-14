/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.assembler;

import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request.SignTcmRxTargetRequest;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.PrescriptionSplitConstants;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-IP-TCM-37：住院中药处方签发取号（禁止 splitPrescription）。
 */
@Component
@RequiredArgsConstructor
public class InpatientTcmRxSignAssembler {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 按前端方标识分组解析或分配处方号（{@code prescription_no}，前缀 {@link AggregateTypeConstants#COMPOSITE_PRESCRIPTION}）。
     *
     * @param signTargets 待签发行
     * @return prescriptionGroupKey → 处方号
     */
    public Map<String, String> resolvePrescriptionNoByGroupKey(List<SignTcmRxTargetRequest> signTargets) {
        Map<String, String> result = new LinkedHashMap<>();
        if (signTargets == null || signTargets.isEmpty()) {
            return result;
        }
        Map<String, List<Long>> requestIdsByGroupKey = new LinkedHashMap<>();
        for (SignTcmRxTargetRequest target : signTargets) {
            if (target == null || StringUtils.isEmpty(target.getPrescriptionGroupKey())) {
                continue;
            }
            String groupKey = target.getPrescriptionGroupKey().trim();
            List<Long> ids = requestIdsByGroupKey.get(groupKey);
            if (ids == null) {
                ids = new ArrayList<>();
                requestIdsByGroupKey.put(groupKey, ids);
            }
            if (target.getMedicationRequestId() != null) {
                ids.add(target.getMedicationRequestId());
            }
        }
        int newRxCount = 0;
        Map<String, String> existingNoByGroupKey = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : requestIdsByGroupKey.entrySet()) {
            String existingNo = findExistingPrescriptionNo(entry.getValue());
            if (StringUtils.isNotEmpty(existingNo)) {
                existingNoByGroupKey.put(entry.getKey(), existingNo.trim());
            } else {
                newRxCount++;
            }
        }
        // Pre：处方号走 CP 分序列（与 bus_no 的 MR 分离，对齐西药 splitPrescription 口径）
        List<String> newNos = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.COMPOSITE_PRESCRIPTION,
            BusinessSequenceConstants.BUS_NO_SEQ_LENGTH,
            newRxCount);
        int newIdx = 0;
        for (Map.Entry<String, List<Long>> entry : requestIdsByGroupKey.entrySet()) {
            String groupKey = entry.getKey();
            String prescriptionNo = existingNoByGroupKey.get(groupKey);
            if (StringUtils.isEmpty(prescriptionNo)) {
                prescriptionNo = newNos.get(newIdx);
                newIdx++;
            }
            result.put(groupKey, prescriptionNo);
        }
        return result;
    }

    /**
     * 推导住院中药饮片处方类别码（BR-IP-TCM-007）。
     *
     * @param medType 医保医疗类别
     * @return rx_type_code
     */
    public String deriveInpatientHerbalRxTypeCode(String medType) {
        boolean emergency = YbMedType.EMERGENCY.getCode().equals(medType)
            || YbMedType.EMERGENCY_TO_HOSPITALIZATION.getCode().equals(medType);
        if (emergency) {
            return PrescriptionSplitConstants.RX_TYPE_EMERGENCY_HERBAL;
        }
        return PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_HERBAL;
    }

    /**
     * 方内任一行已有 prescription_no 即作为整方历史处方号。
     *
     * @param requestIds 药嘱 id 列表
     * @return 处方号；均无则 null
     */
    private String findExistingPrescriptionNo(List<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return null;
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(requestIds);
        List<MedicationRequest> requests = medicationRequestQueryService.listByIds(uniqueIds);
        for (MedicationRequest request : requests) {
            if (request != null && StringUtils.isNotEmpty(request.getPrescriptionNo())) {
                return request.getPrescriptionNo();
            }
        }
        return null;
    }
}
