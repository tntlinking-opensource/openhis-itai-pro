/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.nursestation;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.shared.advice.InpatientOrderListSkeleton;
import com.zxplat.application.shared.advice.ServiceApplyFormOrchestrationSupport;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 住院「麻醉申请」范围：仅按申请单号 AA 前缀识别。
 *
 * <p>麻醉项目与药品的 {@code prescriptionNo} 均写 AA 单号；无类目 25 / 同单号池兼容。
 * {@code anesthesiaApplyOnly=true} 仅保留麻醉申请；false/空则排除（普通默认口径）。</p>
 *
 * <p>麻醉申请执行<strong>不走</strong>用法/诊疗自动带（bindItems）；见 {@link #shouldSkipAutoBind}。</p>
 */
@Component
public class InpatientNurseStationAnesthesiaApplyScopeSupport {

    /**
     * 是否命中麻醉申请（方号 AA 前缀）。
     *
     * @param prescriptionNo 方号/申请单号
     * @return true=麻醉申请内容
     */
    public boolean matchesAnesthesiaApply(String prescriptionNo) {
        return ServiceApplyFormOrchestrationSupport.isAnesthesiaApplicationPrescriptionNo(
            trimToNull(prescriptionNo));
    }

    /**
     * 按麻醉申请开关收窄医嘱骨架。
     *
     * @param skeleton            合并后的骨架行
     * @param medDetailById       药嘱明细（骨架无方号时取 prescriptionNo）；可空
     * @param anesthesiaApplyOnly true=仅麻醉；false/空=排除麻醉
     * @return 收窄后的骨架（新列表）
     */
    public List<InpatientOrderListSkeleton> filterSkeletonByAnesthesiaApplyScope(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        Boolean anesthesiaApplyOnly) {
        if (skeleton == null || skeleton.isEmpty()) {
            // 入参空：返回可写空列表，避免调用方 NPE
            if (skeleton == null) {
                return new ArrayList<>();
            }
            return skeleton;
        }
        boolean onlyAnesth = Boolean.TRUE.equals(anesthesiaApplyOnly);
        List<InpatientOrderListSkeleton> kept = new ArrayList<>();
        for (InpatientOrderListSkeleton row : skeleton) {
            // 空骨架行跳过
            if (row == null) {
                continue;
            }
            boolean match = this.matchesSkeletonAnesthesiaApply(row, medDetailById);
            // onlyAnesth=true 保留命中；默认保留非麻醉
            if (match == onlyAnesth) {
                kept.add(row);
            }
        }
        return kept;
    }

    /**
     * 按麻醉申请开关收窄待汇总药嘱主键。
     *
     * @param medReqIds           候选药嘱主键
     * @param medReqById          药嘱明细
     * @param anesthesiaApplyOnly true=仅麻醉；false/空=排除麻醉
     * @return 收窄后的主键列表（保序）
     */
    public List<Long> filterMedReqIdsByAnesthesiaApplyScope(
        List<Long> medReqIds,
        Map<Long, MedicationRequestListQueryModel> medReqById,
        Boolean anesthesiaApplyOnly) {
        if (medReqIds == null || medReqIds.isEmpty()) {
            // 入参空：返回可写空列表，避免调用方 NPE
            if (medReqIds == null) {
                return new ArrayList<>();
            }
            return medReqIds;
        }
        boolean onlyAnesth = Boolean.TRUE.equals(anesthesiaApplyOnly);
        List<Long> kept = new ArrayList<>();
        for (Long medReqId : medReqIds) {
            // 空主键跳过
            if (medReqId == null) {
                continue;
            }
            MedicationRequestListQueryModel med = null;
            if (medReqById != null) {
                med = medReqById.get(medReqId);
            }
            String prescriptionNo = null;
            if (med != null) {
                prescriptionNo = med.getPrescriptionNo();
            }
            boolean match = this.matchesAnesthesiaApply(prescriptionNo);
            // onlyAnesth=true 保留命中；默认保留非麻醉
            if (match == onlyAnesth) {
                kept.add(medReqId);
            }
        }
        return kept;
    }

    /**
     * 麻醉申请内容是否跳过「自动带」逻辑（用法/诊疗 bindItems 均不挂、不落）。
     *
     * @param order     医嘱骨架
     * @param medDetail 药嘱明细（取方号）；诊嘱可空
     * @return true=跳过自动带
     */
    public boolean shouldSkipAutoBind(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail) {
        if (order == null) {
            return false;
        }
        // 诊疗/药嘱：优先骨架方号
        String prescriptionNo = order.getPrescriptionNo();
        if (prescriptionNo == null && medDetail != null) {
            prescriptionNo = medDetail.getPrescriptionNo();
        }
        return this.matchesAnesthesiaApply(prescriptionNo);
    }

    /**
     * 骨架行是否属麻醉申请（方号 AA 前缀）。
     *
     * @param row           骨架行
     * @param medDetailById 药嘱明细（骨架无方号时回落）；可空
     * @return true=麻醉申请内容；耗材等其它类型恒 false
     */
    private boolean matchesSkeletonAnesthesiaApply(
        InpatientOrderListSkeleton row,
        Map<Long, MedicationRequestListQueryModel> medDetailById) {
        // 非药/诊（如耗材）不参与麻醉申请范围
        if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())
            && !ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())) {
            return false;
        }
        // Pre：骨架已带方号时直接对齐
        String prescriptionNo = row.getPrescriptionNo();
        if (prescriptionNo == null
            && ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())
            && medDetailById != null) {
            MedicationRequestListQueryModel med = medDetailById.get(row.getOrderId());
            if (med != null) {
                prescriptionNo = med.getPrescriptionNo();
            }
        }
        return this.matchesAnesthesiaApply(prescriptionNo);
    }
}
