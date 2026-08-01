/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.bridge.metadata.context;

import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.enums.MaritalStatus;
import com.openhis.health.domain.share.enums.OccupationType;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.emr.domain.loader.EmrAdmissionContextLoader;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link EmrAdmissionContextLoader} 实现：将患者主数据、就诊主数据及诊断摘要写入 EMR 元数据扁平上下文。
 *
 * <p>键名与《EMR 元数据字段定义与维护》§3.3 {@code source_path} 对齐（如 {@code patient.name}、
 * {@code diagnosis.primaryDisplay}）。诊断类键由 {@link EmrDiagnosisMetadataContributor} 贡献。</p>
 */
@Component
@Primary
public class EmrAdmissionContextLoaderImpl implements EmrAdmissionContextLoader {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final EmrDiagnosisMetadataContributor diagnosisMetadataContributor;

    public EmrAdmissionContextLoaderImpl(
            PatientQueryService patientQueryService,
            EncounterQueryService encounterQueryService,
            PractitionerQueryService practitionerQueryService,
            OrganizationQueryService organizationQueryService,
            AccountQueryService accountQueryService,
            ContractQueryService contractQueryService,
            EmrDiagnosisMetadataContributor diagnosisMetadataContributor) {
        this.patientQueryService = patientQueryService;
        this.encounterQueryService = encounterQueryService;
        this.practitionerQueryService = practitionerQueryService;
        this.organizationQueryService = organizationQueryService;
        this.accountQueryService = accountQueryService;
        this.contractQueryService = contractQueryService;
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

    @Override
    public void contributeOrganization(Map<String, String> target, Long organizationId) {
        if (organizationId == null) {
            return;
        }
        // 就诊已写入时不覆盖
        if (StringUtils.hasText(target.get("org.name"))) {
            return;
        }
        put(target, "org.name", resolveHospitalDisplayName(organizationId));
    }

    private void mapPatient(Map<String, String> target, Patient patient) {
        put(target, "patient.id", String.valueOf(patient.getId()));
        put(target, "patient.name", patient.getName());
        put(target, "patient.gender", toGenderDisplay(patient.getGenderEnum()));
        put(target, "patient.medicalRecordNo", patient.getBusNo());
        put(target, "patient.idCard", patient.getIdCard());
        put(target, "patient.phone", patient.getPhone());
        put(target, "patient.workCompany", patient.getWorkCompany());
        put(target, "patient.nativePlace", patient.getNativePlace());
        put(target, "patient.nationality", patient.getNationalityCode());
        put(target, "patient.occupation", toOccupationDisplay(patient.getPrfsEnum()));
        put(target, "patient.maritalStatus", toMaritalStatusDisplay(patient.getMaritalStatusEnum()));
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
            String hospitalName = resolveHospitalDisplayName(encounter.getOrganizationId());
            put(target, "org.name", hospitalName);
        }
        if (encounter.getStartTime() != null) {
            String startText = encounter.getStartTime().format(DATETIME_FMT);
            put(target, "encounter.startTime", startText);
            put(target, "encounter.admissionTime", startText);
            put(target, "encounter.admissionDateTime", startText);
            // 门诊病历「就诊日期」等只展示日，不带时分秒
            put(target, "encounter.startDate", encounter.getStartTime().format(DATE_FMT));
        }
        if (encounter.getClassEnum() != null) {
            put(target, "encounter.classEnum", encounter.getClassEnum());
        }
        // 医保/费别：优先就诊 ybClassText；空则与医生站卡片同口径取优先合同名
        String payorName = encounter.getYbClassText();
        if (!StringUtils.hasText(payorName)) {
            payorName = resolvePreferredContractDisplayName(encounter.getId());
        }
        put(target, "insurance.payorName", payorName);
        LocalDateTime bedAssignedAt = encounterQueryService.findFirstBedAssignedAt(encounter.getId());
        LocalDateTime bedClearedAt = encounterQueryService.findBedClearedAt(encounter.getId());
        // 无床位轨迹时回退就诊起止时间，避免入院/出院日期空白
        LocalDateTime admissionAt = bedAssignedAt != null ? bedAssignedAt : encounter.getStartTime();
        LocalDateTime dischargeAt = bedClearedAt != null ? bedClearedAt : encounter.getEndTime();
        if (admissionAt != null) {
            put(target, "encounter.bedAssignedAt", admissionAt.format(DATE_FMT));
            // 入院记录「入院时间」优先床位分配时刻，格式 yyyy-MM-dd HH:mm
            put(target, "encounter.admissionDateTime", admissionAt.format(DATETIME_FMT));
            put(target, "encounter.admissionTime", admissionAt.format(DATETIME_FMT));
        }
        if (dischargeAt != null) {
            put(target, "encounter.bedClearedAt", dischargeAt.format(DATE_FMT));
        }
        if (admissionAt != null && dischargeAt != null) {
            long days = ChronoUnit.DAYS.between(admissionAt.toLocalDate(), dischargeAt.toLocalDate());
            put(target, "encounter.lengthOfStayDays", String.valueOf(days <= 0 ? 1 : days));
        } else if (admissionAt != null && dischargeAt == null) {
            // 仍在院：按入院日算至今天数，出院日留空由医生填写
            long days = ChronoUnit.DAYS.between(admissionAt.toLocalDate(), LocalDate.now());
            put(target, "encounter.lengthOfStayDays", String.valueOf(days <= 0 ? 1 : days));
        }
    }

