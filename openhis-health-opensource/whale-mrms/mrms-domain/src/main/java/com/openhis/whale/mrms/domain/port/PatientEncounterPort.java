package com.openhis.whale.mrms.domain.port;

import com.openhis.whale.mrms.domain.port.model.EncounterSnapshot;
import com.openhis.whale.mrms.domain.port.model.PatientSnapshot;

import java.util.Optional;

public interface PatientEncounterPort {

    Optional<PatientSnapshot> findPatient(long patientId);

    Optional<EncounterSnapshot> findEncounter(long encounterId);

    int countInpatientAdmissions(long patientId, long beforeEncounterId);
}
