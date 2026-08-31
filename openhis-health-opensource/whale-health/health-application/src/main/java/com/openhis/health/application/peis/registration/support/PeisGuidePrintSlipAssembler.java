/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.support;

import com.openhis.health.application.peis.history.support.PeisPackageSnapshotSupport;
import com.openhis.health.application.peis.registration.dto.PeisGuidePrintSlipDto;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.encounterext.service.PeisEncounterExtQueryService;
import com.openhis.health.domain.peis.servicerequestext.queries.model.PeisServiceRequestExtModel;
import com.openhis.health.domain.peis.servicerequestext.service.PeisServiceRequestExtQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisPrintScenarioConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 体检指引单打印 slip 组装（与 peis-guide-print 模板 PrintData 契约一致）。
 */
@Component
@RequiredArgsConstructor
public class PeisGuidePrintSlipAssembler {

    private static final DateTimeFormatter REGISTERED_AT_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EncounterQueryService encounterQueryService;
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    private final PeisServiceRequestExtQueryService peisServiceRequestExtQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final PatientQueryService patientQueryService;
    private final OrganizationQueryService organizationQueryService;

    /**
     * 按就诊组装体检指引单打印 slip。
     *
     * @param encounterId 就诊主键
     * @return 打印 slip
     */
    public PeisGuidePrintSlipDto assemble(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT,
            "仅体检就诊可打印指引单");
        PeisEncounterExtModel ext = peisEncounterExtQueryService.getByEncounterId(encounterId);

        PeisGuidePrintSlipDto slip = new PeisGuidePrintSlipDto();
        slip.setEncounterId(encounterId);
        slip.setPatientId(encounter.getPatientId());
        slip.setScenarioCode(PeisPrintScenarioConstants.GUIDE_PRINT);
        slip.setHospitalName(firstNonBlank(resolveHospitalName(), ""));
        slip.setBusNo(firstNonBlank(ext.getBusNo(), ""));
        slip.setPackageName(firstNonBlank(
            PeisPackageSnapshotSupport.extractPackageName(ext.getPackageSnapshotJson()), ""));
        slip.setRegisteredAtText(formatRegisteredAt(encounter.getStartTime()));

        Patient patient = resolvePatient(encounter.getPatientId());
        if (patient != null) {
            slip.setPatientName(firstNonBlank(patient.getName(), ""));
            slip.setGenderEnumText(firstNonBlank(resolveGenderText(patient.getGenderEnum()), ""));
            slip.setAgeText(firstNonBlank(resolveAgeText(patient.getBirthDate()), ""));
        } else {
            slip.setPatientName("");
            slip.setGenderEnumText("");
            slip.setAgeText("");
        }

        List<PeisServiceRequestExtModel> lines =
            peisServiceRequestExtQueryService.listByEncounterId(encounterId);
        slip.setGuideLines(buildGuideLines(lines));
        return slip;
    }

    private List<PeisGuidePrintSlipDto.GuideLineDto> buildGuideLines(
        List<PeisServiceRequestExtModel> lines) {
        List<PeisGuidePrintSlipDto.GuideLineDto> result = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        Map<Long, ActivityDefinitionBaseQueryModel> activityById = loadActivityBases(lines);
        Set<Long> orgIds = new HashSet<>();
        for (ActivityDefinitionBaseQueryModel base : activityById.values()) {
            if (base != null && base.getOrgId() != null) {
                orgIds.add(base.getOrgId());
            }
        }
        Map<Long, Organization> orgById = organizationQueryService.mapByIds(orgIds);
        int seq = 1;
        for (PeisServiceRequestExtModel line : lines) {
            if (line == null) {
                continue;
            }
            ActivityDefinitionBaseQueryModel base =
                line.getActivityId() == null ? null : activityById.get(line.getActivityId());
            PeisGuidePrintSlipDto.GuideLineDto row = new PeisGuidePrintSlipDto.GuideLineDto();
            row.setSeqText(String.valueOf(seq++));
            row.setItemName(firstNonBlank(base == null ? null : base.getName(), ""));
            String deptName = "";
            if (base != null && base.getOrgId() != null) {
                Organization org = orgById.get(base.getOrgId());
                if (org != null) {
                    deptName = firstNonBlank(org.getName(), "");
                }
            }
            row.setDeptName(deptName);
            row.setLocationText("");
            result.add(row);
        }
        return result;
    }

    private Map<Long, ActivityDefinitionBaseQueryModel> loadActivityBases(
        List<PeisServiceRequestExtModel> lines) {
        Map<Long, ActivityDefinitionBaseQueryModel> result = new HashMap<>();
        Set<Long> activityIds = new HashSet<>();
        for (PeisServiceRequestExtModel line : lines) {
            if (line != null && line.getActivityId() != null) {
                activityIds.add(line.getActivityId());
            }
        }
        for (Long activityId : activityIds) {
            ActivityDefinitionDetailQueryModel detail =
                activityDefinitionQueryService.getServiceCatalog(activityId);
            if (detail != null && detail.getBaseInfo() != null) {
                result.put(activityId, detail.getBaseInfo());
            }
        }
        return result;
    }

    private Patient resolvePatient(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return patientQueryService.findOptionalPatientEntityById(patientId).orElse(null);
    }

    private String resolveHospitalName() {
        for (Organization organization : organizationQueryService.getOrganizationTree()) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            if (!PublicationStatus.ACTIVE.getCode().equals(organization.getStatusEnum())) {
                continue;
            }
            if (OrganizationType.HOSPITAL.getCode().equals(organization.getTypeEnum())) {
                String name = StringUtils.trimToNull(organization.getName());
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }

    private String resolveGenderText(String genderEnum) {
        String code = StringUtils.trimToEmpty(genderEnum);
        if ("male".equalsIgnoreCase(code)) {
            return "男";
        }
        if ("female".equalsIgnoreCase(code)) {
            return "女";
        }
        return StringUtils.trimToNull(genderEnum);
    }

    private String resolveAgeText(LocalDate birthDate) {
        if (birthDate == null) {
            return "";
        }
        Period period = Period.between(birthDate, LocalDate.now());
        int years = period.getYears();
        if (years < 0) {
            return "";
        }
        return years + "岁";
    }

    private String formatRegisteredAt(LocalDateTime startTime) {
        if (startTime == null) {
            return "";
        }
        return REGISTERED_AT_FORMATTER.format(startTime);
    }

    private static String firstNonBlank(String first, String fallback) {
        String trimmed = StringUtils.trimToNull(first);
        if (trimmed != null) {
            return trimmed;
        }
        return Objects.toString(fallback, "");
    }
}
