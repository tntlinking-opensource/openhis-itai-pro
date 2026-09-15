/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.shared.product.ProductTableNameHelper;
import com.openhis.health.application.outpatient.doctorstation.advice.support.ServiceApplyFormOrchestrator;
import com.openhis.health.application.shared.option.ContractFeeNatureOptionLoader;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.inpatient.InpatientTransferDischargeHoldOrchestrator;
import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceListAssembler;
import com.openhis.health.application.shared.advice.AdviceSignTargetSpec;
import com.openhis.health.application.shared.advice.SkinTestMedicationDraftSpec;
import com.openhis.health.infrastructure.integration.noop.IntegrationAfterCommitSupport;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.application.shared.advice.DoctorStationSkinTestSyncOrchestrator;
import com.openhis.health.application.shared.advice.DoctorStationSkinTestSyncOrchestrator.ChargePolicy;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.ServiceApplyFormAssembler;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.CancelStopLongTermAdviceLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceResourceKeyDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.SaveInpatientMedicationAdviceDraftLinePayload;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.StopLongTermAdviceLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftBindItemDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import com.openhis.health.application.inpatient.doctorstation.advice.mapper.InpatientDoctorStationAdviceMapper;
import com.openhis.health.application.shared.advice.InpatientAdviceRevokeGate;
import com.openhis.health.application.shared.advice.InpatientEntrustOrderGateSupport;
import com.openhis.health.application.shared.advice.InpatientMedicationAdviceGroupRateGate;
import com.openhis.health.application.shared.advice.InpatientNursingOrderGate;
import com.openhis.health.infrastructure.integration.lis.LisAdviceApplyExtension;
import com.openhis.health.infrastructure.integration.lis.ExtensionIn.LisAdviceApplyExtensionIn;
import com.openhis.health.infrastructure.integration.lis.dto.LisAdviceApplyDetailLine;
import com.openhis.health.infrastructure.integration.pacs.PacsAdviceApplyExtension;
import com.openhis.health.infrastructure.integration.pacs.ExtensionIn.PacsAdviceApplyExtensionIn;
import com.openhis.health.infrastructure.integration.pacs.dto.PacsAdviceApplyDetailLine;
import com.openhis.health.application.inpatient.doctorstation.advice.query.InpatientDoctorStationAdviceOrderBindQueryService;
import com.openhis.health.application.inpatient.doctorstation.advice.request.AppendSkinTestCompanionForLongTermMedicationRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.CancelStopLongTermInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ChangeInpatientDoctorStationAdviceBusinessTimeRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ChangeLongTermMedicationDoseInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.CreateDischargeInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.GetInpatientDoctorStationAdviceOrderBindInfoRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.InitInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ListActivityExecutionLocationsRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ListInpatientDoctorStationServiceApplyFormsRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.SaveInpatientDoctorStationServiceApplyFormRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.response.PageInpatientDoctorStationAdviceResponse;
import com.openhis.health.domain.share.constants.DoctorStationAdviceScopeTab;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ResolveInpatientDoctorStationAdviceDraftBindsRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.SaveInpatientDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.SignInpatientDoctorStationAdviceBatchRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.StopLongTermInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.UpdateInpatientDoctorStationMedicationAdviceGroupsRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.WithdrawInpatientDoctorStationAdviceBatchRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.response.AppendSkinTestCompanionForLongTermMedicationResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ActivityExecutionLocationRuleRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.response.CancelStopLongTermInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ChangeInpatientDoctorStationAdviceBusinessTimeResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ChangeLongTermMedicationDoseInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.CreateDischargeInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.GetInpatientDoctorStationAdviceOrderBindInfoResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.InitInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ListActivityExecutionLocationsResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ListInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ListInpatientDoctorStationServiceApplyFormsResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.SaveInpatientDoctorStationServiceApplyFormResponse;
import com.openhis.health.domain.administration.specimendefinition.queries.SearchSpecimenDefinitionQuery;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionSearchItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.service.SpecimenDefinitionQueryService;
import com.openhis.health.domain.diagnosis.bodystructure.service.BodyStructureQueryService;
import com.openhis.health.application.shared.option.BodyStructureOptionAssembler;
import com.openhis.health.application.shared.option.OptionDtoAssembler;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ListInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.PageInpatientDoctorStationAdviceCatalogResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ResolveInpatientDoctorStationAdviceDraftBindsResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.SaveInpatientDoctorStationAdviceDraftBatchResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.SignInpatientDoctorStationAdviceBatchResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.StopLongTermInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.UpdateInpatientDoctorStationMedicationAdviceGroupsResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.WithdrawInpatientDoctorStationAdviceBatchResponse;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationQueryService;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryDomainService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.shared.advice.InpatientOrderListMergeAssembler;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.support.InpatientDoctorStationEncounterVisibilityResolver;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.application.shared.product.DoctorStationCatalogPositiveStockLocationBinder;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestBusinessTimeKind;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.shared.advice.ServiceRequestDetailListAssembler;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医生站医嘱开立应用编排（AS-IP-DOR-003）。
 *
 * <p>草稿/签发：住院长临医嘱均仅开立/签发 Request，不在签发相位建发放与费用（门诊临嘱不变）。</p>
 * <p>停嘱/取消停嘱/撤回：长临均仅更新请求状态（不在签发相位建发放/费用）；取消停嘱见 IF-IP-DOR-25。</p>
 * <p>改开立/停嘱时间：IF-IP-DOR-26；禁止改签发；不改开立人/停嘱人。</p>
 * <p>长期皮试药换批追加皮试检查：IF-IP-DOR-27（based_on 锚皮试药，不挂费）。</p>
 * <p>目录与列表：可开立目录分页、医嘱统一列表与 LIS/PACS 申请对接。</p>
 */
