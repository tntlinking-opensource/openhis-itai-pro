/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.deptdesk.service;

import com.openhis.health.application.peis.deptdesk.dto.ObservationRowDto;
import com.openhis.health.application.peis.deptdesk.mapper.PeisDeptDeskMapper;
import com.openhis.health.application.peis.deptdesk.support.PeisDeptDeskEligibilitySupport;
import com.openhis.health.application.peis.deptdesk.request.ApplyCommonResultRequest;
import com.openhis.health.application.peis.deptdesk.request.BatchSaveObservationsRequest;
import com.openhis.health.application.peis.deptdesk.request.LoadObservationsRequest;
import com.openhis.health.application.peis.deptdesk.request.ProjectResultsRequest;
import com.openhis.health.application.peis.deptdesk.request.SaveProjectConclusionRequest;
import com.openhis.health.application.peis.deptdesk.request.SaveDeptConclusionRequest;
import com.openhis.health.application.peis.deptdesk.request.SaveObservationRequest;
import com.openhis.health.application.peis.deptdesk.request.SearchPendingRequest;
import com.openhis.health.application.peis.deptdesk.response.BatchSaveObservationsResponse;
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
import com.openhis.health.domain.clinical.observationdefinition.command.SaveObservationDefinitionCommand;
import com.openhis.health.domain.clinical.observationdefinition.queries.ObservationDefinitionQuery;
import com.openhis.health.domain.clinical.observationdefinition.queries.model.ObservationDefinitionModel;
import com.openhis.health.domain.clinical.observationdefinition.service.ObservationDefinitionDomainService;
import com.openhis.health.domain.clinical.observationdefinition.service.ObservationDefinitionQueryService;
import com.openhis.health.domain.custom.activityobservationlink.queries.model.ActivityObservationLinkModel;
import com.openhis.health.domain.custom.activityobservationlink.service.ActivityObservationLinkQueryService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /** 租户默认「项目结论」观察项编码（无需手工维护目录）。 */
    private static final String DEFAULT_PROJECT_CONCLUSION_CODE = "PEIS_PROJECT_CONCLUSION";

    /** 租户默认「项目结论」展示名。 */
    private static final String DEFAULT_PROJECT_CONCLUSION_DISPLAY = "项目结论";

    /** 观察实例写侧。 */
    private final ObservationDomainService observationDomainService;
    /** 观察实例读侧。 */
    private final ObservationQueryService observationQueryService;
    /** 观察定义目录读侧（启停与分类）。 */
    private final ObservationDefinitionQueryService observationDefinitionQueryService;
    /** 观察定义目录写侧（懒创建默认项目结论）。 */
    private final ObservationDefinitionDomainService observationDefinitionDomainService;
    /** 项目与小项绑定读侧。 */
    private final ActivityObservationLinkQueryService activityObservationLinkQueryService;
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
    /** 团检 BR-GRP-PAY-01 分科资格。 */
    private final PeisDeptDeskEligibilitySupport peisDeptDeskEligibilitySupport;

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
            // 单条小项确认不等同于项目已检；项目 exec=checked 由全部小项确认或诊台「完成项目」触发
        }
        return peisDeptDeskMapper.toSaveResponse(saved);
    }

    /**
     * 批量保存小项（顺序提交、同一事务，避免并行乐观锁冲突）。
     *
     * @param request 批量请求
     * @return 批量保存结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSaveObservationsResponse batchSaveObservations(BatchSaveObservationsRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "待保存小项不能为空");
        }
        List<SaveObservationResponse> results = new ArrayList<>();
        for (SaveObservationRequest item : request.getItems()) {
            if (item == null) {
                continue;
            }
            results.add(saveObservation(item));
        }
        if (results.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "待保存小项不能为空");
        }
        BatchSaveObservationsResponse response = new BatchSaveObservationsResponse();
        response.setSuccessCount(results.size());
        response.setResults(results);
        return response;
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
     * 保存项目结论（IF-PEIS-DES-04）。
     *
     * @param request 结论请求
     * @return 保存结果
     */
    public SaveObservationResponse saveProjectConclusion(SaveProjectConclusionRequest request) {
        if (request == null || request.getServiceRequestId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "服务申请不能为空");
        }
        if (StringUtils.isEmpty(request.getValueString())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "项目结论文本不能为空");
        }
        ServiceRequestListModel sr = serviceRequestQueryService.mustGetListModelById(request.getServiceRequestId());
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(sr.getEncounterId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "就诊不存在"));
        if (request.getDefinitionId() == null) {
            request.setDefinitionId(
                resolveProjectConclusionDefinition(encounter.getTenantId(), sr.getOrgId()).getId());
        }
        ObservationDefinitionModel definition = observationDefinitionQueryService.getById(request.getDefinitionId());
        assertDefinitionActive(definition);
        if (!ObservationDefinitionConstants.CATEGORY_PROJECT_CONCLUSION.equals(definition.getCategoryCode())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "观察定义须为项目结论分类");
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
     * @deprecated 使用 {@link #saveProjectConclusion(SaveProjectConclusionRequest)}
     */
    @Deprecated
    public SaveObservationResponse saveDeptConclusion(SaveDeptConclusionRequest request) {
        return saveProjectConclusion(request);
    }

    /**
     * 加载服务申请下观察实例列表。
     *
     * @param request 加载请求
     * @return 观察列表
     */
    @Transactional(rollbackFor = Exception.class)
    public ObservationListResponse loadObservationsByServiceRequest(LoadObservationsRequest request) {
        // Gate：服务申请主键必填
        if (request == null || request.getServiceRequestId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "服务申请标识不能为空");
        }
        // Pre：读本聚合观察行
        ServiceRequestListModel sr = serviceRequestQueryService.mustGetListModelById(request.getServiceRequestId());
        peisDeptDeskEligibilitySupport.assertEncounterEligibleForDeptDesk(sr.getEncounterId());
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(sr.getEncounterId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "就诊不存在"));
        List<ObservationModel> models = observationQueryService.listByServiceRequestId(request.getServiceRequestId());
        Long activityId = sr.getActivityId();
        if (activityId == null && request.getActivityId() != null) {
            activityId = request.getActivityId();
        }
        List<ObservationRowDto> rows = buildObservationEntryRows(
            activityId, encounter.getTenantId(), sr.getOrgId(), models);
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
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(sr.getEncounterId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "就诊不存在"));
        if (!EncounterClass.PE.getCode().equals(encounter.getClassEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "非体检就诊不可在分科诊台录入");
        }
        // Gate：团检须已建单（ENROLLED/PAID）或收银结清（BR-GRP-PAY-01 v1.3）
        peisDeptDeskEligibilitySupport.assertEncounterEligibleForDeptDesk(encounter.getId());
        return sr;
    }

    private void assertDefinitionActive(ObservationDefinitionModel definition) {
        if (!ObservationDefinitionConstants.isActiveStatus(definition.getStatusEnum())) {
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
        if (createCmd.getOrgId() == null && sr.getOrgId() != null) {
            createCmd.setOrgId(sr.getOrgId());
        }
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
        if (models == null) {
            return;
        }
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

    /**
     * 合并已录入观察实例与项目绑定的小项模板（诊台无需手输 OD ID）。
     */
    private List<ObservationRowDto> buildObservationEntryRows(
        Long activityId,
        Long tenantId,
        Long deptOrgId,
        List<ObservationModel> existingModels) {
        Map<Long, ObservationModel> existingByDefinitionId = new HashMap<>();
        if (existingModels != null) {
            for (ObservationModel model : existingModels) {
                if (model != null && model.getDefinitionId() != null) {
                    existingByDefinitionId.put(model.getDefinitionId(), model);
                }
            }
        }
        Map<Long, String> displayMap = new HashMap<>();
        Map<Long, String> categoryMap = new HashMap<>();
        enrichDefinitionMaps(existingModels, displayMap, categoryMap);

        List<ObservationRowDto> rows = new ArrayList<>();
        Set<Long> coveredDefinitionIds = new LinkedHashSet<>();
        if (activityId != null) {
            List<ActivityObservationLinkModel> links = activityObservationLinkQueryService.listByActivityId(activityId);
            Set<Long> linkedOdIds = new LinkedHashSet<>();
            for (ActivityObservationLinkModel link : links) {
                if (link != null && link.getObservationDefinitionId() != null) {
                    linkedOdIds.add(link.getObservationDefinitionId());
                }
            }
            Map<Long, ObservationDefinitionModel> odById = new HashMap<>();
            if (!linkedOdIds.isEmpty()) {
                for (ObservationDefinitionModel od : observationDefinitionQueryService.listByIds(linkedOdIds)) {
                    if (od == null || od.getId() == null) {
                        continue;
                    }
                    if (!ObservationDefinitionConstants.isActiveStatus(od.getStatusEnum())) {
                        continue;
                    }
                    odById.put(od.getId(), od);
                    displayMap.put(od.getId(), od.getDisplay());
                    categoryMap.put(od.getId(), od.getCategoryCode());
                }
            }
            for (ActivityObservationLinkModel link : links) {
                if (link == null || link.getObservationDefinitionId() == null) {
                    continue;
                }
                Long definitionId = link.getObservationDefinitionId();
                ObservationDefinitionModel definition = odById.get(definitionId);
                if (definition == null) {
                    continue;
                }
                coveredDefinitionIds.add(definitionId);
                ObservationModel existing = existingByDefinitionId.get(definitionId);
                if (existing != null) {
                    rows.add(toObservationRow(existing, displayMap, categoryMap));
                } else {
                    ObservationRowDto template = new ObservationRowDto();
                    template.setDefinitionId(definitionId);
                    template.setDefinitionDisplay(definition.getDisplay());
                    template.setCategoryCode(definition.getCategoryCode());
                    rows.add(template);
                }
            }
        }
        appendMissingProjectConclusionRow(
            rows,
            coveredDefinitionIds,
            existingByDefinitionId,
            displayMap,
            categoryMap,
            tenantId,
            deptOrgId);
        if (existingModels != null) {
            for (ObservationModel model : existingModels) {
                if (model == null || model.getDefinitionId() == null) {
                    continue;
                }
                if (coveredDefinitionIds.contains(model.getDefinitionId())) {
                    continue;
                }
                rows.add(toObservationRow(model, displayMap, categoryMap));
            }
        }
        return rows;
    }

    /**
     * 每个体检项目自动补一条「项目结论」录入模板（无需在目录手工维护）。
     */
    private void appendMissingProjectConclusionRow(
        List<ObservationRowDto> rows,
        Set<Long> coveredDefinitionIds,
        Map<Long, ObservationModel> existingByDefinitionId,
        Map<Long, String> displayMap,
        Map<Long, String> categoryMap,
        Long tenantId,
        Long deptOrgId) {
        if (rows != null) {
            for (ObservationRowDto row : rows) {
                if (ObservationDefinitionConstants.CATEGORY_PROJECT_CONCLUSION.equals(row.getCategoryCode())) {
                    return;
                }
            }
        }
        ObservationDefinitionModel picked = resolveProjectConclusionDefinition(tenantId, deptOrgId);
        if (picked == null || picked.getId() == null) {
            return;
        }
        Long definitionId = picked.getId();
        if (coveredDefinitionIds.contains(definitionId)) {
            return;
        }
        coveredDefinitionIds.add(definitionId);
        displayMap.put(definitionId, picked.getDisplay());
        categoryMap.put(definitionId, picked.getCategoryCode());
        ObservationModel existing = existingByDefinitionId.get(definitionId);
        if (existing != null) {
            rows.add(toObservationRow(existing, displayMap, categoryMap));
            return;
        }
        ObservationRowDto template = new ObservationRowDto();
        template.setDefinitionId(definitionId);
        template.setDefinitionDisplay(picked.getDisplay());
        template.setCategoryCode(picked.getCategoryCode());
        rows.add(template);
    }

  /**
   * 解析租户可用的项目结论观察定义；不存在时懒创建默认项。
   *
   * @param tenantId 租户
   * @param deptOrgId 执行科室（优先匹配）
   * @return 项目结论 OD
   */
    private ObservationDefinitionModel resolveProjectConclusionDefinition(Long tenantId, Long deptOrgId) {
        if (tenantId == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "租户标识不能为空");
        }
        ObservationDefinitionQuery query = new ObservationDefinitionQuery();
        query.setTenantId(tenantId);
        query.setCategoryCode(ObservationDefinitionConstants.CATEGORY_PROJECT_CONCLUSION);
        List<ObservationDefinitionModel> defs = observationDefinitionQueryService.search(
            query,
            PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "id"))).getContent();
        ObservationDefinitionModel picked = null;
        if (deptOrgId != null) {
            for (ObservationDefinitionModel definition : defs) {
                if (definition != null
                    && definition.getId() != null
                    && deptOrgId.equals(definition.getOrgId())) {
                    picked = definition;
                    break;
                }
            }
        }
        if (picked == null && !defs.isEmpty()) {
            picked = defs.get(0);
        }
        if (picked != null) {
            return picked;
        }
        return createDefaultProjectConclusionDefinition(tenantId, deptOrgId);
    }

    private ObservationDefinitionModel createDefaultProjectConclusionDefinition(
        Long tenantId,
        Long deptOrgId) {
        ObservationDefinitionQuery existingQuery = new ObservationDefinitionQuery();
        existingQuery.setTenantId(tenantId);
        existingQuery.setCategoryCode(ObservationDefinitionConstants.CATEGORY_PROJECT_CONCLUSION);
        existingQuery.setKeyword(DEFAULT_PROJECT_CONCLUSION_CODE);
        List<ObservationDefinitionModel> existing = observationDefinitionQueryService.search(
            existingQuery,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id"))).getContent();
        for (ObservationDefinitionModel row : existing) {
            if (row != null
                && row.getId() != null
                && DEFAULT_PROJECT_CONCLUSION_CODE.equals(row.getCode())) {
                return row;
            }
        }
        SaveObservationDefinitionCommand cmd = new SaveObservationDefinitionCommand();
        cmd.setTenantId(tenantId);
        cmd.setOrgId(deptOrgId);
        cmd.setCode(DEFAULT_PROJECT_CONCLUSION_CODE);
        cmd.setDisplay(DEFAULT_PROJECT_CONCLUSION_DISPLAY);
        cmd.setValueTypeEnum("string");
        cmd.setCategoryCode(ObservationDefinitionConstants.CATEGORY_PROJECT_CONCLUSION);
        cmd.setStatusEnum(ObservationDefinitionConstants.STATUS_ACTIVE);
        return observationDefinitionQueryService.getById(
            observationDefinitionDomainService.save(cmd).getId());
    }

    private ObservationRowDto toObservationRow(
        ObservationModel model,
        Map<Long, String> displayMap,
        Map<Long, String> categoryMap) {
        ObservationRowDto row = new ObservationRowDto();
        row.setId(model.getId());
        row.setDefinitionId(model.getDefinitionId());
        if (model.getDefinitionId() != null) {
            row.setDefinitionDisplay(displayMap.get(model.getDefinitionId()));
            row.setCategoryCode(categoryMap.get(model.getDefinitionId()));
        }
        row.setValueQuantity(model.getValueQuantity());
        row.setValueString(model.getValueString());
        row.setInterpretationCode(model.getInterpretationCode());
        row.setStatusEnum(model.getStatusEnum());
        return row;
    }
}
