/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.chinesemedicine.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.outpatient.doctorstation.chinesemedicine.request.SignTcmRxTargetRequest;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.PrescriptionSplitConstants;
import com.zxplat.domain.share.enums.YbMedType;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-DOR-37：门诊中药处方签发取号（按 {@code prescriptionGroupKey} 分组，禁止 splitPrescription）。
 *
 * <p>处方号前缀 {@link AggregateTypeConstants#COMPOSITE_PRESCRIPTION}，与 bus_no（MR）分序列。</p>
 */
@Component
@RequiredArgsConstructor
public class TcmRxSignAssembler {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 按就诊与分组键解析或分配处方号（兼容 AppService 已归组的 requestId 映射入参）。
     *
     * @param encounterId   就诊主键；由 AppService Gate 校验上下文，本类取号按方内 request 复用 prescription_no
     * @param groupKeyToIds 方标识到药品申请主键列表
     * @return prescriptionGroupKey → 处方号
     */
    public Map<String, String> resolvePrescriptionNoByGroupKey(
        Long encounterId,
        Map<String, List<Long>> groupKeyToIds
    ) {
        // Pre：Map 形态入参展开为 signTargets，与列表入参共用同一取号逻辑
        List<SignTcmRxTargetRequest> targets = new ArrayList<>();
        if (groupKeyToIds != null) {
            for (Map.Entry<String, List<Long>> entry : groupKeyToIds.entrySet()) {
                // 空组跳过，避免产生无 requestId 的伪分组
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
        return this.resolvePrescriptionNoByGroupKey(targets);
    }

    /**
     * 按前端方标识分组解析或分配处方号（{@code prescription_no}，前缀 {@link AggregateTypeConstants#COMPOSITE_PRESCRIPTION}）。
     *
     * @param signTargets 待签发行
     * @return prescriptionGroupKey → 处方号
     */
    public Map<String, String> resolvePrescriptionNoByGroupKey(List<SignTcmRxTargetRequest> signTargets) {
        Map<String, String> result = new LinkedHashMap<>();
        // 无待签发行时直接返回空映射，调用方无需再判空
        if (signTargets == null || signTargets.isEmpty()) {
            return result;
        }
        // Pre：按方标识归组 requestId，保持前端分组顺序
        Map<String, List<Long>> requestIdsByGroupKey = new LinkedHashMap<>();
        for (SignTcmRxTargetRequest target : signTargets) {
            // 缺 groupKey 的行不参与取号，避免污染分组键
            if (target == null || isEmpty(target.getPrescriptionGroupKey())) {
                continue;
            }
            String groupKey = trimToNull(target.getPrescriptionGroupKey());
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
        // Pre：方内已有 prescription_no 则复用，减少重复 CP 序列消耗
        for (Map.Entry<String, List<Long>> entry : requestIdsByGroupKey.entrySet()) {
            String existingNo = this.findExistingPrescriptionNo(entry.getValue());
            if (isNotEmpty(existingNo)) {
                existingNoByGroupKey.put(entry.getKey(), trimToNull(existingNo));
            } else {
                // 全组无历史号，计入本批待分配新序列个数
                newRxCount++;
            }
        }
        // Pre：处方号走 CP 分序列（与 bus_no 的 MR 分离，对齐西药 splitPrescription 口径）
        List<String> newNos = this.businessSequenceService.getSeqByDay(
            AggregateTypeConstants.COMPOSITE_PRESCRIPTION,
            BusinessSequenceConstants.BUS_NO_SEQ_LENGTH,
            newRxCount);
        int newIdx = 0;
        // Asm：按原分组顺序输出 groupKey → prescription_no
        for (Map.Entry<String, List<Long>> entry : requestIdsByGroupKey.entrySet()) {
            String groupKey = entry.getKey();
            String prescriptionNo = existingNoByGroupKey.get(groupKey);
            if (isEmpty(prescriptionNo)) {
                // 分配本批新序列中的下一号
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
        // 急诊就诊挂急诊饮片类别，其余门诊挂普通门诊饮片
        boolean emergency = YbMedType.EMERGENCY.getCode().equals(medType);
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
        List<MedicationRequest> requests = this.medicationRequestQueryService.listByIds(uniqueIds);
        for (MedicationRequest request : requests) {
            // 方内任一行已有 prescription_no 即视为整方历史号来源
            if (request != null && isNotEmpty(request.getPrescriptionNo())) {
                return request.getPrescriptionNo();
            }
        }
        return null;
    }
}
