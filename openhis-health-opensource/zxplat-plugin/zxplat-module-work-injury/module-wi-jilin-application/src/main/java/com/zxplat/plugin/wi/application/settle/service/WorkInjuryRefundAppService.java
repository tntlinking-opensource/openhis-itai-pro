/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.application.settle.service;

import com.zxplat.plugin.wi.application.settle.request.ListWorkInjurySettlementsByPaymentRequest;
import com.zxplat.plugin.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import com.zxplat.plugin.wi.domain.settle.access.ListWorkInjurySettlementsByPaymentAccess;
import com.zxplat.plugin.wi.domain.settle.access.StartWorkInjuryReverseInvoker;
import com.zxplat.plugin.wi.domain.settle.command.StartWorkInjuryReverseCommand;
import com.zxplat.plugin.wi.domain.settle.result.WorkInjurySettlementRecordSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/** 工伤门诊退费（POST /api/app/workInjuryRefund/*）。 */
@Service
@RequiredArgsConstructor
public class WorkInjuryRefundAppService {

    private final ListWorkInjurySettlementsByPaymentAccess listWorkInjurySettlementsByPaymentAccess;
    private final StartWorkInjuryReverseInvoker startWorkInjuryReverseInvoker;

    public List<WorkInjurySettlementRecordSummary> listSettlementsByPayment(
            ListWorkInjurySettlementsByPaymentRequest request) {
        if (request == null || request.getPaymentId() == null) {
            return Collections.emptyList();
        }
        return listWorkInjurySettlementsByPaymentAccess.listByPayment(request.getPaymentId());
    }

    public PrepareWorkInjuryInvokeResponse startReverse(StartWorkInjuryReverseCommand command) {
        return startWorkInjuryReverseInvoker.invoke(command);
    }
}
