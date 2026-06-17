/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.assembler;

import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.request.SignTcmRxTargetRequest;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.PrescriptionSplitConstants;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-DOR-37：中药处方签发取号（按 {@code prescriptionGroupKey} 分组，禁止 splitPrescription）。
 */
@Component
@RequiredArgsConstructor
public class TcmRxSignAssembler {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 按就诊与分组键解析或分配处方号（兼容按组内 requestId 列表调用）。
     *
     * @param encounterId    就诊主键
     * @param groupKeyToIds  方标识到药品申请主键列表
     * @return prescriptionGroupKey → 处方号
     */
    public Map<String, String> resolvePrescriptionNoByGroupKey(
        Long encounterId,
        Map<String, List<Long>> groupKeyToIds
    ) {
        List<SignTcmRxTargetRequest> targets = new ArrayList<>();
        if (groupKeyToIds != null) {
            for (Map.Entry<String, List<Long>> entry : groupKeyToIds.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                for (Long medicationRequestId : entry.getValue()) {
                    SignTcmRxTargetRequest target = new SignTcmRxTargetRequest();
                    target.setPrescriptionGroupKey(entry.getKey());
                    target.setMedicationRequestId(medicationRequestId);
                    targets.add(target);
                }
            }
        }
        return this.resolvePrescriptionNoByGroupKey(targets, null);
    }

    /**
     * 按前端方标识分组解析或分配处方号。
     *
     * @param signTargets 待签发行
     * @param medType     就诊医保医疗类别（推导 rx_type_code）
     * @return prescriptionGroupKey → 处方号
     */
    public Map<String, String> resolvePrescriptionNoByGroupKey(
        List<SignTcmRxTargetRequest> signTargets,
        String medType
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        if (signTargets == null || signTargets.isEmpty()) {
            return result;
        }
        Map<String, List<Long>> requestIdsByGroupKey = new LinkedHashMap<>();
        for (SignTcmRxTargetRequest target : signTargets) {
            if (target == null || StringUtils.isBlank(target.getPrescriptionGroupKey())) {
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
            String existingNo = this.findExistingPrescriptionNo(entry.getValue());
            if (StringUtils.isNotBlank(existingNo)) {
                existingNoByGroupKey.put(entry.getKey(), existingNo.trim());
            } else {
                newRxCount++;
            }
        }
        List<String> newNos = this.businessSequenceService.getSeqByDay(
            AggregateTypeConstants.MEDICATION_REQUEST,
            BusinessSequenceConstants.BUS_NO_SEQ_LENGTH,
            newRxCount);
        int newIdx = 0;
        for (Map.Entry<String, List<Long>> entry : requestIdsByGroupKey.entrySet()) {
            String groupKey = entry.getKey();
            String prescriptionNo = existingNoByGroupKey.get(groupKey);
            if (StringUtils.isBlank(prescriptionNo)) {
                prescriptionNo = newNos.get(newIdx);
                newIdx++;
            }
            result.put(groupKey, prescriptionNo);
        }
        return result;
    }

    /**
     * 推导门诊中药饮片处方类别码（BR-TCM-RX-008）。
     *
     * @param medType 医保医疗类别
     * @return rx_type_code
     */
    public String deriveHerbalRxTypeCode(String medType) {
        boolean emergency = YbMedType.EMERGENCY.getCode().equals(medType);
        if (emergency) {
            return PrescriptionSplitConstants.RX_TYPE_EMERGENCY_HERBAL;
        }
        return PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_HERBAL;
    }

    /**
     * 生成本批次签发码。
     *
     * @return signCode
     */
    public String generateBatchSignCode() {
        List<String> codes = this.businessSequenceService.getSeqByDay(
            AggregateTypeConstants.MEDICATION_REQUEST,
            BusinessSequenceConstants.BUS_NO_SEQ_LENGTH,
            1);
        return codes.get(0);
    }

    private String findExistingPrescriptionNo(List<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return null;
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(requestIds);
        List<MedicationRequest> requests = this.medicationRequestQueryService.listByIds(uniqueIds);
        for (MedicationRequest request : requests) {
            if (request != null && StringUtils.isNotBlank(request.getPrescriptionNo())) {
                return request.getPrescriptionNo();
            }
        }
        return null;
    }
}
