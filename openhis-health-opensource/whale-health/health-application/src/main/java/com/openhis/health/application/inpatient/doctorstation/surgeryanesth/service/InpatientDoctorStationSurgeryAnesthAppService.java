/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormLineRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto.PlannedSurgeryFieldsDto;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormHeaderDto;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormLineRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.mapper.InpatientDoctorStationSurgeryAnesthMapper;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.GetAnesthesiaApplyFormDetailRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.GetPlannedSurgeryFieldsRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.GetSurgeryApplyFormDetailRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.InitSurgeryAnesthRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.ListAnesthesiaApplyFormsRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.ListSurgeryApplyFormsRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.SaveAnesthesiaApplyFormRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.SaveSurgeryApplyFormRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.SearchClinicalProcV3Request;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.SearchSurchargeActivityDefinitionRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.SignAnesthesiaApplyFormRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.SignSurgeryApplyFormRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.ToggleAnesthesiaNestRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.WithdrawAnesthesiaApplyFormRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request.WithdrawSurgeryApplyFormRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.GetAnesthesiaApplyFormDetailResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.GetPlannedSurgeryFieldsResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.GetSurgeryApplyFormDetailResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.InitSurgeryAnesthResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.ListAnesthesiaApplyFormsResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.ListSurgeryApplyFormsResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.SaveAnesthesiaApplyFormResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.SaveSurgeryApplyFormResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.SearchClinicalProcV3Response;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.SearchSurchargeActivityDefinitionResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.SignAnesthesiaApplyFormResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.SignSurgeryApplyFormResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.ToggleAnesthesiaNestResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.WithdrawAnesthesiaApplyFormResponse;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response.WithdrawSurgeryApplyFormResponse;
import com.openhis.health.application.shared.advice.ServiceApplyFormOrchestrationSupport;
import com.openhis.health.application.shared.advice.ServiceRequestDetailListAssembler;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionDetailQueryModel;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.proceduredefinition.service.ProcedureDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.constants.SurgeryAnesthApplyConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.WithdrawSignedServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医生站手术与麻醉申请应用编排（AS-IP-DOR-012 · IF-IP-SUR-01～15）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>经 {@link EncounterContextReader} 门控住院就诊可写/可读上下文</li>
 *   <li>经 {@link ServiceRequestDomainService}/{@link ServiceRequestQueryService} 维护手术/麻醉 AR（多行 SR）</li>
 *   <li>经 {@link ProcedureDefinitionQueryService}/{@link ActivityDefinitionQueryService} 做国临与加收选品校验</li>
 *   <li>经 {@link ServiceApplyFormOrchestrationSupport} 领取 AR 号；不创建 Procedure、不写病案首页</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientDoctorStationSurgeryAnesth/{methodName}}；不另写 RestController。</p>
 */
@Service
@RequiredArgsConstructor
public class InpatientDoctorStationSurgeryAnesthAppService {

    /** 住院就诊门控。 */
    private final EncounterContextReader encounterContextReader;
    /** 术语下拉。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 服务申请读侧。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 服务申请写侧。 */
    private final ServiceRequestDomainService serviceRequestDomainService;
    /** 国临手术定义读侧。 */
    private final ProcedureDefinitionQueryService procedureDefinitionQueryService;
    /** 诊疗项目读侧。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 医务人员读侧。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 当前操作人（practitioner）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** AR 号领取。 */
    private final ServiceApplyFormOrchestrationSupport serviceApplyFormOrchestrationSupport;
    /** 明细批量装配。 */
    private final ServiceRequestDetailListAssembler serviceRequestDetailListAssembler;
    /** DTO/Command 映射。 */
    private final InpatientDoctorStationSurgeryAnesthMapper inpatientDoctorStationSurgeryAnesthMapper;

