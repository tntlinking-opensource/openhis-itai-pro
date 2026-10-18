/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 申请单号段编排支持（门诊/住院医生站共用）。
 *
 * <p>{@code AR}：检验/检查/治疗/手术申请（AS-IP-DOR-012 / IF-IP-DOR-23）。
 * {@code AA}：麻醉申请（与 AR 分序列；麻醉药品与麻醉项目共用 AA 单号）。</p>
 */
@Component
@RequiredArgsConstructor
public class ServiceApplyFormOrchestrationSupport {

    /** 业务流水号生成（按日前缀 + 当日序号）。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 领取新的申请单号（前缀 {@link AggregateTypeConstants#REQUISITION_APPLICATION}）。
     *
     * @return 非空 {@code AR} 申请单号
     */
    public String allocateArPrescriptionNo() {
        return businessSequenceService.getSeqByDay(
            AggregateTypeConstants.REQUISITION_APPLICATION,
            BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }

    /**
     * 领取新的麻醉申请单号（前缀 {@link AggregateTypeConstants#ANESTHESIA_APPLICATION}）。
     *
     * @return 非空 {@code AA} 麻醉申请单号
     */
    public String allocateAnesthesiaApplicationPrescriptionNo() {
        return businessSequenceService.getSeqByDay(
            AggregateTypeConstants.ANESTHESIA_APPLICATION,
            BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }

    /**
     * 判断申请单号是否为申请单序列（{@code AR} 前缀）。
     *
     * @param prescriptionNo 处方/申请单号
     * @return true 表示为 AR 申请单号
     */
    public static boolean isRequisitionApplicationPrescriptionNo(String prescriptionNo) {
        if (prescriptionNo == null || prescriptionNo.isEmpty()) {
            return false;
        }
        return prescriptionNo.startsWith(AggregateTypeConstants.REQUISITION_APPLICATION);
    }

    /**
     * 判断是否为麻醉申请单号（{@code AA} 前缀）。
     *
     * @param prescriptionNo 处方/申请单号
     * @return true 表示为 AA 麻醉申请单号
     */
    public static boolean isAnesthesiaApplicationPrescriptionNo(String prescriptionNo) {
        if (prescriptionNo == null || prescriptionNo.isEmpty()) {
            return false;
        }
        return prescriptionNo.startsWith(AggregateTypeConstants.ANESTHESIA_APPLICATION);
    }
}
