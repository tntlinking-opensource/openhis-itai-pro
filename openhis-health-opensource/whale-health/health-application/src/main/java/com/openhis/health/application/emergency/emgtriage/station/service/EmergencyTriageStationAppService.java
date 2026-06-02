package com.openhis.health.application.emergency.emgtriage.station.service;

import com.alibaba.fastjson2.JSON;
import com.openhis.health.application.emergency.emgtriage.station.dto.PatientAlertRiskDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.PatientIdentifierItemDto;
import com.openhis.health.application.emergency.patientmanagement.service.PatientDisplayFlagAssembler;
import com.openhis.health.application.emergency.emgtriage.station.dto.EmgTriageExtendedJsonFieldOptionsDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.EmgTriageWorkbenchRowDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.EmgKnowledgeBaseItemMaintenanceDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.KnowledgeCandidateRowDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.KnowledgeSearchPresetDto;
import com.openhis.health.application.emergency.emgtriage.station.mapper.EmergencyTriageStationMapper;
import com.openhis.health.application.emergency.emgtriage.station.port.EmergencyReportArrivalPort;
import com.openhis.health.application.emergency.emgtriage.station.port.QueuePriorityPushPort;
import com.openhis.health.application.emergency.emgtriage.station.port.RegistrationSummaryPort;
import com.openhis.health.application.emergency.emgtriage.station.port.RegistrationSummarySnapshot;
import com.openhis.health.application.emergency.emgtriage.station.port.UserWorkbenchLayoutPort;
import com.openhis.health.application.emergency.emgtriage.station.port.VitalsIngestionPort;
import com.openhis.health.application.emergency.emgtriage.station.request.AmendEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.CaptureIdentityForEmergencyTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.CompleteAssessmentScaleRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.CoordinateTriageRegistrationRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.DeleteEmgKnowledgeBaseItemRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.ExportOrDeleteEmgTriageDataRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.FetchRegistrationSummaryRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.IngestVitalsAndSuggestLevelRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.InitializeEmergencyTriageStationRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.ListPendingSecondaryTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.ManageMassCasualtyIncidentRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.GetEmgTriageDetailForEditRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.PageEmgKnowledgeBaseItemsRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.PageEmgTriageWorkbenchRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.PrintWristbandOrTriageSlipRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.PushQueuePriorityRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.RegisterOrUpdatePatientForEmergencyRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.ReportPatientArrivalRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SaveEmgKnowledgeBaseItemRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SaveOrSubmitEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SaveWorkbenchLayoutRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SearchKnowledgeForEmergencyTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SearchPatientsForEmergencyTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SubmitSecondaryEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.VoidEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.response.AmendEmgTriageResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.CaptureIdentityForEmergencyTriageResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.CompleteAssessmentScaleResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.CoordinateTriageRegistrationResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.DeleteEmgKnowledgeBaseItemResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.ExportOrDeleteEmgTriageDataResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.FetchRegistrationSummaryResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.IngestVitalsAndSuggestLevelResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.InitializeEmergencyTriageStationResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.ListPendingSecondaryTriageResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.ManageMassCasualtyIncidentResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.GetEmgTriageDetailForEditResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.PageEmgKnowledgeBaseItemsResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.PageEmgTriageWorkbenchResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.PrintWristbandOrTriageSlipResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.PushQueuePriorityResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.RegisterOrUpdatePatientForEmergencyResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.ReportPatientArrivalResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.SaveEmgKnowledgeBaseItemResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.SaveOrSubmitEmgTriageResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.SaveWorkbenchLayoutResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.SearchKnowledgeForEmergencyTriageResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.SearchPatientsForEmergencyTriageResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.SubmitSecondaryEmgTriageResponse;
import com.openhis.health.application.emergency.emgtriage.station.response.VoidEmgTriageResponse;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.domain.administration.encounter.command.RegisterOutpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.command.TriageEncounterCommand;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.queries.DuplicatePatientCheckQuery;
import com.openhis.health.domain.administration.patient.queries.model.DuplicatePatientCheckQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientSimpleQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientDomainService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.emergency.emgtriage.command.SaveEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriage;
import com.openhis.health.domain.emergency.emgtriage.queries.EmgTriagePageQuery;
import com.openhis.health.domain.emergency.emgtriage.queries.EmgTriagePendingSecondaryQuery;
import com.openhis.health.domain.emergency.emgtriage.queries.EmgTriageQueryService;
import com.openhis.health.domain.emergency.emgtriage.queries.model.EmgTriageByEncounterQueryModel;
import com.openhis.health.domain.emergency.emgtriage.queries.model.EmgTriageListItemQueryModel;
import com.openhis.health.domain.emergency.emgtriage.service.EmgTriageDomainService;
import com.openhis.health.domain.emergency.patientalert.entity.PatientAlert;
import com.openhis.health.domain.emergency.patientalert.queries.PatientAlertQueryService;
import com.openhis.health.domain.emergency.patientalert.queries.model.PatientAlertActiveListItemQueryModel;
import com.openhis.health.domain.emergency.emgtriageassessment.command.BuildAssessmentPayloadCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.command.ComputeMewsCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.command.SuggestTriageLevelCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.model.DerivedScores;
import com.openhis.health.domain.emergency.emgtriageassessment.model.EmgTriageAssessmentPayloadItem;
import com.openhis.health.domain.emergency.emgtriageassessment.model.TriageAssessmentFormDto;
import com.openhis.health.domain.emergency.emgtriageassessment.model.TriageAssessmentFormLineDto;
import com.openhis.health.domain.emergency.emgtriageassessment.service.EmgTriageAssessmentPolicy;
import com.openhis.health.domain.emergency.knowledge.KnowledgeConstants;
import com.openhis.health.domain.emergency.knowledge.command.CreateKnowledgeBaseItemCommand;
import com.openhis.health.domain.emergency.knowledge.command.DeleteKnowledgeBaseItemCommand;
import com.openhis.health.domain.emergency.knowledge.command.UpdateKnowledgeBaseItemCommand;
import com.openhis.health.domain.emergency.knowledge.entity.KnowledgeBaseItem;
import com.openhis.health.domain.emergency.knowledge.queries.KnowledgeBaseItemPageQuery;
import com.openhis.health.domain.emergency.knowledge.queries.KnowledgeQueryService;
import com.openhis.health.domain.emergency.knowledge.service.KnowledgeDomainService;
import com.openhis.health.domain.emergency.knowledge.queries.model.KnowledgeBaseItemQueryModel;
import com.openhis.health.domain.emergency.knowledge.queries.model.KnowledgeTopNItemQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.TriageLevel;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 急诊预检分诊工作站应用编排入口 (AS-EMG-001-SDD 第 3.1 节)。
 *
 * <p>接入：应用服务 POST /api/app/emergencyTriageStation/&lt;methodName&gt;（JSON 请求体）。</p>
 */
@Service
@RequiredArgsConstructor
public class EmergencyTriageStationAppService {

    private static final String MODE_TRIAGE_FIRST = "TRIAGE_FIRST";
    private static final String MODE_REGISTER_FIRST = "REGISTER_FIRST";
    private static final String MODE_CONCURRENT = "CONCURRENT";

    private static final String DEFAULT_KNOWLEDGE_CATEGORY = "OTHER";

    /** 急诊自动开立就诊时写死的号源/服务类型（与产品约定一致）。 */
    private static final long EMERGENCY_REGISTER_SERVICE_TYPE_ID = 1L;

