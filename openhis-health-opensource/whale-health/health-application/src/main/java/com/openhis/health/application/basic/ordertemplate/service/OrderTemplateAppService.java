/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.ordertemplate.service;

import com.openhis.health.application.basic.ordertemplate.dto.AdviceSourceLineKeyDto;
import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateBindingGroupDto;
import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateLineEditDto;
import com.openhis.health.application.basic.ordertemplate.mapper.OrderTemplateMapper;
import com.openhis.health.application.basic.ordertemplate.mapper.OrderTemplateMapper.LineDiffResult;
import com.openhis.health.application.basic.ordertemplate.request.ChangeOrderTemplateLifecycleRequest;
import com.openhis.health.application.basic.ordertemplate.request.GetOrderTemplateDetailRequest;
import com.openhis.health.application.basic.ordertemplate.request.InitOrderTemplateMaintenanceRequest;
import com.openhis.health.application.basic.ordertemplate.request.ListDoctorStationOrderTemplateRequest;
import com.openhis.health.application.basic.ordertemplate.request.ListNurseStationPricingOrderTemplateRequest;
import com.openhis.health.application.basic.ordertemplate.request.PageMaintainedOrderTemplateRequest;
import com.openhis.health.application.basic.ordertemplate.request.PreviewOrderTemplateApplyRequest;
import com.openhis.health.application.basic.ordertemplate.request.SaveMaintainedOrderTemplateHeaderRequest;
import com.openhis.health.application.basic.ordertemplate.request.SaveMaintainedOrderTemplateLinesRequest;
import com.openhis.health.application.basic.ordertemplate.request.SaveOrderTemplateFromAdviceRequest;
import com.openhis.health.application.basic.ordertemplate.response.ChangeOrderTemplateLifecycleResponse;
import com.openhis.health.application.basic.ordertemplate.response.GetOrderTemplateDetailResponse;
import com.openhis.health.application.basic.ordertemplate.response.InitOrderTemplateMaintenanceResponse;
import com.openhis.health.application.basic.ordertemplate.response.ListDoctorStationOrderTemplateResponse;
import com.openhis.health.application.basic.ordertemplate.response.ListNurseStationPricingOrderTemplateResponse;
import com.openhis.health.application.basic.ordertemplate.response.PageMaintainedOrderTemplateResponse;
import com.openhis.health.application.basic.ordertemplate.response.PreviewOrderTemplateApplyResponse;
import com.openhis.health.application.basic.ordertemplate.response.SaveMaintainedOrderTemplateHeaderResponse;
import com.openhis.health.application.basic.ordertemplate.response.SaveMaintainedOrderTemplateLinesResponse;
import com.openhis.health.application.basic.ordertemplate.response.SaveOrderTemplateFromAdviceResponse;
import com.openhis.health.application.basic.ordertemplate.support.OrderTemplateCatalogEnrichSupport;
import com.openhis.health.application.basic.ordertemplate.support.OrderTemplateScopeResolver;
import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.custom.ordertemplate.command.AddOrderTemplateLineCommand;
import com.openhis.health.domain.custom.ordertemplate.command.DeleteOrderTemplateCommand;
import com.openhis.health.domain.custom.ordertemplate.command.PublishOrderTemplateCommand;
import com.openhis.health.domain.custom.ordertemplate.command.RetireOrderTemplateCommand;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateHeaderQueryModel;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateWithLinesQueryModel;
import com.openhis.health.domain.custom.ordertemplate.service.OrderTemplateDomainService;
import com.openhis.health.domain.custom.ordertemplate.service.OrderTemplateQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.OrderTemplateConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.OrderTemplateBinding;
import com.openhis.health.domain.share.enums.OrderTemplateKind;
import com.openhis.health.domain.share.enums.OrderTemplateLineKind;
import com.openhis.health.domain.share.enums.OrderTemplateScene;
import com.openhis.health.domain.share.enums.OrderTemplateSource;
import com.openhis.health.domain.share.enums.OrderTemplateStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 组套模板应用编排（IF-BAS-OTL-01～08、10、11）。
 *
 * <p>职责边界：维护端 CRUD（IF-01～06）；临床侧仅提供<strong>快捷数据源</strong>——列表、预览明细（仅头行结构，不含定价 enrich）、
 * 另存医嘱模板（IF-10）。套用预览的目录/价/库位由前端以行内定义 id 调 IF-DOR-11 {@code prepickedProductKeys} 拉取。
 * <strong>不</strong>代调用医嘱暂存/签发或护士划价写接口。</p>
 *
 * <p>接入方式：由 {@code whale-web} 的 {@code AppServiceControllerRegistrar} 注册为
 * {@code POST /api/app/orderTemplate/{methodName}}；不另写 {@code RestController}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTemplateAppService {

    /** 组套模板写侧。 */
    private final OrderTemplateDomainService orderTemplateDomainService;

    /** 组套模板读侧。 */
    private final OrderTemplateQueryService orderTemplateQueryService;

    /** Request/Command/DTO 映射。 */
    private final OrderTemplateMapper orderTemplateMapper;

    /** 行目录 enrich。 */
    private final OrderTemplateCatalogEnrichSupport orderTemplateCatalogEnrichSupport;

    /** 绑定 → 归属对象 id。 */
    private final OrderTemplateScopeResolver orderTemplateScopeResolver;

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 分页入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /** 就诊上下文读侧（IF-07/08/10）。 */
    private final EncounterContextFacade encounterContextFacade;

    /** 患者与就诊一致性门控。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /** 当前医务人员解析。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 当前会话/管辖科室解析。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /** 药品申请读侧（IF-10）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 诊疗申请读侧（IF-10）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 耗材申请读侧（IF-10）。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /** 频次读侧（IF-10 rateCode → frequencyId）。 */
    private final FrequencyQueryService frequencyQueryService;

    /** 术语码表读（中药用法 tcm_method_code）。 */
    private final CodeSystemQueryService codeSystemQueryService;

    /** 用药频次下拉。 */
    private final FrequencyOptionLoader frequencyOptionLoader;

    /**
     * IF-BAS-OTL-01：维护页初始化下拉选项。
     *
     * @param request 空请求体
     * @return 枚举下拉及 TCM 模板行编辑所需的用法/频次术语
     */
    public InitOrderTemplateMaintenanceResponse initOrderTemplateMaintenance(InitOrderTemplateMaintenanceRequest request) {
        // Asm：枚举 OptionDto 由 Mapper 装配，供维护页表单与筛选
        InitOrderTemplateMaintenanceResponse response = orderTemplateMapper.toInitOrderTemplateMaintenanceResponse();
        // Asm：TCM Tab 行编辑需中药用法术语（tcm_method_code），与医生站中医 Tab 同源
        response.setTcmMethodCodeOptions(
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE));
        // Asm：模板行默认频次下拉，与 IF-DOR-18 medicationRateOptions 同源
        response.setMedicationRateOptions(frequencyOptionLoader.loadActiveRateOptions());
        return response;
    }

    /**
     * IF-BAS-OTL-02：维护端分页查询（固定 MAINTAINED + 当前 Tab kind）。
     *
     * @param request 分页与筛选条件
     * @return 分页头列表
     */
    public PageMaintainedOrderTemplateResponse pageMaintainedOrderTemplates(PageMaintainedOrderTemplateRequest request) {
        // Gate：分页窗口合法，否则拒绝查询
        pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT,
            "请检查组套基本信息后重试");
        // Gate：当前 Tab 组套种类必须合法
        OrderTemplateKind kind = EnumUtil.fromCode(OrderTemplateKind.class, request.getTemplateKindEnum());
        if (kind == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套基本信息后重试");
        }
        String bindingFilter = normalizeOptionalCode(request.getBindingEnum());
        OrderTemplateScene sceneFilter = EnumUtil.fromCode(OrderTemplateScene.class, request.getSceneEnum());
        String statusFilter = normalizeOptionalCode(request.getStatusEnum());
        Pageable pageable = PageRequest.of(
            request.springPageIndex(),
            request.getBoundedPageSize(),
            Sort.by(Sort.Direction.ASC, FieldNameConstants.SORT_WEIGHT)
                .and(Sort.by(Sort.Direction.ASC, FieldNameConstants.ID))
        );
        // Pre：固定 MAINTAINED 来源 + kind 精确匹配（BR-BAS-OTL-007）
        Page<OrderTemplateHeaderQueryModel> page = orderTemplateQueryService.pageOrderTemplates(
            bindingFilter,
            sceneFilter,
            statusFilter,
            kind.getCode(),
            OrderTemplateSource.MAINTAINED.getCode(),
            pageable
        );
        // Asm：映射为分页响应，并按 keyword/searchKey 过滤当前页（SDD searchKey 与 B.10.11 keyword 二选一）
        String pageKeyword = resolvePageKeyword(request);
        return orderTemplateMapper.toPageMaintainedOrderTemplateResponse(page, pageKeyword);
    }

    /**
     * IF-BAS-OTL-03：维护端详情（头 + 全部有效行 + enrich）。
     *
     * @param request 头 id
     * @return 详情
     */
    public GetOrderTemplateDetailResponse getOrderTemplateDetail(GetOrderTemplateDetailRequest request) {
        // Pre：加载头行；不存在时领域层抛 ORDER_TEMPLATE_NOT_FOUND
        OrderTemplateWithLinesQueryModel model = orderTemplateQueryService.getOrderTemplateWithLines(
            request.getOrderTemplateId()
        );
        GetOrderTemplateDetailResponse response = orderTemplateMapper.toGetOrderTemplateDetailResponse(model);
        if (response == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_NOT_FOUND, "组套不存在或已删除");
        }
        // Asm：批量 enrich 目录/术语/执行科室展示
        orderTemplateCatalogEnrichSupport.enrichLines(response.getLines(), null);
        return response;
    }

    /**
     * IF-BAS-OTL-04：维护端保存头（新建/更新，固定 MAINTAINED）。
     *
     * @param request 头表单
     * @return 头 id
     */
    public SaveMaintainedOrderTemplateHeaderResponse saveMaintainedOrderTemplateHeader(
        SaveMaintainedOrderTemplateHeaderRequest request
    ) {
        // Gate：名称、场景、种类及 PRICING/TCM 专项校验
        validateSaveHeaderRequest(request);
        // Gate：解析归属对象（个人/科室/全院缺省补全）
        Long objectId = orderTemplateScopeResolver.resolveObjectId(request.getBindingEnum(), request.getObjectId());
        // Gate：写路径须已登录
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT,
            "未登录，无法保存组套");
        SaveMaintainedOrderTemplateHeaderResponse response = new SaveMaintainedOrderTemplateHeaderResponse();
        if (request.getOrderTemplateId() == null) {
            // Cmd：新建头，来源固定 MAINTAINED，初始 DRAFT
            Long newId = orderTemplateDomainService.createOrderTemplate(
                orderTemplateMapper.toCreateOrderTemplateCommand(request, operatorId, objectId)
            );
            response.setOrderTemplateId(newId);
            return response;
        }
        // Cmd：更新已有头属性（不含来源变更）
        orderTemplateDomainService.updateOrderTemplateHeader(
            orderTemplateMapper.toUpdateOrderTemplateHeaderCommand(request, operatorId)
        );
        response.setOrderTemplateId(request.getOrderTemplateId());
        return response;
    }

    /**
     * IF-BAS-OTL-05：维护端保存行（全量对齐）。
     *
     * @param request 头 id + 行全量集合
     * @return 有效行数
     */
    public SaveMaintainedOrderTemplateLinesResponse saveMaintainedOrderTemplateLines(
        SaveMaintainedOrderTemplateLinesRequest request
    ) {
        if (request.getLines() == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "组套明细不能为空");
        }
        // Pre：加载头行，断言头可维护（非 RETIRED/已删）
        OrderTemplateWithLinesQueryModel existingModel = orderTemplateQueryService.getOrderTemplateWithLines(
            request.getOrderTemplateId()
        );
        assertHeaderLinesMaintainable(existingModel);
        String templateKind = existingModel.getHeader().getTemplateKindEnum();
        // Gate：PRICING 组套禁止药品行（BR-OTL-026 / EX-BAS-OTL-204），覆盖新增与保留行
        for (OrderTemplateLineEditDto line : request.getLines()) {
            if (line == null || Boolean.TRUE.equals(line.getDeleted())) {
                continue;
            }
            assertPricingLineKindAllowed(templateKind, line);
        }
        // Gate：写路径操作人
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT,
            "未登录，无法保存组套明细");
        // Pre：计算增删改集合（全量对齐）
        LineDiffResult diff = orderTemplateMapper.diffLines(request.getLines(), existingModel.getLines());
        // Cmd：先删后改后增，同一 IF 同事务
        for (Long lineId : diff.getRemoveLineIds()) {
            orderTemplateDomainService.removeOrderTemplateLine(
                orderTemplateMapper.toRemoveLineCommand(lineId, operatorId)
            );
        }
        for (OrderTemplateLineEditDto updateLine : diff.getUpdateLines()) {
            orderTemplateDomainService.updateOrderTemplateLine(
                orderTemplateMapper.toUpdateLineCommand(updateLine, operatorId)
            );
        }
        for (OrderTemplateLineEditDto addLine : diff.getAddLines()) {
            orderTemplateDomainService.addOrderTemplateLine(
                orderTemplateMapper.toAddLineCommand(addLine, request.getOrderTemplateId(), operatorId)
            );
        }
        // Asm：回读有效行数
        OrderTemplateWithLinesQueryModel savedModel = orderTemplateQueryService.getOrderTemplateWithLines(
            request.getOrderTemplateId()
        );
        SaveMaintainedOrderTemplateLinesResponse response = new SaveMaintainedOrderTemplateLinesResponse();
        if (savedModel.getLines() == null) {
            response.setSavedLineCount(0);
        } else {
            response.setSavedLineCount(savedModel.getLines().size());
        }
        return response;
    }

    /**
     * IF-BAS-OTL-06：维护端发布 / 停用 / 逻辑删除。
     *
     * @param request 头 id + 动作码
     * @return 成功标志
     */
    public ChangeOrderTemplateLifecycleResponse changeOrderTemplateLifecycle(ChangeOrderTemplateLifecycleRequest request) {
        String action = request.getLifecycleActionCode();
        if (StringUtils.isBlank(action)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前状态不允许该操作");
        }
        String normalizedAction = action.trim().toUpperCase();
        // Gate：动作码白名单
        if (!OrderTemplateConstants.LifecycleAction.PUBLISH.equals(normalizedAction)
            && !OrderTemplateConstants.LifecycleAction.RETIRE.equals(normalizedAction)
            && !OrderTemplateConstants.LifecycleAction.DELETE.equals(normalizedAction)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前状态不允许该操作");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT,
            "未登录，无法变更组套状态");
        // Cmd：互斥执行单一生命周期动作
        if (OrderTemplateConstants.LifecycleAction.PUBLISH.equals(normalizedAction)) {
            PublishOrderTemplateCommand publishCommand = new PublishOrderTemplateCommand();
            publishCommand.setOrderTemplateId(request.getOrderTemplateId());
            publishCommand.setOperatorId(operatorId);
            orderTemplateDomainService.publishOrderTemplate(publishCommand);
        } else if (OrderTemplateConstants.LifecycleAction.RETIRE.equals(normalizedAction)) {
            RetireOrderTemplateCommand retireCommand = new RetireOrderTemplateCommand();
            retireCommand.setOrderTemplateId(request.getOrderTemplateId());
            retireCommand.setOperatorId(operatorId);
            orderTemplateDomainService.retireOrderTemplate(retireCommand);
        } else {
            DeleteOrderTemplateCommand deleteCommand = new DeleteOrderTemplateCommand();
            deleteCommand.setOrderTemplateId(request.getOrderTemplateId());
            deleteCommand.setOperatorId(operatorId);
            orderTemplateDomainService.deleteOrderTemplate(deleteCommand);
        }
        ChangeOrderTemplateLifecycleResponse response = new ChangeOrderTemplateLifecycleResponse();
        response.setSuccess(true);
        return response;
    }

    /**
     * IF-BAS-OTL-07：医生站可套用组套/医嘱模板列表。
     *
     * @param request 就诊、Tab 来源/种类与关键字
     * @return 个人/科室/全院分段列表
     */
    public ListDoctorStationOrderTemplateResponse listDoctorStationOrderTemplates(
        ListDoctorStationOrderTemplateRequest request
    ) {
        validateDoctorStationListRequest(request);
        EncounterSceneContext encounterContext = resolveDoctorStationEncounterContext(
            request.getEncounterId(),
            request.getPatientId()
        );
        Long practitionerId = currentPractitionerResolver.findPractitionerIdOrNull();
        Long departmentOrgId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        Long hospitalOrgId = orderTemplateScopeResolver.resolveObjectId(
            OrderTemplateBinding.HOSPITAL.getCode(), null);
        String source = request.getTemplateSourceEnum().trim();
        String kind = request.getTemplateKindEnum().trim();
        List<OrderTemplateHeaderQueryModel> personalList = listTemplatesForOwner(
            practitionerId, OrderTemplateBinding.PERSONAL.getCode(), encounterContext.getScene(), kind, source);
        List<OrderTemplateHeaderQueryModel> departmentList = listTemplatesForOwner(
            departmentOrgId, OrderTemplateBinding.DEPARTMENT.getCode(), encounterContext.getScene(), kind, source);
        List<OrderTemplateHeaderQueryModel> hospitalList = listTemplatesForOwner(
            hospitalOrgId, OrderTemplateBinding.HOSPITAL.getCode(), encounterContext.getScene(), kind, source);
        OrderTemplateBindingGroupDto groups = orderTemplateMapper.toListBindingGroupResponse(
            personalList, departmentList, hospitalList, request.getSearchKey());
        ListDoctorStationOrderTemplateResponse response = new ListDoctorStationOrderTemplateResponse();
        response.setGroups(groups);
        return response;
    }

    /**
     * IF-BAS-OTL-08：加载组套/模板明细（快捷开嘱/划价数据源）。
     *
     * <p>仅返回头行结构（定义 id、数量、用法等）；<strong>不</strong>做目录 enrich 与实时定价（性能）。
     * 前端以行内定义 id 调 IF-DOR-11 {@code prepickedProductKeys}（与历史医嘱/历史处方同套路）补齐展示名、
     * {@code unitPrice}、{@code defaultDispenseLocationId}；暂存 IF-DOR-14 回传 {@code dispenseLocationId}，
     * 写路径不再二次 match 取药科室模板。</p>
     *
     * @param request 模板 id、上下文与就诊
     * @return 头行预览（无 enrich、无价字段）
     */
    public PreviewOrderTemplateApplyResponse previewOrderTemplateApply(PreviewOrderTemplateApplyRequest request) {
        String applyContext = normalizeApplyContextCode(request.getApplyContextCode());
        // Gate：加载组套头行（QueryService 只读，不做 catalog enrich / 定价）
        OrderTemplateWithLinesQueryModel model = orderTemplateQueryService.getOrderTemplateWithLines(
            request.getOrderTemplateId()
        );
        OrderTemplateScene encounterScene = null;
        if (OrderTemplateConstants.ApplyContext.DOCTOR.equals(applyContext)) {
            // 医生站：按工作站类型解析就诊场景，用于模板 kind/scene 门禁
            EncounterSceneContext encounterContext = resolveEncounterContextByWorkstation(
                request.getEncounterId(),
                request.getPatientId(),
                request.getWorkstationKindCode()
            );
            encounterScene = encounterContext.getScene();
        } else if (OrderTemplateConstants.ApplyContext.NURSE_PRICING.equals(applyContext)) {
            // 护士划价：固定住院场景（组套后走 IF-IP-PRC-01 prepicked 目录）
            encounterScene = OrderTemplateScene.INPATIENT;
        }
        orderTemplateMapper.assertPreviewable(
            model.getHeader(),
            applyContext,
            encounterScene,
            request.getWorkstationKindCode()
        );
        // Asm：结构映射；价/库位由前端 prepicked 调 IF-DOR-11 / IF-IP-PRC-01 补齐
        PreviewOrderTemplateApplyResponse response = orderTemplateMapper.toPreviewOrderTemplateApplyResponse(model);
        if (response == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_NOT_FOUND, "组套不存在或已删除");
        }
        return response;
    }

    /**
     * IF-BAS-OTL-10：医生站另存为医嘱模板（FROM_ADVICE + 个人/科室 + 发布）。
     *
     * @param request 模板名、绑定类型与源行键
     * @return 新建模板 id
     */
    public SaveOrderTemplateFromAdviceResponse saveOrderTemplateFromAdvice(SaveOrderTemplateFromAdviceRequest request) {
        // Gate：须至少选中一条源医嘱
        if (request.getSourceLines() == null || request.getSourceLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请至少选择一条医嘱");
        }
        // Gate：另存仅允许个人或科室归属，缺省按科室（BR-BAS-OTL-002）
        String bindingEnum = resolveSaveFromAdviceBindingEnum(request.getBindingEnum());
        OrderTemplateBinding binding = EnumUtil.fromCode(OrderTemplateBinding.class, bindingEnum);
        if (binding != OrderTemplateBinding.PERSONAL && binding != OrderTemplateBinding.DEPARTMENT) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套基本信息后重试");
        }
        // Pre：就诊场景门控，推导门诊/住院 scene
        EncounterSceneContext encounterContext = resolveDoctorStationEncounterContext(
            request.getEncounterId(),
            request.getPatientId()
        );
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT,
            "未登录，无法保存医嘱模板");
        // Pre：按绑定类型解析 object_id（个人→医务人员，科室→会话科室；BR-BAS-OTL-008）
        Long objectId = orderTemplateScopeResolver.resolveObjectId(bindingEnum, null);
        boolean anyTcm = false;
        int sortOrder = 0;
        for (AdviceSourceLineKeyDto sourceLine : request.getSourceLines()) {
            if (sourceLine == null) {
                continue;
            }
            if (isTcmAdviceLine(sourceLine, request.getEncounterId(), request.getPatientId())) {
                anyTcm = true;
            }
        }
        String templateKind = orderTemplateMapper.deriveTemplateKindFromAdviceLines(anyTcm);
        // Cmd：创建头（FROM_ADVICE；个人或科室归属）
        Long templateId = orderTemplateDomainService.createOrderTemplate(
            orderTemplateMapper.toCreateOrderTemplateFromAdviceCommand(
                request,
                encounterContext.getScene(),
                templateKind,
                bindingEnum,
                objectId,
                operatorId
            )
        );
        for (AdviceSourceLineKeyDto sourceLine : request.getSourceLines()) {
            if (sourceLine == null) {
                continue;
            }
            sortOrder = sortOrder + 1;
            AddOrderTemplateLineCommand addCommand = buildAddLineCommandFromAdvice(
                sourceLine,
                templateId,
                sortOrder,
                operatorId,
                request.getEncounterId(),
                request.getPatientId(),
                templateKind
            );
            orderTemplateDomainService.addOrderTemplateLine(addCommand);
        }
        // Cmd：同事务发布为 ACTIVE，存完即可在 IF-07 列表引用（BR-BAS-OTL-002）
        PublishOrderTemplateCommand publishCommand = new PublishOrderTemplateCommand();
        publishCommand.setOrderTemplateId(templateId);
        publishCommand.setOperatorId(operatorId);
        orderTemplateDomainService.publishOrderTemplate(publishCommand);
        // Asm：返回新建模板 id
        SaveOrderTemplateFromAdviceResponse response = new SaveOrderTemplateFromAdviceResponse();
        response.setOrderTemplateId(templateId);
        return response;
    }

    /**
     * IF-BAS-OTL-11：护士站划价组套列表（PRICING + MAINTAINED + INPATIENT）。
     *
     * @param request 名称关键字
     * @return 分段列表
     */
    public ListNurseStationPricingOrderTemplateResponse listNurseStationPricingOrderTemplates(
        ListNurseStationPricingOrderTemplateRequest request
    ) {
        String kind = OrderTemplateKind.PRICING.getCode();
        String source = OrderTemplateSource.MAINTAINED.getCode();
        OrderTemplateScene scene = OrderTemplateScene.INPATIENT;
        Long practitionerId = currentPractitionerResolver.findPractitionerIdOrNull();
        Long departmentOrgId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        Long hospitalOrgId = orderTemplateScopeResolver.resolveObjectId(
            OrderTemplateBinding.HOSPITAL.getCode(), null);
        List<OrderTemplateHeaderQueryModel> personalList = listTemplatesForOwner(
            practitionerId, OrderTemplateBinding.PERSONAL.getCode(), scene, kind, source);
        List<OrderTemplateHeaderQueryModel> departmentList = listTemplatesForOwner(
            departmentOrgId, OrderTemplateBinding.DEPARTMENT.getCode(), scene, kind, source);
        List<OrderTemplateHeaderQueryModel> hospitalList = listTemplatesForOwner(
            hospitalOrgId, OrderTemplateBinding.HOSPITAL.getCode(), scene, kind, source);
        OrderTemplateBindingGroupDto groups = orderTemplateMapper.toListBindingGroupResponse(
            personalList, departmentList, hospitalList, request.getSearchKey());
        ListNurseStationPricingOrderTemplateResponse response = new ListNurseStationPricingOrderTemplateResponse();
        response.setGroups(groups);
        return response;
    }

    /**
     * 保存头表单 Gate：名称、场景、种类及 PRICING/TCM 规则。
     *
     * @param request 保存头请求
     */
    private void validateSaveHeaderRequest(SaveMaintainedOrderTemplateHeaderRequest request) {
        if (StringUtils.isBlank(request.getName())) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套基本信息后重试");
        }
        // BR-OTL-001：绑定类型须合法
        OrderTemplateBinding binding = EnumUtil.fromCode(OrderTemplateBinding.class, request.getBindingEnum());
        if (binding == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套基本信息后重试");
        }
        OrderTemplateScene scene = EnumUtil.fromCode(OrderTemplateScene.class, request.getSceneEnum());
        if (scene == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套基本信息后重试");
        }
        OrderTemplateKind kind = EnumUtil.fromCode(OrderTemplateKind.class, request.getTemplateKindEnum());
        if (kind == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套基本信息后重试");
        }
        // PRICING 仅允许住院或通用场景（BR-OTL-027）
        if (kind == OrderTemplateKind.PRICING) {
            if (scene != OrderTemplateScene.INPATIENT && scene != OrderTemplateScene.COMMON) {
                throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套基本信息后重试");
            }
        }
        // TCM 默认付数必填且大于 0（BR-OTL-023）
        if (kind == OrderTemplateKind.TCM) {
            BigDecimal dose = request.getDefaultChineseHerbsDoseQuantity();
            if (dose == null || dose.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套基本信息后重试");
            }
        }
    }

    /**
     * 断言头处于可维护行状态（DRAFT/ACTIVE，非 RETIRED）。
     *
     * @param model 头行读模型
     */
    private void assertHeaderLinesMaintainable(OrderTemplateWithLinesQueryModel model) {
        if (model == null || model.getHeader() == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_NOT_FOUND, "组套不存在或已删除");
        }
        String status = model.getHeader().getStatusEnum();
        if (OrderTemplateStatus.RETIRED.getCode().equals(status)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前组套不可编辑");
        }
    }

    /**
     * 解析分页关键字：优先 {@code keyword}，其次 SDD 字段 {@code searchKey}。
     *
     * @param request 分页请求
     * @return trim 后关键字，均无则 null
     */
    private String resolvePageKeyword(PageMaintainedOrderTemplateRequest request) {
        if (StringUtils.isNotBlank(request.getKeyword())) {
            return request.getKeyword().trim();
        }
        if (StringUtils.isNotBlank(request.getSearchKey())) {
            return request.getSearchKey().trim();
        }
        return null;
    }

    /**
     * 划价组套禁止药品行（BR-OTL-026 / EX-BAS-OTL-204）。
     *
     * @param templateKind 头种类
     * @param line 待校验行
     */
    private void assertPricingLineKindAllowed(String templateKind, OrderTemplateLineEditDto line) {
        if (!OrderTemplateKind.PRICING.getCode().equals(templateKind)) {
            return;
        }
        if (line != null && OrderTemplateLineKind.MEDICATION.getCode().equals(line.getLineKindEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT,
                "划价组套不可包含药品");
        }
    }

    /**
     * 可选筛选 code 归一：空白 → null。
     *
     * @param code 原始 code
     * @return trim 后 code 或 null
     */
    private String normalizeOptionalCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return code.trim();
    }

    /**
     * IF-10：另存绑定类型归一；缺省为科室（DEPARTMENT）。
     *
     * @param bindingEnum 请求携带的绑定 code，可空
     * @return trim 后 code，空白时 DEPARTMENT
     */
    private String resolveSaveFromAdviceBindingEnum(String bindingEnum) {
        if (StringUtils.isBlank(bindingEnum)) {
            return OrderTemplateBinding.DEPARTMENT.getCode();
        }
        return bindingEnum.trim();
    }

    /**
     * IF-07 Gate：医生站列表 source/kind 校验（禁止 PRICING + FROM_ADVICE 组合 Tab 误用）。
     */
    private void validateDoctorStationListRequest(ListDoctorStationOrderTemplateRequest request) {
        OrderTemplateSource source = EnumUtil.fromCode(OrderTemplateSource.class, request.getTemplateSourceEnum());
        if (source == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套查询条件后重试");
        }
        OrderTemplateKind kind = EnumUtil.fromCode(OrderTemplateKind.class, request.getTemplateKindEnum());
        if (kind == null || kind == OrderTemplateKind.PRICING) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套查询条件后重试");
        }
        if (source == OrderTemplateSource.FROM_ADVICE && kind == OrderTemplateKind.PRICING) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "医嘱模板不支持划价种类");
        }
    }

    /**
     * 按归属对象加载 ACTIVE 模板头（scene 含 COMMON 并集）。
     */
    private List<OrderTemplateHeaderQueryModel> listTemplatesForOwner(
        Long objectId,
        String bindingEnum,
        OrderTemplateScene scene,
        String kind,
        String source
    ) {
        if (objectId == null) {
            return List.of();
        }
        return orderTemplateQueryService.listOrderTemplatesByOwnerAndScene(
            objectId, bindingEnum, scene, kind, source);
    }

    /**
     * 医生站列表：按 encounterId 推导门诊/住院场景。
     */
    private EncounterSceneContext resolveDoctorStationEncounterContext(Long encounterId, Long patientId) {
        Optional<InpatientEncounterContextQueryModel> inpatient =
            encounterContextFacade.getInpatientContextByEncounterId(encounterId);
        if (inpatient.isPresent()) {
            patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
                patientId,
                inpatient.get().getPatientId(),
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "患者与就诊不一致");
            EncounterSceneContext context = new EncounterSceneContext();
            context.setScene(OrderTemplateScene.INPATIENT);
            context.setPerformOrgContextId(inpatient.get().getOrganizationId());
            return context;
        }
        Optional<OutpatientEncounterContextQueryModel> outpatient =
            encounterContextFacade.getOutpatientContextByEncounterId(encounterId);
        if (outpatient.isPresent()) {
            patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
                patientId,
                outpatient.get().getPatientId(),
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "患者与就诊不一致");
            EncounterSceneContext context = new EncounterSceneContext();
            context.setScene(OrderTemplateScene.OUTPATIENT);
            context.setPerformOrgContextId(outpatient.get().getOrganizationId());
            return context;
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊不存在或已失效");
    }

    /**
     * IF-08/09：按工作站类型加载就诊上下文。
     */
    private EncounterSceneContext resolveEncounterContextByWorkstation(
        Long encounterId,
        Long patientId,
        String workstationKindCode
    ) {
        if (StringUtils.isBlank(workstationKindCode)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "工作站类型不能为空");
        }
        String normalizedWorkstation = workstationKindCode.trim().toUpperCase();
        if (OrderTemplateConstants.WorkstationKind.INPATIENT.equals(normalizedWorkstation)) {
            InpatientEncounterContextQueryModel inpatient =
                encounterContextFacade.mustGetInpatientContextByEncounterId(encounterId);
            patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
                patientId,
                inpatient.getPatientId(),
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "患者与就诊不一致");
            EncounterSceneContext context = new EncounterSceneContext();
            context.setScene(OrderTemplateScene.INPATIENT);
            context.setPerformOrgContextId(inpatient.getOrganizationId());
            return context;
        }
        if (OrderTemplateConstants.WorkstationKind.OUTPATIENT.equals(normalizedWorkstation)) {
            OutpatientEncounterContextQueryModel outpatient =
                encounterContextFacade.mustGetOutpatientContextByEncounterId(encounterId);
            patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
                patientId,
                outpatient.getPatientId(),
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "患者与就诊不一致");
            EncounterSceneContext context = new EncounterSceneContext();
            context.setScene(OrderTemplateScene.OUTPATIENT);
            context.setPerformOrgContextId(outpatient.getOrganizationId());
            return context;
        }
        throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "工作站类型不合法");
    }

    /**
     * IF-10：源医嘱行 → 新增组套行命令。
     */
    private AddOrderTemplateLineCommand buildAddLineCommandFromAdvice(
        AdviceSourceLineKeyDto sourceLine,
        Long templateId,
        int sortOrder,
        Long operatorId,
        Long encounterId,
        Long patientId,
        String templateKind
    ) {
        AddOrderTemplateLineCommand command = new AddOrderTemplateLineCommand();
        command.setOrderTemplateId(templateId);
        command.setOperatorId(operatorId);
        String tableName = sourceLine.getProductTableName().trim();
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(tableName)) {
            MedicationRequest medication = loadMedicationAdviceLine(sourceLine.getRequestId(), encounterId, patientId);
            if (Boolean.TRUE.equals(medication.getTcmFlag()) && OrderTemplateKind.TCM.getCode().equals(templateKind)) {
                command.setChineseHerbsDoseQuantity(medication.getChineseHerbsDoseQuantity());
                command.setSufferingFlag(medication.getSufferingFlag());
            }
            orderTemplateMapper.fillAddOrderTemplateLineFromMedicationAdvice(
                command,
                medication.getMedicationId(),
                medication.getQuantity(),
                medication.getUnitCode(),
                medication.getMethodCode(),
                resolveFrequencyIdByRateCode(medication.getRateCode()),
                medication.getDispensePerDuration(),
                medication.getDosageInstruction(),
                resolveGroupKeyFromGroupId(medication.getGroupId()),
                sortOrder,
                medication.getChineseHerbsDoseQuantity(),
                medication.getSufferingFlag(),
                medication.getOrgId(),
                medication.getRemark()
            );
            return command;
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(tableName)) {
            ServiceRequestListModel service = loadServiceAdviceLine(sourceLine.getRequestId(), encounterId, patientId);
            orderTemplateMapper.fillAddOrderTemplateLineFromServiceAdvice(
                command,
                service.getActivityId(),
                service.getQuantity(),
                service.getUnitCode(),
                service.getOrgId(),
                sortOrder,
                service.getRemark()
            );
            return command;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(tableName)) {
            DeviceRequestListModel device = loadDeviceAdviceLine(sourceLine.getRequestId(), encounterId, patientId);
            orderTemplateMapper.fillAddOrderTemplateLineFromDeviceAdvice(
                command,
                device.getDeviceDefId(),
                device.getQuantity(),
                device.getUnitCode(),
                device.getLocationId(),
                sortOrder,
                device.getRemark()
            );
            return command;
        }
        throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "不支持的医嘱产品类型");
    }

    /**
     * IF-10：判断源行是否为 TCM 药品（推导模板种类）。
     */
    private boolean isTcmAdviceLine(AdviceSourceLineKeyDto sourceLine, Long encounterId, Long patientId) {
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(sourceLine.getProductTableName())) {
            return false;
        }
        MedicationRequest medication = loadMedicationAdviceLine(sourceLine.getRequestId(), encounterId, patientId);
        return Boolean.TRUE.equals(medication.getTcmFlag());
    }

    /**
     * 加载并校验药品源行。
     */
    private MedicationRequest loadMedicationAdviceLine(Long requestId, Long encounterId, Long patientId) {
        MedicationRequest medication = medicationRequestQueryService.findById(requestId);
        if (medication == null || Boolean.TRUE.equals(medication.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "选中医嘱不存在或已删除");
        }
        assertAdviceLineBelongsToEncounter(medication.getEncounterId(), medication.getPatientId(), encounterId, patientId);
        return medication;
    }

    /**
     * 加载并校验诊疗源行。
     */
    private ServiceRequestListModel loadServiceAdviceLine(Long requestId, Long encounterId, Long patientId) {
        ServiceRequestListModel service = serviceRequestQueryService.mustGetListModelById(requestId);
        assertAdviceLineBelongsToEncounter(service.getEncounterId(), service.getPatientId(), encounterId, patientId);
        return service;
    }

    /**
     * 加载并校验耗材源行。
     */
    private DeviceRequestListModel loadDeviceAdviceLine(Long requestId, Long encounterId, Long patientId) {
        DeviceRequestListModel device = deviceRequestQueryService.mustGetListModelById(requestId);
        assertAdviceLineBelongsToEncounter(device.getEncounterId(), device.getPatientId(), encounterId, patientId);
        return device;
    }

    /**
     * 源行须属于当前就诊与患者。
     */
    private void assertAdviceLineBelongsToEncounter(
        Long lineEncounterId,
        Long linePatientId,
        Long encounterId,
        Long patientId
    ) {
        if (lineEncounterId == null || !lineEncounterId.equals(encounterId)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "选中医嘱不属于当前就诊");
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            patientId,
            linePatientId,
            ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT,
            "选中医嘱不属于当前患者");
    }

    /**
     * rateCode → frequencyId（IF-10 另存模板）。
     */
    private Long resolveFrequencyIdByRateCode(String rateCode) {
        if (StringUtils.isBlank(rateCode)) {
            return null;
        }
        for (Frequency frequency : frequencyQueryService.listActiveFrequencies()) {
            if (frequency == null || frequency.getId() == null) {
                continue;
            }
            if (rateCode.equals(frequency.getRateCode())) {
                return frequency.getId();
            }
        }
        return null;
    }

    /**
     * 药品 groupId → 模板 groupKey 字符串。
     */
    private String resolveGroupKeyFromGroupId(Long groupId) {
        if (groupId == null) {
            return null;
        }
        return String.valueOf(groupId);
    }

    /**
     * applyContextCode 归一。
     */
    private String normalizeApplyContextCode(String applyContextCode) {
        if (StringUtils.isBlank(applyContextCode)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "套用上下文不能为空");
        }
        return applyContextCode.trim().toUpperCase();
    }

    /**
     * 就诊场景与执行科室上下文。
     */
    private static final class EncounterSceneContext {

        /** 组套适用场景。 */
        private OrderTemplateScene scene;

        /** enrich 用执行科室上下文 id。 */
        private Long performOrgContextId;

        public OrderTemplateScene getScene() {
            return scene;
        }

        public void setScene(OrderTemplateScene scene) {
            this.scene = scene;
        }

        public Long getPerformOrgContextId() {
            return performOrgContextId;
        }

        public void setPerformOrgContextId(Long performOrgContextId) {
            this.performOrgContextId = performOrgContextId;
        }
    }
}