    /**
     * IF-IP-SUR-01：开立页初始化字典。
     *
     * @param request 就诊标识
     * @return 下拉选项
     */
    public InitSurgeryAnesthResponse initSurgeryAnesth(InitSurgeryAnesthRequest request) {
        // Gate：就诊标识非空
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_ENCOUNTER_ID_REQUIRED);
        }
        // Pre：住院就诊门控
        encounterContextReader.mustGetInpatientContextByEncounterId(request.getEncounterId());
        // Pre：术语码系下拉
        InitSurgeryAnesthResponse response = new InitSurgeryAnesthResponse();
        response.setProcedureLevelOptions(codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.OPRN_OPRT_LV_CODE));
        response.setIncisionTypeOptions(codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.SINC_HEAL_LV_CODE));
        response.setAnesthesiaMethodOptions(codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.ANST_MTD_CODE));
        response.setYesNoOptions(codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.YES_NO_FLAG));
        // Asm：返回 Response
        return response;
    }

    /**
     * IF-IP-SUR-02：手术申请单列表。
     *
     * @param request 就诊标识
     * @return 按 AR 分组的头列表
     */
    public ListSurgeryApplyFormsResponse listSurgeryApplyForms(ListSurgeryApplyFormsRequest request) {
        // Gate：就诊标识必填
        Long encounterId = requireEncounterId(resolveEncounterId(request));
        // Pre：住院就诊可读门控
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        // Pre：筛医生开立、AR 前缀、类目 24 的手术申请行
        List<ServiceRequestListModel> rows = filterApplyFormRows(
            encounterId, ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        // Pre：批量 enrich 开立人与术式/AD 展示名
        EnrichBundle bundle = buildListEnrichBundle(rows);
        // Asm：按 AR 分组头 DTO；嵌套麻醉号优先行字段，空则 basedOn 反查（toggle 嵌套路径）
        ListSurgeryApplyFormsResponse response = new ListSurgeryApplyFormsResponse();
        List<SurgeryApplyFormHeaderDto> headers = inpatientDoctorStationSurgeryAnesthMapper.toSurgeryFormHeaders(
            rows,
            bundle.requesterNameById,
            bundle.pdByActivityId,
            bundle.activityById);
        for (SurgeryApplyFormHeaderDto header : headers) {
            // 已有嵌套号则跳过反查，避免重复扫麻醉行
            if (header == null || isNotEmpty(header.getNestedAnesthesiaPrescriptionNo())) {
                continue;
            }
            List<ServiceRequestListModel> formRows = filterFormRowsByPrescription(
                encounterId,
                header.getPrescriptionNo(),
                ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
            header.setNestedAnesthesiaPrescriptionNo(
                resolveNestedAnesthesiaPrescriptionNo(formRows, encounterId));
        }
        response.setForms(headers);
        return response;
    }

    /**
     * IF-IP-SUR-03：手术申请单详情。
     *
     * @param request 就诊与 AR 号
     * @return 头/行/横表
     */
    public GetSurgeryApplyFormDetailResponse getSurgeryApplyFormDetail(GetSurgeryApplyFormDetailRequest request) {
        // Gate：就诊与手术 AR 号必填
        Long encounterId = requireEncounterId(resolveEncounterId(request));
        String prescriptionNo = requirePrescriptionNo(resolvePrescriptionNo(request));
        // Pre：住院就诊可读门控
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        // Pre：按 AR 定位手术单内全部 SR 行
        List<ServiceRequestListModel> rows = filterFormRowsByPrescription(
            encounterId, prescriptionNo, ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        // 无匹配行则无法展示详情，直接报申请单不存在
        if (rows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
        }
        // Pre：子表明细与拟人员姓名 enrich
        EnrichBundle bundle = buildDetailEnrichBundle(rows);
        // Asm：头/行/拟手术横表 DTO
        GetSurgeryApplyFormDetailResponse detail = inpatientDoctorStationSurgeryAnesthMapper.toSurgeryFormDetail(
            rows,
            bundle.detailsByRequestId,
            bundle.pdByActivityId,
            bundle.activityById,
            bundle.practitionerNameById);
        // Asm：头上嵌套麻醉号空时按 basedOn 反查补齐（与列表同口径）
        if (detail.getHeader() != null && isEmpty(detail.getHeader().getNestedAnesthesiaPrescriptionNo())) {
            detail.getHeader().setNestedAnesthesiaPrescriptionNo(
                resolveNestedAnesthesiaPrescriptionNo(rows, encounterId));
        }
        return detail;
    }

    /**
     * IF-IP-SUR-04：检索国临手术定义。
     *
     * @param request 关键字
     * @return 候选列表
     */
    public SearchClinicalProcV3Response searchClinicalProcV3(SearchClinicalProcV3Request request) {
        // Gate：国临检索关键字非空
        if (request == null || isEmpty(request.getKeyword())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_KEYWORD_REQUIRED);
        }
        // Pre：国临检索
        List<ProcedureDefinitionSearchItemQueryModel> searchItems =
            procedureDefinitionQueryService.searchClinicalProcV3ByKeyword(request.getKeyword().trim());
        Set<Long> activityIds = new HashSet<>();
        // 汇总国临候选绑定的 AD，便于批量 enrich 展示名
        for (ProcedureDefinitionSearchItemQueryModel item : searchItems) {
            if (item != null && item.getActivityDefinitionId() != null) {
                activityIds.add(item.getActivityDefinitionId());
            }
        }
        Map<Long, ActivityDefinition> activityById = mapActivitiesById(activityIds);
        // Asm：国临候选 + AD 名称 DTO
        SearchClinicalProcV3Response response = new SearchClinicalProcV3Response();
        response.setItems(inpatientDoctorStationSurgeryAnesthMapper.toClinicalProcV3Items(searchItems, activityById));
        return response;
    }

    /**
     * IF-IP-SUR-05：检索加收诊疗项目。
     *
     * @param request 侧别与分页
     * @return 加收 AD 分页结果
     */
    public SearchSurchargeActivityDefinitionResponse searchSurchargeActivityDefinition(
        SearchSurchargeActivityDefinitionRequest request) {
        // Gate：侧别合法
        String sideCode = resolveSideCode(request);
        String categoryCode = resolveSurchargeCategoryCode(sideCode);
        int pageNo = 1;
        int pageSize = 20;
        // 未传或非法页码则回落默认第 1 页，避免空页
        if (request != null && request.getPageNo() != null && request.getPageNo() > 0) {
            pageNo = request.getPageNo();
        }
        // 未传或非法页大小则回落默认 20 条，控制单次检索量
        if (request != null && request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        }
        ProductDefinitionCatalogPageQuery query = new ProductDefinitionCatalogPageQuery();
        query.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        query.setCategoryCode(categoryCode);
        query.setActiveFlag(Boolean.TRUE);
        // 有关键字则走模糊检索，否则返回侧别类目下 ACTIVE 加收项
        if (request != null && isNotEmpty(request.getKeyword())) {
            query.setKeyword(request.getKeyword().trim());
        }
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        // Pre：按手术/麻醉侧类目分页检索 ACTIVE 加收 AD
        Page<ActivityDefinition> page = activityDefinitionQueryService.pageForCatalog(
            query, PageRequest.of(pageNo - 1, pageSize));
        // Asm：加收候选分页 DTO
        return inpatientDoctorStationSurgeryAnesthMapper.toSurchargeItems(page);
    }

    /**
     * IF-IP-SUR-06：保存手术申请单。
     *
     * @param request 行与横表
     * @return 新 AR 号与行 id
     */
    public SaveSurgeryApplyFormResponse saveSurgeryApplyForm(SaveSurgeryApplyFormRequest request) {
        // Gate：行级校验
        validateSurgerySaveGate(request);
        Long operatorId = requireOperatorId();
        // Pre：就诊门控
        InpatientEncounterContextQueryModel context =
            encounterContextReader.mustGetInpatientContextByEncounterId(request.getEncounterId());
        // Pre：PD/AD 校验
        validateSurgeryLinesPre(request.getLines());
        Map<Long, ActivityDefinition> activityById = loadAndValidateActivities(collectActivityIdsFromSurgeryLines(request.getLines()));
        // Cmd：删旧草稿
        // 编辑保存须先软删同 AR 下旧 DRAFT 行，避免重复草稿
        if (isNotEmpty(request.getPreviousPrescriptionNo())) {
            removePreviousDraftApplyForm(
                request.getEncounterId(),
                request.getPreviousPrescriptionNo().trim(),
                ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE,
                operatorId);
        }
        boolean nestAnesthesia = Boolean.TRUE.equals(request.getNestAnesthesia());
        String nestedAnesthesiaPrescriptionNo = null;
        // 勾选嵌套麻醉时预领麻醉 AR，写入手术行 nested 字段供后续关联
        if (nestAnesthesia) {
            nestedAnesthesiaPrescriptionNo = serviceApplyFormOrchestrationSupport.allocateArPrescriptionNo();
        }
        // Pre/Cmd：领取手术 AR 并逐行创建
        String prescriptionNo = serviceApplyFormOrchestrationSupport.allocateArPrescriptionNo();
        List<Long> serviceRequestIds = new ArrayList<>();
        SurgeryApplyFormLineRequest firstProcedureLine = findFirstProcedureLine(request.getLines());
        Long firstProcedureServiceRequestId = null;
        // 逐行创建手术 SR 草稿，并记录首条术式行 id 供嵌套麻醉 basedOn
        for (SurgeryApplyFormLineRequest line : request.getLines()) {
            CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
                inpatientDoctorStationSurgeryAnesthMapper.toCreateDraftItem(
                    line, context, prescriptionNo, request.getPlanned(), nestedAnesthesiaPrescriptionNo);
            Long createdId = createOneDraft(operatorId, item);
            serviceRequestIds.add(createdId);
            // 首条术式行 id 作为嵌套麻醉壳行的手术锚点
            if (firstProcedureServiceRequestId == null
                && SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(trimToEmpty(line.getLineKindCode()))) {
                firstProcedureServiceRequestId = createdId;
            }
        }
        // 嵌套开启且存在术式行时，追加创建麻醉壳行（不替代用户后续编辑麻醉单）
        if (nestAnesthesia && firstProcedureLine != null && nestedAnesthesiaPrescriptionNo != null) {
            Long firstSurgerySrId = firstProcedureServiceRequestId;
            // 无术式锚点则无法建立 basedOn，保存失败
            if (firstSurgerySrId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_PROCEDURE_LINE_REQUIRED);
            }
            ActivityDefinition ad = activityById.get(firstProcedureLine.getActivityDefinitionId());
            String unitCode = resolveLineUnitCode(firstProcedureLine, ad);
            CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand shell =
                inpatientDoctorStationSurgeryAnesthMapper.toNestAnesthesiaShellDraftItem(
                    context,
                    nestedAnesthesiaPrescriptionNo,
                    firstSurgerySrId,
                    firstProcedureLine.getActivityDefinitionId(),
                    unitCode,
                    request.getPlanned());
            createOneDraft(operatorId, shell);
        }
        // Asm
        SaveSurgeryApplyFormResponse response = new SaveSurgeryApplyFormResponse();
        response.setPrescriptionNo(prescriptionNo);
        response.setServiceRequestIds(serviceRequestIds);
        response.setNestedAnesthesiaPrescriptionNo(nestedAnesthesiaPrescriptionNo);
        return response;
    }

    /**
     * IF-IP-SUR-07：签发手术申请单。
     *
     * @param request AR 号与签发码
     * @return 已签发行 id
     */
    public SignSurgeryApplyFormResponse signSurgeryApplyForm(SignSurgeryApplyFormRequest request) {
        Long encounterId = null;
        String prescriptionNo = null;
        String signCode = null;
        // Gate：空请求按空参进入统一签发校验
        if (request != null) {
            encounterId = request.getEncounterId();
            prescriptionNo = request.getPrescriptionNo();
            signCode = request.getSignCode();
        }
        // Pre/Cmd/Asm：整单 DRAFT 行逐条签发（见 signApplyForm）
        return signApplyForm(
            encounterId,
            prescriptionNo,
            signCode,
            ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
    }

    /**
     * IF-IP-SUR-08：撤回手术申请单。
     *
     * @param request AR 号
     * @return 已撤回行 id
     */
    public WithdrawSurgeryApplyFormResponse withdrawSurgeryApplyForm(WithdrawSurgeryApplyFormRequest request) {
        Long encounterId = null;
        String prescriptionNo = null;
        // Gate：空请求按空参进入统一撤回校验
        if (request != null) {
            encounterId = request.getEncounterId();
            prescriptionNo = request.getPrescriptionNo();
        }
        // Pre/Cmd：整单 ACTIVE 行可撤回校验后逐条撤回
        List<Long> withdrawnIds = withdrawApplyForm(
            encounterId,
            prescriptionNo,
            ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        // Asm：返回 AR 与已撤回行 id
        WithdrawSurgeryApplyFormResponse response = new WithdrawSurgeryApplyFormResponse();
        response.setPrescriptionNo(prescriptionNo);
        response.setWithdrawnServiceRequestIds(withdrawnIds);
        return response;
    }

    /**
     * IF-IP-SUR-09：勾选或取消嵌套麻醉。
     *
     * @param request 手术 AR 与开关
     * @return 关联麻醉 AR
     */
    public ToggleAnesthesiaNestResponse toggleAnesthesiaNest(ToggleAnesthesiaNestRequest request) {
        // Gate：嵌套开关 enabled 必填
        if (request == null || request.getEnabled() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_NEST_ENABLED_REQUIRED);
        }
        Long encounterId = requireEncounterId(request.getEncounterId());
        String surgeryPrescriptionNo = requirePrescriptionNo(request.getSurgeryPrescriptionNo());
        Long operatorId = requireOperatorId();
        // Pre：就诊门控
        InpatientEncounterContextQueryModel context =
            encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        // Pre：定位手术 AR
        List<ServiceRequestListModel> surgeryRows = filterFormRowsByPrescription(
            encounterId,
            surgeryPrescriptionNo,
            ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        // 手术 AR 无行则无法挂接或解除嵌套麻醉
        if (surgeryRows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                SurgeryAnesthApplyConstants.MSG_NEST_SURGERY_NOT_FOUND);
        }
        String existingAnesthNo = resolveNestedAnesthesiaPrescriptionNo(surgeryRows, encounterId);
        ToggleAnesthesiaNestResponse response = new ToggleAnesthesiaNestResponse();
        response.setSurgeryPrescriptionNo(surgeryPrescriptionNo);
        response.setEnabled(request.getEnabled());
        // 开启嵌套：已有麻醉 AR 则幂等返回，否则创建壳行
        if (Boolean.TRUE.equals(request.getEnabled())) {
            // 已存在嵌套号则不再重复领号建壳，避免双麻醉单
            if (isNotEmpty(existingAnesthNo)) {
                response.setAnesthesiaPrescriptionNo(existingAnesthNo);
                return response;
            }
            // Cmd：创建嵌套麻醉壳行
            ServiceRequestListModel anchor = resolveProcedureAnchorLine(surgeryRows);
            String anesthNo = serviceApplyFormOrchestrationSupport.allocateArPrescriptionNo();
            ActivityDefinition ad = loadActivity(anchor.getActivityId());
            String unitCode = anchor.getUnitCode();
            // 手术行未填单位时从 AD 默认单位回落，保证壳行可落库
            if (isEmpty(unitCode) && ad != null) {
                unitCode = resolveActivityUnitFromDefinition(ad);
            }
            // 仍无单位则用模块默认单位，避免创建草稿因单位缺失失败
            if (isEmpty(unitCode)) {
                unitCode = SurgeryAnesthApplyConstants.NEST_SHELL_DEFAULT_UNIT_CODE;
            }
            CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand shell =
                inpatientDoctorStationSurgeryAnesthMapper.toNestAnesthesiaShellDraftItem(
                    context,
                    anesthNo,
                    anchor.getId(),
                    anchor.getActivityId(),
                    unitCode,
                    null);
            createOneDraft(operatorId, shell);
            response.setAnesthesiaPrescriptionNo(anesthNo);
            return response;
        }
        // enabled=false：撤回并软删关联麻醉行
        List<ServiceRequestListModel> anesthRows = findLinkedAnesthesiaRows(encounterId, surgeryRows, existingAnesthNo);
        // 无关联麻醉行则视为已解除嵌套，直接返回
        if (anesthRows.isEmpty()) {
            response.setAnesthesiaPrescriptionNo(null);
            return response;
        }
        // 已签发麻醉行须先校验存在性与可撤回性，再逐条撤回
        for (ServiceRequestListModel row : anesthRows) {
            if (RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                serviceRequestQueryService.mustGetServiceRequest(row.getId());
                assertWithdrawable(row.getId());
                withdrawOneDraft(operatorId, row.getId());
            }
        }
        // 草稿或已撤回行统一软删，清掉嵌套麻醉单
        for (ServiceRequestListModel row : anesthRows) {
            removeOneDraft(operatorId, row.getId());
        }
        response.setAnesthesiaPrescriptionNo(null);
        return response;
    }

    /**
     * IF-IP-SUR-10：麻醉申请单列表。
     *
     * @param request 就诊标识
     * @return 麻醉 AR 头列表
     */
    public ListAnesthesiaApplyFormsResponse listAnesthesiaApplyForms(ListAnesthesiaApplyFormsRequest request) {
        // Gate：就诊标识必填
        Long encounterId = requireEncounterId(resolveEncounterId(request));
        // Pre：住院就诊可读门控
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        // Pre：筛医生开立、AR 前缀、类目 25 的麻醉申请行
        List<ServiceRequestListModel> rows = filterApplyFormRows(
            encounterId, ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        EnrichBundle bundle = buildListEnrichBundle(rows);
        Map<String, String> linkedMap = buildLinkedSurgeryPrescriptionMap(encounterId, rows);
        // Asm：麻醉 AR 头 + 关联手术 AR enrich
        ListAnesthesiaApplyFormsResponse response = new ListAnesthesiaApplyFormsResponse();
        response.setForms(inpatientDoctorStationSurgeryAnesthMapper.toAnesthesiaFormHeaders(
            rows, bundle.requesterNameById, bundle.activityById, linkedMap));
        return response;
    }

    /**
     * IF-IP-SUR-11：麻醉申请单详情。
     *
     * @param request 就诊与 AR 号
     * @return 头/行/横表
     */
    public GetAnesthesiaApplyFormDetailResponse getAnesthesiaApplyFormDetail(GetAnesthesiaApplyFormDetailRequest request) {
        // Gate：就诊与麻醉 AR 号必填
        Long encounterId = requireEncounterId(resolveEncounterId(request));
        String prescriptionNo = requirePrescriptionNo(resolvePrescriptionNo(request));
        // Pre：住院就诊可读门控
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        // Pre：按 AR 定位麻醉单内全部 SR 行
        List<ServiceRequestListModel> rows = filterFormRowsByPrescription(
            encounterId, prescriptionNo, ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        // 麻醉 AR 无行则无法展示详情
        if (rows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
        }
        // Pre：子表明细、拟人员与 basedOn 关联手术 AR
        EnrichBundle bundle = buildDetailEnrichBundle(rows);
        String linkedSurgery = resolveLinkedSurgeryPrescriptionForAnesthForm(encounterId, rows);
        // Asm：头/行/横表 DTO
        return inpatientDoctorStationSurgeryAnesthMapper.toAnesthesiaFormDetail(
            rows,
            bundle.detailsByRequestId,
            bundle.activityById,
            bundle.practitionerNameById,
            linkedSurgery);
    }

    /**
     * IF-IP-SUR-12：保存麻醉申请单。
     *
     * @param request 行与关联手术 AR
     * @return 新 AR 号
     */
    public SaveAnesthesiaApplyFormResponse saveAnesthesiaApplyForm(SaveAnesthesiaApplyFormRequest request) {
        // Gate：行级校验（类目、数量、禁止耗材等）
        validateAnesthesiaSaveGate(request);
        Long operatorId = requireOperatorId();
        // Pre：住院就诊可写门控与 AD 可用态
        InpatientEncounterContextQueryModel context =
            encounterContextReader.mustGetInpatientContextByEncounterId(request.getEncounterId());
        loadAndValidateActivities(collectActivityIdsFromAnesthesiaLines(request.getLines()));
        Long basedOnSurgeryLineId = null;
        // 关联手术 AR 时解析 basedOn 手术行，供麻醉行挂接
        if (isNotEmpty(request.getLinkedSurgeryPrescriptionNo())) {
            basedOnSurgeryLineId = resolveBasedOnSurgeryLineId(
                request.getEncounterId(), request.getLinkedSurgeryPrescriptionNo().trim());
        }
        // 编辑保存先删旧麻醉 DRAFT 行
        if (isNotEmpty(request.getPreviousPrescriptionNo())) {
            removePreviousDraftApplyForm(
                request.getEncounterId(),
                request.getPreviousPrescriptionNo().trim(),
                ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA,
                operatorId);
        }
        // Cmd：领取麻醉 AR 并逐行创建 SR 草稿
        String prescriptionNo = serviceApplyFormOrchestrationSupport.allocateArPrescriptionNo();
        List<Long> serviceRequestIds = new ArrayList<>();
        // 逐行创建麻醉 SR 草稿（类目 25）
        for (AnesthesiaApplyFormLineRequest line : request.getLines()) {
            CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
                inpatientDoctorStationSurgeryAnesthMapper.toAnesthesiaCreateDraftItem(
                    line, context, prescriptionNo, request.getPlanned(), basedOnSurgeryLineId);
            serviceRequestIds.add(createOneDraft(operatorId, item));
        }
        // Asm
        SaveAnesthesiaApplyFormResponse response = new SaveAnesthesiaApplyFormResponse();
        response.setPrescriptionNo(prescriptionNo);
        response.setServiceRequestIds(serviceRequestIds);
        return response;
    }

    /**
     * IF-IP-SUR-13：签发麻醉申请单。
     *
     * @param request AR 号与签发码
     * @return 已签发行 id
     */
    public SignAnesthesiaApplyFormResponse signAnesthesiaApplyForm(SignAnesthesiaApplyFormRequest request) {
        Long encounterId = null;
        String prescriptionNo = null;
        String signCode = null;
        // Gate：空请求按空参进入统一签发校验
        if (request != null) {
            encounterId = request.getEncounterId();
            prescriptionNo = request.getPrescriptionNo();
            signCode = request.getSignCode();
        }
        // Pre/Cmd/Asm：类目 25 整单签发（复用 signApplyForm）
        SignSurgeryApplyFormResponse signed = signApplyForm(
            encounterId,
            prescriptionNo,
            signCode,
            ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        // Asm：麻醉签发响应 DTO
        SignAnesthesiaApplyFormResponse response = new SignAnesthesiaApplyFormResponse();
        response.setPrescriptionNo(signed.getPrescriptionNo());
        response.setSignedServiceRequestIds(signed.getSignedServiceRequestIds());
        return response;
    }

    /**
     * IF-IP-SUR-14：撤回麻醉申请单。
     *
     * @param request AR 号
     * @return 已撤回行 id
     */
    public WithdrawAnesthesiaApplyFormResponse withdrawAnesthesiaApplyForm(WithdrawAnesthesiaApplyFormRequest request) {
        Long encounterId = null;
        String prescriptionNo = null;
        // Gate：空请求按空参进入统一撤回校验
        if (request != null) {
            encounterId = request.getEncounterId();
            prescriptionNo = request.getPrescriptionNo();
        }
        // Pre/Cmd：类目 25 整单可撤回行逐条撤回
        List<Long> withdrawnIds = withdrawApplyForm(
            encounterId,
            prescriptionNo,
            ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        // Asm：返回 AR 与已撤回行 id
        WithdrawAnesthesiaApplyFormResponse response = new WithdrawAnesthesiaApplyFormResponse();
        response.setPrescriptionNo(prescriptionNo);
        response.setWithdrawnServiceRequestIds(withdrawnIds);
        return response;
    }

    /**
     * IF-IP-SUR-15：加载拟手术横表字段。
     *
     * @param request 表单种类与 AR 号
     * @return 计划字段
     */
    public GetPlannedSurgeryFieldsResponse getPlannedSurgeryFields(GetPlannedSurgeryFieldsRequest request) {
        // Gate：就诊、AR、表单种类（手术/麻醉）必填且合法
        Long encounterId = requireEncounterId(resolveEncounterId(request));
        String prescriptionNo = requirePrescriptionNo(resolvePrescriptionNo(request));
        String categoryCode = resolveFormKindCategory(resolveFormKindCode(request));
        // Pre：住院就诊可读门控
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        // Pre：定位单内行并取横表载体（主手术行优先）
        List<ServiceRequestListModel> rows = filterFormRowsByPrescription(encounterId, prescriptionNo, categoryCode);
        // 单内无行则无法加载横表
        if (rows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
        }
        ServiceRequestListModel carrier = resolvePlannedCarrierLine(rows);
        ServiceRequestQueryModel model = serviceRequestQueryService.mustGetServiceRequest(carrier.getId());
        Set<Long> performerIds = collectPerformerIdsFromListModel(carrier);
        Map<Long, String> nameById = mapPractitionerNames(performerIds);
        // Asm：拟手术/麻醉横表 DTO
        GetPlannedSurgeryFieldsResponse response = new GetPlannedSurgeryFieldsResponse();
        response.setPlanned(inpatientDoctorStationSurgeryAnesthMapper.toPlannedFields(model, nameById));
        return response;
    }

    /**
     * 列表/头 enrich 所需批量读结果。
     */
    private static final class EnrichBundle {
        private Map<Long, String> requesterNameById = new HashMap<>();
        private Map<Long, ProcedureDefinitionSearchItemQueryModel> pdByActivityId = new HashMap<>();
        private Map<Long, ActivityDefinition> activityById = new HashMap<>();
        private Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId = new HashMap<>();
        private Map<Long, String> practitionerNameById = new HashMap<>();
    }

    /**
     * 构建列表 enrich 包。
     *
     * @param rows 过滤后的 SR 行
     * @return enrich 包
     */
    private EnrichBundle buildListEnrichBundle(List<ServiceRequestListModel> rows) {
        EnrichBundle bundle = new EnrichBundle();
        Set<Long> requesterIds = new HashSet<>();
        Set<Long> activityIds = new HashSet<>();
        // 扫描行收集开立人与 AD，供头/行展示 enrich
        for (ServiceRequestListModel row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getRequesterId() != null) {
                requesterIds.add(row.getRequesterId());
            }
            if (row.getActivityId() != null) {
                activityIds.add(row.getActivityId());
            }
        }
        bundle.requesterNameById = mapPractitionerNames(requesterIds);
        bundle.activityById = mapActivitiesById(activityIds);
        bundle.pdByActivityId = procedureDefinitionQueryService.mapActiveClinicalByActivityDefinitionIds(activityIds);
        return bundle;
    }

    /**
     * 构建详情 enrich 包（含明细与拟人员）。
     *
     * @param rows 单内行
     * @return enrich 包
     */
    private EnrichBundle buildDetailEnrichBundle(List<ServiceRequestListModel> rows) {
        EnrichBundle bundle = buildListEnrichBundle(rows);
        List<Long> srIds = new ArrayList<>();
        Set<Long> performerIds = new HashSet<>();
        // 收集 SR id 与拟人员 id，批量拉明细与姓名
        for (ServiceRequestListModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            srIds.add(row.getId());
            performerIds.addAll(collectPerformerIdsFromListModel(row));
        }
        bundle.detailsByRequestId =
            serviceRequestDetailListAssembler.mapDetailListModelsByServiceRequestIds(srIds);
        bundle.practitionerNameById = mapPractitionerNames(performerIds);
        return bundle;
    }

    /**
     * 过滤就诊内医生开立 AR 申请行。
     *
     * @param encounterId  就诊
     * @param categoryCode 类目
     * @return 行列表
     */
    private List<ServiceRequestListModel> filterApplyFormRows(Long encounterId, String categoryCode) {
        List<ServiceRequestListModel> all = serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        List<ServiceRequestListModel> matched = new ArrayList<>();
        // 就诊内仅保留本模块医生开立 AR 且类目匹配的行
        for (ServiceRequestListModel row : all) {
            if (!matchesApplyFormRow(row, categoryCode)) {
                continue;
            }
            matched.add(row);
        }
        return matched;
    }

    /**
     * 按 AR 号过滤单内行。
     *
     * @param encounterId    就诊
     * @param prescriptionNo AR 号
     * @param categoryCode   类目
     * @return 行列表
     */
    private List<ServiceRequestListModel> filterFormRowsByPrescription(
        Long encounterId, String prescriptionNo, String categoryCode) {
        List<ServiceRequestListModel> matched = new ArrayList<>();
        // 在已过滤申请行中按 AR 号精确匹配单内行
        for (ServiceRequestListModel row : filterApplyFormRows(encounterId, categoryCode)) {
            if (prescriptionNo.equals(trimToEmpty(row.getPrescriptionNo()))) {
                matched.add(row);
            }
        }
        return matched;
    }

    /**
     * 判断是否为本模块申请单行。
     *
     * @param row          SR 行
     * @param categoryCode 类目
     * @return 是否匹配
     */
    private boolean matchesApplyFormRow(ServiceRequestListModel row, String categoryCode) {
        // 空行或非 SR 主键行不参与申请单列表
        if (row == null || row.getId() == null) {
            return false;
        }
        // 非医生开立来源不计入住院手术/麻醉申请
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(trimToEmpty(row.getGenerateSourceEnum()))) {
            return false;
        }
        // 类目不符则排除（手术 24 / 麻醉 25）
        if (!categoryCode.equals(trimToEmpty(row.getCategoryEnum()))) {
            return false;
        }
        return ServiceApplyFormOrchestrationSupport.isRequisitionApplicationPrescriptionNo(row.getPrescriptionNo());
    }

    /**
     * 手术保存 Gate。
     *
     * @param request 保存请求
     */
    private void validateSurgerySaveGate(SaveSurgeryApplyFormRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_ENCOUNTER_ID_REQUIRED);
        }
        // 无明细行无法组成申请单
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_LINES_REQUIRED);
        }
        int procedureCount = 0;
        int mainFlagCount = 0;
        // 逐行 Gate 并统计术式行数与主手术标志数
        for (SurgeryApplyFormLineRequest line : request.getLines()) {
            validateSurgeryLineGate(line);
            if (SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(trimToEmpty(line.getLineKindCode()))) {
                procedureCount = procedureCount + 1;
            }
            if (Boolean.TRUE.equals(line.getMainProcedureFlag())) {
                mainFlagCount = mainFlagCount + 1;
            }
        }
        // 至少一条国临术式行，否则无法开立手术申请
        if (procedureCount < 1) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_PROCEDURE_LINE_REQUIRED);
        }
        // 主手术标志超过一条会导致横表载体歧义
        if (mainFlagCount > 1) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_MAIN_PROCEDURE_FLAG_DUPLICATE);
        }
    }

    /**
     * 麻醉保存 Gate。
     *
     * @param request 保存请求
     */
    private void validateAnesthesiaSaveGate(SaveAnesthesiaApplyFormRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_ENCOUNTER_ID_REQUIRED);
        }
        // 无明细行无法组成麻醉申请单
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_LINES_REQUIRED);
        }
        // 逐行校验种类、AD、数量单位
        for (AnesthesiaApplyFormLineRequest line : request.getLines()) {
            validateAnesthesiaLineGate(line);
        }
    }

    /**
     * 单行 Gate（手术）。
     *
     * @param line 行
     */
    private void validateSurgeryLineGate(SurgeryApplyFormLineRequest line) {
        if (line == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_LINES_REQUIRED);
        }
        String kind = trimToEmpty(line.getLineKindCode());
        if (SurgeryAnesthApplyConstants.LINE_KIND_CONSUMABLE.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_CONSUMABLE_FORBIDDEN);
        }
        if (!SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(kind)
            && !SurgeryAnesthApplyConstants.LINE_KIND_SURCHARGE.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_LINE_KIND_INVALID);
        }
        if (line.getActivityDefinitionId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_ACTIVITY_DEFINITION_REQUIRED);
        }
        validateQuantityAndUnit(line.getQuantity(), line.getUnitCode());
        if (SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(kind) && line.getProcedureDefinitionId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_PROCEDURE_DEFINITION_REQUIRED);
        }
    }

    /**
     * 单行 Gate（麻醉）。
     *
     * @param line 行
     */
    private void validateAnesthesiaLineGate(AnesthesiaApplyFormLineRequest line) {
        if (line == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_LINES_REQUIRED);
        }
        String kind = trimToEmpty(line.getLineKindCode());
        if (SurgeryAnesthApplyConstants.LINE_KIND_CONSUMABLE.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_CONSUMABLE_FORBIDDEN);
        }
        if (!SurgeryAnesthApplyConstants.LINE_KIND_ANESTHESIA.equals(kind)
            && !SurgeryAnesthApplyConstants.LINE_KIND_SURCHARGE.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_LINE_KIND_INVALID);
        }
        if (line.getActivityDefinitionId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_ACTIVITY_DEFINITION_REQUIRED);
        }
        validateQuantityAndUnit(line.getQuantity(), line.getUnitCode());
    }

    /**
     * 数量与单位 Gate。
     *
     * @param quantity 数量
     * @param unitCode 单位
     */
    private void validateQuantityAndUnit(BigDecimal quantity, String unitCode) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_QUANTITY_INVALID);
        }
        if (isEmpty(unitCode)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_UNIT_CODE_REQUIRED);
        }
    }

    /**
     * Pre：校验术式行 PD 与 AD。
     *
     * @param lines 行列表
     */
    private void validateSurgeryLinesPre(List<SurgeryApplyFormLineRequest> lines) {
        // 术式行须 PD 有效且与所选 AD 一致，防止错绑国临码
        for (SurgeryApplyFormLineRequest line : lines) {
            if (!SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(trimToEmpty(line.getLineKindCode()))) {
                continue;
            }
            ProcedureDefinitionDetailQueryModel pd = procedureDefinitionQueryService.getOne(line.getProcedureDefinitionId());
            // PD 非 ACTIVE 不可开立
            if (!PublicationStatus.ACTIVE.getCode().equals(trimToEmpty(pd.getStatusEnum()))) {
                throw new BusinessException(
                    ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND,
                    SurgeryAnesthApplyConstants.MSG_PD_ACTIVITY_MISMATCH);
            }
            // PD 绑定 AD 与行上 AD 不一致则拒绝保存
            if (pd.getActivityDefinitionId() == null
                || !pd.getActivityDefinitionId().equals(line.getActivityDefinitionId())) {
                throw new BusinessException(
                    ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND,
                    SurgeryAnesthApplyConstants.MSG_PD_ACTIVITY_MISMATCH);
            }
        }
    }

    /**
     * 加载并校验 AD 均存在且 ACTIVE。
     *
     * @param activityIds id 集合
     * @return id → AD
     */
    private Map<Long, ActivityDefinition> loadAndValidateActivities(Set<Long> activityIds) {
        Map<Long, ActivityDefinition> map = mapActivitiesById(activityIds);
        // 各行 AD 必须存在且 ACTIVE，否则无法落草稿
        for (Long id : activityIds) {
            ActivityDefinition def = map.get(id);
            if (def == null || !PublicationStatus.ACTIVE.getCode().equals(trimToEmpty(def.getStatusEnum()))) {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND,
                    SurgeryAnesthApplyConstants.MSG_ACTIVITY_DEFINITION_UNAVAILABLE);
            }
        }
        return map;
    }

    /**
     * 批量加载 AD。
     *
     * @param activityIds id 集合
     * @return map
     */
    private Map<Long, ActivityDefinition> mapActivitiesById(Set<Long> activityIds) {
        Map<Long, ActivityDefinition> map = new HashMap<>();
        if (activityIds == null || activityIds.isEmpty()) {
            return map;
        }
        for (ActivityDefinition def : activityDefinitionQueryService.findAllByIds(activityIds)) {
            if (def != null && def.getId() != null) {
                map.put(def.getId(), def);
            }
        }
        return map;
    }

    /**
     * 加载单条 AD。
     *
     * @param activityId AD 主键
     * @return AD 或 null
     */
    private ActivityDefinition loadActivity(Long activityId) {
        if (activityId == null) {
            return null;
        }
        Set<Long> ids = new HashSet<>();
        ids.add(activityId);
        Map<Long, ActivityDefinition> map = mapActivitiesById(ids);
        return map.get(activityId);
    }

    /**
     * 编辑删旧：仅 DRAFT 行。
     *
     * @param encounterId    就诊
     * @param prescriptionNo 旧 AR
     * @param categoryCode   类目
     * @param operatorId     操作人
     */
    private void removePreviousDraftApplyForm(
        Long encounterId,
        String prescriptionNo,
        String categoryCode,
        Long operatorId) {
        List<ServiceRequestListModel> oldRows = serviceRequestQueryService.listByEncounterAndPrescriptionNo(
            encounterId, prescriptionNo, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        // 旧 AR 无行说明编辑目标不存在
        if (oldRows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
        }
        // 仅允许删同类目 DRAFT 行，已签发须走撤回而非编辑删旧
        for (ServiceRequestListModel row : oldRows) {
            if (!categoryCode.equals(trimToEmpty(row.getCategoryEnum()))) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
            }
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_NOT_DRAFT);
            }
            removeOneDraft(operatorId, row.getId());
        }
    }

    /**
     * 创建单条 SR 草稿。
     *
     * @param operatorId 操作人
     * @param item       子命令
     * @return 新 SR id
     */
    private Long createOneDraft(Long operatorId, CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item) {
        CreateServiceRequestDraftCommand command = new CreateServiceRequestDraftCommand();
        command.setOperatorId(operatorId);
        command.setItem(item);
        return serviceRequestDomainService.createServiceRequestDraft(command);
    }

    /**
     * 软删单条草稿。
     *
     * @param operatorId      操作人
     * @param serviceRequestId SR id
     */
    private void removeOneDraft(Long operatorId, Long serviceRequestId) {
        RemoveServiceRequestDraftCommand command = inpatientDoctorStationSurgeryAnesthMapper.toRemoveDraftCommand(serviceRequestId);
        command.setOperatorId(operatorId);
        serviceRequestDomainService.removeServiceRequestDraft(command);
    }

    /**
     * 撤回单条已签发 SR。
     *
     * @param operatorId      操作人
     * @param serviceRequestId SR id
     */
    private void withdrawOneDraft(Long operatorId, Long serviceRequestId) {
        WithdrawSignedServiceRequestCommand command = new WithdrawSignedServiceRequestCommand();
        command.setOperatorId(operatorId);
        WithdrawSignedServiceRequestCommand.WithdrawSignedServiceRequestItemCommand item =
            new WithdrawSignedServiceRequestCommand.WithdrawSignedServiceRequestItemCommand();
        item.setId(serviceRequestId);
        command.setItem(item);
        serviceRequestDomainService.withdrawSignedServiceRequest(command);
    }

    /**
     * 签发整单（循环单条 Cmd）。
     *
     * @param encounterId    就诊
     * @param prescriptionNo AR
     * @param signCode       签发码（Gate 非空；域命令不含 signCode）
     * @param categoryCode   类目
     * @return 签发响应
     */
    private SignSurgeryApplyFormResponse signApplyForm(
        Long encounterId, String prescriptionNo, String signCode, String categoryCode) {
        encounterId = requireEncounterId(encounterId);
        prescriptionNo = requirePrescriptionNo(prescriptionNo);
        if (isEmpty(signCode)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_SIGN_CODE_REQUIRED);
        }
        Long operatorId = requireOperatorId();
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        List<ServiceRequestListModel> rows = filterFormRowsByPrescription(encounterId, prescriptionNo, categoryCode);
        // 整单不存在则无法签发
        if (rows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
        }
        // 存在非 DRAFT 行则禁止整单签发，避免部分生效
        for (ServiceRequestListModel row : rows) {
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_NOT_ALL_DRAFT_FOR_SIGN);
            }
        }
        List<Long> signedIds = new ArrayList<>();
        LocalDateTime signTime = LocalDateTime.now();
        // 逐条签发 DRAFT 行，整单同事务外仍按行命令
        for (ServiceRequestListModel row : rows) {
            SignServiceRequestCommand command = new SignServiceRequestCommand();
            command.setOperatorId(operatorId);
            SignServiceRequestCommand.SignServiceRequestItemCommand item =
                new SignServiceRequestCommand.SignServiceRequestItemCommand();
            item.setId(row.getId());
            item.setSignerId(operatorId);
            item.setSignTime(signTime);
            command.setItem(item);
            serviceRequestDomainService.signServiceRequest(command);
            signedIds.add(row.getId());
        }
        SignSurgeryApplyFormResponse response = new SignSurgeryApplyFormResponse();
        response.setPrescriptionNo(prescriptionNo);
        response.setSignedServiceRequestIds(signedIds);
        return response;
    }

    /**
     * 撤回整单。
     *
     * @param encounterId    就诊
     * @param prescriptionNo AR
     * @param categoryCode   类目
     * @return 已撤回 id 列表
     */
    private List<Long> withdrawApplyForm(Long encounterId, String prescriptionNo, String categoryCode) {
        encounterId = requireEncounterId(encounterId);
        prescriptionNo = requirePrescriptionNo(prescriptionNo);
        Long operatorId = requireOperatorId();
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        List<ServiceRequestListModel> rows = filterFormRowsByPrescription(encounterId, prescriptionNo, categoryCode);
        // 整单不存在则无法撤回
        if (rows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
        }
        List<Long> withdrawnIds = new ArrayList<>();
        // 仅撤回 ACTIVE 行；DRAFT 行不在此接口处理
        for (ServiceRequestListModel row : rows) {
            if (RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                assertWithdrawable(row.getId());
                withdrawOneDraft(operatorId, row.getId());
                withdrawnIds.add(row.getId());
            }
        }
        return withdrawnIds;
    }

    /**
     * 撤回前门控。
     *
     * @param serviceRequestId SR id
     */
    private void assertWithdrawable(Long serviceRequestId) {
        Boolean withdrawable = serviceRequestQueryService.validateServiceRequestWithdrawable(serviceRequestId);
        // 下游已执行或域规则禁止撤回时，整单撤回须失败
        if (withdrawable == null || !withdrawable.booleanValue()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_NOT_WITHDRAWABLE);
        }
    }

    /**
     * 解析加收检索类目。
     *
     * @param sideCode 侧别
     * @return category code
     */
    private String resolveSurchargeCategoryCode(String sideCode) {
        String raw = trimToEmpty(sideCode);
        if (SurgeryAnesthApplyConstants.SIDE_SURGERY.equals(raw)) {
            return ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE;
        }
        if (SurgeryAnesthApplyConstants.SIDE_ANESTHESIA.equals(raw)) {
            return ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA;
        }
        throw new BusinessException(
            ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
            SurgeryAnesthApplyConstants.MSG_SIDE_CODE_INVALID);
    }

    /**
     * 表单种类 → 类目。
     *
     * @param formKindCode 种类
     * @return 类目 code
     */
    private String resolveFormKindCategory(String formKindCode) {
        String raw = trimToEmpty(formKindCode);
        if (SurgeryAnesthApplyConstants.FORM_KIND_SURGERY.equals(raw)) {
            return ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE;
        }
        if (SurgeryAnesthApplyConstants.FORM_KIND_ANESTHESIA.equals(raw)) {
            return ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA;
        }
        throw new BusinessException(
            ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
            SurgeryAnesthApplyConstants.MSG_FORM_KIND_INVALID);
    }

    /**
     * 解析关联手术行 id（麻醉 basedOn）。
     *
     * @param encounterId              就诊
     * @param linkedSurgeryPrescriptionNo 手术 AR
     * @return 手术 SR 行 id
     */
    private Long resolveBasedOnSurgeryLineId(Long encounterId, String linkedSurgeryPrescriptionNo) {
        List<ServiceRequestListModel> surgeryRows = filterFormRowsByPrescription(
            encounterId,
            linkedSurgeryPrescriptionNo,
            ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        if (surgeryRows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                SurgeryAnesthApplyConstants.MSG_LINKED_SURGERY_NOT_FOUND);
        }
        ServiceRequestListModel anchor = resolveProcedureAnchorLine(surgeryRows);
        return anchor.getId();
    }

    /**
     * 选取术式锚行（主手术标志优先）。
     *
     * @param surgeryRows 手术行
     * @return 锚行
     */
    private ServiceRequestListModel resolveProcedureAnchorLine(List<ServiceRequestListModel> surgeryRows) {
        ServiceRequestListModel carrier = resolvePlannedCarrierLine(surgeryRows);
        if (carrier != null) {
            return carrier;
        }
        return surgeryRows.get(0);
    }

    /**
     * 横表载体行（与 Mapper 同口径）。
     *
     * @param rows 行列表
     * @return 载体行
     */
    private ServiceRequestListModel resolvePlannedCarrierLine(List<ServiceRequestListModel> rows) {
        for (ServiceRequestListModel row : rows) {
            if (row != null && Boolean.TRUE.equals(row.getMainProcedureFlag())) {
                return row;
            }
        }
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    /**
     * 从手术行解析 nestedAnesthesiaPrescriptionNo。
     *
     * @param surgeryRows  手术行
     * @param encounterId  就诊
     * @return 麻醉 AR 或 null
     */
    private String resolveNestedAnesthesiaPrescriptionNo(
        List<ServiceRequestListModel> surgeryRows, Long encounterId) {
        for (ServiceRequestListModel row : surgeryRows) {
            if (row != null && isNotEmpty(row.getNestedAnesthesiaPrescriptionNo())) {
                return row.getNestedAnesthesiaPrescriptionNo();
            }
        }
        List<ServiceRequestListModel> linked = findLinkedAnesthesiaRows(encounterId, surgeryRows, null);
        if (linked.isEmpty()) {
            return null;
        }
        return linked.get(0).getPrescriptionNo();
    }

    /**
     * 查找与手术 AR 关联的麻醉行。
     *
     * @param encounterId              就诊
     * @param surgeryRows              手术行
     * @param nestedAnesthesiaPrescriptionNo 已知嵌套号
     * @return 麻醉行列表
     */
    private List<ServiceRequestListModel> findLinkedAnesthesiaRows(
        Long encounterId,
        List<ServiceRequestListModel> surgeryRows,
        String nestedAnesthesiaPrescriptionNo) {
        Set<Long> surgeryIds = new HashSet<>();
        for (ServiceRequestListModel row : surgeryRows) {
            if (row != null && row.getId() != null) {
                surgeryIds.add(row.getId());
            }
        }
        List<ServiceRequestListModel> anesthAll = filterApplyFormRows(
            encounterId, ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        List<ServiceRequestListModel> matched = new ArrayList<>();
        // 按 nested 麻醉 AR 或 basedOn 手术行 id 匹配关联麻醉行
        for (ServiceRequestListModel row : anesthAll) {
            if (row == null) {
                continue;
            }
            if (isNotEmpty(nestedAnesthesiaPrescriptionNo)
                && nestedAnesthesiaPrescriptionNo.equals(row.getPrescriptionNo())) {
                matched.add(row);
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getBasedOnTable())
                && row.getBasedOnId() != null
                && surgeryIds.contains(row.getBasedOnId())) {
                matched.add(row);
            }
        }
        return matched;
    }

    /**
     * 构建麻醉 AR → 关联手术 AR map（列表 enrich）。
     *
     * @param encounterId 就诊
     * @param anesthRows  麻醉行
     * @return map
     */
    private Map<String, String> buildLinkedSurgeryPrescriptionMap(
        Long encounterId, List<ServiceRequestListModel> anesthRows) {
        Map<String, String> result = new HashMap<>();
        List<ServiceRequestListModel> all = serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        Map<Long, String> surgeryIdToPrescription = new HashMap<>();
        for (ServiceRequestListModel row : all) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(trimToEmpty(row.getCategoryEnum()))
                && isNotEmpty(row.getPrescriptionNo())) {
                surgeryIdToPrescription.put(row.getId(), row.getPrescriptionNo());
            }
        }
        for (ServiceRequestListModel row : anesthRows) {
            if (row == null || isEmpty(row.getPrescriptionNo())) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getBasedOnTable())
                && row.getBasedOnId() != null) {
                String surgeryNo = surgeryIdToPrescription.get(row.getBasedOnId());
                if (isNotEmpty(surgeryNo)) {
                    result.put(row.getPrescriptionNo(), surgeryNo);
                }
            }
        }
        return result;
    }

    /**
     * 解析麻醉单关联手术 AR（详情）。
     *
     * @param encounterId 就诊
     * @param anesthRows  麻醉行
     * @return 手术 AR 或 null
     */
    private String resolveLinkedSurgeryPrescriptionForAnesthForm(
        Long encounterId, List<ServiceRequestListModel> anesthRows) {
        Map<String, String> map = buildLinkedSurgeryPrescriptionMap(encounterId, anesthRows);
        if (anesthRows.isEmpty()) {
            return null;
        }
        String anesthNo = anesthRows.get(0).getPrescriptionNo();
        return map.get(anesthNo);
    }

    /**
     * 收集手术行 AD id。
     *
     * @param lines 行
     * @return id 集合
     */
    private Set<Long> collectActivityIdsFromSurgeryLines(List<SurgeryApplyFormLineRequest> lines) {
        Set<Long> ids = new HashSet<>();
        for (SurgeryApplyFormLineRequest line : lines) {
            if (line != null && line.getActivityDefinitionId() != null) {
                ids.add(line.getActivityDefinitionId());
            }
        }
        return ids;
    }

    /**
     * 收集麻醉行 AD id。
     *
     * @param lines 行
     * @return id 集合
     */
    private Set<Long> collectActivityIdsFromAnesthesiaLines(List<AnesthesiaApplyFormLineRequest> lines) {
        Set<Long> ids = new HashSet<>();
        for (AnesthesiaApplyFormLineRequest line : lines) {
            if (line != null && line.getActivityDefinitionId() != null) {
                ids.add(line.getActivityDefinitionId());
            }
        }
        return ids;
    }

    /**
     * 找第一条术式行。
     *
     * @param lines 行列表
     * @return 术式行或 null
     */
    private SurgeryApplyFormLineRequest findFirstProcedureLine(List<SurgeryApplyFormLineRequest> lines) {
        for (SurgeryApplyFormLineRequest line : lines) {
            if (line != null
                && SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(trimToEmpty(line.getLineKindCode()))) {
                return line;
            }
        }
        return null;
    }

    /**
     * 解析行单位（行优先，AD 回落）。
     *
     * @param line 行
     * @param ad   AD
     * @return 单位码
     */
    private String resolveLineUnitCode(SurgeryApplyFormLineRequest line, ActivityDefinition ad) {
        if (line != null && isNotEmpty(line.getUnitCode())) {
            return line.getUnitCode();
        }
        if (ad != null) {
            return resolveActivityUnitFromDefinition(ad);
        }
        return SurgeryAnesthApplyConstants.NEST_SHELL_DEFAULT_UNIT_CODE;
    }

    /**
     * AD 默认单位（住院优先）。
     *
     * @param ad AD
     * @return 单位码
     */
    private String resolveActivityUnitFromDefinition(ActivityDefinition ad) {
        if (ad == null) {
            return SurgeryAnesthApplyConstants.NEST_SHELL_DEFAULT_UNIT_CODE;
        }
        if (isNotEmpty(ad.getDefInhospitalUnitCode())) {
            return ad.getDefInhospitalUnitCode();
        }
        if (isNotEmpty(ad.getPackageUnitCode())) {
            return ad.getPackageUnitCode();
        }
        if (isNotEmpty(ad.getDefEncounterUnitCode())) {
            return ad.getDefEncounterUnitCode();
        }
        return SurgeryAnesthApplyConstants.NEST_SHELL_DEFAULT_UNIT_CODE;
    }

    /**
     * 批量解析医务人员姓名。
     *
     * @param practitionerIds id 集合
     * @return id → 姓名
     */
    private Map<Long, String> mapPractitionerNames(Set<Long> practitionerIds) {
        Map<Long, String> map = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return map;
        }
        for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            map.put(practitioner.getId(), practitioner.getName());
        }
        return map;
    }

    /**
     * 从列表行收集拟人员 id。
     *
     * @param row SR 行
     * @return id 集合
     */
    private Set<Long> collectPerformerIdsFromListModel(ServiceRequestListModel row) {
        Set<Long> ids = new HashSet<>();
        if (row == null) {
            return ids;
        }
        if (row.getSurgeonId() != null) {
            ids.add(row.getSurgeonId());
        }
        if (row.getFirstAssistantId() != null) {
            ids.add(row.getFirstAssistantId());
        }
        if (row.getSecondAssistantId() != null) {
            ids.add(row.getSecondAssistantId());
        }
        if (row.getNurseId() != null) {
            ids.add(row.getNurseId());
        }
        if (row.getAnesthetistId() != null) {
            ids.add(row.getAnesthetistId());
        }
        return ids;
    }

    /**
     * 要求就诊 id 非空。
     *
     * @param encounterId 就诊 id
     * @return 非空 id
     */
    private Long requireEncounterId(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_ENCOUNTER_ID_REQUIRED);
        }
        return encounterId;
    }

    /**
     * 要求 AR 号非空。
     *
     * @param prescriptionNo AR 号
     * @return trim 后 AR 号
     */
    private String requirePrescriptionNo(String prescriptionNo) {
        if (isEmpty(prescriptionNo)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_PRESCRIPTION_NO_REQUIRED);
        }
        return prescriptionNo.trim();
    }

    /**
     * 要求当前操作人已绑定 practitioner。
     *
     * @return operatorId
     */
    private Long requireOperatorId() {
        return currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
            SurgeryAnesthApplyConstants.MSG_OPERATOR_NOT_RESOLVED);
    }

    /**
     * 从列表请求安全取出就诊 id。
     *
     * @param request 列表请求
     * @return 就诊 id；请求为空时 null
     */
    private Long resolveEncounterId(ListSurgeryApplyFormsRequest request) {
        if (request == null) {
            return null;
        }
        return request.getEncounterId();
    }

    /**
     * 从详情请求安全取出就诊 id。
     *
     * @param request 详情请求
     * @return 就诊 id；请求为空时 null
     */
    private Long resolveEncounterId(GetSurgeryApplyFormDetailRequest request) {
        if (request == null) {
            return null;
        }
        return request.getEncounterId();
    }

    /**
     * 从麻醉列表请求安全取出就诊 id。
     *
     * @param request 列表请求
     * @return 就诊 id；请求为空时 null
     */
    private Long resolveEncounterId(ListAnesthesiaApplyFormsRequest request) {
        if (request == null) {
            return null;
        }
        return request.getEncounterId();
    }

    /**
     * 从麻醉详情请求安全取出就诊 id。
     *
     * @param request 详情请求
     * @return 就诊 id；请求为空时 null
     */
    private Long resolveEncounterId(GetAnesthesiaApplyFormDetailRequest request) {
        if (request == null) {
            return null;
        }
        return request.getEncounterId();
    }

    /**
     * 从横表请求安全取出就诊 id。
     *
     * @param request 横表请求
     * @return 就诊 id；请求为空时 null
     */
    private Long resolveEncounterId(GetPlannedSurgeryFieldsRequest request) {
        if (request == null) {
            return null;
        }
        return request.getEncounterId();
    }

    /**
     * 从手术详情请求安全取出 AR 号。
     *
     * @param request 详情请求
     * @return AR 号；请求为空时 null
     */
    private String resolvePrescriptionNo(GetSurgeryApplyFormDetailRequest request) {
        if (request == null) {
            return null;
        }
        return request.getPrescriptionNo();
    }

    /**
     * 从麻醉详情请求安全取出 AR 号。
     *
     * @param request 详情请求
     * @return AR 号；请求为空时 null
     */
    private String resolvePrescriptionNo(GetAnesthesiaApplyFormDetailRequest request) {
        if (request == null) {
            return null;
        }
        return request.getPrescriptionNo();
    }

    /**
     * 从横表请求安全取出 AR 号。
     *
     * @param request 横表请求
     * @return AR 号；请求为空时 null
     */
    private String resolvePrescriptionNo(GetPlannedSurgeryFieldsRequest request) {
        if (request == null) {
            return null;
        }
        return request.getPrescriptionNo();
    }

    /**
     * 从加收检索请求安全取出侧别。
     *
     * @param request 加收检索请求
     * @return 侧别；请求为空时 null
     */
    private String resolveSideCode(SearchSurchargeActivityDefinitionRequest request) {
        if (request == null) {
            return null;
        }
        return request.getSideCode();
    }

    /**
     * 从横表请求安全取出表单种类。
     *
     * @param request 横表请求
     * @return 表单种类；请求为空时 null
     */
    private String resolveFormKindCode(GetPlannedSurgeryFieldsRequest request) {
        if (request == null) {
            return null;
        }
        return request.getFormKindCode();
    }
}
