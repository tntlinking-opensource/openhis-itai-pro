/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.service;

import com.zxplat.application.shared.practitioner.PractitionerRoleScopeLoader;
import com.zxplat.application.shared.activitydefinition.ActivityDefinitionPackageChargePersistSupport;
import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.application.shared.inpatient.InpatientDischargeBusinessTimeGate;
import com.zxplat.application.shared.validator.InpatientPendingSettlementClinicalGate;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.share.enums.OrganizationClassification;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationAutoRollListAssembler;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationInpatientBillingCatalogAssembler;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationInpatientBillingListAssembler;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationInpatientBillingWriteAssembler;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollNursingReadonlyDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricedOrderRowDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCancelItemDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCatalogRowDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingParentRequestAnchorDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingPatientGroupDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.PrepickedProductKeyDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationSupplementParentOrderRowDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationAutoRollWriteAssembler;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.AutoRollEncounterProcessResult;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationAutoRollMapper;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationInpatientBillingMapper;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ActivateAutoGenRuleRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.CancelNurseStationPricingRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ChangeNurseStationPricingBusinessTimeRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ConfirmDirectNurseStationPricingRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ConfirmSupplementNurseStationPricingRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.CreateAutoGenRuleRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.DeleteAutoGenRuleRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ExecuteInpatientAutoRollJobRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ListAutoRollConfigsRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ListAutoRollPostedChargesRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ListNurseStationPricedOrdersRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ListNurseStationSupplementParentOrdersRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.PageNurseStationPricingCatalogRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.SuspendAutoGenRuleRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.UpdateAutoGenRuleRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.ActivateAutoGenRuleResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.CancelNurseStationPricingResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.ChangeNurseStationPricingBusinessTimeResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.ConfirmDirectNurseStationPricingResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.ConfirmSupplementNurseStationPricingResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.CreateAutoGenRuleResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.DeleteAutoGenRuleResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.ExecuteInpatientAutoRollJobResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.ListAutoRollConfigsResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.ListAutoRollPostedChargesResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.ListNurseStationPricedOrdersResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.ListNurseStationSupplementParentOrdersResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.PageNurseStationPricingCatalogResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.SuspendAutoGenRuleResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.response.UpdateAutoGenRuleResponse;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.support.NurseStationAutoRollEncounterProcessor;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.support.NurseStationInpatientBillingProcedureOrchestrator;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.administration.encounter.queries.model.InpatientAutoRollEncounterQueryModel;
import com.zxplat.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenRuleQueryModel;
import com.zxplat.domain.custom.chargeitemautogen.service.ChargeItemAutoGenDomainService;
import com.zxplat.domain.custom.chargeitemautogen.service.ChargeItemAutoGenQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.support.InpatientAutoRollEncounterContextLoader;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.application.shared.advice.InpatientOrderListSkeleton;
import com.zxplat.application.shared.advice.InpatientOrderListMergeAssembler;
import com.zxplat.application.shared.advice.InpatientOrderLookupLoader;
import com.zxplat.application.shared.advice.ServiceApplyFormOrchestrationSupport;
import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.application.shared.product.ProductDefinitionCatalogPageLoader;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.PlatformRoleCodeConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.application.shared.nursestation.DeviceDispenseNurseWorkflowOrchestrator;
import com.zxplat.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.application.shared.inventory.InventoryItemProductSelectionPageLoader;
import com.zxplat.domain.administration.chargeitem.command.OffsetNursePricingChargeItemsByServiceAnchorsCommand;
import com.zxplat.domain.clinical.procedure.command.AmendProcedureRecordedTimeCommand;
import com.zxplat.domain.clinical.procedure.service.ProcedureDomainService;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.workflow.devicedispense.command.AmendDeviceDispenseDisplayTimeCommand;
import com.zxplat.domain.workflow.servicerequest.queries.model.InpatientAutoRollNursingLevelQueryModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.job.SchedulableJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院护士站划价应用编排（AS-IP-NUR-005）。
 *
 * <p>选品目录：诊疗与耗材定价目录分页（排除药品）。</p>
 * <p>直接划价/医嘱补费：不经校对签发，同事务落请求、诊疗 Procedure（登录人执行）、收费与耗材自动发放（BR-IP-PRC-008/019）。</p>
 * <p>取消划价：已划价列表多选整行冲销留痕（IF-IP-PRC-06）。</p>
 * <p>改划价展示时间：诊疗改 Procedure.recorded_time；耗材改发放展示时刻（IF-IP-PRC-07）。</p>
 * <p>自动计费：配置 CRUD/启停、已滚费用展开（IF-IP-ARG-09）与住院滚费任务（AS-IP-NUR-013 IF-IP-ARG-02～09）。</p>
 * <p>列表：父医嘱候选与已划价记录，均收窄至护士管理病区可见就诊。</p>
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/nurseStationInpatientBilling/{methodName}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationInpatientBillingAppService {

    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;
    /** 登录工作科室：划价 Procedure.org_id / TECH 诊疗禁划 Gate。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 组织只读：划价禁 TECH 执行科。 */
    private final OrganizationQueryService organizationQueryService;
    private final PageWindowGate pageWindowGate;
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;
    private final InpatientOrderListMergeAssembler inpatientOrderListMergeAssembler;
    private final InpatientOrderLookupLoader inpatientOrderLookupLoader;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final InpatientAutoRollEncounterContextLoader inpatientAutoRollEncounterContextLoader;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    private final DeviceDispenseNurseWorkflowOrchestrator deviceDispenseNurseWorkflowOrchestrator;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final InventoryItemProductSelectionPageLoader inventoryItemProductSelectionPageLoader;
    private final ChargeItemDomainService chargeItemDomainService;
    private final NurseStationInpatientBillingMapper nurseStationInpatientBillingMapper;
    private final NurseStationInpatientBillingCatalogAssembler nurseStationInpatientBillingCatalogAssembler;
    private final NurseStationInpatientBillingListAssembler nurseStationInpatientBillingListAssembler;
    private final NurseStationInpatientBillingWriteAssembler nurseStationInpatientBillingWriteAssembler;
    private final ChargeItemAutoGenQueryService chargeItemAutoGenQueryService;
    private final ChargeItemAutoGenDomainService chargeItemAutoGenDomainService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final NurseStationAutoRollMapper nurseStationAutoRollMapper;
    private final NurseStationAutoRollListAssembler nurseStationAutoRollListAssembler;
    private final NurseStationAutoRollWriteAssembler nurseStationAutoRollWriteAssembler;
    private final NurseStationAutoRollEncounterProcessor nurseStationAutoRollEncounterProcessor;
    private final NurseStationInpatientBillingProcedureOrchestrator nurseStationInpatientBillingProcedureOrchestrator;
    /** 带子项诊疗：父 Charge 落库后补写冻价子项账单。 */
    private final ActivityDefinitionPackageChargePersistSupport activityDefinitionPackageChargePersistSupport;
    /** 诊疗划价展示轴：净剩 Procedure.recorded_time 读/写。 */
    private final ProcedureQueryService procedureQueryService;
    private final ProcedureDomainService procedureDomainService;
    /** 就诊出院结算轨禁止划价/取消划价等费用写路径。 */
    private final InpatientPendingSettlementClinicalGate inpatientPendingSettlementClinicalGate;

    /** 划价/改时 vs 计划出院时间后端门禁（BR-IP-PRC-035；补前端缺 dischargeAt 跳过）。 */
    private final InpatientDischargeBusinessTimeGate inpatientDischargeBusinessTimeGate;
    /** 计费人姓名批量解析。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 在职护士下拉（配置计费人）。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    /** 就诊挂账账户：医保入院强制医保户。 */
    private final AccountQueryService accountQueryService;

    /**
     * IF-IP-PRC-01：划价选品目录分页。
     *
     * <p>耗材：按<strong>住院科室取药模板</strong>候选耗材房库存批号行分页（含数量 0；总量=库存行数；
     * <strong>不</strong>按登录人岗位管辖药房）。诊疗：仍按产品定义分页。</p>
     *
     * @param request 分页、prepickedProductKeys、机构/库房筛选；耗材建议带 encounterId
     * @return 带定价行、单位槽、套餐子项、吸氧/心电 Gate 标记的目录页
     * @throws BusinessException EX-IP-PRC-101 分页非法或目录加载失败
     */
    public PageNurseStationPricingCatalogResponse pageNurseStationPricingCatalog(
        PageNurseStationPricingCatalogRequest request) {
        try {
            // Gate：分页窗口须为正
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientNurseStationConstants.EX_IP_PRC_101,
                "分页参数无效");
            // 耗材 Tab：库存驱动，禁止产品目录分页再过滤（否则总量与空页脱节）
            if (this.isDeviceOnlyPricingCatalogRequest(request)) {
                return this.pageDevicePricingCatalogFromAdmissionDepartmentPharmacyInventory(request);
            }
            // Pre：诊疗产品定义分页
            var pageQuery = nurseStationInpatientBillingMapper.toProductDefinitionPageQuery(request);
            Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
            // Asm：诊疗目录行
            var rows = nurseStationInpatientBillingCatalogAssembler.assembleCatalogRows(request, page);
            return PagedResultBuilder.build(new PageNurseStationPricingCatalogResponse(), rows, page);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_101,
                "划价选品目录加载失败");
        }
    }

    /**
     * 请求是否仅为耗材表（前端耗材 Tab / 组套预选耗材）。
     *
     * @param request 选品请求
     * @return true 表示只查耗材库存分页
     */
    private boolean isDeviceOnlyPricingCatalogRequest(PageNurseStationPricingCatalogRequest request) {
        if (request == null || request.getProductTableNames() == null || request.getProductTableNames().isEmpty()) {
            return false;
        }
        boolean hasDevice = false;
        for (String table : request.getProductTableNames()) {
            if (StringUtils.isEmpty(table)) {
                continue;
            }
            String trimmed = table.trim();
            if (TableNameConstants.ADM_DEVICE_DEFINITION.equalsIgnoreCase(trimmed)) {
                hasDevice = true;
                continue;
            }
            // 夹带非耗材表：走产品定义路径
            return false;
        }
        return hasDevice;
    }

    /**
     * 耗材选品：住院科室取药模板耗材房库存批号分页（含数量 0）。
     *
     * <p>库位来自 {@code ServiceLocationTemplate}（入院/当前住院科室），不按登录人管辖药房。</p>
     *
     * @param request 选品请求
     * @return 库存驱动的划价目录页
     */
    private PageNurseStationPricingCatalogResponse pageDevicePricingCatalogFromAdmissionDepartmentPharmacyInventory(
        PageNurseStationPricingCatalogRequest request
    ) {
        // Pre：住院科室优先取就诊 organizationId，回落请求 organizationUnitId
        Long organizationId = this.resolvePricingCatalogOrganizationId(request);
        if (organizationId == null) {
            return PagedResultBuilder.build(
                new PageNurseStationPricingCatalogResponse(),
                List.of(),
                0L,
                request.getPageNo(),
                request.getBoundedPageSize());
        }
        // 回写请求机构，供装配默认发放库房 match 取药模板
        request.setOrganizationUnitId(organizationId);
        List<Long> templatePharmacyIds =
            nurseStationInpatientBillingCatalogAssembler.listAdmissionDepartmentDevicePharmacyLocationIds(
                organizationId, request.getPharmacyLocationId());
        // 无取药模板耗材房：空页，总量 0（与 content 一致）
        if (templatePharmacyIds.isEmpty()) {
            return PagedResultBuilder.build(
                new PageNurseStationPricingCatalogResponse(),
                List.of(),
                0L,
                request.getPageNo(),
                request.getBoundedPageSize());
        }
        List<Long> locationIds = templatePharmacyIds;
        // 请求指定库房时：须落在模板候选内，否则空页
        if (request.getPharmacyLocationId() != null) {
            if (!templatePharmacyIds.contains(request.getPharmacyLocationId())) {
                return PagedResultBuilder.build(
                    new PageNurseStationPricingCatalogResponse(),
                    List.of(),
                    0L,
                    request.getPageNo(),
                    request.getBoundedPageSize());
            }
            locationIds = List.of(request.getPharmacyLocationId());
        }
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setItemTable(TableNameConstants.ADM_DEVICE_DEFINITION);
        invQuery.setLocationIds(locationIds);
        invQuery.setInventoryStatusEnum(PublicationStatus.ACTIVE.getCode());
        // 有库存记录即返回，数量 0 不排除
        invQuery.setIncludeZeroQuantity(Boolean.TRUE);
        // 组套预选：按耗材定义 id 收窄库存行
        List<Long> prepickedItemIds = this.resolvePrepickedDeviceItemIds(request);
        if (prepickedItemIds != null) {
            if (prepickedItemIds.isEmpty()) {
                return PagedResultBuilder.build(
                    new PageNurseStationPricingCatalogResponse(),
                    List.of(),
                    0L,
                    request.getPageNo(),
                    request.getBoundedPageSize());
            }
            invQuery.setItemIds(prepickedItemIds);
        }
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(),
            request.getBoundedPageSize(),
            Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        // Pre：按库存批号行分页
        Page<InventoryItemModel> inventoryPage =
            inventoryItemProductSelectionPageLoader.page(invQuery, pageable);
        // Asm：库存行 → 划价目录行（批号/剩余量/拆零价）
        List<NurseStationPricingCatalogRowDto> rows =
            nurseStationInpatientBillingCatalogAssembler.assembleDeviceCatalogRowsFromInventory(
                request, inventoryPage.getContent());
        return PagedResultBuilder.build(new PageNurseStationPricingCatalogResponse(), rows, inventoryPage);
    }

    /**
     * 解析划价选品所用住院科室：优先就诊 organizationId，回落请求 organizationUnitId。
     *
     * @param request 选品请求
     * @return 科室主键；均无则 null
     */
    private Long resolvePricingCatalogOrganizationId(PageNurseStationPricingCatalogRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getEncounterId() != null) {
            // 就诊主档 organizationId = 入院/当前住院科室（取药模板键）
            return encounterQueryService.findOptionalEncounterEntityById(request.getEncounterId())
                .map(Encounter::getOrganizationId)
                .orElse(request.getOrganizationUnitId());
        }
        return request.getOrganizationUnitId();
    }

    /**
     * 从 prepickedProductKeys 提取耗材定义 id；无预选时返回 null（表示不收窄）。
     *
     * @param request 选品请求
     * @return null=不收窄；非 null=预选 id 列表（可空表示预选非法）
     */
    private List<Long> resolvePrepickedDeviceItemIds(PageNurseStationPricingCatalogRequest request) {
        if (request.getPrepickedProductKeys() == null || request.getPrepickedProductKeys().isEmpty()) {
            return null;
        }
        LinkedHashSet<Long> itemIds = new LinkedHashSet<>();
        for (PrepickedProductKeyDto key : request.getPrepickedProductKeys()) {
            if (key == null || key.getProductDefinitionId() == null) {
                continue;
            }
            if (StringUtils.isEmpty(key.getProductTableName())) {
                continue;
            }
            if (!TableNameConstants.ADM_DEVICE_DEFINITION.equalsIgnoreCase(key.getProductTableName().trim())) {
                continue;
            }
            itemIds.add(key.getProductDefinitionId());
        }
        return new ArrayList<>(itemIds);
    }

    /**
     * IF-IP-PRC-02：直接划价确认（无父医嘱锚点）。
     *
     * <p>耗材 {@code performLocation} 须为 IF-IP-PRC-01 目录 {@code defaultDispenseLocationId} 回传；
     * 入账价经 {@link NurseStationInpatientBillingWriteAssembler#reapplyAuthoritativeWritePricing} 重算。
     * 可选 {@code pricingTime} 写入客户可见展示轴（诊 recorded / 材发放展示）。</p>
     *
     * @param request 1～50 行写明细，每行须 accountId 与数量等产品字段（单价由服务端重算）；可选 pricingTime
     * @return 新建诊疗/耗材请求 id 汇总
     * @throws BusinessException EX-IP-PRC-201 夹带父锚点；EX-IP-PRC-202 缺耗材库位；其余同写路径
     */
    public ConfirmDirectNurseStationPricingResponse confirmDirectNurseStationPricing(
        ConfirmDirectNurseStationPricingRequest request) {
        try {
            nurseStationInpatientBillingMapper.validateDirectPricingItems(request.getItems());
            return this.confirmPricingWrite(request.getItems(), request.getPricingTime());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_205,
                "划价失败，请稍后重试");
        }
    }

    /**
     *
     * @param request 写明细，携带 parentRequestAnchor
     * @return 与 IF-02 同构的写结果
     * @throws BusinessException EX-IP-PRC-301 父医嘱非法
     */
    public ConfirmSupplementNurseStationPricingResponse confirmSupplementNurseStationPricing(
        ConfirmSupplementNurseStationPricingRequest request) {
        try {
            nurseStationInpatientBillingMapper.validateSupplementPricingItems(request.getItems());
            for (NurseStationPricingWriteItemDto row : request.getItems()) {
                this.assertParentOrderAllowed(row.getParentRequestAnchor(), row.getEncounterId());
            }
            ConfirmDirectNurseStationPricingResponse core =
                this.confirmPricingWrite(request.getItems(), request.getPricingTime());
            ConfirmSupplementNurseStationPricingResponse resp = new ConfirmSupplementNurseStationPricingResponse();
            resp.setSuccess(core.getSuccess());
            resp.setProcessedCount(core.getProcessedCount());
            resp.setCreatedServiceRequestIds(core.getCreatedServiceRequestIds());
            resp.setCreatedDeviceRequestIds(core.getCreatedDeviceRequestIds());
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_205,
                "划价失败，请稍后重试");
        }
    }

    /**
     *
     * @param request 多选 encounter、长临筛选、关键字、科室
     * @return 按就诊分组的父医嘱展示行
     * @throws BusinessException EX-IP-PRC-101/102 列表加载失败或就诊不可见
     */
    public ListNurseStationSupplementParentOrdersResponse listNurseStationSupplementParentOrders(
        ListNurseStationSupplementParentOrdersRequest request) {
        try {
            List<Long> visibleEncounterIds = this.resolveVisibleEncounterIds(request.getEncounterIds());
            String therapyEnum = nurseStationInpatientBillingMapper.resolveTherapyEnum(request.getTherapyType());
            List<InpatientOrderListSkeleton> skeleton =
                inpatientOrderListMergeAssembler.mergeNurseStationSupplementOrders(visibleEncounterIds, therapyEnum);
            String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
            Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = new HashMap<>();
            Map<Long, List<ServiceRequestListModel>> svcByEncounter = new HashMap<>();
            for (Long encounterId : visibleEncounterIds) {
                medByEncounter.put(encounterId,
                    medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                        encounterId, doctorSource));
                svcByEncounter.put(encounterId,
                    serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource));
            }
            List<NurseStationPricingPatientGroupDto> groups =
                nurseStationInpatientBillingListAssembler.assembleSupplementParentGroups(
                    skeleton, medByEncounter, svcByEncounter);
            List<NurseStationSupplementParentOrderRowDto> flat = new ArrayList<>();
            for (NurseStationPricingPatientGroupDto group : groups) {
                if (group.getOrders() != null) {
                    flat.addAll(group.getOrders());
                }
            }
            flat = nurseStationInpatientBillingMapper.filterByKeywordAndOrg(
                flat, request.getKeyword(), request.getOrgId());
            List<NurseStationPricingPatientGroupDto> regrouped = this.regroupSupplementRows(flat);
            ListNurseStationSupplementParentOrdersResponse resp = new ListNurseStationSupplementParentOrdersResponse();
            resp.setGroups(regrouped);
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_101,
                "医嘱列表加载失败");
        }
    }

    /**
     *
     * @param request 多选 encounter、划价时间窗、可选父医嘱 id 过滤补费子行
     * @return 按就诊分组的已划价行（含费用态、耗材发放态）
     * @throws BusinessException EX-IP-PRC-101/102
     */
    public ListNurseStationPricedOrdersResponse listNurseStationPricedOrders(
        ListNurseStationPricedOrdersRequest request) {
        try {
            // Gate：可见就诊
            List<Long> visibleEncounterIds = this.resolveVisibleEncounterIds(request.getEncounterIds());
            // Pre：骨架不按 created_at 预过滤；客户可见时间轴为 recorded/发放时刻，区间在 Asm 后过滤（BR-IP-PRC-030）
            List<InpatientOrderListSkeleton> skeleton = inpatientOrderListMergeAssembler.mergeNurseStationPricedOrders(
                visibleEncounterIds, null, null, request.getParentRequestId());
            Set<Long> serviceIds = new LinkedHashSet<>();
            Set<Long> deviceReqIds = new LinkedHashSet<>();
            for (InpatientOrderListSkeleton row : skeleton) {
                if (row == null || row.getOrderId() == null) {
                    continue;
                }
                if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                    deviceReqIds.add(row.getOrderId());
                } else {
                    serviceIds.add(row.getOrderId());
                }
            }
            // ChargeItem.service_id 与诊疗/耗材请求主键同 id 关联，须合并两轨 id 再批量加载
            Set<Long> chargeAnchorIds = new LinkedHashSet<>(serviceIds);
            chargeAnchorIds.addAll(deviceReqIds);
            ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
            chargeCmd.setServiceIds(new ArrayList<>(chargeAnchorIds));
            chargeCmd.setExcludeSupersededSettledLines(true);
            List<ChargeItem> chargeRows = chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
            Map<Long, List<ChargeItem>> chargeByServiceId = new HashMap<>();
            for (ChargeItem charge : chargeRows) {
                if (charge == null || charge.getServiceId() == null) {
                    continue;
                }
                chargeByServiceId.computeIfAbsent(charge.getServiceId(), k -> new ArrayList<>()).add(charge);
            }
            Map<Long, List<Long>> dispenseIdsByDeviceReqId =
                deviceDispenseQueryService.mapDeviceDispenseIdsByDeviceRequestIds(deviceReqIds);
            Map<Long, String> primaryDispenseStatusByDeviceReqId =
                deviceDispenseQueryService.mapPrimaryDispenseStatusByDeviceReqIds(deviceReqIds);
            Map<Long, LocalDateTime> recordedTimeByServiceRequestId =
                procedureQueryService.mapNetCompletedRecordedTimeByServiceRequestIds(
                    visibleEncounterIds, serviceIds);
            Map<Long, LocalDateTime> displayTimeByDeviceRequestId =
                deviceDispenseQueryService.mapPrimaryDisplayTimeByDeviceReqIds(deviceReqIds);
            Map<Long, NurseStationInpatientBillingListAssembler.PricedOrderRowFacts> pricedRowFactsByOrderId =
                nurseStationInpatientBillingListAssembler.buildPricedRowFactsByOrderId(skeleton);
            // Asm：组装客户可见 pricedTime（recorded / 发放）
            List<NurseStationPricingPatientGroupDto> groups =
                nurseStationInpatientBillingListAssembler.assemblePricedOrderGroups(
                    skeleton, chargeByServiceId, dispenseIdsByDeviceReqId, primaryDispenseStatusByDeviceReqId,
                    pricedRowFactsByOrderId, recordedTimeByServiceRequestId, displayTimeByDeviceRequestId);
            // Asm：按展示轴区间过滤（与列表「划价时间」一致）
            groups = this.filterPricedGroupsByDisplayTime(
                groups, request.getPricedTimeFrom(), request.getPricedTimeTo());
            ListNurseStationPricedOrdersResponse resp = new ListNurseStationPricedOrdersResponse();
            resp.setGroups(groups);
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_101,
                "已划价列表加载失败");
        }
    }

    /**
     * IF-IP-PRC-06：取消划价（整行冲销留痕，BR-IP-PRC-021～028）。
     *
     * <p>Request 保持完成态不软删；费用写负向冲销行；诊疗 Procedure CANCEL；耗材 Dispense 回退。</p>
     *
     * @param request 1～50 行；每行含 orderType、requestId、encounterId
     * @return 已冲销处理的诊疗/耗材请求 id 汇总
     * @throws BusinessException EX-IP-PRC-102 就诊不可见；EX-IP-PRC-401 未选/重复；EX-IP-PRC-402 非 nurse-pricing；
     *     EX-IP-PRC-403 费用已结算或无可冲销正向行；EX-IP-PRC-404 发放不可回退；EX-IP-PRC-405 域写失败
     */
    public CancelNurseStationPricingResponse cancelNurseStationPricing(CancelNurseStationPricingRequest request) {
        try {
            // Gate：入参非空、≤50、禁止重复键（BR-IP-PRC-021）
            nurseStationInpatientBillingMapper.validateCancelPricingItems(request.getItems());
            Long operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_PRC_405,
                "取消划价失败，请稍后重试");
            List<NurseStationPricingCancelItemDto> items = request.getItems();
            List<Long> encounterIds = new ArrayList<>();
            List<Long> serviceRequestIds = new ArrayList<>();
            List<Long> deviceRequestIds = new ArrayList<>();
            // Pre：按 orderType 分轨收集 id，供后续批量域写
            for (NurseStationPricingCancelItemDto row : items) {
                if (row == null) {
                    continue;
                }
                encounterIds.add(row.getEncounterId());
                if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                    serviceRequestIds.add(row.getRequestId());
                } else if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                    deviceRequestIds.add(row.getRequestId());
                }
            }
            // Gate：各行 encounter 须在护士管理病区可见，且处于可变更费用态（EX-IP-PRC-406）
            this.assertEncountersVisibleForChargeWrite(encounterIds);
            String nursePricingSource = GenerateSource.NURSE_PRICING.getCode();
            // Pre：分轨加载并断言 nurse-pricing 来源（BR-IP-PRC-022）；Request 保持完成态，不软删
            Map<Long, ServiceRequestListModel> serviceModelById =
                serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
            Map<Long, DeviceRequestListModel> deviceModelById =
                deviceRequestQueryService.mapListModelsByIds(deviceRequestIds);
            for (NurseStationPricingCancelItemDto row : items) {
                if (row == null) {
                    continue;
                }
                if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                    ServiceRequestListModel model = serviceModelById.get(row.getRequestId());
                    // 诊疗行须可加载、来源 nurse-pricing、encounter 与列表一致
                    if (model == null || !nursePricingSource.equals(model.getGenerateSourceEnum())
                        || !row.getEncounterId().equals(model.getEncounterId())) {
                        throw new BusinessException(
                            InpatientNurseStationConstants.EX_IP_PRC_402,
                            "所选划价行无效，请刷新列表后重试");
                    }
                } else if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                    DeviceRequestListModel model = deviceModelById.get(row.getRequestId());
                    // 耗材行须可加载、来源 nurse-pricing、encounter 与列表一致
                    if (model == null || !nursePricingSource.equals(model.getGenerateSourceEnum())
                        || !row.getEncounterId().equals(model.getEncounterId())) {
                        throw new BusinessException(
                            InpatientNurseStationConstants.EX_IP_PRC_402,
                            "所选划价行无效，请刷新列表后重试");
                    }
                }
            }
            // Pre：关联费用须全部待结算，且存在未冲销正向行（BR-IP-PRC-023/024）
            Set<Long> chargeAnchorIds = new LinkedHashSet<>(serviceRequestIds);
            chargeAnchorIds.addAll(deviceRequestIds);
            ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
            chargeCmd.setServiceIds(new ArrayList<>(chargeAnchorIds));
            chargeCmd.setExcludeSupersededSettledLines(true);
            List<ChargeItem> linkedCharges = chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
            this.assertCancelPricingChargeGate(linkedCharges, chargeAnchorIds, nursePricingSource);
            this.assertCancelPricingHasOffsetableCharges(linkedCharges, chargeAnchorIds, nursePricingSource);
            // Pre：耗材发放态须可回退（BR-IP-PRC-025 / EX-IP-PRC-404）
            for (Long deviceReqId : deviceRequestIds) {
                this.assertDeviceDispenseRevertAllowed(deviceReqId);
            }
            Map<Long, BigDecimal> inventoryIncreaseAccumulator = new HashMap<>();
            // Cmd：耗材轨按 BR-IP-PRC-019 两轨处理 Dispense → 费用冲销（BR-IP-PRC-025）；Request 不软删
            for (NurseStationPricingCancelItemDto row : items) {
                if (row == null || !ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                    continue;
                }
                Long deviceReqId = row.getRequestId();
                // 已发：待退耗材单对冲+库存回补；未发：软删发放单
                deviceDispenseNurseWorkflowOrchestrator.revertInpatientPricingDeviceDispenses(
                    deviceReqId, operatorId, inventoryIncreaseAccumulator);
                String serviceTable = nurseStationInpatientBillingMapper.resolveCancelServiceTable(row.getOrderType());
                OffsetNursePricingChargeItemsByServiceAnchorsCommand offsetChargeCmd =
                    nurseStationInpatientBillingMapper.toOffsetNursePricingChargeItemsByServiceAnchorsCommand(
                        row.getEncounterId(),
                        serviceTable,
                        deviceReqId,
                        operatorId);
                chargeItemDomainService.offsetNursePricingChargeItemsByServiceAnchors(offsetChargeCmd);
            }
            // Cmd：诊疗轨 Procedure CANCEL 对冲 → 费用冲销（BR-IP-PRC-026）；ServiceRequest 保持完成态
            if (!serviceRequestIds.isEmpty()) {
                nurseStationInpatientBillingProcedureOrchestrator.cancelCompletedNursePricingProcedures(
                    serviceRequestIds, operatorId);
            }
            for (NurseStationPricingCancelItemDto row : items) {
                if (row == null || !ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                    continue;
                }
                String serviceTable = nurseStationInpatientBillingMapper.resolveCancelServiceTable(row.getOrderType());
                OffsetNursePricingChargeItemsByServiceAnchorsCommand offsetChargeCmd =
                    nurseStationInpatientBillingMapper.toOffsetNursePricingChargeItemsByServiceAnchorsCommand(
                        row.getEncounterId(),
                        serviceTable,
                        row.getRequestId(),
                        operatorId);
                chargeItemDomainService.offsetNursePricingChargeItemsByServiceAnchors(offsetChargeCmd);
            }
            // Cmd：已发路径累计回补库存（BR-IP-PRC-019 对称，IncreaseQuantityCommand 批量）
            if (!inventoryIncreaseAccumulator.isEmpty()) {
                inventoryItemDomainService.increaseQuantities(
                    nurseStationInpatientBillingMapper.toIncreaseQuantityCommand(
                        operatorId, inventoryIncreaseAccumulator));
            }
            // Asm：汇总已冲销处理的请求 id（补费父医嘱不变，BR-IP-PRC-027；Request 未软删）
            CancelNurseStationPricingResponse resp = new CancelNurseStationPricingResponse();
            resp.setSuccess(Boolean.TRUE);
            resp.setProcessedCount(items.size());
            resp.setCancelledServiceRequestIds(new ArrayList<>(serviceRequestIds));
            resp.setCancelledDeviceRequestIds(new ArrayList<>(deviceRequestIds));
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_405,
                "取消划价失败，请稍后重试");
        }
    }

    /**
     * IF-IP-PRC-07：修改划价客户可见时间（BR-IP-PRC-030～032、**035**）。
     *
     * <p>诊疗仅改净剩 COMPLETED Procedure.{@code recorded_time}；耗材仅改主发放
     * {@code dispense_time}/{@code prepare_time}。不改 {@code occurrence_time}、Charge 发生时间、Request 创建时间。
     * 有生效出院嘱时 {@code newTime} 不得晚于计划出院。</p>
     *
     * @param request items（同冲销行键）+ newTime
     * @return 成功行数
     * @throws BusinessException EX-IP-PRC-102/206/401/402/403/406/407
     */
    public ChangeNurseStationPricingBusinessTimeResponse changeNurseStationPricingBusinessTime(
        ChangeNurseStationPricingBusinessTimeRequest request) {
        try {
            // Gate：入参非空、≤50、禁止重复键（复用冲销校验）
            nurseStationInpatientBillingMapper.validateCancelPricingItems(request.getItems());
            if (request.getNewTime() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_407,
                    "请选择有效的划价时间");
            }
            Long operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_PRC_407,
                "修改划价时间失败，请稍后重试");
            List<NurseStationPricingCancelItemDto> items = request.getItems();
            LocalDateTime newTime = request.getNewTime();
            List<Long> encounterIds = new ArrayList<>();
            List<Long> serviceRequestIds = new ArrayList<>();
            List<Long> deviceRequestIds = new ArrayList<>();
            // Pre：分轨收集 id
            for (NurseStationPricingCancelItemDto row : items) {
                if (row == null) {
                    continue;
                }
                encounterIds.add(row.getEncounterId());
                if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                    serviceRequestIds.add(row.getRequestId());
                } else if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                    deviceRequestIds.add(row.getRequestId());
                }
            }
            // Gate：可见性 + 出院结算轨（与冲销同口径，BR-IP-PRC-029）
            this.assertEncountersVisibleForChargeWrite(encounterIds);
            // Gate：改划价时间不得晚于计划出院（BR-IP-PRC-035）
            inpatientDischargeBusinessTimeGate.assertNotAfterPlannedDischargeForEncounters(
                encounterIds,
                newTime,
                InpatientNurseStationConstants.EX_IP_PRC_206,
                "划价");
            String nursePricingSource = GenerateSource.NURSE_PRICING.getCode();
            Map<Long, ServiceRequestListModel> serviceModelById =
                serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
            Map<Long, DeviceRequestListModel> deviceModelById =
                deviceRequestQueryService.mapListModelsByIds(deviceRequestIds);
            for (NurseStationPricingCancelItemDto row : items) {
                if (row == null) {
                    continue;
                }
                if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                    ServiceRequestListModel model = serviceModelById.get(row.getRequestId());
                    // 诊疗行须 nurse-pricing 且就诊一致
                    if (model == null || !nursePricingSource.equals(model.getGenerateSourceEnum())
                        || !row.getEncounterId().equals(model.getEncounterId())) {
                        throw new BusinessException(
                            InpatientNurseStationConstants.EX_IP_PRC_402,
                            "所选划价行无效，请刷新列表后重试");
                    }
                } else if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                    DeviceRequestListModel model = deviceModelById.get(row.getRequestId());
                    // 耗材行须 nurse-pricing 且就诊一致
                    if (model == null || !nursePricingSource.equals(model.getGenerateSourceEnum())
                        || !row.getEncounterId().equals(model.getEncounterId())) {
                        throw new BusinessException(
                            InpatientNurseStationConstants.EX_IP_PRC_402,
                            "所选划价行无效，请刷新列表后重试");
                    }
                }
            }
            // Pre：关联费用须全部待结算（与冲销同 Gate，避免已结算后改展示时间）
            Set<Long> chargeAnchorIds = new LinkedHashSet<>(serviceRequestIds);
            chargeAnchorIds.addAll(deviceRequestIds);
            ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
            chargeCmd.setServiceIds(new ArrayList<>(chargeAnchorIds));
            chargeCmd.setExcludeSupersededSettledLines(true);
            List<ChargeItem> linkedCharges = chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
            this.assertCancelPricingChargeGate(linkedCharges, chargeAnchorIds, nursePricingSource);
            Map<Long, Long> primaryDispenseIdByDeviceReqId =
                deviceDispenseQueryService.mapPrimaryDispenseIdByDeviceReqIds(deviceRequestIds);
            // Cmd：诊疗轨只改 recorded_time
            for (NurseStationPricingCancelItemDto row : items) {
                if (row == null || !ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                    continue;
                }
                Optional<Long> procedureIdOpt = procedureQueryService.findNetCompletedProcedureIdByServiceRequest(
                    row.getEncounterId(), row.getRequestId());
                // 无净剩 COMPLETED Procedure 则无法改展示轴
                if (procedureIdOpt.isEmpty()) {
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_PRC_407,
                        "划价执行记录不存在，请刷新后重试");
                }
                AmendProcedureRecordedTimeCommand amendCmd = new AmendProcedureRecordedTimeCommand();
                amendCmd.setProcedureId(procedureIdOpt.get());
                amendCmd.setRecordedTime(newTime);
                amendCmd.setOperatorId(operatorId);
                procedureDomainService.amendProcedureRecordedTime(amendCmd);
            }
            // Cmd：耗材轨只改发放/拣配展示时刻
            for (NurseStationPricingCancelItemDto row : items) {
                if (row == null || !ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                    continue;
                }
                Long dispenseId = primaryDispenseIdByDeviceReqId.get(row.getRequestId());
                // 无主发放则无法改展示轴
                if (dispenseId == null) {
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_PRC_407,
                        "耗材发放记录不存在，请刷新后重试");
                }
                AmendDeviceDispenseDisplayTimeCommand amendCmd = new AmendDeviceDispenseDisplayTimeCommand();
                amendCmd.setDispenseId(dispenseId);
                amendCmd.setDisplayTime(newTime);
                amendCmd.setOperatorId(operatorId);
                deviceDispenseDomainService.amendDeviceDispenseDisplayTime(amendCmd);
            }
            // Asm：整批成功
            ChangeNurseStationPricingBusinessTimeResponse resp = new ChangeNurseStationPricingBusinessTimeResponse();
            resp.setSuccess(Boolean.TRUE);
            resp.setProcessedCount(items.size());
            resp.setMessage("已修改划价时间");
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_407,
                "修改划价时间失败，请稍后重试");
        }
    }

    /**
     * 按客户可见划价时间区间过滤已划价分组（空区间不过滤）。
     *
     * @param groups 已组装分组
     * @param from   下限（含）；可空
     * @param to     上限（含）；可空
     * @return 过滤后分组（空组剔除）
     */
    private List<NurseStationPricingPatientGroupDto> filterPricedGroupsByDisplayTime(
        List<NurseStationPricingPatientGroupDto> groups,
        LocalDateTime from,
        LocalDateTime to) {
        if (groups == null || groups.isEmpty()) {
            return new ArrayList<>();
        }
        // 无时间窗：原样返回
        if (from == null && to == null) {
            return groups;
        }
        List<NurseStationPricingPatientGroupDto> filtered = new ArrayList<>();
        for (NurseStationPricingPatientGroupDto group : groups) {
            if (group == null || group.getPricedOrders() == null) {
                continue;
            }
            List<NurseStationPricedOrderRowDto> kept = new ArrayList<>();
            for (NurseStationPricedOrderRowDto row : group.getPricedOrders()) {
                if (row == null) {
                    continue;
                }
                LocalDateTime pricedTime = row.getPricedTime();
                // 无展示时刻：有时间窗时剔除，避免误入
                if (pricedTime == null) {
                    continue;
                }
                if (from != null && pricedTime.isBefore(from)) {
                    continue;
                }
                if (to != null && pricedTime.isAfter(to)) {
                    continue;
                }
                kept.add(row);
            }
            // 组内无行则不返回该就诊组
            if (kept.isEmpty()) {
                continue;
            }
            NurseStationPricingPatientGroupDto next = new NurseStationPricingPatientGroupDto();
            next.setEncounterId(group.getEncounterId());
            next.setPricedOrders(kept);
            filtered.add(next);
        }
        return filtered;
    }

    /**
     * IF-IP-ARG-02：自动计费配置列表（含护理级别只读区）。
     *
     * @param request encounterId、patientId 必填；billDate 不裁剪护理行（展开费用按日过滤）
     * @return 配置列表与护理只读区
     * @throws BusinessException EX-IP-ARG-201 就诊不可见
     */
    public ListAutoRollConfigsResponse listAutoRollConfigs(ListAutoRollConfigsRequest request) {
        // Gate：就诊与患者主键非空（BR-IP-ARG-001）
        if (request == null || request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ARG_201,
                "当前患者不可用，请刷新左侧列表");
        }
        // billDate 仅透传展示；护理行始终全住院期（BR-IP-ARG-005）
        LocalDate billDate = request.getBillDate();
        // Pre：单就诊可见性门控（BR-IP-ARG-001）
        InpatientEncounterContextQueryModel encounterSnapshot = this.requireVisibleSnapshot(
            List.of(request.getEncounterId()),
            request.getPatientId(),
            InpatientNurseStationConstants.EX_IP_ARG_201);
        Long organizationId = encounterSnapshot.getOrganizationId();
        // Pre：配置列表含 INACTIVE（BR-IP-ARG-018）
        List<ChargeItemAutoGenRuleQueryModel> rules =
            chargeItemAutoGenQueryService.findRulesByEncounterId(request.getEncounterId());
        // Pre：护理级别只读列表（空业务日含已停嘱历史，供展开取消）
        List<InpatientAutoRollNursingLevelQueryModel> nursingLevels =
            serviceRequestQueryService.listNursingLevelsForAutoRollTab(
                request.getEncounterId(), billDate);
        Set<Long> activityIds = new HashSet<>();
        Set<Long> chargeDefinitionIds = new HashSet<>();
        Set<Long> billingPractitionerIds = new HashSet<>();
        // Loop：收集配置行 activity/定价/计费人，供批量预取
        for (ChargeItemAutoGenRuleQueryModel rule : rules) {
            // 空规则行跳过，避免 NPE
            if (rule == null) {
                continue;
            }
            // 床位服务定义主键
            if (rule.getTriggerSourceId() != null) {
                activityIds.add(rule.getTriggerSourceId());
            }
            // 定价主键
            if (rule.getChargeDefinitionId() != null) {
                chargeDefinitionIds.add(rule.getChargeDefinitionId());
            }
            // 计费人主键
            if (rule.getBillingPractitionerId() != null) {
                billingPractitionerIds.add(rule.getBillingPractitionerId());
            }
        }
        // Pre：护理定价按 activity 去重解析，实体直接复用（避免再 findAllById）
        Map<Long, ChargeItemDefinition> nursingDefinitionByActivityId = new HashMap<>();
        for (InpatientAutoRollNursingLevelQueryModel nursingRow : nursingLevels) {
            // 无活动定义的护理行无法解析定价，跳过收集
            if (nursingRow == null || nursingRow.getActivityId() == null) {
                continue;
            }
            activityIds.add(nursingRow.getActivityId());
            // 同 activity 只查价一次
            if (nursingDefinitionByActivityId.containsKey(nursingRow.getActivityId())) {
                continue;
            }
            ChargeItemDefinition nursingDefinition =
                nurseStationAutoRollWriteAssembler.resolveNursingChargeDefinition(nursingRow.getActivityId());
            // 解析到定价则缓存实体供装配
            if (nursingDefinition != null && nursingDefinition.getId() != null) {
                nursingDefinitionByActivityId.put(nursingRow.getActivityId(), nursingDefinition);
            }
        }
        Map<Long, ActivityDefinition> activityById = this.buildActivityDefinitionMap(activityIds);
        Map<Long, ChargeItemDefinition> definitionById = new HashMap<>();
        // 配置定价批量装载
        if (!chargeDefinitionIds.isEmpty()) {
            definitionById.putAll(chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(chargeDefinitionIds));
        }
        // 护理定价实体并入（同 id 不重复查库）
        for (ChargeItemDefinition nursingDefinition : nursingDefinitionByActivityId.values()) {
            if (nursingDefinition == null || nursingDefinition.getId() == null) {
                continue;
            }
            definitionById.putIfAbsent(nursingDefinition.getId(), nursingDefinition);
        }
        // Pre：计费人姓名批量解析（禁止列表 N+1）
        Map<Long, String> billingNameById = Collections.emptyMap();
        if (!billingPractitionerIds.isEmpty()) {
            billingNameById = practitionerQueryService.mapDisplayNameByEntererIds(billingPractitionerIds);
        }
        // Pre：护士下拉可选跳过（刷新/按日查询减负；弹窗首屏再装载）
        List<OptionDto> nurseOptions = Collections.emptyList();
        boolean includeNurseOptions = true;
        // 显式 false 才跳过；空/true 保持兼容首屏装载
        if (Boolean.FALSE.equals(request.getIncludeNurseOptions())) {
            includeNurseOptions = false;
        }
        if (includeNurseOptions) {
            nurseOptions = this.buildAutoRollNurseOptions();
        }
        // Asm：配置区与护理只读列表分别装配（BR-IP-ARG-005 护理区只读）
        ListAutoRollConfigsResponse response = new ListAutoRollConfigsResponse();
        response.setConfigs(nurseStationAutoRollListAssembler.assembleConfigRows(
            rules, activityById, definitionById, organizationId, billingNameById));
        List<NurseStationAutoRollNursingReadonlyDto> nursingDtos = new ArrayList<>();
        for (InpatientAutoRollNursingLevelQueryModel nursingRow : nursingLevels) {
            // 无活动定义无法装配展示名/单价，跳过该行
            if (nursingRow == null || nursingRow.getActivityId() == null) {
                continue;
            }
            ChargeItemDefinition nursingDefinition =
                nursingDefinitionByActivityId.get(nursingRow.getActivityId());
            Map<Long, ChargeItemDefinition> nursingDefinitionById = Collections.emptyMap();
            // 已解析到定价定义时传入单项 Map 供单价展示
            if (nursingDefinition != null && nursingDefinition.getId() != null) {
                nursingDefinitionById = Map.of(nursingDefinition.getId(), nursingDefinition);
            }
            NurseStationAutoRollNursingReadonlyDto dto = nurseStationAutoRollListAssembler.assembleNursingReadonly(
                Optional.of(nursingRow), billDate, activityById, nursingDefinitionById, organizationId);
            // 装配成功才加入列表
            if (dto != null) {
                nursingDtos.add(dto);
            }
        }
        response.setNursingLevels(nursingDtos);
        // 兼容字段：首行（check_time 倒序下通常为最近护理级别）
        if (nursingDtos.isEmpty()) {
            response.setNursingLevelReadonly(null);
        } else {
            response.setNursingLevelReadonly(nursingDtos.get(0));
        }
        response.setNurseOptions(nurseOptions);
        return response;
    }

    /**
     * IF-IP-ARG-03：新增自动计费配置。
     *
     * @param request 诊疗活动与定价必填；quantity 空则默认 1
     * @return 新建规则主键
     * @throws BusinessException EX-IP-ARG-201/301/302
     */
    public CreateAutoGenRuleResponse createAutoGenRule(CreateAutoGenRuleRequest request) {
        try {
            // Gate：请求体非空（BR-IP-ARG-003）
            if (request == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "配置参数无效，请刷新后重试");
            }
            // Gate：必填与数量合法性（BR-IP-ARG-003/004）
            this.validateAutoGenWriteRequest(
                request.getEncounterId(),
                request.getPatientId(),
                request.getRuleName(),
                request.getActivityId(),
                request.getChargeDefinitionId(),
                request.getQuantityValue());
            Long operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_ARG_201,
                "操作失败，请重新登录");
            // Pre：就诊可见（BR-IP-ARG-001）
            InpatientEncounterContextQueryModel encounterSnapshot = this.requireVisibleSnapshot(
                List.of(request.getEncounterId()),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_ARG_201);
            this.assertEncounterAllowsChargeWrite(encounterSnapshot);
            // Pre：可日结诊疗类目与定价有效（BR-IP-ARG-003/004）；排除护理/转科/出院/嘱托
            String activityCategoryCode = this.assertAutoRollConfigurableActivity(request.getActivityId());
            this.assertChargeDefinitionValid(request.getChargeDefinitionId());
            var command = nurseStationAutoRollMapper.toCreateAutoGenRuleCommand(request, operatorId);
            // Pre：未指定执行科室时锚定患者当前住院科室（BR-IP-ARG-018；与滚费 findActiveRulesByEncounter 上下文一致）
            if (command.getPerformingOrgId() == null) {
                command.setPerformingOrgId(encounterSnapshot.getOrganizationId());
            }
            // Pre：业务时刻缺省日末；计费人缺省当前登录人（BR-IP-ARG-015/019）
            if (command.getBusinessTime() == null) {
                command.setBusinessTime(InpatientNurseStationConstants.DAY_END_CLOCK);
            }
            if (command.getBillingPractitionerId() == null) {
                command.setBillingPractitionerId(operatorId);
            }
            // Cmd：写库默认 ACTIVE（BR-IP-ARG-016）
            Long ruleId = chargeItemAutoGenDomainService.createAutoGenRule(command);
            // Cmd：ensure 长期滚费 SR（BR-IP-ARG-019；配置保存即 VERIFIED，禁止手动执行）
            var ensureCommand = nurseStationAutoRollMapper.toEnsureAutoRollBedLongTermServiceRequestCommand(
                request, ruleId, operatorId);
            ensureCommand.setCategoryEnum(activityCategoryCode);
            ensureCommand.setUnitCode(
                nurseStationAutoRollWriteAssembler.resolveActivityOrderUnitCode(request.getActivityId()));
            serviceRequestDomainService.ensureAutoRollBedLongTermServiceRequest(ensureCommand);
            CreateAutoGenRuleResponse response = new CreateAutoGenRuleResponse();
            response.setRuleId(ruleId);
            return response;
        } catch (BusinessException ex) {
            throw this.translateAutoGenBusinessException(ex);
        }
    }

    /**
     * IF-IP-ARG-04：更新自动计费配置（非空覆盖，不改启停）。
     *
     * @param request ruleId 必填；activity/charge 非空时复用 IF-03 校验
     * @return success=true
     * @throws BusinessException EX-IP-ARG-201/301/302/401
     */
    public UpdateAutoGenRuleResponse updateAutoGenRule(UpdateAutoGenRuleRequest request) {
        try {
            // Gate：ruleId 与就诊必填
            if (request == null || request.getRuleId() == null
                || request.getEncounterId() == null || request.getPatientId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "配置参数无效，请刷新后重试");
            }
            if (request.getQuantityValue() != null
                && request.getQuantityValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "数量必须大于零");
            }
            Long operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_ARG_201,
                "操作失败，请重新登录");
            // Pre：就诊可见
            InpatientEncounterContextQueryModel updateSnapshot = this.requireVisibleSnapshot(
                List.of(request.getEncounterId()),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_ARG_201);
            this.assertEncounterAllowsChargeWrite(updateSnapshot);
            // Pre：按需校验可日结诊疗类目与定价（排除护理/转科/出院/嘱托）
            if (request.getActivityId() != null) {
                this.assertAutoRollConfigurableActivity(request.getActivityId());
            }
            if (request.getChargeDefinitionId() != null) {
                this.assertChargeDefinitionValid(request.getChargeDefinitionId());
            }
            var command = nurseStationAutoRollMapper.toUpdateAutoGenRuleCommand(request, operatorId);
            // Cmd：写库点（组合键冲突由域抛出）
            chargeItemAutoGenDomainService.updateAutoGenRule(command);
            UpdateAutoGenRuleResponse response = new UpdateAutoGenRuleResponse();
            response.setSuccess(Boolean.TRUE);
            return response;
        } catch (BusinessException ex) {
            throw this.translateAutoGenBusinessException(ex);
        }
    }

    /**
     * IF-IP-ARG-05：删除自动计费配置（软删）。
     *
     * @param request ruleId、encounterId、patientId 必填
     * @return success=true
     * @throws BusinessException EX-IP-ARG-201/401
     */
    public DeleteAutoGenRuleResponse deleteAutoGenRule(DeleteAutoGenRuleRequest request) {
        try {
            if (request == null || request.getRuleId() == null
                || request.getEncounterId() == null || request.getPatientId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "配置参数无效，请刷新后重试");
            }
            Long operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_ARG_201,
                "操作失败，请重新登录");
            InpatientEncounterContextQueryModel deleteSnapshot = this.requireVisibleSnapshot(
                List.of(request.getEncounterId()),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_ARG_201);
            this.assertEncounterAllowsChargeWrite(deleteSnapshot);
            var command = nurseStationAutoRollMapper.toDeleteAutoGenRuleCommand(request, operatorId);
            chargeItemAutoGenDomainService.deleteAutoGenRule(command);
            DeleteAutoGenRuleResponse response = new DeleteAutoGenRuleResponse();
            response.setSuccess(Boolean.TRUE);
            return response;
        } catch (BusinessException ex) {
            throw this.translateAutoGenBusinessException(ex);
        }
    }

    /**
     * IF-IP-ARG-06：启用自动计费配置（INACTIVE→ACTIVE）。
     *
     * @param request ruleId、encounterId、patientId 必填
     * @return success=true
     * @throws BusinessException EX-IP-ARG-201/401/501
     */
    public ActivateAutoGenRuleResponse activateAutoGenRule(ActivateAutoGenRuleRequest request) {
        try {
            if (request == null || request.getRuleId() == null
                || request.getEncounterId() == null || request.getPatientId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "配置参数无效，请刷新后重试");
            }
            Long operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_ARG_201,
                "操作失败，请重新登录");
            InpatientEncounterContextQueryModel activateSnapshot = this.requireVisibleSnapshot(
                List.of(request.getEncounterId()),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_ARG_201);
            this.assertEncounterAllowsChargeWrite(activateSnapshot);
            var command = nurseStationAutoRollMapper.toActivateAutoGenRuleCommand(request, operatorId);
            chargeItemAutoGenDomainService.activateAutoGenRule(command);
            ActivateAutoGenRuleResponse response = new ActivateAutoGenRuleResponse();
            response.setSuccess(Boolean.TRUE);
            return response;
        } catch (BusinessException ex) {
            throw this.translateAutoGenBusinessException(ex);
        }
    }

    /**
     * IF-IP-ARG-07：停用自动计费配置（ACTIVE→INACTIVE）。
     *
     * @param request ruleId、encounterId、patientId 必填
     * @return success=true
     * @throws BusinessException EX-IP-ARG-201/401/501
     */
    public SuspendAutoGenRuleResponse suspendAutoGenRule(SuspendAutoGenRuleRequest request) {
        try {
            if (request == null || request.getRuleId() == null
                || request.getEncounterId() == null || request.getPatientId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "配置参数无效，请刷新后重试");
            }
            Long operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_ARG_201,
                "操作失败，请重新登录");
            InpatientEncounterContextQueryModel suspendSnapshot = this.requireVisibleSnapshot(
                List.of(request.getEncounterId()),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_ARG_201);
            this.assertEncounterAllowsChargeWrite(suspendSnapshot);
            var command = nurseStationAutoRollMapper.toSuspendAutoGenRuleCommand(request, operatorId);
            chargeItemAutoGenDomainService.suspendAutoGenRule(command);
            SuspendAutoGenRuleResponse response = new SuspendAutoGenRuleResponse();
            response.setSuccess(Boolean.TRUE);
            return response;
        } catch (BusinessException ex) {
            throw this.translateAutoGenBusinessException(ex);
        }
    }

    /**
     * IF-IP-ARG-09：查询自动计费已滚费用记录（配置行 / 护理行展开）。
     *
     * <p>读侧：床位配置 → 长期 SR（auto-roll-fees）；护理行 → 护理级别医嘱 SR；
     * 再列 order-execute Charge。取消已滚费用不走本 IF，复用 **AS-IP-NUR-012 IF-IP-CXL-01**。</p>
     *
     * @param request encounterId、patientId 必填；ruleId 与 serviceRequestId 二选一；dateFrom/dateTo 可选
     * @return 已滚费用行（含 procedureId / cancelable）
     * @throws BusinessException EX-IP-ARG-201/401/901/902
     */
    public ListAutoRollPostedChargesResponse listAutoRollPostedCharges(
        ListAutoRollPostedChargesRequest request) {
        // Gate：就诊、患者必填；ruleId 与护理 serviceRequestId 二选一（BR-IP-ARG-001 / BR-IP-ARG-020）
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ARG_201,
                "当前患者不可用，请刷新左侧列表");
        }
        boolean hasRuleId = request.getRuleId() != null;
        boolean hasNursingServiceRequestId = request.getServiceRequestId() != null;
        // Gate：ruleId 与 nursing serviceRequestId 必须恰好其一（BR-IP-ARG-020）
        if (hasRuleId == hasNursingServiceRequestId) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ARG_201,
                "请指定自动计费配置或护理医嘱后重试");
        }
        // Pre：单就诊可见性门控
        this.requireVisibleSnapshot(
            List.of(request.getEncounterId()),
            request.getPatientId(),
            InpatientNurseStationConstants.EX_IP_ARG_201);
        Long serviceRequestId;
        Long responseRuleId = null;
        // 床位配置展开：ruleId → auto-roll-fees 长期 SR
        if (hasRuleId) {
            // Pre：配置须属于本就诊且未软删（BR-IP-ARG-020）
            ChargeItemAutoGenRuleQueryModel matchedRule = null;
            List<ChargeItemAutoGenRuleQueryModel> rules =
                chargeItemAutoGenQueryService.findRulesByEncounterId(request.getEncounterId());
            for (ChargeItemAutoGenRuleQueryModel rule : rules) {
                // 命中本就诊下目标配置行
                if (rule != null && request.getRuleId().equals(rule.getId())) {
                    matchedRule = rule;
                    break;
                }
            }
            // 配置不存在或已软删：禁止展开
            if (matchedRule == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_401,
                    "自动计费配置不存在或已删除");
            }
            // Pre：解析床位滚费长期 SR（based_on=ruleId）
            Optional<Long> serviceRequestIdOpt =
                serviceRequestQueryService.findAutoRollBedServiceRequestIdByRuleId(request.getRuleId());
            // ensure 尚未落库时无法列已滚费用
            if (serviceRequestIdOpt.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_902,
                    "该配置尚未关联床位滚费医嘱，无法查看计费记录");
            }
            serviceRequestId = serviceRequestIdOpt.get();
            responseRuleId = request.getRuleId();
        } else {
            // 护理行展开：直接使用护理级别医嘱 SR（医生开立）
            // Pre：护理医嘱须属于本就诊且为已校对/已停嘱护理级别
            Optional<InpatientAutoRollNursingLevelQueryModel> nursingOpt =
                serviceRequestQueryService.findNursingLevelForAutoRollPostedCharges(
                    request.getEncounterId(), request.getServiceRequestId());
            // 护理 SR 无效或不属于本就诊：禁止展开
            if (nursingOpt.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_401,
                    "护理级别医嘱不存在或已删除");
            }
            serviceRequestId = request.getServiceRequestId();
        }
        List<ChargeItem> chargeRows;
        try {
            // Pre：按 SR 拉取 order-execute 费用（可选自然日闭区间）
            chargeRows = chargeItemQueryService.listOrderExecuteChargesByServiceRequestId(
                serviceRequestId,
                request.getDateFrom(),
                request.getDateTo());
        } catch (RuntimeException ex) {
            log.error(
                "自动计费已滚费用加载失败 ruleId={} serviceRequestId={}",
                request.getRuleId(),
                serviceRequestId,
                ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ARG_901,
                "计费记录加载失败，请稍后重试");
        }
        // Asm：装配展开行（含取消执行可点性）
        ListAutoRollPostedChargesResponse response = new ListAutoRollPostedChargesResponse();
        response.setRuleId(responseRuleId);
        response.setServiceRequestId(serviceRequestId);
        response.setCharges(nurseStationAutoRollListAssembler.assemblePostedChargeRows(
            chargeRows, request.getEncounterId()));
        return response;
    }

    /**
     * IF-IP-ARG-08：住院滚费定时任务（系统调度，无 UI 按钮）。
     *
     * <p>cron 须为 {@code 0 10 0 * * ?}（00:10 Asia/Shanghai），与库存备份 {@code 0 5 0 * * ?} 错开；
     * {@link com.zxplat.framework.core.infrastructure.job.SchedulableJob#openAppServiceTransaction()} 关闭外层 UoW，
     * 单就诊失败不拖整批、不与备份抢长事务。</p>
     *
     * @param request billDate 空则前一日；操作人/审计人一律取配置计费人（或其回落责任护士），禁止写死种子 admin
     * @return 扫描与落账计数汇总
     */
    @SchedulableJob(value = "住院滚费", openAppServiceTransaction = false)
    public ExecuteInpatientAutoRollJobResponse executeInpatientAutoRollJob(
        ExecuteInpatientAutoRollJobRequest request) {
        // Gate：业务日缺省——固定 Asia/Shanghai；00:00～00:15 用前一日（定时窗口 BR-IP-ARG-006）；
        // 其余未传参视为手工补跑当日。补跑昨天须显式传 billDate，或在 00:15 前触发。
        LocalDate billDate = null;
        if (request != null) {
            billDate = request.getBillDate();
        }
        if (billDate == null) {
            ZoneId shanghai = ZoneId.of("Asia/Shanghai");
            LocalDateTime triggerNow = LocalDateTime.now(shanghai);
            LocalTime triggerClock = triggerNow.toLocalTime();
            if (triggerClock.isBefore(LocalTime.of(0, 15))) {
                billDate = triggerNow.toLocalDate().minusDays(1);
            } else {
                billDate = triggerNow.toLocalDate();
            }
        }
        // Pre：在院 encounter 全量扫描（分床后、清床前）
        List<InpatientAutoRollEncounterQueryModel> contexts =
            inpatientAutoRollEncounterContextLoader.listInpatientAutoRollEncounterContexts();
        int scannedEncounterCount = 0;
        if (contexts != null) {
            scannedEncounterCount = contexts.size();
        }
        int processedEncounterCount = 0;
        int createdChargeItemCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        LocalDateTime triggerTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (contexts != null) {
            // Loop：逐 encounter 门控后委托处理器（单 encounter 异常不阻断批次）
            for (InpatientAutoRollEncounterQueryModel ctx : contexts) {
                if (ctx == null || ctx.getEncounterId() == null) {
                    continue;
                }
                LocalDateTime rollIntervalStart = this.resolveAutoRollIntervalStart(ctx);
                // Gate：billDate 当日尚未首次分床入科则跳过（BR-IP-ARG-007；锚 encounter.start_time，转床不改）
                if (rollIntervalStart != null && rollIntervalStart.toLocalDate().isAfter(billDate)) {
                    skippedCount++;
                    log.debug(
                        "住院滚费跳过 encounterId={} reason=admitAfterBillDate rollStart={} billDate={}",
                        ctx.getEncounterId(),
                        rollIntervalStart,
                        billDate);
                    continue;
                }
                // Gate：首次分床当日补跑且触发时刻早于次日 00:00 则跳过（须跨午夜；转床日不适用）
                if (rollIntervalStart != null
                    && rollIntervalStart.toLocalDate().equals(billDate)
                    && triggerTime.isBefore(billDate.plusDays(1).atStartOfDay())) {
                    skippedCount++;
                    log.info(
                        "住院滚费跳过 encounterId={} reason=firstBedDayNotCrossMidnight rollStart={} billDate={} triggerTime={}",
                        ctx.getEncounterId(),
                        rollIntervalStart,
                        billDate,
                        triggerTime);
                    continue;
                }
                // Gate：记进不计出——已办理出院且出院自然日不晚于 billDate：出院日及之后整 encounter 不滚（BR-IP-ARG-008）
                if (this.isAutoRollDischargeDayOrAfter(ctx.getEndTime(), billDate)) {
                    skippedCount++;
                    log.debug(
                        "住院滚费跳过 encounterId={} reason=dischargeDayOrAfter endTime={} billDate={}",
                        ctx.getEncounterId(),
                        ctx.getEndTime(),
                        billDate);
                    continue;
                }
                // Gate：无挂账账户无法落 Charge，整 encounter 跳过
                if (ctx.getAccountId() == null) {
                    skippedCount++;
                    log.warn(
                        "住院滚费跳过 encounterId={} reason=noChargeAccount patientId={}",
                        ctx.getEncounterId(),
                        ctx.getPatientId());
                    continue;
                }
                try {
                    // Pre：责任护士仅作配置未配计费人 / 护理分支回落（BR-IP-ARG-019）；禁止种子 admin
                    AutoRollEncounterProcessResult encounterResult = nurseStationAutoRollEncounterProcessor.processEncounter(
                        ctx, billDate, ctx.getPrimaryNurseId());
                    createdChargeItemCount += encounterResult.getCreatedChargeItemCount();
                    skippedCount += encounterResult.getSkippedCount();
                    failedCount += encounterResult.getFailedCount();
                    if (encounterResult.isProcessedEncounter()) {
                        processedEncounterCount++;
                    }
                } catch (RuntimeException ex) {
                    failedCount++;
                    log.warn("住院滚费单就诊处理失败 encounterId={}", ctx.getEncounterId(), ex);
                }
            }
        }
        // Asm：汇总任务级计数（无外层大事务，部分成功仍返回 200）
        ExecuteInpatientAutoRollJobResponse response = new ExecuteInpatientAutoRollJobResponse();
        response.setBillDate(billDate);
        response.setScannedEncounterCount(scannedEncounterCount);
        response.setProcessedEncounterCount(processedEncounterCount);
        response.setCreatedChargeItemCount(createdChargeItemCount);
        response.setSkippedCount(skippedCount);
        response.setFailedCount(failedCount);
        log.info(
            "住院滚费任务完成 billDate={} scanned={} processed={} created={} skipped={} failed={}",
            billDate,
            scannedEncounterCount,
            processedEncounterCount,
            createdChargeItemCount,
            skippedCount,
            failedCount);
        if (scannedEncounterCount > 0 && createdChargeItemCount == 0) {
            log.warn(
                "住院滚费未生成任何待结算费用 billDate={} scanned={} skipped={} failed={}；"
                    + "常见原因：JobDataMap 残留 billDate 被纠正后仍全跳过、首次分床日未跨午夜(BR-IP-ARG-007)、"
                    + "出院日及之后(记进不计出)、无个人账户、配置未ACTIVE/护理已停嘱、同日已幂等落账、"
                    + "无计费人/责任护士或定价解析失败。请核对日志中单条 failed 原因。",
                billDate,
                scannedEncounterCount,
                skippedCount,
                failedCount);
        }
        return response;
    }

    /**
     * 解析滚费在院区间起点：优先就诊 start_time（首次分床锚点；转床不更新），回落首次 BED 轨迹（BR-IP-ARG-007）。
     *
     * @param ctx 滚费就诊上下文
     * @return 首次分床/入科时刻；均无则 null
     */
    private LocalDateTime resolveAutoRollIntervalStart(InpatientAutoRollEncounterQueryModel ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.getStartTime() != null) {
            return ctx.getStartTime();
        }
        return ctx.getBedAssignedAt();
    }

    /**
     * 是否为出院日及之后的业务日（记进不计出：出院日整 encounter 不滚床位/护理）。
     *
     * @param endTime  就诊 end_time（办理出院写入；未出院可空）
     * @param billDate 滚费业务日
     * @return 应跳过滚费时为 true
     */
    private boolean isAutoRollDischargeDayOrAfter(LocalDateTime endTime, LocalDate billDate) {
        if (endTime == null || billDate == null) {
            return false;
        }
        // endDate <= billDate → 出院日及之后不计
        return !endTime.toLocalDate().isAfter(billDate);
    }

    /**
     * 组装自动计费配置弹窗「计费人」护士下拉。
     *
     * @return OptionDto 列表（value=practitionerId）
     */
    private List<OptionDto> buildAutoRollNurseOptions() {
        List<Practitioner> nurses = practitionerRoleScopeLoader.listActivePractitionersForPlatformRole(
            PlatformRoleCodeConstants.ROLE_NURSE, null);
        List<OptionDto> options = new ArrayList<>();
        if (nurses == null || nurses.isEmpty()) {
            return options;
        }
        for (Practitioner practitioner : nurses) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            String label = practitioner.getName();
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(practitioner.getId());
            }
            options.add(new OptionDto(practitioner.getId(), String.valueOf(practitioner.getId()), label));
        }
        return options;
    }

    /**
     * IF-IP-PRC-02/04 写路径公共编排：重算价、Gate、按产品类型分流落库。
     *
     * <p>{@code pricingTime} 写入客户可见展示轴：诊疗 Procedure.recorded（创建时与 occurrence 同值）；
     * 耗材在发放单生成后 {@code amendDeviceDispenseDisplayTime} 回写 dispense/prepare。
     * 空则取当前时刻。有生效出院嘱时 {@code pricingTime} 不得晚于计划出院（**BR-IP-PRC-035**）。</p>
     *
     * @param items       1～50 行；直接划价无 based_on，补费由 Mapper 写入 based_on
     * @param pricingTime 整批划价客户可见时刻；可空
     * @return 写结果与新建 id 列表
     * @throws BusinessException EX-IP-PRC-206 划价时间晚于计划出院
     */
    private ConfirmDirectNurseStationPricingResponse confirmPricingWrite(
        List<NurseStationPricingWriteItemDto> items,
        LocalDateTime pricingTime) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_204,
                "划价项目无效或价格异常，请重新选品");
        }
        if (items.size() > InpatientNurseStationConstants.PRICING_WRITE_BATCH_MAX) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_204,
                "划价项目无效或价格异常，请重新选品");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_PRC_205,
            "划价失败，请稍后重试");
        // Pre：开嘱人回落登录人；Procedure 执行人/记录人固定 operatorId（B.6.5 登录医务人员）
        Long requesterId = currentPractitionerResolver.findPractitionerIdOrNull();
        if (requesterId == null) {
            requesterId = operatorId;
        }
        // Pre：整批客户可见划价时刻；前端可选改时，空则当前时刻
        LocalDateTime effectivePricingTime = pricingTime;
        if (effectivePricingTime == null) {
            effectivePricingTime = LocalDateTime.now();
        }
        Set<Long> encounterIds = new HashSet<>();
        for (NurseStationPricingWriteItemDto row : items) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        this.assertEncountersVisibleForChargeWrite(new ArrayList<>(encounterIds));
        // Gate：划价时间不得晚于计划出院（有生效出院嘱时；BR-IP-PRC-035 / EX-IP-PRC-206）
        inpatientDischargeBusinessTimeGate.assertNotAfterPlannedDischargeForEncounters(
            encounterIds,
            effectivePricingTime,
            InpatientNurseStationConstants.EX_IP_PRC_206,
            "划价");
        // Pre：医保入院费用挂医保户（覆盖前端可能回传的个人现金户）
        Map<Long, Account> chargeAccountByEncounterId =
            accountQueryService.mapDefaultChargeAccountsByEncounterIds(encounterIds);
        for (NurseStationPricingWriteItemDto row : items) {
            // 空行或无就诊锚点跳过，后续 validateWriteRow 会拦
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            Account chargeAccount = chargeAccountByEncounterId.get(row.getEncounterId());
            // 无挂账账户则整批拒绝，避免落到空 account_id
            if (chargeAccount == null || chargeAccount.getId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_204,
                    "划价项目无效或价格异常，请重新选品");
            }
            row.setAccountId(chargeAccount.getId());
        }
        // 写路径入账价：耗材 performLocation 须由 IF-IP-PRC-01 目录回传，不再服务端 match 模板
        nurseStationInpatientBillingWriteAssembler.reapplyAuthoritativeWritePricing(
            items,
            new ArrayList<>(encounterIds),
            managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty());
        List<NurseStationPricingWriteItemDto> serviceRows = new ArrayList<>();
        List<NurseStationPricingWriteItemDto> deviceRows = new ArrayList<>();
        for (NurseStationPricingWriteItemDto row : items) {
            if (row == null) {
                continue;
            }
            this.validateWriteRow(row);
            // 吸氧/心电：当日上限 + 在院余量 Gate（BR-IP-PRC-010）
            if (Boolean.TRUE.equals(row.getOxygenOrEcgFlag())
                || StringUtils.isNotEmpty(row.getDurationGateKind())) {
                nurseStationInpatientBillingWriteAssembler.assertOxygenOrEcgDuration(row);
            }
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTableName())) {
                serviceRows.add(row);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getProductTableName())) {
                // 耗材划价必须指定发放库房（BR-IP-PRC-009），不做库存校验
                if (row.getPerformLocation() == null) {
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_PRC_202,
                        "耗材划价失败：请选择发放库房");
                }
                deviceRows.add(row);
            } else {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_204,
                    "划价项目无效或价格异常，请重新选品");
            }
        }
        // Gate：执行科室为 TECH 的诊疗禁止护士划价（须医技工作站执行）
        this.assertNursePricingActivitiesNotTechOrg(serviceRows);
        Long loginWorkOrgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            InpatientNurseStationConstants.EX_IP_PRC_205,
            "划价失败，请稍后重试");
        InpatientEncounterContextQueryModel orgSnapshot = this.requireVisibleSnapshot(
            new ArrayList<>(encounterIds), items.get(0).getPatientId());
        Long orgId = orgSnapshot.getOrganizationId();
        List<Long> createdServiceIds = new ArrayList<>();
        List<Long> createdDeviceIds = new ArrayList<>();
        // 耗材轨：请求 COMPLETED + 发放单按开关已配药/已发药 + 费用待结算（BR-IP-PRC-008）
        Map<Long, BigDecimal> inventoryDecreaseAccumulator = new HashMap<>();
        if (!deviceRows.isEmpty()) {
            var deviceCmd = nurseStationInpatientBillingMapper.toCreateExecutedDeviceOrderCommand(deviceRows, operatorId);
            List<Long> deviceReqIds = deviceRequestDomainService.createExecutedDeviceOrder(deviceCmd);
            createdDeviceIds.addAll(deviceReqIds);
            for (int i = 0; i < deviceRows.size(); i++) {
                Long deviceReqId = deviceReqIds.get(i);
                NurseStationPricingWriteItemDto row = deviceRows.get(i);
                var genCmd = nurseStationInpatientBillingMapper.toGenerateDeviceDispenseFromTemporaryPathCommand(
                    deviceReqId, row, operatorId);
                deviceDispenseDomainService.generateDeviceDispenseFromTemporaryPath(genCmd);
                deviceDispenseNurseWorkflowOrchestrator.finalizeInpatientTemporaryPathDispense(
                    deviceReqId, operatorId, requesterId, inventoryDecreaseAccumulator);
                this.assertNursePricingDeviceDispenseFinalized(
                    deviceReqId, operatorId, requesterId, inventoryDecreaseAccumulator);
                DeviceDispenseListModel primaryDispense =
                    deviceDispenseQueryService.resolvePrimaryDispenseForDeviceRequest(deviceReqId);
                Long dispenseId = null;
                if (primaryDispense != null) {
                    dispenseId = primaryDispense.getId();
                }
                // Pre：FIFO 实发批号可能与预划价 lot 不一致；按主发放批号重算 definitionId/单价后再挂账
                this.repriceDeviceWriteRowFromFinalizedDispenseLot(
                    row, primaryDispense, new ArrayList<>(encounterIds));
                // Cmd：客户可见划价时刻回写发放/拣配展示轴（与 IF-07 同域能力）
                if (dispenseId != null) {
                    AmendDeviceDispenseDisplayTimeCommand amendCmd = new AmendDeviceDispenseDisplayTimeCommand();
                    amendCmd.setDispenseId(dispenseId);
                    amendCmd.setDisplayTime(effectivePricingTime);
                    amendCmd.setOperatorId(operatorId);
                    deviceDispenseDomainService.amendDeviceDispenseDisplayTime(amendCmd);
                }
                var chargeCmd = nurseStationInpatientBillingMapper.toCreateChargeItemCommand(
                    row,
                    TableNameConstants.WKF_DEVICE_REQUEST,
                    deviceReqId,
                    TableNameConstants.WKF_DEVICE_DISPENSE,
                    dispenseId,
                    null,
                    operatorId,
                    orgId);
                // 挂麻醉申请父项的手麻计费 → Charge 落「麻醉划价」（Request 仍 nurse-pricing）
                this.applyAnesthesiaPricingGenerateSourceIfNeeded(chargeCmd, row);
                chargeItemDomainService.priceByNurseStation(chargeCmd);
            }
            if (!inventoryDecreaseAccumulator.isEmpty()) {
                inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, inventoryDecreaseAccumulator);
            }
        }
        // 诊疗轨：请求 VERIFIED → Procedure COMPLETED → 父+子费用待结算（BR-IP-PRC-007；套餐子行冻价 BR-IP-PRC-011）
        if (!serviceRows.isEmpty()) {
            var serviceCmd = nurseStationInpatientBillingMapper.toCreateFromNursePricingCommand(
                serviceRows, operatorId, requesterId);
            List<Long> serviceRequestIds = serviceRequestDomainService.createFromNursePricing(serviceCmd);
            createdServiceIds.addAll(serviceRequestIds);
            for (int i = 0; i < serviceRows.size(); i++) {
                NurseStationPricingWriteItemDto row = serviceRows.get(i);
                Long serviceRequestId = serviceRequestIds.get(i);
                // Cmd：划价即视为当场执行；执行人/记录人固定为当前登录医务人员；recorded/occurrence 用整批 pricingTime
                Long procedureId = nurseStationInpatientBillingProcedureOrchestrator.registerCompletedNursePricingProcedure(
                    serviceRequestId,
                    row.getEncounterId(),
                    row.getPatientId(),
                    operatorId,
                    effectivePricingTime,
                    loginWorkOrgId);
                var chargeCmd = nurseStationInpatientBillingMapper.toCreateChargeItemCommand(
                    row,
                    TableNameConstants.WKF_SERVICE_REQUEST,
                    serviceRequestId,
                    null,
                    null,
                    procedureId,
                    operatorId,
                    orgId);
                // 挂麻醉申请父项的手麻计费 → Charge 落「麻醉划价」（Request 仍 nurse-pricing）
                this.applyAnesthesiaPricingGenerateSourceIfNeeded(chargeCmd, row);
                Long parentChargeId = chargeItemDomainService.priceByNurseStation(chargeCmd);
                // Cmd：有套餐子表时按当时 SALE 冻价写子账单（parent_id 挂父；结算只认子行）
                Long activityId = row.getActivityId();
                if (activityId == null) {
                    activityId = row.getProductDefinitionId();
                }
                activityDefinitionPackageChargePersistSupport.persistPackageChildChargeItemsAfterParent(
                    parentChargeId, activityId, operatorId);
            }
        }
        ConfirmDirectNurseStationPricingResponse resp = new ConfirmDirectNurseStationPricingResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setProcessedCount(items.size());
        resp.setCreatedServiceRequestIds(createdServiceIds);
        resp.setCreatedDeviceRequestIds(createdDeviceIds);
        return resp;
    }

    /**
     * 护士划价诊疗写路径：执行科室为 TECH 的活动定义拒绝（须医技工作站执行）。
     *
     * @param serviceRows 诊疗划价行
     */
    private void assertNursePricingActivitiesNotTechOrg(List<NurseStationPricingWriteItemDto> serviceRows) {
        // Gate：无诊疗行则跳过
        if (serviceRows == null || serviceRows.isEmpty()) {
            return;
        }
        Set<Long> activityIds = new HashSet<>();
        for (NurseStationPricingWriteItemDto row : serviceRows) {
            if (row == null) {
                continue;
            }
            Long activityId = row.getActivityId();
            // 业务：activityId 空时回落产品定义主键（同目录选品）
            if (activityId == null) {
                activityId = row.getProductDefinitionId();
            }
            if (activityId == null) {
                continue;
            }
            activityIds.add(activityId);
        }
        if (activityIds.isEmpty()) {
            return;
        }
        List<ActivityDefinition> activities = activityDefinitionQueryService.findAllByIds(activityIds);
        Set<Long> orgIds = new HashSet<>();
        Map<Long, Long> activityOrgById = new HashMap<>();
        if (activities != null) {
            for (ActivityDefinition activity : activities) {
                if (activity == null || activity.getId() == null || activity.getOrgId() == null) {
                    continue;
                }
                activityOrgById.put(activity.getId(), activity.getOrgId());
                orgIds.add(activity.getOrgId());
            }
        }
        Map<Long, Organization> orgById = this.organizationQueryService.mapByIds(orgIds);
        for (NurseStationPricingWriteItemDto row : serviceRows) {
            if (row == null) {
                continue;
            }
            Long activityId = row.getActivityId();
            if (activityId == null) {
                activityId = row.getProductDefinitionId();
            }
            if (activityId == null) {
                continue;
            }
            Long activityOrgId = activityOrgById.get(activityId);
            // 业务：无执行科室定义则不拦（目录残缺由定价 Gate 另拦）
            if (activityOrgId == null) {
                continue;
            }
            Organization org = orgById.get(activityOrgId);
            // Gate：TECH 执行科项目不可护士划价
            if (org != null && org.getClassEnum() == OrganizationClassification.TECH) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_204,
                    "医技执行科室项目请在医技工作站处理，护士站不可划价");
            }
        }
    }

    /**
     * 写行定价字段完整性（BR-IP-PRC-013/204；价目须已由 {@link NurseStationInpatientBillingWriteAssembler#reapplyAuthoritativeWritePricing} 重算）。
     *
     * @param row 待提交行
     */
    private void validateWriteRow(NurseStationPricingWriteItemDto row) {
        if (row.getAccountId() == null || row.getChargeItemDefinitionId() == null
            || row.getQuantity() == null || row.getUnitPrice() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_204,
                "划价项目无效或价格异常，请重新选品");
        }
    }

    /**
     * 补费父医嘱须为医生开立且状态可补（BR-IP-PRC-005/301）。
     *
     * @param anchor      父锚点
     * @param encounterId 就诊
     */
    private void assertParentOrderAllowed(NurseStationPricingParentRequestAnchorDto anchor, Long encounterId) {
        if (anchor == null || anchor.getRequestId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_301,
                "关联医嘱无效，请刷新医嘱列表后重试");
        }
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(anchor.getOrderType())) {
            // 药品父医嘱：须在就诊下医生开立列表中且状态可补
            List<MedicationRequestListQueryModel> meds =
                medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                    encounterId, doctorSource);
            for (MedicationRequestListQueryModel med : meds) {
                if (med != null && anchor.getRequestId().equals(med.getId())) {
                    if (!doctorSource.equals(med.getGenerateSourceEnum())) {
                        throw new BusinessException(
                            InpatientNurseStationConstants.EX_IP_PRC_301,
                            "关联医嘱无效，请刷新医嘱列表后重试");
                    }
                    if (this.isAllowedParentStatus(med.getStatusEnum())) {
                        return;
                    }
                }
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(anchor.getOrderType())) {
            // 诊疗父医嘱：经 OrderInfo 校验来源与状态（与列表骨架一致）
            Optional<InpatientOrderListSkeleton> orderOpt = inpatientOrderLookupLoader.findByOrderTypeAndId(
                ReadModelOrderTypeConstants.SERVICE_REQUEST, anchor.getRequestId());
            if (orderOpt.isPresent()) {
                InpatientOrderListSkeleton order = orderOpt.get();
                if (doctorSource.equals(order.getGenerateSourceEnum())
                    && this.isAllowedParentStatus(order.getStatusEnum())
                    && !this.isExcludedSupplementParentServiceCategory(order.getCategoryEnum())) {
                    return;
                }
            }
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_PRC_301,
            "关联医嘱无效，请刷新医嘱列表后重试");
    }

    /**
     * 诊疗父医嘱是否为转科/出院/护理类（不可作为补费锚点，与 IF-IP-PRC-03 列表剔除同源）。
     *
     * @param categoryEnum 诊疗类目 code
     * @return 须排除为 true
     */
    private boolean isExcludedSupplementParentServiceCategory(String categoryEnum) {
        String category = StringUtils.trimToNull(categoryEnum);
        if (category == null) {
            return false;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_NURSING.equals(category)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(category)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(category)) {
            return true;
        }
        return false;
    }

    /**
     * 父医嘱是否允许补费：已校对或已停嘱（BR-IP-PRC-014）。
     *
     * @param statusEnum 请求状态 code
     * @return 允许为 true
     */
    private boolean isAllowedParentStatus(String statusEnum) {
        if (RequestStatus.VERIFIED.getCode().equals(statusEnum)) {
            return true;
        }
        if (RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 将请求就诊列表收窄为当前护士管理病区内的可见就诊（BR-IP-NUR-001 同口径）。
     *
     * @param requestedEncounterIds 左侧多选 encounter
     * @return 可见 encounterId 列表（顺序稳定）
     * @throws BusinessException EX-IP-PRC-102 无可见就诊
     */
    private List<Long> resolveVisibleEncounterIds(List<Long> requestedEncounterIds) {
        return this.resolveVisibleEncounterIds(
            requestedEncounterIds, InpatientNurseStationConstants.EX_IP_PRC_102);
    }

    /**
     * 将请求就诊列表收窄为当前护士管理病区内的可见就诊（BR-IP-NUR-001 同口径）。
     *
     * @param requestedEncounterIds 左侧多选 encounter
     * @param visibilityErrorCode   不可见时业务码
     * @return 可见 encounterId 列表（顺序稳定）
     * @throws BusinessException 无可见就诊
     */
    private List<Long> resolveVisibleEncounterIds(List<Long> requestedEncounterIds, String visibilityErrorCode) {
        List<Long> distinct = this.distinctEncounterIds(requestedEncounterIds);
        if (distinct.isEmpty()) {
            throw new BusinessException(
                visibilityErrorCode,
                "当前患者不可用，请刷新左侧列表");
        }
        List<Long> wardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        // 住院上下文：护士站队列 + 管理病区 + 请求 encounter 交集
        var query = nurseStationInpatientBillingMapper.toInpatientEncounterPageQuery(distinct, wardIds);
        List<InpatientEncounterContextQueryModel> visible =
            EncounterContextReader.listInpatientEncounterContexts(query);
        List<Long> visibleIds = new ArrayList<>();
        Set<Long> requestedSet = new HashSet<>(distinct);
        // 保持请求顺序，仅保留上下文返回的 encounter
        for (InpatientEncounterContextQueryModel row : visible) {
            if (row != null && row.getEncounterId() != null && requestedSet.contains(row.getEncounterId())) {
                visibleIds.add(row.getEncounterId());
            }
        }
        if (visibleIds.isEmpty()) {
            throw new BusinessException(
                visibilityErrorCode,
                "当前患者不可用，请刷新左侧列表");
        }
        return visibleIds;
    }

    /**
     * 单就诊卡片门控：可见且 patientId 与读模型一致。
     *
     * @param encounterIds 目标就诊（通常单元素）
     * @param patientId    左侧卡片患者 id
     * @return 住院信息快照（含 organizationId 供 Charge）
     */
    private InpatientEncounterContextQueryModel requireVisibleSnapshot(List<Long> encounterIds, Long patientId) {
        return this.requireVisibleSnapshot(
            encounterIds, patientId, InpatientNurseStationConstants.EX_IP_PRC_102);
    }

    /**
     * 单就诊卡片门控：可见且 patientId 与读模型一致。
     *
     * @param encounterIds        目标就诊（通常单元素）
     * @param patientId           左侧卡片患者 id
     * @param visibilityErrorCode 不可见时业务码
     * @return 住院信息快照（含 organizationId 供 Charge）
     */
    private InpatientEncounterContextQueryModel requireVisibleSnapshot(
        List<Long> encounterIds,
        Long patientId,
        String visibilityErrorCode) {
        this.assertEncountersVisible(encounterIds, visibilityErrorCode);
        var query = nurseStationInpatientBillingMapper.toInpatientEncounterPageQuery(
            encounterIds, managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty());
        List<InpatientEncounterContextQueryModel> rows =
            EncounterContextReader.listInpatientEncounterContexts(query);
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && patientId.equals(row.getPatientId())) {
                return row;
            }
        }
        throw new BusinessException(
            visibilityErrorCode,
            "当前患者不可用，请刷新左侧列表");
    }

    /**
     * 断言就诊处于可变更费用态（出院结算轨 Gate，仅写路径）。
     *
     * @param snapshot 已可见就诊读模型
     * @throws BusinessException EX-IP-PRC-406
     */
    private void assertEncounterAllowsChargeWrite(InpatientEncounterContextQueryModel snapshot) {
        if (snapshot == null) {
            return;
        }
        inpatientPendingSettlementClinicalGate.assertEncounterAllowsOrderClinicalMutation(
            snapshot.getStatusEnum(),
            InpatientNurseStationConstants.EX_IP_PRC_406,
            InpatientNurseStationConstants.MSG_ENCOUNTER_PENDING_SETTLEMENT_ORDER_FORBIDDEN);
    }

    /**
     * 可见性 + 可变更费用态（划价/取消划价写路径）。
     *
     * @param encounterIds 待校验就诊列表
     * @throws BusinessException EX-IP-PRC-102/406
     */
    private void assertEncountersVisibleForChargeWrite(List<Long> encounterIds) {
        List<InpatientEncounterContextQueryModel> rows =
            this.loadMatchedVisibleEncounters(encounterIds, InpatientNurseStationConstants.EX_IP_PRC_102);
        inpatientPendingSettlementClinicalGate.assertEncountersAllowOrderClinicalMutation(
            rows,
            InpatientNurseStationConstants.EX_IP_PRC_406,
            InpatientNurseStationConstants.MSG_ENCOUNTER_PENDING_SETTLEMENT_ORDER_FORBIDDEN);
    }

    /**
     * 断言请求列表中的每个 encounter 均在可见集合内（防伪造 encounterId）。
     *
     * @param encounterIds 待校验就诊列表
     */
    private void assertEncountersVisible(List<Long> encounterIds) {
        this.assertEncountersVisible(encounterIds, InpatientNurseStationConstants.EX_IP_PRC_102);
    }

    /**
     * 断言请求列表中的每个 encounter 均在可见集合内（防伪造 encounterId）。
     *
     * @param encounterIds        待校验就诊列表
     * @param visibilityErrorCode 不可见时业务码
     */
    private void assertEncountersVisible(List<Long> encounterIds, String visibilityErrorCode) {
        this.loadMatchedVisibleEncounters(encounterIds, visibilityErrorCode);
    }

    /**
     * 加载护士站可见就诊行（不含出院结算轨写 Gate）。
     *
     * @param encounterIds        待校验就诊列表
     * @param visibilityErrorCode 不可见时业务码
     * @return 与请求交集的可见就诊读模型
     * @throws BusinessException 无可见就诊
     */
    private List<InpatientEncounterContextQueryModel> loadMatchedVisibleEncounters(
        List<Long> encounterIds,
        String visibilityErrorCode
    ) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                visibilityErrorCode,
                "当前患者不可用，请刷新左侧列表");
        }
        List<Long> distinct = this.distinctEncounterIds(encounterIds);
        List<Long> wardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        var query = nurseStationInpatientBillingMapper.toInpatientEncounterPageQuery(distinct, wardIds);
        List<InpatientEncounterContextQueryModel> visibleRows =
            EncounterContextReader.listInpatientEncounterContexts(query);
        Set<Long> requestedSet = new HashSet<>(distinct);
        List<InpatientEncounterContextQueryModel> matchedVisibleRows = new ArrayList<>();
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && row.getEncounterId() != null && requestedSet.contains(row.getEncounterId())) {
                matchedVisibleRows.add(row);
            }
        }
        if (matchedVisibleRows.size() != requestedSet.size()) {
            throw new BusinessException(
                visibilityErrorCode,
                "当前患者不可用，请刷新左侧列表");
        }
        return matchedVisibleRows;
    }

    /**
     * 就诊 id 去重并保持请求顺序。
     *
     * @param encounterIds 原始列表
     * @return 去重后的就诊 id
     */
    private List<Long> distinctEncounterIds(List<Long> encounterIds) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (encounterIds != null) {
            for (Long id : encounterIds) {
                if (id != null) {
                    set.add(id);
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 关键字过滤后按 encounter 重新分组父医嘱行。
     *
     * @param rows 扁平父医嘱行
     * @return 分组结果
     */
    private List<NurseStationPricingPatientGroupDto> regroupSupplementRows(
        List<NurseStationSupplementParentOrderRowDto> rows) {
        // 关键字过滤后扁平列表 → 按 encounter 重新分组（IF-03 响应结构）
        Map<Long, List<NurseStationSupplementParentOrderRowDto>> byEncounter = new HashMap<>();
        for (NurseStationSupplementParentOrderRowDto row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            byEncounter.computeIfAbsent(row.getEncounterId(), k -> new ArrayList<>()).add(row);
        }
        List<NurseStationPricingPatientGroupDto> groups = new ArrayList<>();
        for (Map.Entry<Long, List<NurseStationSupplementParentOrderRowDto>> entry : byEncounter.entrySet()) {
            NurseStationPricingPatientGroupDto group = new NurseStationPricingPatientGroupDto();
            group.setEncounterId(entry.getKey());
            group.setOrders(entry.getValue());
            groups.add(group);
        }
        return groups;
    }

    /**
     * 护士划价耗材：FIFO 定批后按实发批号重算入账价，避免 ChargeItem.definitionId 与发放 lot_number 串批。
     *
     * @param row              写行（就地回写 lotNumber / chargeItemDefinitionId / unitPrice）
     * @param primaryDispense  主发放读模型；可空则跳过
     * @param encounterIds     本批就诊主键（供 WriteAssembler 解析开立机构）
     */
    private void repriceDeviceWriteRowFromFinalizedDispenseLot(
        NurseStationPricingWriteItemDto row,
        DeviceDispenseListModel primaryDispense,
        List<Long> encounterIds) {
        if (row == null || primaryDispense == null) {
            return;
        }
        // 无实发批号则保持预划价，避免空批号触发跨批「择优」LOT_NUMBER_PRICE
        if (StringUtils.isEmpty(primaryDispense.getLotNumber())) {
            return;
        }
        // Pre：写行批号与 FIFO 主发放对齐后再走权威价重算
        row.setLotNumber(primaryDispense.getLotNumber());
        nurseStationInpatientBillingWriteAssembler.reapplyAuthoritativeWritePricing(
            List.of(row),
            encounterIds,
            managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty());
    }

    /**
     * 护士划价耗材写路径 Gate：finalize 后主发放行不得仍为草稿（与直接划价/医嘱补费同口径）。
     *
     * @param deviceReqId                   耗材请求主键
     * @param operatorId                    操作人
     * @param requesterId                   发放人回落
     * @param inventoryDecreaseAccumulator  自动扣库累计
     */
    private void assertNursePricingDeviceDispenseFinalized(
        Long deviceReqId,
        Long operatorId,
        Long requesterId,
        Map<Long, BigDecimal> inventoryDecreaseAccumulator) {
        DeviceDispenseListModel primary =
            deviceDispenseQueryService.resolvePrimaryDispenseForDeviceRequest(deviceReqId);
        if (primary != null && !DispenseStatus.DRAFT.getCode().equals(primary.getStatusEnum())) {
            return;
        }
        deviceDispenseNurseWorkflowOrchestrator.finalizeInpatientTemporaryPathDispense(
            deviceReqId, operatorId, requesterId, inventoryDecreaseAccumulator);
        DeviceDispenseListModel after =
            deviceDispenseQueryService.resolvePrimaryDispenseForDeviceRequest(deviceReqId);
        if (after == null || DispenseStatus.DRAFT.getCode().equals(after.getStatusEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_205,
                "耗材发放失败，请稍后重试");
        }
    }

    /**
     * IF-IP-ARG 写路径：新增配置 Gate 校验。
     *
     * @param encounterId        就诊
     * @param patientId          患者
     * @param ruleName           规则名
     * @param activityId         诊疗活动
     * @param chargeDefinitionId 定价
     * @param quantityValue      数量（可空）
     */
    private void validateAutoGenWriteRequest(
        Long encounterId,
        Long patientId,
        String ruleName,
        Long activityId,
        Long chargeDefinitionId,
        BigDecimal quantityValue) {
        if (encounterId == null || patientId == null || StringUtils.isEmpty(ruleName)
            || activityId == null || chargeDefinitionId == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ARG_302,
                "配置参数无效，请刷新后重试");
        }
        if (quantityValue != null && quantityValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ARG_302,
                "数量必须大于零");
        }
    }

    /**
     * 自动计费选品须为可日结诊疗：有效 ActivityDefinition，排除护理/转科/出院/嘱托（BR-IP-ARG-003）。
     *
     * @param activityId 活动定义主键
     * @return 诊疗类目 code（写入 ensure 长期 SR 的 category_enum）
     */
    private String assertAutoRollConfigurableActivity(Long activityId) {
        try {
            ActivityDefinitionDetailQueryModel catalog = activityDefinitionQueryService.getServiceCatalog(activityId);
            if (catalog == null || catalog.getBaseInfo() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "诊疗项目无效，请重新选品");
            }
            String categoryCode = StringUtils.trimToNull(catalog.getBaseInfo().getCategoryCode());
            // Gate：类目缺失视为目录脏数据，拒绝写入配置
            if (categoryCode == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "诊疗项目无效，请重新选品");
            }
            // Gate：护理走医嘱校对滚费分支；转科/出院/嘱托不计费，均禁止入配置表
            if (this.isExcludedFromAutoRollConfigCategory(categoryCode)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "护理、转科、出院、嘱托等不计费项目不能配置自动计费，请重新选品");
            }
            return categoryCode;
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ARG_302,
                    "诊疗项目无效，请重新选品");
            }
            throw ex;
        }
    }

    /**
     * 是否禁止写入自动计费配置的诊疗类目（护理防双计；转科/出院/嘱托不计费）。
     *
     * @param categoryEnum 诊疗类目 code
     * @return 须排除为 true
     */
    private boolean isExcludedFromAutoRollConfigCategory(String categoryEnum) {
        String category = StringUtils.trimToNull(categoryEnum);
        if (category == null) {
            return false;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_NURSING.equals(category)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(category)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(category)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_ENTRUST.equals(category)) {
            return true;
        }
        return false;
    }

    /**
     * 收费项定价须存在且未废弃（BR-IP-ARG-004）。
     *
     * @param chargeDefinitionId 定价主键
     */
    private void assertChargeDefinitionValid(Long chargeDefinitionId) {
        Map<Long, ChargeItemDefinition> definitionMap =
            chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(Collections.singleton(chargeDefinitionId));
        ChargeItemDefinition definition = definitionMap.get(chargeDefinitionId);
        if (definition == null || Boolean.TRUE.equals(definition.getIsDeleted())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ARG_302,
                "收费项定价无效，请重新选品");
        }
        if (StringUtils.isNotEmpty(definition.getStatusEnum())
            && PublicationStatus.DISCARDED.getCode().equals(definition.getStatusEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ARG_302,
                "收费项定价无效，请重新选品");
        }
    }

    /**
     * 域 CHARGE_ITEM_AUTO_GEN_* 异常码 → EX-IP-ARG-* 应用码。
     *
     * @param ex 捕获的业务异常
     * @return 翻译后异常（可直接 throw）
     */
    private BusinessException translateAutoGenBusinessException(BusinessException ex) {
        String code = ex.getErrorCode();
        if (ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_IDENTITY_CONFLICT.equals(code)) {
            return new BusinessException(InpatientNurseStationConstants.EX_IP_ARG_301, ex.getMessage());
        }
        if (ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_NOT_FOUND.equals(code)) {
            return new BusinessException(InpatientNurseStationConstants.EX_IP_ARG_401, ex.getMessage());
        }
        if (ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_STATUS.equals(code)) {
            return new BusinessException(InpatientNurseStationConstants.EX_IP_ARG_501, ex.getMessage());
        }
        if (ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT.equals(code)) {
            return new BusinessException(InpatientNurseStationConstants.EX_IP_ARG_302, ex.getMessage());
        }
        return ex;
    }

    /**
     * 批量活动定义 id → 实体 Map（展示名装配用）。
     *
     * @param activityIds 活动 id 集合
     * @return id → ActivityDefinition
     */
    private Map<Long, ActivityDefinition> buildActivityDefinitionMap(Set<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ActivityDefinition> activityById = new HashMap<>();
        List<ActivityDefinition> activities = activityDefinitionQueryService.findAllByIds(activityIds);
        for (ActivityDefinition activity : activities) {
            if (activity != null && activity.getId() != null) {
                activityById.put(activity.getId(), activity);
            }
        }
        return activityById;
    }

    /**
     * 挂麻醉申请父项时，将收费命令来源覆盖为麻醉划价。
     *
     * @param chargeCmd 护士划价收费命令
     * @param row       写行（含 parentRequestAnchor）
     */
    private void applyAnesthesiaPricingGenerateSourceIfNeeded(
        CreateChargeItemCommand chargeCmd,
        NurseStationPricingWriteItemDto row) {
        if (chargeCmd == null || row == null) {
            return;
        }
        if (!this.isAnesthesiaApplyParentAnchor(row.getParentRequestAnchor(), row.getEncounterId())) {
            return;
        }
        List<CreateChargeItemCommand.Item> chargeItems = chargeCmd.getItems();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return;
        }
        for (CreateChargeItemCommand.Item item : chargeItems) {
            if (item != null) {
                item.setGenerateSourceEnum(GenerateSource.ANESTHESIA_PRICING.getCode());
            }
        }
    }

    /**
     * 补费父锚点是否为麻醉申请（方号 AA）。
     *
     * @param anchor      父医嘱锚点
     * @param encounterId 就诊主键
     * @return true=麻醉申请父项
     */
    private boolean isAnesthesiaApplyParentAnchor(
        NurseStationPricingParentRequestAnchorDto anchor,
        Long encounterId) {
        if (anchor == null || anchor.getRequestId() == null || encounterId == null) {
            return false;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(anchor.getOrderType())) {
            List<MedicationRequestListQueryModel> meds =
                medicationRequestQueryService.listMedicationRequestsByEncounter(encounterId);
            for (MedicationRequestListQueryModel med : meds) {
                if (med != null && anchor.getRequestId().equals(med.getId())) {
                    return ServiceApplyFormOrchestrationSupport.isAnesthesiaApplicationPrescriptionNo(
                        med.getPrescriptionNo());
                }
            }
            return false;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(anchor.getOrderType())) {
            Optional<InpatientOrderListSkeleton> orderOpt = inpatientOrderLookupLoader.findByOrderTypeAndId(
                ReadModelOrderTypeConstants.SERVICE_REQUEST, anchor.getRequestId());
            if (orderOpt.isEmpty()) {
                return false;
            }
            InpatientOrderListSkeleton order = orderOpt.get();
            if (order == null) {
                return false;
            }
            return ServiceApplyFormOrchestrationSupport.isAnesthesiaApplicationPrescriptionNo(
                order.getPrescriptionNo());
        }
        return false;
    }

    /**
     * 取消划价费用 Gate：锚点下护士站划价族（nurse-pricing / anesthesia-pricing）费用须全部待结算（BR-IP-PRC-023）。
     *
     * @param linkedCharges     锚点关联费用
     * @param cancelAnchorIds   待取消请求 id 集合
     * @param nursePricingSource generate_source=nurse-pricing code（兼容入参；实际按划价族判定）
     */
    private void assertCancelPricingChargeGate(
        List<ChargeItem> linkedCharges,
        Set<Long> cancelAnchorIds,
        String nursePricingSource) {
        if (linkedCharges == null || linkedCharges.isEmpty()) {
            return;
        }
        List<ChargeItem> gatedCharges =
            chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(linkedCharges);
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        // 仅校验待取消锚点下的护士站划价族费用行（含麻醉划价）
        for (ChargeItem charge : gatedCharges) {
            if (charge == null || charge.getServiceId() == null) {
                continue;
            }
            if (!cancelAnchorIds.contains(charge.getServiceId())) {
                continue;
            }
            if (!GenerateSource.isNurseStationPricingFamilyCode(charge.getGenerateSourceEnum())) {
                continue;
            }
            String status = charge.getStatusEnum();
            // 已结算/退费链路整批拒绝（BR-IP-PRC-023）
            if (ChargeItemStatus.SETTLED.getCode().equals(status)
                || ChargeItemStatus.REFUNDING.getCode().equals(status)
                || ChargeItemStatus.REFUNDED.getCode().equals(status)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_403,
                    "费用已结算，无法取消划价");
            }
            // 非待结算态同样拒绝，防止半取消
            if (!pendingCode.equals(status)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_403,
                    "费用已结算，无法取消划价");
            }
        }
    }

    /**
     * 取消划价冲销 Gate：每个锚点须仍有未冲销的正向待结算护士站划价族行（BR-IP-PRC-024）。
     *
     * <p>{@code refund_id} 双义：仅 {@link ChargeItem#isPricingOffsetLine()}（数量非正）视为冲销留痕；
     * 反结再生正向行虽有 {@code refund_id} 仍算可冲销源。</p>
     *
     * @param linkedCharges      锚点关联费用
     * @param cancelAnchorIds    待取消请求 id 集合
     * @param nursePricingSource generate_source=nurse-pricing code（兼容入参；实际按划价族判定）
     */
    private void assertCancelPricingHasOffsetableCharges(
        List<ChargeItem> linkedCharges,
        Set<Long> cancelAnchorIds,
        String nursePricingSource) {
        Set<Long> alreadyOffsetSourceIds = new HashSet<>();
        if (linkedCharges != null) {
            for (ChargeItem charge : linkedCharges) {
                // 仅冲销负向留痕计入「已冲销源」；反结再生正向的 refund_id 不计入
                if (charge == null || !charge.isPricingOffsetLine()) {
                    continue;
                }
                if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(charge.getStatusEnum())) {
                    continue;
                }
                if (!GenerateSource.isNurseStationPricingFamilyCode(charge.getGenerateSourceEnum())) {
                    continue;
                }
                alreadyOffsetSourceIds.add(charge.getRefundId());
            }
        }
        Set<Long> anchorsWithOffsetable = new HashSet<>();
        if (linkedCharges != null) {
            for (ChargeItem charge : linkedCharges) {
                if (charge == null || charge.getServiceId() == null || charge.getId() == null) {
                    continue;
                }
                if (!cancelAnchorIds.contains(charge.getServiceId())) {
                    continue;
                }
                if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(charge.getStatusEnum())) {
                    continue;
                }
                if (!GenerateSource.isNurseStationPricingFamilyCode(charge.getGenerateSourceEnum())) {
                    continue;
                }
                // 冲销负向留痕不可再冲；反结再生正向仍可冲
                if (charge.isPricingOffsetLine()) {
                    continue;
                }
                if (charge.getQuantityValue() == null || charge.getQuantityValue().signum() <= 0) {
                    continue;
                }
                // 已被冲销负向行对冲过的正向源跳过
                if (alreadyOffsetSourceIds.contains(charge.getId())) {
                    continue;
                }
                anchorsWithOffsetable.add(charge.getServiceId());
            }
        }
        for (Long anchorId : cancelAnchorIds) {
            // 无未冲销正向费用则拒绝，避免空跑 Dispense/Procedure
            if (!anchorsWithOffsetable.contains(anchorId)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_403,
                    "费用已冲销或无可取消划价行，请刷新列表后重试");
            }
        }
    }

    /**
     * 取消划价耗材发放 Gate：仅允许护士划价路径可处理态（EX-IP-PRC-404）。
     *
     * <p>已发（COMPLETED/PART_COMPLETED）与未发（PREPARATION/PREPARED）均可取消；汇总等终态拒绝。</p>
     *
     * @param deviceReqId 耗材请求主键
     */
    private void assertDeviceDispenseRevertAllowed(Long deviceReqId) {
        List<Long> dispenseIds = deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(deviceReqId);
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        List<DeviceDispenseListModel> dispenseRows =
            deviceDispenseQueryService.selectByRequestIdList(List.of(deviceReqId));
        // 逐条发放行校验可回退态（护士划价路径不应出现 SUMMARIZED 等）
        for (DeviceDispenseListModel row : dispenseRows) {
            if (row == null || row.getStatusEnum() == null) {
                continue;
            }
            String status = row.getStatusEnum();
            if (DispenseStatus.PREPARATION.getCode().equals(status)) {
                continue;
            }
            if (DispenseStatus.PREPARED.getCode().equals(status)) {
                continue;
            }
            if (DispenseStatus.COMPLETED.getCode().equals(status)) {
                continue;
            }
            if (DispenseStatus.PART_COMPLETED.getCode().equals(status)) {
                continue;
            }
            // 已汇总/已停发等护士划价不应出现的状态，拒绝整批取消
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_404,
                "发放状态不允许取消，请刷新后重试");
        }
    }

}
