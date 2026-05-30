package com.openhis.health.application.mrms.adapter;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.administration.patient.valueobject.Address;
import com.openhis.health.domain.administration.patient.valueobject.ContactPerson;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.whale.mrms.domain.port.PatientEncounterPort;
import com.openhis.whale.mrms.domain.port.model.EncounterSnapshot;
import com.openhis.whale.mrms.domain.port.model.PatientSnapshot;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * health 对 MRMS {@link PatientEncounterPort} 的实现。
 */
@Component
public class HealthMrmsPatientEncounterAdapter implements PatientEncounterPort {

    private final PatientRepository patientRepository;
    private final EncounterRepository encounterRepository;

    public HealthMrmsPatientEncounterAdapter(PatientRepository patientRepository,
            EncounterRepository encounterRepository) {
        this.patientRepository = patientRepository;
        this.encounterRepository = encounterRepository;
    }

    @Override
    public Optional<PatientSnapshot> findPatient(long patientId) {
        return patientRepository.findById(patientId).map(this::mapPatient);
    }

    @Override
    public Optional<EncounterSnapshot> findEncounter(long encounterId) {
        return encounterRepository.findById(encounterId).map(this::mapEncounter);
    }

    @Override
    public int countInpatientAdmissions(long patientId, long beforeEncounterId) {
        Specification<Encounter> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("patientId"), patientId),
                cb.equal(root.get("classEnum"), EncounterClass.IMP.getCode()),
                cb.equal(root.get("isDeleted"), false),
                cb.lessThan(root.get("id"), beforeEncounterId));
        return (int) encounterRepository.count(spec);
    }

    private PatientSnapshot mapPatient(Patient patient) {
        PatientSnapshot snapshot = new PatientSnapshot();
        snapshot.setPatientId(patient.getId());
        snapshot.setName(patient.getName());
        snapshot.setGenderCode(patient.getGenderEnum());
        snapshot.setBirthDate(patient.getBirthDate());
        snapshot.setIdCard(patient.getIdCard());
        snapshot.setNationalityCode(patient.getNationalityCode());
        snapshot.setMaritalStatusCode(patient.getMaritalStatusEnum());
        snapshot.setOccupationCode(patient.getPrfsEnum());
        snapshot.setPhone(patient.getPhone());
        Address address = patient.getAddress();
        if (address != null) {
            snapshot.setAddress(address.getText());
        }
        ContactPerson contact = patient.getLinkName();
        if (contact != null) {
            snapshot.setContactName(contact.getName());
            snapshot.setContactPhone(contact.getTelcom());
        }
        return snapshot;
    }

    private EncounterSnapshot mapEncounter(Encounter encounter) {
        EncounterSnapshot snapshot = new EncounterSnapshot();
        snapshot.setEncounterId(encounter.getId());
        snapshot.setPatientId(encounter.getPatientId());
        snapshot.setInpatientNo(encounter.getBusNo());
        snapshot.setAdmissionAt(encounter.getStartTime());
        snapshot.setDischargeAt(encounter.getEndTime());
        if (encounter.getOrganizationId() != null) {
            snapshot.setDeptCode(String.valueOf(encounter.getOrganizationId()));
            snapshot.setDeptName(String.valueOf(encounter.getOrganizationId()));
            snapshot.setAdmitDeptCode(snapshot.getDeptCode());
            snapshot.setAdmitDeptName(snapshot.getDeptName());
            snapshot.setDischargeDeptCode(snapshot.getDeptCode());
            snapshot.setDischargeDeptName(snapshot.getDeptName());
        }
        snapshot.setAdmitWayCode(encounter.getInWayCode());
        snapshot.setDischargeWayCode(encounter.getOutWayCode());
        return snapshot;
    }
}
