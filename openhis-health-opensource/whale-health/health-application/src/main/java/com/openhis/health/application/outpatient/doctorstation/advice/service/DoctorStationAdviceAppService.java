/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.shared.product.ProductTableNameHelper;
import com.openhis.health.application.shared.advice.ServiceAdviceDraftCommandHelper;
import com.openhis.health.application.outpatient.doctorstation.advice.support.ServiceApplyFormOrchestrator;
import com.openhis.health.application.outpatient.financial.charge.support.OutpatientPaymentDraftLifecycleOrchestrator;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.advice.InpatientEntrustOrderGateSupport;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.outpatient.doctorstation.advice.support.OutpatientMedicationSignChargeOrchestrator;
import com.openhis.health.application.shared.advice.AdviceSignTargetSpec;
import com.openhis.health.application.shared.advice.SkinTestMedicationDraftSpec;
import com.openhis.health.application.shared.advice.DoctorStationSkinTestSyncOrchestrator;
import com.openhis.health.application.shared.advice.DoctorStationSkinTestSyncOrchestrator.ChargePolicy;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.shared.product.DoctorStationAdviceFifoAllocationAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceListAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.ServiceApplyFormAssembler;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftBindItemDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceResourceKeyDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.SaveMedicationAdviceDraftLinePayload;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import com.openhis.health.application.outpatient.doctorstation.advice.mapper.DoctorStationAdviceMapper;
import com.openhis.health.infrastructure.integration.lis.LisAdviceApplyExtension;
import com.openhis.health.infrastructure.integration.lis.ExtensionIn.LisAdviceApplyExtensionIn;
import com.openhis.health.infrastructure.integration.lis.dto.LisAdviceApplyDetailLine;
import com.openhis.health.infrastructure.integration.pacs.PacsAdviceApplyExtension;
import com.openhis.health.infrastructure.integration.pacs.ExtensionIn.PacsAdviceApplyExtensionIn;
import com.openhis.health.infrastructure.integration.pacs.dto.PacsAdviceApplyDetailLine;
import com.openhis.health.application.outpatient.doctorstation.advice.query.DoctorStationAdviceOrderBindQueryService;
import com.openhis.health.application.outpatient.doctorstation.advice.request.GetDoctorStationAdviceOrderBindInfoRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.ListDoctorStationAdviceRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.InitDoctorStationAdviceRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.ListDoctorStationServiceApplyFormsRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SaveDoctorStationServiceApplyFormRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.ResolveDoctorStationAdviceDraftBindsRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SaveDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.response.ResolveDoctorStationAdviceDraftBindsResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SignDoctorStationAdviceBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.UpdateDoctorStationMedicationAdviceGroupsRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.WithdrawDoctorStationAdviceBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.response.GetDoctorStationAdviceOrderBindInfoResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.ListDoctorStationAdviceResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.OutpatientPrescriptionSplitAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.response.InitDoctorStationAdviceResponse;
import com.openhis.health.domain.administration.specimendefinition.queries.SearchSpecimenDefinitionQuery;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionSearchItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.service.SpecimenDefinitionQueryService;
import com.openhis.health.domain.diagnosis.bodystructure.service.BodyStructureQueryService;
import com.openhis.health.application.shared.option.BodyStructureOptionAssembler;
import com.openhis.health.application.shared.option.OptionDtoAssembler;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.health.application.outpatient.doctorstation.advice.response.ListActivityExecutionLocationsResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.ListDoctorStationServiceApplyFormsResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.SaveDoctorStationServiceApplyFormResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.PageDoctorStationAdviceCatalogResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.PageDoctorStationAdviceResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.openhis.health.domain.share.constants.DoctorStationAdviceScopeTab;
import com.openhis.health.application.outpatient.doctorstation.advice.response.SaveDoctorStationAdviceDraftBatchResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.SignDoctorStationAdviceBatchResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.UpdateDoctorStationMedicationAdviceGroupsResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.WithdrawDoctorStationAdviceBatchResponse;
import com.openhis.health.application.shared.financial.EncounterChargeAccountOpener;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.medication.medicationdispense.command.ReleaseDispenseReserveCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.application.outpatient.doctorstation.advice.support.OrderRequestFifoSignContextLoader;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.application.shared.product.DoctorStationCatalogPositiveStockLocationBinder;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.application.shared.option.ContractFeeNatureOptionLoader;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.devicedispense.command.ReleaseDeviceDispenseReserveCommand;
import com.openhis.health.domain.workflow.devicedispense.command.ReplaceFifoDeviceDispenseDraftsCommand;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.command.ChangeDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestFifoSignContext;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.application.outpatient.doctorstation.advice.request.ListActivityExecutionLocationsRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.response.ActivityExecutionLocationRuleRowDto;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.shared.advice.ServiceRequestDetailListAssembler;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊医生站医嘱开立编排：统一列表、目录检索、开立/停嘱/作废与绑定占位。
 */
