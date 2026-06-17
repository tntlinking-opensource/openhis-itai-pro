package com.openhis.whale.mrms.application.support.noop;

import com.openhis.whale.mrms.port.PatientEncounterPort;
import com.openhis.whale.mrms.port.model.EncounterSnapshot;
import com.openhis.whale.mrms.port.model.PatientSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnMissingBean(PatientEncounterPort.class)
public class NoOpPatientEncounterPort implements PatientEncounterPort {

    @Override
    public Optional<PatientSnapshot> findPatient(long patientId) {
        return Optional.empty();
    }

    @Override
    public Optional<EncounterSnapshot> findEncounter(long encounterId) {
        return Optional.empty();
    }

    @Override
    public int countInpatientAdmissions(long patientId, long beforeEncounterId) {
        return 0;
    }
}
