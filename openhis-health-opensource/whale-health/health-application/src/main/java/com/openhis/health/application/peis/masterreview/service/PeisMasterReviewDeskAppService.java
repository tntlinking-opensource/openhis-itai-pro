/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.masterreview.service;

import com.openhis.health.application.peis.masterreview.dto.DeptSummarySectionDto;
import com.openhis.health.application.peis.masterreview.dto.MasterObservationRowDto;
import com.openhis.health.application.peis.masterreview.mapper.PeisMasterReviewMapper;
import com.openhis.health.application.peis.masterreview.request.CompleteMasterReviewRequest;
import com.openhis.health.application.peis.masterreview.request.LoadSummaryRequest;
import com.openhis.health.application.peis.masterreview.request.SaveMasterDraftRequest;
import com.openhis.health.application.peis.masterreview.request.SearchPendingMasterRequest;
import com.openhis.health.application.peis.masterreview.response.CompleteMasterReviewResponse;
import com.openhis.health.application.peis.masterreview.response.MasterSummaryResponse;
import com.openhis.health.application.peis.masterreview.response.SaveMasterDraftResponse;
import com.openhis.health.application.peis.masterreview.response.SearchPendingMasterResponse;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.encounter.command.FinishEncounterCommand;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.clinical.diagnosticreport.command.CreateForEncounterCommand;
import com.openhis.health.domain.clinical.diagnosticreport.command.FinalizeMasterCommand;
import com.openhis.health.domain.clinical.diagnosticreport.command.SaveMasterDraftCommand;
import com.openhis.health.domain.clinical.diagnosticreport.entity.DiagnosticReport;
import com.openhis.health.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.openhis.health.domain.clinical.diagnosticreport.service.DiagnosticReportDomainService;
import com.openhis.health.domain.clinical.diagnosticreport.service.DiagnosticReportQueryService;
import com.openhis.health.domain.clinical.diagnosticreportindex.command.UpsertDrIndexFromPeisCommand;
import com.openhis.health.domain.clinical.diagnosticreportindex.service.DiagnosticReportIndexUpsertService;
import com.openhis.health.domain.clinical.observation.queries.model.ObservationModel;
import com.openhis.health.domain.clinical.observation.service.ObservationQueryService;
import com.openhis.health.domain.clinical.observationdefinition.queries.model.ObservationDefinitionModel;
import com.openhis.health.domain.clinical.observationdefinition.service.ObservationDefinitionQueryService;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.encounterext.service.PeisEncounterExtQueryService;
import com.openhis.health.domain.peis.encounterprogress.command.SignalProgressChangedCommand;
import com.openhis.health.domain.peis.encounterprogress.queries.PeisEncounterProgressQuery;
import com.openhis.health.domain.peis.encounterprogress.queries.model.MasterReviewRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.query.MasterReviewQuery;
import com.openhis.health.domain.peis.servicerequestext.queries.model.PeisServiceRequestExtModel;
import com.openhis.health.domain.peis.servicerequestext.service.PeisServiceRequestExtQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisExecStatusConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.events.custom.peismasterreview.PeisMasterReviewCompletedEvent;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;
import java.time.Period;

