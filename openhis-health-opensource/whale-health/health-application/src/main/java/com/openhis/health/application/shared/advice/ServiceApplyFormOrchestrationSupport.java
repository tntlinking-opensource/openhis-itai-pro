/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 申请单（{@code AR} 前缀）号段编排支持（门诊/住院医生站共用）。
 *
 * <p>对齐 AS-IP-DOR-012 / IF-IP-DOR-23：领取按日流水 {@code AR} 号；禁止各 Feature 私写第二套号算法。</p>
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
}
