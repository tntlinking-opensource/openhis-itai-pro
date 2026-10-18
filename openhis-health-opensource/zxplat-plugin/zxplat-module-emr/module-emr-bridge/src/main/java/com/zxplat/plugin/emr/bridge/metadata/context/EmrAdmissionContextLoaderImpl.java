/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.bridge.metadata.context;

import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterContextQueryService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.patient.entity.Patient;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.financial.contract.service.ContractQueryService;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.share.enums.MaritalStatus;
import com.zxplat.domain.share.enums.OccupationType;
import com.zxplat.domain.share.enums.OrganizationType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.InpatientStayDaysCalculator;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.plugin.emr.domain.loader.EmrAdmissionContextLoader;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
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
    private final EncounterContextQueryService encounterContextQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final EmrDiagnosisMetadataContributor diagnosisMetadataContributor;

    public EmrAdmissionContextLoaderImpl(
            PatientQueryService patientQueryService,
            EncounterQueryService encounterQueryService,
            EncounterContextQueryService encounterContextQueryService,
            LocationQueryService locationQueryService,
            PractitionerQueryService practitionerQueryService,
            OrganizationQueryService organizationQueryService,
            AccountQueryService accountQueryService,
            ContractQueryService contractQueryService,
            ServiceRequestQueryService serviceRequestQueryService,
            EmrDiagnosisMetadataContributor diagnosisMetadataContributor) {
        this.patientQueryService = patientQueryService;
        this.encounterQueryService = encounterQueryService;
        this.encounterContextQueryService = encounterContextQueryService;
        this.locationQueryService = locationQueryService;
        this.practitionerQueryService = practitionerQueryService;
        this.organizationQueryService = organizationQueryService;
        this.accountQueryService = accountQueryService;
        this.contractQueryService = contractQueryService;
        this.serviceRequestQueryService = serviceRequestQueryService;
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
        // 入院：优先分床时刻，无床位轨迹回退就诊 start_time
        LocalDateTime admissionAt = encounter.getStartTime();
        if (bedAssignedAt != null) {
            admissionAt = bedAssignedAt;
        }
        // 业务出院时间：出院医嘱计划时间优先；回落 Encounter.endTime；禁止用清床时刻冒充
        LocalDateTime dischargeAt = encounter.getEndTime();
        Map<Long, LocalDateTime> plannedByEncounter =
            serviceRequestQueryService.mapDischargePlannedTimeByEncounterIds(Set.of(encounter.getId()));
        LocalDateTime plannedDischarge = plannedByEncounter.get(encounter.getId());
        if (plannedDischarge != null) {
            dischargeAt = plannedDischarge;
        }
        if (admissionAt != null) {
            put(target, "encounter.bedAssignedAt", admissionAt.format(DATE_FMT));
            // 入院记录「入院时间」优先床位分配时刻，格式 yyyy-MM-dd HH:mm
            put(target, "encounter.admissionDateTime", admissionAt.format(DATETIME_FMT));
            put(target, "encounter.admissionTime", admissionAt.format(DATETIME_FMT));
        }
        // 清床日仅作元数据；与业务出院时间分离
        if (bedClearedAt != null) {
            put(target, "encounter.bedClearedAt", bedClearedAt.format(DATE_FMT));
        }
        if (dischargeAt != null) {
            // 病历模板 dischargeDate 须绑本字段（勿绑 bedClearedAt）
            put(target, "encounter.dischargeDate", dischargeAt.format(DATE_FMT));
            put(target, "encounter.dischargeDateTime", dischargeAt.format(DATETIME_FMT));
        }
        if (admissionAt != null && dischargeAt != null) {
            // 即进不计出：11 入院 13 出院 → 2 天
            Integer days = InpatientStayDaysCalculator.calcDischargedStayDays(admissionAt, dischargeAt);
            if (days != null) {
                put(target, "encounter.lengthOfStayDays", String.valueOf(days));
            }
        } else if (admissionAt != null && dischargeAt == null) {
            // 仍在院：含当日（今日仍算住院日）
            Integer days = InpatientStayDaysCalculator.calcDisplayStayDays(admissionAt, null);
            if (days != null) {
                put(target, "encounter.lengthOfStayDays", String.valueOf(days));
            }
        }
        // 住院床号/病房：活动床优先，无活动床用末次已完成床；供病历页眉 bedNo/wardName 带出
        this.contributeInpatientBedAndHouse(target, encounter.getId());
    }

    /**
     * 写入住院床号与病房名（{@code encounter.bedNo} / {@code encounter.houseName}）。
     *
     * <p>床位名取床位 Location 名称，空则回落 busNo；去掉护士站「原房-床（未占床）」包装。
     * 病房取床位父级 HOUSE。页眉 fieldKey {@code bedNo} 展示「病房-床位」（如 {@code 506-加}）；
     * {@code wardName}/{@code houseName} 仍为纯病房名。</p>
     *
     * @param target      元数据扁平上下文
     * @param encounterId 就诊主键
     */
    private void contributeInpatientBedAndHouse(Map<String, String> target, Long encounterId) {
        if (encounterId == null) {
            return;
        }
        Optional<InpatientEncounterContextQueryModel> ctxOpt =
                encounterContextQueryService.getInpatientContextByEncounterId(encounterId);
        if (ctxOpt.isEmpty()) {
            return;
        }
        InpatientEncounterContextQueryModel ctx = ctxOpt.get();
        Location bed = this.loadLocation(ctx.getBedId());
        String bedPart = this.resolveLocationDisplayName(bed);
        if (!StringUtils.hasText(bedPart)) {
            bedPart = this.stripUnoccupiedBedDisplay(ctx.getBedName());
        }
        String houseName = this.resolveHouseDisplayName(bed);
        if (!StringUtils.hasText(houseName)) {
            houseName = this.extractHouseFromUnoccupiedBedDisplay(ctx.getBedName());
        }
        put(target, "encounter.houseName", houseName);
        // 模板 fieldKey wardName 展示「病房」，绑 HOUSE 而非病区
        put(target, "encounter.wardName", houseName);
        // 页眉床号统一「病房-床位」，与护士站完整床位展示一致
        put(target, "encounter.bedNo", this.formatWardBedDisplay(houseName, bedPart));
    }

    /**
     * 拼页眉床号展示：有病房且床位段尚未含「-」时写成「病房-床位」。
     *
     * @param houseName 病房名
     * @param bedPart   床位段（可为已含横杠的完整「房-床」）
     * @return 展示文案；皆空时 null
     */
    private String formatWardBedDisplay(String houseName, String bedPart) {
        if (!StringUtils.hasText(bedPart)) {
            return null;
        }
        String bed = bedPart.trim();
        if (!StringUtils.hasText(houseName)) {
            return bed;
        }
        String house = houseName.trim();
        // 已是「506-加」等完整形态，不再前缀病房
        if (bed.contains("-")) {
            return bed;
        }
        if (bed.startsWith(house)) {
            return bed;
        }
        return house + "-" + bed;
    }

    /**
     * 按主键加载位置实体。
     *
     * @param locationId 位置主键
     * @return 实体；无主键或不存在时 null
     */
    private Location loadLocation(Long locationId) {
        if (locationId == null) {
            return null;
        }
        return locationQueryService.findOptionalById(locationId).orElse(null);
    }

    /**
     * 位置展示名：名称优先，空则业务号。
     *
     * @param location 位置实体
     * @return 展示名；皆空时 null
     */
    private String resolveLocationDisplayName(Location location) {
        if (location == null) {
            return null;
        }
        if (StringUtils.hasText(location.getName())) {
            return location.getName().trim();
        }
        if (StringUtils.hasText(location.getBusNo())) {
            return location.getBusNo().trim();
        }
        return null;
    }

    /**
     * 从床位父级解析病房名（仅 HOUSE 形态，避免把病区当病房）。
     *
     * @param bed 床位位置
     * @return 病房展示名；无父级或非 HOUSE 时 null
     */
    private String resolveHouseDisplayName(Location bed) {
        if (bed == null || bed.getParentId() == null) {
            return null;
        }
        Location parent = this.loadLocation(bed.getParentId());
        if (parent == null) {
            return null;
        }
        if (!LocationForm.HOUSE.getCode().equals(parent.getFormEnum())) {
            return null;
        }
        return this.resolveLocationDisplayName(parent);
    }

    /**
     * 去掉护士站「原{房}-{床}（未占床）」包装，得到中间段。
     *
     * @param bedDisplay 上下文床位展示文案
     * @return 中间段；无法解析时原样 trim；空则 null
     */
    private String stripUnoccupiedBedDisplay(String bedDisplay) {
        if (!StringUtils.hasText(bedDisplay)) {
            return null;
        }
        String raw = bedDisplay.trim();
        String prefix = InpatientNurseStationConstants.UNOCCUPIED_LAST_BED_DISPLAY_PREFIX;
        String suffix = InpatientNurseStationConstants.UNOCCUPIED_LAST_BED_DISPLAY_SUFFIX;
        if (raw.startsWith(prefix) && raw.endsWith(suffix) && raw.length() > prefix.length() + suffix.length()) {
            return raw.substring(prefix.length(), raw.length() - suffix.length()).trim();
        }
        return raw;
    }

    /**
     * 从未占床展示中间段拆病房名（「房-床」取最后一段之前）。
     *
     * @param bedDisplay 上下文床位展示文案
     * @return 病房名；无横杠或无法解析时 null
     */
    private String extractHouseFromUnoccupiedBedDisplay(String bedDisplay) {
        String middle = this.stripUnoccupiedBedDisplay(bedDisplay);
        if (!StringUtils.hasText(middle)) {
            return null;
        }
        int dash = middle.lastIndexOf('-');
        if (dash <= 0) {
            return null;
        }
        String house = middle.substring(0, dash).trim();
        if (StringUtils.hasText(house)) {
            return house;
        }
        return null;
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
