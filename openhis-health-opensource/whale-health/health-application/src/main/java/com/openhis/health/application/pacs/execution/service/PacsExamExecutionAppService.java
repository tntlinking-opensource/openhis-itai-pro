/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.execution.service;

import com.openhis.health.application.pacs.execution.dto.PacsExecutionRowDto;
import com.openhis.health.application.pacs.execution.mapper.PacsExecutionMapper;
import com.openhis.health.application.pacs.execution.request.CompleteExamRequest;
import com.openhis.health.application.pacs.execution.request.SearchCompletedExamRequest;
import com.openhis.health.application.pacs.execution.request.SearchCompletedOtherDeptRequest;
import com.openhis.health.application.pacs.execution.request.SearchEnteredInErrorRequest;
import com.openhis.health.application.pacs.execution.request.SearchPendingExecutionRequest;
import com.openhis.health.application.pacs.execution.response.CompleteExamResponse;
import com.openhis.health.application.pacs.execution.response.SearchCompletedExamResponse;
import com.openhis.health.application.pacs.execution.response.SearchCompletedOtherDeptResponse;
import com.openhis.health.application.pacs.execution.response.SearchEnteredInErrorResponse;
import com.openhis.health.application.pacs.execution.response.SearchPendingExecutionResponse;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.pacs.workitem.command.CompletePacsExamCommand;
import com.openhis.health.domain.pacs.workitem.entity.PacsWorkItem;
import com.openhis.health.domain.pacs.workitem.queries.PacsWorkItemSearchQuery;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemQueryModel;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemDomainService;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.whale.core.application.exception.BusinessException;
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
    private final CurrentPractitionerResolver currentPractitionerResolver;
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

    private <R extends com.openhis.whale.core.application.dto.PagedResult<PacsExecutionRowDto>> R searchPage(
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
        PageRequest pageable = PageRequest.of(request.getPageNo() - 1, request.getPageSize());
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
