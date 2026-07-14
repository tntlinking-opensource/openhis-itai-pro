/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.extension;

import com.openhis.whale.crb.bridge.push.workflow.CrbPushOrchestrator;
import com.openhis.whale.crb.domain.push.command.EmrPushEnqueueCommand;
import com.openhis.whale.crb.domain.enums.EmrPushSlaTier;
import com.openhis.whale.crb.domain.enums.PushOperation;
import com.openhis.whale.crb.bridge.push.support.CrbIdFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * {@link CrbEmrPushExtension} 实现：委托 {@link CrbPushOrchestrator} 异步入队。
 */
@Component
@RequiredArgsConstructor
public class CrbEmrPushExtensionImpl implements CrbEmrPushExtension {

    private static final String RESOURCE_PATIENT = "emrPatientInfo";

    private final CrbPushOrchestrator orchestrator;

    @Override
    public void enqueuePatientInfo(Long patientId, Long tenantId, String operatorId) {
        if (patientId == null) {
            return;
        }
        enqueue(new EmrPushEnqueueCommand()
                .setTenantId(tenantId)
                .setResource(RESOURCE_PATIENT)
                .setBusinessKey(CrbIdFormatter.formatPatientId(patientId))
                .setSlaTier(EmrPushSlaTier.REALTIME)
                .setOperation(PushOperation.UPSERT)
                .setOperationTime(Instant.now())
                .setOperatorId(operatorId)
                .setSourceId(patientId));
    }

    @Override
    public void enqueue(EmrPushEnqueueCommand command) {
        if (command == null) {
            return;
        }
        orchestrator.enqueueAndSchedule(command);
    }
}
