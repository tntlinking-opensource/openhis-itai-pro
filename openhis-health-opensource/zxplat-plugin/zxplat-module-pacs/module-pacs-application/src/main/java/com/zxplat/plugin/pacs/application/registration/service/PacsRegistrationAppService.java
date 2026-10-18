/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.service;

import com.zxplat.plugin.pacs.application.registration.dto.PacsRegistrationRowDto;
import com.zxplat.plugin.pacs.application.registration.dto.PacsRegistrationSlipPrintDto;
import com.zxplat.plugin.pacs.application.registration.mapper.PacsRegistrationMapper;
import com.zxplat.plugin.pacs.application.registration.request.CancelRegistrationRequest;
import com.zxplat.plugin.pacs.application.registration.request.CreateLocalRegistrationRequest;
import com.zxplat.plugin.pacs.application.registration.request.GetRegistrationDetailRequest;
import com.zxplat.plugin.pacs.application.registration.request.LoadRegistrationSlipPrintRequest;
import com.zxplat.plugin.pacs.application.registration.request.SaveRegistrationRequest;
import com.zxplat.plugin.pacs.application.registration.request.SearchPendingRegistrationRequest;
import com.zxplat.plugin.pacs.application.registration.response.CancelRegistrationResponse;
import com.zxplat.plugin.pacs.application.registration.response.CreateLocalRegistrationResponse;
import com.zxplat.plugin.pacs.application.registration.response.GetRegistrationDetailResponse;
import com.zxplat.plugin.pacs.application.registration.response.SaveRegistrationResponse;
import com.zxplat.plugin.pacs.application.registration.response.SearchPendingRegistrationResponse;
import com.zxplat.plugin.pacs.application.registration.support.PacsLocalRegistrationEncounterResolver;
import com.zxplat.plugin.pacs.application.registration.support.PacsRegistrationSlipPrintAssembler;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.plugin.pacs.application.share.PacsCurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.plugin.pacs.domain.workitem.command.CancelPacsWorkItemCommand;
import com.zxplat.plugin.pacs.domain.workitem.command.CreateLocalPacsWorkItemCommand;
import com.zxplat.plugin.pacs.domain.workitem.command.UpdatePacsRegistrationCommand;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import com.zxplat.plugin.pacs.domain.workitem.queries.PacsWorkItemSearchQuery;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemDomainService;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检查登记工作台（AS-PACS-REG-001）。
 *
 * <p>接入：{@code POST /api/app/pacsRegistration/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsRegistrationAppService {

    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final PacsWorkItemDomainService pacsWorkItemDomainService;
    private final PatientQueryService patientQueryService;
    private final PacsCurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    /** 登记 DTO 装配。 */
    private final PacsRegistrationMapper pacsRegistrationMapper;
    /** 本地补登就诊解析。 */
    private final PacsLocalRegistrationEncounterResolver localRegistrationEncounterResolver;
    /** 登记条打印 slip 组装。 */
    private final PacsRegistrationSlipPrintAssembler registrationSlipPrintAssembler;

    /**
     * IF-PACS-REG-01：待登记列表。
     *
     * @param request 分页与过滤
     * @return 分页行（含患者姓名）
     */
    public SearchPendingRegistrationResponse searchPendingRegistration(SearchPendingRegistrationRequest request) {
        // Gate：分页窗口
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "分页参数非法");
        PacsWorkItemSearchQuery query = new PacsWorkItemSearchQuery();
        if (request != null) {
            query.setOrganizationId(request.getOrganizationId());
            query.setModalityCode(request.getModalityCode());
            query.setDateFrom(request.getStartDate());
            query.setDateTo(request.getEndDate());
        }
        PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        // Pre：待登记分页
        Page<PacsWorkItemQueryModel> page = pacsWorkItemQueryService.searchPendingRegistration(query, pageable);
        // Pre：批量姓名
        List<Long> patientIds = new ArrayList<>();
        for (PacsWorkItemQueryModel row : page.getContent()) {
            if (row != null && row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
        }
        Map<Long, String> names = patientQueryService.findPatientNamesByIds(patientIds);
        // Asm
        List<PacsRegistrationRowDto> records = pacsRegistrationMapper.toRowDtos(page.getContent(), names);
        return PagedResultBuilder.build(new SearchPendingRegistrationResponse(), records, page);
    }

    /**
     * 登记详情（REG-02 编辑前读侧）。
     *
     * @param request 工单主键
     * @return 详情
     */
    public GetRegistrationDetailResponse getRegistrationDetail(GetRegistrationDetailRequest request) {
        PacsWorkItemDetailQueryModel detail = pacsWorkItemQueryService.getDetail(request.getWorkItemId());
        Map<Long, String> names = patientQueryService.findPatientNamesByIds(List.of(detail.getPatientId()));
        String patientName = names.get(detail.getPatientId());
        return pacsRegistrationMapper.toDetailResponse(detail, patientName);
    }

    /**
     * IF-PACS-REG-02：保存登记；confirmSchedule=true 时生成检查号并进入待摄。
     *
     * @param request 保存入参
     * @return 保存结果
     */
    @Transactional
    public SaveRegistrationResponse saveRegistration(SaveRegistrationRequest request) {
        // Gate：操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "未获取到当前操作人");
        // Pre：存在性
        pacsWorkItemQueryService.getDetail(request.getWorkItemId());
        // Cmd：写库
        UpdatePacsRegistrationCommand cmd = new UpdatePacsRegistrationCommand();
        cmd.setWorkItemId(request.getWorkItemId());
        cmd.setOrganizationId(request.getOrganizationId());
        cmd.setModalityCode(request.getModalityCode());
        cmd.setBodySiteCode(request.getBodySiteCode());
        cmd.setDeviceId(request.getDeviceId());
        cmd.setPerformerPractitionerId(request.getPerformerPractitionerId());
        cmd.setScheduledTime(request.getScheduledTime());
        cmd.setAccessionNumber(request.getAccessionNumber());
        cmd.setConfirmSchedule(request.getConfirmSchedule());
        cmd.setOperatorId(operatorId);
        PacsWorkItem saved = pacsWorkItemDomainService.updateRegistration(cmd);
        // Asm
        return pacsRegistrationMapper.toSaveResponse(saved);
    }

    /**
     * IF-PACS-REG-04：无 HIS 申请本地补登记；可选立即确认待摄。
     *
     * @param request 补登入参
     * @return 工单与检查号
     */
    @Transactional
    public CreateLocalRegistrationResponse createLocalRegistration(CreateLocalRegistrationRequest request) {
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "未获取到当前操作人");
        if (request == null || request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "患者不能为空");
        }
        if (request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "科室不能为空");
        }
        if (StringUtils.isEmpty(request.getModalityCode())) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "模态不能为空");
        }
        // Pre：患者须存在
        if (patientQueryService.findOptionalPatientEntityById(request.getPatientId()).isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_NOT_FOUND, "患者不存在或已删除");
        }
        // Pre：解析就诊（复用当日或开立）
        Long encounterId = localRegistrationEncounterResolver.resolveEncounterId(
            request.getPatientId(),
            request.getOrganizationId(),
            request.getEncounterId(),
            request.getServiceTypeId(),
            operatorId);
        // Cmd：本地工单（无 orderLink）
        CreateLocalPacsWorkItemCommand createCmd = new CreateLocalPacsWorkItemCommand();
        createCmd.setPatientId(request.getPatientId());
        createCmd.setEncounterId(encounterId);
        createCmd.setOrganizationId(request.getOrganizationId());
        createCmd.setTenantId(TenantUtils.getCurrentTenantId());
        createCmd.setModalityCode(request.getModalityCode());
        createCmd.setBodySiteCode(request.getBodySiteCode());
        createCmd.setActivityId(request.getActivityId());
        createCmd.setDeviceId(request.getDeviceId());
        createCmd.setScheduledTime(request.getScheduledTime());
        createCmd.setOperatorId(operatorId);
        PacsWorkItem workItem = pacsWorkItemDomainService.createLocalWorkItem(createCmd);
        boolean scheduleConfirmed = request.getConfirmSchedule() == null
            || Boolean.TRUE.equals(request.getConfirmSchedule());
        if (scheduleConfirmed) {
            UpdatePacsRegistrationCommand scheduleCmd = new UpdatePacsRegistrationCommand();
            scheduleCmd.setWorkItemId(workItem.getId());
            scheduleCmd.setAccessionNumber(request.getAccessionNumber());
            scheduleCmd.setConfirmSchedule(Boolean.TRUE);
            scheduleCmd.setOperatorId(operatorId);
            workItem = pacsWorkItemDomainService.updateRegistration(scheduleCmd);
        }
        return pacsRegistrationMapper.toCreateLocalResponse(workItem, encounterId);
    }

    /**
     * IF-PACS-REG-05：加载登记条打印 slip（printData）；出纸委托 PrintTemplate {@code preparePrint}。
     *
     * @param request 工单主键
     * @return 打印 slip（含 scenarioCode）
     */
    public PacsRegistrationSlipPrintDto loadRegistrationSlipPrint(LoadRegistrationSlipPrintRequest request) {
        if (request == null || request.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "工单标识不能为空");
        }
        // Asm：组装 printData，不调 printJob（由前端 preparePrint）
        return registrationSlipPrintAssembler.assemble(request.getWorkItemId());
    }

    /**
     * IF-PACS-REG-03：作废登记。
     *
     * @param request 作废入参
     * @return 作废结果
     */
    @Transactional
    public CancelRegistrationResponse cancelRegistration(CancelRegistrationRequest request) {
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "未获取到当前操作人");
        CancelPacsWorkItemCommand cmd = new CancelPacsWorkItemCommand();
        cmd.setWorkItemId(request.getWorkItemId());
        String reason = request.getReason();
        if (reason == null) {
            reason = "登记作废";
        }
        cmd.setReason(reason);
        cmd.setOperatorId(operatorId);
        PacsWorkItem cancelled = pacsWorkItemDomainService.cancelWorkItem(cmd);
        CancelRegistrationResponse resp = new CancelRegistrationResponse();
        resp.setWorkItemId(cancelled.getId());
        resp.setStatusEnum(cancelled.getStatusEnum());
        resp.setSuccess(Boolean.TRUE);
        return resp;
    }
}
