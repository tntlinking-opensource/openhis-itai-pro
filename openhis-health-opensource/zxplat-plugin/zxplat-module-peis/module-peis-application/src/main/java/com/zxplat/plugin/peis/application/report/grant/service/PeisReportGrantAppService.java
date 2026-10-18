/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.grant.service;

import com.zxplat.plugin.peis.application.report.grant.dto.GrantRowDto;
import com.zxplat.plugin.peis.application.report.grant.mapper.PeisReportGrantMapper;
import com.zxplat.plugin.peis.application.report.grant.request.BatchGroupPrintRequest;
import com.zxplat.plugin.peis.application.report.grant.request.RegisterGrantRequest;
import com.zxplat.plugin.peis.application.report.grant.request.SearchPendingGrantRequest;
import com.zxplat.plugin.peis.application.report.grant.request.SignGrantRequest;
import com.zxplat.plugin.peis.application.report.grant.response.BatchGroupPrintResponse;
import com.zxplat.plugin.peis.application.report.grant.response.SearchPendingGrantResponse;
import com.zxplat.plugin.peis.application.share.PeisCurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.zxplat.domain.clinical.diagnosticreport.service.DiagnosticReportQueryService;
import com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel;
import com.zxplat.plugin.peis.domain.encounterext.service.PeisEncounterExtQueryService;
import com.zxplat.plugin.peis.domain.reportgrantext.command.MarkGrantPrintedCommand;
import com.zxplat.plugin.peis.domain.reportgrantext.command.RegisterGrantCommand;
import com.zxplat.plugin.peis.domain.reportgrantext.command.SignGrantCommand;
import com.zxplat.plugin.peis.domain.reportgrantext.queries.model.GrantRowModel;
import com.zxplat.plugin.peis.domain.reportgrantext.queries.query.GrantSearchQuery;
import com.zxplat.plugin.peis.domain.reportgrantext.service.PeisReportGrantDomainService;
import com.zxplat.plugin.peis.domain.reportgrantext.service.PeisReportGrantQueryService;
import com.zxplat.domain.share.constants.DiagnosticReportStatusConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报告发放应用服务（AS-PEIS-REP-002）：待发放列表、登记、签收、团检批量打印。
 */
@Service
@RequiredArgsConstructor
public class PeisReportGrantAppService {

    private final PeisReportGrantDomainService peisReportGrantDomainService;
    private final PeisReportGrantQueryService peisReportGrantQueryService;
    private final DiagnosticReportQueryService diagnosticReportQueryService;
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    private final PatientQueryService patientQueryService;
    private final PeisReportGrantMapper peisReportGrantMapper;
    private final PageWindowGate pageWindowGate;
    private final PeisCurrentPractitionerResolver currentPractitionerResolver;
    private final EncounterQueryService encounterQueryService;

