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
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 总检诊台应用服务（AS-PEIS-MAS-001）：待总检列表、汇总、综述草稿、总检完成。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：仅 PE 就诊；分页窗口合法；分科完成度（MAS-04）。</li>
 *   <li>Pre：ProgressQuery 待总检；Observation 汇总；DR 非 final 草稿门禁。</li>
 *   <li>Cmd：DR draft/finalize → catalog index upsert → Encounter finish。</li>
 *   <li>Asm：经 {@link PeisMasterReviewMapper} 装配；发布总检完成事件。</li>
 * </ul>
 *
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
    /** 分科完成度校验。 */
    private final PeisServiceRequestExtQueryService peisServiceRequestExtQueryService;
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
        // Asm：按 SR 分组
        List<MasterObservationRowDto> obsRows = peisMasterReviewMapper.toObservationRows(observations);
        List<DeptSummarySectionDto> sections = groupByServiceRequest(obsRows);
        return peisMasterReviewMapper.toMasterSummaryResponse(
            encounter.getId(), encounter.getPatientId(), reportOpt, sections);
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

        // Pre：分科完成度（BR-MAS-01）
        assertDeptComplete(encounter.getId());

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

    private void assertDeptComplete(Long encounterId) {
        List<PeisServiceRequestExtModel> exts =
            peisServiceRequestExtQueryService.listByEncounterId(encounterId);
        if (exts == null || exts.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.DEPT_NOT_COMPLETE, "尚无分科项目，不可完成总检");
        }
        for (PeisServiceRequestExtModel ext : exts) {
            String exec = ext.getExecStatusEnum();
            boolean done = PeisExecStatusConstants.CHECKED.equals(exec)
                || PeisExecStatusConstants.ABANDONED.equals(exec);
            if (!done) {
                throw new BusinessException(
                    ErrorCodeConstants.DEPT_NOT_COMPLETE, "分科未全部完成，不可完成总检");
            }
        }
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
}