    private final EmgTriageDomainService emgTriageDomainService;
    private final EmgTriageQueryService emgTriageQueryService;
    private final PatientQueryService patientQueryService;
    private final PatientAlertQueryService patientAlertQueryService;
    private final PatientDomainService patientDomainService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterDomainService encounterDomainService;
    private final KnowledgeQueryService knowledgeQueryService;
    private final KnowledgeDomainService knowledgeDomainService;
    private final EmgTriageAssessmentPolicy emgTriageAssessmentPolicy;
    private final OrganizationQueryService organizationQueryService;
    private final LocationQueryService locationQueryService;
    private final CodeSystemQueryService codeSystemQueryService;
    private final RegistrationSummaryPort registrationSummaryPort;
    private final VitalsIngestionPort vitalsIngestionPort;
    private final QueuePriorityPushPort queuePriorityPushPort;
    private final EmergencyReportArrivalPort emergencyReportArrivalPort;
    private final UserWorkbenchLayoutPort userWorkbenchLayoutPort;
    private final EmergencyTriageStationMapper emergencyTriageStationMapper;
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * IF-EMG-01：初始化分诊工作站。
     *
     * @param request 初始化请求
     * @return 下拉与知识预设
     */
    public InitializeEmergencyTriageStationResponse initializeEmergencyTriageStation(
            InitializeEmergencyTriageStationRequest request) {
        if (request == null || request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        Long organizationId = request.getOrganizationId();
        List<OptionDto> triageLevelOptions = new ArrayList<>(codeSystemQueryService.getOptionListByEnum(TriageLevel.class));
        triageLevelOptions.add(new OptionDto(5L, "5", "五级（补充）"));
        List<OptionDto> departmentOptions = buildDepartmentOptions(organizationId);
        List<OptionDto> destinationOptions = buildDestinationOptions(organizationId);
        KnowledgeSearchPresetDto preset = new KnowledgeSearchPresetDto();
        preset.getEnabledTypes().add(KnowledgeConstants.TYPE_CHIEF_COMPLAINT);
        preset.getEnabledTypes().add(KnowledgeConstants.TYPE_SYMPTOM);
        preset.setDefaultTopN(8);
        knowledgeQueryService.listTopKnowledgeItemsForTriage(KnowledgeConstants.TYPE_SYMPTOM, 8);
        List<OptionDto> vitalsPolicyOptions = new ArrayList<>();
        vitalsPolicyOptions.add(new OptionDto(1L, "DEFAULT_MEWS", "默认 MEWS 策略"));
        String symptomDictVersion = buildKnowledgePicklistVersionStamp();
        List<OptionDto> specialtyGreenChannelOptions = new ArrayList<>();
        specialtyGreenChannelOptions.add(new OptionDto(1L, "STROKE", "卒中绿通"));
        specialtyGreenChannelOptions.add(new OptionDto(2L, "CHEST_PAIN", "胸痛绿通"));
        specialtyGreenChannelOptions.add(new OptionDto(3L, "TRAUMA", "创伤绿通"));
        specialtyGreenChannelOptions.add(new OptionDto(4L, "PREGNANCY", "孕产妇绿通"));
        specialtyGreenChannelOptions.add(new OptionDto(5L, "PEDIATRIC", "儿科绿通"));
        List<OptionDto> patientSourceOptions = new ArrayList<>();
        patientSourceOptions.add(new OptionDto(1L, "WALK_IN", "步行 / 自行来院"));
        patientSourceOptions.add(new OptionDto(2L, "AMBULANCE_120", "120 急救入院"));
        patientSourceOptions.add(new OptionDto(3L, "AMBULANCE_OTHER", "其他救护车"));
        patientSourceOptions.add(new OptionDto(4L, "REFERRAL_OUT", "外院转诊"));
        patientSourceOptions.add(new OptionDto(5L, "TRANSFER_IN", "院内转入"));
        patientSourceOptions.add(new OptionDto(6L, "POLICE", "公安 / 警务护送"));
        patientSourceOptions.add(new OptionDto(7L, "OTHER", "其他"));
        List<OptionDto> arrivalModeOptions = new ArrayList<>();
        arrivalModeOptions.add(new OptionDto(0L, "", "（请选择来院方式）"));
        arrivalModeOptions.add(new OptionDto(1L, "WALK_IN", "步行 / 自行来院"));
        arrivalModeOptions.add(new OptionDto(2L, "AMBULANCE_120", "120 救护车"));
        arrivalModeOptions.add(new OptionDto(3L, "AMBULANCE_OTHER", "其他救护车"));
        arrivalModeOptions.add(new OptionDto(4L, EmgTriage.ARRIVAL_EXTERNAL_TRANSFER, "外院转入"));
        arrivalModeOptions.add(new OptionDto(5L, "REFERRAL", "转诊入院"));
        arrivalModeOptions.add(new OptionDto(6L, "OTHER", "其他"));
        List<OptionDto> transferInOrgOptions = new ArrayList<>();
        transferInOrgOptions.add(new OptionDto(0L, "", "（请选择转入机构）"));
        long transferSort = 1;
        for (Organization org : organizationQueryService.getOrganizationTree()) {
            if (org == null || org.getId() == null) {
                continue;
            }
            String label;
            if (StringUtils.isBlank(org.getName())) {
                label = String.valueOf(org.getId());
            } else {
                label = org.getName().trim();
            }
            transferInOrgOptions.add(new OptionDto(transferSort++, String.valueOf(org.getId()), label));
        }
        List<OptionDto> mciEventOptions = new ArrayList<>();
        mciEventOptions.add(new OptionDto(0L, "", "（无群伤关联）"));
        List<OptionDto> chiefComplaintOptionalPolicyOptions = new ArrayList<>();
        chiefComplaintOptionalPolicyOptions.add(new OptionDto(1L, "false", "须含主诉标签"));
        chiefComplaintOptionalPolicyOptions.add(new OptionDto(2L, "true", "允许无主诉（空数组）"));
        List<OptionDto> chiefComplaintTermOptions =
                buildPicklistOptionsFromKnowledge(KnowledgeConstants.TYPE_CHIEF_COMPLAINT);
        List<OptionDto> symptomTermOptions = buildPicklistOptionsFromKnowledge(KnowledgeConstants.TYPE_SYMPTOM);
        EmgTriageExtendedJsonFieldOptionsDto extendedJsonFieldOptions = buildExtendedJsonFieldOptions();
        return emergencyTriageStationMapper.toInitializeEmergencyTriageStationResponse(
                triageLevelOptions,
                departmentOptions,
                destinationOptions,
                preset,
                vitalsPolicyOptions,
                symptomDictVersion,
                specialtyGreenChannelOptions,
                patientSourceOptions,
                arrivalModeOptions,
                transferInOrgOptions,
                mciEventOptions,
                chiefComplaintOptionalPolicyOptions,
                chiefComplaintTermOptions,
                symptomTermOptions,
                extendedJsonFieldOptions);
    }

    /**
     * IF-EMG-01：从 {@code emg_knowledge_base_item} 加载主诉/症状多选候选（启用、未删、sort_order 升序）。
     */
    private List<OptionDto> buildPicklistOptionsFromKnowledge(String type) {
        List<OptionDto> list = new ArrayList<>();
        long sort = 1;
        for (var row : knowledgeQueryService.listEnabledPicklistTermsByType(type)) {
            if (row == null || StringUtils.isBlank(row.getName())) {
                continue;
            }
            String name = row.getName().trim();
            list.add(new OptionDto(sort++, name, name));
        }
        if (!list.isEmpty()) {
            return list;
        }
        return fallbackPicklistOptionsForType(type);
    }

    private static List<OptionDto> fallbackPicklistOptionsForType(String type) {
        String[] labels =
                KnowledgeConstants.TYPE_CHIEF_COMPLAINT.equals(type)
                        ? new String[] {
                            "胸痛", "胸闷", "心悸", "腹痛", "头痛", "发热", "咳嗽", "呼吸困难",
                            "恶心呕吐", "眩晕", "外伤", "出血", "意识障碍", "抽搐",
                        }
                        : new String[] {
                            "急性起病", "反复发作", "进行性加重", "伴冷汗", "伴放射痛",
                            "伴发热", "伴黄疸", "创伤史", "误食", "过敏史",
                        };
        List<OptionDto> list = new ArrayList<>();
        long s = 1;
        for (String lb : labels) {
            list.add(new OptionDto(s++, lb, lb));
        }
        return list;
    }

    private String buildKnowledgePicklistVersionStamp() {
        var cc = knowledgeQueryService.listEnabledPicklistTermsByType(KnowledgeConstants.TYPE_CHIEF_COMPLAINT);
        var sy = knowledgeQueryService.listEnabledPicklistTermsByType(KnowledgeConstants.TYPE_SYMPTOM);
        long ccMax =
                cc.stream()
                        .map(r -> r.getUpdatedAt())
                        .filter(Objects::nonNull)
                        .mapToLong(t -> t.toEpochSecond(java.time.ZoneOffset.UTC))
                        .max()
                        .orElse(0L);
        long syMax =
                sy.stream()
                        .map(r -> r.getUpdatedAt())
                        .filter(Objects::nonNull)
                        .mapToLong(t -> t.toEpochSecond(java.time.ZoneOffset.UTC))
                        .max()
                        .orElse(0L);
        return "emg-knowledge-picklist@" + Math.max(ccMax, syMax) + ":" + cc.size() + "+" + sy.size();
    }

    /**
     * IF-EMG-01：扩展 JSON 片段字段的多选字典（后续可改为术语服务/配置中心）。
     */
    private EmgTriageExtendedJsonFieldOptionsDto buildExtendedJsonFieldOptions() {
        EmgTriageExtendedJsonFieldOptionsDto dto = new EmgTriageExtendedJsonFieldOptionsDto();
        dto.getDiseaseTypeOptions().add(new OptionDto(1L, "CHEST_PAIN", "胸痛"));
        dto.getDiseaseTypeOptions().add(new OptionDto(2L, "STROKE", "卒中"));
        dto.getDiseaseTypeOptions().add(new OptionDto(3L, "TRAUMA", "创伤"));
        dto.getDiseaseTypeOptions().add(new OptionDto(4L, "GI_BLEED", "消化道出血"));
        dto.getDiseaseTypeOptions().add(new OptionDto(5L, "SEPSIS", "脓毒症 / 重症感染"));
        dto.getDiseaseTypeOptions().add(new OptionDto(6L, "MATERNAL_EMERGENCY", "孕产妇急症"));
        dto.getDiseaseTypeOptions().add(new OptionDto(7L, "PEDIATRIC_CRITICAL", "儿科重症"));
        dto.getDiseaseTypeOptions().add(new OptionDto(8L, "RESPIRATORY_FAILURE", "急性呼吸衰竭"));
        dto.getSpecialPopulationTagOptions().add(new OptionDto(1L, "ELDERLY", "高龄"));
        dto.getSpecialPopulationTagOptions().add(new OptionDto(2L, "PREGNANT", "孕产妇"));
        dto.getSpecialPopulationTagOptions().add(new OptionDto(3L, "NEONATE_INFANT", "新生儿 / 婴幼儿"));
        dto.getSpecialPopulationTagOptions().add(new OptionDto(4L, "DISABLED", "残障人士"));
        dto.getSpecialPopulationTagOptions().add(new OptionDto(5L, "MENTAL_ILLNESS", "精神障碍"));
        dto.getSpecialPopulationTagOptions().add(new OptionDto(6L, "UNKNOWN_IDENTITY", "无名氏 / 三无"));
        dto.getEpidemicSurveyOptions().add(new OptionDto(1L, "FEVER", "发热"));
        dto.getEpidemicSurveyOptions().add(new OptionDto(2L, "RESPIRATORY_SYMPTOM", "呼吸道症状"));
        dto.getEpidemicSurveyOptions().add(new OptionDto(3L, "EPIDEMIC_CONTACT", "流行病学暴露史"));
        dto.getEpidemicSurveyOptions().add(new OptionDto(4L, "RECENT_TRAVEL", "近期旅居史"));
        dto.getEpidemicSurveyOptions().add(new OptionDto(5L, "CLUSTER_CASE", "聚集性发病"));
        dto.getAmbulance120Options().add(new OptionDto(1L, "CPR_ON_SCENE", "现场心肺复苏"));
        dto.getAmbulance120Options().add(new OptionDto(2L, "INTUBATION", "气管插管"));
        dto.getAmbulance120Options().add(new OptionDto(3L, "IV_LINE", "静脉通路已建立"));
        dto.getAmbulance120Options().add(new OptionDto(4L, "MEDS_EN_ROUTE", "途中用药"));
        dto.getAmbulance120Options().add(new OptionDto(5L, "DEFIBRILLATION", "电除颤"));
        dto.getPolice110Options().add(new OptionDto(1L, "TRAFFIC_ACCIDENT", "交通事故"));
        dto.getPolice110Options().add(new OptionDto(2L, "VIOLENT_INCIDENT", "治安 / 伤害事件"));
        dto.getPolice110Options().add(new OptionDto(3L, "DRUNK_INTOXICATION", "醉酒 / 中毒"));
        dto.getPolice110Options().add(new OptionDto(4L, "SUICIDE_SELF_HARM", "自杀 / 自残"));
        dto.getMonitorDeviceBindingOptions().add(new OptionDto(1L, "ECG_MONITOR", "心电监护"));
        dto.getMonitorDeviceBindingOptions().add(new OptionDto(2L, "SPO2", "血氧饱和度"));
        dto.getMonitorDeviceBindingOptions().add(new OptionDto(3L, "NIBP", "无创血压"));
        dto.getMonitorDeviceBindingOptions().add(new OptionDto(4L, "ETCO2", "呼气末二氧化碳"));
        dto.getMonitorDeviceBindingOptions().add(new OptionDto(5L, "TEMP", "体温连续监测"));
        return dto;
    }

    /**
     * IF-EMG-02：关键字检索患者。
     *
     * @param request 检索条件
     * @return 分页患者与警示占位
     */
    public SearchPatientsForEmergencyTriageResponse searchPatientsForEmergencyTriage(
            SearchPatientsForEmergencyTriageRequest request) {
        if (request == null || request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        validateEmergencyPageQuery(request.getPageNo(), request.getPageSize());
        Page<PatientPageItemQueryModel> page =
                patientQueryService.searchPatientsPaged(emergencyTriageStationMapper.toPatientPagedQuery(request));
        SearchPatientsForEmergencyTriageResponse resp = new SearchPatientsForEmergencyTriageResponse();
        PagedResult<PatientPageItemQueryModel> patientPage = new PagedResult<>();
        resp.setPatientPage(PagedResultBuilder.build(patientPage, page.getContent(), page));
        List<PatientAlertRiskDto> alerts = new ArrayList<>();
        for (PatientPageItemQueryModel patient : page.getContent()) {
            if (patient.getId() == null) {
                continue;
            }
            for (PatientAlertActiveListItemQueryModel alert :
                patientAlertQueryService.listActiveAlertsByPatient(patient.getId(), null)) {
                if (!PatientAlert.TYPE_BLACKLIST.equals(alert.getAlertTypeEnum())) {
                    continue;
                }
                PatientAlertRiskDto risk = new PatientAlertRiskDto();
                risk.setPatientId(patient.getId());
                risk.setAlertLevelCode("BLACKLIST");
                String typeLabel = PatientDisplayFlagAssembler.markTypeDisplay(alert.getMarkTypeCode());
                risk.setMessage(typeLabel + "：" + alert.getReasonText());
                alerts.add(risk);
            }
        }
        resp.setAlerts(alerts);
        return resp;
    }

    /**
     * IF-EMG-03：急诊建档。
     *
     * @param request 建档请求
     * @return 新建患者主键
     */
    public RegisterOrUpdatePatientForEmergencyResponse registerOrUpdatePatientForEmergency(
            RegisterOrUpdatePatientForEmergencyRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (!hasActiveProvisionalTriageNo(request)) {
            DuplicatePatientCheckQuery dq = new DuplicatePatientCheckQuery();
            dq.setName(request.getName());
            dq.setGenderEnum(request.getGenderEnum());
            dq.setBirthDate(request.getBirthDate());
            dq.setIdCard(request.getIdCard());
            DuplicatePatientCheckQueryModel dup = patientQueryService.checkRegistrationDuplicatePatient(dq);
            if (Boolean.TRUE.equals(dup.getDuplicated())) {
                throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "同人校验未通过，档案可能已存在");
            }
        }
        Long patientId = patientDomainService.registerPatient(emergencyTriageStationMapper.toRegisterPatientCommand(request));
        RegisterOrUpdatePatientForEmergencyResponse response = new RegisterOrUpdatePatientForEmergencyResponse();
        response.setPatientId(patientId);
        return response;
    }

    /**
     * IF-EMG-04：多源身份采集。
     *
     * @param request 渠道与原文
     * @return 标准化证件号与可选命中患者
     */
    public CaptureIdentityForEmergencyTriageResponse captureIdentityForEmergencyTriage(
            CaptureIdentityForEmergencyTriageRequest request) {
        if (request == null || StringUtils.isBlank(request.getChannelCode()) || request.getRawPayload() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "渠道或载荷不能为空");
        }
        String normalized = request.getRawPayload().trim();
        CaptureIdentityForEmergencyTriageResponse resp = new CaptureIdentityForEmergencyTriageResponse();
        resp.setNormalizedIdCard(normalized);
        resp.setHintMessage("请在建档步骤核对证件信息");
        if (normalized.length() >= 15) {
            PatientSimpleQueryModel hit = patientQueryService.findPatientByIdCard(normalized);
            if (hit != null) {
                resp.setPatientId(hit.getId());
            }
        }
        return resp;
    }

