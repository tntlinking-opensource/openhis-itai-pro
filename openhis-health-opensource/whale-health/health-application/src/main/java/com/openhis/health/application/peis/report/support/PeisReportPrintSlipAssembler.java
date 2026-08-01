/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.support;

import com.openhis.health.application.peis.report.dto.PeisReportPrintSlipDto;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.openhis.health.domain.clinical.diagnosticreport.service.DiagnosticReportQueryService;
import com.openhis.health.domain.clinical.observation.queries.model.ObservationModel;
import com.openhis.health.domain.clinical.observation.service.ObservationQueryService;
import com.openhis.health.domain.clinical.observationdefinition.queries.model.ObservationDefinitionModel;
import com.openhis.health.domain.clinical.observationdefinition.service.ObservationDefinitionQueryService;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.encounterext.service.PeisEncounterExtQueryService;
import com.openhis.health.domain.share.constants.DiagnosticReportStatusConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisPrintScenarioConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
 * 体检总检报告打印 slip 组装（与 peis-report-print 模板 PrintData 契约一致）。
 */
@Component
@RequiredArgsConstructor
public class PeisReportPrintSlipAssembler {

    /** 定稿时间展示格式（分钟精度；GeneralSharedConstants 仅到秒）。 */
    private static final DateTimeFormatter CHECK_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DiagnosticReportQueryService diagnosticReportQueryService;
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    private final PatientQueryService patientQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final ObservationQueryService observationQueryService;
    private final ObservationDefinitionQueryService observationDefinitionQueryService;
    private final EncounterQueryService encounterQueryService;

    /**
     * 按就诊组装体检总检报告打印 slip。
     *
     * @param encounterId 就诊主键
     * @return 打印 slip
     */
    public PeisReportPrintSlipDto assemble(Long encounterId) {
        // Gate：PE 就诊 + final DR
        DiagnosticReportModel report = requireFinalReport(encounterId);
        encounterQueryService.requireEncounterEntityOfClass(
            encounterId,
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT,
            "仅体检就诊可打印报告");

        PeisReportPrintSlipDto slip = new PeisReportPrintSlipDto();
        slip.setEncounterId(encounterId);
        slip.setDiagnosticReportId(report.getId());
        slip.setPatientId(report.getPatientId());
        slip.setScenarioCode(PeisPrintScenarioConstants.REPORT_PRINT);
        slip.setSummaryText(firstNonBlank(report.getSummaryText(), ""));
        slip.setConclusionText(firstNonBlank(report.getConclusionText(), ""));
        slip.setCheckDateText(formatCheckDateText(report.getFinalizedAt()));
        slip.setHospitalName(firstNonBlank(resolveHospitalName(), ""));

        Patient patient = resolvePatient(report.getPatientId());
        if (patient != null) {
            slip.setPatientName(firstNonBlank(patient.getName(), ""));
            slip.setGenderEnumText(firstNonBlank(resolveGenderText(patient.getGenderEnum()), ""));
            slip.setAgeText(firstNonBlank(resolveAgeText(patient.getBirthDate()), ""));
        } else {
            slip.setPatientName("");
            slip.setGenderEnumText("");
            slip.setAgeText("");
        }

        String busNo = resolveBusNo(encounterId);
        slip.setBusNo(firstNonBlank(busNo, ""));

        List<ObservationModel> observations = observationQueryService.listByEncounterId(encounterId);
        slip.setObservationLines(buildObservationLines(observations));

        return slip;
    }

