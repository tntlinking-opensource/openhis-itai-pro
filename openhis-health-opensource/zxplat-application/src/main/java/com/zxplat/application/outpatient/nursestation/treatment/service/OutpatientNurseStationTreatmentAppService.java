/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.treatment.service;

import com.zxplat.domain.share.constants.MedicationCommonConstants;
import com.zxplat.domain.share.constants.OutpatientNurseStationConstants;
import com.zxplat.application.shared.option.OptionDtoAssembler;
import com.zxplat.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.application.shared.medicaltech.DeviceSupplyTechVisibilitySupport;
import com.zxplat.application.shared.nursestation.DeviceAutoInventoryDeductSettingResolver;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.application.outpatient.nursestation.treatment.support.ProcedurePerformerDisplayAssembler;
import com.zxplat.application.outpatient.doctorstation.advice.support.ServiceApplyFormOrchestrator;
import com.zxplat.application.shared.nursestation.DeviceDispenseNurseWorkflowOrchestrator;
import com.zxplat.application.shared.nursestation.ProcedureExecutionPerformerOrchestrator;
import com.zxplat.application.outpatient.nursestation.treatment.support.AdviceChargeAnchorRollupHelper;
import com.zxplat.application.shared.advice.AdviceExtensionJsonParser;
import com.zxplat.application.shared.advice.AdviceExtensionJsonParser.AdvicePackageChildItemDto;
import com.zxplat.application.shared.advice.AdviceChargeDisplayStatusAssembler;
import com.zxplat.application.shared.advice.SpecimenTubeMergeBindResolveSupport;
import com.zxplat.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeGroupQueryModel;
import com.zxplat.application.shared.advice.AdviceChargeDisplayStatusAssembler.ListDisplayStatus;
import com.zxplat.application.shared.advice.ServiceAdviceDraftCommandHelper;
import com.zxplat.application.shared.advice.TcmMethodCodeDisplayAssembler;
import com.zxplat.application.shared.advice.OrderCatalogDisplayAssembler;
import com.zxplat.application.shared.advice.OrderRequestCatalogWarmAssembler;
import com.zxplat.application.shared.product.MedicationDosageDisplayAssembler;
import com.zxplat.application.shared.product.MedicationDosageDisplayAssembler.MedicationDosageDisplayResult;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.option.FrequencyOptionLoader;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.advice.OrderRequestCatalogWarmContext;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.application.outpatient.doctorstation.advice.assembler.ServiceApplyFormAssembler;
import com.zxplat.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import com.zxplat.application.outpatient.nursestation.treatment.dto.NurseStationTreatmentLineDto;
import com.zxplat.application.outpatient.nursestation.treatment.dto.OrderExecutionSheetItemDto;
import com.zxplat.application.outpatient.nursestation.treatment.mapper.OutpatientNurseStationTreatmentMapper;
import com.zxplat.application.outpatient.nursestation.treatment.request.BatchCancelDispositionRequest;
import com.zxplat.application.outpatient.nursestation.treatment.request.BatchPerformDispositionRequest;
import com.zxplat.application.outpatient.nursestation.treatment.request.ListManagedSupplyRoomOptionsRequest;
import com.zxplat.application.outpatient.nursestation.treatment.request.ListNetRemainingProcedureRecordsForNurseStationRequest;
import com.zxplat.application.outpatient.nursestation.treatment.request.ListProcedureRecordsForNurseStationRequest;
import com.zxplat.application.outpatient.nursestation.treatment.request.QueryEncounterTreatmentSheetRequest;
import com.zxplat.application.outpatient.nursestation.treatment.response.BatchCancelDispositionResponse;
import com.zxplat.application.outpatient.nursestation.treatment.response.BatchPerformDispositionResponse;
import com.zxplat.application.outpatient.nursestation.treatment.response.ListManagedSupplyRoomOptionsResponse;
import com.zxplat.application.outpatient.nursestation.treatment.response.ListNetRemainingProcedureRecordsForNurseStationResponse;
import com.zxplat.application.outpatient.nursestation.treatment.response.ListProcedureRecordsForNurseStationResponse;
import com.zxplat.application.outpatient.nursestation.treatment.response.QueryEncounterTreatmentSheetResponse;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.share.enums.OrganizationClassification;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.application.outpatient.nursestation.treatment.support.OrderExecutionSheetLoader;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureDetailQueryModel;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.clinical.procedure.service.ProcedureDomainService;
import com.zxplat.domain.clinical.procedure.support.ProcedurePerformCountGate;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.clinical.procedure.queries.OrderExecutionSheetPageQuery;
import com.zxplat.domain.clinical.procedure.queries.model.OrderExecutionSheetQueryModel;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.application.outpatient.nursestation.skintest.support.NurseStationOutpatientTcmExecuteExcludeHelper;
import com.zxplat.application.outpatient.nursestation.skintest.support.NurseStationSkinTestExecuteExcludeHelper;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.application.shared.advice.ServiceRequestDetailListAssembler;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊护士站处置应用编排：处置单三列表、执行记录抽屉、净剩可取消列表、批量处置执行与批量取消执行（IF-NUR-02～05、IF-NUR-03B）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link EncounterQueryService}：IF-NUR-02～05 就诊存在性门闸。</li>
 *   <li>{@link ServiceRequestQueryService} / {@link MedicationRequestQueryService} / {@link DeviceRequestQueryService}：IF-NUR-02 列表装载与 IF-NUR-04 次数门控只读输入。</li>
 *   <li>{@link ChargeItemQueryService}：IF-NUR-02 计费聚合与 IF-NUR-04 已计费门控只读输入。</li>
 *   <li>{@link ProcedureQueryService}：IF-NUR-03 医嘱执行单分页与 IF-NUR-02 次数统计、IF-NUR-04/05 门控输入。</li>
 *   <li>{@link ProcedureDomainService}：IF-NUR-04/05 执行登记/取消写路径。</li>
     *   <li>{@link DeviceDispenseQueryService} / {@link DeviceDispenseNurseWorkflowOrchestrator} / {@link InventoryItemDomainService}：IF-NUR-02 发放态装载与 IF-NUR-04/05 耗材写路径。</li>
 *   <li>{@link OrderRequestCatalogWarmAssembler}：IF-NUR-02 目录定义与类目术语批量预热。</li>
 *   <li>{@link TerminologyOptionLoader} / {@link FrequencyOptionLoader} / {@link ProductDefinitionDisplayTerminologyAssembler}：IF-NUR-02 药品行用法/频次/单位 code+展示名装配（对齐 IF-DOR-10 **BR-ORD-022**）。</li>
 *   <li>{@link OutpatientNurseStationTreatmentMapper}：Request→Query/Command 与查询模型/DTO→Response 字段搬运。</li>
 * </ul>
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 动态注册为 {@code POST /api/app/outpatientNurseStationTreatment/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientNurseStationTreatmentAppService {

    /** 就诊只读查询：IF-NUR-02～05 就诊存在性门闸。 */
    private final EncounterQueryService encounterQueryService;

    /** 皮试检查活动定义解析：执行 Tab 与皮试 Tab 互斥过滤。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * 诊疗服务申请只读查询：IF-NUR-02 诊疗列表与 IF-NUR-04 计划次数门控。
     */
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestDetailListAssembler serviceRequestDetailListAssembler;

    /**
     * 药品申请只读查询：IF-NUR-02 药品列表与 IF-NUR-04 计划次数门控。
     */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * 药品发放只读查询：IF-NUR-02 收费行发药锚点 → 药品申请 id 映射。
     */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 耗材请求只读查询：IF-NUR-02 耗材列表装载。
     */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /**
     * 耗材发放只读查询：IF-NUR-02 发放态/主发放行定位与 IF-NUR-04 待发门控。
     */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /**
     * 收费明细只读查询：IF-NUR-02 计费聚合与 IF-NUR-04 已计费门控。
     */
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 执行记录只读查询：IF-NUR-02 诊疗次数统计与 IF-NUR-04 净次数门控。
     */
    private final ProcedureQueryService procedureQueryService;

    /** 医嘱执行单 Procedure 主表分页（展示列 enrich 在 AppService，不经 JSON 快照）。 */
    private final OrderExecutionSheetLoader orderExecutionSheetLoader;

    /**
     * 执行记录写服务：IF-NUR-04/05 诊疗/药品执行登记与取消。
     */
    private final ProcedureDomainService procedureDomainService;
    private final ProcedurePerformCountGate procedurePerformCountGate;

    /**
     * 库存项写服务：IF-NUR-04/05 合并扣减/回补（允许空映射短路）。
     */
    private final InventoryItemDomainService inventoryItemDomainService;

    /**
     * 护士站装配器：DTO/命令字段搬运。
     */
    private final OutpatientNurseStationTreatmentMapper outpatientNurseStationTreatmentMapper;

    /**
     * IF-NUR-02 处置单目录与类目展示批量预热（module.common 复用）。
     */
    private final OrderRequestCatalogWarmAssembler orderRequestCatalogWarmAssembler;

    /**
     * IF-NUR-02 药品行用法/频次/单位术语展示预热。
     */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * IF-NUR-02 药品行频次展示预热。
     */
    private final FrequencyOptionLoader frequencyOptionLoader;

    /**
     * IF-NUR-02 药品行单位展示回落（产品字典文案）。
     */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 标本合管设备反查（采血贴分组 enrich）。 */
    private final SpecimenTubeMergeBindResolveSupport specimenTubeMergeBindResolveSupport;

    /** 「耗材是否自动扣库」开关读取。 */
    private final DeviceAutoInventoryDeductSettingResolver deviceAutoInventoryDeductSettingResolver;

    /** 护士站耗材发放编排（已配药 / 就地改库房后已发药 + 按签发批号扣库）。 */
    private final DeviceDispenseNurseWorkflowOrchestrator deviceDispenseNurseWorkflowOrchestrator;

    /** 写路径：Procedure 完成后登记主执行人（cln_procedure_performer，1:1）。 */
    private final ProcedureExecutionPerformerOrchestrator procedureExecutionPerformerOrchestrator;

    /** 当前登录医务人员解析（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 登录者管辖库房/药房解析（BR-NUR-006 耗材房 Gate 与下拉同源）。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;

    /** 登录工作科室（写入 Procedure.org_id）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /** 执行参与人展示名批量回填。 */
    private final ProcedurePerformerDisplayAssembler procedurePerformerDisplayAssembler;

    /**
     * 申请单明细摘要 DTO 装配（IF-DOR-10 / IF-NUR-02 **BR-NUR-016**，复用医生站 Assembler）。
     */
    private final ServiceApplyFormAssembler serviceApplyFormAssembler;

    /** 组织只读：处置单排除 TECH 执行科室。 */
    private final OrganizationQueryService organizationQueryService;

    /** 位置只读：耗材发放库位→组织，排除 TECH。 */
    private final LocationQueryService locationQueryService;

    /** 耗材 TECH 可见性轴：挂靠科室及祖先，护士站排除医技房。 */
    private final DeviceSupplyTechVisibilitySupport deviceSupplyTechVisibilitySupport;

    /**
     * IF-NUR-02：按就诊查询处置单（三列表同构）。
     *
     * @param request 就诊主键与诊疗类目过滤条件
     * @return 三列表处置行
     * @throws BusinessException 就诊不存在或查询失败（EX-NUR-102 语义）
     */
    public QueryEncounterTreatmentSheetResponse queryEncounterTreatmentSheet(QueryEncounterTreatmentSheetRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        // Gate：就诊主键必须显式传入，避免护士在未知就诊上下文加载处置单。
        // Pre：强制加载就诊详情，作为后续患者锚点一致性校验输入（不作为写前唯一性真源）。
        EncounterDetailQueryModel encounter = this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        // Pre：装载收费明细全集，用于三列表行级计费聚合（Q-ORD-006）。
        ChargeItemQueryCommand chargeCmd = this.outpatientNurseStationTreatmentMapper.toChargeItemQueryCommand(request.getEncounterId());
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByEncounterId(chargeCmd);
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor =
            AdviceChargeAnchorRollupHelper.buildRequestAnchorChargeIndex(
                chargeItems, this.medicationDispenseQueryService, this.deviceDispenseQueryService);
        // Pre：装载诊疗/药品/耗材请求列表，并在应用层过滤已完成态（护士站处置视图口径）。
        List<ServiceRequestListModel> serviceRequests =
            this.serviceRequestQueryService.listServiceRequestsByEncounter(request.getEncounterId());
        List<MedicationRequestListQueryModel> medicationRequests =
            this.medicationRequestQueryService.listMedicationRequestsByEncounter(request.getEncounterId());
        List<DeviceRequestListModel> deviceRequests =
            this.deviceRequestQueryService.listDeviceRequestsByEncounter(request.getEncounterId());
        List<DeviceDispenseListModel> deviceDispenses =
            this.deviceDispenseQueryService.listDeviceDispensesByEncounter(request.getEncounterId());
        List<Long> deviceReqIds = new ArrayList<>();
        for (DeviceRequestListModel dr : deviceRequests) {
            if (dr.getId() != null) {
                deviceReqIds.add(dr.getId());
            }
        }
        // Step：耗材医嘱→发放单 id 列表一次 IN 查询后内存分组（对齐 A.9.1，禁止按医嘱循环查库）
        Map<Long, List<Long>> deviceDispenseIdsByDeviceRequest =
            this.deviceDispenseQueryService.mapDeviceDispenseIdsByDeviceRequestIds(deviceReqIds);
        // Pre：批量预热诊疗/药品/耗材定义与类目术语，供三列表 itemName 与 medCategoryCodeText 等展示列装配（Q-ORD-006 §3.4.3）。
        OrderRequestCatalogWarmContext catalogContext =
            this.orderRequestCatalogWarmAssembler.prepareFromTreatmentSheetRows(
                serviceRequests, medicationRequests, deviceRequests);
        // Pre：药品行用法/频次/剂量与总量单位术语及产品字典回落文案（IF-NUR-02 步骤 8b，对齐 IF-DOR-10 BR-ORD-022）。
        NurseStationMedicationDisplayWarmContext medicationDisplayWarm =
            this.prepareMedicationDisplayWarm(medicationRequests);
        // Pre：申请单行（AR 前缀）批量预取 Detail，供列表回填部位/样本摘要（BR-NUR-016，对齐 IF-DOR-10）。
        List<Long> applyFormServiceRequestIds = new ArrayList<>();
        for (ServiceRequestListModel serviceRow : serviceRequests) {
            if (serviceRow.getId() != null
                && ServiceApplyFormOrchestrator.isRequisitionApplicationPrescriptionNo(
                    serviceRow.getPrescriptionNo())) {
                applyFormServiceRequestIds.add(serviceRow.getId());
            }
        }
        Map<Long, List<ServiceRequestDetailListModel>> applyFormDetailsByRequestId =
            this.serviceRequestDetailListAssembler.mapDetailListModelsByServiceRequestIds(applyFormServiceRequestIds);
        Long skinTestActivityId = this.activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        LinkedHashSet<Long> serviceActivityIds = new LinkedHashSet<>();
        for (ServiceRequestListModel serviceRow : serviceRequests) {
            if (serviceRow != null && serviceRow.getActivityId() != null) {
                serviceActivityIds.add(serviceRow.getActivityId());
            }
        }
        Map<Long, SpecimenTubeMergeGroupQueryModel> mergeGroupRefByActivityId =
            specimenTubeMergeBindResolveSupport.mapMergeGroupRefByActivityIds(serviceActivityIds);
        Map<Long, List<AdvicePackageChildItemDto>> packageChildrenByActivityId =
            this.loadPackageChildrenByActivityId(serviceRequests);
        // Pre：批量装载执行科室组织。TECH 诊疗不进护士站，检验（22）除外，仅供下方采血贴医技列表
        Set<Long> serviceOrgIds = new LinkedHashSet<>();
        for (ServiceRequestListModel serviceRow : serviceRequests) {
            if (serviceRow != null && serviceRow.getOrgId() != null) {
                serviceOrgIds.add(serviceRow.getOrgId());
            }
        }
        Map<Long, Organization> serviceOrgById = this.organizationQueryService.mapByIds(serviceOrgIds);
        // Pre：有效库位=请求库位，空则回落主发放库位（院点请求常不写 location）
        Set<Long> deviceRequestIdsForLoc = new LinkedHashSet<>();
        for (DeviceRequestListModel deviceRow : deviceRequests) {
            if (deviceRow != null && deviceRow.getId() != null) {
                deviceRequestIdsForLoc.add(deviceRow.getId());
            }
        }
        Map<Long, Long> primaryDispenseLocationByReqId =
            this.deviceDispenseQueryService.mapDeviceRequestToPrimaryDispenseLocationId(deviceRequestIdsForLoc);
        Set<Long> deviceLocationIds = new LinkedHashSet<>();
        for (DeviceRequestListModel deviceRow : deviceRequests) {
            if (deviceRow == null) {
                continue;
            }
            Long effectiveLocId = this.deviceSupplyTechVisibilitySupport.resolveEffectiveDeviceLocationId(
                deviceRow.getLocationId(), deviceRow.getId(), primaryDispenseLocationByReqId);
            if (effectiveLocId != null) {
                deviceLocationIds.add(effectiveLocId);
            }
        }
        Map<Long, Location> deviceLocationById =
            this.locationQueryService.loadLocationEntityMapByIds(deviceLocationIds);
        Set<Long> deviceOrgIds = new LinkedHashSet<>();
        for (Location location : deviceLocationById.values()) {
            if (location != null && location.getOrganizationId() != null) {
                deviceOrgIds.add(location.getOrganizationId());
            }
        }
        Map<Long, Organization> deviceOrgById =
            this.deviceSupplyTechVisibilitySupport.mapOrganizationsWithAncestors(deviceOrgIds);
        List<NurseStationTreatmentLineDto> serviceLines = new ArrayList<>();
        Map<NurseStationTreatmentLineDto, Long> latestCompletedProcedureByLine = new HashMap<>();
        for (ServiceRequestListModel row : serviceRequests) {
            if (NurseStationSkinTestExecuteExcludeHelper.shouldExcludeServiceFromNurseStationExecuteList(
                skinTestActivityId, row)) {
                continue;
            }
            // 业务：TECH 由医技站执行；真实检验（22、非采血费）仍返回供采血贴；采血费不进医技列表
            if (this.isTechOrganization(row.getOrgId(), serviceOrgById)
                && !this.isLabProofServiceRequest(row, catalogContext)) {
                continue;
            }
            if (AdviceChargeDisplayStatusAssembler.isRefundOrderCopyRequest(row.getRefundServiceId())) {
                continue;
            }
            String serviceChargeStatus = this.resolveAggregatedChargeStatus(
                TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), chargeByAnchor);
            if (!AdviceChargeDisplayStatusAssembler.shouldIncludeNurseStationTreatmentSheetRow(
                row.getStatusEnum(), TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), serviceChargeStatus)) {
                continue;
            }
            if (StringUtils.isNotEmpty(request.getServiceCategoryCode())) {
                if (row.getTherapyEnum() == null) {
                    continue;
                }
                if (!request.getServiceCategoryCode().trim().equals(row.getTherapyEnum().trim())) {
                    continue;
                }
            }
            NurseStationTreatmentLineDto line =
                this.buildServiceLine(
                    row,
                    request.getEncounterId(),
                    chargeByAnchor,
                    catalogContext,
                    mergeGroupRefByActivityId,
                    medicationDisplayWarm,
                    packageChildrenByActivityId);
            this.applyServiceApplyFormLineDisplay(line, row, applyFormDetailsByRequestId);
            List<ProcedureRecordQueryModel> procedures = this.procedureQueryService.getProcedureRecords(
                row.getId(), TableNameConstants.WKF_SERVICE_REQUEST);
            // Pre：执行科室组织一次装载；护士次数按 org 类型隔离医技（与写 Procedure.orgId=登录科同轴）
            Map<Long, Organization> procedureOrgById = this.mapOrganizationsForProcedureRecords(procedures);
            int completed = 0;
            int aborted = 0;
            for (ProcedureRecordQueryModel p : procedures) {
                // 业务：仅护士侧（空 org 或非 TECH）；医技站写 TECH org，不计入护士已执行次数
                if (!this.isNurseSideProcedureRecord(p, procedureOrgById)) {
                    continue;
                }
                if (EventStatus.COMPLETED.getCode().equals(p.getStatusEnum())) {
                    completed++;
                }
                if (EventStatus.isCancelExecutionStatusCode(p.getStatusEnum())) {
                    aborted++;
                }
            }
            line.setPerformCount(Integer.valueOf(completed));
            line.setCancelCount(Integer.valueOf(aborted));
            ProcedureRecordQueryModel latestCompleted =
                this.pickLatestNetRemainingCompletedProcedureNurseSide(procedures, procedureOrgById);
            if (latestCompleted != null && latestCompleted.getId() != null) {
                latestCompletedProcedureByLine.put(line, latestCompleted.getId());
            }
            this.finalizeNurseTreatmentLineDisplay(line);
            serviceLines.add(line);
        }
        List<NurseStationTreatmentLineDto> medicationLines = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRequests) {
            if (NurseStationSkinTestExecuteExcludeHelper.shouldExcludeMedicationFromNurseStationExecuteList(row)) {
                continue;
            }
            if (NurseStationOutpatientTcmExecuteExcludeHelper.shouldExcludeMedicationFromOutpatientNurseStationExecuteList(row)) {
                continue;
            }
            if (AdviceChargeDisplayStatusAssembler.isRefundOrderCopyRequest(row.getRefundMedicineId())) {
                continue;
            }
            String medicationChargeStatus = this.resolveAggregatedChargeStatus(
                TableNameConstants.MED_MEDICATION_REQUEST, row.getId(), chargeByAnchor);
            if (!AdviceChargeDisplayStatusAssembler.shouldIncludeNurseStationTreatmentSheetRow(
                row.getStatusEnum(), TableNameConstants.MED_MEDICATION_REQUEST, row.getId(), medicationChargeStatus)) {
                continue;
            }
            NurseStationTreatmentLineDto medicationLine =
                this.buildMedicationLine(row, request.getEncounterId(), chargeByAnchor, catalogContext, medicationDisplayWarm);
            List<ProcedureRecordQueryModel> medicationProcedures = this.procedureQueryService.getProcedureRecords(
                row.getId(), TableNameConstants.MED_MEDICATION_REQUEST);
            ProcedureRecordQueryModel latestMedicationCompleted =
                this.pickLatestNetRemainingCompletedProcedure(medicationProcedures);
            if (latestMedicationCompleted != null && latestMedicationCompleted.getId() != null) {
                latestCompletedProcedureByLine.put(medicationLine, latestMedicationCompleted.getId());
            }
            this.finalizeNurseTreatmentLineDisplay(medicationLine);
            medicationLines.add(medicationLine);
        }
        List<NurseStationTreatmentLineDto> deviceLines = new ArrayList<>();
        for (DeviceRequestListModel row : deviceRequests) {
            // 退单镜像（负向复制行）不进侧栏
            if (AdviceChargeDisplayStatusAssembler.isRefundOrderCopyRequest(row.getRefundDeviceId())) {
                continue;
            }
            // 业务：有效库位（请求或主发放）挂靠 TECH 时由医技站发放，护士站侧栏跳过
            Long effectiveDeviceLocationId =
                this.deviceSupplyTechVisibilitySupport.resolveEffectiveDeviceLocationId(
                    row.getLocationId(), row.getId(), primaryDispenseLocationByReqId);
            if (this.deviceSupplyTechVisibilitySupport.isTechDeviceLocation(
                effectiveDeviceLocationId, deviceLocationById, deviceOrgById)) {
                continue;
            }
            String deviceChargeStatus = this.resolveAggregatedChargeStatus(
                TableNameConstants.WKF_DEVICE_REQUEST, row.getId(), chargeByAnchor);
            // 与中区药品一致：退费中/已退费仍返回，由前端展示「待退费/已退费」且不可勾选（BR-NUR-018）
            if (!AdviceChargeDisplayStatusAssembler.shouldIncludeNurseStationTreatmentSheetRow(
                row.getStatusEnum(), TableNameConstants.WKF_DEVICE_REQUEST, row.getId(), deviceChargeStatus)) {
                continue;
            }
            NurseStationTreatmentLineDto deviceLine = this.buildDeviceLine(
                row,
                request.getEncounterId(),
                chargeByAnchor,
                deviceDispenses,
                deviceDispenseIdsByDeviceRequest,
                catalogContext,
                medicationDisplayWarm);
            // 有效计费下的作废发放勿进侧栏；退费收费态下保留行以便展示待退费/已退费
            if (AdviceChargeDisplayStatusAssembler.isActiveBillableChargeStatus(deviceChargeStatus)
                && this.isExcludedNurseStationDeviceSupplyDispenseStatus(deviceLine.getDispenseStatusEnum())) {
                continue;
            }
            this.finalizeNurseTreatmentLineDisplay(deviceLine);
            deviceLines.add(deviceLine);
        }
        // Asm：批量回填三列表最近主执行人展示名（cln_procedure_performer → lastPerformerDisplay）。
        this.applyLastPerformerDisplayFromProcedureIds(latestCompletedProcedureByLine);
        // Asm：诊疗列表按申请单号聚邻，便于护士识别同单多项目（IF-NUR-02 BR-NUR-015）。
        this.sortServiceLinesForApplyFormGrouping(serviceLines);
        // Asm：将三列表与就诊头信息包装为统一响应（encounter 头信息字段由前端自行使用 encounterId 二次查询时可省略）。
        return this.outpatientNurseStationTreatmentMapper.toQueryEncounterTreatmentSheetResponse(
            request, encounter, serviceLines, medicationLines, deviceLines);
    }

    /**
     * 加载当前登录者管辖的启用态药房，供耗材房下拉使用（BR-NUR-006）。
     *
     * @param request 无业务字段；管辖范围由 {@link CurrentManagedLocationIdsResolver} 解析
     * @return 耗材房下拉选项
     */
    public ListManagedSupplyRoomOptionsResponse listManagedSupplyRoomOptions(ListManagedSupplyRoomOptionsRequest request) {
        this.currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未登录或会话无效，无法加载耗材房选项");
        List<Location> pharmacies = this.managedLocationIdsResolver.listManagedActivePharmaciesOrEmpty();
        List<OptionDto> options = OptionDtoAssembler.toOptions(pharmacies, Location::getId, Location::getName, 0L);
        ListManagedSupplyRoomOptionsResponse response = new ListManagedSupplyRoomOptionsResponse();
        response.setSupplyRoomOptions(options);
        return response;
    }

    /**
     * IF-NUR-03：分页查询诊疗执行记录（执行记录抽屉）。
     *
     * @param request 就诊收敛与分页筛选条件
     * @return 执行记录分页结果
     * @throws BusinessException 就诊不存在或查询失败（EX-NUR-103 语义）
     */
    public ListProcedureRecordsForNurseStationResponse listProcedureRecordsForNurseStation(
        ListProcedureRecordsForNurseStationRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        // Gate：就诊主键必须显式传入，避免跨就诊误查执行记录抽屉数据。
        // Pre：强制加载就诊，保证护士当前上下文与执行单筛选 encounterId 同源。
        this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        // Pre：将抽屉请求映射为读模型分页查询（pageNo 1-based 与 sort 口径由 OrderExecutionSheetPageQuery 继承 AbstractPageQuery）。
        OrderExecutionSheetPageQuery pageQuery =
            this.outpatientNurseStationTreatmentMapper.toOrderExecutionSheetPageQuery(request);
        Page<OrderExecutionSheetQueryModel> page =
            this.orderExecutionSheetLoader.pageOrderExecutionSheet(pageQuery);
        // Asm：装配抽屉响应并回填项目名、操作文案与操作人展示列（IF-NUR-03 禁止前端解析 JSON）。
        ListProcedureRecordsForNurseStationResponse response =
            this.outpatientNurseStationTreatmentMapper.toListProcedureRecordsForNurseStationResponse(page);
        this.enrichExecutionSheetDrawerDisplay(request.getEncounterId(), response.getContent());
        return response;
    }

    /**
     * IF-NUR-03B：查询净剩可取消的已完成执行记录（对冲后仍有效的 COMPLETED 行）。
     *
     * <p>已存在 {@code CANCEL.refund_id} 指向的 COMPLETED 与对应 CANCEL 均不返回，供取消抽屉勾选。</p>
     *
     * @param request 就诊锚点与可选来源请求缩窄条件
     * @return 净剩执行记录列表
     * @throws BusinessException 就诊不存在或缩窄参数不合法
     */
    public ListNetRemainingProcedureRecordsForNurseStationResponse listNetRemainingProcedureRecordsForNurseStation(
        ListNetRemainingProcedureRecordsForNurseStationRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        if (request.getRequestId() != null && StringUtils.isEmpty(request.getRequestTable())) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "缩窄来源请求时 requestTable 不能为空");
        }
        // Pre：就诊存在，保证列表与取消操作同属一次就诊上下文。
        this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        List<ProcedureRecordQueryModel> netRows = this.procedureQueryService.listNetRemainingCompletedProcedureRecords(
            request.getEncounterId(),
            request.getRequestId(),
            request.getRequestTable());
        // Asm：按记录时间、主键倒序，便于护士优先看到最近执行。
        netRows.sort(this.netRemainingProcedureRecordComparator());
        return this.outpatientNurseStationTreatmentMapper.toListNetRemainingProcedureRecordsForNurseStationResponse(netRows);
    }

    /**
     * IF-NUR-04：批量处置执行（诊疗/药品登记与耗材发放）。
     *
     * @param request 批量勾选与操作人上下文
     * @return 成功标记与各路径实际完成主键集合
     * @throws BusinessException 门控失败、未计费、次数已满、耗材房缺失或领域写失败（EX-NUR-201～208 语义）
     */
    public BatchPerformDispositionResponse batchPerformDisposition(BatchPerformDispositionRequest request) {
        // 门禁校验：请求参数与勾选项目组合合法性
        this.validateBatchPerformDispositionGate(request);
        // Pre：强制加载就诊，保证批量执行锚点就诊一致且存在。
        EncounterDetailQueryModel encounter = this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        // 加载收费明细用于后续已计费门控
        ChargeItemQueryCommand chargeCmd = this.outpatientNurseStationTreatmentMapper.toChargeItemQueryCommand(request.getEncounterId());
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByEncounterId(chargeCmd);
        // 过滤已计费诊疗与药品申请：仅保留存在已结算收费项的请求
        List<Long> retainedServiceIds = this.retainBillableRequestIds(request.getServiceRequestIds(), TableNameConstants.WKF_SERVICE_REQUEST, chargeItems);
        List<Long> retainedMedicationIds = this.retainBillableRequestIds(request.getMedicationRequestIds(), TableNameConstants.MED_MEDICATION_REQUEST, chargeItems);
        Long skinTestActivityId = this.activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        // 判断是否存在耗材执行项
        boolean hasDevice = request.getDeviceDispenseIds() != null && !request.getDeviceDispenseIds().isEmpty();
        // Gate：退费中/已退费耗材禁止再发放（对称 BR-NUR-018，避免侧栏脏勾选仍能执行）
        List<Long> retainedDeviceDispenseIds = Collections.emptyList();
        if (hasDevice) {
            retainedDeviceDispenseIds = this.retainBillableDeviceDispenseIds(
                request.getDeviceDispenseIds(), chargeItems);
        }
        // 自动扣库时：护士可换耗材房（只改 location）；须有签发批号；目标房该批库存由编排断言
        if (!retainedDeviceDispenseIds.isEmpty() && this.deviceAutoInventoryDeductSettingResolver.isEnabled()) {
            this.deviceDispenseNurseWorkflowOrchestrator.validateOutpatientAutoDeductInventoryLocation(
                retainedDeviceDispenseIds,
                request.getInventoryLocationId());
            // Gate：护士指定房须落在登录者管辖药房白名单（与下拉同源）
            if (request.getInventoryLocationId() != null) {
                this.assertInventoryLocationInManagedPharmacies(request.getInventoryLocationId());
            }
        }
        // 无任何可执行项目时阻断流程（含：仅勾选了已退费耗材）
        if (retainedServiceIds.isEmpty() && retainedMedicationIds.isEmpty() && retainedDeviceDispenseIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "所选项目无已计费收费项");
        }
        List<Long> performedServiceIds = new ArrayList<>();
        List<Long> performedMedicationIds = new ArrayList<>();
        List<Long> completedDispenseIds = new ArrayList<>();
        Map<Long, ServiceRequestListModel> serviceRowById = new HashMap<>();
        for (ServiceRequestListModel row : this.serviceRequestQueryService.listServiceRequestsByEncounter(request.getEncounterId())) {
            if (row.getId() != null) {
                serviceRowById.put(row.getId(), row);
            }
        }
        Map<Long, MedicationRequestListQueryModel> medicationRowById = new HashMap<>();
        for (MedicationRequestListQueryModel row : this.medicationRequestQueryService.listMedicationRequestsByEncounter(request.getEncounterId())) {
            if (row.getId() != null) {
                medicationRowById.put(row.getId(), row);
            }
        }
        // Pre：操作人与执行人同口径（adm_practitioner.id）；请求未传则取当前登录医务人员（B.6.5）。
        Long practitionerId = this.resolveNurseStationPractitionerId(request.getOperatorId());
        // Pre：登录工作科室写入本批 Procedure.org_id（须非 TECH，与次数统计分侧同轴）
        Long loginWorkOrgId = this.requireLoginNurseSideWorkOrgId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT);
        // Pre：批量执行前整批校验净次数（GAP-EXE-006，满额整批失败）。
        for (Long serviceRequestId : retainedServiceIds) {
            ServiceRequestListModel listRow = serviceRowById.get(serviceRequestId);
            if (NurseStationSkinTestExecuteExcludeHelper.shouldExcludeServiceFromNurseStationExecuteList(
                skinTestActivityId, listRow)) {
                continue;
            }
            int planned = this.resolvePlannedServicePerformCount(listRow);
            this.procedurePerformCountGate.assertPerformCountBelowPlanned(
                serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST, planned, loginWorkOrgId, true);
        }
        for (Long medicationRequestId : retainedMedicationIds) {
            MedicationRequestListQueryModel listRow = medicationRowById.get(medicationRequestId);
            if (NurseStationSkinTestExecuteExcludeHelper.shouldExcludeMedicationFromNurseStationExecuteList(listRow)) {
                continue;
            }
            if (NurseStationOutpatientTcmExecuteExcludeHelper.shouldExcludeMedicationFromOutpatientNurseStationExecuteList(listRow)) {
                continue;
            }
            MedicationRequestDetailQueryModel med = this.medicationRequestQueryService.loadMedicationRequestById(medicationRequestId);
            int planned = this.resolvePlannedMedicationPerformCount(listRow, med);
            this.procedurePerformCountGate.assertPerformCountBelowPlanned(
                medicationRequestId, TableNameConstants.MED_MEDICATION_REQUEST, planned, loginWorkOrgId, true);
        }
        // Cmd：诊疗路径——逐条校验净次数门控后执行 create→start→complete（护士站通过 CreateProcedureRecordCommand#skipDuplicateRequestAnchorCheck 允许多次登记）。
        for (Long serviceRequestId : retainedServiceIds) {
            ServiceRequestListModel listRow = serviceRowById.get(serviceRequestId);
            if (NurseStationSkinTestExecuteExcludeHelper.shouldExcludeServiceFromNurseStationExecuteList(
                skinTestActivityId, listRow)) {
                continue;
            }
            ServiceRequestQueryModel sr = this.serviceRequestQueryService.mustGetServiceRequest(serviceRequestId);
            int planned = this.resolvePlannedServicePerformCount(listRow);
            this.procedurePerformCountGate.assertPerformCountBelowPlanned(
                serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST, planned, loginWorkOrgId, true);
            LocalDateTime now = LocalDateTime.now();
            com.zxplat.domain.clinical.procedure.command.CreateProcedureRecordCommand createCmd =
                this.outpatientNurseStationTreatmentMapper.toCreateProcedureRecordCommand(
                    serviceRequestId,
                    TableNameConstants.WKF_SERVICE_REQUEST,
                    request.getEncounterId(),
                    encounter.getPatientId(),
                    sr.getCategoryEnum(),
                    now,
                    practitionerId);
            createCmd.setOrgId(loginWorkOrgId);
            Long procedureId = this.procedureDomainService.createProcedureRecord(createCmd);
            this.procedureDomainService.startProcedureExecution(
                this.outpatientNurseStationTreatmentMapper.toStartProcedureExecutionCommand(procedureId));
            // Cmd：执行人须在 COMPLETED 前挂入（DM-CLN-004 §6.1.3）
            this.procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
                procedureId, practitionerId, practitionerId, now);
            this.procedureDomainService.completeProcedureRecord(
                this.outpatientNurseStationTreatmentMapper.toCompleteProcedureRecordCommand(procedureId, practitionerId));
            performedServiceIds.add(serviceRequestId);
        }
        // Cmd：药品路径——净次数门控计划次数取自 execute_num（BR-NUR-005，见 MedicationRequestDetailQueryModel）。
        for (Long medicationRequestId : retainedMedicationIds) {
            MedicationRequestListQueryModel listRow = medicationRowById.get(medicationRequestId);
            if (NurseStationSkinTestExecuteExcludeHelper.shouldExcludeMedicationFromNurseStationExecuteList(listRow)) {
                continue;
            }
            if (NurseStationOutpatientTcmExecuteExcludeHelper.shouldExcludeMedicationFromOutpatientNurseStationExecuteList(listRow)) {
                continue;
            }
            MedicationRequestDetailQueryModel med = this.medicationRequestQueryService.loadMedicationRequestById(medicationRequestId);
            int planned = this.resolvePlannedMedicationPerformCount(listRow, med);
            this.procedurePerformCountGate.assertPerformCountBelowPlanned(
                medicationRequestId, TableNameConstants.MED_MEDICATION_REQUEST, planned, loginWorkOrgId, true);
            LocalDateTime now = LocalDateTime.now();
            String category = med.getTherapyEnum();
            if (category == null) {
                category = OutpatientNurseStationConstants.MEDICATION_PROCEDURE_CATEGORY_FALLBACK;
            }
            com.zxplat.domain.clinical.procedure.command.CreateProcedureRecordCommand createCmd =
                this.outpatientNurseStationTreatmentMapper.toCreateProcedureRecordCommand(
                    medicationRequestId,
                    TableNameConstants.MED_MEDICATION_REQUEST,
                    request.getEncounterId(),
                    encounter.getPatientId(),
                    category,
                    now,
                    practitionerId);
            createCmd.setOrgId(loginWorkOrgId);
            Long procedureId = this.procedureDomainService.createProcedureRecord(createCmd);
            this.procedureDomainService.startProcedureExecution(
                this.outpatientNurseStationTreatmentMapper.toStartProcedureExecutionCommand(procedureId));
            // Cmd：执行人须在 COMPLETED 前挂入（DM-CLN-004 §6.1.3）
            this.procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
                procedureId, practitionerId, practitionerId, now);
            this.procedureDomainService.completeProcedureRecord(
                this.outpatientNurseStationTreatmentMapper.toCompleteProcedureRecordCommand(procedureId, practitionerId));
            performedMedicationIds.add(medicationRequestId);
        }
        // Cmd：耗材路径——按开关推进已配药或就地改库房后已发药（只改 location、按签发批号扣库）；DeviceRequest 状态不变
        if (!retainedDeviceDispenseIds.isEmpty()) {
            List<Long> sortedDispenseIds = new ArrayList<>(retainedDeviceDispenseIds);
            Collections.sort(sortedDispenseIds);
            Map<Long, BigDecimal> inventoryIdToDecreaseQty = new HashMap<>();
            this.deviceDispenseNurseWorkflowOrchestrator.applyOutpatientNurseDeviceDispenses(
                sortedDispenseIds,
                practitionerId,
                practitionerId,
                practitionerId,
                request.getInventoryLocationId(),
                completedDispenseIds,
                inventoryIdToDecreaseQty);
            if (!inventoryIdToDecreaseQty.isEmpty()) {
                this.inventoryItemDomainService.decreaseQuantitiesMerged(
                    practitionerId, inventoryIdToDecreaseQty);
            }
        }
        // 审计：记录护士、就诊与批量勾选摘要，满足 AS-SDD §6.5 可追溯要求。
        log.info(
            "OutpatientNurseStation batchPerformDisposition operatorId={} encounterId={} services={} medications={} dispenses={} inventoryLocationId={}",
            practitionerId,
            request.getEncounterId(),
            request.getServiceRequestIds(),
            request.getMedicationRequestIds(),
            retainedDeviceDispenseIds,
            request.getInventoryLocationId());
        BatchPerformDispositionResponse response = new BatchPerformDispositionResponse();
        response.setSuccess(Boolean.TRUE);
        response.setPerformedServiceIds(performedServiceIds);
        response.setPerformedMedicationIds(performedMedicationIds);
        response.setCompletedDeviceDispenseIds(completedDispenseIds);
        return response;
    }

    /**
     * IF-NUR-05：批量取消执行（诊疗/药品追加 CANCEL 与耗材发放回退）。
     *
     * <p>耗材：已配药/已发药对称回退至执行前 {@link DispenseStatus#PREPARATION}；
     * 仅已发药路径合并回补库存。DeviceRequest 状态不变。不含 ChargeItem 回滚。</p>
     *
     * @param request 批量勾选、取消原因与操作人上下文
     * @return 成功标记与各路径实际取消主键集合
     * @throws BusinessException 门控失败、净次数为 0、发放态不可取消或领域写失败（EX-NUR-209～213 语义）
     */
    public BatchCancelDispositionResponse batchCancelDisposition(BatchCancelDispositionRequest request) {
        // Gate：入参、取消原因与至少一类勾选（EX-NUR-209）；不触碰 ChargeItem。
        this.validateBatchCancelDispositionGate(request);
        // Pre：操作人未传则取当前登录医务人员（B.6.5）。
        Long practitionerId = this.resolveNurseStationPractitionerId(request.getOperatorId());
        // Pre：就诊锚点存在，后续 Procedure 创建需患者主键。
        EncounterDetailQueryModel encounter = this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        List<Long> cancelledProcedureIds = new ArrayList<>();
        List<Long> revertedDeviceDispenseIds = new ArrayList<>();
        // Cmd：诊疗/药品——按前端勾选的已完成 procedureId 逐条追加 CANCEL（refund_id 指向该 COMPLETED，BR-NUR-011 / BR-PRO-005）。
        List<Long> procedureIds = this.normalizeDistinctProcedureIdList(request.getProcedureIds());
        for (Long completedProcedureId : procedureIds) {
            ProcedureRecordQueryModel completedRow =
                this.mustResolveCancellableCompletedProcedure(completedProcedureId, request.getEncounterId());
            String categoryEnum = completedRow.getCategoryEnum();
            if (categoryEnum == null) {
                if (TableNameConstants.MED_MEDICATION_REQUEST.equals(completedRow.getRequestTable())) {
                    categoryEnum = OutpatientNurseStationConstants.MEDICATION_PROCEDURE_CATEGORY_FALLBACK;
                } else {
                    throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "执行记录类目不能为空");
                }
            }
            this.appendProcedureCancelOffset(
                completedRow.getRequestId(),
                completedRow.getRequestTable(),
                request.getEncounterId(),
                encounter.getPatientId(),
                categoryEnum,
                request.getStatusReasonText(),
                practitionerId,
                completedProcedureId,
                completedRow.getOrgId());
            cancelledProcedureIds.add(completedProcedureId);
        }
        boolean hasDevice = request.getDeviceDispenseIds() != null && !request.getDeviceDispenseIds().isEmpty();
        if (hasDevice) {
            List<Long> sortedDispenseIds = new ArrayList<>(request.getDeviceDispenseIds());
            Collections.sort(sortedDispenseIds);
            Map<Long, BigDecimal> inventoryIdToIncreaseQty = new HashMap<>();
            this.deviceDispenseNurseWorkflowOrchestrator.revertOutpatientNurseDeviceDispenses(
                sortedDispenseIds,
                practitionerId,
                revertedDeviceDispenseIds,
                inventoryIdToIncreaseQty);
            if (!inventoryIdToIncreaseQty.isEmpty()) {
                this.inventoryItemDomainService.increaseQuantities(
                    this.outpatientNurseStationTreatmentMapper.toIncreaseQuantityCommand(
                        practitionerId, inventoryIdToIncreaseQty));
            }
        }
        if (cancelledProcedureIds.isEmpty() && revertedDeviceDispenseIds.isEmpty()) {
            if (hasDevice) {
                // 勾选了耗材但无一行处于可回退态（EX-NUR-211）。
                throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "所选耗材发放状态不允许取消");
            }
            // 未抵消任何执行记录且未回退任何发放行（EX-NUR-210）。
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "无可取消的执行记录");
        }
        log.info(
            "OutpatientNurseStation batchCancelDisposition operatorId={} encounterId={} procedureIds={} dispenses={} statusReasonText={}",
            practitionerId,
            request.getEncounterId(),
            request.getProcedureIds(),
            request.getDeviceDispenseIds(),
            request.getStatusReasonText());
        BatchCancelDispositionResponse response = new BatchCancelDispositionResponse();
        response.setSuccess(Boolean.TRUE);
        response.setCancelledProcedureIds(cancelledProcedureIds);
        response.setRevertedDeviceDispenseIds(revertedDeviceDispenseIds);
        return response;
    }

    /**
     * IF-NUR-05 Gate：批量取消请求入参组合校验。
     *
     * @param request 入参
     * @throws BusinessException 未勾选任何可取消项或缺少操作人/就诊锚点/取消原因
     */
    private void validateBatchCancelDispositionGate(BatchCancelDispositionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        if (StringUtils.isEmpty(request.getStatusReasonText())) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "取消原因不能为空");
        }
        boolean any = false;
        if (request.getProcedureIds() != null && !request.getProcedureIds().isEmpty()) {
            any = true;
        }
        if (request.getDeviceDispenseIds() != null && !request.getDeviceDispenseIds().isEmpty()) {
            any = true;
        }
        if (!any) {
            // 诊疗/药品与耗材勾选均为空，无取消对象（EX-NUR-209）。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "请先勾选可取消的执行记录或已发放耗材");
        }
    }

    /**
     * 诊疗/药品路径：追加一条 CANCEL 执行记录（create→start→cancel，BR-NUR-011）。
     *
     * @param requestId        请求主键
     * @param requestTable     请求表持久化名
     * @param encounterId      就诊主键
     * @param patientId        患者主键
     * @param categoryEnum     执行类目码
     * @param statusReasonText              取消原因
     * @param operatorId                    操作人
     * @param offsetCompletedProcedureId    被抵消的已完成执行记录主键（写入新 CANCEL 行的 {@code refund_id}）
     * @param executionOrgId                被对冲行执行科室（CANCEL 同侧继承）
     */
    private void appendProcedureCancelOffset(
        Long requestId,
        String requestTable,
        Long encounterId,
        Long patientId,
        String categoryEnum,
        String statusReasonText,
        Long operatorId,
        Long offsetCompletedProcedureId,
        Long executionOrgId) {
        LocalDateTime now = LocalDateTime.now();
        // 新建执行记录并跳过同请求锚点幂等，以支撑多次登记/取消（skipDuplicateRequestAnchorCheck）。
        com.zxplat.domain.clinical.procedure.command.CreateProcedureRecordCommand createCmd =
            this.outpatientNurseStationTreatmentMapper.toCreateProcedureRecordCommand(
                requestId,
                requestTable,
                encounterId,
                patientId,
                categoryEnum,
                now,
                operatorId);
        // CANCEL 对冲须同侧：继承被对冲 COMPLETED 的执行科室
        createCmd.setOrgId(executionOrgId);
        Long procedureId = this.procedureDomainService.createProcedureRecord(createCmd);
        // 进入进行中态后写入 CANCEL 终态（非对历史 COMPLETED 行直接 cancel）。
        this.procedureDomainService.startProcedureExecution(
            this.outpatientNurseStationTreatmentMapper.toStartProcedureExecutionCommand(procedureId));
        // Cmd：取消操作人须在 CANCEL 前挂入（DM-CLN-004 §6.1.3 仅 PREPARATION/IN_PROGRESS）
        this.procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
            procedureId, operatorId, operatorId, now);
        if (offsetCompletedProcedureId == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "被取消的已完成执行记录主键不能为空");
        }
        // BR-PRO-005：新 CANCEL 行 refund_id 指向被抵消的已完成 cln_procedure.id。
        this.procedureDomainService.cancelProcedureRecord(
            this.outpatientNurseStationTreatmentMapper.toCancelProcedureRecordCommand(
                procedureId, statusReasonText, offsetCompletedProcedureId, operatorId));
    }

    /**
     * 校验并加载可被取消的已完成执行记录（前端传入的 {@code procedureId} 须为净剩 COMPLETED 行）。
     *
     * @param completedProcedureId 待抵消的已完成执行记录主键
     * @param encounterId          本批就诊锚点
     * @return 可取消的已完成行模型
     * @throws BusinessException 记录不存在、状态非法、就诊不一致或已被对冲
     */
    private ProcedureRecordQueryModel mustResolveCancellableCompletedProcedure(
        Long completedProcedureId,
        Long encounterId) {
        if (completedProcedureId == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "procedureId 不能为空");
        }
        ProcedureDetailQueryModel detail = this.procedureQueryService.loadProcedureById(completedProcedureId);
        if (detail == null || detail.getProcedure() == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_NOT_FOUND, "执行记录不存在");
        }
        ProcedureRecordQueryModel row = detail.getProcedure();
        if (!EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "仅已完成的执行记录可取消");
        }
        if (row.getEncounterId() == null || !row.getEncounterId().equals(encounterId)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "执行记录不属于当前就诊");
        }
        if (!this.isNurseStationProcedureRequestTable(row.getRequestTable())) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "该执行记录不属于诊疗/药品登记链路");
        }
        if (this.isProcedureAlreadyOffsetByCancel(row.getRequestId(), row.getRequestTable(), completedProcedureId)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "该执行记录已被取消对冲");
        }
        return row;
    }

    /**
     * 判断某条已完成执行记录是否已被同请求下的 CANCEL 行对冲。
     *
     * @param requestId            来源请求主键
     * @param requestTable         来源表持久化名
     * @param completedProcedureId 已完成执行记录主键
     * @return 已被对冲时返回 true
     */
    private boolean isProcedureAlreadyOffsetByCancel(Long requestId, String requestTable, Long completedProcedureId) {
        List<ProcedureRecordQueryModel> siblings = this.procedureQueryService.getProcedureRecords(requestId, requestTable);
        for (ProcedureRecordQueryModel sibling : siblings) {
            if (sibling == null) {
                continue;
            }
            if (!EventStatus.isCancelExecutionStatusCode(sibling.getStatusEnum())) {
                continue;
            }
            if (completedProcedureId.equals(sibling.getRefundId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断来源表是否属于护士站诊疗/药品执行登记。
     *
     * @param requestTable 来源表持久化名
     * @return 是否为诊疗或药品请求表
     */
    private boolean isNurseStationProcedureRequestTable(String requestTable) {
        if (StringUtils.isEmpty(requestTable)) {
            return false;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(requestTable)) {
            return true;
        }
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(requestTable)) {
            return true;
        }
        return false;
    }

    /**
     * 净剩执行记录列表排序：记录时间倒序，同时间按主键倒序。
     *
     * @return 比较器
     */
    private Comparator<ProcedureRecordQueryModel> netRemainingProcedureRecordComparator() {
        return (left, right) -> {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            LocalDateTime leftTime = left.getRecordedTime();
            LocalDateTime rightTime = right.getRecordedTime();
            if (leftTime != null && rightTime != null) {
                int timeCmp = rightTime.compareTo(leftTime);
                if (timeCmp != 0) {
                    return timeCmp;
                }
            }
            if (leftTime == null && rightTime != null) {
                return 1;
            }
            if (leftTime != null && rightTime == null) {
                return -1;
            }
            Long leftId = left.getId();
            Long rightId = right.getId();
            if (leftId == null && rightId == null) {
                return 0;
            }
            if (leftId == null) {
                return 1;
            }
            if (rightId == null) {
                return -1;
            }
            return rightId.compareTo(leftId);
        };
    }

    /**
     * 去重并保持勾选顺序的 procedureId 列表（同批禁止重复抵消同一条 COMPLETED）。
     *
     * @param procedureIds 原始勾选列表
     * @return 去重后的主键列表
     * @throws BusinessException 同批重复勾选同一 procedureId
     */
    private List<Long> normalizeDistinctProcedureIdList(List<Long> procedureIds) {
        if (procedureIds == null || procedureIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long procedureId : procedureIds) {
            if (procedureId == null) {
                continue;
            }
            if (distinct.contains(procedureId)) {
                throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "同批不能重复勾选同一执行记录");
            }
            distinct.add(procedureId);
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 解析护士站写路径操作/执行人（同口径 {@code adm_practitioner.id}，B.6.5）。
     *
     * @param requestOperatorId 请求体 operatorId；未传则取当前登录绑定医务人员
     * @return 非 null 医务人员主键
     * @throws BusinessException 未传且当前会话未绑定医务人员
     */
    private Long resolveNurseStationPractitionerId(Long requestOperatorId) {
        if (requestOperatorId != null) {
            return requestOperatorId;
        }
        return this.currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "未解析到当前医务人员，无法执行护士站操作");
    }

    /**
     * 选取最近一次净剩已完成执行记录（BR-NUR-013：已被 CANCEL 对冲的 COMPLETED 不计入）。
     *
     * @param procedures 同一请求下的执行记录列表
     * @return 最近净剩已完成行；无命中时返回 null
     */
    private ProcedureRecordQueryModel pickLatestNetRemainingCompletedProcedure(
        List<ProcedureRecordQueryModel> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        Set<Long> offsetCompletedIds = new HashSet<>();
        for (ProcedureRecordQueryModel row : procedures) {
            if (row == null) {
                continue;
            }
            if (EventStatus.isCancelExecutionStatusCode(row.getStatusEnum())) {
                if (row.getRefundId() != null) {
                    offsetCompletedIds.add(row.getRefundId());
                }
            }
        }
        ProcedureRecordQueryModel latest = null;
        LocalDateTime latestTime = null;
        for (ProcedureRecordQueryModel row : procedures) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (offsetCompletedIds.contains(row.getId())) {
                continue;
            }
            LocalDateTime candidateTime = row.getOccurrenceTime();
            if (candidateTime == null) {
                candidateTime = row.getRecordedTime();
            }
            if (latest == null) {
                latest = row;
                latestTime = candidateTime;
                continue;
            }
            if (candidateTime != null) {
                if (latestTime == null || candidateTime.isAfter(latestTime)) {
                    latest = row;
                    latestTime = candidateTime;
                }
            }
        }
        return latest;
    }

    /**
     * 选取护士侧最近一条净剩 COMPLETED（空 org 或执行科室非 TECH）。
     *
     * @param procedures       同请求下全部执行记录
     * @param procedureOrgById 执行记录 org → 组织（可与次数统计共用，避免重复查）
     * @return 最近护士侧净剩 COMPLETED；无则 null
     */
    private ProcedureRecordQueryModel pickLatestNetRemainingCompletedProcedureNurseSide(
        List<ProcedureRecordQueryModel> procedures,
        Map<Long, Organization> procedureOrgById) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        Map<Long, Organization> orgById = procedureOrgById;
        // Pre：调用方未预装组织时就地补齐
        if (orgById == null) {
            orgById = this.mapOrganizationsForProcedureRecords(procedures);
        }
        List<ProcedureRecordQueryModel> nurseSide = new ArrayList<>();
        for (ProcedureRecordQueryModel row : procedures) {
            // 历史空 org 或非 TECH 工作科室 → 护士侧
            if (this.isNurseSideProcedureRecord(row, orgById)) {
                nurseSide.add(row);
            }
        }
        return this.pickLatestNetRemainingCompletedProcedure(nurseSide);
    }

    /**
     * 批量装载执行记录上的工作科室组织（禁按条 N+1）。
     *
     * @param procedures 同请求下执行记录
     * @return orgId → Organization；无命中返回空 Map
     */
    private Map<Long, Organization> mapOrganizationsForProcedureRecords(
        List<ProcedureRecordQueryModel> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> orgIds = new HashSet<>();
        for (ProcedureRecordQueryModel row : procedures) {
            if (row != null && row.getOrgId() != null) {
                orgIds.add(row.getOrgId());
            }
        }
        return this.organizationQueryService.mapByIds(orgIds);
    }

    /**
     * 是否护士侧执行记录：历史空 org，或工作科室 class 非 TECH（TECH 归医技站）。
     *
     * <p>与批量执行写 {@code Procedure.orgId=登录科} 同轴：护士站登录科非 TECH，次数/执行人须计入。</p>
     *
     * @param row     执行记录
     * @param orgById 组织索引
     * @return 护士侧时 true
     */
    private boolean isNurseSideProcedureRecord(
        ProcedureRecordQueryModel row,
        Map<Long, Organization> orgById) {
        if (row == null) {
            return false;
        }
        // 历史未写 org 的执行记录按护士侧兼容
        if (row.getOrgId() == null) {
            return true;
        }
        return !this.isTechOrganization(row.getOrgId(), orgById);
    }

    /**
     * 解析护士站写 Procedure 用的登录工作科室：须非 TECH。
     *
     * <p>医技科登录应走医技工作站；若护士站仍写 TECH org，次数按 class 分侧会当成医技执行而不进处置单。</p>
     *
     * @param errorCode 业务异常码
     * @return 非 TECH 登录科室主键
     * @throws BusinessException 未登录、科室缺失或当前登录科为 TECH
     */
    private Long requireLoginNurseSideWorkOrgId(String errorCode) {
        Long loginWorkOrgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            errorCode,
            "未登录或科室上下文缺失，无法执行");
        Organization loginOrg = this.organizationQueryService.loadOrganizationByIdForDisplay(loginWorkOrgId);
        // Gate：医技科禁止在护士站写执行，否则 org.class=TECH 与医技侧不可分
        if (loginOrg != null && loginOrg.getClassEnum() == OrganizationClassification.TECH) {
            throw new BusinessException(
                errorCode,
                "当前登录科室为医技科，请切换到护士站科室后再执行");
        }
        return loginWorkOrgId;
    }

    /**
     * 判断组织是否为 TECH 医技科。
     *
     * @param orgId   组织主键
     * @param orgById 组织索引
     * @return TECH 时 true
     */
    private boolean isTechOrganization(Long orgId, Map<Long, Organization> orgById) {
        if (orgId == null || orgById == null) {
            return false;
        }
        Organization org = orgById.get(orgId);
        if (org == null || org.getClassEnum() == null) {
            return false;
        }
        return org.getClassEnum() == OrganizationClassification.TECH;
    }

    /**
     * 是否可进护士站「医技列表」的检验行（类目 22，且非合管派生采血费）。
     *
     * <p>TECH 检验仍进入 IF-NUR-02 诊疗列表，供前端下方「医技列表」打印采血贴；
     * 不得进入上方待执行勾选/批量执行语义。采血费类目虽为 22，仅计费不打条码，TECH 轴下不走本例外。</p>
     *
     * @param row            诊疗请求
     * @param catalogContext 已预热的诊疗定义
     * @return 真实检验项目时 true；采血费或非检验为 false
     */
    private boolean isLabProofServiceRequest(
        ServiceRequestListModel row,
        OrderRequestCatalogWarmContext catalogContext) {
        if (row == null || row.getActivityId() == null || catalogContext == null) {
            return false;
        }
        // 采血费：与检验同属类目 22，但不进采血贴医技列表（BR-NUR-002）
        if (this.specimenTubeMergeBindResolveSupport.isBloodCollectionFeeActivityId(row.getActivityId())) {
            return false;
        }
        ActivityDefinition activityDef =
            catalogContext.getActivityDefinitionById().get(row.getActivityId());
        if (activityDef == null || StringUtils.isEmpty(activityDef.getCategoryCode())) {
            return false;
        }
        return ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROOF.equals(activityDef.getCategoryCode().trim());
    }

    /**
     * 批量回填处置单行的最近主执行人展示字段。
     *
     * @param lineToProcedureId 处置行 → 最近已完成 Procedure 主键
     */
    private void applyLastPerformerDisplayFromProcedureIds(
        Map<NurseStationTreatmentLineDto, Long> lineToProcedureId) {
        if (lineToProcedureId == null || lineToProcedureId.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        for (Long procedureId : lineToProcedureId.values()) {
            if (procedureId != null) {
                procedureIds.add(procedureId);
            }
        }
        if (procedureIds.isEmpty()) {
            return;
        }
        // Pre：直接从 cln_procedure_performer 批量解析主执行人，避免 JSON 序列化/反序列化丢字段。
        Map<Long, Long> primaryPractitionerByProcedureId =
            this.procedureQueryService.mapPrimaryPractitionerIdByProcedureIds(procedureIds);
        if (primaryPractitionerByProcedureId.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>(primaryPractitionerByProcedureId.values());
        Map<Long, String> nameByPractitionerId =
            this.procedurePerformerDisplayAssembler.mapDisplayNameByPractitionerIds(practitionerIds);
        for (Map.Entry<NurseStationTreatmentLineDto, Long> entry : lineToProcedureId.entrySet()) {
            NurseStationTreatmentLineDto line = entry.getKey();
            Long procedureId = entry.getValue();
            if (line == null || procedureId == null) {
                continue;
            }
            Long practitionerId = primaryPractitionerByProcedureId.get(procedureId);
            if (practitionerId == null) {
                continue;
            }
            line.setLastPerformerPractitionerId(practitionerId);
            String displayName = nameByPractitionerId.get(practitionerId);
            if (StringUtils.isNotEmpty(displayName)) {
                line.setLastPerformerDisplay(displayName);
            }
        }
    }

    /**
     * IF-NUR-03：为执行记录抽屉批量回填可直接展示的列（项目名、操作、操作人）。
     *
     * @param encounterId 就诊主键（用于批量加载同源请求行，避免按 procedure 循环查库）
     * @param records     执行单 DTO 行；允许 null
     */
    private void enrichExecutionSheetDrawerDisplay(Long encounterId, List<OrderExecutionSheetItemDto> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, ServiceRequestListModel> serviceById = new HashMap<>();
        Map<Long, MedicationRequestListQueryModel> medicationById = new HashMap<>();
        if (encounterId != null) {
            List<ServiceRequestListModel> serviceRows =
                this.serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
            if (serviceRows != null) {
                for (ServiceRequestListModel row : serviceRows) {
                    if (row != null && row.getId() != null) {
                        serviceById.put(row.getId(), row);
                    }
                }
            }
            List<MedicationRequestListQueryModel> medicationRows =
                this.medicationRequestQueryService.listMedicationRequestsByEncounter(encounterId);
            if (medicationRows != null) {
                for (MedicationRequestListQueryModel row : medicationRows) {
                    if (row != null && row.getId() != null) {
                        medicationById.put(row.getId(), row);
                    }
                }
            }
        }
        OrderRequestCatalogWarmContext catalogContext = this.orderRequestCatalogWarmAssembler.prepareFromTreatmentSheetRows(
            new ArrayList<>(serviceById.values()),
            new ArrayList<>(medicationById.values()),
            Collections.emptyList());
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        for (OrderExecutionSheetItemDto record : records) {
            if (record != null && record.getProcedureId() != null) {
                procedureIds.add(record.getProcedureId());
            }
        }
        // Pre：直接从 cln_procedure_performer 批量解析主执行人，避免 JSON 序列化/反序列化丢字段。
        Map<Long, Long> primaryPractitionerByProcedureId =
            this.procedureQueryService.mapPrimaryPractitionerIdByProcedureIds(procedureIds);
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>(primaryPractitionerByProcedureId.values());
        Map<Long, String> practitionerNameById =
            this.procedurePerformerDisplayAssembler.mapDisplayNameByPractitionerIds(practitionerIds);
        for (OrderExecutionSheetItemDto record : records) {
            if (record == null) {
                continue;
            }
            record.setOrderItemDisplayName(
                this.resolveExecutionRecordOrderItemDisplayName(record, serviceById, medicationById, catalogContext));
            record.setActionDisplayText(this.resolveExecutionRecordActionDisplayText(record));
            String operatorRole = this.resolveExecutionRecordOperatorRoleDisplayText(record.getStatusEnum());
            record.setOperatorRoleDisplayText(operatorRole);
            String operatorName = null;
            if (record.getProcedureId() != null) {
                Long practitionerId = primaryPractitionerByProcedureId.get(record.getProcedureId());
                if (practitionerId != null) {
                    operatorName = practitionerNameById.get(practitionerId);
                }
            }
            record.setOperatorDisplayName(operatorName);
        }
    }

    /**
     * 解析执行记录对应医嘱/项目展示名（诊疗活动定义名或药品定义名）。
     *
     * @param record          执行单 DTO 行
     * @param serviceById     就诊下诊疗申请索引
     * @param medicationById  就诊下药品申请索引
     * @param catalogContext  目录预热上下文
     * @return 展示名；无法解析时回落占位文案
     */
    private String resolveExecutionRecordOrderItemDisplayName(
        OrderExecutionSheetItemDto record,
        Map<Long, ServiceRequestListModel> serviceById,
        Map<Long, MedicationRequestListQueryModel> medicationById,
        OrderRequestCatalogWarmContext catalogContext) {
        if (record == null || record.getRequestId() == null || StringUtils.isEmpty(record.getRequestTable())) {
            return OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_SERVICE_LINE_NAME_FALLBACK;
        }
        String requestTable = record.getRequestTable().trim();
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(requestTable)) {
            ServiceRequestListModel serviceRow = serviceById.get(record.getRequestId());
            ActivityDefinition activityDef = null;
            if (serviceRow != null && serviceRow.getActivityId() != null && catalogContext != null) {
                activityDef = catalogContext.getActivityDefinitionById().get(serviceRow.getActivityId());
            }
            String activityName = null;
            if (activityDef != null) {
                activityName = activityDef.getName();
            }
            return OrderCatalogDisplayAssembler.resolveItemDisplayName(
                activityName,
                OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_SERVICE_LINE_NAME_FALLBACK);
        }
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(requestTable)) {
            MedicationRequestListQueryModel medicationRow = medicationById.get(record.getRequestId());
            MedicationDefinition medicationDef = null;
            if (medicationRow != null && medicationRow.getMedicationId() != null && catalogContext != null) {
                medicationDef = catalogContext.getMedicationDefinitionById().get(medicationRow.getMedicationId());
            }
            String medicationName = null;
            if (medicationDef != null) {
                medicationName = medicationDef.getName();
            }
            return OrderCatalogDisplayAssembler.resolveItemDisplayName(
                medicationName,
                OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_MEDICATION_LINE_NAME_FALLBACK);
        }
        return OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_SERVICE_LINE_NAME_FALLBACK;
    }

    /**
     * 解析执行记录「操作」列展示文案（状态 + 结果摘要，占位摘要替换为业务语义）。
     *
     * @param record 执行单 DTO 行
     * @return 操作展示文案
     */
    private String resolveExecutionRecordActionDisplayText(OrderExecutionSheetItemDto record) {
        if (record == null) {
            return OutpatientNurseStationConstants.EXECUTION_RECORD_ACTION_DEFAULT;
        }
        String statusCode = record.getStatusEnum();
        if (statusCode != null) {
            statusCode = statusCode.trim();
        }
        String resultText = record.getResultText();
        if (resultText != null) {
            resultText = resultText.trim();
        }
        if (EventStatus.CANCEL.getCode().equals(statusCode)) {
            if (StringUtils.isNotEmpty(resultText)) {
                return resultText;
            }
            return OutpatientNurseStationConstants.EXECUTION_RECORD_ACTION_CANCEL;
        }
        if (EventStatus.COMPLETED.getCode().equals(statusCode)) {
            if (StringUtils.isNotEmpty(resultText)
                && !OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_PROCEDURE_RESULT_TEXT.equals(resultText)) {
                return resultText;
            }
            return OutpatientNurseStationConstants.EXECUTION_RECORD_ACTION_COMPLETED;
        }
        if (EventStatus.STOPPED.getCode().equals(statusCode)) {
            if (StringUtils.isNotEmpty(resultText)) {
                return resultText;
            }
            return OutpatientNurseStationConstants.EXECUTION_RECORD_ACTION_STOPPED;
        }
        if (EventStatus.IN_PROGRESS.getCode().equals(statusCode)) {
            return OutpatientNurseStationConstants.EXECUTION_RECORD_ACTION_IN_PROGRESS;
        }
        if (EventStatus.PREPARATION.getCode().equals(statusCode)) {
            return OutpatientNurseStationConstants.EXECUTION_RECORD_ACTION_PREPARATION;
        }
        if (StringUtils.isNotEmpty(resultText)) {
            return resultText;
        }
        if (StringUtils.isNotEmpty(record.getStatusEnumText())) {
            return record.getStatusEnumText().trim();
        }
        return OutpatientNurseStationConstants.EXECUTION_RECORD_ACTION_DEFAULT;
    }

    /**
     * 解析操作人列标题语义（取消记录与完成记录区分展示）。
     *
     * @param statusEnum 执行状态持久化码
     * @return 「执行人」或「取消执行人」
     */
    private String resolveExecutionRecordOperatorRoleDisplayText(String statusEnum) {
        if (EventStatus.CANCEL.getCode().equals(statusEnum)) {
            return OutpatientNurseStationConstants.EXECUTION_RECORD_OPERATOR_ROLE_CANCEL;
        }
        return OutpatientNurseStationConstants.EXECUTION_RECORD_OPERATOR_ROLE_EXECUTE;
    }

    /**
     * 将可空的主键列表规范为非 null 引用；未勾选时按空列表迭代。
     *
     * @param ids 请求体中的 id 列表；允许 null
     * @return 非 null 列表引用
     */
    private List<Long> normalizeOptionalIdList(List<Long> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids;
    }

    /**
     * IF-NUR-04 Gate：批量处置请求入参组合校验。
     *
     * @param request 入参
     * @throws BusinessException 未勾选任何执行项或缺少操作人/就诊锚点
     */
    private void validateBatchPerformDispositionGate(BatchPerformDispositionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        boolean any = false;
        if (request.getServiceRequestIds() != null && !request.getServiceRequestIds().isEmpty()) {
            any = true;
        }
        if (request.getMedicationRequestIds() != null && !request.getMedicationRequestIds().isEmpty()) {
            any = true;
        }
        if (request.getDeviceDispenseIds() != null && !request.getDeviceDispenseIds().isEmpty()) {
            any = true;
        }
        if (!any) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "请先勾选待执行项目或耗材");
        }
    }

    /**
     * 断言前端传入耗材房落在登录者管辖药房白名单内（与下拉 {@link #listManagedSupplyRoomOptions} 同源）。
     *
     * @param inventoryLocationId 耗材房库位主键
     */
    private void assertInventoryLocationInManagedPharmacies(Long inventoryLocationId) {
        if (inventoryLocationId == null) {
            return;
        }
        Set<Long> allowedIds = this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.managedLocationIdsResolver.listManagedActivePharmaciesOrEmpty());
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            inventoryLocationId,
            allowedIds,
            ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT,
            "所选耗材房不在管辖范围内");
    }

    /**
     * 基于已计费门控（PLANNED∪SETTLED）过滤请求主键列表。
     *
     * @param requestIds  勾选主键
     * @param serviceTable 请求表持久化名
     * @param chargeItems 就诊收费明细
     * @return 仍保留的请求主键列表
     */
    private List<Long> retainBillableRequestIds(List<Long> requestIds, String serviceTable, List<ChargeItem> chargeItems) {
        List<Long> retained = new ArrayList<>();
        if (requestIds == null || requestIds.isEmpty()) {
            return retained;
        }
        Set<Long> billable = new HashSet<>();
        if (chargeItems != null) {
            for (ChargeItem item : chargeItems) {
                if (item.getServiceId() == null || StringUtils.isEmpty(item.getServiceTable())) {
                    continue;
                }
                if (!serviceTable.equals(item.getServiceTable())) {
                    continue;
                }
                if (ChargeItemStatus.PLANNED.getCode().equals(item.getStatusEnum())
                    || ChargeItemStatus.SETTLED.getCode().equals(item.getStatusEnum())) {
                    billable.add(item.getServiceId());
                }
            }
        }
        for (Long id : requestIds) {
            if (id == null) {
                continue;
            }
            if (billable.contains(id)) {
                retained.add(id);
            }
        }
        return retained;
    }

    /**
     * 耗材发放勾选：仅保留对应耗材申请仍存在有效计费（PLANNED/SETTLED）的发放行（BR-NUR-018）。
     *
     * <p>兼容收费锚在请求表或发放表；退费中/已退费申请下的发放一律剔除，禁止脏勾选再执行。</p>
     *
     * @param deviceDispenseIds 勾选发放主键
     * @param chargeItems       就诊收费明细
     * @return 仍允许发放的主键列表（保持入参相对顺序）
     */
    private List<Long> retainBillableDeviceDispenseIds(List<Long> deviceDispenseIds, List<ChargeItem> chargeItems) {
        List<Long> retained = new ArrayList<>();
        // Gate：未勾选则空表
        if (deviceDispenseIds == null || deviceDispenseIds.isEmpty()) {
            return retained;
        }
        // Pre：发放 id → 耗材申请 id，供与收费锚点对齐
        Map<Long, Long> dispenseIdToRequestId =
            this.deviceDispenseQueryService.mapDeviceDispenseIdsToDeviceReqIds(deviceDispenseIds);
        // Pre：按请求锚点归并收费态（含发放表映射）
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor =
            AdviceChargeAnchorRollupHelper.buildRequestAnchorChargeIndex(
                chargeItems, this.medicationDispenseQueryService, this.deviceDispenseQueryService);
        for (Long dispenseId : deviceDispenseIds) {
            // Skip：空主键
            if (dispenseId == null) {
                continue;
            }
            Long deviceRequestId = dispenseIdToRequestId.get(dispenseId);
            // Skip：无法回落申请锚点的发放行
            if (deviceRequestId == null) {
                continue;
            }
            String chargeStatus = this.resolveAggregatedChargeStatus(
                TableNameConstants.WKF_DEVICE_REQUEST, deviceRequestId, chargeByAnchor);
            // Skip：退费中/已退费或无有效计费
            if (!AdviceChargeDisplayStatusAssembler.isActiveBillableChargeStatus(chargeStatus)) {
                continue;
            }
            retained.add(dispenseId);
        }
        return retained;
    }

    /**
     * 计算某请求下执行记录净次数（完成 − 取消）。
     *
     * @param requestId    请求主键
     * @param requestTable 请求表持久化名
     * @return 净次数
     */
    private int netProcedureCount(Long requestId, String requestTable) {
        List<ProcedureRecordQueryModel> records = this.procedureQueryService.getProcedureRecords(requestId, requestTable);
        int completed = 0;
        int cancelCount = 0;
        for (ProcedureRecordQueryModel row : records) {
            if (EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                completed++;
            }
            if (EventStatus.isCancelExecutionStatusCode(row.getStatusEnum())) {
                cancelCount++;
            }
        }
        int net = completed - cancelCount;
        return net;
    }

    /**
     * 从诊疗列表行解析计划执行次数（仅 {@code execution_count}）。
     *
     * @param listRow 列表行；允许 null
     * @return 计划次数
     */
    private int resolvePlannedServicePerformCount(ServiceRequestListModel listRow) {
        return ServiceAdviceDraftCommandHelper.resolvePlannedServicePerformCount(listRow);
    }

    /**
     * 从药品列表行与详情解析计划执行次数：优先使用列表行数量取整（execute_num 未在详情模型暴露时的回落口径）。
     *
     * @param listRow 列表行；允许 null
     * @param detail  详情；允许 null
     * @return 计划次数
     */
    private int resolvePlannedMedicationPerformCount(MedicationRequestListQueryModel listRow, MedicationRequestDetailQueryModel detail) {
        if (detail != null && detail.getExecuteNum() != null) {
            return detail.getExecuteNum();
        }
        if (listRow != null && listRow.getQuantity() != null) {
            return listRow.getQuantity().intValue();
        }
        if (detail != null && detail.getQuantity() != null) {
            return detail.getQuantity().intValue();
        }
        return 1;
    }

    /**
     * IF-NUR-02 步骤 8b：批量预热药品行用法/频次/单位术语与产品单位回落文案。
     *
     * <p>与 IF-DOR-10 {@code DoctorStationAdviceListAssembler} 共用 {@link TerminologyOptionLoader}、
     * {@link FrequencyOptionLoader}、{@link ProductDefinitionCatalogUnitBandsAssembler} 口径（**BR-ORD-022**）。</p>
     *
     * @param medicationRequests 就诊下药品申请列表（含过滤前全集，避免循环查库）
     * @return 术语与产品展示预热上下文；无药品行时 productByKey 为空 Map
     */
    private NurseStationMedicationDisplayWarmContext prepareMedicationDisplayWarm(
        List<MedicationRequestListQueryModel> medicationRequests) {
        NurseStationMedicationDisplayWarmContext warm = new NurseStationMedicationDisplayWarmContext();
        // Pre：预热单位/用法/剂量单位术语 Map，供药品行 code+展示名成对回显。
        warm.setUnitLabelByCode(
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE));
        warm.setMethodLabelByCode(
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE));
        warm.setTcmMethodLabelByCode(
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE));
        warm.setRateLabelByCode(this.frequencyOptionLoader.buildRateLabelMap());
        warm.setDoseUnitLabelByCode(
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT));
        if (medicationRequests == null || medicationRequests.isEmpty()) {
            // 分支：就诊下无药品申请时跳过产品批量加载，仍返回术语 Map 供空列表安全复用。
            warm.setProductByKey(new HashMap<>());
            return warm;
        }
        Set<Long> medicationDefIds = new LinkedHashSet<>();
        // Pre：收集本就诊药品定义 id，供 ProductDefinitionDisplayTerminologyAssembler 一次 IN 批量加载。
        for (MedicationRequestListQueryModel row : medicationRequests) {
            if (row.getMedicationId() != null) {
                medicationDefIds.add(row.getMedicationId());
            }
        }
        warm.setProductByKey(this.batchLoadMedicationProducts(medicationDefIds));
        return warm;
    }

    /**
     * 按药品定义主键批量加载产品展示 DTO（单位/剂量单位回落文案）。
     *
     * @param medicationDefIds 药品定义主键集合
     * @return 复合键 → 产品展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> batchLoadMedicationProducts(Set<Long> medicationDefIds) {
        if (medicationDefIds == null || medicationDefIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long medicationDefId : medicationDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationDefId);
            lookups.add(lookup);
        }
        return this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 解析药品行对应的产品展示 DTO。
     *
     * @param row 药品申请列表行
     * @param medicationDisplayWarm 术语与产品预热上下文
     * @return 产品展示 DTO；无法解析时 null
     */
    private ProductDefinitionDisplayDto resolveMedicationProductDisplay(
        MedicationRequestListQueryModel row,
        NurseStationMedicationDisplayWarmContext medicationDisplayWarm) {
        if (row == null || row.getMedicationId() == null || medicationDisplayWarm == null) {
            return null;
        }
        return medicationDisplayWarm.getProductByKey().get(
            ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId()));
    }

    /**
     * 诊疗列表按申请单号聚邻排序（IF-NUR-02 BR-NUR-015）。
     *
     * @param serviceLines 待排序诊疗行（就地排序）
     */
    private void sortServiceLinesForApplyFormGrouping(List<NurseStationTreatmentLineDto> serviceLines) {
        serviceLines.sort(this::compareServiceLinesForApplyFormGrouping);
    }

    /**
     * 比较两条诊疗行：先按申请单聚合键，再 sortNumber、requestId。
     *
     * @param left  左行
     * @param right 右行
     * @return 比较结果
     */
    private int compareServiceLinesForApplyFormGrouping(
        NurseStationTreatmentLineDto left,
        NurseStationTreatmentLineDto right) {
        String leftFormKey = this.resolveApplyFormGroupingKey(left.getPrescriptionNo());
        String rightFormKey = this.resolveApplyFormGroupingKey(right.getPrescriptionNo());
        int formKeyCompare = leftFormKey.compareTo(rightFormKey);
        if (formKeyCompare != 0) {
            return formKeyCompare;
        }
        int leftSortValue = this.resolveSortNumberForGrouping(left.getSortNumber());
        int rightSortValue = this.resolveSortNumberForGrouping(right.getSortNumber());
        if (leftSortValue != rightSortValue) {
            if (leftSortValue < rightSortValue) {
                return -1;
            }
            return 1;
        }
        Long leftRequestId = left.getRequestId();
        Long rightRequestId = right.getRequestId();
        if (leftRequestId == null && rightRequestId == null) {
            return 0;
        }
        if (leftRequestId == null) {
            return 1;
        }
        if (rightRequestId == null) {
            return -1;
        }
        return leftRequestId.compareTo(rightRequestId);
    }

    /**
     * 申请单聚邻排序键：{@code AR} 前缀申请单优先聚组，非申请单置后。
     *
     * @param prescriptionNo 处方/申请单号
     * @return 排序键
     */
    private String resolveApplyFormGroupingKey(String prescriptionNo) {
        if (ServiceApplyFormOrchestrator.isRequisitionApplicationPrescriptionNo(prescriptionNo)) {
            return "0_" + prescriptionNo.trim();
        }
        if (StringUtils.isNotEmpty(prescriptionNo)) {
            return "1_" + prescriptionNo.trim();
        }
        return "2_";
    }

    /**
     * 解析 sortNumber 用于聚邻排序；缺失时置后。
     *
     * @param sortNumber 排序号
     * @return 比较用整型
     */
    private int resolveSortNumberForGrouping(Integer sortNumber) {
        if (sortNumber == null) {
            return Integer.MAX_VALUE;
        }
        return sortNumber.intValue();
    }

    /**
     * 构建诊疗处置行（锚点、数量与计费聚合字段）。
     *
     * @param row                     诊疗申请列表行
     * @param encounterId             就诊主键
     * @param chargeByAnchor          计费聚合索引
     * @param catalogContext          目录与类目展示预热上下文
     * @param mergeGroupRefByActivityId activity → 合管分组（设备 + 组序号）
     * @param medicationDisplayWarm   单位术语预热（复用步骤 8b 的 unit_code 映射，供处置单数量列展示）
     * @param packageChildrenByActivityId activity → 目录子项（处置单打印展开；列表不展示）
     * @return 处置行 DTO
     */
    private NurseStationTreatmentLineDto buildServiceLine(
        ServiceRequestListModel row,
        Long encounterId,
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor,
        OrderRequestCatalogWarmContext catalogContext,
        Map<Long, SpecimenTubeMergeGroupQueryModel> mergeGroupRefByActivityId,
        NurseStationMedicationDisplayWarmContext medicationDisplayWarm,
        Map<Long, List<AdvicePackageChildItemDto>> packageChildrenByActivityId) {
        NurseStationTreatmentLineDto line = new NurseStationTreatmentLineDto();
        line.setEncounterId(encounterId);
        line.setRequestId(row.getId());
        line.setDispenseId(null);
        line.setRequestTable(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_SERVICE);
        line.setOrderType(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_SERVICE);
        ActivityDefinition activityDef = null;
        if (row.getActivityId() != null && catalogContext != null) {
            activityDef = catalogContext.getActivityDefinitionById().get(row.getActivityId());
        }
        String activityBusNo = null;
        String activityName = null;
        if (activityDef != null) {
            activityBusNo = activityDef.getBusNo();
            activityName = activityDef.getName();
        }
        line.setItemCode(OrderCatalogDisplayAssembler.resolveItemCatalogCode(activityBusNo, row.getActivityId()));
        line.setItemName(OrderCatalogDisplayAssembler.resolveItemDisplayName(
            activityName,
            OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_SERVICE_LINE_NAME_FALLBACK));
        // Asm：采血贴/LIS applyNo 同源 SR.bus_no（禁止用 AR 申请单号当条码）
        if (StringUtils.isNotEmpty(row.getBusNo())) {
            line.setBusNo(row.getBusNo().trim());
        }
        if (StringUtils.isNotEmpty(row.getPrescriptionNo())) {
            line.setPrescriptionNo(row.getPrescriptionNo().trim());
        }
        line.setSpecText(null);
        if (activityDef != null) {
            line.setPyStr(activityDef.getPyStr());
            line.setWbStr(activityDef.getWbStr());
        } else {
            line.setPyStr(null);
            line.setWbStr(null);
        }
        String serviceCategoryCode = null;
        if (activityDef != null && StringUtils.isNotEmpty(activityDef.getCategoryCode())) {
            serviceCategoryCode = activityDef.getCategoryCode().trim();
        }
        line.setServiceCategoryCode(serviceCategoryCode);
        if (row.getActivityId() != null) {
            line.setActivityDefinitionId(row.getActivityId());
            if (mergeGroupRefByActivityId != null) {
                SpecimenTubeMergeGroupQueryModel mergeGroupRef = mergeGroupRefByActivityId.get(row.getActivityId());
                if (mergeGroupRef != null) {
                    line.setSpecimenTubeMergeDeviceId(mergeGroupRef.getDeviceId());
                    line.setSpecimenTubeMergeGroupNo(mergeGroupRef.getGroupNo());
                }
            }
        }
        // Asm：优先级下发供采血贴「急」
        line.setPriorityEnum(row.getPriorityEnum());
        List<AdvicePackageChildItemDto> packageChildren = Collections.emptyList();
        if (row.getActivityId() != null && packageChildrenByActivityId != null) {
            List<AdvicePackageChildItemDto> found = packageChildrenByActivityId.get(row.getActivityId());
            if (found != null) {
                // 处置单列表不渲染子项；打印层按院点开关展开
                packageChildren = found;
            }
        }
        line.setPackageChildren(packageChildren);
        if (catalogContext != null) {
            line.setServiceCategoryCodeText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                    catalogContext.getActivityCategoryLabelByCode(), serviceCategoryCode));
        }
        line.setDeviceCategoryCode(null);
        line.setDeviceCategoryCodeText(null);
        line.setMedCategoryCode(null);
        line.setMedCategoryCodeText(null);
        line.setQuantity(row.getQuantity());
        line.setUnitCode(row.getUnitCode());
        // Asm：诊疗单位展示名（术语 unit_code；无产品回落，对齐处置单打印数量列）
        if (medicationDisplayWarm != null) {
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                medicationDisplayWarm.getUnitLabelByCode(), row.getUnitCode(), null));
        } else {
            line.setUnitName(null);
        }
        line.setPlannedPerformCount(this.resolvePlannedServicePerformCount(row));
        line.setMedicationExecuteNum(null);
        line.setPerformCount(null);
        line.setCancelCount(null);
        line.setRefundPairRequestId(null);
        line.setGroupId(null);
        line.setSortNumber(row.getSortNumber());
        this.applyChargeAggregation(line, TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), chargeByAnchor);
        return line;
    }

    /**
     * Pre：按本批诊嘱 activityId 批量加载目录子项（禁逐行查库）。
     *
     * @param serviceRequests 本就诊诊疗申请
     * @return activityId → 子项；无子项时对应键可缺省
     */
    private Map<Long, List<AdvicePackageChildItemDto>> loadPackageChildrenByActivityId(
        List<ServiceRequestListModel> serviceRequests) {
        if (serviceRequests == null || serviceRequests.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> activityIds = new LinkedHashSet<>();
        for (ServiceRequestListModel svc : serviceRequests) {
            if (svc != null && svc.getActivityId() != null) {
                activityIds.add(svc.getActivityId());
            }
        }
        if (activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<ActivityDefinitionChildQueryModel>> modelsByActivityId =
            this.activityDefinitionQueryService.listChildrenByActivityIds(activityIds);
        Map<Long, List<AdvicePackageChildItemDto>> result = new HashMap<>();
        for (Map.Entry<Long, List<ActivityDefinitionChildQueryModel>> entry : modelsByActivityId.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(entry.getKey(), AdviceExtensionJsonParser.toPackageChildItems(entry.getValue()));
        }
        return result;
    }

    /**
     * 申请单诊疗行回填纯备注与部位/样本明细摘要（BR-NUR-016；非申请单行保持 null/空）。
     *
     * @param line                         已构建的处置行
     * @param row                          诊疗请求列表行
     * @param applyFormDetailsByRequestId  批量预取的 Detail 读模型索引
     */
    private void applyServiceApplyFormLineDisplay(
        NurseStationTreatmentLineDto line,
        ServiceRequestListModel row,
        Map<Long, List<ServiceRequestDetailListModel>> applyFormDetailsByRequestId) {
        if (line == null || row == null) {
            return;
        }
        if (!ServiceApplyFormOrchestrator.isRequisitionApplicationPrescriptionNo(row.getPrescriptionNo())) {
            return;
        }
        if (StringUtils.isNotEmpty(row.getRemark())) {
            line.setRemark(row.getRemark().trim());
        }
        if (applyFormDetailsByRequestId == null || row.getId() == null) {
            return;
        }
        List<ServiceRequestDetailListModel> detailModels = applyFormDetailsByRequestId.get(row.getId());
        if (detailModels == null || detailModels.isEmpty()) {
            return;
        }
        List<ServiceRequestDetailSummaryDto> detailDtos = this.serviceApplyFormAssembler.toDetailSummaryDtos(
            ServiceApplyFormOrchestrator.toDetailSummaryModels(detailModels));
        if (detailDtos != null && !detailDtos.isEmpty()) {
            line.setServiceRequestDetails(detailDtos);
        }
    }

    /**
     * 构建药品处置行（锚点、规格/用法用量/总量展示与计费聚合字段，IF-NUR-02）。
     *
     * @param row            药品申请列表行
     * @param encounterId    就诊主键
     * @param chargeByAnchor 计费聚合索引
     * @param catalogContext 目录与类目展示预热上下文
     * @param medicationDisplayWarm IF-NUR-02 步骤 8b 术语与产品单位回落预热结果
     * @return 处置行 DTO
     */
    private NurseStationTreatmentLineDto buildMedicationLine(
        MedicationRequestListQueryModel row,
        Long encounterId,
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor,
        OrderRequestCatalogWarmContext catalogContext,
        NurseStationMedicationDisplayWarmContext medicationDisplayWarm) {
        NurseStationTreatmentLineDto line = new NurseStationTreatmentLineDto();
        line.setEncounterId(encounterId);
        line.setRequestId(row.getId());
        line.setDispenseId(null);
        line.setRequestTable(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_MEDICATION);
        line.setOrderType(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_MEDICATION);
        MedicationDefinition medicationDef = null;
        if (row.getMedicationId() != null && catalogContext != null) {
            medicationDef = catalogContext.getMedicationDefinitionById().get(row.getMedicationId());
        }
        String medicationBusNo = null;
        String medicationName = null;
        if (medicationDef != null) {
            medicationBusNo = medicationDef.getBusNo();
            medicationName = medicationDef.getName();
        }
        line.setItemCode(OrderCatalogDisplayAssembler.resolveItemCatalogCode(medicationBusNo, row.getMedicationId()));
        line.setItemName(OrderCatalogDisplayAssembler.resolveItemDisplayName(
            medicationName,
            OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_MEDICATION_LINE_NAME_FALLBACK));
        if (StringUtils.isNotEmpty(row.getPrescriptionNo())) {
            line.setPrescriptionNo(row.getPrescriptionNo().trim());
        }
        line.setSpecText(OrderCatalogDisplayAssembler.resolveMedicationSpecText(medicationDef));
        if (medicationDef != null) {
            line.setPyStr(medicationDef.getPyStr());
            line.setWbStr(medicationDef.getWbStr());
        } else {
            line.setPyStr(null);
            line.setWbStr(null);
        }
        line.setServiceCategoryCode(null);
        line.setServiceCategoryCodeText(null);
        line.setDeviceCategoryCode(null);
        line.setDeviceCategoryCodeText(null);
        String medCategoryCode = null;
        if (medicationDef != null && StringUtils.isNotEmpty(medicationDef.getCategoryCode())) {
            medCategoryCode = medicationDef.getCategoryCode().trim();
        }
        line.setMedCategoryCode(medCategoryCode);
        if (catalogContext != null) {
            line.setMedCategoryCodeText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                    catalogContext.getMedicationCategoryLabelByCode(), medCategoryCode));
        }
        line.setQuantity(row.getQuantity());
        line.setUnitCode(row.getUnitCode());
        // Asm：药品单次剂量/总量单位/用法/频次/天数与对应展示名（禁止仅回传 code，对齐 BR-ORD-022）。
        ProductDefinitionDisplayDto medProduct = this.resolveMedicationProductDisplay(row, medicationDisplayWarm);
        if (medicationDisplayWarm != null) {
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                medicationDisplayWarm.getUnitLabelByCode(), row.getUnitCode(), medProduct));
            line.setDose(row.getDose());
            line.setDoseUnitCode(row.getDoseUnitCode());
            line.setDoseUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
                medicationDisplayWarm.getDoseUnitLabelByCode(), row.getDoseUnitCode(), medProduct));
            // Asm：单次剂量展示串 + 拆零结构化字段（单位相同时无括号）
            MedicationDosageDisplayResult dosageResult = MedicationDosageDisplayAssembler.build(
                row.getDose(),
                row.getDoseUnitCode(),
                medicationDisplayWarm.getDoseUnitLabelByCode(),
                medProduct);
            line.setDosageDisplay(dosageResult.getDosageDisplay());
            line.setSplitDose(dosageResult.getSplitDose());
            line.setSplitDoseUnitCode(dosageResult.getSplitDoseUnitCode());
            line.setSplitDoseUnitName(dosageResult.getSplitDoseUnitName());
            line.setMethodCode(row.getMethodCode());
            line.setMethodName(TcmMethodCodeDisplayAssembler.resolveMethodDisplayName(
                medicationDisplayWarm.getTcmMethodLabelByCode(),
                medicationDisplayWarm.getMethodLabelByCode(),
                row.getTcmFlag(),
                row.getMethodCode()));
            line.setRateCode(row.getRateCode());
            line.setRateName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                medicationDisplayWarm.getRateLabelByCode(), row.getRateCode()));
            line.setDispensePerDuration(row.getDispensePerDuration());
        } else {
            // 分支：预热上下文缺失时不输出用量展示字段，避免前端仅拿到 code 无法拼用法用量行。
            line.setUnitName(null);
            line.setDose(null);
            line.setDoseUnitCode(null);
            line.setDoseUnitName(null);
            line.setDosageDisplay(null);
            line.setSplitDose(null);
            line.setSplitDoseUnitCode(null);
            line.setSplitDoseUnitName(null);
            line.setMethodCode(row.getMethodCode());
            line.setMethodName(null);
            line.setRateCode(row.getRateCode());
            line.setRateName(null);
            line.setDispensePerDuration(null);
        }
        line.setPlannedPerformCount(null);
        MedicationRequestDetailQueryModel medDetail = null;
        if (row.getId() != null) {
            medDetail = this.medicationRequestQueryService.loadMedicationRequestById(row.getId());
        }
        line.setMedicationExecuteNum(this.resolvePlannedMedicationPerformCount(row, medDetail));
        line.setPerformCount(null);
        line.setCancelCount(null);
        line.setRefundPairRequestId(null);
        line.setGroupId(row.getGroupId());
        line.setSortNumber(row.getSortNumber());
        // 瓶贴口径：目录 inject_flag 为真即输液，不按用法限制；否则沿用药嘱 infusion_flag
        Boolean infusionFlag = row.getInfusionFlag();
        if (medicationDef != null && Boolean.TRUE.equals(medicationDef.getInjectFlag())) {
            infusionFlag = Boolean.TRUE;
        } else if (infusionFlag == null && medicationDef != null) {
            infusionFlag = medicationDef.getInjectFlag();
        }
        line.setInfusionFlag(infusionFlag);
        if (Boolean.TRUE.equals(infusionFlag)) {
            line.setInfusionFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_YES);
        } else if (Boolean.FALSE.equals(infusionFlag)) {
            line.setInfusionFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO);
        } else {
            line.setInfusionFlagText(null);
        }
        line.setSkinTestFlag(row.getSkinTestFlag());
        line.setTcmFlag(row.getTcmFlag());
        this.applyChargeAggregation(line, TableNameConstants.MED_MEDICATION_REQUEST, row.getId(), chargeByAnchor);
        return line;
    }

    /**
     * 构建耗材处置行（锚点、发放态与计费聚合字段）。
     *
     * @param row                        耗材申请列表行
     * @param encounterId                就诊主键
     * @param chargeByAnchor             计费聚合索引
     * @param deviceDispenses            就诊下发放单行集合
     * @param deviceDispenseIdsByRequest AS-SDD IF-NUR-02 步骤 8：{@code listDeviceDispenseIdsByDeviceRequest} 结果（主发放行取 id 最小）
     * @param catalogContext             目录与类目展示预热上下文
     * @param medicationDisplayWarm      单位术语预热（复用步骤 8b 的 unit_code 映射，供处置单数量列展示）
     * @return 处置行 DTO
     */
    private NurseStationTreatmentLineDto buildDeviceLine(
        DeviceRequestListModel row,
        Long encounterId,
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor,
        List<DeviceDispenseListModel> deviceDispenses,
        Map<Long, List<Long>> deviceDispenseIdsByRequest,
        OrderRequestCatalogWarmContext catalogContext,
        NurseStationMedicationDisplayWarmContext medicationDisplayWarm) {
        NurseStationTreatmentLineDto line = new NurseStationTreatmentLineDto();
        line.setEncounterId(encounterId);
        line.setRequestId(row.getId());
        Long primaryDispenseId = this.resolvePrimaryDispenseId(row.getId(), deviceDispenses, deviceDispenseIdsByRequest);
        line.setDispenseId(primaryDispenseId);
        line.setRequestTable(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_DEVICE);
        line.setOrderType(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_DEVICE);
        DeviceDefinition deviceDef = null;
        if (row.getDeviceDefId() != null && catalogContext != null) {
            deviceDef = catalogContext.getDeviceDefinitionById().get(row.getDeviceDefId());
        }
        String deviceBusNo = null;
        String deviceName = null;
        if (deviceDef != null) {
            deviceBusNo = deviceDef.getBusNo();
            deviceName = deviceDef.getName();
        }
        line.setItemCode(OrderCatalogDisplayAssembler.resolveItemCatalogCode(deviceBusNo, row.getDeviceDefId()));
        line.setItemName(OrderCatalogDisplayAssembler.resolveItemDisplayName(
            deviceName,
            OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LINE_NAME_FALLBACK));
        if (StringUtils.isNotEmpty(row.getDeviceSpecifications())) {
            line.setSpecText(row.getDeviceSpecifications().trim());
        } else if (deviceDef != null && StringUtils.isNotEmpty(deviceDef.getTotalVolume())) {
            line.setSpecText(deviceDef.getTotalVolume().trim());
        } else {
            line.setSpecText(null);
        }
        if (deviceDef != null) {
            line.setPyStr(deviceDef.getPyStr());
            line.setWbStr(deviceDef.getWbStr());
        } else {
            line.setPyStr(null);
            line.setWbStr(null);
        }
        line.setServiceCategoryCode(null);
        line.setServiceCategoryCodeText(null);
        String deviceCategoryCode = null;
        if (deviceDef != null && StringUtils.isNotEmpty(deviceDef.getCategoryCode())) {
            deviceCategoryCode = deviceDef.getCategoryCode().trim();
        }
        line.setDeviceCategoryCode(deviceCategoryCode);
        if (catalogContext != null) {
            line.setDeviceCategoryCodeText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                    catalogContext.getDeviceCategoryLabelByCode(), deviceCategoryCode));
        }
        line.setMedCategoryCode(null);
        line.setMedCategoryCodeText(null);
        line.setQuantity(row.getQuantity());
        line.setUnitCode(row.getUnitCode());
        // Asm：耗材单位展示名（术语 unit_code；无产品回落，对齐处置单打印数量列）
        if (medicationDisplayWarm != null) {
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                medicationDisplayWarm.getUnitLabelByCode(), row.getUnitCode(), null));
        } else {
            line.setUnitName(null);
        }
        line.setPlannedPerformCount(null);
        line.setMedicationExecuteNum(null);
        line.setPerformCount(null);
        line.setCancelCount(null);
        line.setRefundPairRequestId(null);
        line.setGroupId(null);
        line.setSortNumber(row.getSortNumber());
        this.applyChargeAggregation(line, TableNameConstants.WKF_DEVICE_REQUEST, row.getId(), chargeByAnchor);
        line.setDispenseStatusEnum(this.resolveDispenseStatusForRequest(row.getId(), deviceDispenses, primaryDispenseId));
        DispenseStatus dispenseStatus = EnumUtil.fromCode(DispenseStatus.class, line.getDispenseStatusEnum());
        if (dispenseStatus != null) {
            line.setDispenseStatusEnumText(dispenseStatus.getDisplay());
        }
        // Asm：侧栏耗材明细展示签发批号（主发放行）
        line.setLotNumber(this.resolveLotNumberForPrimaryDispense(primaryDispenseId, deviceDispenses));
        return line;
    }

    /**
     * 解析主发放行签发批号（护士站耗材侧栏展示；占位批号仍原样回传由前端空展示）。
     *
     * @param primaryDispenseId 主发放行主键
     * @param deviceDispenses   就诊下发放行集合
     * @return 批号；无发放或批号空时 null
     */
    private String resolveLotNumberForPrimaryDispense(
        Long primaryDispenseId,
        List<DeviceDispenseListModel> deviceDispenses) {
        // Gate：无主发放行则无批号
        if (primaryDispenseId == null || deviceDispenses == null || deviceDispenses.isEmpty()) {
            return null;
        }
        for (DeviceDispenseListModel dispense : deviceDispenses) {
            // Skip：非主发放行
            if (dispense == null || !primaryDispenseId.equals(dispense.getId())) {
                continue;
            }
            String lot = dispense.getLotNumber();
            // 分支：空白批号不写字段
            if (StringUtils.isEmpty(lot)) {
                return null;
            }
            String trimmed = lot.trim();
            // 分支：护士占位批号对护士无意义，侧栏不展示
            if (OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(trimmed)) {
                return null;
            }
            return trimmed;
        }
        return null;
    }

    /**
     * 耗材侧栏是否排除该发放态（退费作废/终止等，禁止再当作「待发放」勾选）。
     *
     * @param dispenseStatusEnum 发放状态码；可空
     * @return 应排除时 true
     */
    private boolean isExcludedNurseStationDeviceSupplyDispenseStatus(String dispenseStatusEnum) {
        // 尚无发放单时由其它门禁处理；此处仅拦明确终态
        if (StringUtils.isEmpty(dispenseStatusEnum)) {
            return false;
        }
        String code = dispenseStatusEnum.trim();
        // 退费未发作废：护士不可再执行
        if (DispenseStatus.DECLINED.getCode().equals(code)) {
            return true;
        }
        // 停发/作废/已退实物：侧栏不展示
        if (DispenseStatus.STOPPED.getCode().equals(code)
            || DispenseStatus.CANCELLED.getCode().equals(code)
            || DispenseStatus.REFUNDED.getCode().equals(code)
            || DispenseStatus.PENDING_REFUND.getCode().equals(code)) {
            return true;
        }
        return false;
    }

    /**
     * 解析某耗材医嘱对应的主发放行主键：优先 map；否则回落为同就诊同请求下 id 最小发放行。
     *
     * @param deviceRequestId            耗材请求主键
     * @param deviceDispenseIdsByRequest 医嘱→发放单 id 列表（IF-NUR-02 步骤 8）；允许 null
     * @param deviceDispenses            发放单行集合
     * @return 发放行主键；可能为 null
     */
    private Long resolvePrimaryDispenseId(
        Long deviceRequestId,
        List<DeviceDispenseListModel> deviceDispenses,
        Map<Long, List<Long>> deviceDispenseIdsByRequest) {
        if (deviceRequestId == null) {
            return null;
        }
        if (deviceDispenseIdsByRequest != null) {
            List<Long> byReq = deviceDispenseIdsByRequest.get(deviceRequestId);
            if (byReq != null && !byReq.isEmpty()) {
                return Collections.min(byReq);
            }
        }
        Long minId = null;
        if (deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (!deviceRequestId.equals(d.getDeviceReqId())) {
                    continue;
                }
                if (d.getId() == null) {
                    continue;
                }
                if (minId == null || d.getId() < minId) {
                    minId = d.getId();
                }
            }
        }
        return minId;
    }

    /**
     * 解析耗材发放状态展示用持久化码：命中主发放行则取其状态，否则为空。
     *
     * @param deviceRequestId 耗材请求主键
     * @param deviceDispenses 发放单行集合
     * @param primaryDispenseId 主发放行主键
     * @return 发放状态码；可能为 null
     */
    private String resolveDispenseStatusForRequest(
        Long deviceRequestId,
        List<DeviceDispenseListModel> deviceDispenses,
        Long primaryDispenseId) {
        if (primaryDispenseId != null && deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (primaryDispenseId.equals(d.getId())) {
                    return d.getStatusEnum();
                }
            }
        }
        if (deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (deviceRequestId != null && deviceRequestId.equals(d.getDeviceReqId())) {
                    return d.getStatusEnum();
                }
            }
        }
        return null;
    }

    /**
     * 回填处置行的计费聚合字段（首条状态与金额和）。
     *
     * @param line           处置行
     * @param serviceTable   请求表持久化名
     * @param requestId      请求主键
     * @param chargeByAnchor 聚合索引
     */
    private void applyChargeAggregation(
        NurseStationTreatmentLineDto line,
        String serviceTable,
        Long requestId,
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor) {
        if (requestId == null || chargeByAnchor == null) {
            return;
        }
        String key = AdviceChargeAnchorRollupHelper.buildChargeAnchorKey(serviceTable, requestId);
        AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation agg = chargeByAnchor.get(key);
        if (agg == null) {
            return;
        }
        String chargeStatusEnum = agg.getChargeStatusEnum();
        line.setChargeStatusEnum(chargeStatusEnum);
        line.setChargeStatusEnumText(
            AdviceChargeDisplayStatusAssembler.resolveChargeStatusDisplayTextForAdviceList(chargeStatusEnum));
        line.setDisplayTotalPrice(agg.getSumTotalPrice());
        // Asm：单价取账单升序首条 unit_price，供耗材侧栏展示（不查当前目录价）
        line.setUnitPrice(agg.getUnitPrice());
    }

    /**
     * 从计费聚合索引读取请求锚点上的收费汇总态码。
     */
    private String resolveAggregatedChargeStatus(
        String serviceTable,
        Long requestId,
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor) {
        if (requestId == null || chargeByAnchor == null) {
            return null;
        }
        String key = AdviceChargeAnchorRollupHelper.buildChargeAnchorKey(serviceTable, requestId);
        if (key == null) {
            return null;
        }
        AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation agg = chargeByAnchor.get(key);
        if (agg == null) {
            return null;
        }
        return agg.getChargeStatusEnum();
    }

    /**
     * 回填列表「状态」列：收费退费态优先，否则执行进度（待执行/已完成）。
     */
    private void finalizeNurseTreatmentLineDisplay(NurseStationTreatmentLineDto line) {
        String executionText = this.resolveNurseTreatmentExecutionDisplayText(line);
        ListDisplayStatus display = AdviceChargeDisplayStatusAssembler.resolveNurseTreatmentLineDisplayStatus(
            line.getChargeStatusEnum(), executionText);
        line.setDisplayStatusEnum(display.displayStatusEnum());
        line.setDisplayStatusEnumText(display.displayStatusEnumText());
    }

    /**
     * 按请求类型与执行次数解析执行态展示文案（不含收费态）。
     */
    private String resolveNurseTreatmentExecutionDisplayText(NurseStationTreatmentLineDto line) {
        if (line == null) {
            return "待执行";
        }
        String requestTable = line.getRequestTable();
        if (OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_SERVICE.equals(requestTable)) {
            int planned = 1;
            if (line.getPlannedPerformCount() != null) {
                planned = line.getPlannedPerformCount().intValue();
            }
            int performed = 0;
            if (line.getPerformCount() != null) {
                performed = line.getPerformCount().intValue();
            }
            int cancelled = 0;
            if (line.getCancelCount() != null) {
                cancelled = line.getCancelCount().intValue();
            }
            int net = performed - cancelled;
            if (net >= planned) {
                return "已完成";
            }
            return "待执行";
        }
        if (OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_MEDICATION.equals(requestTable)) {
            int planned = 1;
            if (line.getMedicationExecuteNum() != null) {
                planned = line.getMedicationExecuteNum().intValue();
            }
            int performed = 0;
            if (line.getPerformCount() != null) {
                performed = line.getPerformCount().intValue();
            }
            int cancelled = 0;
            if (line.getCancelCount() != null) {
                cancelled = line.getCancelCount().intValue();
            }
            int net = performed - cancelled;
            if (planned > 0 && net >= planned) {
                return "已完成";
            }
            return "待执行";
        }
        return "待执行";
    }

    /**
     * IF-NUR-02 步骤 8b：药品行展示术语与产品单位回落预热结果（仅 App 内传递，不序列化）。
     */
    private static final class NurseStationMedicationDisplayWarmContext {
        /** 开立/发药单位 code → 展示名（术语 unit_code）。 */
        private Map<String, String> unitLabelByCode = new HashMap<>();
        /** 给药途径 code → 展示名（术语 method_code）。 */
        private Map<String, String> methodLabelByCode = new HashMap<>();
        /** 中药用法 code → 展示名（术语 tcm_method_code）。 */
        private Map<String, String> tcmMethodLabelByCode = new HashMap<>();
        /** 用药频次 code → 展示名。 */
        private Map<String, String> rateLabelByCode = new HashMap<>();
        /** 剂量单位 code → 展示名（术语 dose_unit）。 */
        private Map<String, String> doseUnitLabelByCode = new HashMap<>();
        /** 药品定义复合键 → 产品展示 DTO（单位/剂量单位字典回落）。 */
        private Map<String, ProductDefinitionDisplayDto> productByKey = new HashMap<>();

        public Map<String, String> getUnitLabelByCode() {
            return unitLabelByCode;
        }

        public void setUnitLabelByCode(Map<String, String> unitLabelByCode) {
            this.unitLabelByCode = unitLabelByCode;
        }

        public Map<String, String> getMethodLabelByCode() {
            return methodLabelByCode;
        }

        public void setMethodLabelByCode(Map<String, String> methodLabelByCode) {
            this.methodLabelByCode = methodLabelByCode;
        }

        public Map<String, String> getTcmMethodLabelByCode() {
            return tcmMethodLabelByCode;
        }

        public void setTcmMethodLabelByCode(Map<String, String> tcmMethodLabelByCode) {
            this.tcmMethodLabelByCode = tcmMethodLabelByCode;
        }

        public Map<String, String> getRateLabelByCode() {
            return rateLabelByCode;
        }

        public void setRateLabelByCode(Map<String, String> rateLabelByCode) {
            this.rateLabelByCode = rateLabelByCode;
        }

        public Map<String, String> getDoseUnitLabelByCode() {
            return doseUnitLabelByCode;
        }

        public void setDoseUnitLabelByCode(Map<String, String> doseUnitLabelByCode) {
            this.doseUnitLabelByCode = doseUnitLabelByCode;
        }

        public Map<String, ProductDefinitionDisplayDto> getProductByKey() {
            return productByKey;
        }

        public void setProductByKey(Map<String, ProductDefinitionDisplayDto> productByKey) {
            this.productByKey = productByKey;
        }
    }

    /**
     * 耗材取消前库存回补快照（须在发放回退前采集批号/库位/数量）。
     * <p>仅用于 IF-NUR-05 耗材回退链路，不对外暴露。</p>
     */
    private static final class DeviceCancelRestoreSnapshot {
        private final Long deviceReqId;
        private final Long locationId;
        private final Long deviceDefId;
        private final String lotNumber;
        private final BigDecimal restoreQty;

        private DeviceCancelRestoreSnapshot(
            Long deviceReqId,
            Long locationId,
            Long deviceDefId,
            String lotNumber,
            BigDecimal restoreQty) {
            this.deviceReqId = deviceReqId;
            this.locationId = locationId;
            this.deviceDefId = deviceDefId;
            this.lotNumber = lotNumber;
            this.restoreQty = restoreQty;
        }

        public Long getDeviceReqId() {
            return deviceReqId;
        }

        public Long getLocationId() {
            return locationId;
        }

        public Long getDeviceDefId() {
            return deviceDefId;
        }

        public String getLotNumber() {
            return lotNumber;
        }

        public BigDecimal getRestoreQty() {
            return restoreQty;
        }
    }

}
