/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.support.noop;

import com.openhis.whale.mrms.contracts.reader.HealthDiagnosisProcedureReader;
import com.openhis.whale.mrms.contracts.snapshot.DiagnosisLineSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.ProcedureLineSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 无 HIS 适配器时的 {@link HealthDiagnosisProcedureReader} 空实现。
 */
@Component
@ConditionalOnMissingBean(HealthDiagnosisProcedureReader.class)
public class NoOpHealthDiagnosisProcedureReader implements HealthDiagnosisProcedureReader {

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DiagnosisLineSnapshot> listDischargeDiagnoses(Long encounterId) {
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DiagnosisLineSnapshot> listAdmissionDiagnoses(Long encounterId) {
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ProcedureLineSnapshot> listProcedures(Long encounterId) {
        return Collections.emptyList();
    }
}
