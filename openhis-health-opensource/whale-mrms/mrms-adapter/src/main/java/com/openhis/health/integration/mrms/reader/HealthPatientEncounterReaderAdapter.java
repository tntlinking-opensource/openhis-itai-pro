/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.reader;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterParticipantQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.whale.mrms.contracts.reader.HealthPatientEncounterReader;
import com.openhis.whale.mrms.contracts.snapshot.EncounterSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.PatientSnapshot;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link HealthPatientEncounterReader} 实现：读 HIS 患者与就诊。
 */
@Component
@RequiredArgsConstructor
public class HealthPatientEncounterReaderAdapter implements HealthPatientEncounterReader {

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;

    @Override
    public PatientSnapshot loadPatient(Long patientId) {
        if (patientId == null) {
            return null;
        }
        Patient patient = patientQueryService.findOptionalPatientEntityById(patientId).orElse(null);
        if (patient == null) {
            return null;
        }
        PatientSnapshot snap = new PatientSnapshot();
        snap.setPatientId(patient.getId());
        snap.setName(patient.getName());
        snap.setGenderCode(patient.getGenderEnum());
        snap.setBirthDate(patient.getBirthDate());
        snap.setCertno(patient.getIdCard());
        snap.setPhone(patient.getPhone());
        return snap;
    }

    @Override
    public EncounterSnapshot loadEncounter(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(encounterId).orElse(null);
        if (encounter == null) {
            return null;
        }
        EncounterSnapshot snap = new EncounterSnapshot();
        snap.setEncounterId(encounter.getId());
        snap.setPatientId(encounter.getPatientId());
        snap.setIptNo(encounter.getBusNo());
        snap.setAdmTime(encounter.getStartTime());
        snap.setDscgTime(encounter.getEndTime());
        applyDepartments(snap, encounter);
        applyParticipants(snap, encounterQueryService.listActiveEncounterParticipants(encounterId));
        return snap;
    }

    private void applyDepartments(EncounterSnapshot snap, Encounter encounter) {
        if (encounter.getOrganizationId() != null) {
            applyOrganization(snap, organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId()), true);
        }
        encounterQueryService.findLastCompletedWardLocationIdByEncounterId(encounter.getId())
                .flatMap(locationQueryService::findOptionalById)
                .ifPresent(ward -> applyOrganization(
                        snap,
                        organizationQueryService.loadOrganizationByIdForDisplay(ward.getOrganizationId()),
                        false));
    }

    private void applyOrganization(EncounterSnapshot snap, Organization organization, boolean admission) {
        if (organization == null) {
            return;
        }
        String code = StringUtils.trimToNull(organization.getBusNo());
        String name = StringUtils.trimToNull(organization.getName());
        if (admission) {
            if (code != null) {
                snap.setAdmCatyCode(code);
            }
            if (name != null) {
                snap.setAdmCatyName(name);
            }
            return;
        }
        if (code != null) {
            snap.setDscgCatyCode(code);
        }
        if (name != null) {
            snap.setDscgCatyName(name);
        }
    }

    private void applyParticipants(EncounterSnapshot snap, List<EncounterParticipantQueryModel> participants) {
        if (participants == null || participants.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = new HashSet<>();
        for (EncounterParticipantQueryModel participant : participants) {
            if (participant.getPractitionerId() != null) {
                practitionerIds.add(participant.getPractitionerId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        if (!practitionerIds.isEmpty()) {
            for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
                if (practitioner.getId() != null && StringUtils.isNotBlank(practitioner.getName())) {
                    nameById.put(practitioner.getId(), practitioner.getName());
                }
            }
        }
        for (EncounterParticipantQueryModel participant : participants) {
            if (participant.getPractitionerId() == null) {
                continue;
            }
            String roleCode = participant.getRoleCode();
            Long practitionerId = participant.getPractitionerId();
            String name = nameById.get(practitionerId);
            if (EncounterParticipantRole.ATTENDING_DOCTOR.getCode().equals(roleCode)) {
                snap.setAtddrPractitionerId(practitionerId);
                snap.setAtddrName(name);
            } else if (EncounterParticipantRole.CHIEF_DOCTOR.getCode().equals(roleCode)) {
                snap.setChfdrName(name);
            } else if (EncounterParticipantRole.ADMITTER.getCode().equals(roleCode)) {
                snap.setIptDrName(name);
            } else if (EncounterParticipantRole.PRIMARY_NURSE.getCode().equals(roleCode)) {
                snap.setRespNursName(name);
            }
        }
    }
}
