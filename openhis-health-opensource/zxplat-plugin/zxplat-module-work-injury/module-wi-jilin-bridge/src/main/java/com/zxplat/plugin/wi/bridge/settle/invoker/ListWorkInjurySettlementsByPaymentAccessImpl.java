/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.settle.invoker;

import com.zxplat.plugin.wi.bridge.settle.workflow.WorkInjuryOutpatientSettleOrchestrator;
import com.zxplat.plugin.wi.domain.settle.access.ListWorkInjurySettlementsByPaymentAccess;
import com.zxplat.plugin.wi.domain.settle.result.WorkInjurySettlementRecordSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/** 按 payment 查询工伤正式结算回执端口实现。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class ListWorkInjurySettlementsByPaymentAccessImpl implements ListWorkInjurySettlementsByPaymentAccess {

    private final WorkInjuryOutpatientSettleOrchestrator workInjuryOutpatientSettleOrchestrator;

    @Override
    public List<WorkInjurySettlementRecordSummary> listByPayment(Long paymentId) {
        if (paymentId == null) {
            return Collections.emptyList();
        }
        return workInjuryOutpatientSettleOrchestrator.listSettlementsByPayment(paymentId);
    }
}
