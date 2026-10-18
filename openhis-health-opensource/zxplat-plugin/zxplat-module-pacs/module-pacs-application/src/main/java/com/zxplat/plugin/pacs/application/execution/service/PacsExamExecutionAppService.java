/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.execution.service;

import com.zxplat.plugin.pacs.application.execution.dto.PacsExecutionRowDto;
import com.zxplat.plugin.pacs.application.execution.mapper.PacsExecutionMapper;
import com.zxplat.plugin.pacs.application.execution.request.CompleteExamRequest;
import com.zxplat.plugin.pacs.application.execution.request.SearchCompletedExamRequest;
import com.zxplat.plugin.pacs.application.execution.request.SearchCompletedOtherDeptRequest;
import com.zxplat.plugin.pacs.application.execution.request.SearchEnteredInErrorRequest;
import com.zxplat.plugin.pacs.application.execution.request.SearchPendingExecutionRequest;
import com.zxplat.plugin.pacs.application.execution.response.CompleteExamResponse;
import com.zxplat.plugin.pacs.application.execution.response.SearchCompletedExamResponse;
import com.zxplat.plugin.pacs.application.execution.response.SearchCompletedOtherDeptResponse;
import com.zxplat.plugin.pacs.application.execution.response.SearchEnteredInErrorResponse;
import com.zxplat.plugin.pacs.application.execution.response.SearchPendingExecutionResponse;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.plugin.pacs.application.share.PacsCurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.plugin.pacs.domain.workitem.command.CompletePacsExamCommand;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import com.zxplat.plugin.pacs.domain.workitem.queries.PacsWorkItemSearchQuery;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemDomainService;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.paging.AbstractPageQuery;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 检查执行工作台（AS-PACS-EXE-001）。
 *
 * <p>接入：{@code POST /api/app/pacsExamExecution/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsExamExecutionAppService {

    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final PacsWorkItemDomainService pacsWorkItemDomainService;
    private final PatientQueryService patientQueryService;
    private final PacsCurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final PacsExecutionMapper pacsExecutionMapper;

    /**
     * IF-PACS-EXE-01：待摄列表。
     *
     * @param request 分页过滤
     * @return 分页行
     */
    public SearchPendingExecutionResponse searchPendingExecution(SearchPendingExecutionRequest request) {
        return this.searchPage(
            request,
            request.getOrganizationId(),
            null,
            request.getModalityCode(),
            pacsWorkItemQueryService::searchPendingExecution,
            new SearchPendingExecutionResponse());
    }

    /**
     * IF-PACS-EXE-02：已检列表。
     *
     * @param request 分页过滤
     * @return 分页行
     */
    public SearchCompletedExamResponse searchCompleted(SearchCompletedExamRequest request) {
        return this.searchPage(
            request,
            request.getOrganizationId(),
            null,
            request.getModalityCode(),
            pacsWorkItemQueryService::searchCompleted,
            new SearchCompletedExamResponse());
    }

    /**
     * IF-PACS-EXE-03：他科已检列表。
     *
     * @param request 须含 excludeOrganizationId
     * @return 分页行
     */
    public SearchCompletedOtherDeptResponse searchCompletedOtherDept(SearchCompletedOtherDeptRequest request) {
        // Gate：排除科室必填（当前登录科室）
        if (request.getExcludeOrganizationId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "排除科室不能为空");
        }
        return this.searchPage(
            request,
            null,
            request.getExcludeOrganizationId(),
            request.getModalityCode(),
            pacsWorkItemQueryService::searchCompletedOtherDept,
            new SearchCompletedOtherDeptResponse());
    }

    /**
     * IF-PACS-EXE-04：已作废列表。
     *
     * @param request 分页过滤
     * @return 分页行
     */
    public SearchEnteredInErrorResponse searchEnteredInError(SearchEnteredInErrorRequest request) {
        return this.searchPage(
            request,
            request.getOrganizationId(),
            null,
            request.getModalityCode(),
            pacsWorkItemQueryService::searchEnteredInError,
            new SearchEnteredInErrorResponse());
    }

    /**
     * IF-PACS-EXE-05：确认检查完成。
     *
     * @param request 完成入参
     * @return 完成结果
     */
    @Transactional
    public CompleteExamResponse completeExam(CompleteExamRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：工单迁入已检（须已有检查号）
        CompletePacsExamCommand cmd = new CompletePacsExamCommand();
        cmd.setWorkItemId(request.getWorkItemId());
        cmd.setPerformedTime(request.getPerformedTime());
        cmd.setPerformerPractitionerId(request.getPerformerPractitionerId());
        cmd.setDeviceId(request.getDeviceId());
        cmd.setOperatorId(operatorId);
        PacsWorkItem saved = pacsWorkItemDomainService.completeExam(cmd);
        // Asm：完成结果
        return pacsExecutionMapper.toCompleteResponse(saved);
    }

    private <R extends com.zxplat.framework.core.application.dto.PagedResult<PacsExecutionRowDto>> R searchPage(
        AbstractPageQuery request,
        Long organizationId,
        Long excludeOrganizationId,
        String modalityCode,
        BiFunction<PacsWorkItemSearchQuery, PageRequest, Page<PacsWorkItemQueryModel>> searcher,
        R response) {
        // Gate：分页窗口须合法
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "分页参数非法");
        PacsWorkItemSearchQuery query = new PacsWorkItemSearchQuery();
        query.setOrganizationId(organizationId);
        query.setExcludeOrganizationId(excludeOrganizationId);
        query.setModalityCode(modalityCode);
        if (request != null) {
            query.setDateFrom(request.getStartDate());
            query.setDateTo(request.getEndDate());
        }
        PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        // Pre：按条件分页查工单
        Page<PacsWorkItemQueryModel> page = searcher.apply(query, pageable);
        List<Long> patientIds = new ArrayList<>();
        for (PacsWorkItemQueryModel row : page.getContent()) {
            if (row != null && row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
        }
        // Asm：批量姓名
        Map<Long, String> names = patientQueryService.findPatientNamesByIds(patientIds);
        List<PacsExecutionRowDto> records = pacsExecutionMapper.toRowDtos(page.getContent(), names);
        return PagedResultBuilder.build(response, records, page);
    }
}
