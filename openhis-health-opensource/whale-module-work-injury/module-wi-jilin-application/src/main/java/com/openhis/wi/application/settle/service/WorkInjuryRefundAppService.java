/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.settle.service;

import com.openhis.wi.application.settle.request.ListWorkInjurySettlementsByPaymentRequest;
import com.openhis.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import com.openhis.wi.domain.settle.access.ListWorkInjurySettlementsByPaymentAccess;
import com.openhis.wi.domain.settle.access.StartWorkInjuryReverseInvoker;
import com.openhis.wi.domain.settle.command.StartWorkInjuryReverseCommand;
import com.openhis.wi.domain.settle.result.WorkInjurySettlementRecordSummary;
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
