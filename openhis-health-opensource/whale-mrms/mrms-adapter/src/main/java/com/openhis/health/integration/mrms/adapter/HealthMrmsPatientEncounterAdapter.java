/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.adapter;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.patient.valueobject.Address;
import com.openhis.health.domain.administration.patient.valueobject.ContactPerson;
import com.openhis.health.bridge.shared.organization.OrganizationDisplayResolver;
import com.openhis.whale.mrms.port.PatientEncounterPort;
import com.openhis.whale.mrms.port.model.EncounterSnapshot;
import com.openhis.whale.mrms.port.model.PatientSnapshot;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * health 对 MRMS {@link PatientEncounterPort} 的实现：患者/就诊快照与住院次数统计。
 */
@Component
@Primary
public class HealthMrmsPatientEncounterAdapter implements PatientEncounterPort {

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;
    private final OrganizationDisplayResolver organizationDisplayResolver;

    public HealthMrmsPatientEncounterAdapter(
            PatientQueryService patientQueryService,
            EncounterQueryService encounterQueryService,
            OrganizationDisplayResolver organizationDisplayResolver) {
        this.patientQueryService = patientQueryService;
        this.encounterQueryService = encounterQueryService;
        this.organizationDisplayResolver = organizationDisplayResolver;
    }

    @Override
    public Optional<PatientSnapshot> findPatient(long patientId) {
        return patientQueryService.findOptionalPatientEntityById(patientId).map(this::mapPatient);
    }

    @Override
    public Optional<EncounterSnapshot> findEncounter(long encounterId) {
        return encounterQueryService.findOptionalEncounterEntityById(encounterId).map(this::mapEncounter);
    }

    @Override
    public int countInpatientAdmissions(long patientId, long beforeEncounterId) {
        return encounterQueryService.countInpatientAdmissionsBefore(patientId, beforeEncounterId);
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
            applyOrganizationFields(snapshot, encounter.getOrganizationId());
        }
        snapshot.setAdmitWayCode(encounter.getInWayCode());
        snapshot.setDischargeWayCode(encounter.getOutWayCode());
        return snapshot;
    }

    private void applyOrganizationFields(EncounterSnapshot snapshot, Long organizationId) {
        String deptCode = organizationDisplayResolver.resolveBusNoOrId(organizationId);
        String deptName = organizationDisplayResolver.resolveDisplayName(organizationId);
        snapshot.setDeptCode(deptCode);
        snapshot.setDeptName(deptName);
        snapshot.setAdmitDeptCode(deptCode);
        snapshot.setAdmitDeptName(deptName);
        snapshot.setDischargeDeptCode(deptCode);
        snapshot.setDischargeDeptName(deptName);
    }
}