@Service
@RequiredArgsConstructor
public class InpatientDoctorStationAdviceAppService {

    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final EncounterQueryService encounterQueryService;
    /** 医生站可见性 Gate（BR-IP-DOR-010）。 */
    private final InpatientDoctorStationEncounterVisibilityResolver inpatientDoctorStationEncounterVisibilityResolver;
    private final InpatientOrderListMergeAssembler inpatientOrderListMergeAssembler;
    private final InpatientDoctorStationAdviceMapper inpatientDoctorStationAdviceMapper;
    private final InpatientDoctorStationAdviceListAssembler inpatientDoctorStationAdviceListAssembler;
    private final InpatientDoctorStationAdviceCatalogAssembler inpatientDoctorStationAdviceCatalogAssembler;
    private final InpatientDoctorStationAdviceOrderBindQueryService inpatientDoctorStationAdviceOrderBindQueryService;
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;
    /** 医生站「只看库存>0」：模板库位下推至目录分页 Spec。 */
    private final DoctorStationCatalogPositiveStockLocationBinder doctorStationCatalogPositiveStockLocationBinder;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final InpatientTransferDischargeHoldOrchestrator transferDischargeHoldOrchestrationSupport;
    private final BusinessSequenceService businessSequenceService;
    private final LisAdviceApplyExtension lisAdviceApplyExtension;
    private final PacsAdviceApplyExtension pacsAdviceApplyExtension;
    private final CodeSystemQueryService codeSystemQueryService;
    /** 用药频次下拉：读 cus_frequency 启用态。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 费用性质（合同）下拉：按就诊挂号分类收窄。 */
    private final ContractFeeNatureOptionLoader contractFeeNatureOptionLoader;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestDetailListAssembler serviceRequestDetailListAssembler;
    private final ActivityExecutionLocationQueryService activityExecutionLocationQueryService;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final EncounterHistoryDomainService encounterHistoryDomainService;
    private final ServiceApplyFormOrchestrator serviceApplyFormOrchestrator;
    private final ServiceApplyFormAssembler serviceApplyFormAssembler;
    private final BodyStructureQueryService bodyStructureQueryService;
    private final SpecimenDefinitionQueryService specimenDefinitionQueryService;
    /** BR-IP-ORD-024：皮试药联动 doctor-prescription 皮试检查 SR（住院不挂费）。 */
    private final DoctorStationSkinTestSyncOrchestrator doctorStationSkinTestSyncOrchestrator;
    /** IF-IP-DOR-16：签退前执行/发药/费用门控（EX-IP-ORD-103）。 */
    private final InpatientAdviceRevokeGate adviceRevokeGate;
    /** BR-IP-ORD-026：护理级别仅长期、同就诊唯一有效。 */
    private final InpatientNursingOrderGate inpatientNursingOrderGate;
    /** BR-IP-ORD-028/029：嘱托正文必填；出院带药禁止诊疗行。 */
    private final InpatientEntrustOrderGateSupport inpatientEntrustOrderGateSupport;
    /** BR-IP-ORD-033：同组成组药品频次须一致。 */
    private final InpatientMedicationAdviceGroupRateGate inpatientMedicationAdviceGroupRateGate;
    private final CaseRecordDomainService caseRecordDomainService;
    /**
     * IF-IP-DOR-22：分页查询患者住院医嘱（单接口分 Tab：当前医嘱 / 历史医嘱；全类型统一列表）。
     *
     * <p>读口径与 IF-IP-DOR-10 一致（药品 + 诊疗）；是否按处方展示由前端处理。</p>
     *
     * @param request 就诊、患者、{@code adviceScopeTabCode}、可选长临/科室/状态过滤及分页
     * @return 医嘱统一列表行分页
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public PageInpatientDoctorStationAdviceResponse pageInpatientDoctorStationAdvice(
        PageInpatientDoctorStationAdviceRequest request) {
        // Gate：与列表/目录一致，先校验住院快照与患者卡片一致，再加载医嘱读模型。
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        Long currentUserId = currentPractitionerResolver.findOperatorIdOrNull();
        List<InpatientDoctorStationAdviceRowDto> allRows;
        // 分支：HISTORY 走患者维度跨就诊；其余（含 null）默认当前就诊全类型医嘱。
        if (DoctorStationAdviceScopeTab.HISTORY.equals(request.getAdviceScopeTabCode())) {
            // Pre：历史 Tab 按患者跨就诊拉全类型医嘱，排除当前工作站就诊。
            LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
            // Pre：药/诊两类请求并集收集历史就诊 id（住院医嘱列表不含耗材）。
            encounterIds.addAll(medicationRequestQueryService.listDistinctEncounterIdsByPatientForDoctorStation(
                request.getPatientId(), request.getEncounterId()));
            encounterIds.addAll(serviceRequestQueryService.listDistinctEncounterIdsByPatientForDoctorStation(
                request.getPatientId(), request.getEncounterId()));
            allRows = new ArrayList<>();
            // Loop：逐历史就诊 merge 骨架后再装配，继承长临/科室/状态过滤。
            for (Long historyEncounterId : encounterIds) {
                List<InpatientOrderListSkeleton> orders = inpatientOrderListMergeAssembler.mergeDoctorStationOrders(
                    historyEncounterId, request.getTherapyTypeCode());
                allRows.addAll(inpatientDoctorStationAdviceListAssembler.assembleAdviceQueryRows(
                    historyEncounterId,
                    request.getPatientId(),
                    orders,
                    currentUserId,
                    request.getOrderingDepartmentId(),
                    request.getRequestStatusEnumIn(),
                    false));
            }
            // Asm：跨就诊结果按开立时间（reqOpenedTime，空则 reqAuthoredTime）倒序，同时间再按就诊倒序。
            allRows.sort(Comparator
                .comparing((InpatientDoctorStationAdviceRowDto row) -> {
                    // 历史 Tab 排序主轴：开立时刻优先，空回落签发追溯列。
                    if (row.getReqOpenedTime() != null) {
                        return row.getReqOpenedTime();
                    }
                    return row.getReqAuthoredTime();
                }, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(InpatientDoctorStationAdviceRowDto::getEncounterId, Comparator.nullsLast(Comparator.reverseOrder())));
        } else {
            // Pre：当前 Tab 仅装配当前就诊全类型医嘱。
            List<InpatientOrderListSkeleton> orders = inpatientOrderListMergeAssembler.mergeDoctorStationOrders(
                request.getEncounterId(), request.getTherapyTypeCode());
            allRows = inpatientDoctorStationAdviceListAssembler.assembleAdviceQueryRows(
                request.getEncounterId(),
                request.getPatientId(),
                orders,
                currentUserId,
                request.getOrderingDepartmentId(),
                request.getRequestStatusEnumIn(),
                false);
        }
        // Asm：全量行内存分页，与 IF-IP-DOR-10 行 DTO 同构下发。
        return PagedResultBuilder.buildFromFullList(
            new PageInpatientDoctorStationAdviceResponse(),
            allRows,
            request.getPageNo(),
            request.getBoundedPageSize());
    }

    /**
     * 医嘱开立页初始化：频次、给药途径、输液滴速与费用性质（合同）下拉。
     *
     * @param request 就诊 id、患者 id（Gate 用）
     * @return 频次、给药途径、滴速与费用性质 {@code OptionDto} 列表
     * @throws BusinessException 快照缺失或患者与读模型不一致
     */
    public InitInpatientDoctorStationAdviceResponse initInpatientDoctorStationAdvice(InitInpatientDoctorStationAdviceRequest request) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        InitInpatientDoctorStationAdviceResponse resp = new InitInpatientDoctorStationAdviceResponse();
        resp.setMedicationRateOptions(frequencyOptionLoader.loadActiveRateOptions());
        resp.setMedicationRouteOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.METHOD_CODE));
        // Pre：输液滴速术语（与列表 speedName 同源 code_system=speed_code）
        resp.setMedicationSpeedOptions(codeSystemQueryService.getOptionListByCodeSystemName(
            DoctorStationAdviceConstants.TERMINOLOGY_SPEED_CODE_SYSTEM_NAME));
        resp.setFeeNatureOptions(
            contractFeeNatureOptionLoader.loadOptionsForEncounter(request.getPatientId(), request.getEncounterId()));
        // Pre：读侧树模型按 busNo 已排序，再过滤为启用态供新申请引用
        resp.setBodyStructureOptions(
            BodyStructureOptionAssembler.toActiveOptionTree(bodyStructureQueryService.getBodyStructureTree()));
        resp.setSpecimenDefinitionOptions(loadSpecimenDefinitionOptions());
        return resp;
    }

    /**
     *
     * <p>只读接口：按活动定义 id 拉取全院执行科室/机构规则，并批量回填展示名称；
     * 不含库存 Gate，供开立诊疗前选择执行地点。</p>
     *
     * @param request 就诊上下文与 {@code activityDefinitionId}
     * @return 可执行科室规则行列表
     * @throws BusinessException 快照或患者门控失败
     */
    public ListActivityExecutionLocationsResponse listActivityExecutionLocations(ListActivityExecutionLocationsRequest request) {
        // Gate：住院就诊可见性与患者一致
        InpatientEncounterContextQueryModel snapshot =
            requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        // Pre：解析诊疗大类，并按开立科室（就诊科室）拉取项目级 + 大类默认可执行科室规则
        ProductDefinitionDisplayDto activityProduct =
            loadSingleProduct(TableNameConstants.WKF_ACTIVITY_DEFINITION, request.getActivityDefinitionId());
        List<ExecutionRuleListQueryModel> ruleModels =
            activityExecutionLocationQueryService.listExecutionRulesForActivityResolution(
                snapshot.getOrganizationId(),
                request.getActivityDefinitionId(),
                activityProduct.getCategoryCode());
        Set<Long> orgIds = new HashSet<>();
        Set<Long> locIds = new HashSet<>();
        for (ExecutionRuleListQueryModel row : ruleModels) {
            if (row.getExecutionOrgId() != null) {
                orgIds.add(row.getExecutionOrgId());
            }
            if (row.getExecutionLocationId() != null) {
                locIds.add(row.getExecutionLocationId());
            }
        }
        // Asm：批量解析执行科室/地点展示名并装配响应
        Map<Long, String> orgNameById = organizationQueryService.mapNamesByIds(orgIds);
        Map<Long, String> locNameById = locationQueryService.mapNamesByIds(locIds);
        List<ActivityExecutionLocationRuleRowDto> rows = new ArrayList<>();
        for (ExecutionRuleListQueryModel src : ruleModels) {
            ActivityExecutionLocationRuleRowDto line = new ActivityExecutionLocationRuleRowDto();
            line.setId(src.getId());
            line.setActivityDefinitionId(src.getActivityDefinitionId());
            line.setActivityCategoryCode(src.getActivityCategoryCode());
            line.setExecutionOrgId(src.getExecutionOrgId());
            if (src.getExecutionOrgId() != null) {
                line.setExecutionOrgName(orgNameById.get(src.getExecutionOrgId()));
            }
            line.setExecutionLocationId(src.getExecutionLocationId());
            if (src.getExecutionLocationId() != null) {
                line.setExecutionLocationName(locNameById.get(src.getExecutionLocationId()));
            }
            line.setStartTime(src.getStartTime());
            line.setEndTime(src.getEndTime());
            line.setPriority(src.getPriority());
            line.setDisplayOrder(src.getDisplayOrder());
            line.setRemark(src.getRemark());
            rows.add(line);
        }
        ListActivityExecutionLocationsResponse resp = new ListActivityExecutionLocationsResponse();
        resp.setRules(rows);
        return resp;
    }

    /**
     * IF-IP-DOR-10：加载住院医生站医嘱统一列表。
     *
     * <p>只读接口：以 OrderInfo 读模型（Q-ORD-007）为主数据源，叠加护士执行后费用归并、术语与发放库位展示；
     * 可按长临类型筛选；返回全量列表（非 DB 分页）。
     * 病案首页医嘱侧栏传 {@code includeTcmMedication=true} 纳入饮片，与西药/诊疗同一列表。</p>
     *
     * @param request 就诊 id、患者 id、可选 {@code therapyTypeCode} 筛选、可选纳入中药饮片
     * @return 医嘱统一列表行（{@code content} 承载行集合）
     * @throws BusinessException 快照、患者或列表 Gate 失败
     */
    public ListInpatientDoctorStationAdviceResponse listInpatientDoctorStationAdvice(ListInpatientDoctorStationAdviceRequest request) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        inpatientDoctorStationAdviceMapper.validateListRequest(request.getEncounterId(), request.getPatientId());
        Long currentUserId = currentPractitionerResolver.findOperatorIdOrNull();
        List<InpatientOrderListSkeleton> orders = inpatientOrderListMergeAssembler.mergeDoctorStationOrders(
            request.getEncounterId(), request.getTherapyTypeCode());
        // Pre：开立 Tab 默认排除饮片（中医 Tab 分域）；病案首页侧栏 includeTcmMedication=true 纳入同列表
        boolean excludeTcmMedication = !Boolean.TRUE.equals(request.getIncludeTcmMedication());
        List<InpatientDoctorStationAdviceRowDto> rows = inpatientDoctorStationAdviceListAssembler.assembleAdviceQueryRows(
            request.getEncounterId(),
            request.getPatientId(),
            orders,
            currentUserId,
            null,
            null,
            excludeTcmMedication);
        return PagedResultBuilder.buildList(new ListInpatientDoctorStationAdviceResponse(), rows);
    }

    /**
     *
     * <p>只读接口：在产品定义读模型上分页检索药品与诊疗（不含耗材），
     * 装配定价、库存展示等住院场景字段；不校验就诊可写态。</p>
     *
     * @param request 分页、关键字、目录筛选及就诊上下文
     * @return 可开立目录分页行（{@code pageNo}/{@code pageSize} 经 {@link PagedResultBuilder} 装配）
     * @throws BusinessException 快照或患者门控失败
     */
    public PageInpatientDoctorStationAdviceCatalogResponse pageInpatientDoctorStationAdviceCatalog(
        PageInpatientDoctorStationAdviceCatalogRequest request
    ) {
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        ProductDefinitionPageQuery pageQuery = inpatientDoctorStationAdviceMapper.toProductDefinitionPageQuery(request);
        // Pre：只看库存>0 时按取药模板库位收窄 EXISTS（与目录可开立量展示对齐）
        Long catalogOrgId = snapshot.getOrganizationId();
        if (request.getOrganizationUnitId() != null) {
            catalogOrgId = request.getOrganizationUnitId();
        }
        doctorStationCatalogPositiveStockLocationBinder.bindIfPositiveQuantityOnly(
            pageQuery, catalogOrgId, request.getPharmacyLocationId());
        Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
        List<InpatientDoctorStationAdviceCatalogRowDto> rows =
            inpatientDoctorStationAdviceCatalogAssembler.assemblePageContent(request, page, snapshot);
        return PagedResultBuilder.build(new PageInpatientDoctorStationAdviceCatalogResponse(), rows, page);
    }

    /**
     * IF-IP-DOR-23：保存检验/检查/治疗申请单（整张）；编辑时删旧 DRAFT 后取新 {@code AR} 号。
     *
     * @param request 就诊、患者、类别、整单执行科室、整单执行次数、整单诊断绑定（可选）与明细行
     * @return 新申请单号与新建 requestId 列表
     * @throws BusinessException 门控失败、旧号校验失败、定价缺失或域校验失败
     */
    public SaveInpatientDoctorStationServiceApplyFormResponse saveInpatientDoctorStationServiceApplyForm(
        SaveInpatientDoctorStationServiceApplyFormRequest request
    ) {
        // Gate：住院 IMP 门控 + 患者一致性。
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(
            request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：共享编排 saveApplyForm；住院不在暂存相位挂费用。
        ServiceApplyFormOrchestrator.SaveApplyFormResult saved = serviceApplyFormOrchestrator.saveApplyForm(
            request.getEncounterId(),
            request.getPatientId(),
            request.getCategoryEnum(),
            request.getOrgId(),
            request.getEncounterDiagnosisIds(),
            request.getPreviousPrescriptionNo(),
            serviceApplyFormAssembler.toLineSpecs(request.getLines()),
            request.getExecutionCount(),
            operatorId,
            null);
        // Asm：返回新号与 requestId 列表。
        SaveInpatientDoctorStationServiceApplyFormResponse resp = new SaveInpatientDoctorStationServiceApplyFormResponse();
        resp.setPrescriptionNo(saved.getPrescriptionNo());
        resp.setRequestIds(saved.getRequestIds());
        return resp;
    }

    /**
     * IF-IP-DOR-24：按类别查询当前就诊 {@code AR} 前缀申请单列表。
     *
     * @param request 就诊、患者与 categoryEnum
     * @return 按 prescriptionNo 分组的申请单头列表
     * @throws BusinessException 快照或患者门控失败
     */
    public ListInpatientDoctorStationServiceApplyFormsResponse listInpatientDoctorStationServiceApplyForms(
        ListInpatientDoctorStationServiceApplyFormsRequest request
    ) {
        // Gate：住院 IMP 门控。
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        // Pre + Asm：按 AR 前缀与 category 过滤后分组装配。
        ListInpatientDoctorStationServiceApplyFormsResponse resp = new ListInpatientDoctorStationServiceApplyFormsResponse();
        resp.setForms(serviceApplyFormAssembler.toHeaderDtos(
            serviceApplyFormOrchestrator.listApplyForms(
                request.getEncounterId(), request.getCategoryEnum())));
        return resp;
    }

    /**
     *
     * <p>只读接口：按绑定类型解析业务项号或主键，返回用法→物资或诊疗模板子项绑定行，
     * 供开立页联动展示；不写库。</p>
     *
     * @param request 绑定类型、项号/主键、机构单元 id
     * @return 绑定行列表
     * @throws BusinessException 项号无法解析或绑定类型非法
     */
    public GetInpatientDoctorStationAdviceOrderBindInfoResponse getInpatientDoctorStationAdviceOrderBindInfo(
        GetInpatientDoctorStationAdviceOrderBindInfoRequest request
    ) {
        String sourceItemKey = inpatientDoctorStationAdviceMapper.resolveOrderBindSourceItemKey(
            request.getBindType(), request.getItemNo(), request.getItemId());
        GetInpatientDoctorStationAdviceOrderBindInfoResponse resp = new GetInpatientDoctorStationAdviceOrderBindInfoResponse();
        resp.setBindRows(inpatientDoctorStationAdviceOrderBindQueryService.listBindingsBySourceItemKey(
            request.getBindType(), sourceItemKey, request.getOrganizationUnitId()));
        return resp;
    }

    /**
     * IF-IP-DOR-23：暂存前解析变更行中药品/诊疗的直接绑定项（纯读；住院不自动带耗材）。
     *
     * @param request 就诊、患者与暂存变更行
     * @return 绑定明细及触发源 based_on 锚点（不含器材定义目标）
     */
    public ResolveInpatientDoctorStationAdviceDraftBindsResponse resolveInpatientDoctorStationAdviceDraftBinds(
        ResolveInpatientDoctorStationAdviceDraftBindsRequest request
    ) {
        // Gate：住院就诊上下文与患者一致
        InpatientEncounterContextQueryModel snapshot =
            requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        // Pre：住院开立不带合管/耗材；同批仅保留非耗材模板绑定
        List<DoctorStationAdviceDraftBindItemDto> bindItems =
            inpatientDoctorStationAdviceOrderBindQueryService.listDraftBindItems(
                request.getLines(), snapshot.getOrganizationId());
        // Asm：返回绑定列表，派生行落库由前端在 IF-IP-DOR-14 前处理
        ResolveInpatientDoctorStationAdviceDraftBindsResponse resp =
            new ResolveInpatientDoctorStationAdviceDraftBindsResponse();
        resp.setBindItems(bindItems);
        return resp;
    }

    /**
     *
     * <p>写接口：按行操作码 CREATE/UPDATE/DELETE 分轨处理药品与诊疗草稿。
     * 长期/临时均仅维护 Request 草稿，不在暂存相位挂费用或发放（BR-IP-ORD-005/006）。
     * 单行失败则整批随事务回滚。</p>
     *
     * @param request 批次行列表与就诊上下文
     * @return 成功标记与本批新建药品请求 id
     * @throws BusinessException 可写态/操作人/行操作码等门控失败
     */
    public SaveInpatientDoctorStationAdviceDraftBatchResponse saveInpatientDoctorStationAdviceDraftBatch(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request
    ) {
        // Gate：住院 IMP 可写态 + 操作人
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        Long orgId = snapshot.getOrganizationId();
        // Gate：同 group_id 药品频次须一致（BR-IP-ORD-033），再落库避免主药 QD、溶媒 ST 异频并组
        this.assertMedicationDraftBatchGroupRates(request.getLines());
        List<Long> newMedIds = new ArrayList<>();
        // Cmd：按行 CREATE/UPDATE/DELETE 分轨，长临均仅维护 Request
        for (InpatientDoctorStationAdviceDraftLineRequest line : request.getLines()) {
            String op = trimToEmpty(line.getLineOp()).toUpperCase(java.util.Locale.ROOT);
            if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                handleCreateLine(request, line, operatorId, orgId, newMedIds);
            } else if (DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                handleUpdateLine(request, line, operatorId, orgId);
            } else if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                handleDeleteLine(request, line, operatorId);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_LINE_OP_PREFIX + line.getLineOp());
            }
        }
        // Asm：返回本批新建药品 requestId
        SaveInpatientDoctorStationAdviceDraftBatchResponse resp = new SaveInpatientDoctorStationAdviceDraftBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setNewMedicationRequestIds(newMedIds);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * IF-IP-DOR-15：批次签发药品/诊疗请求。
     *
     * <p>写接口：住院长临均仅 sign 请求（处方号/签署编码），不在签发相位建发放与费用；
     * FIFO 与 ORDER_EXECUTE 费用在护士执行相位落库（AS-IP-NUR-004）。</p>
     *
     * @param request 签发目标列表与就诊上下文
     * @return 药品处方号映射
     * @throws BusinessException 门控失败或产品表不支持
     */
    public SignInpatientDoctorStationAdviceBatchResponse signInpatientDoctorStationAdviceBatch(
        SignInpatientDoctorStationAdviceBatchRequest request
    ) {
        // Gate：住院 IMP 可写态 + 操作人
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        Map<Long, String> rxMap = new HashMap<>();
        // Pre（BR-IP-ORD-024）：签发皮试药时自动扩 companion 皮试检查 SR 至待签发集合。
        List<InpatientDoctorStationAdviceResourceKeyDto> signTargets =
            this.fromSignTargetSpecs(doctorStationSkinTestSyncOrchestrator
                .expandSignTargetsWithLinkedSkinTestServiceRequests(this.toSignTargetSpecs(request.getSignTargets())));
        // Cmd：长临均 sign 请求，不在签发相位建发放/费用
        for (InpatientDoctorStationAdviceResourceKeyDto target : signTargets) {
            String pt = ProductTableNameHelper.normalize(target.getProductTableName());
            if (ProductTableNameHelper.isMedication(pt)) {
                signMedicationTarget(request, target, operatorId, rxMap);
            } else if (ProductTableNameHelper.isActivity(pt)) {
                signServiceTarget(request, target, operatorId);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + target.getProductTableName());
            }
        }
        // Asm：返回处方号映射
        SignInpatientDoctorStationAdviceBatchResponse resp = new SignInpatientDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setPrescriptionNoByMedicationRequestId(rxMap);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * IF-IP-DOR-16：批次签退已签发医嘱。
     *
     * <p>写接口：住院长临药品均仅 signOff 请求；诊疗 withdrawSigned（BR-IP-ORD-009）。</p>
     *
     * @param request 签退目标列表与就诊上下文
     * @return 成功标记
     * @throws BusinessException 可写态/操作人门控失败或产品表不支持
     */
    public WithdrawInpatientDoctorStationAdviceBatchResponse withdrawInpatientDoctorStationAdviceBatch(
        WithdrawInpatientDoctorStationAdviceBatchRequest request
    ) {
        // Gate：住院 IMP 可写态 + 操作人
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        List<Long> medicationRequestIds = new ArrayList<>();
        List<Long> serviceRequestIds = new ArrayList<>();
        for (InpatientDoctorStationAdviceResourceKeyDto target : request.getTargets()) {
            String pt = ProductTableNameHelper.normalize(target.getProductTableName());
            if (ProductTableNameHelper.isMedication(pt)) {
                medicationRequestIds.add(target.getRequestId());
            } else if (ProductTableNameHelper.isActivity(pt)) {
                serviceRequestIds.add(target.getRequestId());
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + target.getProductTableName());
            }
        }
        // Pre（BR-IP-ORD-024）：同批勾选皮试药时 companion 将联动签退，从显式诊疗目标中剔除，避免二次签退
        Set<Long> autoWithdrawCompanionIds =
            doctorStationSkinTestSyncOrchestrator.collectCompanionSkinTestServiceRequestIds(medicationRequestIds);
        if (!autoWithdrawCompanionIds.isEmpty() && !serviceRequestIds.isEmpty()) {
            List<Long> explicitServiceRequestIds = new ArrayList<>();
            for (Long serviceRequestId : serviceRequestIds) {
                if (serviceRequestId == null || autoWithdrawCompanionIds.contains(serviceRequestId)) {
                    continue;
                }
                explicitServiceRequestIds.add(serviceRequestId);
            }
            serviceRequestIds = explicitServiceRequestIds;
        }
        // Pre：签退门控（EX-IP-ORD-103）
        adviceRevokeGate.assertMedicationWithdrawAllowed(
            request.getEncounterId(),
            medicationRequestIds,
            DoctorStationAdviceConstants.EX_IP_ORD_103,
            DoctorStationAdviceConstants.MSG_INPATIENT_WITHDRAW_BLOCKED);
        adviceRevokeGate.assertServiceRequestWithdrawAllowed(
            request.getEncounterId(),
            serviceRequestIds,
            DoctorStationAdviceConstants.EX_IP_ORD_103,
            DoctorStationAdviceConstants.MSG_INPATIENT_WITHDRAW_BLOCKED);
        // Cmd：长临药品 signOff；诊疗 withdrawSigned
        LocalDateTime revertedAt = LocalDateTime.now();
        for (InpatientDoctorStationAdviceResourceKeyDto target : request.getTargets()) {
            String pt = ProductTableNameHelper.normalize(target.getProductTableName());
            if (ProductTableNameHelper.isMedication(pt)) {
                // 住院长临药品签退：仅 signOff 请求（不在签发相位建发放/费用）。
                medicationRequestDomainService.signOffMedicationOrder(
                    inpatientDoctorStationAdviceMapper.toSignOffMedicationOrderCommand(target.getRequestId(), operatorId));
                // Cmd（BR-IP-ORD-024）：签退皮试药时对称处理 companion 皮试检查 SR。
                doctorStationSkinTestSyncOrchestrator.withdrawLinkedSkinTestServiceRequestIfPresent(
                    ChargePolicy.INPATIENT, request.getEncounterId(), target.getRequestId(), operatorId);
            } else if (ProductTableNameHelper.isActivity(pt)) {
                // 已由皮试药联动签退的 companion：本批跳过显式签退
                if (autoWithdrawCompanionIds.contains(target.getRequestId())) {
                    continue;
                }
                serviceRequestDomainService.withdrawSignedServiceRequest(
                    inpatientDoctorStationAdviceMapper.toWithdrawServiceRequestCommand(target.getRequestId(), operatorId));
                transferDischargeHoldOrchestrationSupport.tryRevertHoldAfterServiceRequestReverted(
                    target.getRequestId(),
                    operatorId,
                    revertedAt,
                    ErrorCodeConstants.ENCOUNTER_INVALID_STATUS);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + target.getProductTableName());
            }
        }
        WithdrawInpatientDoctorStationAdviceBatchResponse resp = new WithdrawInpatientDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * IF-IP-DOR-17：更新药品医嘱组号（清组/赋组）。
     *
     * <p>写接口：支持先清组再赋组，两段调用域 {@code adjustMedicationOrderGroup}；
     * 仅作用于药品请求，不涉及诊疗或费用直接变更。赋组前须同组频次一致（BR-IP-ORD-033）。</p>
     *
     * @param request 待清组 id 列表、待赋组载荷与就诊上下文
     * @return 成功标记
     * @throws BusinessException 可写态或操作人门控失败；同组频次不一致 → EX-IP-ORD-308
     */
    public UpdateInpatientDoctorStationMedicationAdviceGroupsResponse updateInpatientDoctorStationMedicationAdviceGroups(
        UpdateInpatientDoctorStationMedicationAdviceGroupsRequest request
    ) {
        // Gate：住院可写态 + 操作人
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        // Cmd：清组（groupId 置空）
        if (request.getClearGroupIds() != null && !request.getClearGroupIds().isEmpty()) {
            medicationRequestDomainService.adjustMedicationOrderGroup(
                inpatientDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(request.getClearGroupIds(), null, operatorId));
        }
        // Cmd：赋组；先断言频次唯一再写 group_id
        if (request.getAssignGroup() != null && request.getAssignGroup().getMedicationRequestIds() != null) {
            // Gate：赋组前校验目标组与待并入行频次唯一（BR-IP-ORD-033）
            inpatientMedicationAdviceGroupRateGate.assertAssignGroupConsistent(
                request.getAssignGroup().getMedicationRequestIds(),
                request.getAssignGroup().getGroupId());
            medicationRequestDomainService.adjustMedicationOrderGroup(
                inpatientDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                    request.getAssignGroup().getMedicationRequestIds(), request.getAssignGroup().getGroupId(), operatorId));
        }
        // Asm：成功响应
        UpdateInpatientDoctorStationMedicationAdviceGroupsResponse resp = new UpdateInpatientDoctorStationMedicationAdviceGroupsResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     *
     * <p>写接口：仅允许长期药品/诊疗停嘱；临时医嘱须走 DELETE 或签退路径。
     * 按行分轨调用域 {@code stopLongTermMedicationOrder} / {@code stopServiceRequest}。</p>
     *
     * @param request 停嘱行列表（含产品表名与请求 id）及就诊上下文
     * @return 成功标记
     * @throws BusinessException 非长期医嘱、行列表为空或门控失败
     */
    public StopLongTermInpatientDoctorStationAdviceResponse stopLongTermInpatientDoctorStationAdvice(
        StopLongTermInpatientDoctorStationAdviceRequest request
    ) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_STOP_LONG_TERM_LINES_REQUIRED);
        }
        for (StopLongTermAdviceLineRequest line : request.getLines()) {
            String pt = ProductTableNameHelper.normalize(line.getProductTableName());
            if (ProductTableNameHelper.isMedication(pt)) {
                assertLongTermMedication(line.getRequestId());
                medicationRequestDomainService.stopLongTermMedicationOrder(
                    inpatientDoctorStationAdviceMapper.toStopLongTermMedicationOrderCommand(line, operatorId));
            } else if (ProductTableNameHelper.isActivity(pt)) {
                assertLongTermService(request.getEncounterId(), line.getRequestId());
                serviceRequestDomainService.stopServiceRequest(
                    inpatientDoctorStationAdviceMapper.toStopServiceRequestCommand(line, operatorId));
                transferDischargeHoldOrchestrationSupport.tryRevertHoldAfterServiceRequestReverted(
                    line.getRequestId(),
                    operatorId,
                    LocalDateTime.now(),
                    ErrorCodeConstants.ENCOUNTER_INVALID_STATUS);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
            }
        }
        StopLongTermInpatientDoctorStationAdviceResponse resp = new StopLongTermInpatientDoctorStationAdviceResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 取消停嘱（长期医嘱，IF-IP-DOR-25）。
     *
     * <p>写接口：仅允许对已 STOPPED 的长期药品/诊疗恢复为 VERIFIED 或 ACTIVE；
     * 同就诊存在转科/出院申请或待出院态时拒绝。</p>
     *
     * @param request 取消停嘱行列表（含产品表名与请求 id）及就诊上下文
     * @return 成功标记
     * @throws BusinessException 非长期、非 STOPPED、出院门禁或行列表为空
     */
    public CancelStopLongTermInpatientDoctorStationAdviceResponse cancelStopLongTermInpatientDoctorStationAdvice(
        CancelStopLongTermInpatientDoctorStationAdviceRequest request
    ) {
        // Gate：取消停嘱行不能为空。
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_CANCEL_STOP_LONG_TERM_LINES_REQUIRED);
        }
        // Pre：住院快照与可写门控。
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        // Pre：转科/出院申请或待出院态禁止取消停嘱。
        assertCancelStopDischargeGate(request.getEncounterId(), snapshot);
        // Pre：解析当前操作人，供领域命令写入审计字段。
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        // Cmd：逐行分轨取消停嘱，药/诊各自调用域写服务。
        for (CancelStopLongTermAdviceLineRequest line : request.getLines()) {
            String pt = ProductTableNameHelper.normalize(line.getProductTableName());
            if (ProductTableNameHelper.isMedication(pt)) {
                // Pre：药品须为长期且已停嘱。
                assertStoppedLongTermMedication(line.getRequestId());
                // Cmd：调用药品取消停嘱域写。
                medicationRequestDomainService.cancelStopLongTermMedicationOrder(
                    inpatientDoctorStationAdviceMapper.toCancelStopLongTermMedicationOrderCommand(line, operatorId));
            } else if (ProductTableNameHelper.isActivity(pt)) {
                // Pre：诊疗须为长期且已停嘱。
                assertStoppedLongTermService(request.getEncounterId(), line.getRequestId());
                // Cmd：调用诊疗取消停嘱域写。
                serviceRequestDomainService.cancelStopServiceRequest(
                    inpatientDoctorStationAdviceMapper.toCancelStopServiceRequestCommand(line, operatorId));
            } else {
                // Gate：产品表名不在药/诊白名单内，拒绝跨域误操作。
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
            }
        }
        // Asm：统一成功响应，与 IF-IP-DOR-20 停嘱对称。
        CancelStopLongTermInpatientDoctorStationAdviceResponse resp = new CancelStopLongTermInpatientDoctorStationAdviceResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 长期皮试药快捷追加 companion「皮试检查」临嘱（IF-IP-DOR-27 / BR-IP-ORD-024③）。
     *
     * <p>写接口：已有草稿 companion 则复用；否则新建，{@code based_on} 锚该长期药；住院不挂费。
     * 追加后须由医生签发（IF-IP-DOR-15 自动扩草稿 companion）。</p>
     *
     * @param request 就诊上下文与长期皮试药主键列表
     * @return 新建或复用的 companion 主键列表
     * @throws BusinessException 非长期、未勾皮试、已停嘱、跨就诊或门控失败
     */
    public AppendSkinTestCompanionForLongTermMedicationResponse appendSkinTestCompanionForLongTermMedication(
        AppendSkinTestCompanionForLongTermMedicationRequest request
    ) {
        // Gate：药品主键列表不能为空
        if (request.getMedicationRequestIds() == null || request.getMedicationRequestIds().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_APPEND_SKIN_TEST_MED_IDS_REQUIRED);
        }
        // Gate：住院可写门控（BR-IP-ORD-027）
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        // Pre：解析操作人，供 companion 创建审计字段
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        List<Long> serviceRequestIds = new ArrayList<>();
        // Cmd：逐药追加或复用草稿 companion（based_on→该长期药）
        for (Long medicationRequestId : request.getMedicationRequestIds()) {
            if (medicationRequestId == null) {
                continue;
            }
            MedicationRequest med = medicationRequestQueryService.findById(medicationRequestId);
            if (med == null) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_MEDICATION_UPDATE_REQUEST_ID_REQUIRED);
            }
            // Gate：须归属当前就诊，防止跨就诊追加
            if (!request.getEncounterId().equals(med.getEncounterId())) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_APPEND_SKIN_TEST_ENCOUNTER_MISMATCH);
            }
            // Gate：仅长期药
            if (!TherapyType.LONG_TERM.getCode().equals(med.getTherapyEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_APPEND_SKIN_TEST_LONG_TERM_ONLY);
            }
            // Gate：须已勾皮试
            if (!Boolean.TRUE.equals(med.getSkinTestFlag())) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_APPEND_SKIN_TEST_FLAG_REQUIRED);
            }
            // Gate：已停嘱不可追加
            if (RequestStatus.STOPPED.getCode().equals(med.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_APPEND_SKIN_TEST_STOPPED_FORBIDDEN);
            }
            SkinTestMedicationDraftSpec payload = new SkinTestMedicationDraftSpec()
                .setSkinTestFlag(Boolean.TRUE)
                .setContractId(med.getContractId())
                .setEncounterDiagnosisIds(med.getEncounterDiagnosisIds());
            Long companionId = doctorStationSkinTestSyncOrchestrator.appendCompanionSkinTestServiceRequest(
                request.getEncounterId(),
                request.getPatientId(),
                med.getOrgId(),
                operatorId,
                medicationRequestId,
                payload);
            if (companionId != null) {
                serviceRequestIds.add(companionId);
            }
        }
        // Asm：返回 companion 主键供前端签发/刷新
        AppendSkinTestCompanionForLongTermMedicationResponse resp =
            new AppendSkinTestCompanionForLongTermMedicationResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        resp.setServiceRequestIds(serviceRequestIds);
        return resp;
    }

    /**
     * 长期药品改量（IF-IP-DOR-28 / BR-IP-ORD-032）：停旧（若在用）+ 同药重开草稿 + {@code based_on} 链。
     *
     * <p>写接口：本 IF <strong>不签发</strong>；新嘱为 DRAFT，医生再走 IF-IP-DOR-15。
     * 口服一盒一点剩余续算依赖本链（BR-IP-EXE-038）。</p>
     *
     * @param request 就诊上下文、源嘱、可选停嘱时间与新嘱载荷
     * @return 新草稿 id 与 basedOn
     * @throws BusinessException 非长期、状态非法、换药、跨就诊或门控失败
     */
    public ChangeLongTermMedicationDoseInpatientDoctorStationAdviceResponse
        changeLongTermMedicationDoseInpatientDoctorStationAdvice(
            ChangeLongTermMedicationDoseInpatientDoctorStationAdviceRequest request
        ) {
        // Gate：源嘱与载荷不可空（Bean Validation 之外再兜底）
        if (request.getSourceMedicationRequestId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHANGE_DOSE_SOURCE_ID_REQUIRED);
        }
        if (request.getMedicationPayload() == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHANGE_DOSE_PAYLOAD_REQUIRED);
        }
        // Gate：住院可写门控（BR-IP-ORD-027）
        InpatientEncounterContextQueryModel snapshot =
            requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        // Pre：解析开嘱人与机构
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        Long orgId = snapshot.getOrganizationId();
        // Pre：加载源嘱
        MedicationRequest source =
            medicationRequestQueryService.findById(request.getSourceMedicationRequestId());
        if (source == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_MEDICATION_UPDATE_REQUEST_ID_REQUIRED);
        }
        // Gate：须同就诊
        if (!request.getEncounterId().equals(source.getEncounterId())) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHANGE_DOSE_ENCOUNTER_MISMATCH);
        }
        // Gate：仅长期药品
        if (!TherapyType.LONG_TERM.getCode().equals(source.getTherapyEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHANGE_DOSE_LONG_TERM_MED_ONLY);
        }
        // Gate：仅 VERIFIED 或 STOPPED
        boolean verified = RequestStatus.VERIFIED.getCode().equals(source.getStatusEnum());
        boolean stopped = RequestStatus.STOPPED.getCode().equals(source.getStatusEnum());
        if (!verified && !stopped) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHANGE_DOSE_STATUS_INVALID);
        }
        // Gate：禁止换药（载荷若传定义须与源一致；未传则锁定源药）
        Long payloadMedId = request.getMedicationPayload().getMedicationDefinitionId();
        if (payloadMedId != null && !payloadMedId.equals(source.getMedicationId())) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHANGE_DOSE_MEDICATION_MISMATCH);
        }
        // Cmd：在用则同事务停嘱
        if (verified) {
            medicationRequestDomainService.stopLongTermMedicationOrder(
                inpatientDoctorStationAdviceMapper.toStopLongTermMedicationOrderCommandForDoseChange(
                    request, operatorId));
        }
        // Cmd：建长期草稿并 based_on→源
        Long newId = medicationRequestDomainService.createMedicationOrderDraft(
            inpatientDoctorStationAdviceMapper.toCreateMedicationOrderDraftCommandForDoseChange(
                request, source, operatorId, orgId));
        // Cmd：皮试标志同步 companion（与暂存 CREATE 同口径；住院不挂费）
        doctorStationSkinTestSyncOrchestrator.syncAfterMedicationDraftLine(
            ChargePolicy.INPATIENT,
            request.getEncounterId(),
            request.getPatientId(),
            null,
            orgId,
            operatorId,
            newId,
            this.toSkinTestSpec(request.getMedicationPayload()));
        // Asm：返回新草稿供前端刷新后签发
        ChangeLongTermMedicationDoseInpatientDoctorStationAdviceResponse resp =
            new ChangeLongTermMedicationDoseInpatientDoctorStationAdviceResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        resp.setNewMedicationRequestId(newId);
        resp.setSourceMedicationRequestId(source.getId());
        resp.setBasedOnId(source.getId());
        return resp;
    }

    /**
     * 改住院医嘱开立时间或停嘱时间（IF-IP-DOR-26 / BR-IP-ORD-031）。
     *
     * <p>写接口：一次请求仅改开立或停嘱之一；禁止改签发；改开立不改开立人、改停嘱不改停嘱人。</p>
     *
     * @param request 就诊上下文、药/诊主键、时间种类与新时间
     * @return 成功标记
     * @throws BusinessException 就诊不可写、时间种类非法、非已停嘱改停嘱或产品表不支持
     */
    public ChangeInpatientDoctorStationAdviceBusinessTimeResponse changeInpatientDoctorStationAdviceBusinessTime(
        ChangeInpatientDoctorStationAdviceBusinessTimeRequest request
    ) {
        // Gate：就诊可写门控（BR-IP-ORD-027 / 结算轨 EX-IP-ORD-107）。
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        // Gate：时间种类仅 OPENED/STOP，禁止隐含改签发。
        RequestBusinessTimeKind timeKind = RequestBusinessTimeKind.fromCodeOrNull(request.getTimeKind());
        if (timeKind == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHANGE_BUSINESS_TIME_KIND_INVALID);
        }
        // Pre：解析操作人，供领域命令审计字段（不落「改时人」列）。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        String pt = ProductTableNameHelper.normalize(request.getProductTableName());
        if (ProductTableNameHelper.isMedication(pt)) {
            // Pre：改停嘱时间须长期且已 STOPPED（EX-IP-ORD-307）。
            if (RequestBusinessTimeKind.STOP.equals(timeKind)) {
                assertChangeStopTimeMedication(request.getRequestId());
            }
            // Cmd：药品仅写开立或停嘱列。
            medicationRequestDomainService.changeMedicationRequestBusinessTime(
                inpatientDoctorStationAdviceMapper.toChangeMedicationRequestBusinessTimeCommand(request, operatorId));
        } else if (ProductTableNameHelper.isActivity(pt)) {
            // Pre：改停嘱时间须长期且已 STOPPED（EX-IP-ORD-307）。
            if (RequestBusinessTimeKind.STOP.equals(timeKind)) {
                assertChangeStopTimeService(request.getEncounterId(), request.getRequestId());
            }
            // Cmd：诊疗仅写开立或停嘱列。
            serviceRequestDomainService.changeServiceRequestBusinessTime(
                inpatientDoctorStationAdviceMapper.toChangeServiceRequestBusinessTimeCommand(request, operatorId));
        } else {
            // Gate：产品表名不在药/诊白名单。
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + request.getProductTableName());
        }
        // Asm：成功响应，列表由前端刷新。
        ChangeInpatientDoctorStationAdviceBusinessTimeResponse resp =
            new ChangeInpatientDoctorStationAdviceBusinessTimeResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     *
     * <p>写接口：创建出院类 ServiceRequest 并记录就诊历程出院里程碑；
     * 同一 App 事务内完成，重复转科/出院申请会被拒绝。</p>
     *
     * @param request 出院医嘱内容与就诊上下文
     * @return 新建的诊疗申请 id 与就诊历程 id
     * @throws BusinessException 重复出院/转科申请或门控失败
     */
    public CreateDischargeInpatientDoctorStationAdviceResponse createDischargeInpatientDoctorStationAdvice(
        CreateDischargeInpatientDoctorStationAdviceRequest request
    ) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        if (Boolean.TRUE.equals(serviceRequestQueryService.checkDuplicateTransferOrDischargeRequest(request.getEncounterId()))) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_DUPLICATE_TRANSFER_OR_DISCHARGE_REQUEST);
        }
        Long serviceRequestId = serviceRequestDomainService.createDischargeRequest(
            inpatientDoctorStationAdviceMapper.toCreateDischargeRequestCommand(request, operatorId));
        Long encounterHistoryId = encounterHistoryDomainService.recordDischargeMilestone(
            inpatientDoctorStationAdviceMapper.toRecordDischargeMilestoneCommand(serviceRequestId, request, operatorId));
        Long encounterId = request.getEncounterId();
        IntegrationAfterCommitSupport.runAfterCommit(() ->
                encounterQueryService.findOptionalEncounterEntityById(encounterId).ifPresent(encounter -> {
                    // 入院登记已建档；此处幂等兜底历史未建档就诊（失败不影响出院医嘱）
                    if (encounter.getTenantId() != null && encounter.getPatientId() != null) {
                        caseRecordDomainService.bootstrapIfAbsent(
                                encounter.getTenantId(),
                                encounter.getPatientId(),
                                encounterId,
                                operatorId);
                    }
                }));
        return inpatientDoctorStationAdviceMapper.toCreateDischargeResponse(serviceRequestId, encounterHistoryId);
    }

    /**
     * 住院医生站统一门控：加载快照并校验患者、IMP、可选可写态。
     *
     * <p>可见性经 {@link InpatientDoctorStationEncounterVisibilityResolver}：在科队列优先，
     * 否则按就诊主键回落（病案首页已出院患者可读医嘱）。</p>
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键（与卡片一致）
     * @param writable    为 {@code true} 时额外校验 IN_PROGRESS/ON_HOLD
     * @return 住院信息读模型快照
     */
    private InpatientEncounterContextQueryModel requireInpatientSnapshot(Long encounterId, Long patientId, boolean writable) {
        InpatientEncounterContextQueryModel snapshot =
            this.inpatientDoctorStationEncounterVisibilityResolver.assertVisible(encounterId, patientId);
        inpatientDoctorStationAdviceMapper.validateInpatientEncounterClassImp(snapshot);
        if (writable) {
            inpatientDoctorStationAdviceMapper.validateInpatientEncounterWritableGate(snapshot);
        }
        return snapshot;
    }

    /**
     * 暂存批次中的 CREATE 行：按产品表名分轨创建药/诊草稿。
     *
     * <p>长/临/出院带药均仅写 Request（BR-IP-ORD-006/029）；嘱托须正文（BR-IP-ORD-028）；
     * 出院带药 therapy 禁止诊疗行（BR-IP-ORD-029②）。</p>
     *
     * @param request    批次父请求（就诊/患者）
     * @param line       单行 CREATE
     * @param operatorId 开嘱操作人
     * @param orgId      当前机构
     * @param newMedIds  本批新建药品 requestId 收集器
     */
    private void handleCreateLine(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        List<Long> newMedIds
    ) {
        String pt = ProductTableNameHelper.normalize(line.getProductTableName());
        // Cmd：药品行（含出院带药 therapy）仅建 MedicationRequest
        if (ProductTableNameHelper.isMedication(pt)) {
            Long newId = medicationRequestDomainService.createMedicationOrderDraft(
                inpatientDoctorStationAdviceMapper.toCreateMedicationOrderDraftCommand(request, line, operatorId, orgId));
            // Pre：前端已带组号时立即并组，避免二次保存
            if (line.getMedicationPayload() != null && line.getMedicationPayload().getGroupId() != null) {
                medicationRequestDomainService.adjustMedicationOrderGroup(
                    inpatientDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                        List.of(newId), line.getMedicationPayload().getGroupId(), operatorId));
            }
            // Cmd（BR-IP-ORD-024）：皮试药联动 companion 皮试检查 SR（住院不挂费；出院带药软约定不禁）
            if (line.getMedicationPayload() != null) {
                doctorStationSkinTestSyncOrchestrator.syncAfterMedicationDraftLine(
                    ChargePolicy.INPATIENT, request.getEncounterId(), request.getPatientId(), null, orgId, operatorId,
                    newId, this.toSkinTestSpec(line.getMedicationPayload()));
            }
            newMedIds.add(newId);
        } else if (ProductTableNameHelper.isActivity(pt)) {
            // Pre：诊疗 CREATE 须带活动定义主键
            if (line.getServicePayload() == null || line.getServicePayload().getActivityDefinitionId() == null) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SERVICE_PAYLOAD_OR_ACTIVITY_ID_REQUIRED);
            }
            // Gate：出院带药仅药品，拒绝诊疗/嘱托行（BR-IP-ORD-029②）
            inpatientEntrustOrderGateSupport.assertNotDischargeMedicationForServiceLine(
                line.getTherapyTypeCode(),
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_DISCHARGE_MEDICATION_MEDICATION_ONLY);
            ProductDefinitionDisplayDto product = loadSingleProduct(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
            // Gate：嘱托行正文必填，空/空白 → EX-IP-ORD-306（BR-IP-ORD-028③）
            inpatientEntrustOrderGateSupport.requireEntrustContentIfEntrustActivity(
                product.getCategoryCode(),
                product.getProductName(),
                line.getServicePayload().getEntrustContent(),
                DoctorStationAdviceConstants.EX_IP_ORD_306,
                DoctorStationAdviceConstants.MSG_ENTRUST_CONTENT_REQUIRED);
            // Gate：护理级别须长期且同就诊无其它有效护理级别（BR-IP-ORD-026；非护理行 Gate 内直接放行）
            inpatientNursingOrderGate.assertNursingLevelLongTermOnly(
                product.getCategoryCode(),
                line.getTherapyTypeCode(),
                DoctorStationAdviceConstants.EX_IP_ORD_106,
                DoctorStationAdviceConstants.MSG_NURSING_LEVEL_LONG_TERM_ONLY);
            inpatientNursingOrderGate.assertNoConcurrentNursingLevelOrder(
                product.getCategoryCode(),
                request.getEncounterId(),
                null,
                DoctorStationAdviceConstants.EX_IP_ORD_106,
                DoctorStationAdviceConstants.MSG_NURSING_LEVEL_STOP_BEFORE_NEW);
            // Cmd：诊疗草稿仅 Request；嘱托写 entrust_content + category_enum=52
            serviceRequestDomainService.createServiceRequestDraft(
                inpatientDoctorStationAdviceMapper.toCreateServiceRequestDraftCommand(request, line, product, operatorId));
        } else {
            // Gate：住院医生站禁止耗材及其它非药/诊表名（BR-IP-ORD-012）
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
        }
    }

    /**
     * 暂存批次中的 UPDATE 行：更新药/诊草稿（长/临/出院带药均不挂费用草稿）。
     *
     * @param request    批次父请求
     * @param line       单行 UPDATE（须含 requestId）
     * @param operatorId 操作人
     * @param orgId      当前机构
     */
    private void handleUpdateLine(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId
    ) {
        String pt = ProductTableNameHelper.normalize(line.getProductTableName());
        // Pre：UPDATE 必须锚定已有请求主键
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UPDATE_LINE_REQUEST_ID_REQUIRED);
        }
        // Cmd：药品草稿变更（含出院带药 therapy 透传）
        if (ProductTableNameHelper.isMedication(pt)) {
            medicationRequestDomainService.changeMedicationOrderDraft(
                inpatientDoctorStationAdviceMapper.toChangeMedicationOrderDraftCommand(line, operatorId));
            // Cmd（BR-IP-ORD-024）：皮试标志变更时同步 companion 皮试检查 SR
            if (line.getMedicationPayload() != null) {
                doctorStationSkinTestSyncOrchestrator.syncAfterMedicationDraftLine(
                    ChargePolicy.INPATIENT, request.getEncounterId(), request.getPatientId(), null, orgId, operatorId,
                    line.getRequestId(), this.toSkinTestSpec(line.getMedicationPayload()));
            }
        } else if (ProductTableNameHelper.isActivity(pt)) {
            // Gate：出院带药 therapy 不得改写诊疗行（BR-IP-ORD-029②）
            inpatientEntrustOrderGateSupport.assertNotDischargeMedicationForServiceLine(
                line.getTherapyTypeCode(),
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_DISCHARGE_MEDICATION_MEDICATION_ONLY);
            ServiceRequestListModel existingRow = resolveServiceRequestRow(request.getEncounterId(), line.getRequestId());
            String nursingCategoryEnum = existingRow.getCategoryEnum();
            String entrustActivityName = null;
            // Pre：未传正文时回落库内已有嘱托，允许仅改排序/频次；显式空串仍走 Gate 拒绝
            String entrustContent = existingRow.getEntrustContent();
            if (line.getServicePayload() != null) {
                // Pre：载荷显式带了 entrustContent（含空串）则以本次为准做 Gate
                if (line.getServicePayload().getEntrustContent() != null) {
                    entrustContent = line.getServicePayload().getEntrustContent();
                }
                // Pre：若改活动定义，类目/名称以新产品目录为准识别嘱托
                if (line.getServicePayload().getActivityDefinitionId() != null) {
                    ProductDefinitionDisplayDto product = loadSingleProduct(
                        TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
                    nursingCategoryEnum = product.getCategoryCode();
                    entrustActivityName = product.getProductName();
                }
            }
            // Gate：嘱托行 UPDATE 正文须非空（BR-IP-ORD-028③ → EX-IP-ORD-306）
            inpatientEntrustOrderGateSupport.requireEntrustContentIfEntrustActivity(
                nursingCategoryEnum,
                entrustActivityName,
                entrustContent,
                DoctorStationAdviceConstants.EX_IP_ORD_306,
                DoctorStationAdviceConstants.MSG_ENTRUST_CONTENT_REQUIRED);
            // Gate：护理级别修改后仍须为长期（BR-IP-ORD-026）
            inpatientNursingOrderGate.assertNursingLevelLongTermOnly(
                nursingCategoryEnum,
                line.getTherapyTypeCode(),
                DoctorStationAdviceConstants.EX_IP_ORD_106,
                DoctorStationAdviceConstants.MSG_NURSING_LEVEL_LONG_TERM_ONLY);
            // Cmd：修改诊疗草稿；嘱托正文经 Mapper 透传 modify.entrustContent
            serviceRequestDomainService.modifyServiceRequestDraft(
                inpatientDoctorStationAdviceMapper.toModifyServiceRequestDraftCommand(line, operatorId));
        } else {
            // Gate：住院医生站禁止耗材及其它非药/诊表名（BR-IP-ORD-012）
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
        }
    }

    /**
     * 暂存批次中的 DELETE 行：软删请求草稿（长临均不在暂存挂费）。
     */
    private void handleDeleteLine(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId
    ) {
        String pt = ProductTableNameHelper.normalize(line.getProductTableName());
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_DELETE_LINE_REQUEST_ID_REQUIRED);
        }
        if (ProductTableNameHelper.isMedication(pt)) {
            // Cmd（BR-IP-ORD-024）：删皮试药前先清理草稿态 companion 皮试检查 SR。
            doctorStationSkinTestSyncOrchestrator.removeDraftCompanionSkinTestServiceRequest(
                ChargePolicy.INPATIENT, request.getEncounterId(), line.getRequestId(), operatorId);
            medicationRequestDomainService.removeMedicationOrderDraft(
                inpatientDoctorStationAdviceMapper.toRemoveMedicationOrderDraftCommand(line.getRequestId(), operatorId));
        } else if (ProductTableNameHelper.isActivity(pt)) {
            serviceRequestDomainService.removeServiceRequestDraft(
                inpatientDoctorStationAdviceMapper.toRemoveServiceRequestDraftCommand(line.getRequestId(), operatorId));
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
        }
    }

    /**
     * 签发单个药品目标：长临均仅 sign，不建发放与费用（BR-IP-ORD-005/006）。
     */
    private void signMedicationTarget(
        SignInpatientDoctorStationAdviceBatchRequest request,
        InpatientDoctorStationAdviceResourceKeyDto target,
        Long operatorId,
        Map<Long, String> rxMap
    ) {
        String prescriptionNo = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.COMPOSITE_PRESCRIPTION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        medicationRequestDomainService.signMedicationOrder(
            inpatientDoctorStationAdviceMapper.toSignMedicationOrderCommand(
                target.getRequestId(), prescriptionNo, operatorId));
        rxMap.put(target.getRequestId(), prescriptionNo);
    }

    /**
     * 签发单个诊疗目标：域服务 sign 后触发 LIS/PACS 占位（不升位费用）。
     */
    private void signServiceTarget(
        SignInpatientDoctorStationAdviceBatchRequest request,
        InpatientDoctorStationAdviceResourceKeyDto target,
        Long operatorId
    ) {
        ServiceRequestListModel serviceRow = resolveServiceRequestRow(request.getEncounterId(), target.getRequestId());
        // Gate（BR-IP-ORD-026）：仅护理级别行校验长临与并发；其它诊疗签发不受已有护理级别影响
        inpatientNursingOrderGate.assertNursingLevelLongTermOnly(
            serviceRow.getCategoryEnum(),
            serviceRow.getTherapyEnum(),
            DoctorStationAdviceConstants.EX_IP_ORD_106,
            DoctorStationAdviceConstants.MSG_NURSING_LEVEL_LONG_TERM_ONLY);
        inpatientNursingOrderGate.assertNoConcurrentNursingLevelOrder(
            serviceRow.getCategoryEnum(),
            request.getEncounterId(),
            target.getRequestId(),
            DoctorStationAdviceConstants.EX_IP_ORD_106,
            DoctorStationAdviceConstants.MSG_NURSING_LEVEL_STOP_BEFORE_NEW);
        serviceRequestDomainService.signServiceRequest(
            inpatientDoctorStationAdviceMapper.toSignServiceRequestCommand(target.getRequestId(), operatorId));
        dispatchLabOrImagingAdvice(request.getEncounterId(), request.getPatientId(), target.getRequestId());
    }

    /**
     * 在就诊下按医生开立来源检索诊疗申请，解析指定申请的长临属性 code。
     *
     * @param encounterId       就诊主键
     * @param serviceRequestId  诊疗申请主键
     * @return {@link TherapyType} 域 code 字符串
     * @throws BusinessException 未在医生开立来源列表中命中该申请时抛出
     */
    private String resolveServiceTherapyEnum(Long encounterId, Long serviceRequestId) {
        List<ServiceRequestListModel> rows = serviceRequestQueryService.listServiceRequestsByGenerateSource(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        for (ServiceRequestListModel row : rows) {
            if (serviceRequestId.equals(row.getId())) {
                return row.getTherapyEnum();
            }
        }
        throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, DoctorStationAdviceConstants.MSG_SERVICE_REQUEST_NOT_FOUND);
    }

    /**
     * IF-IP-DOR-14 Gate：汇总本批药品行的组号/频次，断言同组一致（BR-IP-ORD-033）。
     *
     * @param lines 草稿批次行；可空
     */
    private void assertMedicationDraftBatchGroupRates(List<InpatientDoctorStationAdviceDraftLineRequest> lines) {
        // 空批无需校验
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<Long, String> batchRateByRequestId = new HashMap<>();
        Map<Long, String> batchGroupByRequestId = new HashMap<>();
        Map<Long, List<String>> createGroupRates = new HashMap<>();
        List<Long> deleteRequestIds = new ArrayList<>();
        for (InpatientDoctorStationAdviceDraftLineRequest line : lines) {
            // 跳过空行
            if (line == null) {
                continue;
            }
            String op = trimToEmpty(line.getLineOp()).toUpperCase(java.util.Locale.ROOT);
            SaveInpatientMedicationAdviceDraftLinePayload med = line.getMedicationPayload();
            // 诊疗行不参与成组频次
            if (med == null) {
                continue;
            }
            // DELETE：从库内同组比对中剔除
            if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                if (line.getRequestId() != null) {
                    deleteRequestIds.add(line.getRequestId());
                }
                continue;
            }
            Long groupId = med.getGroupId();
            String rateCode = med.getRateCode();
            // CREATE：尚无 requestId，仅按 groupId 聚合频次
            if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                if (groupId == null) {
                    continue;
                }
                List<String> bucket = createGroupRates.get(groupId);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    createGroupRates.put(groupId, bucket);
                }
                if (rateCode != null && !rateCode.isBlank()) {
                    bucket.add(rateCode.trim());
                }
                continue;
            }
            // UPDATE：以 requestId 锚组号与频次
            if (line.getRequestId() == null) {
                continue;
            }
            if (groupId != null) {
                batchGroupByRequestId.put(line.getRequestId(), String.valueOf(groupId));
            }
            if (rateCode != null && !rateCode.isBlank()) {
                batchRateByRequestId.put(line.getRequestId(), rateCode.trim());
            }
        }
        inpatientMedicationAdviceGroupRateGate.assertDraftBatchConsistent(
            batchRateByRequestId, batchGroupByRequestId, createGroupRates, deleteRequestIds);
    }

    /**
     * 停嘱前置：药品须为长期（状态须 VERIFIED 由领域 {@code ensureLongTermForStop} 再断言）。
     *
     * @param requestId 药品申请主键
     * @throws BusinessException 非长期医嘱时抛出
     */
    private void assertLongTermMedication(Long requestId) {
        MedicationRequestDetailQueryModel detail = medicationRequestQueryService.loadMedicationRequestById(requestId);
        if (!TherapyType.LONG_TERM.getCode().equals(detail.getTherapyEnum())) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_STOP_LONG_TERM_MEDICATION_ONLY);
        }
    }

    /**
     * 停嘱前置：诊疗须为长期（状态须 VERIFIED/COMPLETED 由领域 {@code assertStoppable} 再断言）。
     *
     * @param encounterId 就诊主键（用于列举医生开立来源申请）
     * @param requestId   诊疗申请主键
     * @throws BusinessException 非长期医嘱时抛出
     */
    private void assertLongTermService(Long encounterId, Long requestId) {
        String therapy = resolveServiceTherapyEnum(encounterId, requestId);
        if (!TherapyType.LONG_TERM.getCode().equals(therapy)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_STOP_LONG_TERM_SERVICE_ONLY);
        }
    }

    /**
     * 取消停嘱前置：药品须为长期、已 STOPPED，且护士尚未完成停嘱校对（BR-IP-ORD-025）。
     *
     * @param requestId 药品申请主键
     * @throws BusinessException 非长期、非 STOPPED 或已停嘱校对时抛出
     */
    private void assertStoppedLongTermMedication(Long requestId) {
        MedicationRequestDetailQueryModel detail = medicationRequestQueryService.loadMedicationRequestById(requestId);
        if (!TherapyType.LONG_TERM.getCode().equals(detail.getTherapyEnum())) {
            // 临时医嘱须走签退/删除路径，不可取消停嘱。
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_CANCEL_STOP_LONG_TERM_MEDICATION_ONLY);
        }
        if (!RequestStatus.STOPPED.getCode().equals(detail.getStatusEnum())) {
            // 仅已停嘱行可恢复，避免误改在执状态。
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_CANCEL_STOP_NOT_STOPPED);
        }
        // 已停嘱校对须护士先取消停嘱校对（逆流程）。
        MedicationRequest entity = medicationRequestQueryService.findById(requestId);
        if (entity != null && entity.getStopPerformerCheckId() != null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                DoctorStationAdviceConstants.MSG_CANCEL_STOP_ALREADY_STOP_CHECKED);
        }
    }

    /**
     * 取消停嘱前置：诊疗须为长期、已 STOPPED，且护士尚未完成停嘱校对（BR-IP-ORD-025）。
     *
     * @param encounterId 就诊主键（用于列举医生开立来源申请）
     * @param requestId   诊疗申请主键
     * @throws BusinessException 非长期、非 STOPPED 或已停嘱校对时抛出
     */
    private void assertStoppedLongTermService(Long encounterId, Long requestId) {
        ServiceRequestListModel row = resolveServiceRequestRow(encounterId, requestId);
        if (!TherapyType.LONG_TERM.getCode().equals(row.getTherapyEnum())) {
            // 临时诊疗须走签退/删除路径，不可取消停嘱。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_CANCEL_STOP_LONG_TERM_SERVICE_ONLY);
        }
        if (!RequestStatus.STOPPED.getCode().equals(row.getStatusEnum())) {
            // 仅已停嘱行可恢复，避免误改在执状态。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_CANCEL_STOP_NOT_STOPPED);
        }
        // 已停嘱校对须护士先取消停嘱校对（逆流程）。
        if (row.getStopPerformerCheckId() != null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CANCEL_STOP_ALREADY_STOP_CHECKED);
        }
    }

    /**
     * 改停嘱时间前置：药品须为长期且当前 STOPPED。
     *
     * @param requestId 药品申请主键
     * @throws BusinessException 非长期或非 STOPPED 时抛出
     */
    private void assertChangeStopTimeMedication(Long requestId) {
        MedicationRequestDetailQueryModel detail = medicationRequestQueryService.loadMedicationRequestById(requestId);
        if (!TherapyType.LONG_TERM.getCode().equals(detail.getTherapyEnum())
            || !RequestStatus.STOPPED.getCode().equals(detail.getStatusEnum())) {
            // 仅已停嘱长期行可改停嘱时间，与领域门禁一致。
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHANGE_STOP_TIME_NOT_STOPPED);
        }
    }

    /**
     * 改停嘱时间前置：诊疗须为长期且当前 STOPPED。
     *
     * @param encounterId 就诊主键
     * @param requestId   诊疗申请主键
     * @throws BusinessException 非长期或非 STOPPED 时抛出
     */
    private void assertChangeStopTimeService(Long encounterId, Long requestId) {
        ServiceRequestListModel row = resolveServiceRequestRow(encounterId, requestId);
        if (!TherapyType.LONG_TERM.getCode().equals(row.getTherapyEnum())
            || !RequestStatus.STOPPED.getCode().equals(row.getStatusEnum())) {
            // 仅已停嘱长期行可改停嘱时间，与领域门禁一致。
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHANGE_STOP_TIME_NOT_STOPPED);
        }
    }

    /**
     * 取消停嘱出院门禁：存在未完成转科/出院申请或就诊待出院态时拒绝。
     *
     * @param encounterId 就诊主键
     * @param snapshot    住院快照（含 dischargeTypeEnum）
     * @throws BusinessException 命中门禁时抛出
     */
    private void assertCancelStopDischargeGate(Long encounterId, InpatientEncounterContextQueryModel snapshot) {
        if (Boolean.TRUE.equals(serviceRequestQueryService.checkDuplicateTransferOrDischargeRequest(encounterId))) {
            // 同就诊存在草稿/进行中的转科或出院申请时，禁止撤销停嘱以免流程冲突。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_CANCEL_STOP_DISCHARGE_GATE);
        }
        if (CustomConstants.EncounterHistoryMilestoneName.DISCHARGE.equals(snapshot.getDischargeTypeEnum())) {
            // 读模型已标记待出院里程碑时，同样禁止取消停嘱。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_CANCEL_STOP_DISCHARGE_GATE);
        }
    }

    /**
     * 在医生开立来源诊疗列表中定位单条申请读模型。
     *
     * @param encounterId       就诊主键
     * @param serviceRequestId  诊疗申请主键
     * @return 列表读模型
     * @throws BusinessException 未命中时抛出
     */
    private ServiceRequestListModel resolveServiceRequestRow(Long encounterId, Long serviceRequestId) {
        List<ServiceRequestListModel> rows = serviceRequestQueryService.listServiceRequestsByGenerateSource(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        // 在医生开立来源列表中定位目标诊疗申请，供长临/状态 Pre 校验。
        for (ServiceRequestListModel row : rows) {
            if (serviceRequestId.equals(row.getId())) {
                return row;
            }
        }
        throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, DoctorStationAdviceConstants.MSG_SERVICE_REQUEST_NOT_FOUND);
    }

    /**
     * 按产品表与主键加载单条产品定义读模型，供诊疗开立前校验与 Mapper 装配。
     *
     * @param table 产品来源表名
     * @param id    定义主键
     * @return 读模型
     * @throws BusinessException 未命中
     */
    private ProductDefinitionDisplayDto loadSingleProduct(String table, Long id) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(table);
        lookup.setProductDefinitionId(id);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, id);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_ACTIVITY_PRODUCT_DEFINITION_NOT_FOUND);
        }
        return row;
    }

    /**
     * 规范化并校验长临医嘱类型 code（仅 LONG_TERM / TEMPORARY）。
     *
     * @param code 前端传入的长临 code
     * @return 去空白后的域 code
     * @throws BusinessException 空或非法 code 时抛出
     */
    private static String normalizeTherapyTypeCode(String code) {
        String trimmed = trimToEmpty(code);
        if (StringUtils.isEmpty(trimmed)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_THERAPY_TYPE_REQUIRED);
        }
        if (TherapyType.LONG_TERM.getCode().equals(trimmed) || TherapyType.TEMPORARY.getCode().equals(trimmed)) {
            return trimmed;
        }
        throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
            DoctorStationAdviceConstants.MSG_INVALID_THERAPY_TYPE_PREFIX + trimmed);
    }

    /**
     * IF-IP-DOR-18：加载启用态样本定义下拉（含拼音/五笔，供申请单样本 filterable）。
     *
     * @return OptionDto 列表（上限 500）
     */
    private List<OptionDto> loadSpecimenDefinitionOptions() {
        // Pre：无关键字取启用样本上限 500，透出 pyStr/wbStr 供前端模糊
        SearchSpecimenDefinitionQuery query = new SearchSpecimenDefinitionQuery();
        query.setLimit(500);
        List<SpecimenDefinitionSearchItemQueryModel> rows = specimenDefinitionQueryService.searchByKeyWord(query);
        return OptionDtoAssembler.sortBySortAsc(
            OptionDtoAssembler.toSearchableOptionsWithSortFallback(
                rows,
                SpecimenDefinitionSearchItemQueryModel::getId,
                SpecimenDefinitionSearchItemQueryModel::getName,
                SpecimenDefinitionSearchItemQueryModel::getId,
                SpecimenDefinitionSearchItemQueryModel::getPyStr,
                SpecimenDefinitionSearchItemQueryModel::getWbStr,
                0L));
    }

    /**
     * 签署检验/检查医嘱后按类别分流提交 LIS/PACS 外发申请（扩展点 no-op 安全）。
     *
     * @param encounterId 就诊
     * @param patientId   患者
     * @param requestId   服务申请主键
     */
    private void dispatchLabOrImagingAdvice(Long encounterId, Long patientId, Long requestId) {
        Map<Long, List<ServiceRequestDetailListModel>> detailMap =
            serviceRequestDetailListAssembler.mapDetailListModelsByServiceRequestIds(List.of(requestId));
        List<ServiceRequestDetailSummaryDto> summaries = serviceApplyFormAssembler.toDetailSummaryDtos(
            ServiceApplyFormOrchestrator.toDetailSummaryModels(detailMap.get(requestId)));
        LisAdviceApplyExtensionIn lisCmd = new LisAdviceApplyExtensionIn();
        lisCmd.setEncounterId(encounterId);
        lisCmd.setPatientId(patientId);
        lisCmd.setRequestId(requestId);
        lisCmd.setDetails(toLisAdviceApplyDetailLines(summaries));
        lisAdviceApplyExtension.submitIfLab(lisCmd);

        PacsAdviceApplyExtensionIn pacsCmd = new PacsAdviceApplyExtensionIn();
        pacsCmd.setEncounterId(encounterId);
        pacsCmd.setPatientId(patientId);
        pacsCmd.setRequestId(requestId);
        pacsCmd.setDetails(toPacsAdviceApplyDetailLines(summaries));
        pacsAdviceApplyExtension.submitIfImaging(pacsCmd);
    }

    /**
     * 将申请明细摘要映射为 LIS 外发行（targetTable/targetId/展示名/remark）。
     *
     * @param summaries 服务申请明细摘要
     * @return LIS 明细行；空输入返回不可变空列表
     */
    private List<LisAdviceApplyDetailLine> toLisAdviceApplyDetailLines(List<ServiceRequestDetailSummaryDto> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        List<LisAdviceApplyDetailLine> lines = new ArrayList<>();
        for (ServiceRequestDetailSummaryDto summary : summaries) {
            if (summary == null) {
                continue;
            }
            LisAdviceApplyDetailLine line = new LisAdviceApplyDetailLine();
            line.setTargetTable(summary.getTargetTable());
            line.setTargetId(summary.getTargetId());
            line.setTargetDisplayName(summary.getTargetDisplayName());
            line.setRemark(summary.getRemark());
            lines.add(line);
        }
        return lines;
    }

    /**
     * 将申请明细摘要映射为 PACS 外发行（targetTable/targetId/展示名/remark）。
     *
     * @param summaries 服务申请明细摘要
     * @return PACS 明细行；空输入返回不可变空列表
     */
    private List<PacsAdviceApplyDetailLine> toPacsAdviceApplyDetailLines(List<ServiceRequestDetailSummaryDto> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        List<PacsAdviceApplyDetailLine> lines = new ArrayList<>();
        for (ServiceRequestDetailSummaryDto summary : summaries) {
            if (summary == null) {
                continue;
            }
            PacsAdviceApplyDetailLine line = new PacsAdviceApplyDetailLine();
            line.setTargetTable(summary.getTargetTable());
            line.setTargetId(summary.getTargetId());
            line.setTargetDisplayName(summary.getTargetDisplayName());
            line.setRemark(summary.getRemark());
            lines.add(line);
        }
        return lines;
    }

    /**
     * 将住院皮试药草稿载荷映射为 orchestration 中性 Spec（边界转换，禁止 orchestration 依赖 application DTO）。
     *
     * @param payload 住院皮试药草稿行载荷
     * @return 皮试同步 Spec；payload 为空时返回 null
     */
    private SkinTestMedicationDraftSpec toSkinTestSpec(SaveInpatientMedicationAdviceDraftLinePayload payload) {
        if (payload == null) {
            return null;
        }
        return new SkinTestMedicationDraftSpec()
            .setSkinTestFlag(payload.getSkinTestFlag())
            .setContractId(payload.getContractId())
            .setEncounterDiagnosisIds(payload.getEncounterDiagnosisIds());
    }

    /**
     * 将签发目标 DTO 列表映射为 orchestration 中性 Spec 列表。
     *
     * @param targets 签发目标 DTO 列表
     * @return Spec 列表；入参为空时返回空列表
     */
    private List<AdviceSignTargetSpec> toSignTargetSpecs(List<InpatientDoctorStationAdviceResourceKeyDto> targets) {
        if (targets == null) {
            return List.of();
        }
        List<AdviceSignTargetSpec> specs = new ArrayList<>();
        for (InpatientDoctorStationAdviceResourceKeyDto target : targets) {
            if (target == null) {
                continue;
            }
            specs.add(new AdviceSignTargetSpec()
                .setProductTableName(target.getProductTableName())
                .setRequestId(target.getRequestId()));
        }
        return specs;
    }

    /**
     * 将 orchestration 中性签发目标 Spec 列表映射回应用层 DTO 列表。
     *
     * @param specs 签发目标 Spec 列表
     * @return DTO 列表；入参为空时返回空列表
     */
    private List<InpatientDoctorStationAdviceResourceKeyDto> fromSignTargetSpecs(List<AdviceSignTargetSpec> specs) {
        if (specs == null) {
            return List.of();
        }
        List<InpatientDoctorStationAdviceResourceKeyDto> targets = new ArrayList<>();
        for (AdviceSignTargetSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            InpatientDoctorStationAdviceResourceKeyDto target = new InpatientDoctorStationAdviceResourceKeyDto();
            target.setProductTableName(spec.getProductTableName());
            target.setRequestId(spec.getRequestId());
            targets.add(target);
        }
        return targets;
    }
}