    /**
     * 读取已定稿总检报告，未定稿抛 REPORT_NOT_FINAL。
     *
     * @param encounterId 就诊主键
     * @return 总检报告读模型
     */
    private DiagnosticReportModel requireFinalReport(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        return diagnosticReportQueryService.getByEncounterId(encounterId)
            .filter(report -> DiagnosticReportStatusConstants.STATUS_FINAL.equals(report.getStatusEnum()))
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.REPORT_NOT_FINAL, "总检报告未定稿，不可打印"));
    }

    /**
     * 按患者主键读取患者实体。
     *
     * @param patientId 患者主键
     * @return 患者；不存在时 null
     */
    private Patient resolvePatient(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return patientQueryService.findOptionalPatientEntityById(patientId).orElse(null);
    }

    /**
     * 读取体检流水号。
     *
     * @param encounterId 就诊主键
     * @return 流水号；无扩展时 null
     */
    private String resolveBusNo(Long encounterId) {
        return peisEncounterExtQueryService.findOptionalByEncounterId(encounterId)
            .map(PeisEncounterExtModel::getBusNo)
            .map(StringUtils::trimToNull)
            .orElse(null);
    }

    /**
     * 观察实例列表转打印明细行。
     *
     * @param observations 观察读模型
     * @return 打印行
     */
    private List<PeisReportPrintSlipDto.ObservationLineDto> buildObservationLines(
        List<ObservationModel> observations) {
        List<PeisReportPrintSlipDto.ObservationLineDto> lines = new ArrayList<>();
        if (observations == null || observations.isEmpty()) {
            return lines;
        }
        Map<Long, ObservationDefinitionModel> definitionById = loadDefinitionMap(observations);
        for (ObservationModel observation : observations) {
            if (observation == null) {
                continue;
            }
            PeisReportPrintSlipDto.ObservationLineDto line = new PeisReportPrintSlipDto.ObservationLineDto();
            ObservationDefinitionModel definition = null;
            if (observation.getDefinitionId() != null) {
                definition = definitionById.get(observation.getDefinitionId());
            }
            line.setItemName(firstNonBlank(resolveItemName(definition), ""));
            line.setResultText(firstNonBlank(resolveResultText(observation), ""));
            line.setUnitText(firstNonBlank(resolveUnitText(definition), ""));
            line.setRefRangeText("");
            lines.add(line);
        }
        return lines;
    }

    /**
     * 批量加载观察定义（itemName / unitText）。
     *
     * @param observations 观察列表
     * @return definitionId → 定义读模型
     */
    private Map<Long, ObservationDefinitionModel> loadDefinitionMap(List<ObservationModel> observations) {
        Set<Long> definitionIds = new HashSet<>();
        for (ObservationModel observation : observations) {
            if (observation != null && observation.getDefinitionId() != null) {
                definitionIds.add(observation.getDefinitionId());
            }
        }
        Map<Long, ObservationDefinitionModel> result = new HashMap<>();
        if (definitionIds.isEmpty()) {
            return result;
        }
        for (ObservationDefinitionModel definition : observationDefinitionQueryService.listByIds(definitionIds)) {
            if (definition != null && definition.getId() != null) {
                result.put(definition.getId(), definition);
            }
        }
        return result;
    }

    /**
     * 解析观察项展示名。
     *
     * @param definition 观察定义
     * @return 展示名；无定义时 null
     */
    private String resolveItemName(ObservationDefinitionModel definition) {
        if (definition == null) {
            return null;
        }
        return StringUtils.trimToNull(definition.getDisplay());
    }

    /**
     * 解析结果展示：优先 valueString，否则 valueQuantity。
     *
     * @param observation 观察实例
     * @return 结果展示；无值时 null
     */
    private String resolveResultText(ObservationModel observation) {
        if (observation == null) {
            return null;
        }
        String textValue = StringUtils.trimToNull(observation.getValueString());
        if (textValue != null) {
            return textValue;
        }
        BigDecimal quantity = observation.getValueQuantity();
        if (quantity == null) {
            return null;
        }
        return quantity.stripTrailingZeros().toPlainString();
    }

    /**
     * 解析单位展示。
     *
     * @param definition 观察定义
     * @return 单位；无则 null
     */
    private String resolveUnitText(ObservationDefinitionModel definition) {
        if (definition == null) {
            return null;
        }
        return StringUtils.trimToNull(definition.getUnitCode());
    }

    /**
     * 从组织树取首个有效医院机构名作为报告抬头。
     *
     * @return 医院名称；未配置时 null
     */
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

    /**
     * 性别 code 转中文展示（male/female → 男/女，其余原样）。
     *
     * @param genderEnum 性别 code
     * @return 展示文案
     */
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

    /**
     * 由出生日期计算周岁年龄展示。
     *
     * @param birthDate 出生日期
     * @return 如 {@code 25岁}；无法计算时空串
     */
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

    /**
     * 定稿时间格式化为报告展示串。
     *
     * @param finalizedAt 定稿时间
     * @return 格式化时间；null 时空串
     */
    private String formatCheckDateText(LocalDateTime finalizedAt) {
        if (finalizedAt == null) {
            return "";
        }
        return CHECK_DATE_FORMATTER.format(finalizedAt);
    }

    /**
     * 两字符串非 blank 者优先，否则回落 fallback。
     *
     * @param first    优先串
     * @param fallback 回落串
     * @return trim 后非空串或 fallback 字符串形式
     */
    private static String firstNonBlank(String first, String fallback) {
        String trimmed = StringUtils.trimToNull(first);
        if (trimmed != null) {
            return trimmed;
        }
        return Objects.toString(fallback, "");
    }
}
