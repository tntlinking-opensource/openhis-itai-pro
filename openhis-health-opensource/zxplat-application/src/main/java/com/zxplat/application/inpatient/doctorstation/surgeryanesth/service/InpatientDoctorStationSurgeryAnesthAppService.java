/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.service;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormHeaderDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormLineDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormLineRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.PlannedPerformerDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.PlannedSurgeryFieldsDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurchargeActivityDefinitionItemDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormHeaderDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormLineDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormLineRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.mapper.InpatientDoctorStationSurgeryAnesthMapper;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.DeleteAnesthesiaApplyFormRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.DeleteSurgeryApplyFormRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.GetAnesthesiaApplyFormDetailRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.GetPlannedSurgeryFieldsRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.GetSurgeryApplyFormDetailRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.InitSurgeryAnesthRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.ListAnesthesiaApplyFormsRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.ListSurgeryApplyFormsRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.SaveAnesthesiaApplyFormRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.SaveSurgeryApplyFormRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.SearchClinicalProcV3Request;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.SearchSurchargeActivityDefinitionRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.SignAnesthesiaApplyFormRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.SignSurgeryApplyFormRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.ToggleAnesthesiaNestRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.WithdrawAnesthesiaApplyFormRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.request.WithdrawSurgeryApplyFormRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.DeleteAnesthesiaApplyFormResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.DeleteSurgeryApplyFormResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.GetAnesthesiaApplyFormDetailResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.GetPlannedSurgeryFieldsResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.GetSurgeryApplyFormDetailResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.InitSurgeryAnesthResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.ListAnesthesiaApplyFormsResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.ListSurgeryApplyFormsResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.SaveAnesthesiaApplyFormResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.SaveSurgeryApplyFormResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.SearchClinicalProcV3Response;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.SearchSurchargeActivityDefinitionResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.SignAnesthesiaApplyFormResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.SignSurgeryApplyFormResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.ToggleAnesthesiaNestResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.WithdrawAnesthesiaApplyFormResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.WithdrawSurgeryApplyFormResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.support.SurgeryAnesthAnesthetistOptionSupport;
import com.zxplat.application.shared.advice.ServiceApplyFormOrchestrationSupport;
import com.zxplat.application.shared.advice.ServiceRequestDetailListAssembler;
import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.application.shared.option.FrequencyOptionLoader;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionDetailQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.service.ProcedureDefinitionQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.medication.medicationrequest.command.CreateMedicationOrderDraftCommand;
import com.zxplat.domain.medication.medicationrequest.command.RemoveMedicationOrderDraftCommand;
import com.zxplat.domain.medication.medicationrequest.command.SignMedicationOrderCommand;
import com.zxplat.domain.medication.medicationrequest.command.SignOffMedicationOrderCommand;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.ClinicalProcedureConstants;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.constants.SurgeryAnesthApplyConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.workflow.servicerequest.entity.ServiceRequest;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.utils.EncounterStayTimeBoundsUtil;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.share.query.ProductDefinitionCatalogPageQuery;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.command.ModifyServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.zxplat.domain.workflow.servicerequest.command.WithdrawSignedServiceRequestCommand;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
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
 * 住院医生站手术与麻醉申请应用编排（AS-IP-DOR-012 · IF-IP-SUR-01～17）。
 *
 * <p>开立口径：手术 = 术式（{@code procedure_definition_id} + {@code activity_id=0}）+ 术前嘱托（类目 52，
 * {@code basedOn} 术式 SR）；麻醉 = 麻醉项目 + 麻醉药品（平级，不写 basedOn）；附加/加收由护士划价。
 * 手麻申请唯一 basedOn 主子关系为「术前嘱托 → 术式」。</p>
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>经 {@link EncounterContextReader} 门控住院就诊可写/可读上下文</li>
 *   <li>经 {@link ServiceRequestDomainService}/{@link ServiceRequestQueryService} 维护手术/麻醉 AR（多行 SR）</li>
 *   <li>经 {@link ProcedureDefinitionQueryService}/{@link ActivityDefinitionQueryService} 做国临与麻醉选品校验</li>
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
    /** 药品定义读侧（麻醉药品选品）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    /** 药品医嘱写侧（麻醉药品开立）。 */
    private final MedicationRequestDomainService medicationRequestDomainService;
    /** 药品医嘱读侧（麻醉药品按 AR 回显）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 医务人员读侧。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 当前操作人（practitioner）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** AR 号领取。 */
    private final ServiceApplyFormOrchestrationSupport serviceApplyFormOrchestrationSupport;
    /** 明细批量装配。 */
    private final ServiceRequestDetailListAssembler serviceRequestDetailListAssembler;
    /** 频次下拉（与医嘱开立同源）。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 麻醉医师下拉（麻醉室 ROLE_DOCTOR 出诊岗）。 */
    private final SurgeryAnesthAnesthetistOptionSupport surgeryAnesthAnesthetistOptionSupport;
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
        // 与医嘱开立同口径：频次 + 用法，供麻醉项目/药品行填写
        response.setRateOptions(frequencyOptionLoader.loadActiveRateOptions());
        response.setMethodOptions(codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.METHOD_CODE));
        // 单次剂量单位：术语 dose_unit；麻醉不换算总量，前端可按名称/码检索改选
        response.setDoseUnitOptions(codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.DOSE_UNIT));
        // Pre/Asm：麻醉医师=配置科室（如麻醉室）上激活医师出诊岗，与用户管理出诊科室同口径
        response.setAnesthetistOptions(surgeryAnesthAnesthetistOptionSupport.loadAnesthetistOptions());
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
        // Asm：补齐 basedOn 术式的术前嘱托行（类目 52，不在手术 AR 内）
        appendEntrustLinesToSurgeryDetail(detail, rows);
        // Asm：头上嵌套麻醉号空时按 nested 字段反查补齐（与列表同口径）
        if (detail.getHeader() != null && isEmpty(detail.getHeader().getNestedAnesthesiaPrescriptionNo())) {
            detail.getHeader().setNestedAnesthesiaPrescriptionNo(
                resolveNestedAnesthesiaPrescriptionNo(rows, encounterId));
        }
        return detail;
    }

    /**
     * IF-IP-SUR-04：检索已启用手术目录（不限国临/手术操作目录来源）。
     *
     * <p>接口名仍为 searchClinicalProcV3；关键字可空——打开下拉即可分页浏览（对齐医嘱选品）。</p>
     *
     * @param request 关键字与分页
     * @return 候选分页
     */
    public SearchClinicalProcV3Response searchClinicalProcV3(SearchClinicalProcV3Request request) {
        int pageNo = 1;
        int pageSize = 20;
        if (request != null && request.getPageNo() != null && request.getPageNo() > 0) {
            pageNo = request.getPageNo();
        }
        if (request != null && request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        }
        String keyword = null;
        if (request != null && isNotEmpty(request.getKeyword())) {
            keyword = request.getKeyword().trim();
        }
        // Pre：全来源 ACTIVE 术式分页（空关键字=浏览）
        Page<ProcedureDefinitionSearchItemQueryModel> page =
            procedureDefinitionQueryService.pageActiveByKeyword(keyword, PageRequest.of(pageNo - 1, pageSize));
        List<ProcedureDefinitionSearchItemQueryModel> searchItems = page.getContent();
        Set<Long> activityIds = new HashSet<>();
        // 存量行若仍挂 AD，批量取名供展示；开立不依赖 AD
        for (ProcedureDefinitionSearchItemQueryModel item : searchItems) {
            if (item != null && item.getActivityDefinitionId() != null) {
                activityIds.add(item.getActivityDefinitionId());
            }
        }
        Map<Long, ActivityDefinition> activityById = mapActivitiesById(activityIds);
        // Asm：术式候选 DTO
        SearchClinicalProcV3Response response = new SearchClinicalProcV3Response();
        response.setItems(inpatientDoctorStationSurgeryAnesthMapper.toClinicalProcV3Items(searchItems, activityById));
        response.setTotalCount(page.getTotalElements());
        return response;
    }

    /**
     * IF-IP-SUR-05：检索选品（麻醉主项 MAIN / 麻醉药品 MEDICATION）。
     *
     * <p>关键字可空：空则分页返回该类目下 ACTIVE 项（打开下拉即可浏览）。</p>
     * <p>EXTRA 附加选品已废弃；手术侧术式走 IF-IP-SUR-04，加收由护士划价。</p>
     *
     * @param request 侧别、选品种类与分页
     * @return 候选分页结果
     */
    public SearchSurchargeActivityDefinitionResponse searchSurchargeActivityDefinition(
        SearchSurchargeActivityDefinitionRequest request) {
        // Gate：侧别 + 选品种类合法
        String sideCode = resolveSideCode(request);
        String itemKindCode = resolveItemKindCode(request);
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
        String keyword = null;
        if (request != null && isNotEmpty(request.getKeyword())) {
            keyword = request.getKeyword().trim();
        }
        // 麻醉药品：药品定义全量（排除中草药类目），与诊疗 AD 分轨
        if (SurgeryAnesthApplyConstants.ITEM_KIND_MEDICATION.equals(itemKindCode)) {
            return searchMedicationItems(keyword, pageNo, pageSize);
        }
        String categoryCode = resolveSearchCategoryCode(sideCode);
        ProductDefinitionCatalogPageQuery query = new ProductDefinitionCatalogPageQuery();
        query.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        query.setCategoryCode(categoryCode);
        query.setActiveFlag(Boolean.TRUE);
        // 有关键字则模糊；无关键字分页浏览该类目 ACTIVE 项
        if (keyword != null) {
            query.setKeyword(keyword);
        }
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        // Pre：按类目分页检索 ACTIVE AD
        Page<ActivityDefinition> page = activityDefinitionQueryService.pageForCatalog(
            query, PageRequest.of(pageNo - 1, pageSize));
        // Asm：候选 DTO，回写选品种类
        SearchSurchargeActivityDefinitionResponse response =
            inpatientDoctorStationSurgeryAnesthMapper.toSurchargeItems(page);
        if (response.getItems() != null) {
            for (SurchargeActivityDefinitionItemDto item : response.getItems()) {
                if (item != null) {
                    item.setItemKindCode(itemKindCode);
                }
            }
        }
        return response;
    }

    /**
     * 麻醉药品选品：ACTIVE 药品定义分页（排除中草药 category=4）。
     *
     * @param keyword  可空关键字
     * @param pageNo   页码
     * @param pageSize 页大小
     * @return 药品候选
     */
    private SearchSurchargeActivityDefinitionResponse searchMedicationItems(
        String keyword, int pageNo, int pageSize) {
        ProductDefinitionCatalogPageQuery query = new ProductDefinitionCatalogPageQuery();
        query.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        query.setActiveFlag(Boolean.TRUE);
        // 与西药开立一致：排除中草药类目
        query.setExcludeMedicationCategoryCodes(List.of("4"));
        if (keyword != null) {
            query.setKeyword(keyword);
        }
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<MedicationDefinition> page = medicationDefinitionQueryService.pageForCatalog(
            query, PageRequest.of(pageNo - 1, pageSize));
        SearchSurchargeActivityDefinitionResponse response = new SearchSurchargeActivityDefinitionResponse();
        response.setItems(new ArrayList<>());
        // 分页空则总数 0，避免 NPE
        if (page == null) {
            response.setTotalCount(0L);
            return response;
        }
        response.setTotalCount(page.getTotalElements());
        for (MedicationDefinition def : page.getContent()) {
            if (def == null || def.getId() == null) {
                continue;
            }
            SurchargeActivityDefinitionItemDto item = new SurchargeActivityDefinitionItemDto();
            item.setMedicationDefinitionId(def.getId());
            item.setBusNo(def.getBusNo());
            item.setName(def.getName());
            item.setCategoryCode(def.getCategoryCode());
            // 优先拆零单位，否则采购单位，再回落占位
            String unit = trimToNull(def.getSplitUnitCode());
            if (unit == null) {
                unit = trimToNull(def.getPurchaseUnitCode());
            }
            if (unit == null) {
                unit = DoctorStationAdviceConstants.DEFAULT_MEDICATION_REQUEST_UNIT_CODE_PLACEHOLDER;
            }
            item.setUnitCode(unit);
            item.setItemKindCode(SurgeryAnesthApplyConstants.ITEM_KIND_MEDICATION);
            response.getItems().add(item);
        }
        return response;
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
        // Gate：拟手术时间须落在入院～出院窗（BR-IP-SUR-TIME-001；开立第一关）
        assertPlannedProcedureTimeWithinStay(request.getPlanned(), context);
        // 术式行未填频次时默认「立即」
        applyProcedureDefaultRate(request.getLines());
        // Pre：术式 PD 须 ACTIVE（术式不再要求 PD↔AD 对码；开立不加收行）
        validateSurgeryLinesPre(request.getLines());
        // 勾选需要麻醉：一次带齐麻醉明细，保存时生成完整麻醉申请（非空壳）
        boolean nestAnesthesia = Boolean.TRUE.equals(request.getNestAnesthesia());
        List<AnesthesiaApplyFormLineRequest> nestAnesthesiaLines = request.getAnesthesiaLines();
        if (nestAnesthesia) {
            // Gate：嵌套麻醉须指定麻醉医师（与独立麻醉申请同口径）
            assertAnesthetistRequired(request.getPlanned());
            // Gate：嵌套麻醉须至少一行可开立项目（仅麻醉项目/麻醉药品）
            if (nestAnesthesiaLines == null || nestAnesthesiaLines.isEmpty()) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_LINES_REQUIRED);
            }
            for (AnesthesiaApplyFormLineRequest line : nestAnesthesiaLines) {
                validateAnesthesiaLineGate(line);
            }
            loadAndValidateActivities(collectActivityIdsFromAnesthesiaLines(nestAnesthesiaLines));
        }
        // 编辑时解析旧嵌套麻醉 AR，供删旧草稿或取消嵌套
        String previousNestedAnesthesiaPrescriptionNo = null;
        if (isNotEmpty(request.getPreviousPrescriptionNo())) {
            List<ServiceRequestListModel> previousSurgeryRows = filterFormRowsByPrescription(
                request.getEncounterId(),
                request.getPreviousPrescriptionNo().trim(),
                ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
            previousNestedAnesthesiaPrescriptionNo =
                resolveNestedAnesthesiaPrescriptionNo(previousSurgeryRows, request.getEncounterId());
        }
        // Cmd：删旧手术草稿
        if (isNotEmpty(request.getPreviousPrescriptionNo())) {
            removePreviousDraftApplyForm(
                request.getEncounterId(),
                request.getPreviousPrescriptionNo().trim(),
                ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE,
                operatorId);
        }
        // 取消嵌套或重填嵌套：先软删旧麻醉草稿
        if (isNotEmpty(previousNestedAnesthesiaPrescriptionNo)) {
            removePreviousDraftApplyForm(
                request.getEncounterId(),
                previousNestedAnesthesiaPrescriptionNo.trim(),
                ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA,
                operatorId);
        }
        String nestedAnesthesiaPrescriptionNo = null;
        if (nestAnesthesia) {
            // 嵌套麻醉领 AA 号（与独立麻醉申请同序列）
            nestedAnesthesiaPrescriptionNo =
                serviceApplyFormOrchestrationSupport.allocateAnesthesiaApplicationPrescriptionNo();
        }
        // Pre/Cmd：领取手术 AR；先落术式，再落术前嘱托（basedOn 术式）；麻醉平级不写 basedOn
        String prescriptionNo = serviceApplyFormOrchestrationSupport.allocateArPrescriptionNo();
        List<Long> serviceRequestIds = new ArrayList<>();
        Map<Integer, Long> procedureSrIdBySortNumber = new HashMap<>();
        // 创建术式行：activity_id=0 + procedure_definition_id
        for (SurgeryApplyFormLineRequest line : request.getLines()) {
            if (!SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(trimToEmpty(line.getLineKindCode()))) {
                continue;
            }
            CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
                inpatientDoctorStationSurgeryAnesthMapper.toCreateDraftItem(
                    line, context, prescriptionNo, request.getPlanned(), nestedAnesthesiaPrescriptionNo);
            Long createdId = createOneDraft(operatorId, item);
            serviceRequestIds.add(createdId);
            if (line.getSortNumber() != null) {
                procedureSrIdBySortNumber.put(line.getSortNumber(), createdId);
            }
        }
        // 删旧草稿时同步清挂靠旧术式的术前嘱托（见 removePreviousDraftApplyForm）
        // 再创建术前嘱托：basedOn 对应术式 SR（手麻唯一主子）
        Long entrustActivityId = null;
        for (SurgeryApplyFormLineRequest line : request.getLines()) {
            if (!SurgeryAnesthApplyConstants.LINE_KIND_ENTRUST.equals(trimToEmpty(line.getLineKindCode()))) {
                continue;
            }
            if (entrustActivityId == null) {
                entrustActivityId = activityDefinitionQueryService.requireIdByExactName(
                    GeneralSharedConstants.ENTRUST_ACTIVITY_NAME);
            }
            Long parentSrId = resolveEntrustParentProcedureSrId(line, procedureSrIdBySortNumber);
            String rateCode = trimToNull(line.getRateCode());
            if (rateCode == null) {
                rateCode = resolveImmediateRateCode();
            }
            CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand entrustItem =
                inpatientDoctorStationSurgeryAnesthMapper.toEntrustCreateDraftItem(
                    line, context, entrustActivityId, parentSrId, rateCode);
            serviceRequestIds.add(createOneDraft(operatorId, entrustItem));
        }
        // 嵌套开启：按明细生成完整麻醉申请（麻醉项目 SR + 药品 MR；平级无 basedOn）
        if (nestAnesthesia
            && nestedAnesthesiaPrescriptionNo != null
            && nestAnesthesiaLines != null
            && !nestAnesthesiaLines.isEmpty()) {
            for (AnesthesiaApplyFormLineRequest line : nestAnesthesiaLines) {
                // 药品行走 MR，不在此建麻醉项目 SR
                if (SurgeryAnesthApplyConstants.LINE_KIND_MEDICATION.equals(trimToEmpty(line.getLineKindCode()))) {
                    continue;
                }
                CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
                    inpatientDoctorStationSurgeryAnesthMapper.toAnesthesiaCreateDraftItem(
                        line,
                        context,
                        nestedAnesthesiaPrescriptionNo,
                        request.getPlanned());
                serviceRequestIds.add(createOneDraft(operatorId, item));
            }
            for (AnesthesiaApplyFormLineRequest line : nestAnesthesiaLines) {
                // 非药品行已在上一段落库
                if (!SurgeryAnesthApplyConstants.LINE_KIND_MEDICATION.equals(trimToEmpty(line.getLineKindCode()))) {
                    continue;
                }
                createAnesthesiaMedicationDraft(operatorId, context, line, nestedAnesthesiaPrescriptionNo);
            }
            // 嵌套仅开药：药品已写 AA 单号，不落空壳 SR（列表/详情/签发按 AA 前缀聚合）
        }
        // Asm：回写手术 AR、已建术式行 id、可选嵌套麻醉 AR
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
        // Pre/Cmd/Asm：整单 DRAFT 签发，并连带嵌套麻醉 SR+同 AR 药品（见 signApplyForm）
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
        // Pre/Cmd：整单 ACTIVE 撤回，并连带嵌套麻醉 SR+同 AR 药品签退
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
     * IF-IP-SUR-16：删除手术申请单（仅草稿；一并清理嵌套麻醉草稿）。
     *
     * @param request 就诊与手术 AR 号
     * @return 已删除行 id
     */
    public DeleteSurgeryApplyFormResponse deleteSurgeryApplyForm(DeleteSurgeryApplyFormRequest request) {
        Long encounterId = null;
        String prescriptionNo = null;
        // Gate：空请求按空参进入统一删除校验
        if (request != null) {
            encounterId = request.getEncounterId();
            prescriptionNo = request.getPrescriptionNo();
        }
        encounterId = requireEncounterId(encounterId);
        prescriptionNo = requirePrescriptionNo(prescriptionNo);
        Long operatorId = requireOperatorId();
        // Pre：住院就诊可读门控
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        List<ServiceRequestListModel> surgeryRows = filterFormRowsByPrescription(
            encounterId, prescriptionNo, ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        // 整单不存在则无法删除
        if (surgeryRows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
        }
        // Pre：手术行须全部 DRAFT，已签发须先撤回
        assertAllDraftForDelete(surgeryRows);
        List<Long> deletedIds = new ArrayList<>();
        // Cmd：先清嵌套麻醉（ACTIVE 先撤回再软删，与取消勾选同构）
        String nestedAnesthNo = resolveNestedAnesthesiaPrescriptionNo(surgeryRows, encounterId);
        List<ServiceRequestListModel> anesthRows = findLinkedAnesthesiaRows(encounterId, surgeryRows, nestedAnesthNo);
        for (ServiceRequestListModel row : anesthRows) {
            if (RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                serviceRequestQueryService.mustGetServiceRequest(row.getId());
                assertWithdrawable(row.getId());
                withdrawOneDraft(operatorId, row.getId());
            }
        }
        for (ServiceRequestListModel row : anesthRows) {
            removeOneDraft(operatorId, row.getId());
            deletedIds.add(row.getId());
        }
        // Cmd：软删手术草稿行
        for (ServiceRequestListModel row : surgeryRows) {
            removeOneDraft(operatorId, row.getId());
            deletedIds.add(row.getId());
        }
        // Asm：返回 AR 与已删行 id
        DeleteSurgeryApplyFormResponse response = new DeleteSurgeryApplyFormResponse();
        response.setPrescriptionNo(prescriptionNo);
        response.setDeletedServiceRequestIds(deletedIds);
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
            // Cmd：仅领 AA 号并写回手术行嵌套字段；不落空壳 SR，麻醉页按 AA 号开立
            String anesthNo = serviceApplyFormOrchestrationSupport.allocateAnesthesiaApplicationPrescriptionNo();
            stampNestedAnesthesiaOnSurgeryDrafts(encounterId, surgeryPrescriptionNo, anesthNo, operatorId);
            response.setAnesthesiaPrescriptionNo(anesthNo);
            return response;
        }
        // enabled=false：撤回并软删关联麻醉行；仅开药/仅嵌套号时清药品与手术嵌套字段
        List<ServiceRequestListModel> anesthRows = findLinkedAnesthesiaRows(encounterId, surgeryRows, existingAnesthNo);
        // 已签发麻醉行须先校验存在性与可撤回性，再逐条撤回
        for (ServiceRequestListModel row : anesthRows) {
            if (RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                serviceRequestQueryService.mustGetServiceRequest(row.getId());
                assertWithdrawable(row.getId());
                withdrawOneDraft(operatorId, row.getId());
            }
        }
        // 草稿或已撤回行统一软删
        for (ServiceRequestListModel row : anesthRows) {
            removeOneDraft(operatorId, row.getId());
        }
        // 同嵌套号药品签退/删草稿（含仅开药）
        if (isNotEmpty(existingAnesthNo)) {
            signOffAnesthesiaMedications(existingAnesthNo.trim(), operatorId);
            removeAnesthesiaMedicationDraftsByPrescriptionNo(existingAnesthNo.trim(), operatorId);
            stampNestedAnesthesiaOnSurgeryDrafts(encounterId, surgeryPrescriptionNo, null, operatorId);
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
        // Pre：筛医生开立、AA/存量 AR 前缀、类目 25 的麻醉申请行
        List<ServiceRequestListModel> rows = filterApplyFormRows(
            encounterId, ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        EnrichBundle bundle = buildListEnrichBundle(rows);
        Map<String, String> linkedMap = buildLinkedSurgeryPrescriptionMap(encounterId, rows);
        // Asm：麻醉单头（含 SR）+ 关联手术 enrich
        List<AnesthesiaApplyFormHeaderDto> forms = inpatientDoctorStationSurgeryAnesthMapper.toAnesthesiaFormHeaders(
            rows, bundle.requesterNameById, bundle.activityById, linkedMap);
        // Asm：仅开药（无 SR）的 AA 单并入列表头
        forms = this.mergeMedicationOnlyAnesthesiaFormHeaders(encounterId, forms, linkedMap);
        ListAnesthesiaApplyFormsResponse response = new ListAnesthesiaApplyFormsResponse();
        response.setForms(forms);
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
        // Pre：按单号定位麻醉单内 SR 行（可空：仅开药）
        List<ServiceRequestListModel> rows = filterFormRowsByPrescription(
            encounterId, prescriptionNo, ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        List<MedicationRequestListQueryModel> medOnlyRows = List.of();
        if (rows.isEmpty()) {
            // 仅开药：无类目 25 SR，按 AA/同单号药嘱回开
            medOnlyRows = this.listAnesthesiaMedicationsByPrescriptionNo(prescriptionNo);
            if (medOnlyRows.isEmpty()) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                    SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
            }
        }
        GetAnesthesiaApplyFormDetailResponse detail;
        if (rows.isEmpty()) {
            // Asm：纯药品单——头来自药嘱聚合，行仅药品
            detail = this.buildMedicationOnlyAnesthesiaDetail(encounterId, prescriptionNo, medOnlyRows);
        } else {
            // Pre：子表明细、拟人员与关联手术 AR
            EnrichBundle bundle = buildDetailEnrichBundle(rows);
            String linkedSurgery = resolveLinkedSurgeryPrescriptionForAnesthForm(encounterId, rows);
            // Asm：头/行/横表 DTO（诊疗 SR）
            detail = inpatientDoctorStationSurgeryAnesthMapper.toAnesthesiaFormDetail(
                rows,
                bundle.detailsByRequestId,
                bundle.activityById,
                bundle.practitionerNameById,
                linkedSurgery);
            // Asm：同单号麻醉药品 MR 并入明细（与麻醉项目平级）
            appendAnesthesiaMedicationLines(detail, prescriptionNo);
        }
        // Asm：单位码 → 展示名（禁止前端把术语码当单位名）
        enrichAnesthesiaLineUnitNames(detail);
        return detail;
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
        // Gate：麻醉单横表拟手术时间同窗校验（与手术开立同口径；未填则跳过）
        assertPlannedProcedureTimeWithinStay(request.getPlanned(), context);
        loadAndValidateActivities(collectActivityIdsFromAnesthesiaLines(request.getLines()));
        // 编辑保存先删旧麻醉 DRAFT 行
        if (isNotEmpty(request.getPreviousPrescriptionNo())) {
            removePreviousDraftApplyForm(
                request.getEncounterId(),
                request.getPreviousPrescriptionNo().trim(),
                ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA,
                operatorId);
        }
        // Cmd：领取麻醉 AA 单号；麻醉项目与药品平级，均不写 basedOn；药品 prescriptionNo 同写 AA
        String prescriptionNo = serviceApplyFormOrchestrationSupport.allocateAnesthesiaApplicationPrescriptionNo();
        List<Long> serviceRequestIds = new ArrayList<>();
        for (AnesthesiaApplyFormLineRequest line : request.getLines()) {
            // 药品行只落 MR，跳过 SR 创建
            if (SurgeryAnesthApplyConstants.LINE_KIND_MEDICATION.equals(trimToEmpty(line.getLineKindCode()))) {
                continue;
            }
            CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
                inpatientDoctorStationSurgeryAnesthMapper.toAnesthesiaCreateDraftItem(
                    line, context, prescriptionNo, request.getPlanned());
            serviceRequestIds.add(createOneDraft(operatorId, item));
        }
        // 麻醉药品：临时药嘱，与麻醉项目平级（不写 basedOn；方号=AA 单号）
        for (AnesthesiaApplyFormLineRequest line : request.getLines()) {
            // 非药品行已在上一段落库
            if (!SurgeryAnesthApplyConstants.LINE_KIND_MEDICATION.equals(trimToEmpty(line.getLineKindCode()))) {
                continue;
            }
            createAnesthesiaMedicationDraft(operatorId, context, line, prescriptionNo);
        }
        // 关联手术：写手术行 nestedAnesthesiaPrescriptionNo（表单级关联，非 basedOn）
        if (isNotEmpty(request.getLinkedSurgeryPrescriptionNo())) {
            stampNestedAnesthesiaOnSurgeryDrafts(
                request.getEncounterId(),
                request.getLinkedSurgeryPrescriptionNo().trim(),
                prescriptionNo,
                operatorId);
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
        // Pre/Cmd/Asm：类目 25 整单签发，并连带同 AR 麻醉药品（复用 signApplyForm）
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
        // Pre/Cmd：类目 25 整单撤回，并连带同 AR 麻醉药品签退
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
     * IF-IP-SUR-17：删除麻醉申请单（仅草稿）。
     *
     * @param request 就诊与麻醉 AR 号
     * @return 已删除行 id
     */
    public DeleteAnesthesiaApplyFormResponse deleteAnesthesiaApplyForm(DeleteAnesthesiaApplyFormRequest request) {
        Long encounterId = null;
        String prescriptionNo = null;
        // Gate：空请求按空参进入统一删除校验
        if (request != null) {
            encounterId = request.getEncounterId();
            prescriptionNo = request.getPrescriptionNo();
        }
        encounterId = requireEncounterId(encounterId);
        prescriptionNo = requirePrescriptionNo(prescriptionNo);
        Long operatorId = requireOperatorId();
        // Pre：住院就诊可读门控
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        List<ServiceRequestListModel> rows = filterFormRowsByPrescription(
            encounterId, prescriptionNo, ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        // 整单不存在：仅开药单允许只删同号药品草稿
        if (rows.isEmpty()) {
            if (this.listAnesthesiaMedicationsByPrescriptionNo(prescriptionNo).isEmpty()) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                    SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
            }
            removeAnesthesiaMedicationDraftsByPrescriptionNo(prescriptionNo, operatorId);
            DeleteAnesthesiaApplyFormResponse medOnlyResp = new DeleteAnesthesiaApplyFormResponse();
            medOnlyResp.setPrescriptionNo(prescriptionNo);
            medOnlyResp.setDeletedServiceRequestIds(new ArrayList<>());
            return medOnlyResp;
        }
        // Pre：麻醉行须全部 DRAFT，已签发须先撤回
        assertAllDraftForDelete(rows);
        List<Long> deletedIds = new ArrayList<>();
        // Cmd：先清同单号麻醉药品草稿，再软删麻醉 SR
        removeAnesthesiaMedicationDraftsByPrescriptionNo(prescriptionNo, operatorId);
        for (ServiceRequestListModel row : rows) {
            removeOneDraft(operatorId, row.getId());
            deletedIds.add(row.getId());
        }
        // Asm：返回单号与已删行 id
        DeleteAnesthesiaApplyFormResponse response = new DeleteAnesthesiaApplyFormResponse();
        response.setPrescriptionNo(prescriptionNo);
        response.setDeletedServiceRequestIds(deletedIds);
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
     *
     * <p>{@code pdByActivityId} 字段名历史遗留：现主要为「PD 主键 → 检索项」，
     * 并兼容旧数据「AD 主键 → PD」反查结果（Mapper 双路径读）。</p>
     */
    private static final class EnrichBundle {
        /** 开立人姓名。 */
        private Map<Long, String> requesterNameById = new HashMap<>();
        /** 术式检索项（主键多为 PD id；兼容旧 AD id）。 */
        private Map<Long, ProcedureDefinitionSearchItemQueryModel> pdByActivityId = new HashMap<>();
        /** 加收等 AD。 */
        private Map<Long, ActivityDefinition> activityById = new HashMap<>();
        /** SR 明细。 */
        private Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId = new HashMap<>();
        /** 拟手术人员姓名。 */
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
        Set<Long> procedureDefinitionIds = new HashSet<>();
        // 扫描行：开立人、加收 AD、术式 PD（主表真源）
        for (ServiceRequestListModel row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getRequesterId() != null) {
                requesterIds.add(row.getRequesterId());
            }
            if (row.getActivityId() != null
                && row.getActivityId() != SurgeryAnesthApplyConstants.ACTIVITY_ID_PLACEHOLDER) {
                activityIds.add(row.getActivityId());
            }
            if (row.getProcedureDefinitionId() != null) {
                procedureDefinitionIds.add(row.getProcedureDefinitionId());
            }
        }
        bundle.requesterNameById = mapPractitionerNames(requesterIds);
        bundle.activityById = mapActivitiesById(activityIds);
        // pdByActivityId 字段复用为「PD id → 检索项」（历史字段名保留，避免 Mapper 签名大改）
        bundle.pdByActivityId = procedureDefinitionQueryService.mapSearchItemsByIds(procedureDefinitionIds);
        // 兼容旧数据：无主表 PD 时仍按 AD 反查一次并入图（key 为 AD id，行装配侧双路径）
        Map<Long, ProcedureDefinitionSearchItemQueryModel> legacyByAd =
            procedureDefinitionQueryService.mapActiveClinicalByActivityDefinitionIds(activityIds);
        for (Map.Entry<Long, ProcedureDefinitionSearchItemQueryModel> entry : legacyByAd.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            bundle.pdByActivityId.putIfAbsent(entry.getKey(), entry.getValue());
            if (entry.getValue().getId() != null) {
                bundle.pdByActivityId.putIfAbsent(entry.getValue().getId(), entry.getValue());
            }
        }
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
        // 麻醉：AA 前缀（新）或 AR 前缀（存量空壳/历史单）
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryCode)) {
            return ServiceApplyFormOrchestrationSupport.isAnesthesiaApplicationPrescriptionNo(row.getPrescriptionNo())
                || ServiceApplyFormOrchestrationSupport.isRequisitionApplicationPrescriptionNo(row.getPrescriptionNo());
        }
        // 手术等：AR 申请单号
        return ServiceApplyFormOrchestrationSupport.isRequisitionApplicationPrescriptionNo(row.getPrescriptionNo());
    }

    /**
     * 拟手术时间相对就诊入院/出院窗（BR-IP-SUR-TIME-001）。
     *
     * <p>未填拟手术时间跳过；未出院则不上界。越窗拒绝保存，避免开立即写入非法计划时刻。</p>
     *
     * @param planned 横表计划字段；可空
     * @param context 住院就诊上下文（含 startTime/endTime）
     */
    private void assertPlannedProcedureTimeWithinStay(
            PlannedSurgeryFieldsDto planned,
            InpatientEncounterContextQueryModel context) {
        // 无计划体或无拟手术时间则不拦
        if (planned == null || planned.getProcedureTime() == null) {
            return;
        }
        LocalDateTime admissionTime = null;
        LocalDateTime dischargeTime = null;
        // 就诊上下文缺失时仅跳过边界（Gate 已保证 encounter 存在；此处防空）
        if (context != null) {
            admissionTime = context.getStartTime();
            dischargeTime = context.getEndTime();
        }
        String outside = EncounterStayTimeBoundsUtil.messageIfOutsideStay(
            planned.getProcedureTime(),
            admissionTime,
            dischargeTime,
            "拟手术时间");
        // 越窗则拒绝开立保存
        if (outside != null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, outside);
        }
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
        Set<Integer> procedureSortNumbers = new HashSet<>();
        // 逐行 Gate 并统计术式行数与主手术标志数
        for (SurgeryApplyFormLineRequest line : request.getLines()) {
            validateSurgeryLineGate(line);
            if (SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(trimToEmpty(line.getLineKindCode()))) {
                procedureCount = procedureCount + 1;
                if (line.getSortNumber() != null) {
                    procedureSortNumbers.add(line.getSortNumber());
                }
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
        // 术前嘱托挂靠的 sortNumber 必须是本单某条术式
        for (SurgeryApplyFormLineRequest line : request.getLines()) {
            if (!SurgeryAnesthApplyConstants.LINE_KIND_ENTRUST.equals(trimToEmpty(line.getLineKindCode()))) {
                continue;
            }
            if (line.getBasedOnProcedureSortNumber() == null
                || !procedureSortNumbers.contains(line.getBasedOnProcedureSortNumber())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_ENTRUST_PARENT_NOT_FOUND);
            }
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
        // Gate：麻醉医师必填
        assertAnesthetistRequired(request.getPlanned());
        // 逐行校验种类、AD、数量单位
        for (AnesthesiaApplyFormLineRequest line : request.getLines()) {
            validateAnesthesiaLineGate(line);
        }
    }

    /**
     * Gate：横表须含麻醉医师角色。
     *
     * @param planned 拟手术/麻醉横表
     */
    private void assertAnesthetistRequired(PlannedSurgeryFieldsDto planned) {
        // Gate：未选麻醉医师则拒绝保存（独立麻醉与嵌套麻醉同口径）
        if (resolveAnesthetistId(planned) == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_ANESTHETIST_REQUIRED);
        }
    }

    /**
     * 从横表人员列表解析麻醉医师主键。
     *
     * @param planned 拟手术/麻醉横表
     * @return 麻醉医师主键；未填 null
     */
    private Long resolveAnesthetistId(PlannedSurgeryFieldsDto planned) {
        // 横表或人员列表为空：视为未指定麻醉医师
        if (planned == null || planned.getPerformers() == null) {
            return null;
        }
        for (PlannedPerformerDto performer : planned.getPerformers()) {
            // 空行或无医务人员主键跳过
            if (performer == null || performer.getPractitionerId() == null) {
                continue;
            }
            // 角色为麻醉医师则取该主键
            if (ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_ANESTHETIST
                .equals(trimToEmpty(performer.getRoleCode()))) {
                return performer.getPractitionerId();
            }
        }
        // 列表中无麻醉医师角色
        return null;
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
        // 耗材禁止开立
        if (SurgeryAnesthApplyConstants.LINE_KIND_CONSUMABLE.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_CONSUMABLE_FORBIDDEN);
        }
        // 附加行为伪需求：医生只开术式+术前嘱托，加收由护士划价
        if (SurgeryAnesthApplyConstants.LINE_KIND_SURCHARGE.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_SURCHARGE_FORBIDDEN);
        }
        // 手术开立仅允许术式 / 术前嘱托
        if (!SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(kind)
            && !SurgeryAnesthApplyConstants.LINE_KIND_ENTRUST.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_LINE_KIND_INVALID);
        }
        // 术前嘱托：正文 + 挂靠术式 sortNumber 必填
        if (SurgeryAnesthApplyConstants.LINE_KIND_ENTRUST.equals(kind)) {
            if (isEmpty(line.getEntrustContent())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_ENTRUST_CONTENT_REQUIRED);
            }
            if (line.getBasedOnProcedureSortNumber() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_ENTRUST_PARENT_REQUIRED);
            }
            BigDecimal entrustQty = line.getQuantity();
            if (entrustQty == null) {
                entrustQty = BigDecimal.ONE;
            }
            String entrustUnit = line.getUnitCode();
            if (isEmpty(entrustUnit)) {
                entrustUnit = SurgeryAnesthApplyConstants.NEST_SHELL_DEFAULT_UNIT_CODE;
            }
            validateQuantityAndUnit(entrustQty, entrustUnit);
            return;
        }
        validateQuantityAndUnit(line.getQuantity(), line.getUnitCode());
        // 术式行只校验国临 PD；activity_id 由后端写 0，不要求前端传 AD；频次可空（保存时默认立即）
        if (line.getProcedureDefinitionId() == null) {
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
        // 耗材禁止开立
        if (SurgeryAnesthApplyConstants.LINE_KIND_CONSUMABLE.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_CONSUMABLE_FORBIDDEN);
        }
        // 附加行为伪需求：麻醉只开项目与麻醉药，加收由护士划价
        if (SurgeryAnesthApplyConstants.LINE_KIND_SURCHARGE.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_SURCHARGE_FORBIDDEN);
        }
        // 麻醉开立仅允许麻醉项目 / 麻醉药品
        if (!SurgeryAnesthApplyConstants.LINE_KIND_ANESTHESIA.equals(kind)
            && !SurgeryAnesthApplyConstants.LINE_KIND_MEDICATION.equals(kind)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_LINE_KIND_INVALID);
        }
        // 药品行认药品定义；麻醉项目行认 AD
        if (SurgeryAnesthApplyConstants.LINE_KIND_MEDICATION.equals(kind)) {
            if (line.getMedicationDefinitionId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_MEDICATION_DEFINITION_REQUIRED);
            }
            // 药品：用法 + 单次剂量 + 剂量单位必填（麻醉不按剂量单位换算总量，单位可选手动改选）
            if (isEmpty(line.getMethodCode())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_METHOD_CODE_REQUIRED);
            }
            if (line.getDose() == null || line.getDose().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_DOSE_REQUIRED);
            }
            if (isEmpty(line.getDoseUnitCode())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_DOSE_UNIT_REQUIRED);
            }
        } else if (line.getActivityDefinitionId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_ACTIVITY_DEFINITION_REQUIRED);
        }
        // 麻醉项目/药品：频次必选（不能默认）
        if (isEmpty(line.getRateCode())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_RATE_CODE_REQUIRED);
        }
        validateQuantityAndUnit(line.getQuantity(), line.getUnitCode());
    }

    /**
     * 开立麻醉药品临时医嘱草稿（药房发药；与麻醉项目平级，不写 basedOn；prescriptionNo=麻醉 AA 单号）。
     *
     * @param operatorId     操作人
     * @param context        就诊上下文
     * @param line           药品行
     * @param prescriptionNo 麻醉申请 AA 单号（与麻醉项目 SR 同号，供详情回显）
     */
    private void createAnesthesiaMedicationDraft(
        Long operatorId,
        InpatientEncounterContextQueryModel context,
        AnesthesiaApplyFormLineRequest line,
        String prescriptionNo) {
        CreateMedicationOrderDraftCommand cmd = new CreateMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(context.getEncounterId());
        cmd.setPatientId(context.getPatientId());
        cmd.setMedicationId(line.getMedicationDefinitionId());
        cmd.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        cmd.setQuantity(line.getQuantity());
        cmd.setUnitCode(line.getUnitCode());
        // 用法空则回落静脉注射（与前端麻醉开立默认一致；不用目录常见静滴）
        String methodCode = trimToNull(line.getMethodCode());
        if (methodCode == null) {
            methodCode = resolveDefaultAnesthesiaMethodCode();
        }
        cmd.setMethodCode(methodCode);
        cmd.setRateCode(trimToNull(line.getRateCode()));
        cmd.setDose(line.getDose());
        String doseUnit = trimToNull(line.getDoseUnitCode());
        if (doseUnit == null) {
            doseUnit = trimToNull(line.getUnitCode());
        }
        cmd.setDoseUnitCode(doseUnit);
        cmd.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        cmd.setOrgId(context.getOrganizationId());
        cmd.setRecorderId(operatorId);
        cmd.setSortNumber(line.getSortNumber());
        cmd.setRemark(line.getNote());
        // 与麻醉 SR 共用 AR，详情按 prescriptionNo 拉回药品行
        cmd.setPrescriptionNo(trimToNull(prescriptionNo));
        medicationRequestDomainService.createMedicationOrderDraft(cmd);
    }

    /**
     * 术式行未传频次时回落「立即」（与临时医嘱默认一致）。
     *
     * @return 立即频次码；找不到则 null
     */
    private String resolveImmediateRateCode() {
        List<OptionDto> options = frequencyOptionLoader.loadActiveRateOptions();
        if (options == null) {
            return null;
        }
        for (OptionDto opt : options) {
            if (opt == null) {
                continue;
            }
            if (SurgeryAnesthApplyConstants.DEFAULT_PROCEDURE_RATE_LABEL.equals(trimToEmpty(opt.getLabel()))) {
                return trimToNull(opt.getValue());
            }
        }
        return null;
    }

    /**
     * 麻醉药品默认用法：术语 label「静脉注射」对应 code；未配则硬回落 404。
     *
     * @return 非空用法 code
     */
    private String resolveDefaultAnesthesiaMethodCode() {
        List<OptionDto> options = codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.METHOD_CODE);
        if (options != null) {
            for (OptionDto opt : options) {
                if (opt == null) {
                    continue;
                }
                if (SurgeryAnesthApplyConstants.DEFAULT_MEDICATION_METHOD_LABEL.equals(
                    trimToEmpty(opt.getLabel()))) {
                    String code = trimToNull(opt.getValue());
                    if (code != null) {
                        return code;
                    }
                }
            }
        }
        return SurgeryAnesthApplyConstants.DEFAULT_MEDICATION_METHOD_CODE;
    }

    /**
     * 保存前：术式行补默认立即频次。
     *
     * @param lines 手术行
     */
    private void applyProcedureDefaultRate(List<SurgeryApplyFormLineRequest> lines) {
        if (lines == null) {
            return;
        }
        String immediate = resolveImmediateRateCode();
        for (SurgeryApplyFormLineRequest line : lines) {
            if (line == null) {
                continue;
            }
            if (!SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(trimToEmpty(line.getLineKindCode()))) {
                continue;
            }
            if (isEmpty(line.getRateCode()) && immediate != null) {
                line.setRateCode(immediate);
            }
        }
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
     * Pre：校验术式行国临 PD 已启用（不再要求 PD↔AD 对码）。
     *
     * @param lines 行列表
     */
    private void validateSurgeryLinesPre(List<SurgeryApplyFormLineRequest> lines) {
        for (SurgeryApplyFormLineRequest line : lines) {
            if (!SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(trimToEmpty(line.getLineKindCode()))) {
                continue;
            }
            ProcedureDefinitionDetailQueryModel pd = procedureDefinitionQueryService.getOne(line.getProcedureDefinitionId());
            // PD 非 ACTIVE 不可开立
            if (!PublicationStatus.ACTIVE.getCode().equals(trimToEmpty(pd.getStatusEnum()))) {
                throw new BusinessException(
                    ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND,
                    SurgeryAnesthApplyConstants.MSG_PROCEDURE_DEFINITION_UNAVAILABLE);
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
        // 旧 AR 无 SR：历史纯药品脏数据仍可能残留同号 MR，麻醉类目先清药嘱再放行；其余类目仍报不存在
        if (oldRows.isEmpty()) {
            // 麻醉编辑删旧：无壳/无项目 SR 时只清同号药品草稿，避免孤儿药嘱阻断重开
            if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryCode)) {
                removeAnesthesiaMedicationDraftsByPrescriptionNo(prescriptionNo, operatorId);
                return;
            }
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
        }
        // 手术删旧：先软删挂靠术式的术前嘱托草稿，再删术式行
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(categoryCode)) {
            removeEntrustDraftsBasedOnProcedureRows(oldRows, operatorId);
        }
        // 麻醉删旧：同步软删同 AR 麻醉药品草稿
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryCode)) {
            removeAnesthesiaMedicationDraftsByPrescriptionNo(prescriptionNo, operatorId);
        }
        for (ServiceRequestListModel row : oldRows) {
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
     * 签发整单（手术含术前嘱托与嵌套麻醉包；麻醉含同 AR 药品）。
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
        // Gate：签发码必填（域命令不含 signCode，由应用层校验）
        if (isEmpty(signCode)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_SIGN_CODE_REQUIRED);
        }
        Long operatorId = requireOperatorId();
        encounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        List<ServiceRequestListModel> rows = filterFormRowsByPrescription(encounterId, prescriptionNo, categoryCode);
        boolean anesthesiaMedOnly = false;
        // Gate：整单不存在——麻醉允许仅开药（无 SR、有同号药品草稿）
        if (rows.isEmpty()) {
            if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryCode)
                && this.hasAnesthesiaMedicationDrafts(prescriptionNo)) {
                anesthesiaMedOnly = true;
            } else {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                    SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
            }
        }
        // Gate：存在非 DRAFT 行则禁止整单签发，避免部分生效
        for (ServiceRequestListModel row : rows) {
            // 非草稿行拒绝整单签发
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_NOT_ALL_DRAFT_FOR_SIGN);
            }
        }
        List<Long> signedIds = new ArrayList<>();
        LocalDateTime signTime = LocalDateTime.now();
        // Cmd：手术签发时同步签发挂靠术式的术前嘱托草稿
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(categoryCode)) {
            // 遍历挂靠本单术式的术前嘱托草稿
            for (ServiceRequestListModel entrust : listEntrustDraftsBasedOnProcedureRows(rows)) {
                signOneServiceRequest(operatorId, entrust.getId(), signTime);
                signedIds.add(entrust.getId());
            }
        }
        // Cmd：逐条签发本单 DRAFT 行（仅开药麻醉无 SR 可跳过）
        for (ServiceRequestListModel row : rows) {
            signOneServiceRequest(operatorId, row.getId(), signTime);
            signedIds.add(row.getId());
        }
        // Cmd：手术签发连带嵌套麻醉申请（麻醉 SR + 同单号药品）
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(categoryCode)) {
            String nestedAnesthNo = resolveNestedAnesthesiaPrescriptionNo(rows, encounterId);
            // 有嵌套麻醉单号才连带签发
            if (isNotEmpty(nestedAnesthNo)) {
                signedIds.addAll(
                    signAnesthesiaBundle(encounterId, nestedAnesthNo.trim(), operatorId, signTime));
            }
        }
        // Cmd：麻醉签发时同单号麻醉药品一并签发（含仅开药）
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryCode)
            || anesthesiaMedOnly) {
            signedIds.addAll(signAnesthesiaMedicationDrafts(prescriptionNo, operatorId));
        }
        SignSurgeryApplyFormResponse response = new SignSurgeryApplyFormResponse();
        response.setPrescriptionNo(prescriptionNo);
        response.setSignedServiceRequestIds(signedIds);
        return response;
    }

    /**
     * 撤回整单（手术含术前嘱托与嵌套麻醉包；麻醉含同 AR 药品签退）。
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
        boolean anesthesiaMedOnly = false;
        // Gate：整单不存在——麻醉允许仅开药（无 SR、有同号药品）
        if (rows.isEmpty()) {
            if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryCode)
                && !this.listAnesthesiaMedicationsByPrescriptionNo(prescriptionNo).isEmpty()) {
                anesthesiaMedOnly = true;
            } else {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND,
                    SurgeryAnesthApplyConstants.MSG_APPLY_FORM_NOT_FOUND);
            }
        }
        List<Long> withdrawnIds = new ArrayList<>();
        // Cmd：手术撤回时同步撤回挂靠术式的术前嘱托 ACTIVE 行
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(categoryCode)) {
            // 遍历挂靠本单术式的术前嘱托 ACTIVE 行
            for (ServiceRequestListModel entrust : listEntrustActiveBasedOnProcedureRows(rows)) {
                assertWithdrawable(entrust.getId());
                withdrawOneDraft(operatorId, entrust.getId());
                withdrawnIds.add(entrust.getId());
            }
        }
        // Cmd：仅撤回本单 ACTIVE 行；DRAFT 行不在此接口处理
        for (ServiceRequestListModel row : rows) {
            // 非 ACTIVE 跳过（幂等）
            if (RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                assertWithdrawable(row.getId());
                withdrawOneDraft(operatorId, row.getId());
                withdrawnIds.add(row.getId());
            }
        }
        // Cmd：手术撤回连带嵌套麻醉申请（麻醉 SR + 同单号药品签退）
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(categoryCode)) {
            String nestedAnesthNo = resolveNestedAnesthesiaPrescriptionNo(rows, encounterId);
            // 有嵌套麻醉单号才连带撤回
            if (isNotEmpty(nestedAnesthNo)) {
                withdrawnIds.addAll(
                    withdrawAnesthesiaBundle(encounterId, nestedAnesthNo.trim(), operatorId));
            }
        }
        // Cmd：麻醉撤回时同单号药品签退回草稿（含仅开药）
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryCode)
            || anesthesiaMedOnly) {
            withdrawnIds.addAll(signOffAnesthesiaMedications(prescriptionNo, operatorId));
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
     * 删除前门控：整单须全部为草稿。
     *
     * @param rows 申请单 SR 行
     */
    private void assertAllDraftForDelete(List<ServiceRequestListModel> rows) {
        for (ServiceRequestListModel row : rows) {
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    SurgeryAnesthApplyConstants.MSG_NOT_DRAFT_FOR_DELETE);
            }
        }
    }

    /**
     * 解析选品种类（空默认 MAIN；EXTRA 已废弃并拒绝）。
     *
     * @param request 检索请求
     * @return MAIN / MEDICATION
     */
    private String resolveItemKindCode(SearchSurchargeActivityDefinitionRequest request) {
        String raw = "";
        if (request != null) {
            raw = trimToEmpty(request.getItemKindCode());
        }
        if (raw.isEmpty() || SurgeryAnesthApplyConstants.ITEM_KIND_MAIN.equals(raw)) {
            return SurgeryAnesthApplyConstants.ITEM_KIND_MAIN;
        }
        // EXTRA 为伪需求：开立不允许附加选品
        if (SurgeryAnesthApplyConstants.ITEM_KIND_EXTRA.equals(raw)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_SURCHARGE_FORBIDDEN);
        }
        if (SurgeryAnesthApplyConstants.ITEM_KIND_MEDICATION.equals(raw)) {
            return SurgeryAnesthApplyConstants.ITEM_KIND_MEDICATION;
        }
        throw new BusinessException(
            ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
            SurgeryAnesthApplyConstants.MSG_ITEM_KIND_INVALID);
    }

    /**
     * 解析诊疗选品类目：按侧别类目（麻醉=25，手术=24）。
     *
     * <p>开立检索仅 MAIN（麻醉主项）；附加选品已废弃。</p>
     *
     * @param sideCode 侧别
     * @return category code
     */
    private String resolveSearchCategoryCode(String sideCode) {
        String side = trimToEmpty(sideCode);
        // 选品类目认侧别（麻醉主项 25；手术侧本接口一般不走——术式用 PD 检索）
        if (SurgeryAnesthApplyConstants.SIDE_SURGERY.equals(side)) {
            return ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE;
        }
        if (SurgeryAnesthApplyConstants.SIDE_ANESTHESIA.equals(side)) {
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
        List<ServiceRequestListModel> anesthAll = filterApplyFormRows(
            encounterId, ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        List<ServiceRequestListModel> matched = new ArrayList<>();
        // 仅按手术行 nestedAnesthesiaPrescriptionNo 匹配（麻醉不再 basedOn 术式）
        for (ServiceRequestListModel row : anesthAll) {
            if (row == null) {
                continue;
            }
            if (isNotEmpty(nestedAnesthesiaPrescriptionNo)
                && nestedAnesthesiaPrescriptionNo.equals(row.getPrescriptionNo())) {
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
        // 表单级关联：手术行 nestedAnesthesiaPrescriptionNo → 麻醉 AR（不再靠 basedOn）
        for (ServiceRequestListModel row : all) {
            if (row == null || isEmpty(row.getNestedAnesthesiaPrescriptionNo())) {
                continue;
            }
            if (!ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(trimToEmpty(row.getCategoryEnum()))) {
                continue;
            }
            if (isEmpty(row.getPrescriptionNo())) {
                continue;
            }
            result.put(row.getNestedAnesthesiaPrescriptionNo().trim(), row.getPrescriptionNo().trim());
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
     * 收集麻醉行 AD id（麻醉项目；不含药品与已禁止的附加行）。
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

    /**
     * 解析术前嘱托挂靠的术式 SR id。
     *
     * @param line                       嘱托行
     * @param procedureSrIdBySortNumber  术式 sortNumber → SR id
     * @return 术式 SR id
     */
    private Long resolveEntrustParentProcedureSrId(
        SurgeryApplyFormLineRequest line,
        Map<Integer, Long> procedureSrIdBySortNumber) {
        Long parentSrId = procedureSrIdBySortNumber.get(line.getBasedOnProcedureSortNumber());
        // 挂靠术式未落库则无法建立 basedOn 主子
        if (parentSrId == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                SurgeryAnesthApplyConstants.MSG_ENTRUST_PARENT_NOT_FOUND);
        }
        return parentSrId;
    }

    /**
     * 详情补齐挂靠术式的术前嘱托行。
     *
     * @param detail       手术详情（已含术式行）
     * @param procedureRows 术式 SR 行
     */
    private void appendEntrustLinesToSurgeryDetail(
        GetSurgeryApplyFormDetailResponse detail,
        List<ServiceRequestListModel> procedureRows) {
        if (detail == null || procedureRows == null || procedureRows.isEmpty()) {
            return;
        }
        Map<Long, Integer> sortByProcedureId = new HashMap<>();
        Set<Long> procedureIds = new HashSet<>();
        for (ServiceRequestListModel row : procedureRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            procedureIds.add(row.getId());
            sortByProcedureId.put(row.getId(), row.getSortNumber());
        }
        if (procedureIds.isEmpty()) {
            return;
        }
        List<ServiceRequest> entrustEntities = serviceRequestQueryService.listServiceRequestsByBasedOnAnchors(
            TableNameConstants.WKF_SERVICE_REQUEST, procedureIds);
        List<SurgeryApplyFormLineDto> lines = detail.getLines();
        if (lines == null) {
            lines = new ArrayList<>();
            detail.setLines(lines);
        }
        for (ServiceRequest entrust : entrustEntities) {
            if (entrust == null) {
                continue;
            }
            // 仅术前嘱托（类目 52）回显到手术明细
            if (!ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST.equals(
                trimToEmpty(entrust.getCategoryEnum()))) {
                continue;
            }
            SurgeryApplyFormLineDto dto = new SurgeryApplyFormLineDto();
            dto.setServiceRequestId(entrust.getId());
            dto.setLineKindCode(SurgeryAnesthApplyConstants.LINE_KIND_ENTRUST);
            dto.setEntrustContent(entrust.getEntrustContent());
            dto.setActivityDefinitionId(entrust.getActivityId());
            dto.setQuantity(entrust.getQuantity());
            dto.setUnitCode(entrust.getUnitCode());
            dto.setStatusEnum(entrust.getStatusEnum());
            dto.setSortNumber(entrust.getSortNumber());
            dto.setBasedOnProcedureServiceRequestId(entrust.getBasedOnId());
            dto.setBasedOnProcedureSortNumber(sortByProcedureId.get(entrust.getBasedOnId()));
            lines.add(dto);
        }
    }

    /**
     * 软删挂靠指定术式行的术前嘱托草稿。
     *
     * @param procedureRows 术式行
     * @param operatorId    操作人
     */
    private void removeEntrustDraftsBasedOnProcedureRows(
        List<ServiceRequestListModel> procedureRows,
        Long operatorId) {
        for (ServiceRequestListModel entrust : listEntrustDraftsBasedOnProcedureRows(procedureRows)) {
            removeOneDraft(operatorId, entrust.getId());
        }
    }

    /**
     * 列出挂靠术式的术前嘱托草稿行。
     *
     * @param procedureRows 术式行
     * @return 嘱托 ListModel（仅 DRAFT）
     */
    private List<ServiceRequestListModel> listEntrustDraftsBasedOnProcedureRows(
        List<ServiceRequestListModel> procedureRows) {
        return filterEntrustBasedOnProcedureRows(procedureRows, RequestStatus.DRAFT.getCode());
    }

    /**
     * 列出挂靠术式的术前嘱托已签发行。
     *
     * @param procedureRows 术式行
     * @return 嘱托 ListModel（仅 ACTIVE）
     */
    private List<ServiceRequestListModel> listEntrustActiveBasedOnProcedureRows(
        List<ServiceRequestListModel> procedureRows) {
        return filterEntrustBasedOnProcedureRows(procedureRows, RequestStatus.ACTIVE.getCode());
    }

    /**
     * 按状态过滤挂靠术式的术前嘱托。
     *
     * @param procedureRows 术式行
     * @param statusEnum    目标状态 code
     * @return 嘱托 ListModel
     */
    private List<ServiceRequestListModel> filterEntrustBasedOnProcedureRows(
        List<ServiceRequestListModel> procedureRows,
        String statusEnum) {
        List<ServiceRequestListModel> result = new ArrayList<>();
        Set<Long> procedureIds = new HashSet<>();
        for (ServiceRequestListModel row : procedureRows) {
            if (row != null && row.getId() != null) {
                procedureIds.add(row.getId());
            }
        }
        if (procedureIds.isEmpty()) {
            return result;
        }
        List<ServiceRequest> entities = serviceRequestQueryService.listServiceRequestsByBasedOnAnchors(
            TableNameConstants.WKF_SERVICE_REQUEST, procedureIds);
        for (ServiceRequest entrust : entities) {
            if (entrust == null || entrust.getId() == null) {
                continue;
            }
            if (!ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST.equals(
                trimToEmpty(entrust.getCategoryEnum()))) {
                continue;
            }
            if (!statusEnum.equals(trimToEmpty(entrust.getStatusEnum()))) {
                continue;
            }
            ServiceRequestListModel model = new ServiceRequestListModel();
            model.setId(entrust.getId());
            model.setStatusEnum(entrust.getStatusEnum());
            model.setCategoryEnum(entrust.getCategoryEnum());
            model.setBasedOnId(entrust.getBasedOnId());
            model.setBasedOnTable(entrust.getBasedOnTable());
            model.setEntrustContent(entrust.getEntrustContent());
            result.add(model);
        }
        return result;
    }

    /**
     * 麻醉关联手术：把麻醉 AR 写入手术 DRAFT 行的 nestedAnesthesiaPrescriptionNo（表单级关联）。
     *
     * @param encounterId                 就诊
     * @param linkedSurgeryPrescriptionNo 手术 AR
     * @param anesthesiaPrescriptionNo    麻醉 AR
     * @param operatorId                  操作人
     */
    private void stampNestedAnesthesiaOnSurgeryDrafts(
        Long encounterId,
        String linkedSurgeryPrescriptionNo,
        String anesthesiaPrescriptionNo,
        Long operatorId) {
        List<ServiceRequestListModel> surgeryRows = filterFormRowsByPrescription(
            encounterId,
            linkedSurgeryPrescriptionNo,
            ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        for (ServiceRequestListModel row : surgeryRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // 仅草稿可改嵌套号；已签发手术不在此改写
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            ModifyServiceRequestDraftCommand cmd = new ModifyServiceRequestDraftCommand();
            cmd.setOperatorId(operatorId);
            ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand item =
                new ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand();
            item.setId(row.getId());
            item.setNestedAnesthesiaPrescriptionNo(anesthesiaPrescriptionNo);
            cmd.setItem(item);
            serviceRequestDomainService.modifyServiceRequestDraft(cmd);
        }
    }

    /**
     * 麻醉详情并入同 AR 的药品医嘱行。
     *
     * @param detail         麻醉详情
     * @param prescriptionNo 麻醉 AR
     */
    private void appendAnesthesiaMedicationLines(
        GetAnesthesiaApplyFormDetailResponse detail,
        String prescriptionNo) {
        if (detail == null || isEmpty(prescriptionNo)) {
            return;
        }
        List<MedicationRequestListQueryModel> medRows =
            medicationRequestQueryService.listMedicationRequestListModelsByPrescriptionNos(
                List.of(prescriptionNo.trim()));
        if (medRows.isEmpty()) {
            return;
        }
        Set<Long> medicationIds = new HashSet<>();
        for (MedicationRequestListQueryModel med : medRows) {
            if (med != null && med.getMedicationId() != null) {
                medicationIds.add(med.getMedicationId());
            }
        }
        Map<Long, String> nameByMedicationId = new HashMap<>();
        for (MedicationDefinition def : medicationDefinitionQueryService.findAllByIds(medicationIds)) {
            if (def != null && def.getId() != null) {
                nameByMedicationId.put(def.getId(), def.getName());
            }
        }
        List<AnesthesiaApplyFormLineDto> lines = detail.getLines();
        if (lines == null) {
            lines = new ArrayList<>();
            detail.setLines(lines);
        }
        for (MedicationRequestListQueryModel med : medRows) {
            if (med == null || med.getId() == null) {
                continue;
            }
            AnesthesiaApplyFormLineDto dto = new AnesthesiaApplyFormLineDto();
            dto.setLineKindCode(SurgeryAnesthApplyConstants.LINE_KIND_MEDICATION);
            dto.setMedicationRequestId(med.getId());
            dto.setMedicationDefinitionId(med.getMedicationId());
            dto.setMedicationName(nameByMedicationId.get(med.getMedicationId()));
            dto.setQuantity(med.getQuantity());
            dto.setUnitCode(med.getUnitCode());
            dto.setRateCode(med.getRateCode());
            dto.setMethodCode(med.getMethodCode());
            dto.setDose(med.getDose());
            dto.setDoseUnitCode(med.getDoseUnitCode());
            dto.setStatusEnum(med.getStatusEnum());
            dto.setSortNumber(med.getSortNumber());
            lines.add(dto);
        }
    }

    /**
     * 麻醉明细单位码转展示名：总量单位认 {@code unit_code}，单次剂量单位认 {@code dose_unit}。
     *
     * @param detail 麻醉详情
     */
    private void enrichAnesthesiaLineUnitNames(GetAnesthesiaApplyFormDetailResponse detail) {
        if (detail == null || detail.getLines() == null || detail.getLines().isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = buildTerminologyLabelMap(
            TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> doseUnitLabelByCode = buildTerminologyLabelMap(
            TerminologyCodeSystemNameConstants.DOSE_UNIT);
        for (AnesthesiaApplyFormLineDto line : detail.getLines()) {
            if (line == null) {
                continue;
            }
            String unitCode = trimToNull(line.getUnitCode());
            if (unitCode != null) {
                String label = unitLabelByCode.get(unitCode);
                if (label != null) {
                    line.setUnitName(label);
                }
            }
            String doseUnitCode = trimToNull(line.getDoseUnitCode());
            if (doseUnitCode != null) {
                // 剂量单位优先 dose_unit；历史误写 unit_code 时回落计量码表文案
                String doseLabel = doseUnitLabelByCode.get(doseUnitCode);
                if (doseLabel == null) {
                    doseLabel = unitLabelByCode.get(doseUnitCode);
                }
                if (doseLabel != null) {
                    line.setDoseUnitName(doseLabel);
                }
            }
        }
    }

    /**
     * 术语码系 OptionDto → code→展示名（空码跳过）。
     *
     * @param codeSystemName 术语体系 name
     * @return 可变 map；无选项时为空 map
     */
    private Map<String, String> buildTerminologyLabelMap(String codeSystemName) {
        Map<String, String> labelByCode = new HashMap<>();
        List<OptionDto> options = codeSystemQueryService.getOptionListByCodeSystemName(codeSystemName);
        if (options == null) {
            return labelByCode;
        }
        for (OptionDto opt : options) {
            if (opt == null || isEmpty(opt.getValue())) {
                continue;
            }
            labelByCode.put(opt.getValue().trim(), opt.getLabel());
        }
        return labelByCode;
    }

    /**
     * 签发单条服务申请草稿。
     *
     * @param operatorId 操作人
     * @param serviceRequestId SR id
     * @param signTime   签发时间
     */
    private void signOneServiceRequest(Long operatorId, Long serviceRequestId, LocalDateTime signTime) {
        SignServiceRequestCommand command = new SignServiceRequestCommand();
        command.setOperatorId(operatorId);
        SignServiceRequestCommand.SignServiceRequestItemCommand item =
            new SignServiceRequestCommand.SignServiceRequestItemCommand();
        item.setId(serviceRequestId);
        item.setSignerId(operatorId);
        item.setSignTime(signTime);
        command.setItem(item);
        serviceRequestDomainService.signServiceRequest(command);
    }

    /**
     * 签发麻醉申请包：麻醉 SR 草稿 + 同 AR 药品草稿。
     *
     * <p>供手术签发连带嵌套麻醉，或独立麻醉签发复用药品签发。</p>
     *
     * @param encounterId    就诊
     * @param anesthPrescriptionNo 麻醉 AR
     * @param operatorId     操作人
     * @param signTime       签发时间
     * @return 已签发 id（SR + MR）
     */
    private List<Long> signAnesthesiaBundle(
        Long encounterId,
        String anesthPrescriptionNo,
        Long operatorId,
        LocalDateTime signTime) {
        List<Long> signedIds = new ArrayList<>();
        List<ServiceRequestListModel> anesthRows = filterFormRowsByPrescription(
            encounterId,
            anesthPrescriptionNo,
            ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        // 仅签发仍为草稿的麻醉行；已 ACTIVE 幂等跳过，不阻断主手术签发
        for (ServiceRequestListModel row : anesthRows) {
            // 脏数据跳过
            if (row == null || row.getId() == null) {
                continue;
            }
            // 非草稿不签发（幂等）
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            signOneServiceRequest(operatorId, row.getId(), signTime);
            signedIds.add(row.getId());
        }
        // 同单号药品（含仅开药、无 SR）
        signedIds.addAll(signAnesthesiaMedicationDrafts(anesthPrescriptionNo, operatorId));
        return signedIds;
    }

    /**
     * 签发同麻醉 AR 下的药品草稿（保持 prescriptionNo=麻醉 AR）。
     *
     * @param anesthPrescriptionNo 麻醉 AR
     * @param operatorId           操作人
     * @return 已签发药品 id
     */
    private List<Long> signAnesthesiaMedicationDrafts(String anesthPrescriptionNo, Long operatorId) {
        List<Long> signedIds = new ArrayList<>();
        // AR 空则无药品可签
        if (isEmpty(anesthPrescriptionNo)) {
            return signedIds;
        }
        List<MedicationRequestListQueryModel> medRows =
            medicationRequestQueryService.listMedicationRequestListModelsByPrescriptionNos(
                List.of(anesthPrescriptionNo.trim()));
        for (MedicationRequestListQueryModel med : medRows) {
            // 脏数据跳过
            if (med == null || med.getId() == null) {
                continue;
            }
            // 仅签发草稿药品；已 ACTIVE 的跳过（幂等）
            if (!RequestStatus.DRAFT.getCode().equals(trimToEmpty(med.getStatusEnum()))) {
                continue;
            }
            SignMedicationOrderCommand cmd = new SignMedicationOrderCommand();
            cmd.setId(med.getId());
            cmd.setOperatorId(operatorId);
            // 保持与麻醉 SR 同 AR，便于详情/撤回按方聚合
            cmd.setPrescriptionNo(anesthPrescriptionNo.trim());
            medicationRequestDomainService.signMedicationOrder(cmd);
            signedIds.add(med.getId());
        }
        return signedIds;
    }

    /**
     * 撤回嵌套麻醉申请包：麻醉 ACTIVE SR + 同 AR 药品签退。
     *
     * @param encounterId          就诊
     * @param anesthPrescriptionNo 麻醉 AR
     * @param operatorId           操作人
     * @return 已撤回/签退 id
     */
    private List<Long> withdrawAnesthesiaBundle(
        Long encounterId,
        String anesthPrescriptionNo,
        Long operatorId) {
        List<Long> withdrawnIds = new ArrayList<>();
        List<ServiceRequestListModel> anesthRows = filterFormRowsByPrescription(
            encounterId,
            anesthPrescriptionNo,
            ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        for (ServiceRequestListModel row : anesthRows) {
            // 脏数据跳过
            if (row == null || row.getId() == null) {
                continue;
            }
            // 非 ACTIVE 不撤回（幂等）
            if (!RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            assertWithdrawable(row.getId());
            withdrawOneDraft(operatorId, row.getId());
            withdrawnIds.add(row.getId());
        }
        withdrawnIds.addAll(signOffAnesthesiaMedications(anesthPrescriptionNo, operatorId));
        return withdrawnIds;
    }

    /**
     * 同麻醉 AR 药品 ACTIVE → 签退回草稿。
     *
     * @param anesthPrescriptionNo 麻醉 AR
     * @param operatorId           操作人
     * @return 已签退药品 id
     */
    private List<Long> signOffAnesthesiaMedications(String anesthPrescriptionNo, Long operatorId) {
        List<Long> ids = new ArrayList<>();
        // AR 空则无药品可签退
        if (isEmpty(anesthPrescriptionNo)) {
            return ids;
        }
        List<MedicationRequestListQueryModel> medRows =
            medicationRequestQueryService.listMedicationRequestListModelsByPrescriptionNos(
                List.of(anesthPrescriptionNo.trim()));
        for (MedicationRequestListQueryModel med : medRows) {
            // 脏数据跳过
            if (med == null || med.getId() == null) {
                continue;
            }
            // 非 ACTIVE 不签退（幂等）
            if (!RequestStatus.ACTIVE.getCode().equals(trimToEmpty(med.getStatusEnum()))) {
                continue;
            }
            SignOffMedicationOrderCommand cmd = new SignOffMedicationOrderCommand();
            cmd.setId(med.getId());
            cmd.setOperatorId(operatorId);
            medicationRequestDomainService.signOffMedicationOrder(cmd);
            ids.add(med.getId());
        }
        return ids;
    }

    /**
     * 软删同麻醉单号下的药品草稿。
     *
     * @param prescriptionNo 麻醉申请单号（AA / 存量 AR）
     * @param operatorId     操作人
     */
    private void removeAnesthesiaMedicationDraftsByPrescriptionNo(String prescriptionNo, Long operatorId) {
        if (isEmpty(prescriptionNo)) {
            return;
        }
        List<MedicationRequestListQueryModel> medRows =
            this.listAnesthesiaMedicationsByPrescriptionNo(prescriptionNo);
        for (MedicationRequestListQueryModel med : medRows) {
            if (med == null || med.getId() == null) {
                continue;
            }
            // 仅删草稿；已签发须走撤回，不在改存删旧路径处理
            if (!RequestStatus.DRAFT.getCode().equals(trimToEmpty(med.getStatusEnum()))) {
                continue;
            }
            RemoveMedicationOrderDraftCommand cmd = new RemoveMedicationOrderDraftCommand();
            cmd.setId(med.getId());
            cmd.setOperatorId(operatorId);
            medicationRequestDomainService.removeMedicationOrderDraft(cmd);
        }
    }

    /**
     * 按单号查询麻醉药品医嘱（含仅开药、无 SR）。
     *
     * @param prescriptionNo 麻醉申请单号
     * @return 药嘱列表；单号空则空列表
     */
    private List<MedicationRequestListQueryModel> listAnesthesiaMedicationsByPrescriptionNo(String prescriptionNo) {
        if (isEmpty(prescriptionNo)) {
            return List.of();
        }
        return medicationRequestQueryService.listMedicationRequestListModelsByPrescriptionNos(
            List.of(prescriptionNo.trim()));
    }

    /**
     * 同单号是否存在麻醉药品草稿。
     *
     * @param prescriptionNo 麻醉申请单号
     * @return true=存在 DRAFT 药品
     */
    private boolean hasAnesthesiaMedicationDrafts(String prescriptionNo) {
        for (MedicationRequestListQueryModel med : this.listAnesthesiaMedicationsByPrescriptionNo(prescriptionNo)) {
            if (med != null && RequestStatus.DRAFT.getCode().equals(trimToEmpty(med.getStatusEnum()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 列表并入仅开药（无类目 25 SR）的 AA 麻醉单头。
     *
     * @param encounterId 就诊
     * @param forms       已有 SR 头
     * @param linkedMap   麻醉单号 → 手术 AR
     * @return 合并后的头列表
     */
    private List<AnesthesiaApplyFormHeaderDto> mergeMedicationOnlyAnesthesiaFormHeaders(
        Long encounterId,
        List<AnesthesiaApplyFormHeaderDto> forms,
        Map<String, String> linkedMap) {
        List<AnesthesiaApplyFormHeaderDto> result = new ArrayList<>();
        if (forms != null) {
            result.addAll(forms);
        }
        Set<String> existingNos = new HashSet<>();
        for (AnesthesiaApplyFormHeaderDto header : result) {
            if (header != null && isNotEmpty(header.getPrescriptionNo())) {
                existingNos.add(header.getPrescriptionNo().trim());
            }
        }
        // Pre：就诊下医生开立药嘱，筛 AA 前缀且尚未出现在 SR 头中的单号
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        List<MedicationRequestListQueryModel> medRows =
            medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                encounterId, doctorSource);
        Map<String, List<MedicationRequestListQueryModel>> medByAaNo = new HashMap<>();
        for (MedicationRequestListQueryModel med : medRows) {
            if (med == null) {
                continue;
            }
            String no = trimToNull(med.getPrescriptionNo());
            if (!ServiceApplyFormOrchestrationSupport.isAnesthesiaApplicationPrescriptionNo(no)) {
                continue;
            }
            if (existingNos.contains(no)) {
                continue;
            }
            List<MedicationRequestListQueryModel> group = medByAaNo.get(no);
            if (group == null) {
                group = new ArrayList<>();
                medByAaNo.put(no, group);
            }
            group.add(med);
        }
        for (Map.Entry<String, List<MedicationRequestListQueryModel>> entry : medByAaNo.entrySet()) {
            AnesthesiaApplyFormHeaderDto header = new AnesthesiaApplyFormHeaderDto();
            header.setPrescriptionNo(entry.getKey());
            header.setLineCount(entry.getValue().size());
            header.setStatusEnum(this.aggregateMedicationHeaderStatus(entry.getValue()));
            if (linkedMap != null) {
                header.setLinkedSurgeryPrescriptionNo(linkedMap.get(entry.getKey()));
            }
            MedicationRequestListQueryModel first = entry.getValue().get(0);
            if (first != null && first.getRequesterId() != null) {
                // 开单人名：与 SR 头一致走 enrich 成本过高时允许空，前端可再刷
                header.setRequesterName(null);
            }
            result.add(header);
        }
        return result;
    }

    /**
     * 纯药品麻醉单详情装配。
     *
     * @param encounterId    就诊
     * @param prescriptionNo 麻醉 AA 单号
     * @param medRows        同单号药品
     * @return 详情（仅药品行）
     */
    private GetAnesthesiaApplyFormDetailResponse buildMedicationOnlyAnesthesiaDetail(
        Long encounterId,
        String prescriptionNo,
        List<MedicationRequestListQueryModel> medRows) {
        GetAnesthesiaApplyFormDetailResponse detail = new GetAnesthesiaApplyFormDetailResponse();
        AnesthesiaApplyFormHeaderDto header = new AnesthesiaApplyFormHeaderDto();
        header.setPrescriptionNo(prescriptionNo);
        header.setLineCount(medRows.size());
        header.setStatusEnum(this.aggregateMedicationHeaderStatus(medRows));
        Map<String, String> linkedMap = buildLinkedSurgeryPrescriptionMap(encounterId, List.of());
        if (linkedMap != null) {
            header.setLinkedSurgeryPrescriptionNo(linkedMap.get(prescriptionNo));
        }
        detail.setHeader(header);
        detail.setLines(new ArrayList<>());
        appendAnesthesiaMedicationLines(detail, prescriptionNo);
        return detail;
    }

    /**
     * 聚合药品行状态（同 SR 头口径：一致取该态，否则 MIXED）。
     *
     * @param medRows 药品行
     * @return 状态 code 或 MIXED
     */
    private String aggregateMedicationHeaderStatus(List<MedicationRequestListQueryModel> medRows) {
        String first = null;
        for (MedicationRequestListQueryModel med : medRows) {
            if (med == null) {
                continue;
            }
            String status = trimToEmpty(med.getStatusEnum());
            if (status.isEmpty()) {
                continue;
            }
            if (first == null) {
                first = status;
            } else if (!first.equals(status)) {
                return "MIXED";
            }
        }
        return first;
    }
}
