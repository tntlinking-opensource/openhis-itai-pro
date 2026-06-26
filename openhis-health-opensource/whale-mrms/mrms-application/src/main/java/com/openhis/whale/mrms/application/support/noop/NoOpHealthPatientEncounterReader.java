/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.support.noop;

import com.openhis.whale.mrms.contracts.reader.HealthPatientEncounterReader;
import com.openhis.whale.mrms.contracts.snapshot.EncounterSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.PatientSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 无 HIS 适配器时的 {@link HealthPatientEncounterReader} 空实现。
 */
@Component
@ConditionalOnMissingBean(HealthPatientEncounterReader.class)
public class NoOpHealthPatientEncounterReader implements HealthPatientEncounterReader {

    /**
     * {@inheritDoc}
     */
    @Override
    public PatientSnapshot loadPatient(Long patientId) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EncounterSnapshot loadEncounter(Long encounterId) {
        return null;
    }
}