    /**
     * IF-EMG-05：拉取挂号摘要。
     *
     * @param request 就诊或流水号
     * @return 摘要字段
     */
    public FetchRegistrationSummaryResponse fetchRegistrationSummary(FetchRegistrationSummaryRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "请求体不能为空");
        }
        Long encounterId = request.getEncounterId();
        if (encounterId == null && StringUtils.isBlank(request.getSlipNo())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "encounterId 与 slipNo 至少填其一");
        }
        if (encounterId == null) {
            encounterId = encounterQueryService.loadEncounterByBusNo(request.getSlipNo()).getId();
        }
        RegistrationSummarySnapshot snap = registrationSummaryPort.fetchSummary(encounterId);
        FetchRegistrationSummaryResponse resp = new FetchRegistrationSummaryResponse();
        Long outEncounterId;
        if (snap.getEncounterId() != null) {
            outEncounterId = snap.getEncounterId();
        } else {
            outEncounterId = encounterId;
        }
        resp.setEncounterId(outEncounterId);
        resp.setRegistrationStatus(snap.getRegistrationStatus());
        resp.setDepartmentName(snap.getDepartmentName());
        resp.setDoctorName(snap.getDoctorName());
        resp.setFailReason(null);
        return resp;
    }

    /**
     * IF-EMG-06：保存或提交分诊。
     *
     * @param request 分诊表单
     * @return 分诊主键
     */
    public SaveOrSubmitEmgTriageResponse saveOrSubmitEmgTriage(SaveOrSubmitEmgTriageRequest request) {
        if (request != null && StringUtils.isBlank(request.getTriageRegistrationModeEnum())) {
            request.setTriageRegistrationModeEnum(MODE_TRIAGE_FIRST);
        }
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        SaveOrSubmitEmgTriageResponse resp = executeSaveOrSubmitEmgTriage(request, operatorId);
        maybeApplyTriageFirstTransitionAfterSave(request, operatorId);
        return resp;
    }

    /**
     * IF-EMG-07：修订分诊。
     *
     * @param request 修订载荷
     * @return 分诊主键
     */
    public AmendEmgTriageResponse amendEmgTriage(AmendEmgTriageRequest request) {
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        EmgTriage saved =
                emgTriageDomainService.amendEmgTriage(
                        emergencyTriageStationMapper.toAmendEmgTriageCommand(request, operatorId));
        AmendEmgTriageResponse resp = new AmendEmgTriageResponse();
        resp.setEmgTriageId(saved.getId());
        return resp;
    }

    /**
     * IF-EMG-08：作废分诊。
     *
     * @param request 作废原因
     * @return 是否成功
     */
    public VoidEmgTriageResponse voidEmgTriage(VoidEmgTriageRequest request) {
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        boolean ok =
                emgTriageDomainService.voidEmgTriage(
                        emergencyTriageStationMapper.toVoidEmgTriageCommand(request, operatorId));
        VoidEmgTriageResponse resp = new VoidEmgTriageResponse();
        resp.setSuccess(ok);
        return resp;
    }

    /**
     * IF-EMG-09：二次分诊。
     *
     * @param request 二次分诊载荷
     * @return 新分诊主键
     */
    public SubmitSecondaryEmgTriageResponse submitSecondaryEmgTriage(SubmitSecondaryEmgTriageRequest request) {
        if (request == null || request.getPriorEmgTriageId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "前序分诊主键不能为空");
        }
        if (request.getId() != null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "二次分诊必须新建分诊行");
        }
        encounterQueryService.mustGetEncounter(request.getEncounterId());
        SaveOrSubmitEmgTriageResponse core = executeSaveOrSubmitEmgTriage(request);
        SubmitSecondaryEmgTriageResponse resp = new SubmitSecondaryEmgTriageResponse();
        resp.setEmgTriageId(core.getEmgTriageId());
        return resp;
    }

    /**
     * IF-EMG-10：待二次分诊分页。
     *
     * @param request 分页参数
     * @return 工作台行分页
     */
    public ListPendingSecondaryTriageResponse listPendingSecondaryTriage(ListPendingSecondaryTriageRequest request) {
        if (request == null || request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        validateEmergencyPageQuery(request.getPageNo(), request.getPageSize());
        EmgTriagePendingSecondaryQuery filter = new EmgTriagePendingSecondaryQuery();
        filter.setOrganizationId(request.getOrganizationId());
        filter.setSlaMinutes(request.getSlaMinutes());
        Pageable pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        Page<EmgTriageListItemQueryModel> page = emgTriageQueryService.queryPendingSecondaryTriagePage(filter, pageable);
        List<EmgTriageWorkbenchRowDto> rows = new ArrayList<>();
        for (EmgTriageListItemQueryModel row : page.getContent()) {
            rows.add(emergencyTriageStationMapper.toWorkbenchRowDto(row));
        }
        enrichEmgTriageWorkbenchRows(rows);
        ListPendingSecondaryTriageResponse resp = new ListPendingSecondaryTriageResponse();
        PagedResult<EmgTriageWorkbenchRowDto> pageResult = new PagedResult<>();
        resp.setPage(PagedResultBuilder.build(pageResult, rows, page));
        return resp;
    }

    /**
     * IF-EMG-11：体征接入与分级建议。
     *
     * @param request 体征与上下文
     * @return 建议分级与追溯键
     */
    public IngestVitalsAndSuggestLevelResponse ingestVitalsAndSuggestLevel(IngestVitalsAndSuggestLevelRequest request) {
        if (request == null || request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊或患者不能为空");
        }
        try {
            vitalsIngestionPort.persist(request.getEncounterId(), request.getPatientId(), request.getVitalsInput());
        } catch (BusinessException ignored) {
            // IF-EMG-11：Observation 未接入时不阻断 MEWS/建议分级回显（体征落库与建议解耦）
        }
        ComputeMewsCommand mewsCmd = new ComputeMewsCommand();
        mewsCmd.setVitalsInput(request.getVitalsInput());
        mewsCmd.setAvpuCode(request.getAvpuCode());
        mewsCmd.setMewsDisabledByOrganization(request.getMewsDisabledByOrganization());
        Integer mewsScore = emgTriageAssessmentPolicy.computeMews(mewsCmd);
        DerivedScores ds = new DerivedScores();
        ds.setMewsScore(mewsScore);
        SuggestTriageLevelCommand suggestCmd = new SuggestTriageLevelCommand();
        suggestCmd.setDerivedScores(ds);
        suggestCmd.setVitalsInput(request.getVitalsInput());
        Integer suggested = emgTriageAssessmentPolicy.suggestTriageLevel(suggestCmd);
        IngestVitalsAndSuggestLevelResponse resp = new IngestVitalsAndSuggestLevelResponse();
        resp.setSuggestedLevelEnum(suggested);
        resp.setPolicyTraceId(request.getVitalsAutoTierPolicyId());
        resp.setMewsScore(mewsScore);
        return resp;
    }

    /**
     * IF-EMG-12：完成评分量表（不落库）。
     *
     * @param request 量表作答
     * @return 载荷 JSON 与建议分级
     */
    public CompleteAssessmentScaleResponse completeAssessmentScale(CompleteAssessmentScaleRequest request) {
        if (request == null || request.getEmgTriageId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "分诊主键不能为空");
        }
        TriageAssessmentFormDto formDto = new TriageAssessmentFormDto();
        for (TriageAssessmentFormLineDto line : request.getScaleInputs()) {
            if (line == null) {
                continue;
            }
            if (StringUtils.isBlank(line.getAssessmentType()) && StringUtils.isNotBlank(request.getAssessmentType())) {
                line.setAssessmentType(request.getAssessmentType());
            }
            formDto.getLines().add(line);
        }
        BuildAssessmentPayloadCommand buildCmd = new BuildAssessmentPayloadCommand();
        buildCmd.setEmgTriageId(request.getEmgTriageId());
        buildCmd.setFormDto(formDto);
        buildCmd.setAssessedBy(request.getAssessedBy());
        List<EmgTriageAssessmentPayloadItem> payloadItems =
                emgTriageAssessmentPolicy.buildAssessmentPayload(buildCmd);
        DerivedScores derivedScores = new DerivedScores();
        SuggestTriageLevelCommand suggestCmd = new SuggestTriageLevelCommand();
        suggestCmd.setDerivedScores(derivedScores);
        Integer suggested = emgTriageAssessmentPolicy.suggestTriageLevel(suggestCmd);
        CompleteAssessmentScaleResponse resp = new CompleteAssessmentScaleResponse();
        resp.setPayloadJson(JSON.toJSONString(payloadItems));
        resp.setDerivedScores(derivedScores);
        resp.setSuggestedLevelEnum(suggested);
        return resp;
    }

    /**
     * IF-EMG-13：检索知识库词条。
     *
     * @param request 关键字或 TopN
     * @return 分页或 TopN 候选
     */
    public SearchKnowledgeForEmergencyTriageResponse searchKnowledgeForEmergencyTriage(
            SearchKnowledgeForEmergencyTriageRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "请求体不能为空");
        }
        SearchKnowledgeForEmergencyTriageResponse resp = new SearchKnowledgeForEmergencyTriageResponse();
        if (request.getTopNLimit() != null && request.getTopNLimit() > 0) {
            List<KnowledgeTopNItemQueryModel> top =
                    knowledgeQueryService.listTopKnowledgeItemsForTriage(
                            StringUtils.defaultIfBlank(request.getType(), KnowledgeConstants.TYPE_SYMPTOM),
                            request.getTopNLimit());
            List<KnowledgeCandidateRowDto> rows = new ArrayList<>();
            for (KnowledgeTopNItemQueryModel item : top) {
                KnowledgeCandidateRowDto dto = new KnowledgeCandidateRowDto();
                dto.setId(item.getId());
                dto.setName(item.getName());
                dto.setPinyinCode(item.getPinyinCode());
                dto.setTriageLevel(item.getTriageLevel());
                dto.setType(item.getType());
                rows.add(dto);
            }
            resp.setTopN(rows);
            return resp;
        }
        if (request.getPageNum() == null || request.getPageSize() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "分页参数不能为空");
        }
        KnowledgeBaseItemPageQuery filter = new KnowledgeBaseItemPageQuery();
        filter.setNameHint(request.getNameHint());
        filter.setType(request.getType());
        filter.setStatus(KnowledgeConstants.STATUS_ENABLED);
        Pageable pageable = PageRequest.of(request.getPageNum(), request.getPageSize());
        Page<KnowledgeBaseItemQueryModel> page = knowledgeQueryService.listKnowledgeBaseItemsPaged(filter, pageable);
        List<KnowledgeCandidateRowDto> rows = new ArrayList<>();
        for (KnowledgeBaseItemQueryModel row : page.getContent()) {
            rows.add(emergencyTriageStationMapper.toKnowledgeCandidateRowDto(row));
        }
        PagedResult<KnowledgeCandidateRowDto> pageResult = new PagedResult<>();
        resp.setPage(PagedResultBuilder.build(pageResult, rows, page));
        return resp;
    }

    /**
     * 知识库维护：分页列表（主诉/症状，含禁用项）。
     */
    public PageEmgKnowledgeBaseItemsResponse pageEmgKnowledgeBaseItems(PageEmgKnowledgeBaseItemsRequest request) {
        if (request == null || StringUtils.isBlank(request.getType())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "知识类型不能为空");
        }
        validateEmergencyPageQuery(request.getPageNo(), request.getPageSize());
        KnowledgeBaseItemPageQuery filter = new KnowledgeBaseItemPageQuery();
        filter.setType(request.getType().trim());
        filter.setNameHint(request.getNameHint());
        filter.setStatus(request.getStatus());
        Pageable pageable =
                PageRequest.of(
                        request.springPageIndex(),
                        request.getBoundedPageSize(),
                        Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<KnowledgeBaseItemQueryModel> page = knowledgeQueryService.listKnowledgeBaseItemsPaged(filter, pageable);
        List<EmgKnowledgeBaseItemMaintenanceDto> rows = new ArrayList<>();
        for (KnowledgeBaseItemQueryModel row : page.getContent()) {
            rows.add(emergencyTriageStationMapper.toEmgKnowledgeBaseItemMaintenanceDto(row));
        }
        PageEmgKnowledgeBaseItemsResponse resp = new PageEmgKnowledgeBaseItemsResponse();
        PagedResult<EmgKnowledgeBaseItemMaintenanceDto> pageResult = new PagedResult<>();
        resp.setPage(PagedResultBuilder.build(pageResult, rows, page));
        return resp;
    }

    /**
     * 知识库维护：新建或更新条目（快捷关联建议分级 triageLevel）。
     */
    public SaveEmgKnowledgeBaseItemResponse saveEmgKnowledgeBaseItem(SaveEmgKnowledgeBaseItemRequest request) {
        if (request == null || StringUtils.isBlank(request.getName()) || request.getTriageLevel() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "名称与建议分级不能为空");
        }
        if (StringUtils.isBlank(request.getType())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "知识类型不能为空");
        }
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        String typeNorm = request.getType().trim().toUpperCase();
        String category;
        if (StringUtils.isBlank(request.getCategory())) {
            category = DEFAULT_KNOWLEDGE_CATEGORY;
        } else {
            category = request.getCategory().trim();
        }
        Integer status;
        if (request.getStatus() == null) {
            status = KnowledgeConstants.STATUS_ENABLED;
        } else {
            status = request.getStatus();
        }
        KnowledgeBaseItem saved;
        if (request.getId() == null) {
            CreateKnowledgeBaseItemCommand cmd = new CreateKnowledgeBaseItemCommand();
            cmd.setName(request.getName().trim());
            cmd.setPinyinCode(StringUtils.defaultString(request.getPinyinCode()).trim());
            cmd.setTriageLevel(request.getTriageLevel());
            cmd.setType(typeNorm);
            cmd.setCategory(category);
            int sortOrder;
            if (request.getSortOrder() != null) {
                sortOrder = request.getSortOrder();
            } else {
                sortOrder = resolveNextKnowledgeSortOrder(typeNorm);
            }
            cmd.setSortOrder(sortOrder);
            cmd.setStatus(status);
            cmd.setOperatorId(operatorId);
            saved = knowledgeDomainService.createKnowledgeBaseItem(cmd);
        } else {
            UpdateKnowledgeBaseItemCommand cmd = new UpdateKnowledgeBaseItemCommand();
            cmd.setItemId(request.getId());
            cmd.setName(request.getName().trim());
            if (request.getPinyinCode() != null) {
                cmd.setPinyinCode(request.getPinyinCode().trim());
            } else {
                cmd.setPinyinCode(null);
            }
            cmd.setTriageLevel(request.getTriageLevel());
            cmd.setType(typeNorm);
            cmd.setCategory(category);
            if (request.getSortOrder() != null) {
                cmd.setSortOrder(request.getSortOrder());
            }
            cmd.setStatus(status);
            cmd.setOperatorId(operatorId);
            saved = knowledgeDomainService.updateKnowledgeBaseItem(cmd);
        }
        SaveEmgKnowledgeBaseItemResponse resp = new SaveEmgKnowledgeBaseItemResponse();
        resp.setId(saved.getId());
        return resp;
    }

    /**
     * 知识库维护：逻辑删除。
     */
    public DeleteEmgKnowledgeBaseItemResponse deleteEmgKnowledgeBaseItem(DeleteEmgKnowledgeBaseItemRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "条目主键不能为空");
        }
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        DeleteKnowledgeBaseItemCommand cmd = new DeleteKnowledgeBaseItemCommand();
        cmd.setItemId(request.getId());
        cmd.setOperatorId(operatorId);
        knowledgeDomainService.deleteKnowledgeBaseItem(cmd);
        DeleteEmgKnowledgeBaseItemResponse resp = new DeleteEmgKnowledgeBaseItemResponse();
        resp.setDeleted(Boolean.TRUE);
        return resp;
    }

    private int resolveNextKnowledgeSortOrder(String type) {
        KnowledgeBaseItemPageQuery filter = new KnowledgeBaseItemPageQuery();
        filter.setType(type);
        Pageable pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "sortOrder"));
        Page<KnowledgeBaseItemQueryModel> page = knowledgeQueryService.listKnowledgeBaseItemsPaged(filter, pageable);
        if (page.isEmpty() || page.getContent().isEmpty()) {
            return 1;
        }
        Integer max = page.getContent().get(0).getSortOrder();
        int base;
        if (max == null) {
            base = 0;
        } else {
            base = max;
        }
        return base + 1;
    }

    /**
     * IF-EMG-14：打印腕带时间戳回写。
     *
     * @param request 就诊与打印时刻
     * @return 分诊主键
     */
    public PrintWristbandOrTriageSlipResponse printWristbandOrTriageSlip(PrintWristbandOrTriageSlipRequest request) {
        Optional<EmgTriageByEncounterQueryModel> snap =
                emgTriageQueryService.getEmgTriageByEncounterId(request.getEncounterId());
        if (snap.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "有效分诊不存在");
        }
        LocalDateTime printedAt = request.getWristbandPrintedAt();
        if (printedAt == null) {
            printedAt = LocalDateTime.now();
        }
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        SaveEmgTriageCommand cmd =
                emergencyTriageStationMapper.toSaveEmgTriageCommandForPrint(snap.get(), operatorId, printedAt);
        EmgTriage saved = emgTriageDomainService.saveEmgTriage(cmd);
        PrintWristbandOrTriageSlipResponse resp = new PrintWristbandOrTriageSlipResponse();
        resp.setEmgTriageId(saved.getId());
        resp.setWristbandPrintedAt(printedAt);
        return resp;
    }

    /**
     * IF-EMG-15：分诊挂号协同。
     *
     * @param request 模式与分诊载荷
     * @return 分诊主键
     */
    public CoordinateTriageRegistrationResponse coordinateTriageRegistration(CoordinateTriageRegistrationRequest request) {
        if (request == null || request.getTriage() == null || StringUtils.isBlank(request.getTriageRegistrationModeEnum())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "协同模式或分诊载荷不能为空");
        }
        SaveOrSubmitEmgTriageRequest triage = request.getTriage();
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        String mode = request.getTriageRegistrationModeEnum().trim();
        if (MODE_REGISTER_FIRST.equals(mode)) {
            encounterQueryService.mustGetEncounter(triage.getEncounterId());
            applyEncounterTriageTransition(triage, operatorId);
            SaveOrSubmitEmgTriageResponse saved = executeSaveOrSubmitEmgTriage(triage, operatorId);
            CoordinateTriageRegistrationResponse resp = new CoordinateTriageRegistrationResponse();
            resp.setEmgTriageId(saved.getEmgTriageId());
            return resp;
        }
        if (MODE_TRIAGE_FIRST.equals(mode) || MODE_CONCURRENT.equals(mode)) {
            SaveOrSubmitEmgTriageResponse saved = executeSaveOrSubmitEmgTriage(triage, operatorId);
            encounterQueryService.mustGetEncounter(triage.getEncounterId());
            maybeApplyTriageFirstTransitionAfterSave(triage, operatorId);
            CoordinateTriageRegistrationResponse resp = new CoordinateTriageRegistrationResponse();
            resp.setEmgTriageId(saved.getEmgTriageId());
            return resp;
        }
        throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "未知协同模式");
    }

    /**
     * IF-EMG-16：患者报道。
     *
     * @param request 幂等键
     * @return 受理标记
     */
    public ReportPatientArrivalResponse reportPatientArrival(ReportPatientArrivalRequest request) {
        encounterQueryService.mustGetEncounter(request.getEncounterId());
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        emergencyReportArrivalPort.report(request.getEncounterId(), request.getIdempotencyKey(), operatorId);
        ReportPatientArrivalResponse resp = new ReportPatientArrivalResponse();
        resp.setAccepted(Boolean.TRUE);
        return resp;
    }

    /**
     * IF-EMG-17：推送队列优先级。
     *
     * @param request 就诊标识
     * @return 投递标记
     */
    public PushQueuePriorityResponse pushQueuePriority(PushQueuePriorityRequest request) {
        Optional<EmgTriageByEncounterQueryModel> tri =
                emgTriageQueryService.getEmgTriageByEncounterId(request.getEncounterId());
        if (tri.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "有效分诊不存在");
        }
        Integer qp = tri.get().getQueuePriority();
        if (qp == null) {
            qp = 0;
        }
        queuePriorityPushPort.push(tri.get().getId(), request.getEncounterId(), qp);
        PushQueuePriorityResponse resp = new PushQueuePriorityResponse();
        resp.setDispatched(Boolean.TRUE);
        return resp;
    }

    /**
     * 为工作台列表行补齐患者姓名、科室名称、去向汇总（IF-EMG-18 / IF-EMG-10 共用）。
     */
    private void enrichEmgTriageWorkbenchRows(List<EmgTriageWorkbenchRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> patientIds = new ArrayList<>();
        List<Long> organizationIds = new ArrayList<>();
        Set<Long> locationIds = new HashSet<>();
        for (EmgTriageWorkbenchRowDto r : rows) {
            if (r.getPatientId() != null) {
                patientIds.add(r.getPatientId());
            }
            if (r.getOrganizationId() != null) {
                organizationIds.add(r.getOrganizationId());
            }
            if (r.getLocationId() != null) {
                locationIds.add(r.getLocationId());
            }
            if (r.getSecondaryLocationId() != null) {
                locationIds.add(r.getSecondaryLocationId());
            }
        }
        Map<Long, String> patientNames = patientQueryService.findPatientNamesByIds(patientIds);
        Map<Long, String> orgNames = organizationQueryService.findOrganizationNamesByIds(organizationIds);
        Map<Long, String> locNames = locationQueryService.findLocationNamesByIds(locationIds);
        for (EmgTriageWorkbenchRowDto r : rows) {
            if (StringUtils.isBlank(r.getPatientName()) && r.getPatientId() != null) {
                r.setPatientName(patientNames.get(r.getPatientId()));
            }
            if (StringUtils.isBlank(r.getOrganizationName()) && r.getOrganizationId() != null) {
                r.setOrganizationName(orgNames.get(r.getOrganizationId()));
            }
            r.setDestinationDisplay(buildWorkbenchDestinationDisplay(r, locNames));
        }
    }

    private static String buildWorkbenchDestinationDisplay(EmgTriageWorkbenchRowDto r, Map<Long, String> locNames) {
        String primary = "";
        if (r.getLocationId() != null) {
            primary = StringUtils.defaultString(locNames.get(r.getLocationId()));
        }
        if (EmgTriage.DESTINATION_MULTI.equals(r.getDestinationModeEnum()) && r.getSecondaryLocationId() != null) {
            String sec = StringUtils.defaultString(locNames.get(r.getSecondaryLocationId()));
            if (StringUtils.isAllBlank(primary, sec)) {
                return "—";
            }
            if (StringUtils.isBlank(sec)) {
                if (StringUtils.isBlank(primary)) {
                    return "—";
                }
                return primary;
            }
            if (StringUtils.isBlank(primary)) {
                return "次：" + sec;
            }
            return primary + "；次：" + sec;
        }
        if (StringUtils.isBlank(primary)) {
            return "—";
        }
        return primary;
    }

    /**
     * IF-EMG-18：工作台分页。
     *
     * @param request 过滤与分页
     * @return 分诊行分页
     */
    public PageEmgTriageWorkbenchResponse pageEmgTriageWorkbench(PageEmgTriageWorkbenchRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "请求体不能为空");
        }
        validateEmergencyPageQuery(request.getPageNo(), request.getPageSize());
        EmgTriagePageQuery query = emergencyTriageStationMapper.toEmgTriagePageQuery(request);
        Pageable pageable =
                PageRequest.of(
                        request.springPageIndex(),
                        request.getBoundedPageSize(),
                        Sort.by(Sort.Direction.DESC, "triageTime"));
        Page<EmgTriageListItemQueryModel> page =
                emgTriageQueryService.queryEmgTriageRecordsPaged(query, pageable);
        List<EmgTriageWorkbenchRowDto> rows = new ArrayList<>();
        for (EmgTriageListItemQueryModel row : page.getContent()) {
            rows.add(emergencyTriageStationMapper.toWorkbenchRowDto(row));
        }
        enrichEmgTriageWorkbenchRows(rows);
        PageEmgTriageWorkbenchResponse resp = new PageEmgTriageWorkbenchResponse();
        PagedResult<EmgTriageWorkbenchRowDto> pageResult = new PagedResult<>();
        resp.setPage(PagedResultBuilder.build(pageResult, rows, page));
        return resp;
    }

    /**
     * 按分诊主键加载完整分诊事实（与 IF-EMG-06 请求体同构），供工作站右侧编辑区回显。
     *
     * @param request 分诊主键
     * @return 详情载荷与患者姓名
     */
    public GetEmgTriageDetailForEditResponse getEmgTriageDetailForEdit(GetEmgTriageDetailForEditRequest request) {
        if (request == null || request.getEmgTriageId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "分诊主键不能为空");
        }
        var opt = emgTriageQueryService.getEmgTriageByActiveId(request.getEmgTriageId());
        if (opt.isEmpty()) {
            throw new BusinessException(
                    ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "未找到有效的分诊记录或已非有效态");
        }
        SaveOrSubmitEmgTriageRequest detail = emergencyTriageStationMapper.toSaveOrSubmitEmgTriageRequest(opt.get());
        if (detail.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "分诊记录缺少患者标识");
        }
        if (detail.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "分诊记录缺少科室标识");
        }
        String patientName = null;
        Map<Long, String> names = patientQueryService.findPatientNamesByIds(List.of(detail.getPatientId()));
        patientName = names.get(detail.getPatientId());
        GetEmgTriageDetailForEditResponse resp = new GetEmgTriageDetailForEditResponse();
        resp.setDetail(detail);
        resp.setPatientName(patientName);
        return resp;
    }

    /**
     * IF-EMG-19：群伤事件（领域未接入）。
     *
     * @param request 占位请求
     * @return 永不返回
     */
    public ManageMassCasualtyIncidentResponse manageMassCasualtyIncident(ManageMassCasualtyIncidentRequest request) {
        throw new BusinessException(
                ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "群伤事件领域服务尚未接入（GAP-EMG-AS-03）");
    }

    /**
     * IF-EMG-20：保存工作站布局。
     *
     * @param request 布局 JSON
     * @return 保存标记
     */
    public SaveWorkbenchLayoutResponse saveWorkbenchLayout(SaveWorkbenchLayoutRequest request) {
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        userWorkbenchLayoutPort.save(operatorId, request.getLayoutSchemaJson());
        SaveWorkbenchLayoutResponse resp = new SaveWorkbenchLayoutResponse();
        resp.setSaved(Boolean.TRUE);
        return resp;
    }

    /**
     * IF-EMG-21：导出或删除。
     *
     * @param request 动作与主键
     * @return 导出 URL 或作废结果
     */
    public ExportOrDeleteEmgTriageDataResponse exportOrDeleteEmgTriageData(ExportOrDeleteEmgTriageDataRequest request) {
        if (request == null || StringUtils.isBlank(request.getAction())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "动作不能为空");
        }
        ExportOrDeleteEmgTriageDataResponse resp = new ExportOrDeleteEmgTriageDataResponse();
        String action = request.getAction().trim().toUpperCase();
        if ("EXPORT".equals(action)) {
            resp.setExportFileUrl(null);
            resp.setVoided(null);
            return resp;
        }
        if ("VOID".equals(action)) {
            if (StringUtils.isBlank(request.getVoidReason())) {
                throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "作废须填写原因");
            }
            Long operatorId =
                    currentOperatorResolver.requireOperatorId(
                            ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                            "未登录或无法解析当前操作人");
            VoidEmgTriageRequest voidReq = new VoidEmgTriageRequest();
            voidReq.setEmgTriageId(request.getEmgTriageId());
            voidReq.setVoidReason(request.getVoidReason());
            boolean ok =
                    emgTriageDomainService.voidEmgTriage(
                            emergencyTriageStationMapper.toVoidEmgTriageCommand(voidReq, operatorId));
            resp.setVoided(ok);
            resp.setExportFileUrl(null);
            return resp;
        }
        throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "不支持的动作");
    }

    private SaveOrSubmitEmgTriageResponse executeSaveOrSubmitEmgTriage(SaveOrSubmitEmgTriageRequest request) {
        Long operatorId =
                currentOperatorResolver.requireOperatorId(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "未登录或无法解析当前操作人");
        return executeSaveOrSubmitEmgTriage(request, operatorId);
    }

    private SaveOrSubmitEmgTriageResponse executeSaveOrSubmitEmgTriage(
            SaveOrSubmitEmgTriageRequest request, Long operatorId) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request instanceof SubmitSecondaryEmgTriageRequest && Boolean.TRUE.equals(request.getIsStaged())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "二次分诊不支持暂存标记");
        }
        if (request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "患者标识不能为空");
        }
        if (Boolean.TRUE.equals(request.getIsStaged()) && request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "暂存须包含机构（organizationId）");
        }
        if (Boolean.TRUE.equals(request.getIsStaged())) {
            applyStagedDraftDefaults(request);
        }
        Long encounterId = request.getEncounterId();
        if (encounterId == null) {
            encounterId = resolveOrCreateEmergencyEncounter(request, operatorId);
            request.setEncounterId(encounterId);
        } else {
            encounterQueryService.mustGetEncounter(encounterId);
        }
        SaveEmgTriageCommand cmd;
        if (request instanceof SubmitSecondaryEmgTriageRequest secondary) {
            cmd = emergencyTriageStationMapper.toSaveEmgTriageCommand(secondary, operatorId);
        } else {
            cmd = emergencyTriageStationMapper.toSaveEmgTriageCommand(request, operatorId);
        }
        EmgTriage saved = emgTriageDomainService.saveEmgTriage(cmd);
        SaveOrSubmitEmgTriageResponse resp = new SaveOrSubmitEmgTriageResponse();
        resp.setEmgTriageId(saved.getId());
        resp.setEncounterId(request.getEncounterId());
        return resp;
    }

    /**
     * 暂存草稿补默认值以便写入 {@code emg_triage} 非空列（去向位置优先取机构下第一条启用位置）。
     */
    private void applyStagedDraftDefaults(SaveOrSubmitEmgTriageRequest request) {
        if (request.getTriageTime() == null) {
            request.setTriageTime(LocalDateTime.now());
        }
        if (request.getTriageLevelEnum() == null) {
            request.setTriageLevelEnum(3);
        }
        if (request.getQueuePriority() == null || Integer.valueOf(0).equals(request.getQueuePriority())) {
            request.setQueuePriority(50);
        }
        if (request.getIsGreenChannel() == null) {
            request.setIsGreenChannel(Boolean.FALSE);
        }
        if (request.getIsFreeRegister() == null) {
            request.setIsFreeRegister(Boolean.FALSE);
        }
        if (StringUtils.isBlank(request.getDestinationModeEnum())) {
            request.setDestinationModeEnum(EmgTriage.DESTINATION_SINGLE);
        }
        if (EmgTriage.DESTINATION_MULTI.equals(request.getDestinationModeEnum()) && request.getSecondaryLocationId() == null) {
            request.setDestinationModeEnum(EmgTriage.DESTINATION_SINGLE);
        }
        if (EmgTriage.ARRIVAL_EXTERNAL_TRANSFER.equals(request.getArrivalModeEnum()) && request.getTransferInOrgId() == null) {
            request.setArrivalModeEnum(null);
        }
        boolean locationUnset = request.getLocationId() == null || Long.valueOf(0L).equals(request.getLocationId());
        if (locationUnset) {
            List<Location> locs = locationQueryService.listActiveLocationsByOrganization(request.getOrganizationId());
            if (locs.isEmpty()) {
                throw new BusinessException(
                        ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                        "暂存失败：该机构下无启用中的去向位置，请在表单中选择「主去向」或维护位置主数据。");
            }
            request.setLocationId(locs.get(0).getId());
        }
        if (Boolean.TRUE.equals(request.getIsGreenChannel())) {
            Integer lvl = request.getTriageLevelEnum();
            if (lvl == null || (lvl != 1 && lvl != 2)) {
                request.setIsGreenChannel(Boolean.FALSE);
            }
        }
        if (Boolean.TRUE.equals(request.getIsGreenChannel()) && !Boolean.TRUE.equals(request.getIsFreeRegister())) {
            request.setIsFreeRegister(Boolean.TRUE);
        }
    }

    /**
     * 无就诊标识时复用未终结急诊就诊或开立新的急诊就诊（先分诊 / TRIAGE_FIRST 编排）。
     */
    private Long resolveOrCreateEmergencyEncounter(SaveOrSubmitEmgTriageRequest request, Long operatorId) {
        Optional<Long> reuse =
                encounterQueryService.findLatestActiveEmergencyEncounterId(
                        request.getPatientId(), request.getOrganizationId(), EMERGENCY_REGISTER_SERVICE_TYPE_ID);
        if (reuse.isPresent()) {
            return reuse.get();
        }
        RegisterOutpatientEncounterCommand regCmd = new RegisterOutpatientEncounterCommand();
        regCmd.setOperatorId(operatorId);
        LocalDateTime now = LocalDateTime.now();
        regCmd.setOccurredAt(now);
        RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand item =
                new RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand();
        item.setPatientId(request.getPatientId());
        item.setServiceTypeId(EMERGENCY_REGISTER_SERVICE_TYPE_ID);
        item.setOrganizationId(request.getOrganizationId());
        item.setClassEnum(EncounterClass.EMER.getCode());
        item.setStartTime(now);
        item.setRegistrarId(operatorId);
        regCmd.setItem(item);
        return encounterDomainService.registerOutpatientEncounter(regCmd);
    }

    /**
     * IF-EMG-06 公开入口在落库后补齐「先分诊」就诊状态迁移（与协同模式 TRIAGE_FIRST 后置步骤一致）。
     */
    private void maybeApplyTriageFirstTransitionAfterSave(SaveOrSubmitEmgTriageRequest request, Long operatorId) {
        if (Boolean.TRUE.equals(request.getIsStaged())) {
            return;
        }
        if (request.getEncounterId() == null) {
            return;
        }
        var detail = encounterQueryService.loadEncounterById(request.getEncounterId());
        if (!EncounterClass.EMER.getCode().equals(detail.getClassEnum())) {
            return;
        }
        if (!EncounterStatus.PLANNED.getCode().equals(detail.getStatusEnum())) {
            return;
        }
        applyEncounterTriageTransition(request, operatorId);
    }

    private void applyEncounterTriageTransition(SaveOrSubmitEmgTriageRequest triage, Long operatorId) {
        TriageEncounterCommand triageCmd = new TriageEncounterCommand();
        triageCmd.setOperatorId(operatorId);
        TriageEncounterCommand.TriageEncounterItemCommand item = new TriageEncounterCommand.TriageEncounterItemCommand();
        item.setEncounterId(triage.getEncounterId());
        item.setTriageNurseId(operatorId);
        item.setTriageLevelCode(String.valueOf(triage.getTriageLevelEnum()));
        item.setTriageTime(triage.getTriageTime());
        triageCmd.setItem(item);
        encounterDomainService.triageEncounter(triageCmd);
    }

    private List<OptionDto> buildDepartmentOptions(Long organizationId) {
        List<OptionDto> options = new ArrayList<>();
        long sort = 1;
        for (Organization dept : organizationQueryService.listActiveDepartmentsForTriageScope(organizationId)) {
            if (dept == null || dept.getId() == null) {
                continue;
            }
            long order;
            if (dept.getDisplayOrder() != null) {
                order = dept.getDisplayOrder().longValue();
            } else {
                order = sort;
            }
            String label;
            if (StringUtils.isBlank(dept.getName())) {
                label = String.valueOf(dept.getId());
            } else {
                label = dept.getName().trim();
            }
            options.add(new OptionDto(order, String.valueOf(dept.getId()), label));
            sort++;
        }
        return options;
    }

    private List<OptionDto> buildDestinationOptions(Long organizationId) {
        List<OptionDto> options = new ArrayList<>();
        List<Location> locations = locationQueryService.listActiveLocationsByOrganization(organizationId);
        for (Location loc : locations) {
            options.add(new OptionDto(loc.getId(), String.valueOf(loc.getId()), loc.getName()));
        }
        return options;
    }

    /** B.10.11：分页须 {@code pageNo}≥1、{@code pageSize}≥1（与 {@link com.openhis.health.domain.share.query.AbstractPageQuery} 语义一致）。 */
    private static void validateEmergencyPageQuery(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "页码须从 1 开始");
        }
        if (pageSize == null || pageSize < 1) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "页大小须为正整数");
        }
    }

    /**
     * 建档请求中含有效的三无临时号证件行时，同人校验易与多条「无名氏」占位冲突，故跳过 BR-PAT-014 建档同人校验。
     */
    private boolean hasActiveProvisionalTriageNo(RegisterOrUpdatePatientForEmergencyRequest request) {
        if (request.getIdentifierItems() == null || request.getIdentifierItems().isEmpty()) {
            return false;
        }
        String code = GeneralSharedConstants.PROVISIONAL_TRIAGE_NO_TYPE_CODE;
        for (PatientIdentifierItemDto item : request.getIdentifierItems()) {
            if (item == null) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(code, item.getTypeCode()) && StringUtils.isNotBlank(item.getIdentifierNo())) {
                return true;
            }
        }
        return false;
    }
}
