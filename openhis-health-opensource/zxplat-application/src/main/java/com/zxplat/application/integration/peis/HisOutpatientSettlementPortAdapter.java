/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.integration.peis;

import java.util.List;

import org.springframework.stereotype.Component;

import com.zxplat.application.outpatient.financial.charge.support.OutpatientPaymentDraftLifecycleOrchestrator;
import com.zxplat.application.outpatient.financial.refund.request.ExecuteOutpatientBillRefundByPaymentRequest;
import com.zxplat.application.outpatient.financial.refund.response.ExecuteOutpatientBillRefundByPaymentResponse;
import com.zxplat.application.outpatient.financial.refund.service.OutpatientRefundAppService;
import com.zxplat.domain.infrastructure.integration.payment.dto.ClientRefundResultDto;
import com.zxplat.plugin.peis.domain.port.HisOutpatientSettlementPort;

import lombok.RequiredArgsConstructor;

/**
 * HIS 适配：门诊支付草稿取消 + 按原支付退费 → {@link HisOutpatientSettlementPort}。
 */
@Component
@RequiredArgsConstructor
public class HisOutpatientSettlementPortAdapter implements HisOutpatientSettlementPort {

    private final OutpatientPaymentDraftLifecycleOrchestrator outpatientPaymentDraftLifecycleOrchestrator;
    private final OutpatientRefundAppService outpatientRefundAppService;

    @Override
    public void cancelAllPaymentDraftsForEncounter(Long encounterId, Long retainDraftPaymentId) {
        outpatientPaymentDraftLifecycleOrchestrator.cancelAllDraftsForEncounter(encounterId, retainDraftPaymentId);
    }

    @Override
    public Long executeBillRefundByPayment(
            Long encounterId,
            Long originalPaymentId,
            List<Long> refundingChargeItemIds,
            String billDate,
            ClientRefundResultDto clientRefundResult) {
        ExecuteOutpatientBillRefundByPaymentRequest executeRequest = new ExecuteOutpatientBillRefundByPaymentRequest();
        executeRequest.setEncounterId(encounterId);
        executeRequest.setOriginalPaymentId(originalPaymentId);
        executeRequest.setRefundingChargeItemIds(refundingChargeItemIds);
        executeRequest.setBillDate(billDate);
        executeRequest.setClientRefundResult(clientRefundResult);
        ExecuteOutpatientBillRefundByPaymentResponse executed =
                outpatientRefundAppService.executeOutpatientBillRefundByPayment(executeRequest);
        if (executed == null) {
            return null;
        }
        return executed.getReversePaymentId();
    }
}
