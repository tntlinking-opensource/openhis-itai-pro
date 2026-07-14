/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.bridge.metadata.context;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.whale.emr.domain.loader.EmrAdmissionContextLoader;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.Optional;

/**
 * {@link EmrAdmissionContextLoader} 实现：将患者主数据、就诊主数据及诊断摘要写入 EMR 元数据扁平上下文。
 *
 * <p>键名与《EMR 元数据字段定义与维护》§3.3 {@code source_path} 对齐（如 {@code patient.name}、
 * {@code diagnosis.primaryDisplay}）。诊断类键由 {@link EmrDiagnosisMetadataContributor} 贡献。</p>
 */
@Component
@Primary
public class EmrAdmissionContextLoaderImpl implements EmrAdmissionContextLoader {

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final EmrDiagnosisMetadataContributor diagnosisMetadataContributor;

    public EmrAdmissionContextLoaderImpl(
            PatientQueryService patientQueryService,
            EncounterQueryService encounterQueryService,
            PractitionerQueryService practitionerQueryService,
            OrganizationQueryService organizationQueryService,
            EmrDiagnosisMetadataContributor diagnosisMetadataContributor) {
        this.patientQueryService = patientQueryService;
        this.encounterQueryService = encounterQueryService;
        this.practitionerQueryService = practitionerQueryService;
        this.organizationQueryService = organizationQueryService;
        this.diagnosisMetadataContributor = diagnosisMetadataContributor;
    }

    @Override
    public void contributePatient(Map<String, String> target, String patientId) {
        Optional<Long> patientPk = parseLongId(patientId);
        if (patientPk.isEmpty()) {
            return;
        }
        patientQueryService.findOptionalPatientEntityById(patientPk.get()).ifPresent(p -> mapPatient(target, p));
    }

    @Override
    public void contributeEncounter(Map<String, String> target, String encounterId) {
        Optional<Long> encounterPk = parseLongId(encounterId);
        if (encounterPk.isEmpty()) {
            return;
        }
        Optional<Encounter> encounterOpt = encounterQueryService.findOptionalEncounterEntityById(encounterPk.get());
        if (encounterOpt.isEmpty()) {
            return;
        }
        Encounter encounter = encounterOpt.get();
        mapEncounter(target, encounter);
        diagnosisMetadataContributor.contribute(target, encounter);
    }

    @Override
    public void contributePractitioner(Map<String, String> target, Long practitionerId) {
        if (practitionerId == null) {
            return;
        }
        Practitioner practitioner = practitionerQueryService.findById(practitionerId);
        if (practitioner != null) {
            mapPractitioner(target, practitioner);
        }
    }

    private void mapPatient(Map<String, String> target, Patient patient) {
        put(target, "patient.id", String.valueOf(patient.getId()));
        put(target, "patient.name", patient.getName());
        put(target, "patient.gender", patient.getGenderEnum());
        put(target, "patient.medicalRecordNo", patient.getBusNo());
        put(target, "patient.idCard", patient.getIdCard());
        put(target, "patient.phone", patient.getPhone());
        if (patient.getBirthDate() != null) {
            put(target, "patient.birthDate", patient.getBirthDate().toString());
            int years = Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
            if (years >= 0) {
                put(target, "patient.age", String.valueOf(years));
            }
        }
    }

    private void mapPractitioner(Map<String, String> target, Practitioner practitioner) {
        put(target, "practitioner.id", String.valueOf(practitioner.getId()));
        put(target, "practitioner.name", practitioner.getName());
        put(target, "practitioner.busNo", practitioner.getBusNo());
        String signatureImage = normalizeSignatureDataUrl(practitioner.getSignature());
        if (signatureImage != null) {
            put(target, "practitioner.signatureImage", signatureImage);
            put(target, "user.signatureImage", signatureImage);
        }
    }

    private static String normalizeSignatureDataUrl(String stored) {
        if (!StringUtils.hasText(stored)) {
            return null;
        }
        String trimmed = stored.trim();
        if (trimmed.startsWith("data:")) {
            return trimmed;
        }
        return "data:image/png;base64," + trimmed;
    }

    private void mapEncounter(Map<String, String> target, Encounter encounter) {
        put(target, "encounter.id", String.valueOf(encounter.getId()));
        put(target, "encounter.busNo", encounter.getBusNo());
        if (encounter.getOrganizationId() != null) {
            String orgIdText = String.valueOf(encounter.getOrganizationId());
            put(target, "encounter.organizationId", orgIdText);
            String deptName = resolveOrganizationDisplayName(encounter.getOrganizationId());
            put(target, "encounter.deptName", deptName);
            put(target, "dept.name", deptName);
        }
        if (encounter.getStartTime() != null) {
            String startText = encounter.getStartTime().toString();
            put(target, "encounter.startTime", startText);
            put(target, "encounter.admissionTime", startText);
        }
        if (encounter.getClassEnum() != null) {
            put(target, "encounter.classEnum", encounter.getClassEnum());
        }
        if (encounter.getYbClassText() != null) {
            put(target, "insurance.payorName", encounter.getYbClassText());
        }
    }

    private String resolveOrganizationDisplayName(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(organizationId);
        if (organization != null && StringUtils.hasText(organization.getName())) {
            return organization.getName().trim();
        }
        return String.valueOf(organizationId);
    }

    private static Optional<Long> parseLongId(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(id.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }
}
