/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.execution.service;

import com.openhis.health.application.inpatient.nursestation.ATDManager.support.InpatientPatientQueueNursingLevelEnrichAssembler;
import com.openhis.health.application.inpatient.nursestation.ATDManager.support.dto.InpatientPatientQueueNursingLevelDisplayRow;
import com.openhis.health.application.medicaltech.execution.dto.MedicalTechCancelDeviceItemDto;
import com.openhis.health.application.medicaltech.execution.dto.MedicalTechCancelProcedureItemDto;
import com.openhis.health.application.medicaltech.execution.dto.MedicalTechExecutionLineDto;
import com.openhis.health.application.medicaltech.execution.dto.MedicalTechPatientQueueItemDto;
import com.openhis.health.application.medicaltech.execution.dto.MedicalTechPerformItemDto;
import com.openhis.health.application.medicaltech.execution.dto.MedicalTechProcedureRecordDto;
import com.openhis.health.application.medicaltech.execution.mapper.MedicalTechExecutionMapper;
import com.openhis.health.application.medicaltech.execution.request.BatchCancelMedicalTechExecutionRequest;
import com.openhis.health.application.medicaltech.execution.request.BatchPerformMedicalTechExecutionRequest;
import com.openhis.health.application.medicaltech.execution.request.ListMedicalTechNetRemainingProceduresRequest;
import com.openhis.health.application.medicaltech.execution.request.LoadMedicalTechExecutionSheetRequest;
import com.openhis.health.application.medicaltech.execution.request.PageMedicalTechPatientQueueRequest;
import com.openhis.health.application.medicaltech.execution.request.PageMedicalTechProcedureRecordsRequest;
import com.openhis.health.application.medicaltech.execution.response.BatchCancelMedicalTechExecutionResponse;
import com.openhis.health.application.medicaltech.execution.response.BatchPerformMedicalTechExecutionResponse;
import com.openhis.health.application.medicaltech.execution.response.ListMedicalTechNetRemainingProceduresResponse;
import com.openhis.health.application.medicaltech.execution.response.LoadMedicalTechExecutionSheetResponse;
import com.openhis.health.application.medicaltech.execution.response.PageMedicalTechPatientQueueResponse;
import com.openhis.health.application.medicaltech.execution.response.PageMedicalTechProcedureRecordsResponse;
import com.openhis.health.application.outpatient.nursestation.treatment.support.AdviceChargeAnchorRollupHelper;
import com.openhis.health.application.shared.advice.AdviceChargeDisplayStatusAssembler;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.advice.OrderRequestCatalogWarmAssembler;
import com.openhis.health.application.shared.advice.OrderRequestCatalogWarmContext;
import com.openhis.health.application.shared.advice.ServiceAdviceDraftCommandHelper;
import com.openhis.health.application.shared.advice.SpecimenTubeMergeBindResolveSupport;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.application.shared.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayResult;
import com.openhis.health.application.shared.medicaltech.MedicalTechBilateralHomologySupport;
import com.openhis.health.application.shared.nursestation.DeviceAutoInventoryDeductSettingResolver;
import com.openhis.health.application.shared.nursestation.DeviceDispenseNurseWorkflowOrchestrator;
import com.openhis.health.application.shared.nursestation.ProcedureExecutionPerformerOrchestrator;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.MedicalTechWorkstationQueueQuery;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureDetailQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureDomainService;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.clinical.procedure.support.ProcedurePerformCountGate;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.MedicalTechExecutionConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.devicedispense.entity.DeviceDispense;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医技工作站·医技执行应用编排（AS-MT-EXE-001，IF-MT-EXE-01～05、03B）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link EncounterQueryService}：医技队列分页与就诊门闸。</li>
 *   <li>{@link EncounterContextReader}：队列人口学上下文批量装配。</li>
 *   <li>{@link ServiceRequestQueryService} / {@link DeviceRequestQueryService} /
 *       {@link DeviceDispenseQueryService}：执行单装载与本站可见性轴过滤。</li>
 *   <li>{@link ChargeItemQueryService}：门诊已计费门控与列表金额聚合。</li>
 *   <li>{@link ProcedureQueryService} / {@link ProcedureDomainService}：次数、记录、完成/取消写链
 *       （{@code org_id}=登录 TECH）。</li>
 *   <li>{@link DeviceDispenseNurseWorkflowOrchestrator}：耗材发放/回退（与护士站同开关）。</li>
 *   <li>{@link MedicalTechBilateralHomologySupport}：取消同态已废；出院确费门禁在入出转侧。</li>
 *   <li>{@link MedicalTechExecutionMapper}：Request/Query/Command 与 Response 装配。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/medicalTechExecution/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalTechExecutionAppService {

    /** 会话登录科室（须 TECH）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /** 组织只读：TECH 门禁与耗材房上级科室。 */
    private final OrganizationQueryService organizationQueryService;

    /** 位置只读：发放库位→组织。 */
    private final LocationQueryService locationQueryService;

    /** 就诊只读：队列与门闸。 */
    private final EncounterQueryService encounterQueryService;

    /** 就诊上下文：队列人口学 enrich。 */
    private final EncounterContextReader encounterContextReader;

    /** 费用性质展示。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;

    /** 诊疗请求只读。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 耗材请求只读。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /** 耗材发放只读。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 收费只读。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 账户只读：住院队列余额展示（个人现金户）。 */
    private final AccountQueryService accountQueryService;

    /** 入院诊断摘要：住院队列诊断列。 */
    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;

    /** 护理级别 enrich：住院队列护理级别展示。 */
    private final InpatientPatientQueueNursingLevelEnrichAssembler inpatientPatientQueueNursingLevelEnrichAssembler;

    /** 药品发放只读（收费锚点归并）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 执行记录只读。 */
    private final ProcedureQueryService procedureQueryService;

    /** 执行记录写。 */
    private final ProcedureDomainService procedureDomainService;

    /** 执行次数门控。 */
    private final ProcedurePerformCountGate procedurePerformCountGate;

    /** 耗材发放编排。 */
    private final DeviceDispenseNurseWorkflowOrchestrator deviceDispenseNurseWorkflowOrchestrator;

    /** 扣库开关。 */
    private final DeviceAutoInventoryDeductSettingResolver deviceAutoInventoryDeductSettingResolver;

    /** 库存写。 */
    private final InventoryItemDomainService inventoryItemDomainService;

    /** 主执行人登记。 */
    private final ProcedureExecutionPerformerOrchestrator procedureExecutionPerformerOrchestrator;

    /** 当前医务人员。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 目录预热。 */
    private final OrderRequestCatalogWarmAssembler orderRequestCatalogWarmAssembler;

    /** 医务人员只读：执行记录操作人展示名。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 术语选项：单位码 → 展示名。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 目录现价解析（执行单无账单时单价/总价预览；有 ChargeItem 仍以落库价为准）。
     */
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /** 医技协同门禁（取消同态已废）。 */
    private final MedicalTechBilateralHomologySupport medicalTechBilateralHomologySupport;

    /** 字段映射。 */
    private final MedicalTechExecutionMapper medicalTechExecutionMapper;

    /** 检验合管设备：同一 activity 多行只标记一个设备主键，供采血贴只打第一张。 */
    private final SpecimenTubeMergeBindResolveSupport specimenTubeMergeBindResolveSupport;

    /**
     * IF-MT-EXE-01：分页查询医技患者队列。
     *
     * @param request 队列筛选与分页
     * @return 当前页队列（{@code content}/{@code totalCount}，继承 {@code PagedResult}）
     * @throws com.openhis.whale.core.application.exception.BusinessException 非 TECH 登录或 scope 非法（EX-MT-EXE-101）
     */
    public PageMedicalTechPatientQueueResponse pageMedicalTechPatientQueue(
        PageMedicalTechPatientQueueRequest request) {
        // Gate：登录科须 TECH；scope 合法
        Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechExecutionConstants.EX_MT_EXE_101);
        String scope = null;
        if (request != null) {
            scope = request.getEncounterScope();
        }
        this.assertEncounterScope(scope, MedicalTechExecutionConstants.EX_MT_EXE_101);
        // Pre：映射队列查询并分页
        MedicalTechWorkstationQueueQuery queueQuery =
            this.medicalTechExecutionMapper.toQueueQuery(request, loginTechOrgId);
        // 住院不按就诊开始时间筛（在院跨越多日；仅门诊用 startDate/endDate）
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_INPATIENT.equals(scope)) {
            queueQuery.setStartDate(null);
            queueQuery.setEndDate(null);
        }
        Page<Encounter> page = this.encounterQueryService.pageMedicalTechWorkstationQueue(queueQuery);
        // Asm：本页人口学 + 费用性质 + 本站待发红点
        List<MedicalTechPatientQueueItemDto> records =
            this.assembleQueueRows(page.getContent(), scope, loginTechOrgId);
        return this.medicalTechExecutionMapper.toPageQueueResponse(page, records);
    }

    /**
     * IF-MT-EXE-02：加载医技执行单（本站 TECH 轴；无药品）。
     *
     * @param request 就诊与类目过滤
     * @return 诊疗/耗材两列表
     * @throws com.openhis.whale.core.application.exception.BusinessException 非 TECH、就诊不可见或查询失败（EX-MT-EXE-102）
     */
    public LoadMedicalTechExecutionSheetResponse loadMedicalTechExecutionSheet(
        LoadMedicalTechExecutionSheetRequest request) {
        // Gate：TECH + 入参
        Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechExecutionConstants.EX_MT_EXE_102);
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_102, "encounterId 不能为空");
        }
        this.assertEncounterScope(request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_102);
        // Pre：就诊存在且类别匹配 scope
        EncounterDetailQueryModel encounter =
            this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        this.assertEncounterMatchesScope(
            encounter.getClassEnum(), request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_102);
        // Pre：装载收费与请求
        ChargeItemQueryCommand chargeCmd =
            this.medicalTechExecutionMapper.toChargeItemQueryCommand(request.getEncounterId());
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByEncounterId(chargeCmd);
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor =
            AdviceChargeAnchorRollupHelper.buildRequestAnchorChargeIndex(
                chargeItems, this.medicationDispenseQueryService, this.deviceDispenseQueryService);
        List<ServiceRequestListModel> serviceRequests =
            this.serviceRequestQueryService.listServiceRequestsByEncounter(request.getEncounterId());
        List<DeviceRequestListModel> deviceRequests =
            this.deviceRequestQueryService.listDeviceRequestsByEncounter(request.getEncounterId());
        List<DeviceDispenseListModel> deviceDispenses =
            this.deviceDispenseQueryService.listDeviceDispensesByEncounter(request.getEncounterId());
        Map<Long, Long> dispenseOrgByDispenseId =
            this.mapDispenseIdToOrganizationId(deviceRequests, deviceDispenses);
        OrderRequestCatalogWarmContext catalogContext =
            this.orderRequestCatalogWarmAssembler.prepareFromTreatmentSheetRows(
                serviceRequests, Collections.emptyList(), deviceRequests);
        // Pre：本页诊疗/耗材目录现价批量预取（无账单时单价总价预览；禁 N+1）
        Map<String, List<ChargeItemDefinition>> catalogPriceByProductKey =
            this.loadCatalogPriceLinesForExecutionSheet(catalogContext);
        // Pre：单位术语映射（禁止把 unitCode 原样当 unitName）
        Map<String, String> unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        LinkedHashSet<Long> serviceActivityIds = new LinkedHashSet<>();
        for (ServiceRequestListModel serviceRow : serviceRequests) {
            if (serviceRow != null && serviceRow.getActivityId() != null) {
                serviceActivityIds.add(serviceRow.getActivityId());
            }
        }
        // Pre：合管设备按诊疗定义批量解析，避免逐行查
        Map<Long, Long> mergeDeviceByActivityId =
            this.specimenTubeMergeBindResolveSupport.mapMergeDeviceIdByActivityIds(serviceActivityIds);
        List<MedicalTechExecutionLineDto> serviceLines = new ArrayList<>();
        for (ServiceRequestListModel row : serviceRequests) {
            // 仅本登录 TECH 科诊疗
            if (row == null || row.getId() == null || !loginTechOrgId.equals(row.getOrgId())) {
                continue;
            }
            if (AdviceChargeDisplayStatusAssembler.isRefundOrderCopyRequest(row.getRefundServiceId())) {
                continue;
            }
            String chargeStatus = this.resolveAggregatedChargeStatus(
                TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), chargeByAnchor);
            // 执行单行纳入与队列有活同轴：门诊 COMPLETED；住院 VERIFIED（禁止队列有人、明细空单）
            if (!this.shouldIncludeMedicalTechSheetRequestRow(
                request.getEncounterScope(),
                row.getStatusEnum(),
                TableNameConstants.WKF_SERVICE_REQUEST,
                row.getId(),
                chargeStatus)) {
                continue;
            }
            if (StringUtils.isNotEmpty(request.getServiceCategoryCode())) {
                if (row.getTherapyEnum() == null
                    || !request.getServiceCategoryCode().trim().equals(row.getTherapyEnum().trim())) {
                    continue;
                }
            }
            MedicalTechExecutionLineDto line = this.buildServiceLine(
                row,
                chargeByAnchor,
                catalogContext,
                loginTechOrgId,
                unitLabelByCode,
                catalogPriceByProductKey,
                mergeDeviceByActivityId);
            serviceLines.add(line);
        }
        List<MedicalTechExecutionLineDto> deviceLines = new ArrayList<>();
        for (DeviceRequestListModel row : deviceRequests) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (AdviceChargeDisplayStatusAssembler.isRefundOrderCopyRequest(row.getRefundDeviceId())) {
                continue;
            }
            Long primaryDispenseId = this.resolvePrimaryDispenseId(row.getId(), deviceDispenses);
            // 仅本登录 TECH 科耗材房发放
            if (!this.isDispenseBelongToTechOrg(primaryDispenseId, row.getLocationId(), dispenseOrgByDispenseId, loginTechOrgId)) {
                continue;
            }
            String chargeStatus = this.resolveAggregatedChargeStatus(
                TableNameConstants.WKF_DEVICE_REQUEST, row.getId(), chargeByAnchor);
            // 耗材：门诊/住院均以已完成请求或退费展示态纳入（划价落库 COMPLETED）
            if (!this.shouldIncludeMedicalTechSheetRequestRow(
                request.getEncounterScope(),
                row.getStatusEnum(),
                TableNameConstants.WKF_DEVICE_REQUEST,
                row.getId(),
                chargeStatus)) {
                continue;
            }
            MedicalTechExecutionLineDto line = this.buildDeviceLine(
                row,
                chargeByAnchor,
                catalogContext,
                deviceDispenses,
                primaryDispenseId,
                unitLabelByCode,
                catalogPriceByProductKey);
            deviceLines.add(line);
        }
        // Asm：包装响应
        return this.medicalTechExecutionMapper.toSheetResponse(serviceLines, deviceLines);
    }

    /**
     * IF-MT-EXE-03：分页查询医技执行记录（仅 loginTech org_id）。
     *
     * @param request 就诊与分页筛选
     * @return 执行记录分页（{@code content}/{@code totalCount}）
     * @throws com.openhis.whale.core.application.exception.BusinessException 非 TECH 或查询失败（EX-MT-EXE-103）
     */
    public PageMedicalTechProcedureRecordsResponse pageMedicalTechProcedureRecords(
        PageMedicalTechProcedureRecordsRequest request) {
        // Gate：TECH + 入参
        Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechExecutionConstants.EX_MT_EXE_103);
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_103, "encounterId 不能为空");
        }
        this.assertEncounterScope(request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_103);
        // Pre：就诊门闸
        EncounterDetailQueryModel encounter =
            this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        this.assertEncounterMatchesScope(
            encounter.getClassEnum(), request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_103);
        // Pre：装载本就诊 Procedure，内存按 orgId / requestId / status 过滤后分页
        List<Procedure> all = this.procedureQueryService.listByEncounterIds(
            Collections.singleton(request.getEncounterId()));
        List<ProcedureRecordQueryModel> filtered = new ArrayList<>();
        for (Procedure entity : all) {
            if (entity == null || entity.getId() == null) {
                continue;
            }
            // 仅本站医技侧
            if (!loginTechOrgId.equals(entity.getOrgId())) {
                continue;
            }
            if (request.getRequestId() != null && !request.getRequestId().equals(entity.getRequestId())) {
                continue;
            }
            if (StringUtils.isNotEmpty(request.getStatusEnum())
                && !request.getStatusEnum().trim().equals(entity.getStatusEnum())) {
                continue;
            }
            filtered.add(this.toProcedureRecordQueryModel(entity));
        }
        filtered.sort(this.procedureRecordTimeDescComparator());
        int pageNo = 1;
        int pageSize = 20;
        if (request.getPageNo() != null && request.getPageNo() > 0) {
            pageNo = request.getPageNo();
        }
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        }
        int from = (pageNo - 1) * pageSize;
        List<MedicalTechProcedureRecordDto> content = new ArrayList<>();
        List<ProcedureRecordQueryModel> pageRows = Collections.emptyList();
        if (from < filtered.size()) {
            int to = from + pageSize;
            if (to > filtered.size()) {
                to = filtered.size();
            }
            pageRows = filtered.subList(from, to);
            for (ProcedureRecordQueryModel row : pageRows) {
                MedicalTechProcedureRecordDto dto = this.medicalTechExecutionMapper.toProcedureRecordDto(row);
                if (dto != null) {
                    content.add(dto);
                }
            }
        }
        // Asm：回填项目名 / 操作人（mapper 仅映射 Procedure 本表字段）
        this.enrichProcedureRecordDisplays(request.getEncounterId(), pageRows, content);
        Page<ProcedureRecordQueryModel> pageShell =
            new PageImpl<>(filtered.subList(Math.min(from, filtered.size()), Math.min(from + pageSize, filtered.size())),
                PageRequest.of(pageNo - 1, pageSize), filtered.size());
        // Asm：禁止手写 getNumber()+1，走 PagedResultBuilder
        return this.medicalTechExecutionMapper.toPageProcedureRecordsResponse(content, pageShell);
    }

    /**
     * IF-MT-EXE-03B：查询净剩可取消执行记录（仅 loginTech org_id）。
     *
     * @param request 就诊与可选缩窄
     * @return 净剩列表
     * @throws com.openhis.whale.core.application.exception.BusinessException 非 TECH 或查询失败（EX-MT-EXE-103）
     */
    public ListMedicalTechNetRemainingProceduresResponse listMedicalTechNetRemainingProcedures(
        ListMedicalTechNetRemainingProceduresRequest request) {
        // Gate：TECH + 入参
        Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechExecutionConstants.EX_MT_EXE_103);
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_103, "encounterId 不能为空");
        }
        this.assertEncounterScope(request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_103);
        if (request.getRequestId() != null && StringUtils.isEmpty(request.getRequestTable())) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_103, "缩窄来源请求时 requestTable 不能为空");
        }
        // Pre：就诊门闸
        EncounterDetailQueryModel encounter =
            this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        this.assertEncounterMatchesScope(
            encounter.getClassEnum(), request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_103);
        List<ProcedureRecordQueryModel> netRows =
            this.procedureQueryService.listNetRemainingCompletedProcedureRecords(
                request.getEncounterId(), request.getRequestId(), request.getRequestTable());
        List<ProcedureRecordQueryModel> netSourceRows = new ArrayList<>();
        List<MedicalTechProcedureRecordDto> records = new ArrayList<>();
        for (ProcedureRecordQueryModel row : netRows) {
            // 仅本站医技侧
            if (row == null || !loginTechOrgId.equals(row.getOrgId())) {
                continue;
            }
            MedicalTechProcedureRecordDto dto = this.medicalTechExecutionMapper.toProcedureRecordDto(row);
            if (dto != null) {
                netSourceRows.add(row);
                records.add(dto);
            }
        }
        // Asm：回填项目名 / 操作人
        this.enrichProcedureRecordDisplays(request.getEncounterId(), netSourceRows, records);
        // Asm
        return this.medicalTechExecutionMapper.toNetRemainingResponse(records);
    }

    /**
     * IF-MT-EXE-04：批量医技执行。
     *
     * <p>门诊：已计费门控（EX-201）。住院：写医技侧 Procedure（{@code org_id}=登录 TECH），不建诊疗 Charge；
     * <strong>不</strong>校验护士/医技执行先后（EX-202 预留不抛）。</p>
     *
     * @param request 勾选诊疗/耗材
     * @return 成功条数与提示
     * @throws com.openhis.whale.core.application.exception.BusinessException EX-MT-EXE-201/203～205
     */
    public BatchPerformMedicalTechExecutionResponse batchPerformMedicalTechExecution(
        BatchPerformMedicalTechExecutionRequest request) {
        // Gate：TECH + 非空勾选
        Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechExecutionConstants.EX_MT_EXE_201);
        this.validateBatchPerformGate(request);
        this.assertEncounterScope(request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_201);
        // Pre：就诊
        EncounterDetailQueryModel encounter =
            this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        this.assertEncounterMatchesScope(
            encounter.getClassEnum(), request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_201);
        boolean outpatient = EncounterClass.AMB.getCode().equals(encounter.getClassEnum());
        ChargeItemQueryCommand chargeCmd =
            this.medicalTechExecutionMapper.toChargeItemQueryCommand(request.getEncounterId());
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByEncounterId(chargeCmd);
        Long practitionerId = this.currentPractitionerResolver.requirePractitionerId(
            MedicalTechExecutionConstants.EX_MT_EXE_201, "未解析到当前医务人员，无法医技执行");
        List<MedicalTechPerformItemDto> serviceItems = this.safeList(request.getServiceItems());
        List<MedicalTechPerformItemDto> deviceItems = this.safeList(request.getDeviceItems());
        // Pre：本站归属 + 门诊已计费门控
        List<Long> serviceRequestIds = new ArrayList<>();
        Map<Long, ServiceRequestListModel> serviceById = new HashMap<>();
        for (ServiceRequestListModel row :
            this.serviceRequestQueryService.listServiceRequestsByEncounter(request.getEncounterId())) {
            if (row != null && row.getId() != null) {
                serviceById.put(row.getId(), row);
            }
        }
        for (MedicalTechPerformItemDto item : serviceItems) {
            if (item == null || item.getRequestId() == null) {
                continue;
            }
            ServiceRequestListModel row = serviceById.get(item.getRequestId());
            // 业务：须属本登录 TECH 科
            if (row == null || !loginTechOrgId.equals(row.getOrgId())) {
                throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_201, "所选诊疗不属于本医技科");
            }
            serviceRequestIds.add(item.getRequestId());
        }
        if (outpatient) {
            List<Long> billable = this.retainBillableRequestIds(
                serviceRequestIds, TableNameConstants.WKF_SERVICE_REQUEST, chargeItems);
            if (billable.size() != serviceRequestIds.size()) {
                throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_201, "门诊诊疗未计费，无法医技执行");
            }
        }
        List<Long> deviceDispenseIds = new ArrayList<>();
        for (MedicalTechPerformItemDto item : deviceItems) {
            if (item == null || item.getDispenseId() == null) {
                continue;
            }
            deviceDispenseIds.add(item.getDispenseId());
        }
        if (!deviceDispenseIds.isEmpty() && this.deviceAutoInventoryDeductSettingResolver.isEnabled()) {
            this.deviceDispenseNurseWorkflowOrchestrator.validateOutpatientAutoDeductInventoryLocation(
                deviceDispenseIds, request.getDeviceLocationId());
        }
        // Pre：次数门控（医技侧 orgId）
        for (Long serviceRequestId : serviceRequestIds) {
            ServiceRequestListModel listRow = serviceById.get(serviceRequestId);
            int planned = ServiceAdviceDraftCommandHelper.resolvePlannedServicePerformCount(listRow);
            try {
                this.procedurePerformCountGate.assertPerformCountBelowPlanned(
                    serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST, planned, loginTechOrgId);
            } catch (BusinessException ex) {
                throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_203, "计划执行次数已满，请刷新后重试");
            }
        }
        int successCount = 0;
        // Cmd：诊疗 create→start→complete，写 orgId=loginTech
        for (Long serviceRequestId : serviceRequestIds) {
            ServiceRequestListModel listRow = serviceById.get(serviceRequestId);
            ServiceRequestQueryModel sr = this.serviceRequestQueryService.mustGetServiceRequest(serviceRequestId);
            int planned = ServiceAdviceDraftCommandHelper.resolvePlannedServicePerformCount(listRow);
            this.procedurePerformCountGate.assertPerformCountBelowPlanned(
                serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST, planned, loginTechOrgId);
            LocalDateTime now = LocalDateTime.now();
            CreateProcedureRecordCommand createCmd = this.medicalTechExecutionMapper.toCreateProcedureRecordCommand(
                serviceRequestId,
                TableNameConstants.WKF_SERVICE_REQUEST,
                request.getEncounterId(),
                encounter.getPatientId(),
                sr.getCategoryEnum(),
                now,
                practitionerId);
            createCmd.setOrgId(loginTechOrgId);
            Long procedureId = this.procedureDomainService.createProcedureRecord(createCmd);
            this.procedureDomainService.startProcedureExecution(
                this.medicalTechExecutionMapper.toStartProcedureExecutionCommand(procedureId));
            // Cmd：执行人须在 COMPLETED 前挂入（DM-CLN-004 §6.1.3）
            this.procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
                procedureId, practitionerId, practitionerId, now);
            this.procedureDomainService.completeProcedureRecord(
                this.medicalTechExecutionMapper.toCompleteProcedureRecordCommand(procedureId, practitionerId));
            successCount++;
        }
        // Cmd：耗材发放
        if (!deviceDispenseIds.isEmpty()) {
            List<Long> sorted = new ArrayList<>(deviceDispenseIds);
            Collections.sort(sorted);
            List<Long> completedDispenseIds = new ArrayList<>();
            Map<Long, BigDecimal> inventoryIdToDecreaseQty = new HashMap<>();
            this.deviceDispenseNurseWorkflowOrchestrator.applyOutpatientNurseDeviceDispenses(
                sorted,
                practitionerId,
                practitionerId,
                practitionerId,
                request.getDeviceLocationId(),
                completedDispenseIds,
                inventoryIdToDecreaseQty);
            if (!inventoryIdToDecreaseQty.isEmpty()) {
                this.inventoryItemDomainService.decreaseQuantitiesMerged(
                    practitionerId, inventoryIdToDecreaseQty);
            }
            successCount = successCount + completedDispenseIds.size();
        }
        // Asm
        BatchPerformMedicalTechExecutionResponse response = new BatchPerformMedicalTechExecutionResponse();
        response.setSuccessCount(Integer.valueOf(successCount));
        response.setMessage("医技执行成功");
        log.info(
            "MedicalTechExecution batchPerform operatorId={} encounterId={} services={} devices={}",
            practitionerId,
            request.getEncounterId(),
            serviceRequestIds,
            deviceDispenseIds);
        return response;
    }

    /**
     * IF-MT-EXE-05：批量取消医技执行。
     *
     * <p>软落地：取消本侧不互卡护士侧；出院仅卡医技相关费用确费（见协同 DD）。</p>
     *
     * @param request 取消勾选
     * @return 成功条数与提示
     * @throws com.openhis.whale.core.application.exception.BusinessException EX-MT-EXE-211～213
     */
    public BatchCancelMedicalTechExecutionResponse batchCancelMedicalTechExecution(
        BatchCancelMedicalTechExecutionRequest request) {
        // Gate：TECH + 非空勾选
        Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechExecutionConstants.EX_MT_EXE_211);
        this.validateBatchCancelGate(request);
        this.assertEncounterScope(request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_211);
        // Pre：就诊
        EncounterDetailQueryModel encounter =
            this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        this.assertEncounterMatchesScope(
            encounter.getClassEnum(), request.getEncounterScope(), MedicalTechExecutionConstants.EX_MT_EXE_211);
        boolean inpatient = EncounterClass.IMP.getCode().equals(encounter.getClassEnum());
        Long practitionerId = this.currentPractitionerResolver.requirePractitionerId(
            MedicalTechExecutionConstants.EX_MT_EXE_211, "未解析到当前医务人员，无法取消医技执行");
        List<MedicalTechCancelProcedureItemDto> procedureItems = this.safeList(request.getProcedureItems());
        Map<Long, ProcedureRecordQueryModel> completedById = new HashMap<>();
        for (MedicalTechCancelProcedureItemDto item : procedureItems) {
            if (item == null || item.getProcedureId() == null) {
                continue;
            }
            ProcedureRecordQueryModel row =
                this.mustResolveCancellableCompletedProcedure(
                    item.getProcedureId(), request.getEncounterId(), loginTechOrgId);
            completedById.put(item.getProcedureId(), row);
        }
        // Pre：取消同态已废——空操作（协同 DD 软落地）
        if (inpatient && !completedById.isEmpty()) {
            this.medicalTechBilateralHomologySupport.assertTechCancelHomology(completedById.values());
        }
        int successCount = 0;
        // Cmd：诊疗 CANCEL 对冲
        for (Map.Entry<Long, ProcedureRecordQueryModel> entry : completedById.entrySet()) {
            ProcedureRecordQueryModel completedRow = entry.getValue();
            LocalDateTime now = LocalDateTime.now();
            CreateProcedureRecordCommand createCmd = this.medicalTechExecutionMapper.toCreateProcedureRecordCommand(
                completedRow.getRequestId(),
                completedRow.getRequestTable(),
                request.getEncounterId(),
                encounter.getPatientId(),
                completedRow.getCategoryEnum(),
                now,
                practitionerId);
            createCmd.setOrgId(loginTechOrgId);
            Long cancelProcedureId = this.procedureDomainService.createProcedureRecord(createCmd);
            this.procedureDomainService.startProcedureExecution(
                this.medicalTechExecutionMapper.toStartProcedureExecutionCommand(cancelProcedureId));
            // Cmd：取消操作人须在 CANCEL 前挂入（DM-CLN-004 §6.1.3）
            this.procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
                cancelProcedureId, practitionerId, practitionerId, now);
            this.procedureDomainService.cancelProcedureRecord(
                this.medicalTechExecutionMapper.toCancelProcedureRecordCommand(
                    cancelProcedureId, "医技取消执行", entry.getKey(), practitionerId));
            successCount++;
        }
        // Cmd：耗材回退
        List<Long> dispenseIds = new ArrayList<>();
        for (MedicalTechCancelDeviceItemDto item : this.safeList(request.getDeviceItems())) {
            if (item != null && item.getDispenseId() != null) {
                dispenseIds.add(item.getDispenseId());
            }
        }
        if (!dispenseIds.isEmpty()) {
            List<Long> sorted = new ArrayList<>(dispenseIds);
            Collections.sort(sorted);
            List<Long> reverted = new ArrayList<>();
            Map<Long, BigDecimal> inventoryIdToIncreaseQty = new HashMap<>();
            this.deviceDispenseNurseWorkflowOrchestrator.revertOutpatientNurseDeviceDispenses(
                sorted, practitionerId, reverted, inventoryIdToIncreaseQty);
            if (!inventoryIdToIncreaseQty.isEmpty()) {
                this.inventoryItemDomainService.increaseQuantities(
                    this.medicalTechExecutionMapper.toIncreaseQuantityCommand(
                        practitionerId, inventoryIdToIncreaseQty));
            }
            successCount = successCount + reverted.size();
        }
        // Asm
        BatchCancelMedicalTechExecutionResponse response = new BatchCancelMedicalTechExecutionResponse();
        response.setSuccessCount(Integer.valueOf(successCount));
        response.setMessage("取消医技执行成功");
        return response;
    }

    /**
     * 断言当前登录组织为单位且 class=TECH，返回组织主键。
     *
     * @param errorCode 失败码
     * @return 登录 TECH 科主键
     */
    private Long requireLoginTechOrgId(String errorCode) {
        Long orgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            errorCode, "未登录或机构上下文缺失");
        Organization org = this.organizationQueryService.loadOrganizationByIdForDisplay(orgId);
        // 业务：非 TECH 科禁止进入医技执行
        if (org == null || org.getClassEnum() != OrganizationClassification.TECH) {
            throw new BusinessException(errorCode, "当前登录科室不是医技科");
        }
        return orgId;
    }

    /**
     * 断言 encounterScope 合法。
     *
     * @param encounterScope scope
     * @param errorCode     失败码
     */
    private void assertEncounterScope(String encounterScope, String errorCode) {
        if (StringUtils.isEmpty(encounterScope)) {
            throw new BusinessException(errorCode, "encounterScope 不能为空");
        }
        String scope = encounterScope.trim();
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(scope)) {
            return;
        }
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_INPATIENT.equals(scope)) {
            return;
        }
        throw new BusinessException(errorCode, "encounterScope 非法");
    }

    /**
     * 断言就诊类别与 scope 一致。
     *
     * @param classEnum 就诊 class
     * @param scope     请求 scope
     * @param errorCode 失败码
     */
    private void assertEncounterMatchesScope(String classEnum, String scope, String errorCode) {
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(scope)
            && EncounterClass.AMB.getCode().equals(classEnum)) {
            return;
        }
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_INPATIENT.equals(scope)
            && EncounterClass.IMP.getCode().equals(classEnum)) {
            return;
        }
        throw new BusinessException(errorCode, "就诊类别与 encounterScope 不一致");
    }

    /**
     * 装配队列行（人口学 + 合同 + 本站待发红点；住院另含余额/护理级/病区床位/诊断）。
     *
     * <p>门诊与住院均投影手机号、身份证号，供费用清单/执行页信息条回显。</p>
     *
     * @param encounters     本页 Encounter
     * @param encounterScope scope
     * @param loginTechOrgId 登录 TECH
     * @return 队列 DTO
     */
    private List<MedicalTechPatientQueueItemDto> assembleQueueRows(
        List<Encounter> encounters,
        String encounterScope,
        Long loginTechOrgId) {
        List<MedicalTechPatientQueueItemDto> rows = new ArrayList<>();
        if (encounters == null || encounters.isEmpty()) {
            return rows;
        }
        List<Long> encounterIdsOrdered = new ArrayList<>();
        for (Encounter encounter : encounters) {
            if (encounter != null && encounter.getId() != null) {
                encounterIdsOrdered.add(encounter.getId());
            }
        }
        Map<Long, String> contractByEncounter =
            this.encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(encounterIdsOrdered);
        Set<Long> pendingDeviceEncounterIds =
            this.collectPendingDeviceEncounterIdsForTech(encounterIdsOrdered, loginTechOrgId);
        boolean outpatient = MedicalTechExecutionConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(encounterScope);
        if (outpatient) {
            List<OutpatientEncounterContextQueryModel> contexts =
                this.encounterContextReader.listOutpatientContextsByEncounterIdsOrdered(encounterIdsOrdered);
            Map<Long, OutpatientEncounterContextQueryModel> byId = new HashMap<>();
            for (OutpatientEncounterContextQueryModel ctx : contexts) {
                if (ctx != null && ctx.getEncounterId() != null) {
                    byId.put(ctx.getEncounterId(), ctx);
                }
            }
            for (Long encounterId : encounterIdsOrdered) {
                OutpatientEncounterContextQueryModel ctx = byId.get(encounterId);
                MedicalTechPatientQueueItemDto dto = new MedicalTechPatientQueueItemDto();
                dto.setEncounterId(encounterId);
                if (ctx != null) {
                    dto.setPatientName(ctx.getPatientName());
                    dto.setGenderEnum(ctx.getGenderEnum());
                    AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, ctx.getGenderEnum());
                    if (gender != null) {
                        dto.setGenderEnumText(gender.getDisplay());
                    }
                    if (ctx.getBirthDate() != null) {
                        dto.setBirthDate(ctx.getBirthDate().toLocalDate());
                    }
                    dto.setAgeText(ctx.getAgeText());
                    dto.setSlipNo(ctx.getEncounterBusNo());
                    dto.setEncounterTime(ctx.getStartTime());
                    // 信息条回显：门诊上下文带手机号/身份证
                    dto.setPhone(ctx.getPhone());
                    dto.setIdCard(ctx.getIdCard());
                }
                dto.setContractDisplay(contractByEncounter.get(encounterId));
                dto.setHasPendingDevice(Boolean.valueOf(pendingDeviceEncounterIds.contains(encounterId)));
                rows.add(dto);
            }
            return rows;
        }
        List<InpatientEncounterContextQueryModel> contexts =
            this.encounterContextReader.listInpatientContextsByEncounterIdsOrdered(encounterIdsOrdered);
        Map<Long, InpatientEncounterContextQueryModel> byId = new HashMap<>();
        for (InpatientEncounterContextQueryModel ctx : contexts) {
            if (ctx != null && ctx.getEncounterId() != null) {
                byId.put(ctx.getEncounterId(), ctx);
            }
        }
        // Pre：住院队列余额对齐护士站——个人现金户 + 待结算合计（划价 PENDING 会扣展示余额）
        Set<Long> encounterIdSet = new LinkedHashSet<>(encounterIdsOrdered);
        Map<Long, Account> registrationAccountByEncounterId =
            this.accountQueryService.mapRegistrationAccountsByEncounterIds(encounterIdSet);
        Map<Long, BigDecimal> pendingSettlementByEncounterId =
            this.chargeItemQueryService.mapPendingSettlementTotalPriceByEncounterIds(encounterIdSet);
        // Pre：护理级别（与护士站队列同 enrich）
        Map<Long, InpatientPatientQueueNursingLevelDisplayRow> nursingLevelByEncounterId =
            this.inpatientPatientQueueNursingLevelEnrichAssembler.enrichByEncounterIds(
                encounterIdSet, LocalDate.now());
        for (Long encounterId : encounterIdsOrdered) {
            InpatientEncounterContextQueryModel ctx = byId.get(encounterId);
            MedicalTechPatientQueueItemDto dto = new MedicalTechPatientQueueItemDto();
            dto.setEncounterId(encounterId);
            if (ctx != null) {
                dto.setPatientName(ctx.getPatientName());
                dto.setGenderEnum(ctx.getGenderEnum());
                AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, ctx.getGenderEnum());
                if (gender != null) {
                    dto.setGenderEnumText(gender.getDisplay());
                }
                if (ctx.getBirthDate() != null) {
                    dto.setBirthDate(ctx.getBirthDate().toLocalDate());
                }
                dto.setAgeText(ctx.getAgeText());
                dto.setSlipNo(ctx.getEncounterBusNo());
                dto.setEncounterTime(ctx.getStartTime());
                // 信息条回显：住院上下文同样投影手机号/身份证（与门诊字段同源，非业务同轴）
                dto.setPhone(ctx.getPhone());
                dto.setIdCard(ctx.getIdCard());
                // 住院队列卡片字段：状态 / 病区床位 / 诊断
                dto.setStatusEnum(ctx.getStatusEnum());
                String statusText = ctx.getStatusEnumText();
                if (StringUtils.isEmpty(statusText)) {
                    statusText = InpatientEncounterStatusDisplayUtil.resolveDisplayTextForReadModel(
                        ctx.getStatusEnum(), ctx.getTransferOrgId(), ctx.getDischargeTypeEnum());
                }
                dto.setStatusEnumText(statusText);
                dto.setWardName(ctx.getWardName());
                dto.setBedName(ctx.getBedName());
                dto.setDiagnosisSummary(
                    this.admissionPrimaryDiagnosisAssembler.resolveDiagnosisSummary(ctx.getDiagnosisJson()));
            }
            InpatientPatientQueueNursingLevelDisplayRow nursing = nursingLevelByEncounterId.get(encounterId);
            if (nursing != null) {
                dto.setNursingLevelName(nursing.getActivityName());
            }
            dto.setContractDisplay(contractByEncounter.get(encounterId));
            dto.setHasPendingDevice(Boolean.valueOf(pendingDeviceEncounterIds.contains(encounterId)));
            BigDecimal pending = pendingSettlementByEncounterId.get(encounterId);
            if (pending == null) {
                pending = BigDecimal.ZERO;
            }
            InpatientAccountBalanceDisplayResult balanceDisplay =
                InpatientAccountBalanceDisplayAssembler.assemble(
                    registrationAccountByEncounterId.get(encounterId),
                    pending,
                    BigDecimal.ZERO,
                    false);
            InpatientAccountBalanceDisplayDtoApplier.apply(dto, balanceDisplay);
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 收集本站 TECH 仍有待发耗材的就诊。
     *
     * @param encounterIds   本页就诊
     * @param loginTechOrgId 登录 TECH
     * @return 待发就诊集合
     */
    private Set<Long> collectPendingDeviceEncounterIdsForTech(List<Long> encounterIds, Long loginTechOrgId) {
        Set<Long> pending = new LinkedHashSet<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return pending;
        }
        LinkedHashSet<Long> idSet = new LinkedHashSet<>(encounterIds);
        List<DeviceRequestListModel> deviceRequests =
            this.deviceRequestQueryService.listDeviceRequestsByEncounterIds(idSet);
        List<DeviceDispenseListModel> deviceDispenses =
            this.deviceDispenseQueryService.listDeviceDispensesByEncounterIds(idSet);
        Map<Long, Long> dispenseOrgByDispenseId =
            this.mapDispenseIdToOrganizationId(deviceRequests, deviceDispenses);
        Map<Long, List<DeviceDispenseListModel>> dispensesByRequestId = new HashMap<>();
        for (DeviceDispenseListModel dispense : deviceDispenses) {
            if (dispense == null || dispense.getDeviceReqId() == null) {
                continue;
            }
            List<DeviceDispenseListModel> bucket = dispensesByRequestId.get(dispense.getDeviceReqId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                dispensesByRequestId.put(dispense.getDeviceReqId(), bucket);
            }
            bucket.add(dispense);
        }
        for (DeviceRequestListModel request : deviceRequests) {
            if (request == null || request.getId() == null || request.getEncounterId() == null) {
                continue;
            }
            Long primaryDispenseId = this.resolvePrimaryDispenseId(request.getId(), deviceDispenses);
            if (!this.isDispenseBelongToTechOrg(
                primaryDispenseId, request.getLocationId(), dispenseOrgByDispenseId, loginTechOrgId)) {
                continue;
            }
            if (this.hasPendingDeviceDispense(dispensesByRequestId.get(request.getId()))) {
                pending.add(request.getEncounterId());
            }
        }
        return pending;
    }

    /**
     * 构建诊疗执行行。
     *
     * <p>单价/总价：有锚点 ChargeItem 用落库价；否则用诊疗目录 SALE 现价预览（住院医技执行不建诊疗费，
     * 不可等护士执行后才有价）。</p>
     *
     * @param row                    诊疗请求
     * @param chargeByAnchor         收费索引
     * @param catalogContext         目录预热
     * @param loginTechOrgId         登录 TECH
     * @param unitLabelByCode        单位术语码 → 展示名
     * @param catalogPriceByProductKey 目录现价（表+实例复合键）
     * @param mergeDeviceByActivityId  合管设备主键（activityId → deviceId）
     * @return 行 DTO
     */
    private MedicalTechExecutionLineDto buildServiceLine(
        ServiceRequestListModel row,
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor,
        OrderRequestCatalogWarmContext catalogContext,
        Long loginTechOrgId,
        Map<String, String> unitLabelByCode,
        Map<String, List<ChargeItemDefinition>> catalogPriceByProductKey,
        Map<Long, Long> mergeDeviceByActivityId) {
        MedicalTechExecutionLineDto line = new MedicalTechExecutionLineDto();
        line.setRequestId(row.getId());
        line.setRequestTable(TableNameConstants.WKF_SERVICE_REQUEST);
        ActivityDefinition activity = null;
        if (row.getActivityId() != null && catalogContext != null) {
            activity = catalogContext.getActivityDefinitionById().get(row.getActivityId());
        }
        String busNo = null;
        String name = null;
        if (activity != null) {
            busNo = activity.getBusNo();
            name = activity.getName();
        }
        line.setItemCode(OrderCatalogDisplayAssembler.resolveItemCatalogCode(busNo, row.getActivityId()));
        line.setItemName(OrderCatalogDisplayAssembler.resolveItemDisplayName(name, "诊疗项目"));
        // 检验类目与合管设备：采血贴列表过滤、合管只打第一张
        if (activity != null && StringUtils.isNotEmpty(activity.getCategoryCode())) {
            line.setServiceCategoryCode(activity.getCategoryCode().trim());
        }
        if (row.getActivityId() != null && mergeDeviceByActivityId != null) {
            line.setSpecimenTubeMergeDeviceId(mergeDeviceByActivityId.get(row.getActivityId()));
        }
        // Asm：优先级供采血贴「急」
        line.setPriorityEnum(row.getPriorityEnum());
        line.setQuantity(row.getQuantity());
        // 单位：术语展示名；未命中时回落码本身（避免空列）
        String unitDisplay = ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            unitLabelByCode, row.getUnitCode(), null);
        if (StringUtils.isNotEmpty(unitDisplay)) {
            line.setUnitName(unitDisplay.trim());
        } else if (StringUtils.isNotEmpty(row.getUnitCode())) {
            line.setUnitName(row.getUnitCode().trim());
        }
        line.setPrescriptionNo(row.getPrescriptionNo());
        line.setRemark(row.getRemark());
        line.setPlannedExecutionCount(
            Integer.valueOf(ServiceAdviceDraftCommandHelper.resolvePlannedServicePerformCount(row)));
        int completed = 0;
        int cancelled = 0;
        for (ProcedureRecordQueryModel p :
            this.procedureQueryService.getProcedureRecords(row.getId(), TableNameConstants.WKF_SERVICE_REQUEST)) {
            if (p == null || !loginTechOrgId.equals(p.getOrgId())) {
                continue;
            }
            if (EventStatus.COMPLETED.getCode().equals(p.getStatusEnum())) {
                completed++;
            }
            if (EventStatus.isCancelExecutionStatusCode(p.getStatusEnum())) {
                cancelled++;
            }
        }
        line.setCompletedCount(Integer.valueOf(completed));
        line.setCancelledCount(Integer.valueOf(cancelled));
        AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation agg =
            chargeByAnchor.get(AdviceChargeAnchorRollupHelper.buildChargeAnchorKey(
                TableNameConstants.WKF_SERVICE_REQUEST, row.getId()));
        if (agg != null) {
            line.setChargeAmount(agg.getSumTotalPrice());
            line.setChargeStatusEnum(agg.getChargeStatusEnum());
            // 医技口语：PENDING_SETTLEMENT → 已划价待结算
            line.setChargeStatusEnumText(
                AdviceChargeDisplayStatusAssembler.resolveMedicalTechChargeStatusDisplayText(
                    agg.getChargeStatusEnum()));
            line.setUnitPrice(agg.getUnitPrice());
        }
        // 无落库单价/总价时：目录 SALE 现价预览（BR-PRICE-SRC-001 当前价合法场景）
        this.applyCatalogPricePreviewIfMissing(
            line,
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            row.getActivityId(),
            catalogPriceByProductKey);
        int planned = 0;
        if (line.getPlannedExecutionCount() != null) {
            planned = line.getPlannedExecutionCount().intValue();
        }
        int net = completed - cancelled;
        line.setSelectable(Boolean.valueOf(net < planned));
        return line;
    }

    /**
     * 构建耗材执行行。
     *
     * <p>有账单用落库价；无账单用目录参考售价预览（展示用，不冒充批号落账价）。</p>
     *
     * @param row                      耗材请求
     * @param chargeByAnchor           收费索引
     * @param catalogContext           目录预热
     * @param deviceDispenses          发放行
     * @param primaryDispenseId        主发放
     * @param unitLabelByCode          单位术语码 → 展示名
     * @param catalogPriceByProductKey 目录现价
     * @return 行 DTO
     */
    private MedicalTechExecutionLineDto buildDeviceLine(
        DeviceRequestListModel row,
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor,
        OrderRequestCatalogWarmContext catalogContext,
        List<DeviceDispenseListModel> deviceDispenses,
        Long primaryDispenseId,
        Map<String, String> unitLabelByCode,
        Map<String, List<ChargeItemDefinition>> catalogPriceByProductKey) {
        MedicalTechExecutionLineDto line = new MedicalTechExecutionLineDto();
        line.setRequestId(row.getId());
        line.setRequestTable(TableNameConstants.WKF_DEVICE_REQUEST);
        line.setDispenseId(primaryDispenseId);
        DeviceDefinition deviceDef = null;
        if (row.getDeviceDefId() != null && catalogContext != null) {
            deviceDef = catalogContext.getDeviceDefinitionById().get(row.getDeviceDefId());
        }
        String busNo = null;
        String name = null;
        if (deviceDef != null) {
            busNo = deviceDef.getBusNo();
            name = deviceDef.getName();
        }
        line.setItemCode(OrderCatalogDisplayAssembler.resolveItemCatalogCode(busNo, row.getDeviceDefId()));
        line.setItemName(OrderCatalogDisplayAssembler.resolveItemDisplayName(name, "耗材"));
        if (StringUtils.isNotEmpty(row.getDeviceSpecifications())) {
            line.setSpecification(row.getDeviceSpecifications().trim());
        }
        line.setQuantity(row.getQuantity());
        // 单位：术语展示名；未命中时回落码本身
        String unitDisplay = ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            unitLabelByCode, row.getUnitCode(), null);
        if (StringUtils.isNotEmpty(unitDisplay)) {
            line.setUnitName(unitDisplay.trim());
        } else if (StringUtils.isNotEmpty(row.getUnitCode())) {
            line.setUnitName(row.getUnitCode().trim());
        }
        line.setPrescriptionNo(row.getBusNo());
        AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation agg =
            chargeByAnchor.get(AdviceChargeAnchorRollupHelper.buildChargeAnchorKey(
                TableNameConstants.WKF_DEVICE_REQUEST, row.getId()));
        if (agg != null) {
            line.setChargeAmount(agg.getSumTotalPrice());
            line.setChargeStatusEnum(agg.getChargeStatusEnum());
            // 医技口语：PENDING_SETTLEMENT → 已划价待结算
            line.setChargeStatusEnumText(
                AdviceChargeDisplayStatusAssembler.resolveMedicalTechChargeStatusDisplayText(
                    agg.getChargeStatusEnum()));
            line.setUnitPrice(agg.getUnitPrice());
        }
        // 无落库价：目录参考售价预览（有账单不覆盖）
        this.applyCatalogPricePreviewIfMissing(
            line,
            TableNameConstants.ADM_DEVICE_DEFINITION,
            row.getDeviceDefId(),
            catalogPriceByProductKey);
        String dispenseStatus = this.resolveDispenseStatus(row.getId(), deviceDispenses, primaryDispenseId);
        line.setDispenseStatusEnum(dispenseStatus);
        DispenseStatus status = EnumUtil.fromCode(DispenseStatus.class, dispenseStatus);
        if (status != null) {
            line.setDispenseStatusEnumText(status.getDisplay());
        }
        line.setLotNumber(this.resolveLotNumber(primaryDispenseId, deviceDispenses));
        boolean pending = this.isPendingDispenseStatus(dispenseStatus);
        line.setSelectable(Boolean.valueOf(pending));
        return line;
    }

    /**
     * 执行单本页目录现价批量预取（诊疗 SALE；耗材 SALE+批号售价行）。
     *
     * @param catalogContext 已预热的活动/耗材定义
     * @return 复合键 → 定价行
     */
    private Map<String, List<ChargeItemDefinition>> loadCatalogPriceLinesForExecutionSheet(
        OrderRequestCatalogWarmContext catalogContext) {
        Map<String, List<Long>> idsByTable = new LinkedHashMap<>();
        if (catalogContext != null && catalogContext.getActivityDefinitionById() != null) {
            List<Long> activityIds = new ArrayList<>(catalogContext.getActivityDefinitionById().keySet());
            if (!activityIds.isEmpty()) {
                idsByTable.put(TableNameConstants.WKF_ACTIVITY_DEFINITION, activityIds);
            }
        }
        if (catalogContext != null && catalogContext.getDeviceDefinitionById() != null) {
            List<Long> deviceIds = new ArrayList<>(catalogContext.getDeviceDefinitionById().keySet());
            if (!deviceIds.isEmpty()) {
                idsByTable.put(TableNameConstants.ADM_DEVICE_DEFINITION, deviceIds);
            }
        }
        return this.clinicalCatalogPriceResolver.loadPriceLinesGroupedByTable(idsByTable);
    }

    /**
     * 行上无落库单价/总价时，用目录现价填展示（不覆盖已有 ChargeItem 金额）。
     *
     * @param line                     执行行
     * @param productTableName         产品表
     * @param productDefinitionId      产品定义主键
     * @param catalogPriceByProductKey 批量定价索引
     */
    private void applyCatalogPricePreviewIfMissing(
        MedicalTechExecutionLineDto line,
        String productTableName,
        Long productDefinitionId,
        Map<String, List<ChargeItemDefinition>> catalogPriceByProductKey) {
        if (line == null || productDefinitionId == null || StringUtils.isEmpty(productTableName)) {
            return;
        }
        boolean needUnit = line.getUnitPrice() == null;
        boolean needAmount = line.getChargeAmount() == null;
        if (!needUnit && !needAmount) {
            return;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(productTableName, productDefinitionId);
        List<ChargeItemDefinition> priceLines = Collections.emptyList();
        if (catalogPriceByProductKey != null) {
            List<ChargeItemDefinition> found = catalogPriceByProductKey.get(key);
            if (found != null) {
                priceLines = found;
            }
        }
        ChargeItemDefinition selected = null;
        // 诊疗：SALE 择优；耗材：无库存场景用参考零售价（SALE），避免执行单读路径 FIFO N+1
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTableName)) {
            selected = this.clinicalCatalogPriceResolver.resolveCatalogUnitPrice(
                ClinicalCatalogUnitPriceQuery.builder()
                    .productTableName(productTableName)
                    .productDefinitionId(productDefinitionId)
                    .priceLines(priceLines)
                    .build());
        } else {
            selected = this.clinicalCatalogPriceResolver.selectReferenceSalePriceLine(priceLines);
        }
        if (selected == null || selected.getPriceValue() == null) {
            return;
        }
        BigDecimal unitPrice = selected.getPriceValue();
        if (needUnit) {
            line.setUnitPrice(unitPrice);
        }
        if (needAmount) {
            BigDecimal qty = line.getQuantity();
            if (qty == null) {
                qty = BigDecimal.ONE;
            }
            line.setChargeAmount(MonetaryAmountSupport.lineTotal(unitPrice, qty));
        }
    }

    /**
     * 发放行主键 → 库位组织主键。
     *
     * @param deviceRequests  耗材请求
     * @param deviceDispenses 发放列表模型
     * @return dispenseId → organizationId
     */
    private Map<Long, Long> mapDispenseIdToOrganizationId(
        List<DeviceRequestListModel> deviceRequests,
        List<DeviceDispenseListModel> deviceDispenses) {
        Map<Long, Long> result = new HashMap<>();
        Set<Long> dispenseIds = new HashSet<>();
        if (deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (d != null && d.getId() != null) {
                    dispenseIds.add(d.getId());
                }
            }
        }
        List<DeviceDispense> entities = this.deviceDispenseQueryService.listDeviceDispensesByIds(dispenseIds);
        Set<Long> locationIds = new HashSet<>();
        Map<Long, Long> locationByDispenseId = new HashMap<>();
        for (DeviceDispense entity : entities) {
            if (entity == null || entity.getId() == null || entity.getLocationId() == null) {
                continue;
            }
            locationByDispenseId.put(entity.getId(), entity.getLocationId());
            locationIds.add(entity.getLocationId());
        }
        if (deviceRequests != null) {
            for (DeviceRequestListModel req : deviceRequests) {
                if (req != null && req.getLocationId() != null) {
                    locationIds.add(req.getLocationId());
                }
            }
        }
        Map<Long, Location> locationById = this.locationQueryService.loadLocationEntityMapByIds(locationIds);
        for (Map.Entry<Long, Long> entry : locationByDispenseId.entrySet()) {
            Location location = locationById.get(entry.getValue());
            if (location != null && location.getOrganizationId() != null) {
                result.put(entry.getKey(), location.getOrganizationId());
            }
        }
        return result;
    }

    /**
     * 发放/请求是否属于登录 TECH 科。
     *
     * @param primaryDispenseId       主发放
     * @param requestLocationId       请求库位
     * @param dispenseOrgByDispenseId 发放→组织
     * @param loginTechOrgId          登录 TECH
     * @return 属于时 true
     */
    private boolean isDispenseBelongToTechOrg(
        Long primaryDispenseId,
        Long requestLocationId,
        Map<Long, Long> dispenseOrgByDispenseId,
        Long loginTechOrgId) {
        if (primaryDispenseId != null && dispenseOrgByDispenseId != null) {
            Long orgId = dispenseOrgByDispenseId.get(primaryDispenseId);
            if (orgId != null) {
                return loginTechOrgId.equals(orgId);
            }
        }
        if (requestLocationId == null) {
            return false;
        }
        Map<Long, Location> locationById =
            this.locationQueryService.loadLocationEntityMapByIds(Collections.singleton(requestLocationId));
        Location location = locationById.get(requestLocationId);
        if (location == null || location.getOrganizationId() == null) {
            return false;
        }
        return loginTechOrgId.equals(location.getOrganizationId());
    }

    /**
     * 解析主发放行（id 最小）。
     *
     * @param deviceRequestId 耗材请求
     * @param deviceDispenses 发放列表
     * @return 主发放主键
     */
    private Long resolvePrimaryDispenseId(Long deviceRequestId, List<DeviceDispenseListModel> deviceDispenses) {
        Long primary = null;
        if (deviceDispenses == null) {
            return null;
        }
        for (DeviceDispenseListModel d : deviceDispenses) {
            if (d == null || d.getId() == null || !deviceRequestId.equals(d.getDeviceReqId())) {
                continue;
            }
            if (primary == null || d.getId() < primary) {
                primary = d.getId();
            }
        }
        return primary;
    }

    /**
     * 解析发放状态。
     *
     * @param deviceRequestId   请求
     * @param deviceDispenses   发放
     * @param primaryDispenseId 主发放
     * @return 状态码
     */
    private String resolveDispenseStatus(
        Long deviceRequestId,
        List<DeviceDispenseListModel> deviceDispenses,
        Long primaryDispenseId) {
        if (primaryDispenseId != null && deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (d != null && primaryDispenseId.equals(d.getId())) {
                    return d.getStatusEnum();
                }
            }
        }
        return DispenseStatus.PREPARATION.getCode();
    }

    /**
     * 解析批号。
     *
     * @param primaryDispenseId 主发放
     * @param deviceDispenses   发放
     * @return 批号
     */
    private String resolveLotNumber(Long primaryDispenseId, List<DeviceDispenseListModel> deviceDispenses) {
        if (primaryDispenseId == null || deviceDispenses == null) {
            return null;
        }
        for (DeviceDispenseListModel d : deviceDispenses) {
            if (d != null && primaryDispenseId.equals(d.getId()) && StringUtils.isNotEmpty(d.getLotNumber())) {
                return d.getLotNumber().trim();
            }
        }
        return null;
    }

    /**
     * 是否仍待发放。
     *
     * @param statusEnum 状态码
     * @return 待发时 true
     */
    private boolean isPendingDispenseStatus(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return true;
        }
        String code = statusEnum.trim().toUpperCase();
        if (DispenseStatus.PREPARED.getCode().equals(code)
            || DispenseStatus.COMPLETED.getCode().equals(code)
            || DispenseStatus.PART_COMPLETED.getCode().equals(code)) {
            return false;
        }
        if (DispenseStatus.DECLINED.getCode().equals(code)
            || DispenseStatus.STOPPED.getCode().equals(code)
            || DispenseStatus.CANCELLED.getCode().equals(code)
            || DispenseStatus.REFUNDED.getCode().equals(code)
            || DispenseStatus.PENDING_REFUND.getCode().equals(code)) {
            return false;
        }
        return true;
    }

    /**
     * 请求下是否仍有待发发放行。
     *
     * @param dispenses 发放行
     * @return 待发时 true
     */
    private boolean hasPendingDeviceDispense(List<DeviceDispenseListModel> dispenses) {
        if (dispenses == null || dispenses.isEmpty()) {
            return true;
        }
        for (DeviceDispenseListModel d : dispenses) {
            if (d != null && this.isPendingDispenseStatus(d.getStatusEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析收费归并状态码。
     *
     * @param table          来源表
     * @param requestId      请求主键
     * @param chargeByAnchor 索引
     * @return 状态码
     */
    private String resolveAggregatedChargeStatus(
        String table,
        Long requestId,
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor) {
        if (chargeByAnchor == null) {
            return null;
        }
        AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation agg =
            chargeByAnchor.get(AdviceChargeAnchorRollupHelper.buildChargeAnchorKey(table, requestId));
        if (agg == null) {
            return null;
        }
        return agg.getChargeStatusEnum();
    }

    /**
     * 门诊已计费请求过滤。
     *
     * @param requestIds  勾选
     * @param serviceTable 表名
     * @param chargeItems 收费行
     * @return 已计费主键
     */
    private List<Long> retainBillableRequestIds(
        List<Long> requestIds,
        String serviceTable,
        List<ChargeItem> chargeItems) {
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
            if (id != null && billable.contains(id)) {
                retained.add(id);
            }
        }
        return retained;
    }

    /**
     * 执行单请求行是否纳入列表（与队列「有活」同轴，见可见性 DD §1）。
     *
     * <p>门诊诊疗/耗材：复用护士站处置单 COMPLETED（或退费展示）。
     * 住院诊疗：{@code VERIFIED}（BR-MT-EXE-002）；
     * 住院耗材：划价落库 {@code COMPLETED}，亦纳入 {@code VERIFIED}（医生偶发开立）与退费展示。</p>
     *
     * @param encounterScope    OUTPATIENT / INPATIENT
     * @param requestStatusEnum 请求状态码
     * @param serviceTable      请求表名
     * @param requestId         请求主键
     * @param chargeStatusEnum  行级收费汇总态
     * @return true 纳入执行单
     */
    private boolean shouldIncludeMedicalTechSheetRequestRow(
        String encounterScope,
        String requestStatusEnum,
        String serviceTable,
        Long requestId,
        String chargeStatusEnum) {
        // 退费跟进展示：门诊/住院同轴
        if (requestId != null
            && StringUtils.isNotEmpty(serviceTable)
            && AdviceChargeDisplayStatusAssembler.isRefundChargeStatus(chargeStatusEnum)) {
            return true;
        }
        // 住院诊疗：已校对即展示，不得套用门诊 COMPLETED 门控
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_INPATIENT.equals(encounterScope)
            && TableNameConstants.WKF_SERVICE_REQUEST.equals(serviceTable)) {
            return RequestStatus.VERIFIED.getCode().equals(requestStatusEnum);
        }
        // 住院耗材：主路径划价 COMPLETED；兼容已校对开立
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_INPATIENT.equals(encounterScope)
            && TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)) {
            if (RequestStatus.COMPLETED.getCode().equals(requestStatusEnum)) {
                return true;
            }
            return RequestStatus.VERIFIED.getCode().equals(requestStatusEnum);
        }
        // 门诊诊疗/耗材：已完成或退费展示
        return AdviceChargeDisplayStatusAssembler.shouldIncludeNurseStationTreatmentSheetRow(
            requestStatusEnum, serviceTable, requestId, chargeStatusEnum);
    }

    /**
     * 批量执行 Gate。
     *
     * @param request 请求
     */
    private void validateBatchPerformGate(BatchPerformMedicalTechExecutionRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_201, "encounterId 不能为空");
        }
        boolean hasService = request.getServiceItems() != null && !request.getServiceItems().isEmpty();
        boolean hasDevice = request.getDeviceItems() != null && !request.getDeviceItems().isEmpty();
        if (!hasService && !hasDevice) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_201, "请至少勾选诊疗或耗材");
        }
    }

    /**
     * 批量取消 Gate。
     *
     * @param request 请求
     */
    private void validateBatchCancelGate(BatchCancelMedicalTechExecutionRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_211, "encounterId 不能为空");
        }
        boolean hasProc = request.getProcedureItems() != null && !request.getProcedureItems().isEmpty();
        boolean hasDevice = request.getDeviceItems() != null && !request.getDeviceItems().isEmpty();
        if (!hasProc && !hasDevice) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_211, "请至少勾选执行记录或耗材");
        }
    }

    /**
     * 校验可取消的本站 COMPLETED。
     *
     * @param completedProcedureId 待取消
     * @param encounterId          就诊
     * @param loginTechOrgId       登录 TECH
     * @return COMPLETED 行
     */
    private ProcedureRecordQueryModel mustResolveCancellableCompletedProcedure(
        Long completedProcedureId,
        Long encounterId,
        Long loginTechOrgId) {
        ProcedureDetailQueryModel detail = this.procedureQueryService.loadProcedureById(completedProcedureId);
        if (detail == null || detail.getProcedure() == null) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_211, "执行记录不存在");
        }
        ProcedureRecordQueryModel row = detail.getProcedure();
        if (!EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_211, "仅已完成的执行记录可取消");
        }
        if (row.getEncounterId() == null || !row.getEncounterId().equals(encounterId)) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_211, "执行记录不属于当前就诊");
        }
        if (!loginTechOrgId.equals(row.getOrgId())) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_211, "执行记录不属于本医技科");
        }
        if (this.isProcedureAlreadyOffset(row.getRequestId(), row.getRequestTable(), completedProcedureId)) {
            throw new BusinessException(MedicalTechExecutionConstants.EX_MT_EXE_212, "该执行记录已被取消对冲");
        }
        return row;
    }

    /**
     * 是否已被 CANCEL 对冲。
     *
     * @param requestId            请求
     * @param requestTable         表
     * @param completedProcedureId COMPLETED
     * @return 已对冲 true
     */
    private boolean isProcedureAlreadyOffset(Long requestId, String requestTable, Long completedProcedureId) {
        for (ProcedureRecordQueryModel sibling :
            this.procedureQueryService.getProcedureRecords(requestId, requestTable)) {
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
     * 回填执行记录项目名与操作人展示名。
     *
     * <p>按 request 锚点解析诊疗/耗材目录名；操作人取 Procedure 主执行人。</p>
     *
     * @param encounterId 就诊主键
     * @param sourceRows  本页/本批 Procedure 读模型（与 dtos 按 procedureId 对齐）
     * @param dtos        待回填 DTO 列表
     */
    private void enrichProcedureRecordDisplays(
        Long encounterId,
        List<ProcedureRecordQueryModel> sourceRows,
        List<MedicalTechProcedureRecordDto> dtos) {
        if (encounterId == null || dtos == null || dtos.isEmpty()) {
            return;
        }
        Map<Long, ProcedureRecordQueryModel> sourceByProcedureId = new HashMap<>();
        if (sourceRows != null) {
            for (ProcedureRecordQueryModel row : sourceRows) {
                if (row == null || row.getId() == null) {
                    continue;
                }
                sourceByProcedureId.put(row.getId(), row);
            }
        }
        // Pre：本就诊诊疗/耗材请求 + 目录预热（批量，禁 N+1）
        List<ServiceRequestListModel> serviceRequests =
            this.serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        List<DeviceRequestListModel> deviceRequests =
            this.deviceRequestQueryService.listDeviceRequestsByEncounter(encounterId);
        OrderRequestCatalogWarmContext catalogContext =
            this.orderRequestCatalogWarmAssembler.prepareFromTreatmentSheetRows(
                serviceRequests, Collections.emptyList(), deviceRequests);
        Map<Long, ServiceRequestListModel> serviceById = new HashMap<>();
        for (ServiceRequestListModel serviceRow : serviceRequests) {
            if (serviceRow == null || serviceRow.getId() == null) {
                continue;
            }
            serviceById.put(serviceRow.getId(), serviceRow);
        }
        Map<Long, DeviceRequestListModel> deviceById = new HashMap<>();
        for (DeviceRequestListModel deviceRow : deviceRequests) {
            if (deviceRow == null || deviceRow.getId() == null) {
                continue;
            }
            deviceById.put(deviceRow.getId(), deviceRow);
        }
        // Pre：主执行人批量解析
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        for (MedicalTechProcedureRecordDto dto : dtos) {
            if (dto != null && dto.getProcedureId() != null) {
                procedureIds.add(dto.getProcedureId());
            }
        }
        Map<Long, Long> primaryPractitionerByProcedureId =
            this.procedureQueryService.mapPrimaryPractitionerIdByProcedureIds(procedureIds);
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>(primaryPractitionerByProcedureId.values());
        Map<Long, String> practitionerNameById = this.mapPractitionerDisplayNameByIds(practitionerIds);
        // Asm：逐行回填
        for (MedicalTechProcedureRecordDto dto : dtos) {
            if (dto == null || dto.getProcedureId() == null) {
                continue;
            }
            ProcedureRecordQueryModel source = sourceByProcedureId.get(dto.getProcedureId());
            String requestTable = null;
            Long requestId = dto.getRequestId();
            if (source != null) {
                requestTable = source.getRequestTable();
                if (requestId == null) {
                    requestId = source.getRequestId();
                }
            }
            dto.setOrderItemDisplayName(
                this.resolveProcedureRecordOrderItemDisplayName(
                    requestId, requestTable, serviceById, deviceById, catalogContext));
            Long practitionerId = primaryPractitionerByProcedureId.get(dto.getProcedureId());
            if (practitionerId != null) {
                dto.setOperatorDisplayName(practitionerNameById.get(practitionerId));
            }
        }
    }

    /**
     * 解析执行记录对应项目展示名（诊疗活动或耗材定义名）。
     *
     * @param requestId      来源请求主键
     * @param requestTable   来源表
     * @param serviceById    就诊下诊疗申请索引
     * @param deviceById     就诊下耗材申请索引
     * @param catalogContext 目录预热
     * @return 展示名；无法解析时回落占位
     */
    private String resolveProcedureRecordOrderItemDisplayName(
        Long requestId,
        String requestTable,
        Map<Long, ServiceRequestListModel> serviceById,
        Map<Long, DeviceRequestListModel> deviceById,
        OrderRequestCatalogWarmContext catalogContext) {
        if (requestId == null || StringUtils.isEmpty(requestTable)) {
            return "诊疗项目";
        }
        String table = requestTable.trim();
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(table)) {
            ServiceRequestListModel serviceRow = serviceById.get(requestId);
            ActivityDefinition activityDef = null;
            if (serviceRow != null && serviceRow.getActivityId() != null && catalogContext != null) {
                activityDef = catalogContext.getActivityDefinitionById().get(serviceRow.getActivityId());
            }
            String activityName = null;
            if (activityDef != null) {
                activityName = activityDef.getName();
            }
            return OrderCatalogDisplayAssembler.resolveItemDisplayName(activityName, "诊疗项目");
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(table)) {
            DeviceRequestListModel deviceRow = deviceById.get(requestId);
            DeviceDefinition deviceDef = null;
            if (deviceRow != null && deviceRow.getDeviceDefId() != null && catalogContext != null) {
                deviceDef = catalogContext.getDeviceDefinitionById().get(deviceRow.getDeviceDefId());
            }
            String deviceName = null;
            if (deviceDef != null) {
                deviceName = deviceDef.getName();
            }
            return OrderCatalogDisplayAssembler.resolveItemDisplayName(deviceName, "耗材");
        }
        return "诊疗项目";
    }

    /**
     * 按医务人员主键批量解析展示名。
     *
     * @param practitionerIds 医务人员主键
     * @return practitionerId → 姓名
     */
    private Map<Long, String> mapPractitionerDisplayNameByIds(Collection<Long> practitionerIds) {
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        Map<Long, String> nameById = new HashMap<>();
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(practitioner.getName())) {
                nameById.put(practitioner.getId(), practitioner.getName());
            }
        }
        return nameById;
    }

    /**
     * Procedure 实体 → 读模型。
     *
     * @param entity 实体
     * @return 读模型
     */
    private ProcedureRecordQueryModel toProcedureRecordQueryModel(Procedure entity) {
        ProcedureRecordQueryModel model = new ProcedureRecordQueryModel();
        model.setId(entity.getId());
        model.setRequestId(entity.getRequestId());
        model.setRequestTable(entity.getRequestTable());
        model.setStatusEnum(entity.getStatusEnum());
        model.setRecordedTime(entity.getRecordedTime());
        model.setRefundId(entity.getRefundId());
        model.setEncounterId(entity.getEncounterId());
        model.setPatientId(entity.getPatientId());
        model.setCategoryEnum(entity.getCategoryEnum());
        model.setOccurrenceTime(entity.getOccurrenceTime());
        model.setRecorderId(entity.getRecorderId());
        model.setResultText(entity.getResultText());
        model.setOrgId(entity.getOrgId());
        return model;
    }

    /**
     * 记录时间倒序比较器。
     *
     * @return 比较器
     */
    private Comparator<ProcedureRecordQueryModel> procedureRecordTimeDescComparator() {
        return (left, right) -> {
            LocalDateTime lt = null;
            if (left != null) {
                lt = left.getRecordedTime();
            }
            LocalDateTime rt = null;
            if (right != null) {
                rt = right.getRecordedTime();
            }
            if (lt == null && rt == null) {
                return 0;
            }
            if (lt == null) {
                return 1;
            }
            if (rt == null) {
                return -1;
            }
            return rt.compareTo(lt);
        };
    }

    /**
     * null 安全列表。
     *
     * @param list 原列表
     * @param <T>  元素类型
     * @return 非 null 列表
     */
    private <T> List<T> safeList(List<T> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }
}
