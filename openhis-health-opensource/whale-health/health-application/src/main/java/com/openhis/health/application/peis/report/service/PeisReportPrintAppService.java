/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.service;

import com.openhis.health.application.peis.history.dto.ExamHistoryRowDto;
import com.openhis.health.application.peis.history.request.ListPatientExamHistoryRequest;
import com.openhis.health.application.peis.history.response.ListPatientExamHistoryResponse;
import com.openhis.health.application.peis.history.service.PeisExamHistoryAppService;
import com.openhis.health.application.peis.report.dto.GradReportRowDto;
import com.openhis.health.application.peis.report.dto.PeisReportPrintSlipDto;
import com.openhis.health.application.peis.report.mapper.PeisReportPrintMapper;
import com.openhis.health.application.peis.report.request.ConfirmPrintRequest;
import com.openhis.health.application.peis.report.request.ListGradReportsRequest;
import com.openhis.health.application.peis.report.request.LoadReportPrintSlipRequest;
import com.openhis.health.application.peis.report.request.PreviewReportRequest;
import com.openhis.health.application.peis.report.request.PrintGradCombinedRequest;
import com.openhis.health.application.peis.report.request.SearchPrintableRequest;
import com.openhis.health.application.peis.report.response.ConfirmPrintResponse;
import com.openhis.health.application.peis.report.response.ListGradReportsResponse;
import com.openhis.health.application.peis.report.response.PreviewReportResponse;
import com.openhis.health.application.peis.report.response.PrintGradCombinedResponse;
import com.openhis.health.application.peis.report.response.SearchPrintableResponse;
import com.openhis.health.application.peis.report.support.PeisReportPrintSlipAssembler;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.openhis.health.domain.clinical.diagnosticreport.service.DiagnosticReportQueryService;
import com.openhis.health.domain.peis.encounterprogress.queries.PeisEncounterProgressQuery;
import com.openhis.health.domain.peis.encounterprogress.queries.model.PrintableReportRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.query.ReportPrintQuery;
import com.openhis.health.domain.share.constants.DiagnosticReportStatusConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.events.custom.peisreportprint.PeisReportPrintedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 报告打印应用服务（AS-PEIS-REP-001）：可打印列表、预览、确认打印；含 REP-05 毕业合打。
 *
 * <p>真出纸走 PrintTemplate/OpenHIS.Win（preparePrint → hiprint → printJob/confirmPrint）；
 * 本服务提供 slip 数据与业务打印事件，不做服务端 PDF 渲染。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class PeisReportPrintAppService {
    private final PeisEncounterProgressQuery peisEncounterProgressQuery;
    private final DiagnosticReportQueryService diagnosticReportQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PeisExamHistoryAppService peisExamHistoryAppService;
    private final PeisReportPrintMapper peisReportPrintMapper;
    private final PeisReportPrintSlipAssembler peisReportPrintSlipAssembler;
    private final PageWindowGate pageWindowGate;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 可打印报告列表（IF-PEIS-REP-01）。
     */
    public SearchPrintableResponse searchPrintable(SearchPrintableRequest request) {
        pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT,
            "可打印列表分页参数非法");
        ReportPrintQuery query = peisReportPrintMapper.toReportPrintQuery(request);
        Page<PrintableReportRowModel> page = peisEncounterProgressQuery.searchPrintableReports(query, null);
        return peisReportPrintMapper.toSearchPrintableResponse(
            page, peisReportPrintMapper.toPrintableRows(page.getContent()));
    }

    /**
     * 加载体检报告打印 slip（IF-PEIS-REP-02 扩展；真打印前拉数）。
     */
    public PeisReportPrintSlipDto loadReportPrintSlip(@Valid LoadReportPrintSlipRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        return peisReportPrintSlipAssembler.assemble(request.getEncounterId());
    }

    /**
     * 预览报告（IF-PEIS-REP-02）。
     */
    public PreviewReportResponse previewReport(@Valid PreviewReportRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        PeisReportPrintSlipDto slip = peisReportPrintSlipAssembler.assemble(request.getEncounterId());
        PreviewReportResponse response = new PreviewReportResponse();
        response.setEncounterId(slip.getEncounterId());
        response.setDiagnosticReportId(slip.getDiagnosticReportId());
        response.setSummaryText(slip.getSummaryText());
        response.setConclusionText(slip.getConclusionText());
        response.setScenarioCode(slip.getScenarioCode());
        response.setHospitalName(slip.getHospitalName());
        response.setPatientName(slip.getPatientName());
        response.setGenderEnumText(slip.getGenderEnumText());
        response.setAgeText(slip.getAgeText());
        response.setBusNo(slip.getBusNo());
        response.setCheckDateText(slip.getCheckDateText());
        response.setObservationLines(slip.getObservationLines());
        response.setPreviewHint("内容预览；出纸走 PrintTemplate/OpenHIS.Win");
        return response;
    }

    /**
     * 确认打印（IF-PEIS-REP-03）。
     */
    public ConfirmPrintResponse confirmPrint(@Valid ConfirmPrintRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Long operatorId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "请先登录后再确认打印");
        DiagnosticReportModel report = requireFinalReport(request.getEncounterId());
        domainEventPublisher.publish(new PeisReportPrintedEvent(
            request.getEncounterId(), report.getId(), operatorId));
        ConfirmPrintResponse response = new ConfirmPrintResponse();
        response.setAccepted(Boolean.TRUE);
        response.setDiagnosticReportId(report.getId());
        response.setMessage("业务侧已记录打印事件（版式经 PrintTemplate）");
        return response;
    }

    /**
     * IF-PEIS-REP-05：按学号/患者列历年 PE 体检（毕业场景；复用体检历史查询）。
     */
    public ListGradReportsResponse listGradReportsByStudentNo(@Valid ListGradReportsRequest request) {
        ListPatientExamHistoryRequest historyRequest = new ListPatientExamHistoryRequest();
        historyRequest.setPatientId(request.getPatientId());
        historyRequest.setStudentNo(request.getStudentNo());
        ListPatientExamHistoryResponse history = peisExamHistoryAppService.listPatientExamHistory(historyRequest);
        ListGradReportsResponse response = new ListGradReportsResponse();
        response.setPatientId(history.getPatientId());
        response.setStudentNo(history.getStudentNo() != null ? history.getStudentNo() : request.getStudentNo());
        List<GradReportRowDto> rows = new ArrayList<>();
        for (ExamHistoryRowDto src : history.getRows()) {
            GradReportRowDto row = new GradReportRowDto();
            row.setEncounterId(src.getEncounterId());
            row.setBusNo(src.getBusNo());
            row.setPackageActivityId(src.getPackageActivityId());
            row.setGroupFlag(src.getGroupFlag());
            row.setStatusEnum(src.getStatusEnum());
            row.setCreatedAt(src.getCreatedAt());
            row.setExamKind(src.getExamKind());
            row.setPackageName(src.getPackageName());
            row.setCalendarYear(src.getCalendarYear());
            row.setFinalReport(src.getFinalReport());
            row.setDiagnosticReportId(src.getDiagnosticReportId());
            rows.add(row);
        }
        response.setRows(rows);
        return response;
    }

    /**
     * IF-PEIS-REP-05：毕业合打受理（实际出纸由前端逐份 printPeisReport）。
     */
    public PrintGradCombinedResponse printGradCombined(@Valid PrintGradCombinedRequest request) {
        if (request.getEncounterIds() == null || request.getEncounterIds().isEmpty()) {
            throw new ValidationException("请选择要合打的历年体检");
        }
        PrintGradCombinedResponse response = new PrintGradCombinedResponse();
        response.setEncounterIds(request.getEncounterIds());
        response.setMessage("已接收合打请求；请前端对选中就诊逐份调用 printPeisReport（peis-report-print）");
        response.setAccepted(Boolean.TRUE);
        return response;
    }

    private DiagnosticReportModel requireFinalReport(Long encounterId) {
        encounterQueryService.requireEncounterEntityOfClass(
            encounterId,
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT,
            "仅体检就诊可打印报告");
        Optional<DiagnosticReportModel> reportOpt = diagnosticReportQueryService.getByEncounterId(encounterId);
        if (reportOpt.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.REPORT_NOT_FINAL, "总检报告不存在或未定型");
        }
        DiagnosticReportModel report = reportOpt.get();
        if (!DiagnosticReportStatusConstants.STATUS_FINAL.equals(report.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.REPORT_NOT_FINAL, "总检报告未定稿，不可打印");
        }
        return report;
    }
}
