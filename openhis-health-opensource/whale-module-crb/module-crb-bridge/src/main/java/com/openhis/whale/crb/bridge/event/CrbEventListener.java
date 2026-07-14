/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.event;

import com.openhis.health.domain.share.events.administration.patient.PatientProfileModifiedEvent;
import com.openhis.health.domain.share.events.administration.patient.PatientRegisteredEvent;
import com.openhis.whale.crb.bridge.extension.CrbEmrPushExtension;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * health 领域事件订阅入口，委托 {@link CrbEmrPushExtension} 异步入队。
 */
@Component
@RequiredArgsConstructor
public class CrbEventListener {

    private final CrbEmrPushExtension crbEmrPushExtension;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPatientRegistered(PatientRegisteredEvent event) {
        if (event == null || event.getAggregateId() == null) {
            return;
        }
        crbEmrPushExtension.enqueuePatientInfo(
                event.getAggregateId(),
                event.getTenantId(),
                event.getUserId() == null ? null : String.valueOf(event.getUserId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPatientProfileModified(PatientProfileModifiedEvent event) {
        if (event == null || event.getAggregateId() == null) {
            return;
        }
        crbEmrPushExtension.enqueuePatientInfo(
                event.getAggregateId(),
                event.getTenantId(),
                event.getOperatorId() == null ? null : String.valueOf(event.getOperatorId()));
    }
}
