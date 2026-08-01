/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.deptdesk.service;

import com.openhis.health.application.peis.deptdesk.dto.ObservationRowDto;
import com.openhis.health.application.peis.deptdesk.mapper.PeisDeptDeskMapper;
import com.openhis.health.application.peis.deptdesk.request.ApplyCommonResultRequest;
import com.openhis.health.application.peis.deptdesk.request.LoadObservationsRequest;
import com.openhis.health.application.peis.deptdesk.request.ProjectResultsRequest;
import com.openhis.health.application.peis.deptdesk.request.SaveDeptConclusionRequest;
import com.openhis.health.application.peis.deptdesk.request.SaveObservationRequest;
import com.openhis.health.application.peis.deptdesk.request.SearchPendingRequest;
import com.openhis.health.application.peis.deptdesk.response.ObservationListResponse;
import com.openhis.health.application.peis.deptdesk.response.SaveObservationResponse;
import com.openhis.health.application.peis.deptdesk.response.SearchPendingResponse;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.observation.command.CreateObservationCommand;
import com.openhis.health.domain.clinical.observation.command.FinalizeObservationCommand;
import com.openhis.health.domain.clinical.observation.command.UpdateObservationCommand;
import com.openhis.health.domain.clinical.observation.entity.Observation;
import com.openhis.health.domain.clinical.observation.queries.model.ObservationModel;
import com.openhis.health.domain.clinical.observation.service.ObservationDomainService;
import com.openhis.health.domain.clinical.observation.service.ObservationQueryService;
import com.openhis.health.domain.clinical.observationdefinition.queries.model.ObservationDefinitionModel;
import com.openhis.health.domain.clinical.observationdefinition.service.ObservationDefinitionQueryService;
import com.openhis.health.domain.peis.commonresult.queries.model.PeisCommonResultModel;
import com.openhis.health.domain.peis.commonresult.service.PeisCommonResultQueryService;
import com.openhis.health.domain.peis.encounterprogress.queries.PeisEncounterProgressQuery;
import com.openhis.health.domain.peis.encounterprogress.queries.model.DeptDeskRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.query.DeptDeskQuery;
import com.openhis.health.domain.peis.servicerequestext.command.UpdatePeisSrExecStatusCommand;
import com.openhis.health.domain.peis.servicerequestext.service.PeisServiceRequestExtDomainService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ObservationDefinitionConstants;
import com.openhis.health.domain.share.constants.PeisExecStatusConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 分科诊台应用服务（AS-PEIS-DES-001）：待检列表、小项录入、常见结果套用、分科结论。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：仅 PE 就诊；可选科室与 SR.orgId 一致；OD 须启用。</li>
 *   <li>Pre：ProgressQuery 待检队列；按 SR 加载 Observation；常见结果模板读侧。</li>
 *   <li>Cmd：ObservationDomainService upsert/finalize；确认后更新 peis_sr_ext.exec=checked。</li>
 *   <li>Asm：经 {@link PeisDeptDeskMapper} 装配；DES-08 投影 Gap 未关前拒绝。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} → {@code POST /api/app/peisDeptExamDesk/{methodName}}；不另写 RestController。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisDeptExamDeskAppService {

    private static final String GAP_PROJECTION_MESSAGE =
        "LIS/PACS 结果投影尚未就绪（GAP-PEIS-DES-01），请等待领域 LisResultProjectionService 关闭后再试";

    /** 观察实例写侧。 */
    private final ObservationDomainService observationDomainService;
    /** 观察实例读侧。 */
    private final ObservationQueryService observationQueryService;
    /** 观察定义目录读侧（启停与分类）。 */
    private final ObservationDefinitionQueryService observationDefinitionQueryService;
    /** 体检进度只读（DES-01）。 */
    private final PeisEncounterProgressQuery peisEncounterProgressQuery;
    /** 项目执行扩展写侧（确认后 checked）。 */
    private final PeisServiceRequestExtDomainService peisServiceRequestExtDomainService;
    /** 常见结果模板读侧。 */
    private final PeisCommonResultQueryService peisCommonResultQueryService;
    /** 服务申请读侧。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 就诊读侧（PE 门禁）。 */
    private final EncounterQueryService encounterQueryService;
    /** 写路径操作人。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 分页窗口门禁。 */
    private final PageWindowGate pageWindowGate;
    /** 请求/响应映射。 */
    private final PeisDeptDeskMapper peisDeptDeskMapper;

    /**
     * 待检列表（IF-PEIS-DES-01）。
     *
     * @param request 检索条件
     * @return 分页待检行
     */
    public SearchPendingResponse searchPending(SearchPendingRequest request) {
        // Gate：分页窗口合法
        pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT,
            "待检列表分页参数非法");
        // Pre：HTTP 请求转领域查询
        DeptDeskQuery query = peisDeptDeskMapper.toDeptDeskQuery(request);
        // Cmd：经 ProgressQuery 只读 join
        Page<DeptDeskRowModel> page = peisEncounterProgressQuery.searchPendingDeptDesk(query);
        // Asm：映射出参
        return peisDeptDeskMapper.toSearchPendingResponse(
            page, peisDeptDeskMapper.toPendingDeskRows(page.getContent()));
    }

    /**
     * 保存小项结果（IF-PEIS-DES-02）。
     *
     * @param request 保存请求
     * @return 保存结果
     */
    public SaveObservationResponse saveObservation(SaveObservationRequest request) {
        // Gate：参数与服务申请科室归属
        ServiceRequestListModel sr = assertSaveObservationGate(request);
        Long performerId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "请先登录后再录入结果");
        ObservationDefinitionModel definition = observationDefinitionQueryService.getById(request.getDefinitionId());
        assertDefinitionActive(definition);

        Observation saved = upsertObservation(request, sr, performerId, definition.getCode());
        if (Boolean.TRUE.equals(request.getFinalizeFlag())) {
            FinalizeObservationCommand finalizeCmd = peisDeptDeskMapper.toFinalizeCommand(saved.getId(), performerId);
            saved = observationDomainService.finalizeObservation(finalizeCmd);
            markServiceRequestChecked(sr);
        }
        return peisDeptDeskMapper.toSaveResponse(saved);
    }

    /**
     * 套用常见结果（IF-PEIS-DES-03）。
     *
     * @param request 套用请求
     * @return 保存结果
     */
    public SaveObservationResponse applyCommonResult(ApplyCommonResultRequest request) {
        // Gate：模板与服务申请须存在
        if (request == null || request.getServiceRequestId() == null || request.getCommonResultId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "服务申请与常见结果模板不能为空");
        }
        PeisCommonResultModel template = peisCommonResultQueryService.findById(request.getCommonResultId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PEIS_COMMON_RESULT_NOT_FOUND, "常见结果模板不存在"));
        SaveObservationRequest saveRequest = new SaveObservationRequest();
        saveRequest.setServiceRequestId(request.getServiceRequestId());
        saveRequest.setDefinitionId(template.getObservationDefinitionId());
        saveRequest.setDeptOrgId(request.getDeptOrgId());
        saveRequest.setValueString(template.getValueString());
        saveRequest.setInterpretationCode(template.getInterpretationCode());
        saveRequest.setFinalizeFlag(request.getFinalizeFlag());
        return saveObservation(saveRequest);
    }

    /**
     * 保存分科结论（IF-PEIS-DES-04）。
     *
     * @param request 结论请求
     * @return 保存结果
     */
    public SaveObservationResponse saveDeptConclusion(SaveDeptConclusionRequest request) {
        // Gate：结论文本与 OD 分类须为 dept_conclusion
        if (request == null || request.getServiceRequestId() == null || request.getDefinitionId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "服务申请与观察定义不能为空");
        }
        if (StringUtils.isEmpty(request.getValueString())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "分科结论文本不能为空");
        }
        ObservationDefinitionModel definition = observationDefinitionQueryService.getById(request.getDefinitionId());
        assertDefinitionActive(definition);
        if (!ObservationDefinitionConstants.CATEGORY_DEPT_CONCLUSION.equals(definition.getCategoryCode())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "观察定义须为分科结论分类");
        }
        SaveObservationRequest saveRequest = new SaveObservationRequest();
        saveRequest.setServiceRequestId(request.getServiceRequestId());
        saveRequest.setDefinitionId(request.getDefinitionId());
        saveRequest.setDeptOrgId(request.getDeptOrgId());
        saveRequest.setValueString(request.getValueString());
        saveRequest.setFinalizeFlag(Boolean.TRUE);
        return saveObservation(saveRequest);
    }

    /**
     * 加载服务申请下观察实例列表。
     *
     * @param request 加载请求
     * @return 观察列表
     */
    public ObservationListResponse loadObservationsByServiceRequest(LoadObservationsRequest request) {
        // Gate：服务申请主键必填
        if (request == null || request.getServiceRequestId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "服务申请标识不能为空");
        }
        // Pre：读本聚合观察行
        List<ObservationModel> models = observationQueryService.listByServiceRequestId(request.getServiceRequestId());
        Map<Long, String> displayMap = new HashMap<>();
        Map<Long, String> categoryMap = new HashMap<>();
        enrichDefinitionMaps(models, displayMap, categoryMap);
        List<ObservationRowDto> rows = peisDeptDeskMapper.toObservationRows(models, displayMap, categoryMap);
        return peisDeptDeskMapper.toObservationListResponse(request.getServiceRequestId(), rows);
    }

    /**
     * 拉取 LIS/PACS 结果投影（IF-PEIS-DES-08；Gap 未关闭前拒绝）。
     *
     * @param request 投影请求
     */
    public void projectResults(ProjectResultsRequest request) {
        // Gate：Gap 未关闭，禁止伪实现投影
        if (request == null || request.getServiceRequestId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "服务申请标识不能为空");
        }
        throw new BusinessException(ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, GAP_PROJECTION_MESSAGE);
    }

    private ServiceRequestListModel assertSaveObservationGate(SaveObservationRequest request) {
        if (request == null || request.getServiceRequestId() == null || request.getDefinitionId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "服务申请与观察定义不能为空");
        }
        ServiceRequestListModel sr = serviceRequestQueryService.mustGetListModelById(request.getServiceRequestId());
        assertServiceRequestInDept(sr, request.getDeptOrgId());
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(sr.getEncounterId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "就诊不存在"));
        if (!EncounterClass.PE.getCode().equals(encounter.getClassEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "非体检就诊不可在分科诊台录入");
        }
        return sr;
    }

    private void assertServiceRequestInDept(ServiceRequestListModel sr, Long deptOrgId) {
        if (deptOrgId == null) {
            return;
        }
        if (sr.getOrgId() != null && !Objects.equals(sr.getOrgId(), deptOrgId)) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_SR_NOT_IN_DEPT, "该检查项目不属于当前科室");
        }
    }

    private void assertDefinitionActive(ObservationDefinitionModel definition) {
        if (!ObservationDefinitionConstants.STATUS_ACTIVE.equals(definition.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INACTIVE, "观察定义已停用");
        }
    }

    private Observation upsertObservation(
        SaveObservationRequest request,
        ServiceRequestListModel sr,
        Long performerId,
        String definitionCode) {
        Optional<ObservationModel> existing = observationQueryService.findBySrAndDefinition(
            request.getServiceRequestId(), request.getDefinitionId());
        if (existing.isPresent()) {
            UpdateObservationCommand updateCmd = peisDeptDeskMapper.toUpdateCommand(
                request, existing.get().getId(), performerId);
            return observationDomainService.updateObservation(updateCmd);
        }
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(sr.getEncounterId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "就诊不存在"));
        CreateObservationCommand createCmd = peisDeptDeskMapper.toCreateCommand(
            request, encounter.getTenantId(), sr.getEncounterId(), performerId, definitionCode);
        return observationDomainService.createObservation(createCmd);
    }

    private void markServiceRequestChecked(ServiceRequestListModel sr) {
        UpdatePeisSrExecStatusCommand execCmd = new UpdatePeisSrExecStatusCommand();
        execCmd.setServiceRequestId(sr.getId());
        execCmd.setEncounterId(sr.getEncounterId());
        execCmd.setExecStatusEnum(PeisExecStatusConstants.CHECKED);
        peisServiceRequestExtDomainService.updateExecStatus(execCmd);
    }

    private void enrichDefinitionMaps(
        List<ObservationModel> models,
        Map<Long, String> displayMap,
        Map<Long, String> categoryMap) {
        Set<Long> definitionIds = new HashSet<>();
        for (ObservationModel model : models) {
            if (model.getDefinitionId() != null) {
                definitionIds.add(model.getDefinitionId());
            }
        }
        if (definitionIds.isEmpty()) {
            return;
        }
        for (ObservationDefinitionModel od : observationDefinitionQueryService.listByIds(definitionIds)) {
            displayMap.put(od.getId(), od.getDisplay());
            categoryMap.put(od.getId(), od.getCategoryCode());
        }
    }
}
