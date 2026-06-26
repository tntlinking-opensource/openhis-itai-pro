/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.writer;

import com.openhis.whale.mrms.domain.service.CaseRecordBootstrapService;
import com.openhis.whale.mrms.contracts.writer.CaseRecordBootstrapWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link CaseRecordBootstrapWriter} 实现。
 */
@Component
@RequiredArgsConstructor
public class CaseRecordBootstrapWriterImpl implements CaseRecordBootstrapWriter {

    private final CaseRecordBootstrapService caseRecordBootstrapService;

    @Override
    public Long createIfAbsent(Long tenantId, Long patientId, Long encounterId, Long operatorId) {
        return caseRecordBootstrapService.createIfAbsent(tenantId, patientId, encounterId, operatorId);
    }
}