@Service
@RequiredArgsConstructor
public class DoctorStationAdviceAppService {

    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final DoctorStationAdviceMapper doctorStationAdviceMapper;
    private final DoctorStationAdviceListAssembler doctorStationAdviceListAssembler;
    private final DoctorStationAdviceCatalogAssembler doctorStationAdviceCatalogAssembler;
    private final DoctorStationAdviceOrderBindQueryService doctorStationAdviceOrderBindQueryService;
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;
    /** 医生站「只看库存>0」：模板库位下推至目录分页 Spec。 */
    private final DoctorStationCatalogPositiveStockLocationBinder doctorStationCatalogPositiveStockLocationBinder;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    private final ChargeItemDomainService chargeItemDomainService;
    /** 读侧：签发耗材前复用草稿费用账户（行级费用性质） */
    private final ChargeItemQueryService chargeItemQueryService;
    private final AccountQueryService accountQueryService;
    /** 就诊挂账账户解析（按费用性质合同 → account_id）。 */
    private final EncounterChargeAccountOpener encounterChargeAccountOpener;
    /** 合同读侧：行级 contractId → bus_no。 */
    private final ContractQueryService contractQueryService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final BusinessSequenceService businessSequenceService;
    private final LisAdviceApplyExtension lisAdviceApplyExtension;
    private final PacsAdviceApplyExtension pacsAdviceApplyExtension;
    private final CodeSystemQueryService codeSystemQueryService;
    /** 用药频次下拉：读 cus_frequency 启用态，替代术语 rate_code。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 费用性质下拉：按就诊已开立账户装配（展示合同名）。 */
    private final ContractFeeNatureOptionLoader contractFeeNatureOptionLoader;
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    private final OrderRequestFifoSignContextLoader orderRequestFifoSignContextLoader;
    private final OutpatientMedicationSignChargeOrchestrator outpatientMedicationSignChargeOrchestrator;
    private final DoctorStationSkinTestSyncOrchestrator doctorStationSkinTestSyncOrchestrator;
    /** 门诊禁开嘱托识别（BR-ORD-032；与住院嘱托双锚同源）。 */
    private final InpatientEntrustOrderGateSupport inpatientEntrustOrderGateSupport;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final ActivityExecutionLocationQueryService activityExecutionLocationQueryService;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final OutpatientPrescriptionSplitAssembler outpatientPrescriptionSplitAssembler;
    private final ServiceApplyFormOrchestrator serviceApplyFormOrchestrator;
    private final ServiceApplyFormAssembler serviceApplyFormAssembler;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestDetailListAssembler serviceRequestDetailListAssembler;
    private final BodyStructureQueryService bodyStructureQueryService;
    private final SpecimenDefinitionQueryService specimenDefinitionQueryService;
    private final OutpatientPaymentDraftLifecycleOrchestrator outpatientPaymentDraftLifecycleOrchestrator;
    /**
     * 医嘱开立页初始化：用药频次、用法（给药途径）与费用性质（合同）下拉。
     *
     * @param request 就诊与患者门控
     * @return 术语与费用性质初始化数据
     * @throws BusinessException 就诊不存在或患者不一致
     */
    public InitDoctorStationAdviceResponse initDoctorStationAdvice(InitDoctorStationAdviceRequest request) {
        // 门控：与列表/目录一致，先加载门诊快照并校验患者卡片与就诊绑定一致。
        OutpatientEncounterContextQueryModel snapshot = EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // 前置：频次读 cus_frequency 主数据；给药途径仍走术语缓存；费用性质按就诊挂号账户分类收窄。
        InitDoctorStationAdviceResponse resp = new InitDoctorStationAdviceResponse();
        resp.setMedicationRateOptions(frequencyOptionLoader.loadActiveRateOptions());
        resp.setMedicationRouteOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.METHOD_CODE));
        resp.setFeeNatureOptions(
            contractFeeNatureOptionLoader.loadOptionsForEncounter(request.getPatientId(), request.getEncounterId()));
        resp.setBodyStructureOptions(loadBodyStructureOptions());
        resp.setSpecimenDefinitionOptions(loadSpecimenDefinitionOptions());
        return resp;
    }

    /**
     * 按诊疗定义查询可执行科室规则（执行科室/地点展示名批量解析）。
     *
     * @param request 就诊、患者与诊疗定义门控
     * @return 规则列表（含展示名）
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public ListActivityExecutionLocationsResponse listActivityExecutionLocations(ListActivityExecutionLocationsRequest request) {
        // Gate：就诊与患者一致性
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
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
            } else {
                line.setExecutionOrgName(null);
            }
            line.setExecutionLocationId(src.getExecutionLocationId());
            if (src.getExecutionLocationId() != null) {
                line.setExecutionLocationName(locNameById.get(src.getExecutionLocationId()));
            } else {
                line.setExecutionLocationName(null);
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
     * IF-DOR-20：分页查询患者门诊医嘱（单接口分 Tab：当前医嘱 / 历史医嘱；全类型统一列表）。
     *
     * <p>读口径与 IF-DOR-10 一致（药品 + 诊疗 + 耗材）；是否按处方展示由前端处理。</p>
     *
     * @param request 就诊、患者、{@code adviceScopeTabCode}、可选过滤与分页
     * @return 医嘱统一列表行分页
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public PageDoctorStationAdviceResponse pageDoctorStationAdvice(PageDoctorStationAdviceRequest request) {
        // Gate：与列表/目录一致，先校验就诊快照与患者卡片一致，再加载医嘱读模型。
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        List<DoctorStationAdviceRowDto> allRows;
        // 分支：HISTORY 走患者维度跨就诊；其余（含 null）默认当前就诊全类型医嘱。
        if (DoctorStationAdviceScopeTab.HISTORY.equals(request.getAdviceScopeTabCode())) {
            // Pre：历史 Tab 按患者跨就诊拉全类型医嘱，排除当前工作站就诊。
            allRows = doctorStationAdviceListAssembler.assemblePatientHistoryAdviceRows(
                request.getPatientId(),
                request.getEncounterId(),
                request.getOrderingDepartmentId(),
                request.getRequestStatusEnumIn());
        } else {
            // Pre：当前 Tab 仅装配当前就诊全类型医嘱。
            allRows = doctorStationAdviceListAssembler.assembleAdviceQueryRows(
                request.getEncounterId(),
                request.getPatientId(),
                false,
                null,
                request.getOrderingDepartmentId(),
                request.getRequestStatusEnumIn());
        }
        // Asm：全量行内存分页，与 IF-DOR-10 行 DTO 同构下发。
        return PagedResultBuilder.buildFromFullList(
            new PageDoctorStationAdviceResponse(),
            allRows,
            request.getPageNo(),
            request.getBoundedPageSize());
    }

    /**
     * 加载当前就诊医嘱统一列表。
     *
     * @param request 就诊、患者、仅本人过滤
     * @return 合并药品/诊疗/耗材行与收费态汇总
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public ListDoctorStationAdviceResponse listDoctorStationAdvice(ListDoctorStationAdviceRequest request) {
        // 门控：必须先有有效门诊读模型行，再比对请求中的患者，防止跨患者卡片拉医嘱。
        OutpatientEncounterContextQueryModel snapshot = EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        Long uid = null;
        if (Boolean.TRUE.equals(request.getOnlyMine())) {
            uid = currentPractitionerResolver.findOperatorIdOrNull();
        }
        // 装配：三类申请与收费态归并由装配器一次性完成，本方法不写库、不签发。
        List<DoctorStationAdviceRowDto> rows =
            doctorStationAdviceListAssembler.assembleRows(
                request.getEncounterId(), request.getPatientId(), request.getOnlyMine(), uid);
        // —— B.10.11：使用 PagedResultBuilder 构建响应，保持 Response 类纯粹性。——
        return PagedResultBuilder.buildList(new ListDoctorStationAdviceResponse(), rows);
    }

    /**
     * 分页检索可开立医嘱项目目录。
     *
     * @param request 分页条件、定价来源、药房库位等
     * @return 当前页目录行（含库存预减、定价 JSON）
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public PageDoctorStationAdviceCatalogResponse pageDoctorStationAdviceCatalog(PageDoctorStationAdviceCatalogRequest request) {
        OutpatientEncounterContextQueryModel snapshot = EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // 前置：将前端分页/筛选参数转为读模型分页命令（定价来源、关键字等下推至 RM 查询）。
        ProductDefinitionPageQuery pageQuery = doctorStationAdviceMapper.toProductDefinitionPageQuery(request);
        // Pre：只看库存>0 时按取药模板库位收窄 EXISTS（与目录可开立量展示对齐）
        Long catalogOrgId = snapshot.getOrganizationId();
        if (request.getOrganizationUnitId() != null) {
            catalogOrgId = request.getOrganizationUnitId();
        }
        doctorStationCatalogPositiveStockLocationBinder.bindIfPositiveQuantityOnly(
            pageQuery, catalogOrgId, request.getPharmacyLocationId());
        Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
        // 装配：目录行（读模型已分页，装配器不再发起分页查询）
        List<DoctorStationAdviceCatalogRowDto> rows =
            doctorStationAdviceCatalogAssembler.assemblePageContent(request, page, snapshot);
        // —— B.10.11：行类型与 Page 元素类型不同，用 build(response, dtoRows, page) 取 Page 元数据 + 已映射 content。——
        return PagedResultBuilder.build(new PageDoctorStationAdviceCatalogResponse(), rows, page);
    }

    /**
     * IF-DOR-21：保存检验/检查/治疗申请单（整张）；编辑时删旧 DRAFT 后取新 {@code AR} 号。
     *
     * @param request 就诊、患者、类别、整单执行科室、整单执行次数、整单诊断绑定（可选）与明细行
     * @return 新申请单号与新建 requestId 列表
     * @throws BusinessException 门控失败、旧号校验失败、定价缺失或域校验失败
     */
    public SaveDoctorStationServiceApplyFormResponse saveDoctorStationServiceApplyForm(
        SaveDoctorStationServiceApplyFormRequest request
    ) {
        // Gate：与 IF-DOR-14 一致，先校验就诊快照与患者卡片一致。
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        // Pre：申请单默认挂账——双账户优先医保户，单户个人现金；行级合同可在 Spec 中覆盖时仍走本默认
        Long accountId = this.resolveAdviceChargeAccountId(request.getEncounterId(), null);
        SaveDoctorStationAdviceDraftBatchRequest chargeContext = new SaveDoctorStationAdviceDraftBatchRequest();
        chargeContext.setEncounterId(request.getEncounterId());
        chargeContext.setPatientId(request.getPatientId());
        // Cmd：删旧（若有）→ 取新 AR 号 → 逐行 createServiceRequestDraft（整单 org/诊断透传）+ 挂费。
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
            (encounterId, patientId, serviceRequestId, activityDefinitionId, quantity, unitCode, productRow) ->
                createChargeIfPossible(
                    chargeContext, serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST,
                    TableNameConstants.WKF_ACTIVITY_DEFINITION, activityDefinitionId,
                    quantity, unitCode, productRow, null, null, accountId, orgId, operatorId));
        // Asm：返回新号与 requestId 列表。
        SaveDoctorStationServiceApplyFormResponse resp = new SaveDoctorStationServiceApplyFormResponse();
        resp.setPrescriptionNo(saved.getPrescriptionNo());
        resp.setRequestIds(saved.getRequestIds());
        return resp;
    }

    /**
     * IF-DOR-22：按类别查询当前就诊 {@code AR} 前缀申请单列表。
     *
     * @param request 就诊、患者与 categoryEnum
     * @return 按 prescriptionNo 分组的申请单头列表
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public ListDoctorStationServiceApplyFormsResponse listDoctorStationServiceApplyForms(
        ListDoctorStationServiceApplyFormsRequest request
    ) {
        // Gate：与 IF-DOR-10 一致。
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // Pre + Asm：按 AR 前缀与 category 过滤后分组装配。
        ListDoctorStationServiceApplyFormsResponse resp = new ListDoctorStationServiceApplyFormsResponse();
        resp.setForms(serviceApplyFormAssembler.toHeaderDtos(
            serviceApplyFormOrchestrator.listApplyForms(
                request.getEncounterId(), request.getCategoryEnum())));
        return resp;
    }

    /**
     * 查询医嘱绑定信息（当前为占位查询服务，返回空列表亦可）。
     *
     * @param request 绑定类型、项目主键/业务号、组织单元
     * @return 绑定行集合
     */
    public GetDoctorStationAdviceOrderBindInfoResponse getDoctorStationAdviceOrderBindInfo(GetDoctorStationAdviceOrderBindInfoRequest request) {
        // 前置：用法绑定 itemNo=method_code；诊疗绑定 itemId/数字 itemNo=活动定义主键字符串
        String sourceItemKey = doctorStationAdviceMapper.resolveOrderBindSourceItemKey(
            request.getBindType(), request.getItemNo(), request.getItemId());
        GetDoctorStationAdviceOrderBindInfoResponse resp = new GetDoctorStationAdviceOrderBindInfoResponse();
        resp.setBindRows(doctorStationAdviceOrderBindQueryService.listBindingsBySourceItemKey(
            request.getBindType(), sourceItemKey, request.getOrganizationUnitId()));
        return resp;
    }

    /**
     * IF-DOR-23：暂存前解析变更行中药品/诊疗的直接绑定项（纯读，不去重、不级联）。
     *
     * @param request 与 IF-DOR-14 同构的就诊、患者与暂存变更行
     * @return 绑定明细及触发源 based_on 锚点，供前端确认后再暂存
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public ResolveDoctorStationAdviceDraftBindsResponse resolveDoctorStationAdviceDraftBinds(
        ResolveDoctorStationAdviceDraftBindsRequest request
    ) {
        // Gate：与 IF-DOR-14 一致，防止跨就诊误解析绑定
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // Pre：按暂存变更行解析 USAGE/ACTIVITY 直接模板绑定，数量取模板默认值
        List<DoctorStationAdviceDraftBindItemDto> bindItems =
            doctorStationAdviceOrderBindQueryService.listDraftBindItems(
                request.getLines(), snapshot.getOrganizationId());
        // Asm：返回绑定列表，去重与派生行落库由前端在 IF-DOR-14 前处理
        ResolveDoctorStationAdviceDraftBindsResponse resp = new ResolveDoctorStationAdviceDraftBindsResponse();
        resp.setBindItems(bindItems);
        return resp;
    }

    /**
     * 暂存医嘱变更批次（新建/更新/删除行混合）。
     *
     * @param request 就诊、患者、行集合
     * @return 成功标记与新建药品医嘱主键列表
     * @throws BusinessException 门控失败、自费账户缺失、行操作或资源类型非法、域校验失败
     */
    public SaveDoctorStationAdviceDraftBatchResponse saveDoctorStationAdviceDraftBatch(SaveDoctorStationAdviceDraftBatchRequest request) {
        // 门控：加载门诊快照并校验患者上下文一致
        OutpatientEncounterContextQueryModel snapshot = EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // 解析当前操作人主键
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        // 前置：至少存在个人现金或医保挂账账户；具体行按费用性质合同解析 account_id
        if (accountQueryService.getSelfPayAccount(request.getEncounterId()) == null
            && accountQueryService.getMedicalInsuranceAccount(request.getEncounterId()) == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊未解析到挂账账户，无法生成费用草稿");
        }
        // 收集新建药品医嘱主键，供前端局部刷新
        List<Long> newMedIds = new ArrayList<>();
        // 命令：按行操作码分发 CREATE/UPDATE/DELETE；单行失败则整批随 AppService UoW 回滚。
        for (DoctorStationAdviceDraftLineRequest line : request.getLines()) {
            String op = trimToEmpty(line.getLineOp()).toUpperCase(java.util.Locale.ROOT);
            // Pre：行级合同 → 挂账账户（双账户默认医保；选自费合同则挂个人现金）
            Long lineAccountId = this.resolveAdviceChargeAccountId(
                request.getEncounterId(), this.extractLineContractId(line));
            if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                handleCreateLine(request, line, operatorId, orgId, lineAccountId, newMedIds);
            } else if (DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                handleUpdateLine(request, line, operatorId, orgId, lineAccountId);
            } else if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                handleDeleteLine(request, line, operatorId);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的行操作: " + line.getLineOp());
            }
        }
        // 装配：批次成功应答；新建药品 id 列表供前端局部刷新。
        SaveDoctorStationAdviceDraftBatchResponse resp = new SaveDoctorStationAdviceDraftBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setNewMedicationRequestIds(newMedIds);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 处理暂存批次中的 CREATE 行（新建草稿医嘱并按定价规则挂费用草稿；定价不满足开立口径时由 {@link #createChargeIfPossible} 阻断）。
     *
     * @param request    批次上下文（就诊、患者），用于组装域命令
     * @param line       单行请求（资源类型 + 各资源载荷）
     * @param operatorId 当前操作人，写入域命令
     * @param orgId      开立机构，药品草稿落库需要
     * @param accountId  挂账账户（按行费用性质解析后的 account_id）
     * @param newMedIds  输出：本轮新建的药品申请主键，供前端刷新列表或跳转
     * @throws BusinessException 资源类型非法、载荷缺失或域不变量失败（由下层抛出）
     */
    private void handleCreateLine(
        SaveDoctorStationAdviceDraftBatchRequest request,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        Long accountId,
        List<Long> newMedIds
    ) {
        String pt = ProductTableNameHelper.normalize(line.getProductTableName());
        if (ProductTableNameHelper.isMedication(pt)) {
            // 药品：先落药品申请草稿（含生成来源），再按需调整组号，最后按批号售价定价行生成费用草稿（缺失则整批保存失败）
            Long newMedicationRequestId = medicationRequestDomainService.createMedicationOrderDraft(
                doctorStationAdviceMapper.toCreateMedicationOrderDraftCommand(request, line, operatorId, orgId));
            if (line.getMedicationPayload() != null && line.getMedicationPayload().getGroupId() != null) {
                // 成组：与另开成组接口一致，此处允许单行创建后立即并入组
                medicationRequestDomainService.adjustMedicationOrderGroup(
                    doctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                        List.of(newMedicationRequestId), line.getMedicationPayload().getGroupId(), operatorId));
            }
            createChargeIfPossible(request, newMedicationRequestId, TableNameConstants.MED_MEDICATION_REQUEST,
                TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId(),
                line.getMedicationPayload().getQuantity(), line.getMedicationPayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId()),
                line.getMedicationPayload().getDispenseLocationId(), line.getMedicationPayload().getMethodCode(),
                accountId, orgId, operatorId);
            // Cmd（BR-ORD-025）：皮试药联动 companion 皮试检查 SR + 操作费草稿。
            doctorStationSkinTestSyncOrchestrator.syncAfterMedicationDraftLine(
                ChargePolicy.OUTPATIENT, request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                newMedicationRequestId, this.toSkinTestSpec(line.getMedicationPayload()));
            newMedIds.add(newMedicationRequestId);
        } else if (ProductTableNameHelper.isDevice(pt)) {
            // 耗材：草稿 + 费用；dispenseLocationId 来自目录 IF-DOR-11，写路径 FIFO 不再 match 模板
            CreateDeviceOrderDraftCommand createDeviceCmd =
                doctorStationAdviceMapper.toCreateDeviceOrderDraftCommand(request, line, operatorId);
            // Pre：行级合同 id → bus_no 落 contract_code，供列表回显费用性质
            this.applyDeviceContractCode(createDeviceCmd.getItem(), this.extractLineContractId(line));
            Long newDeviceRequestId = deviceRequestDomainService.createDeviceOrderDraft(createDeviceCmd);
            createChargeIfPossible(request, newDeviceRequestId, TableNameConstants.WKF_DEVICE_REQUEST,
                TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId(),
                line.getDevicePayload().getQuantity(), line.getDevicePayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId()),
                line.getDevicePayload().getDispenseLocationId(), null,
                accountId, orgId, operatorId);
        } else if (ProductTableNameHelper.isActivity(pt)) {
            // 诊疗：必须先解析活动定义对应的产品读模型行，再落服务申请草稿（携带展示/计价元数据）
            ProductDefinitionDisplayDto activityProductRow =
                loadSingleProduct(TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
            // Gate：门诊禁开嘱托（目录已排除；写路径再拦预选/组套绕过，BR-ORD-032）
            String activityName = null;
            if (activityProductRow != null) {
                activityName = activityProductRow.getProductName();
            }
            String categoryCode = null;
            if (activityProductRow != null) {
                categoryCode = activityProductRow.getCategoryCode();
            }
            inpatientEntrustOrderGateSupport.assertNotEntrustForOutpatient(categoryCode, activityName);
            Long newServiceRequestId = serviceRequestDomainService.createServiceRequestDraft(
                doctorStationAdviceMapper.toCreateServiceRequestDraftCommand(request, line, activityProductRow, operatorId));
            createChargeIfPossible(request, newServiceRequestId, TableNameConstants.WKF_SERVICE_REQUEST,
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId(),
                ServiceAdviceDraftCommandHelper.requireQuantity(
                    line.getServicePayload().getQuantity(), line.getServicePayload().getChildOverridesJson()),
                ServiceAdviceDraftCommandHelper.requireUnitCode(
                    line.getServicePayload().getUnitCode(), line.getServicePayload().getChildOverridesJson(), activityProductRow),
                activityProductRow, null, null, accountId, orgId, operatorId);
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + line.getProductTableName());
        }
    }

    /**
     * 处理 UPDATE 行——先作废「未计费且仍为草稿」的收费项，避免改医嘱后费用仍指向旧定义，再改医嘱并重挂费用。
     *
     * @param request    批次上下文
     * @param line       含 {@code requestId} 与各资源载荷
     * @param operatorId 操作人
     * @param orgId      机构上下文（费用草稿）
     * @param accountId  挂账账户（按行费用性质解析；UPDATE 会作废旧草稿费后按新账户重建）
     * @throws BusinessException 资源类型非法、requestId 缺失（由 Mapper/域抛出）或域校验失败
     */
    private void handleUpdateLine(
        SaveDoctorStationAdviceDraftBatchRequest request,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        Long accountId
    ) {
        String pt = ProductTableNameHelper.normalize(line.getProductTableName());
        if (ProductTableNameHelper.isMedication(pt)) {
            // 药品：作废旧草稿费用 → 改药品草稿 → 再按新定义/数量生成费用草稿
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, line.getRequestId(), operatorId));
            medicationRequestDomainService.changeMedicationOrderDraft(
                doctorStationAdviceMapper.toChangeMedicationOrderDraftCommand(line, operatorId));
            createChargeIfPossible(request, line.getRequestId(), TableNameConstants.MED_MEDICATION_REQUEST,
                TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId(),
                line.getMedicationPayload().getQuantity(), line.getMedicationPayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId()),
                line.getMedicationPayload().getDispenseLocationId(), line.getMedicationPayload().getMethodCode(),
                accountId, orgId, operatorId);
            // Cmd（BR-ORD-025）：皮试标志变更时同步 companion 皮试检查 SR。
            doctorStationSkinTestSyncOrchestrator.syncAfterMedicationDraftLine(
                ChargePolicy.OUTPATIENT, request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                line.getRequestId(), this.toSkinTestSpec(line.getMedicationPayload()));
        } else if (ProductTableNameHelper.isDevice(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, line.getRequestId(), operatorId));
            ChangeDeviceOrderDraftCommand changeDeviceCmd =
                doctorStationAdviceMapper.toChangeDeviceOrderDraftCommand(line, operatorId);
            // Pre：行级合同 id → bus_no 更新 contract_code，供列表回显费用性质
            this.applyDeviceContractCode(changeDeviceCmd.getItem(), this.extractLineContractId(line));
            deviceRequestDomainService.changeDeviceOrderDraft(changeDeviceCmd);
            createChargeIfPossible(request, line.getRequestId(), TableNameConstants.WKF_DEVICE_REQUEST,
                TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId(),
                line.getDevicePayload().getQuantity(), line.getDevicePayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId()),
                line.getDevicePayload().getDispenseLocationId(), null,
                accountId, orgId, operatorId);
        } else if (ProductTableNameHelper.isActivity(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, line.getRequestId(), operatorId));
            ProductDefinitionDisplayDto activityProductRow =
                loadSingleProduct(TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
            // Gate：UPDATE 亦禁嘱托（预选/改活动定义绕过目录，BR-ORD-032）
            String activityName = null;
            if (activityProductRow != null) {
                activityName = activityProductRow.getProductName();
            }
            String categoryCode = null;
            if (activityProductRow != null) {
                categoryCode = activityProductRow.getCategoryCode();
            }
            inpatientEntrustOrderGateSupport.assertNotEntrustForOutpatient(categoryCode, activityName);
            serviceRequestDomainService.modifyServiceRequestDraft(doctorStationAdviceMapper.toModifyServiceRequestDraftCommand(line, operatorId));
            createChargeIfPossible(request, line.getRequestId(), TableNameConstants.WKF_SERVICE_REQUEST,
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId(),
                ServiceAdviceDraftCommandHelper.requireQuantity(
                    line.getServicePayload().getQuantity(), line.getServicePayload().getChildOverridesJson()),
                ServiceAdviceDraftCommandHelper.requireUnitCode(
                    line.getServicePayload().getUnitCode(), line.getServicePayload().getChildOverridesJson(), activityProductRow),
                activityProductRow, null, null, accountId, orgId, operatorId);
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + line.getProductTableName());
        }
    }

    /**
     * 处理 DELETE 行——先作废未计费草稿费用，再物理/逻辑删除医嘱草稿（语义由域服务定义）。
     *
     * @param request    批次上下文
     * @param line       必须带 {@code requestId}，否则无法定位待删聚合
     * @param operatorId 操作人
     * @throws BusinessException {@code requestId} 为空、资源类型非法或域拒绝删除
     */
    private void handleDeleteLine(SaveDoctorStationAdviceDraftBatchRequest request, DoctorStationAdviceDraftLineRequest line, Long operatorId) {
        String pt = ProductTableNameHelper.normalize(line.getProductTableName());
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "DELETE 操作 requestId 不能为空");
        }
        if (ProductTableNameHelper.isMedication(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, line.getRequestId(), operatorId));
            // Cmd（BR-ORD-025）：删皮试药前先清理草稿态 companion 皮试检查 SR。
            doctorStationSkinTestSyncOrchestrator.removeDraftCompanionSkinTestServiceRequest(
                ChargePolicy.OUTPATIENT, request.getEncounterId(), line.getRequestId(), operatorId);
            medicationRequestDomainService.removeMedicationOrderDraft(
                doctorStationAdviceMapper.toRemoveMedicationOrderDraftCommand(line.getRequestId(), operatorId));
        } else if (ProductTableNameHelper.isDevice(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, line.getRequestId(), operatorId));
            deviceRequestDomainService.removeDeviceOrderDraft(
                doctorStationAdviceMapper.toRemoveDeviceOrderDraftCommand(line.getRequestId(), operatorId));
        } else if (ProductTableNameHelper.isActivity(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, line.getRequestId(), operatorId));
            serviceRequestDomainService.removeServiceRequestDraft(
                doctorStationAdviceMapper.toRemoveServiceRequestDraftCommand(line.getRequestId(), operatorId));
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + line.getProductTableName());
        }
    }

    /**
     * 在存在收费项定义「定价行」的前提下，为当前医嘱申请生成一笔就诊费用草稿。
     *
     * <p>定义主键为空时不生成费用（历史兼容）；定义主键非空时经
     * {@link ClinicalCatalogPriceResolver#resolveDraftChargePriceLine} 定价（诊疗 {@code SALE} 择优；
     * 药耗 {@code LOT_NUMBER_PRICE}，{@code clientResolvedDispenseLocationId} 非空时 FIFO 直接在该库位取批号、
     * 不再 match 取药科室模板），无命中则抛出 {@link BusinessException}，避免无锚点费用默默落库。</p>
     *
     * @param request        就诊与患者，写入费用项
     * @param requestId      医嘱申请主键，作为收费 service_id
     * @param requestTable   医嘱申请物理表名，与收费锚点一致
     * @param definitionTable 定价查询用的定义表（药品定义/活动定义/耗材定义）
     * @param definitionId   定义主键；为 {@code null} 时不查询定价、不生成费用
     * @param quantity       计价数量（与医嘱请求 quantity 同语义）
     * @param quantityUnit   计价单位 code
     * @param productRow     产品定义读模型
     * @param clientResolvedDispenseLocationId 目录 IF-DOR-11 已解析发药库位；药耗非空时写路径不再 match 模板
     * @param methodCode     给药途径（药品 FIFO 模板 calcScore）；诊疗/耗材可空
     * @param accountId      挂账账户
     * @param orgId          机构
     * @param operatorId     操作人
     * @throws BusinessException 定义主键已给出但无可用开立口径定价行时抛出（错误码 {@link ErrorCodeConstants#CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT}）
     */
    private void createChargeIfPossible(
        SaveDoctorStationAdviceDraftBatchRequest request,
        Long requestId,
        String requestTable,
        String definitionTable,
        Long definitionId,
        java.math.BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow,
        Long clientResolvedDispenseLocationId,
        String methodCode,
        Long accountId,
        Long orgId,
        Long operatorId
    ) {
        if (definitionId == null) {
            // 历史兼容：未重选定义时不挂费（UPDATE 行可能仅改数量/用法）
            return;
        }
        // Pre：按定义 + 目录已解析库位（clientResolvedDispenseLocationId）解析 LOT_NUMBER_PRICE / SALE
        ChargeItemDefinition priceLine = clinicalCatalogPriceResolver.resolveDraftChargePriceLine(
            definitionTable,
            definitionId,
            productRow,
            orgId,
            request.getEncounterId(),
            clientResolvedDispenseLocationId,
            methodCode
        );
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "未配置符合开立规则的收费定价（诊疗须零售价 SALE，药品/耗材须批号售价 LOT_NUMBER_PRICE）。请先维护收费项定价后再保存医嘱。");
        }
        // Cmd：落一笔未计费费用草稿，锚定当前医嘱 requestId
        chargeItemDomainService.createDraftChargeItemFromOrder(
            doctorStationAdviceMapper.toCreateChargeItemDraftFromOrder(
                request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                requestId, requestTable, definitionTable, definitionId, priceLine, quantity, quantityUnit, productRow));
    }

    /**
     * 按产品表与主键加载单条产品定义读模型，供诊疗开立前校验与展示。
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
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "未找到诊疗活动产品定义行");
        }
        return row;
    }

    /**
     * 签发医嘱批次（药品批次号、处方号、诊疗外呼占位）。
     *
     * <p>签发成功后，将本批次内各医嘱锚点下仍处于 DRAFT 的收费明细升为 PLANNED，以便门诊收费预结算通过
     *
     * @param request 就诊、患者、签发目标集合
     * @return 药品处方号映射
     * @throws BusinessException 门控失败、产品表名不支持，或域签发/费用状态迁移失败
     */
    public SignDoctorStationAdviceBatchResponse signDoctorStationAdviceBatch(SignDoctorStationAdviceBatchRequest request) {
        // 门控：加载门诊快照并校验患者上下文一致
        OutpatientEncounterContextQueryModel snapshot = EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // 解析当前操作人主键与机构上下文
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        // 签发默认挂账户：双账户优先医保、单户个人现金；药/耗材循环内再按行 contractId 覆盖
        Long defaultChargeAccountId = this.resolveAdviceChargeAccountId(request.getEncounterId(), null);
        // 收集药品医嘱与处方号映射，供前端展示
        Map<Long, String> rxMap = new HashMap<>();
        // Pre：本批药品签发目标主键（分方只带这些 DRAFT + 已 ACTIVE 未收费，不写其它未签发草稿）
        List<Long> signingMedicationRequestIds = new ArrayList<>();
        for (DoctorStationAdviceResourceKeyDto signTarget : request.getSignTargets()) {
            String productTable = ProductTableNameHelper.normalize(signTarget.getProductTableName());
            if (ProductTableNameHelper.isMedication(productTable) && signTarget.getRequestId() != null) {
                signingMedicationRequestIds.add(signTarget.getRequestId());
            }
        }
        // Pre（BR-ORD-023）：药品签发前对本批目标 ∪ 已签发未收费重分方，再逐条 signMedicationOrder
        Map<Long, String> splitPrescriptionNoByRequestId = Map.of();
        if (!signingMedicationRequestIds.isEmpty()) {
            splitPrescriptionNoByRequestId = outpatientPrescriptionSplitAssembler.splitAndAssignPrescriptions(
                request.getEncounterId(), orgId, operatorId, signingMedicationRequestIds);
        }
        // Pre（BR-ORD-025）：签发皮试药时自动扩 companion 皮试检查 SR 至待签发集合。
        List<DoctorStationAdviceResourceKeyDto> signTargets =
            this.fromSignTargetSpecs(doctorStationSkinTestSyncOrchestrator
                .expandSignTargetsWithLinkedSkinTestServiceRequests(this.toSignTargetSpecs(request.getSignTargets())));
        // Pre：批量预取本批药品列表模型（中药门禁）与 FIFO 上下文，减少循环 N+1；写路径仍逐条
        Map<Long, MedicationRequestListQueryModel> medicationListById =
            this.mapMedicationListModelsByIds(signingMedicationRequestIds);
        Map<Long, MedicationRequestFifoSignContext> medicationFifoById =
            this.orderRequestFifoSignContextLoader.loadMedicationFifoSignContextsOrThrow(signingMedicationRequestIds);
        // Pre：批内挂费读缓存——同药定价/产品展示只查一次，选价规则不变
        OutpatientMedicationSignChargeOrchestrator.SignChargeReadCache signChargeReadCache =
            new OutpatientMedicationSignChargeOrchestrator.SignChargeReadCache();
        Set<Long> medicationDefinitionIdsForCharge = new LinkedHashSet<>();
        for (MedicationRequestFifoSignContext fifoCtx : medicationFifoById.values()) {
            if (fifoCtx == null || fifoCtx.getMedicationId() == null) {
                continue;
            }
            medicationDefinitionIdsForCharge.add(fifoCtx.getMedicationId());
        }
        this.outpatientMedicationSignChargeOrchestrator.prefetchMedicationCatalogReads(
            signChargeReadCache, medicationDefinitionIdsForCharge);
        // Pre：本批耗材 FIFO 上下文一次装载，供挂费预取与循环复用（避免双查）
        Map<Long, DeviceRequestFifoSignContext> deviceFifoById = new HashMap<>();
        Set<Long> deviceDefinitionIdsForCharge = new LinkedHashSet<>();
        for (DoctorStationAdviceResourceKeyDto signTarget : signTargets) {
            String productTable = ProductTableNameHelper.normalize(signTarget.getProductTableName());
            if (!ProductTableNameHelper.isDevice(productTable) || signTarget.getRequestId() == null) {
                continue;
            }
            DeviceRequestFifoSignContext fifoDevCtx =
                this.orderRequestFifoSignContextLoader.loadDeviceFifoSignContextOrThrow(signTarget.getRequestId());
            deviceFifoById.put(signTarget.getRequestId(), fifoDevCtx);
            if (fifoDevCtx.getDeviceDefId() != null) {
                deviceDefinitionIdsForCharge.add(fifoDevCtx.getDeviceDefId());
            }
        }
        this.outpatientMedicationSignChargeOrchestrator.prefetchDeviceCatalogReads(
            signChargeReadCache, deviceDefinitionIdsForCharge);
        // Cmd：按目标类型签发；诊疗分支触发外呼网关占位
        for (DoctorStationAdviceResourceKeyDto t : signTargets) {
            String pt = ProductTableNameHelper.normalize(t.getProductTableName());
            if (ProductTableNameHelper.isMedication(pt)) {
                // Gate：中药饮片须在中医 Tab 签发（BR-TCM-ISO-001），避免与 IF-DOR-37 双轨冲突
                MedicationRequestListQueryModel medRow = medicationListById.get(t.getRequestId());
                if (medRow != null && Boolean.TRUE.equals(medRow.getTcmFlag())) {
                    throw new BusinessException(
                        ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "请在中医 Tab 签发");
                }
                String prescriptionNo = splitPrescriptionNoByRequestId.get(t.getRequestId());
                if (StringUtils.isEmpty(prescriptionNo)) {
                    throw new BusinessException(
                        ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "药品医嘱处方分方未生成处方号");
                }
                medicationRequestDomainService.signMedicationOrder(
                    doctorStationAdviceMapper.toSignMedicationOrderCommand(t.getRequestId(), prescriptionNo, operatorId));
                rxMap.put(t.getRequestId(), prescriptionNo);
                MedicationRequestFifoSignContext fifoMedCtx = medicationFifoById.get(t.getRequestId());
                if (fifoMedCtx == null) {
                    throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "药品申请不存在");
                }
                // Pre：分方后的处方号写回 FIFO 切片（装载早于 sign，上下文内原号可能为空）
                fifoMedCtx.setPrescriptionNo(prescriptionNo);
                // Pre：按药品行已落库 contractId 解析挂账，避免整批默认医保覆盖医生选自费
                Long medicationContractId = null;
                if (medRow != null) {
                    medicationContractId = medRow.getContractId();
                }
                Long medicationAccountId = this.resolveAdviceChargeAccountId(
                    request.getEncounterId(), medicationContractId);
                // Cmd：FIFO 发放 + 分段挂费 + PLANNED（与 IF-DOR-37 共用 Support；FIFO 仍逐条以保证库存占用正确）
                outpatientMedicationSignChargeOrchestrator.replaceFifoDispenseAndPlanChargesAfterMedicationSigned(
                    request.getEncounterId(), request.getPatientId(), medicationAccountId, orgId, operatorId,
                    t.getRequestId(), fifoMedCtx, signChargeReadCache);
            } else if (ProductTableNameHelper.isActivity(pt)) {
                // 诊疗：域 sign 后外呼仅占位，真实 LIS/PACS 路由待检查申请类型后替换
                serviceRequestDomainService.signServiceRequest(
                    doctorStationAdviceMapper.toSignServiceRequestCommand(t.getRequestId(), operatorId));
                dispatchLabOrImagingAdvice(request.getEncounterId(), request.getPatientId(), t.getRequestId());
                // 诊疗申请签发完成：检查/治疗类费用行进入可预结算态。
                chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, t.getRequestId());
            } else if (ProductTableNameHelper.isDevice(pt)) {
                // 耗材：签发后走 FIFO 分段发放与费用升位
                deviceRequestDomainService.signDeviceOrder(doctorStationAdviceMapper.toSignDeviceOrderCommand(t.getRequestId(), operatorId));
                // Pre：签发前读取草稿费用账户，保留医生所选费用性质（耗材实体无 contract_id）
                Long deviceAccountId = this.resolveDeviceDraftChargeAccountId(
                    request.getEncounterId(), t.getRequestId(), defaultChargeAccountId);
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, List.of(t.getRequestId()));
                DeviceRequestFifoSignContext fifoDevCtx = deviceFifoById.get(t.getRequestId());
                if (fifoDevCtx == null) {
                    fifoDevCtx = this.orderRequestFifoSignContextLoader.loadDeviceFifoSignContextOrThrow(t.getRequestId());
                }
                List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> devSegments = doctorStationAdviceFifoAllocationAssembler.allocateDeviceFifo(
                    orgId, fifoDevCtx.getEncounterId(), fifoDevCtx.getDeviceDefId(), fifoDevCtx.getCatalogCategoryCode(),
                    fifoDevCtx.getUnitCode(), fifoDevCtx.getQuantity());
                ReplaceFifoDeviceDispenseDraftsCommand fifoDevCmd = new ReplaceFifoDeviceDispenseDraftsCommand();
                fifoDevCmd.setOperatorId(operatorId);
                fifoDevCmd.setDeviceReqId(fifoDevCtx.getDeviceReqId());
                fifoDevCmd.setDeviceDefId(fifoDevCtx.getDeviceDefId());
                fifoDevCmd.setEncounterId(fifoDevCtx.getEncounterId());
                fifoDevCmd.setPatientId(fifoDevCtx.getPatientId());
                fifoDevCmd.setUnitCode(fifoDevCtx.getUnitCode());
                fifoDevCmd.setSegments(devSegments);
                List<Long> devDispenseIds = deviceDispenseDomainService.replaceFifoDraftDispensesAfterDoctorStationSign(fifoDevCmd);
                List<BigDecimal> devQtys = new ArrayList<>();
                List<String> devLotNumbers = new ArrayList<>();
                for (ReplaceFifoDeviceDispenseDraftsCommand.Segment seg : devSegments) {
                    // Pre：分段数量与 FIFO 批号等长传入挂费，禁止跨批号 priority 择优
                    devQtys.add(seg.getQuantity());
                    devLotNumbers.add(seg.getLotNumber());
                }
                // Pre：优先复用暂存时按费用性质解析的账户；无草稿费用时回落就诊默认账户
                this.outpatientMedicationSignChargeOrchestrator.createDispenseSegmentChargeDrafts(
                    request.getEncounterId(), request.getPatientId(), deviceAccountId, orgId, operatorId,
                    fifoDevCtx.getDeviceReqId(),
                    TableNameConstants.WKF_DEVICE_REQUEST,
                    TableNameConstants.ADM_DEVICE_DEFINITION,
                    fifoDevCtx.getDeviceDefId(),
                    TableNameConstants.WKF_DEVICE_DISPENSE,
                    devDispenseIds,
                    devQtys,
                    devLotNumbers,
                    fifoDevCtx.getUnitCode(),
                    null,
                    signChargeReadCache);
                chargeItemDomainService.planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_DISPENSE, devDispenseIds);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + t.getProductTableName());
            }
        }
        SignDoctorStationAdviceBatchResponse resp = new SignDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setPrescriptionNoByMedicationRequestId(rxMap);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 签退医嘱批次。
     *
     * <p>签退成功后，将各目标医嘱锚点下仍为 PLANNED 且未结算的收费明细回退为 DRAFT，与「医嘱未对外生效」语义一致，
     * 避免收费端继续对已签退医嘱发起预结算。</p>
     *
     * @param request 就诊、患者、签退目标
     * @return 成功应答
     * @throws BusinessException 门控失败、产品表名不支持，或域签退/费用状态回退失败
     */

    public WithdrawDoctorStationAdviceBatchResponse withdrawDoctorStationAdviceBatch(WithdrawDoctorStationAdviceBatchRequest request) {
        OutpatientEncounterContextQueryModel snapshot = EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        // Pre（BR-ORD-025）：同批勾选皮试药时 companion 将联动签退，显式诊疗目标去重避免二次签退
        List<Long> medicationRequestIds = new ArrayList<>();
        for (DoctorStationAdviceResourceKeyDto t : request.getTargets()) {
            if (t == null || t.getRequestId() == null) {
                continue;
            }
            String pt = ProductTableNameHelper.normalize(t.getProductTableName());
            if (ProductTableNameHelper.isMedication(pt)) {
                medicationRequestIds.add(t.getRequestId());
            }
        }
        Set<Long> autoWithdrawCompanionIds =
            doctorStationSkinTestSyncOrchestrator.collectCompanionSkinTestServiceRequestIds(medicationRequestIds);
        // 命令：按目标类型签退（与签发不对称处以保持域服务既有命令为准）
        for (DoctorStationAdviceResourceKeyDto t : request.getTargets()) {
            String pt = ProductTableNameHelper.normalize(t.getProductTableName());
            if (ProductTableNameHelper.isMedication(pt)) {
                List<Long> medDispenseIds = medicationDispenseQueryService.listMedicationDispenseIdsByMedicationRequest(t.getRequestId());
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, t.getRequestId());
                medicationRequestDomainService.signOffMedicationOrder(
                    doctorStationAdviceMapper.toSignOffMedicationOrderCommand(t.getRequestId(), operatorId));
                chargeItemDomainService.voidUnbilledDraftChargeItemsByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(t.getRequestId()));
                ReleaseDispenseReserveCommand releaseMed = new ReleaseDispenseReserveCommand();
                releaseMed.setMedReqId(t.getRequestId());
                releaseMed.setOperatorId(operatorId);
                medicationDispenseDomainService.releaseDispenseReserve(releaseMed);
                // Cmd（BR-ORD-025）：签退皮试药时对称处理 companion 皮试检查 SR。
                doctorStationSkinTestSyncOrchestrator.withdrawLinkedSkinTestServiceRequestIfPresent(
                    ChargePolicy.OUTPATIENT, request.getEncounterId(), t.getRequestId(), operatorId);
            } else if (ProductTableNameHelper.isActivity(pt)) {
                // 已由皮试药联动签退的 companion：本批跳过显式签退
                if (autoWithdrawCompanionIds.contains(t.getRequestId())) {
                    continue;
                }
                serviceRequestDomainService.withdrawSignedServiceRequest(
                    doctorStationAdviceMapper.toWithdrawServiceRequestCommand(t.getRequestId(), operatorId));
                // 诊疗签退：检查申请对应的可计费行收回为草稿。
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, t.getRequestId());
            } else if (ProductTableNameHelper.isDevice(pt)) {
                List<Long> devDispenseIds = deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(t.getRequestId());
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_DISPENSE, devDispenseIds);
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, t.getRequestId());
                deviceRequestDomainService.signOffDeviceOrder(
                    doctorStationAdviceMapper.toSignOffDeviceOrderCommand(t.getRequestId(), operatorId));
                chargeItemDomainService.voidUnbilledDraftChargeItemsByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_DISPENSE, devDispenseIds);
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, List.of(t.getRequestId()));
                // Cmd：按 WD① 解析出的发放主键释放库存预占（与药品 signOff 后始终 release 对称；优先逗号 id 列表，避免单键误解析）。
                ReleaseDeviceDispenseReserveCommand releaseDev = new ReleaseDeviceDispenseReserveCommand();
                releaseDev.setIdListOrRequestKey(buildDeviceDispenseReleaseFlexibleKey(devDispenseIds, t.getRequestId()));
                releaseDev.setOperatorId(operatorId);
                deviceDispenseDomainService.releaseDeviceDispenseReserve(releaseDev);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + t.getProductTableName());
            }
        }
        // 签退回滚 PLANNED 费用后作废仍挂载非 PLANNED 项的预结算草稿（GAP-STALE-001）。
        this.outpatientPaymentDraftLifecycleOrchestrator.cancelDraftsWithNonPlannedBoundCharges(request.getEncounterId());
        WithdrawDoctorStationAdviceBatchResponse resp = new WithdrawDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 更新药品医嘱组号（清空与成组两段命令）。
     *
     * @param request 就诊、患者、清空列表与成组指令
     * @return 成功应答
     * @throws BusinessException 门控失败
     */

    public UpdateDoctorStationMedicationAdviceGroupsResponse updateDoctorStationMedicationAdviceGroups(UpdateDoctorStationMedicationAdviceGroupsRequest request) {
        OutpatientEncounterContextQueryModel snapshot = EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        // 命令：先清空组号再按请求成组（两段独立域调用）
        if (request.getClearGroupIds() != null && !request.getClearGroupIds().isEmpty()) {
            medicationRequestDomainService.adjustMedicationOrderGroup(
                doctorStationAdviceMapper.toAdjustMedicationGroupCommand(request.getClearGroupIds(), null, operatorId));
        }
        if (request.getAssignGroup() != null && request.getAssignGroup().getMedicationRequestIds() != null) {
            medicationRequestDomainService.adjustMedicationOrderGroup(
                doctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                    request.getAssignGroup().getMedicationRequestIds(), request.getAssignGroup().getGroupId(), operatorId));
        }
        UpdateDoctorStationMedicationAdviceGroupsResponse resp = new UpdateDoctorStationMedicationAdviceGroupsResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * IF-DOR-18：加载启用态身体部位树形下拉（申请单检查行明细）。
     *
     * @return 仅含 ACTIVE 节点的 OptionDto 树
     */
    private List<OptionDto> loadBodyStructureOptions() {
        // Pre：读侧树模型按 busNo 已排序，再过滤为启用态供新申请引用
        return BodyStructureOptionAssembler.toActiveOptionTree(bodyStructureQueryService.getBodyStructureTree());
    }

    /**
     * IF-DOR-18：加载启用态样本定义下拉（含拼音/五笔，供申请单样本 filterable）。
     *
     * @return 排序后的样本 OptionDto 列表（上限 500）
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
     * 装配耗材签退释放预占用的灵活定位键：优先逗号分隔发放主键（IF-DOR-16 WD⑦），否则回落 deviceReqId。
     *
     * @param dispenseIds  本请求下关联发放主键（WD① 查询结果）
     * @param deviceReqId  耗材请求主键，无发放事实时作请求维度键
     * @return 传入 {@link ReleaseDeviceDispenseReserveCommand#setIdListOrRequestKey} 的定位串
     */
    private static String buildDeviceDispenseReleaseFlexibleKey(List<Long> dispenseIds, Long deviceReqId) {
        if (dispenseIds != null && !dispenseIds.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (Long dispenseId : dispenseIds) {
                if (dispenseId == null) {
                    continue;
                }
                if (!first) {
                    builder.append(",");
                }
                builder.append(dispenseId);
                first = false;
            }
            if (!builder.isEmpty()) {
                return builder.toString();
            }
        }
        if (deviceReqId != null) {
            return String.valueOf(deviceReqId);
        }
        return "";
    }

    /**
     * 按药品申请主键批量装载列表模型，供签发循环中药门禁复用（与逐条 getListModelById 同口径）。
     *
     * @param medicationRequestIds 药品申请主键集合
     * @return 申请主键 → 列表模型；未命中不进 Map
     */
    private Map<Long, MedicationRequestListQueryModel> mapMedicationListModelsByIds(
        Collection<Long> medicationRequestIds
    ) {
        Map<Long, MedicationRequestListQueryModel> byId = new HashMap<>();
        if (medicationRequestIds == null || medicationRequestIds.isEmpty()) {
            return byId;
        }
        List<MedicationRequestListQueryModel> rows =
            this.medicationRequestQueryService.listMedicationRequestListModelsByIds(medicationRequestIds);
        for (MedicationRequestListQueryModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            byId.put(row.getId(), row);
        }
        return byId;
    }

    /**
     * IF-DOR-15：签发诊疗后分别外发 LIS / PACS（BR-REQ-008）。
     */
    private void dispatchLabOrImagingAdvice(Long encounterId, Long patientId, Long requestId) {
        List<ServiceRequestDetailSummaryDto> summaries = loadAdviceApplyDetailSummaries(requestId);
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
     * 按诊疗请求 id 加载明细摘要（LIS/PACS 外发扩展共用读侧）。
     *
     * @param requestId 诊疗请求主键
     * @return 明细摘要 DTO 列表
     */
    private List<ServiceRequestDetailSummaryDto> loadAdviceApplyDetailSummaries(Long requestId) {
        Map<Long, List<ServiceRequestDetailListModel>> detailMap =
            serviceRequestDetailListAssembler.mapDetailListModelsByServiceRequestIds(List.of(requestId));
        return serviceApplyFormAssembler.toDetailSummaryDtos(
            ServiceApplyFormOrchestrator.toDetailSummaryModels(detailMap.get(requestId)));
    }

    /**
     * 明细摘要 → LIS 外发明细行（targetTable/targetId/展示名/remark）。
     *
     * @param summaries 诊疗明细摘要
     * @return LIS 扩展明细；空入参返回空列表
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
     * 明细摘要 → PACS 外发明细行（targetTable/targetId/展示名/remark）。
     *
     * @param summaries 诊疗明细摘要
     * @return PACS 扩展明细；空入参返回空列表
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
     * 将门诊皮试药草稿载荷映射为 orchestration 中性 Spec（边界转换，禁止 orchestration 依赖 application DTO）。
     *
     * @param payload 门诊皮试药草稿行载荷
     * @return 皮试同步 Spec；payload 为空时返回 null
     */
    private SkinTestMedicationDraftSpec toSkinTestSpec(SaveMedicationAdviceDraftLinePayload payload) {
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
    private List<AdviceSignTargetSpec> toSignTargetSpecs(List<DoctorStationAdviceResourceKeyDto> targets) {
        if (targets == null) {
            return List.of();
        }
        List<AdviceSignTargetSpec> specs = new ArrayList<>();
        for (DoctorStationAdviceResourceKeyDto target : targets) {
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
    private List<DoctorStationAdviceResourceKeyDto> fromSignTargetSpecs(List<AdviceSignTargetSpec> specs) {
        if (specs == null) {
            return List.of();
        }
        List<DoctorStationAdviceResourceKeyDto> targets = new ArrayList<>();
        for (AdviceSignTargetSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            DoctorStationAdviceResourceKeyDto target = new DoctorStationAdviceResourceKeyDto();
            target.setProductTableName(spec.getProductTableName());
            target.setRequestId(spec.getRequestId());
            targets.add(target);
        }
        return targets;
    }

    /**
     * 从医嘱草稿行提取费用性质合同主键（药品/诊疗/耗材载荷）。
     *
     * @param line 草稿行
     * @return 合同主键；未选时 null
     */
    private Long extractLineContractId(DoctorStationAdviceDraftLineRequest line) {
        if (line == null) {
            return null;
        }
        if (line.getMedicationPayload() != null && line.getMedicationPayload().getContractId() != null) {
            return line.getMedicationPayload().getContractId();
        }
        if (line.getServicePayload() != null && line.getServicePayload().getContractId() != null) {
            return line.getServicePayload().getContractId();
        }
        // 耗材：行级合同用于挂账账户解析，并落 contract_code（bus_no）供列表回显
        if (line.getDevicePayload() != null && line.getDevicePayload().getContractId() != null) {
            return line.getDevicePayload().getContractId();
        }
        return null;
    }

    /**
     * 将费用性质合同主键解析为 bus_no，写入耗材草稿命令的 {@code contractCode}。
     *
     * @param item       创建或修改耗材草稿项（可空则忽略）
     * @param contractId 合同主键；空则不写
     */
    private void applyDeviceContractCode(
        CreateDeviceOrderDraftCommand.CreateDeviceOrderDraftItemCommand item,
        Long contractId
    ) {
        if (item == null || contractId == null) {
            return;
        }
        ContractDetailQueryModel contract = contractQueryService.findContractById(contractId);
        if (contract == null || StringUtils.isEmpty(contract.getBusNo())) {
            return;
        }
        item.setContractCode(contract.getBusNo().trim());
    }

    /**
     * 将费用性质合同主键解析为 bus_no，写入耗材修改草稿命令的 {@code contractCode}。
     *
     * @param item       修改耗材草稿项（可空则忽略）
     * @param contractId 合同主键；空则不写
     */
    private void applyDeviceContractCode(
        ChangeDeviceOrderDraftCommand.ChangeDeviceOrderDraftItemCommand item,
        Long contractId
    ) {
        if (item == null || contractId == null) {
            return;
        }
        ContractDetailQueryModel contract = contractQueryService.findContractById(contractId);
        if (contract == null || StringUtils.isEmpty(contract.getBusNo())) {
            return;
        }
        item.setContractCode(contract.getBusNo().trim());
    }

    /**
     * 耗材签发前读取未计费草稿费用的挂账账户，保留暂存时费用性质选择。
     *
     * @param encounterId           就诊主键
     * @param deviceRequestId       耗材申请主键
     * @param defaultChargeAccountId 无草稿费用时的回落账户
     * @return 挂账账户主键
     */
    private Long resolveDeviceDraftChargeAccountId(
        Long encounterId,
        Long deviceRequestId,
        Long defaultChargeAccountId
    ) {
        if (encounterId == null || deviceRequestId == null) {
            return defaultChargeAccountId;
        }
        List<ChargeItem> draftCharges = chargeItemQueryService.listByEncounterAndServiceAnchors(
            encounterId, TableNameConstants.WKF_DEVICE_REQUEST, List.of(deviceRequestId));
        for (ChargeItem chargeItem : draftCharges) {
            if (chargeItem == null || chargeItem.getAccountId() == null) {
                continue;
            }
            String status = chargeItem.getStatusEnum();
            if (ChargeItemStatus.DRAFT.getCode().equals(status)
                || ChargeItemStatus.PLANNED.getCode().equals(status)) {
                return chargeItem.getAccountId();
            }
        }
        return defaultChargeAccountId;
    }

    /**
     * 按行合同解析挂账账户：有合同则匹配账户 contract_no；无合同双账户默认医保户。
     *
     * <p>已选自费合同时 {@code preferInsurance=false}，避免 contract_no 未精确命中时误挂医保户。</p>
     *
     * @param encounterId 就诊主键
     * @param contractId  行级合同主键；可空
     * @return 挂账账户主键
     */
    private Long resolveAdviceChargeAccountId(Long encounterId, Long contractId) {
        String contractBusNo = null;
        // 无行合同时双账户默认医保；有合同则按是否并行医保性质决定回落优先
        boolean preferInsurance = true;
        if (contractId != null) {
            ContractDetailQueryModel contract = contractQueryService.findContractById(contractId);
            if (contract != null) {
                if (contract.getBusNo() != null) {
                    contractBusNo = contract.getBusNo().trim();
                }
                preferInsurance = encounterChargeAccountOpener.needsParallelMedicalInsuranceAccount(
                    contract.getYbFlag(), contract.getCategoryEnum());
            }
        }
        return encounterChargeAccountOpener.resolveChargeAccountId(encounterId, contractBusNo, preferInsurance);
    }

}

