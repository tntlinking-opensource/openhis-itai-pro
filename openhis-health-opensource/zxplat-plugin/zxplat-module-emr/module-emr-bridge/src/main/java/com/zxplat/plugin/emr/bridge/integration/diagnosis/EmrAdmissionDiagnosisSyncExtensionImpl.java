/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.bridge.integration.diagnosis;

import com.zxplat.domain.infrastructure.integration.emr.EmrAdmissionDiagnosisSyncExtension;
import com.zxplat.plugin.emr.bridge.diagnosis.workflow.EmrDiagnosisSyncOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 住院诊断变更 Extension：委托 {@link EmrDiagnosisSyncOrchestrator}。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.emr.enabled", havingValue = "true", matchIfMissing = true)
public class EmrAdmissionDiagnosisSyncExtensionImpl implements EmrAdmissionDiagnosisSyncExtension {

    private final EmrDiagnosisSyncOrchestrator diagnosisSyncOrchestrator;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void syncAfterInpatientDiagnosisChanged(Long encounterId, Long patientId) {
        diagnosisSyncOrchestrator.syncAfterInpatientDiagnosisChanged(encounterId, patientId);
    }
}