    /**
     * 解析就诊费用性质展示名（有医保账户优先其合同名，否则登记个人现金户合同名）。
     *
     * @param encounterId 就诊主键
     * @return 合同名称；无则 null
     */
    private String resolvePreferredContractDisplayName(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        Map<Long, String> contractNoByEncounterId =
                accountQueryService.mapPreferredContractNoByEncounterIds(List.of(encounterId));
        String contractNo = contractNoByEncounterId.get(encounterId);
        if (!StringUtils.hasText(contractNo)) {
            return null;
        }
        Map<String, String> nameByBusNo =
                contractQueryService.mapContractNameByBusNoIn(Set.of(contractNo.trim()));
        if (nameByBusNo == null || nameByBusNo.isEmpty()) {
            return null;
        }
        String name = nameByBusNo.get(contractNo.trim());
        return StringUtils.hasText(name) ? name.trim() : null;
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

    /**
     * 自科室向上回溯至医院节点（{@link OrganizationType#HOSPITAL}）；找不到则退回当前节点名称。
     */
    private String resolveHospitalDisplayName(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        Long cursor = organizationId;
        String fallbackName = null;
        for (int depth = 0; depth < 16 && cursor != null; depth++) {
            Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(cursor);
            if (organization == null) {
                break;
            }
            if (StringUtils.hasText(organization.getName())) {
                fallbackName = organization.getName().trim();
            }
            if (OrganizationType.isHospital(organization.getTypeEnum())) {
                return fallbackName;
            }
            Long parentId = organization.getParentId();
            if (parentId == null || parentId.equals(cursor)) {
                break;
            }
            cursor = parentId;
        }
        return fallbackName;
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

    /** 性别码 → 中文展示（病历打印/页眉） */
    private static String toGenderDisplay(String genderEnum) {
        if (!StringUtils.hasText(genderEnum)) {
            return null;
        }
        String raw = genderEnum.trim();
        String key = raw.toLowerCase();
        return switch (key) {
            case "male", "m", "1", "man" -> "男";
            case "female", "f", "2", "woman" -> "女";
            case "other", "o", "9", "unknown", "unk" -> "未知";
            default -> {
                if ("男".equals(raw) || "女".equals(raw) || "未知".equals(raw)
                        || "男性".equals(raw) || "女性".equals(raw)) {
                    yield raw.length() >= 2 && raw.endsWith("性") ? raw.substring(0, 1) : raw;
                }
                yield raw;
            }
        };
    }

    private static String toOccupationDisplay(String prfsEnum) {
        if (!StringUtils.hasText(prfsEnum)) {
            return null;
        }
        String raw = prfsEnum.trim();
        OccupationType type = EnumUtil.fromCode(OccupationType.class, raw);
        return type != null ? type.getDisplay() : raw;
    }

    private static String toMaritalStatusDisplay(String maritalStatusEnum) {
        if (!StringUtils.hasText(maritalStatusEnum)) {
            return null;
        }
        String raw = maritalStatusEnum.trim();
        MaritalStatus status = EnumUtil.fromCode(MaritalStatus.class, raw);
        return status != null ? status.getDisplay() : raw;
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }
}