/**
 * 总检诊台应用服务（AS-PEIS-MAS-001）：待总检列表、汇总、综述草稿、总检完成。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：仅 PE 就诊；分页窗口合法。</li>
 *   <li>Pre：ProgressQuery 待总检；Observation 汇总；DR 非 final 草稿门禁。</li>
 *   <li>Cmd：DR draft/finalize → catalog index upsert → Encounter finish。</li>
 *   <li>Asm：经 {@link PeisMasterReviewMapper} 装配；发布总检完成事件。</li>
 * </ul>
 *
 * <p>说明：待总检列表与总检完成均不强制分科项目全部已检/弃检；完成比仅作展示。</p>
 * <p>接入：{@code AppServiceControllerRegistrar} → {@code POST /api/app/peisMasterReviewDesk/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisMasterReviewDeskAppService {

    /** 体检进度只读（MAS-01）。 */
    private final PeisEncounterProgressQuery peisEncounterProgressQuery;
    /** 观察实例读侧（MAS-02）。 */
    private final ObservationQueryService observationQueryService;
    /** 总检报告写侧。 */
    private final DiagnosticReportDomainService diagnosticReportDomainService;
    /** 总检报告读侧。 */
    private final DiagnosticReportQueryService diagnosticReportQueryService;
    /** 报告读侧索引 upsert。 */
    private final DiagnosticReportIndexUpsertService diagnosticReportIndexUpsertService;
    /** 就诊写侧（结束就诊）。 */
    private final EncounterDomainService encounterDomainService;
    /** 就诊读侧（PE 门禁）。 */
    private final EncounterQueryService encounterQueryService;
    /** 服务申请执行扩展读侧（分科完成比展示）。 */
    private final PeisServiceRequestExtQueryService peisServiceRequestExtQueryService;
    /** PE 就诊扩展读侧。 */
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    /** 患者读侧。 */
    private final PatientQueryService patientQueryService;
    /** 观察定义读侧。 */
    private final ObservationDefinitionQueryService observationDefinitionQueryService;
    /** 诊疗项目读侧。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 写路径操作人。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 分页窗口门禁。 */
    private final PageWindowGate pageWindowGate;
    /** 请求/响应映射。 */
    private final PeisMasterReviewMapper peisMasterReviewMapper;
    /** 领域事件发布。 */
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 待总检列表（IF-PEIS-MAS-01）。
     *
     * @param request 检索条件
     * @return 分页待总检行
     */
    public SearchPendingMasterResponse searchPendingMaster(SearchPendingMasterRequest request) {
        // Gate：分页窗口合法
        pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT,
            "待总检列表分页参数非法");
        // Pre：HTTP 请求转领域查询
        MasterReviewQuery query = peisMasterReviewMapper.toMasterReviewQuery(request);
        // Cmd：经 ProgressQuery 只读 join
        Page<MasterReviewRowModel> page = peisEncounterProgressQuery.searchPendingMasterReview(query);
        // Asm：映射出参
        return peisMasterReviewMapper.toSearchPendingMasterResponse(
            page, peisMasterReviewMapper.toPendingRows(page.getContent()));
    }

    /**
     * 汇总分科结果（IF-PEIS-MAS-02）。
     *
     * @param request 汇总请求
     * @return 汇总响应
     */
    public MasterSummaryResponse loadSummary(LoadSummaryRequest request) {
        // Gate：PE 就诊存在
        Long encounterId = null;
        if (request != null) {
            encounterId = request.getEncounterId();
        }
        Encounter encounter = requirePeEncounter(encounterId);
        // Pre：读 Observation + 可选 DR
        List<ObservationModel> observations =
            observationQueryService.listByEncounterId(encounter.getId());
        Optional<DiagnosticReportModel> reportOpt =
            diagnosticReportQueryService.getByEncounterId(encounter.getId());
        Map<Long, String> definitionDisplay = buildDefinitionDisplayMap(observations);
        List<MasterObservationRowDto> obsRows =
            peisMasterReviewMapper.toObservationRows(observations, definitionDisplay);
        List<DeptSummarySectionDto> sections = enrichSections(
            groupByServiceRequest(obsRows), encounter.getId());
        MasterSummaryResponse response = peisMasterReviewMapper.toMasterSummaryResponse(
            encounter.getId(), encounter.getPatientId(), reportOpt, sections);
        enrichPatientContext(response, encounter);
        response.setDeptCompleteRatio(calcDeptCompleteRatio(encounter.getId()));
        return response;
    }

    /**
     * 保存总检综述草稿（IF-PEIS-MAS-03）。
     *
     * @param request 草稿请求
     * @return 保存结果
     */
    public SaveMasterDraftResponse saveMasterDraft(SaveMasterDraftRequest request) {
        // Gate：PE 就诊 + 结论文本
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (StringUtils.isEmpty(request.getConclusionText())
            && StringUtils.isEmpty(request.getSummaryText())) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "综述与结论不能同时为空");
        }
        Encounter encounter = requirePeEncounter(request.getEncounterId());
        Long operatorId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "请先登录后再保存总检综述");

        // Pre：确保报告行存在
        CreateForEncounterCommand createCmd = new CreateForEncounterCommand();
        createCmd.setEncounterId(encounter.getId());
        createCmd.setPatientId(encounter.getPatientId());
        createCmd.setTenantId(encounter.getTenantId());
        createCmd.setOrgId(encounter.getOrganizationId());
        createCmd.setOperatorId(operatorId);
        diagnosticReportDomainService.createOrGetForEncounter(createCmd);

        // Cmd：写草稿
        SaveMasterDraftCommand draftCmd = new SaveMasterDraftCommand();
        draftCmd.setEncounterId(encounter.getId());
        draftCmd.setSummaryText(request.getSummaryText());
        draftCmd.setConclusionText(request.getConclusionText());
        draftCmd.setOperatorId(operatorId);
        DiagnosticReport saved = diagnosticReportDomainService.saveMasterDraft(draftCmd);

        // Asm
        SaveMasterDraftResponse response = new SaveMasterDraftResponse();
        response.setDiagnosticReportId(saved.getId());
        response.setStatusEnum(saved.getStatusEnum());
        return response;
    }

    /**
     * 总检完成（IF-PEIS-MAS-04）。
     *
     * <p>写库顺序：cln_diagnostic_report → catalog index → adm_encounter。</p>
     *
     * @param request 完成请求
     * @return 完成结果
     */
    public CompleteMasterReviewResponse completeMasterReview(CompleteMasterReviewRequest request) {
        // Gate：PE 就诊
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Encounter encounter = requirePeEncounter(request.getEncounterId());
        Long operatorId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "请先登录后再完成总检");

        // Cmd-1：定稿 DR
        FinalizeMasterCommand finalizeCmd = new FinalizeMasterCommand();
        finalizeCmd.setEncounterId(encounter.getId());
        finalizeCmd.setOperatorId(operatorId);
        DiagnosticReport report = diagnosticReportDomainService.finalizeMaster(finalizeCmd);

        // Cmd-2：upsert 读侧索引（module=PEIS）
        UpsertDrIndexFromPeisCommand indexCmd = new UpsertDrIndexFromPeisCommand();
        indexCmd.setSourceReportId(report.getId());
        indexCmd.setEncounterId(encounter.getId());
        indexCmd.setPatientId(encounter.getPatientId());
        indexCmd.setTenantId(encounter.getTenantId());
        indexCmd.setOrgId(encounter.getOrganizationId());
        indexCmd.setTitle("体检总检报告");
        diagnosticReportIndexUpsertService.upsertFromPeis(indexCmd);

        // Cmd-3：结束就诊
        FinishEncounterCommand finishCmd = new FinishEncounterCommand();
        finishCmd.setEncounterId(encounter.getId());
        finishCmd.setOperatorId(operatorId);
        encounterDomainService.finishEncounter(finishCmd);

        // Asm：进度信号 + 总检完成事件
        SignalProgressChangedCommand signal = new SignalProgressChangedCommand();
        signal.setEncounterId(encounter.getId());
        signal.setProgressCode("master_completed");
        signal.setTriggerSource("MAS-04");
        peisEncounterProgressQuery.signalProgressChanged(signal);
        domainEventPublisher.publish(new PeisMasterReviewCompletedEvent(
            encounter.getId(), encounter.getPatientId(), report.getId()));

        CompleteMasterReviewResponse response = new CompleteMasterReviewResponse();
        response.setEncounterId(encounter.getId());
        response.setDiagnosticReportId(report.getId());
        response.setStatusEnum(report.getStatusEnum());
        return response;
    }

    private Encounter requirePeEncounter(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        return encounterQueryService.requireEncounterEntityOfClass(
            encounterId,
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT,
            "仅体检就诊可进入总检诊台");
    }

    private List<DeptSummarySectionDto> groupByServiceRequest(List<MasterObservationRowDto> obsRows) {
        Map<Long, DeptSummarySectionDto> sectionMap = new LinkedHashMap<>();
        List<MasterObservationRowDto> ungrouped = new ArrayList<>();
        if (obsRows != null) {
            for (MasterObservationRowDto row : obsRows) {
                Long srId = row.getServiceRequestId();
                if (srId == null) {
                    ungrouped.add(row);
                    continue;
                }
                DeptSummarySectionDto section = sectionMap.get(srId);
                if (section == null) {
                    section = new DeptSummarySectionDto();
                    section.setServiceRequestId(srId);
                    sectionMap.put(srId, section);
                }
                section.getObservations().add(row);
            }
        }
        List<DeptSummarySectionDto> sections = new ArrayList<>(sectionMap.values());
        if (!ungrouped.isEmpty()) {
            DeptSummarySectionDto orphan = new DeptSummarySectionDto();
            orphan.setServiceRequestId(null);
            orphan.setObservations(ungrouped);
            sections.add(orphan);
        }
        return sections;
    }

    private Map<Long, String> buildDefinitionDisplayMap(List<ObservationModel> observations) {
        Map<Long, String> displayMap = new HashMap<>();
        if (observations == null || observations.isEmpty()) {
            return displayMap;
        }
        Set<Long> definitionIds = new HashSet<>();
        for (ObservationModel model : observations) {
            if (model != null && model.getDefinitionId() != null) {
                definitionIds.add(model.getDefinitionId());
            }
        }
        if (definitionIds.isEmpty()) {
            return displayMap;
        }
        for (ObservationDefinitionModel od : observationDefinitionQueryService.listByIds(definitionIds)) {
            if (od != null && od.getId() != null) {
                displayMap.put(od.getId(), od.getDisplay());
            }
        }
        return displayMap;
    }

    private List<DeptSummarySectionDto> enrichSections(
        List<DeptSummarySectionDto> sections, Long encounterId) {
        if (sections == null || sections.isEmpty()) {
            return sections;
        }
        List<PeisServiceRequestExtModel> exts =
            peisServiceRequestExtQueryService.listByEncounterId(encounterId);
        Map<Long, PeisServiceRequestExtModel> extBySr = new HashMap<>();
        Set<Long> activityIds = new HashSet<>();
        if (exts != null) {
            for (PeisServiceRequestExtModel ext : exts) {
                if (ext == null || ext.getServiceRequestId() == null) {
                    continue;
                }
                extBySr.put(ext.getServiceRequestId(), ext);
                if (ext.getActivityId() != null) {
                    activityIds.add(ext.getActivityId());
                }
            }
        }
        Map<Long, String> activityNameMap = new HashMap<>();
        if (!activityIds.isEmpty()) {
            for (ActivityDefinition ad : activityDefinitionQueryService.findAllByIds(activityIds)) {
                if (ad != null && ad.getId() != null) {
                    activityNameMap.put(ad.getId(), ad.getName());
                }
            }
        }
        for (DeptSummarySectionDto section : sections) {
            if (section == null || section.getServiceRequestId() == null) {
                continue;
            }
            PeisServiceRequestExtModel ext = extBySr.get(section.getServiceRequestId());
            if (ext != null) {
                section.setExecStatusEnum(ext.getExecStatusEnum());
                if (ext.getActivityId() != null) {
                    section.setActivityDisplay(activityNameMap.get(ext.getActivityId()));
                }
            }
        }
        return sections;
    }

    private void enrichPatientContext(MasterSummaryResponse response, Encounter encounter) {
        if (response == null || encounter == null) {
            return;
        }
        Optional<PeisEncounterExtModel> peeOpt =
            peisEncounterExtQueryService.findOptionalByEncounterId(encounter.getId());
        peeOpt.ifPresent(pee -> {
            response.setBusNo(pee.getBusNo());
            response.setGroupFlag(pee.getGroupFlag());
        });
        if (encounter.getPatientId() != null) {
            patientQueryService.findOptionalPatientEntityById(encounter.getPatientId()).ifPresent(patient -> {
                response.setPatientName(patient.getName());
                response.setGenderEnum(patient.getGenderEnum());
                response.setPatientAge(calcPatientAge(patient.getBirthDate()));
            });
        }
    }

    private Integer calcPatientAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private String calcDeptCompleteRatio(Long encounterId) {
        List<PeisServiceRequestExtModel> exts =
            peisServiceRequestExtQueryService.listByEncounterId(encounterId);
        if (exts == null || exts.isEmpty()) {
            return "0/0";
        }
        int total = exts.size();
        int done = 0;
        for (PeisServiceRequestExtModel ext : exts) {
            String exec = ext.getExecStatusEnum();
            if (PeisExecStatusConstants.CHECKED.equals(exec)
                || PeisExecStatusConstants.ABANDONED.equals(exec)) {
                done += 1;
            }
        }
        return done + "/" + total;
    }
}