    /**
     * 待发放列表（IF-PEIS-REP-05）。
     *
     * @param request 检索条件
     * @return 分页发放行
     */
    public SearchPendingGrantResponse searchPendingGrant(SearchPendingGrantRequest request) {
        // Gate：分页窗口
        pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT,
            "待发放列表分页参数非法");
        // Pre：领域查询
        GrantSearchQuery query = peisReportGrantMapper.toGrantSearchQuery(request);
        Page<GrantRowModel> page = peisReportGrantQueryService.searchPendingGrant(query);
        // Asm：enrich DR/患者/流水号
        List<GrantRowDto> rows = enrichGrantRows(page.getContent());
        return peisReportGrantMapper.toSearchPendingGrantResponse(page, rows);
    }

    /**
     * 发放登记（IF-PEIS-REP-05）。
     *
     * @param request 登记请求
     */
    public void registerGrant(RegisterGrantRequest request) {
        // Gate：报告主键
        if (request == null || request.getDiagnosticReportId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "报告标识不能为空");
        }
        // Gate：操作人取会话执业者（B.6.5；忽略请求体 operatorId）
        Long operatorId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "请先登录后再操作");
        // Pre：DR final（BR-REP2-01）
        DiagnosticReportModel report = diagnosticReportQueryService.mustGetById(request.getDiagnosticReportId());
        assertReportFinal(report);
        // Pre：无重复登记
        if (peisReportGrantQueryService.getByDiagnosticReportId(request.getDiagnosticReportId()).isPresent()) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_ALREADY_REGISTERED, "该报告已登记发放");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(report.getEncounterId());
        // Cmd：写 grant ext
        RegisterGrantCommand cmd = new RegisterGrantCommand();
        cmd.setDiagnosticReportId(request.getDiagnosticReportId());
        cmd.setTenantId(TenantUtils.getCurrentTenantId());
        cmd.setOrgId(encounter.getOrganizationId());
        cmd.setOperatorId(operatorId);
        peisReportGrantDomainService.registerGrant(cmd);
    }

    /**
     * 签收（IF-PEIS-REP-05）。
     *
     * @param request 签收请求
     */
    public void signGrant(SignGrantRequest request) {
        // Gate：报告主键必填
        if (request == null || request.getDiagnosticReportId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "报告标识不能为空");
        }
        // Gate：操作人取会话执业者（B.6.5）
        Long operatorId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "请先登录后再操作");
        // Pre：领域侧校验 pending/printed 态（DomainService 内抛 GRANT_NOT_PENDING）
        // Cmd：签收写库 → signed
        SignGrantCommand cmd = new SignGrantCommand();
        cmd.setDiagnosticReportId(request.getDiagnosticReportId());
        cmd.setRecipientName(request.getRecipientName());
        cmd.setOperatorId(operatorId);
        peisReportGrantDomainService.signGrant(cmd);
        // Asm：无额外出参
    }

    /**
     * 团体批量打印（IF-PEIS-REP-06；无 RenderPort，逐 DR markPrinted）。
     *
     * @param request 批量请求
     * @return 成功/失败统计
     */
    public BatchGroupPrintResponse batchGroupPrint(BatchGroupPrintRequest request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "批量打印参数不能为空");
        }
        // Gate：操作人取会话执业者（B.6.5）
        Long operatorId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "请先登录后再操作");
        // Pre：解析就诊清单
        List<Long> encounterIds = request.getEncounterIds();
        if (encounterIds == null) {
            encounterIds = new ArrayList<>();
        }
        int success = 0;
        List<Long> failed = new ArrayList<>();
        // Cmd：逐 Encounter 校验 final DR 并 markPrinted（幂等）
        for (Long encounterId : encounterIds) {
            if (encounterId == null) {
                continue;
            }
            try {
                DiagnosticReportModel report =
                    diagnosticReportQueryService.getByEncounterId(encounterId).orElse(null);
                if (report == null
                    || !DiagnosticReportStatusConstants.STATUS_FINAL.equals(report.getStatusEnum())) {
                    failed.add(encounterId);
                    continue;
                }
                MarkGrantPrintedCommand printCmd = new MarkGrantPrintedCommand();
                printCmd.setDiagnosticReportId(report.getId());
                printCmd.setOperatorId(operatorId);
                peisReportGrantDomainService.markPrinted(printCmd);
                success++;
            } catch (BusinessException ex) {
                failed.add(encounterId);
            }
        }
        // Asm：统计出参
        BatchGroupPrintResponse response = new BatchGroupPrintResponse();
        response.setSuccessCount(success);
        response.setFailedEncounterIds(failed);
        response.setMessage("团体批量打印完成（PDF 渲染待 PrintTemplate 接通）");
        return response;
    }

    /**
     * 报告邮寄（IF-PEIS-REP-07 桩）。
     *
     * @param request 签收形参请求
     */
    public void markMailed(SignGrantRequest request) {
        // Gate：报告主键
        if (request == null || request.getDiagnosticReportId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "报告标识不能为空");
        }
        // Gate：操作人取会话执业者（B.6.5）
        Long operatorId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "请先登录后再操作");
        // Pre/Cmd：P3 邮寄态写库（桩：DomainService#markMailed）
        SignGrantCommand cmd = new SignGrantCommand();
        cmd.setDiagnosticReportId(request.getDiagnosticReportId());
        cmd.setOperatorId(operatorId);
        peisReportGrantDomainService.markMailed(cmd);
        // Asm：无出参
    }

    /**
     * 为发放列表行补齐患者姓名、流水号、定稿时间（跨聚合读经各 QueryService）。
     *
     * @param models 领域发放行
     * @return 应用层 DTO 行
     */
    private List<GrantRowDto> enrichGrantRows(List<GrantRowModel> models) {
        List<GrantRowDto> rows = new ArrayList<>();
        if (models == null || models.isEmpty()) {
            return rows;
        }
        Set<Long> drIds = new HashSet<>();
        for (GrantRowModel model : models) {
            if (model.getDiagnosticReportId() != null) {
                drIds.add(model.getDiagnosticReportId());
            }
        }
        Map<Long, DiagnosticReportModel> reportById = new HashMap<>();
        Set<Long> encounterIds = new HashSet<>();
        Set<Long> patientIds = new HashSet<>();
        for (Long drId : drIds) {
            DiagnosticReportModel report = diagnosticReportQueryService.mustGetById(drId);
            reportById.put(drId, report);
            if (report.getEncounterId() != null) {
                encounterIds.add(report.getEncounterId());
            }
            if (report.getPatientId() != null) {
                patientIds.add(report.getPatientId());
            }
        }
        Map<Long, PeisEncounterExtModel> extMap = peisEncounterExtQueryService.mapByEncounterIds(encounterIds);
        Map<Long, String> patientNames = patientQueryService.findPatientNamesByIds(patientIds);
        for (GrantRowModel model : models) {
            GrantRowDto dto = new GrantRowDto();
            dto.setDiagnosticReportId(model.getDiagnosticReportId());
            dto.setGrantStatusCode(model.getGrantStatusCode());
            DiagnosticReportModel report = reportById.get(model.getDiagnosticReportId());
            if (report != null) {
                dto.setEncounterId(report.getEncounterId());
                dto.setReportFinalTime(report.getFinalizedAt());
                if (report.getPatientId() != null) {
                    dto.setPatientName(patientNames.get(report.getPatientId()));
                }
                if (report.getEncounterId() != null) {
                    PeisEncounterExtModel ext = extMap.get(report.getEncounterId());
                    if (ext != null) {
                        dto.setBusNo(ext.getBusNo());
                    }
                }
            }
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 校验总检报告已 final（BR-REP2-01）。
     *
     * @param report 报告读模型
     */
    private void assertReportFinal(DiagnosticReportModel report) {
        if (!DiagnosticReportStatusConstants.STATUS_FINAL.equals(report.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.REPORT_NOT_FINAL, "仅已定稿报告可发放");
        }
    }

}
