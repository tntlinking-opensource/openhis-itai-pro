/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stocktake.service;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.tracenumber.TraceNumberStocktakeGainLossTraceOrchestrator;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.application.inventory.department.stocktake.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.stocktake.dto.StocktakeDetailLineDto;
import com.openhis.health.application.inventory.department.stocktake.dto.StocktakeLineDto;
import com.openhis.health.application.inventory.department.stocktake.dto.StocktakeListRowDto;
import com.openhis.health.application.inventory.department.stocktake.mapper.InventoryDepartmentStocktakeMapper;
import com.openhis.health.application.inventory.department.stocktake.request.*;
import com.openhis.health.application.inventory.department.stocktake.response.*;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InventoryDepartmentConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.application.inventory.support.SupplyRequestPricedWriteOrchestrator;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterStocktakeExtensionIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 科室药房盘点单（{@link SupplyType#DEPT_STOCKTAKE}）应用编排。
 *
 * <p>同意后按差异发放并在盘点科室药房（{@link LocationForm#DEPARTMENT_PHARMACY}）做盈亏加减；
 * 盘盈建库存行复用 {@link InventoryWarehouseInboundReceiptMapper}。
 * 详情槽价仅锚 {@code SupplyRequest} 落库行价（**BR-PRICE-SRC-001**）；选品页采购价读当前 ACTIVE 目录价。</p>
 *
 * <p>HTTP 前缀 {@code inventoryDepartmentStocktake}；接入 {@code AppServiceControllerRegistrar}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDepartmentStocktakeAppService {

    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestPricedWriteOrchestrator supplyRequestPricedWriteOrchestrator;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final SupplyDeliveryDomainService supplyDeliveryDomainService;
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryDepartmentStocktakeMapper stocktakeMapper;
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** IF-TRC-14 盘点/损益剩余码差分写追溯。 */
    private final TraceNumberStocktakeGainLossTraceOrchestrator traceNumberStocktakeGainLossTraceOrchestrator;

    /**
     * 加载页主数据（库位、人员、状态等下拉）。
     *
     * @param request 可为空；当前无额外筛选字段
     * @return 表单初始化选项集合
     */
    public LoadStocktakeFormInitResponse loadStocktakeFormInit(LoadStocktakeFormInitRequest request) {
        // Gate：会话机构上下文须存在，否则无法按管辖收窄库位与人员
        // Pre：拉取启用库位/人员并经管辖 Gate 收窄
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEP_ST_101, "机构上下文缺失");
        try {
            List<Location> departmentPharmacies = locationQueryService.listAllActiveDepartmentPharmacies();
            List<Location> gatedDepartmentPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(departmentPharmacies);
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
            // Asm：下拉选项写入初始化响应
            return stocktakeMapper.toLoadStocktakeFormInitResponse(
                stocktakeMapper.toOptionDtoListFromLocations(gatedDepartmentPharmacies),
                stocktakeMapper.toOptionDtoListInboundItemTableTypes(),
                stocktakeMapper.toOptionDtoListSupplyStatuses(),
                stocktakeMapper.toOptionDtoListFromPractitioners(pharmacists),
                stocktakeMapper.toOptionDtoListFromPractitioners(warehouseKeepers));
        } catch (RuntimeException ex) {
            log.warn("loadStocktakeFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_ST_101, "盘点页主数据加载失败");
        }
    }

    /**
     * 分页查询单据列表。
     *
     * @param request 含库位筛选、状态与分页窗口
     * @return 分页列表结果
     */
    public PageStocktakesResponse pageStocktakes(PageStocktakesRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        // Pre：组装供应单据列表查询条件
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "请求不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_ST_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_ST_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_STOCKTAKE.getCode());
        if (request.getPurposeLocationId() != null) {
            if (Long.valueOf(InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID)
                .equals(request.getPurposeLocationId())) {
                if (allowedDepartmentPharmacies.isEmpty()) {
                    // 无可见科室药房时用占位 id，避免误查全表
                    listQuery.setPurposeLocationIds(
                        List.of(InventoryDepartmentConstants.LIST_FILTER_NO_VISIBLE_DEPARTMENT_PHARMACY_PLACEHOLDER_ID));
                } else {
                    listQuery.setPurposeLocationIds(new ArrayList<>(allowedDepartmentPharmacies));
                }
            } else {
                // 指定单一科室药房时须通过管辖 Gate，防止越权查单
                this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);
                listQuery.setPurposeLocationId(request.getPurposeLocationId());
            }
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                // 非法状态 code 拒绝查询，与 AS 参数校验一致
                throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        LocalDateTime rangeStart = request.getApplyTimeFrom();
        if (rangeStart == null) {
            rangeStart = request.getStartDate();
        }
        listQuery.setStartDate(rangeStart);
        LocalDateTime rangeEnd = request.getApplyTimeTo();
        if (rangeEnd == null) {
            rangeEnd = request.getEndDate();
        }
        listQuery.setEndDate(rangeEnd);
        try {
            // Cmd：按 busNo 去重分页查询供应请求行
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<StocktakeListRowDto> records = this.stocktakeMapper.toStocktakeListAggregated(busNos, lines);
            this.enrichStocktakeListDisplayNames(records, lines);
            this.enrichStocktakeListPurposeLocationNames(records);
            this.enrichStocktakeListStatusEnumText(records);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合行映射为分页响应
            return this.stocktakeMapper.toPageStocktakesAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageStocktakes failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_ST_102, "盘点单列表查询失败");
        }
    }

    /**
     * 按业务单号查询单据详情。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     */
    public GetStocktakeDetailResponse getStocktakeDetail(GetStocktakeDetailRequest request) {
        // Gate：busNo 不可为空
        // Pre：按单号回读供应请求行并批量预取产品展示信息
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_103, "盘点单不存在");
        }
        this.assertDeptStocktake(lines);
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> products;
        try {
            products = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("batchGetProductByIds degraded for stocktake detail", ex);
            products = new HashMap<>();
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
        Map<Long, ProductDefinitionCatalogEditorHintsDto> hints = new HashMap<>();
        for (SupplyRequestModel line : lines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            ProductDefinitionCatalogEditorHintsDto hint =
                ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
            if (hint != null) {
                hints.put(line.getId(), hint);
            }
        }
        GetStocktakeDetailResponse res = stocktakeMapper.toGetStocktakeDetailResponse(lines, hints);
        SupplyRequestModel head = lines.get(0);
        if (head.getPurposeLocationId() != null) {
            Optional<Location> locOpt = locationQueryService.findOptionalById(head.getPurposeLocationId());
            if (locOpt.isPresent() && StringUtils.isNotEmpty(locOpt.get().getName())) {
                res.setPurposeLocationName(locOpt.get().getName().trim());
            }
        }
        this.enrichStocktakeDetailLines(res.getLines(), lines, products);
        this.enrichStocktakeDetailLinesCatalogPricing(res.getLines());
        return res;
    }

    /**
     * 新建单据（草稿/待提交）。
     *
     * @param request 含库位、明细行等业务字段
     * @return 新建业务单号
     */
    public CreateStocktakeResponse createStocktake(CreateStocktakeRequest request) {
        // Gate：请求体、库位与明细不可为空
        // Pre：解析操作人与制单人（医务人员档案）
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "盘点明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_ST_101, "机构上下文缺失");
        this.assertStocktakePurposeLocationGate(request.getPurposeLocationId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "经手人不能为空");
        }
        this.assertDeptStocktakeLinesFields(request.getLines());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_ST_101, "用户未登录");
        Long applicantId = this.requireStocktakeApplicantPractitionerId(
            ErrorCodeConstants.DEP_ST_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            String busNo = supplyRequestPricedWriteOrchestrator.createStocktake(
                stocktakeMapper.toCreateStocktakeCommand(request, operatorId, applicantId, null));
            return stocktakeMapper.toCreateStocktakeResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "盘点参数不合法：" + ex.getMessage());
        } catch (BusinessException ex) {
            throw this.mapStocktakeLocationException(ex);
        }
    }

    /**
     * 更新单据（全量替换明细，仅可编辑态允许）。
     *
     * @param request 含 busNo、库位与明细
     * @return 业务单号
     */
    public UpdateStocktakeResponse updateStocktake(UpdateStocktakeRequest request) {
        // Gate：busNo 与明细、库位不可为空
        // Pre：回读现有单并校验可编辑态
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "盘点明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_ST_101, "机构上下文缺失");
        this.assertStocktakePurposeLocationGate(request.getPurposeLocationId());
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_103, "盘点单不存在");
        }
        this.assertDeptStocktake(existing);
        SupplyRequestModel head = existing.get(0);
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_004, "当前状态不允许编辑盘点单");
        }
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "经手人不能为空");
        }
        this.assertDeptStocktakeLinesFields(request.getLines());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_ST_101, "用户未登录");
        Long applicantId = this.requireStocktakeApplicantPractitionerId(
            ErrorCodeConstants.DEP_ST_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            String busNo = supplyRequestPricedWriteOrchestrator.createStocktake(
                stocktakeMapper.toCreateStocktakeCommand(request, operatorId, applicantId));
            return stocktakeMapper.toUpdateStocktakeResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "盘点参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_105, "盘点单已被并发修改，请刷新后重试", ex);
        } catch (BusinessException ex) {
            throw this.mapStocktakeLocationException(ex);
        }
    }

    /**
     * 提交单据审批。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public SubmitStocktakeApprovalResponse submitStocktakeApproval(SubmitStocktakeApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireStocktakeApplicantPractitionerId(
            ErrorCodeConstants.DEP_ST_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批，头行进入待审态
            supplyRequestDomainService.submitApproval(
                stocktakeMapper.toSubmitApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_ST_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_ST_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        // Asm：回读头状态并映射成功体
        return stocktakeMapper.toSubmitStocktakeApprovalResponse(st);
    }

    /**
     * 撤回单据审批（回到可编辑态）。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public WithdrawStocktakeApprovalResponse withdrawStocktakeApproval(WithdrawStocktakeApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批，单据回到可编辑态
            supplyRequestDomainService.withdrawApproval(
                stocktakeMapper.toWithdrawApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_ST_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_ST_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        // Asm：回读头状态并映射成功体
        return stocktakeMapper.toWithdrawStocktakeApprovalResponse(st);
    }

    /**
     * 科室药房盘点单同意编排：领域落已同意态 → 盘点发放 → 药房库存盈亏入账（盘盈可新建库存行）。
     *
     * @param request 须含 busNo
     * @return 同意成功标志
     */
    public AgreeStocktakeResponse agreeStocktake(AgreeStocktakeRequest request) {
        // Gate：busNo 与操作人不可为空
        // Pre：回读单据并校验未重复同意
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_ST_101, "用户未登录");
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_103, "盘点单不存在");
        }
        this.assertDeptStocktake(beforeLines);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "盘点单已同意，禁止重复同意");
        }
        try {
            supplyRequestDomainService.agreeRequest(stocktakeMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_105, "盘点单已被并发修改，请刷新后重试", ex);
        }
        this.supplyRequestDomainService.flushPendingChanges();
        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertDeptStocktake(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "同意后回读状态异常，中止盘点编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "盘点明细缺少持久化主键，无法完成编排");
            }
        }
        List<Long> requestIdsForDeliveryGate = new ArrayList<>();
        for (SupplyRequestModel row : agreedLines) {
            if (row != null && row.getId() != null) {
                requestIdsForDeliveryGate.add(row.getId());
            }
        }
        Set<Long> duplicateCompletedRequestIds =
            supplyDeliveryQueryService.findRequestIdsHavingCompletedDelivery(requestIdsForDeliveryGate);
        if (!duplicateCompletedRequestIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "供应请求行已存在完成态发放，禁止重复同意盘点");
        }
        try {
            supplyDeliveryDomainService.createStocktakingDelivery(
                stocktakeMapper.toCreateStocktakingDeliveryCommand(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "盘点发放落库失败：" + ex.getMessage());
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey = new HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                productByKey = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed during stocktake inventory assemble", ex);
            }
        }
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            BigDecimal profitLossQty = line.getItemQuantity();
            // 零盈亏行跳过库存与发放。
            if (profitLossQty == null || profitLossQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Long purposeLocationId = line.getPurposeLocationId();
            String resolvedLot = this.resolveLotNumberForStocktakeInventory(line.getLotNumber());
            InventoryMatchKey matchKey = new InventoryMatchKey();
            matchKey.setLotNumber(resolvedLot);
            matchKey.setLocationId(purposeLocationId);
            matchKey.setItemTable(line.getItemTable());
            matchKey.setItemId(line.getItemId());
            Long inventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(matchKey).orElse(null);
            int sign = profitLossQty.compareTo(BigDecimal.ZERO);
            if (sign > 0) {
                try {
                    if (inventoryId != null) {
                        IncreaseQuantityCommand incCmd =
                            stocktakeMapper.toIncreaseQuantityCommand(inventoryId, profitLossQty, operatorId);
                        inventoryItemDomainService.increaseQuantity(incCmd);
                    } else {
                        String productName = this.resolveProductName(line, productByKey);
                        String key = ProductDefinitionKeyUtil.buildCompositeKey(
                            line.getItemTable(), line.getItemId());
                        ProductDefinitionDisplayDto product = productByKey.get(key);
                        String splitUnitCode = this.resolveSplitUnitCode(line, product);
                        CreateInventoryItemCommand createCmd = inboundReceiptMapper.toCreateInventoryItemCommand(
                            line,
                            purposeLocationId,
                            productName,
                            resolvedLot,
                            profitLossQty,
                            splitUnitCode,
                            operatorId,
                            product,
                            line.getSupplierId());
                        inventoryItemDomainService.createInventoryItem(createCmd);
                    }
                } catch (BusinessException ex) {
                    throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "盘盈库存增加失败：" + ex.getMessage());
                }
            } else {
                BigDecimal absQty = profitLossQty.abs();
                if (inventoryId == null) {
                    throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "盘点科室药房未找到匹配库存行，无法盘亏扣减");
                }
                try {
                    DecreaseQuantityCommand decCmd =
                        stocktakeMapper.toDecreaseQuantityCommand(inventoryId, absQty, true, operatorId);
                    inventoryItemDomainService.decreaseQuantity(decCmd);
                } catch (BusinessException ex) {
                    throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "盘亏库存扣减失败：" + ex.getMessage());
                }
            }
        }
        // Cmd：IF-TRC-14 剩余码与本库账差分写追溯主子表（库存入账成功后、同事务）
        traceNumberStocktakeGainLossTraceOrchestrator.recordFromAgreedLines(
            agreedLines, operatorId, productByKey, ErrorCodeConstants.DEP_ST_104);
        tryUploadMedicareInventoryAfterStocktakeAgree(request, agreedLines);
        return stocktakeMapper.toAgreeStocktakeResponse(
            request.getBusNo().trim(), SupplyStatus.AGREE.getCode());
    }

    /**
     * 科室盘点 agree 后尝试医保库存上传；无明细行时跳过。
     *
     * @param request     agree 请求
     * @param agreedLines 已 agree 明细
     */
    private void tryUploadMedicareInventoryAfterStocktakeAgree(
        AgreeStocktakeRequest request,
        List<SupplyRequestModel> agreedLines
    ) {
        List<Long> lineIds = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line != null && line.getId() != null) {
                lineIds.add(line.getId());
            }
        }
        if (lineIds.isEmpty()) {
            return;
        }
        inventoryUploadExtension.runUploadIfEnabled(() -> {
            InventoryUploadAfterStocktakeExtensionIn command = new InventoryUploadAfterStocktakeExtensionIn()
                .setStocktakeBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setSupplyTypeCode(SupplyType.DEPT_STOCKTAKE.getCode())
                .setContractBusNo(request.getContractBusNo());
            inventoryUploadExtension.uploadAfterStocktakeAgree(command);
        });
    }

    /**
     * 驳回单据。
     *
     * @param request 须含 busNo 与驳回原因
     * @return 驳回成功标志
     */
    public RejectStocktakeResponse rejectStocktake(RejectStocktakeRequest request) {
        // Gate：busNo 与驳回原因不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getChangeReason()) && StringUtils.isEmpty(request.getChangeReasonCode())) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "驳回原因不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_ST_101, "用户未登录");
        try {
            // Cmd：领域驳回单据
            supplyRequestDomainService.rejectRequest(stocktakeMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_ST_003);
        }
        // Asm：映射驳回成功标志
        return stocktakeMapper.toRejectStocktakeResponse(true);
    }

    /**
     * 删除单据（仅草稿/可删态）。
     *
     * @param request 须含 busNo
     * @return 删除成功标志
     */
    public RemoveStocktakeResponse removeStocktake(RemoveStocktakeRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_ST_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删草稿单
            supplyRequestDomainService.removeByBusNo(stocktakeMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_ST_005);
        }
        // Asm：映射删除成功标志
        return stocktakeMapper.toRemoveStocktakeResponse(true);
    }

    /**
     * 分页查询选品（采购目录 + 可选库存）。
     *
     * @param request 分页窗口与选品筛选条件
     * @return 产品定义展示行分页
     */
    public PageProductsForStocktakeResponse pageProductsForStocktake(PageProductsForStocktakeRequest request) {
        // Gate：分页窗口与机构上下文
        // Pre：组装产品定义分页查询
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_ST_001, "分页参数不能为空");
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_ST_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForStocktakeSelection(request.getProductTableName());
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getPurposeLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        try {
            Page<InventoryItemModel> page = inventoryItemQueryService.queryInventoryItemsByPage(invQuery, pageable);
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv == null || inv.getItemId() == null || StringUtils.isEmpty(inv.getItemTable())) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductDefinitionId(inv.getItemId());
                lookup.setProductTableName(inv.getItemTable().trim());
                lookups.add(lookup);
            }
            Map<String, ProductDefinitionDisplayDto> products = new HashMap<>();
            if (!lookups.isEmpty()) {
                try {
                    products = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
                } catch (RuntimeException ex) {
                    log.warn("batchGetProductByIds failed for stocktake selection", ex);
                }
            }
            String itemTable = request.getProductTableName().trim();
            List<Long> pageItemIds = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv != null && inv.getItemId() != null) {
                    pageItemIds.add(inv.getItemId());
                }
            }
            Map<Long, Map<String, BigDecimal>> availableQtyByItemIdAndNormLot = new HashMap<>();
            if (!pageItemIds.isEmpty()) {
                List<InventoryCatalogAvailabilityRowModel> availRows =
                    this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                        itemTable, pageItemIds, List.of(request.getPurposeLocationId()));
                for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                    if (ar == null || ar.getItemId() == null || ar.getQuantity() == null) {
                        continue;
                    }
                    if (!Objects.equals(ar.getLocationId(), request.getPurposeLocationId())) {
                        continue;
                    }
                    String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(ar.getLotNumber());
                    availableQtyByItemIdAndNormLot
                        .computeIfAbsent(ar.getItemId(), k -> new HashMap<>())
                        .merge(normLot, ar.getQuantity(), BigDecimal::add);
                }
            }
            ProductDefinitionSelectionTerminologyMaps terminologyMaps =
                this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
            List<ProductDefinitionItemDto> records = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(inv.getItemTable(), inv.getItemId());
                ProductDefinitionDisplayDto pr = products.get(key);
                ProductDefinitionItemDto dto = stocktakeMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
                if (inv.getItemId() != null) {
                    String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(inv.getLotNumber());
                    Map<String, BigDecimal> byLot = availableQtyByItemIdAndNormLot.get(inv.getItemId());
                    BigDecimal avail = BigDecimal.ZERO;
                    if (byLot != null) {
                        BigDecimal v = byLot.get(normLot);
                        if (v != null) {
                            avail = v;
                        }
                    }
                    dto.setAvailableQuantity(avail);
                } else {
                    dto.setAvailableQuantity(BigDecimal.ZERO);
                }
                BigDecimal lotCostUnit = null;
                if (StringUtils.isNotEmpty(inv.getLotNumber())) {
                    lotCostUnit = this.chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                        inv.getItemTable(), inv.getItemId(), inv.getLotNumber().trim());
                }
                dto.setPurchaseUnitPrice(lotCostUnit);
                ProductDefinitionCatalogEditorHintsDto hints =
                    ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
                if (hints != null) {
                    List<ProductDefinitionCatalogUnitBandDto> bands = hints.getUnitBands();
                    dto.setUnitBands(bands);
                    BigDecimal packageAnchor = lotCostUnit;
                    if (packageAnchor == null && StringUtils.isNotEmpty(inv.getItemTable()) && inv.getItemId() != null) {
                        packageAnchor = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                            inv.getItemTable().trim(), inv.getItemId());
                    }
                    ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                        bands, pr, packageAnchor);
                }
                records.add(dto);
            }
            return PagedResultBuilder.build(new PageProductsForStocktakeResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForStocktake failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_ST_102, "盘点选品查询失败");
        }
    }

    /** 列表行回填盘点科室药房展示名。 */
    private void enrichStocktakeListPurposeLocationNames(List<StocktakeListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> purposeIds = new HashSet<>();
        for (StocktakeListRowDto row : rows) {
            if (row != null && row.getPurposeLocationId() != null) {
                purposeIds.add(row.getPurposeLocationId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        // 批量解析科室药房名称，避免列表行 N+1
        for (Long pid : purposeIds) {
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(pid);
            if (locOpt.isEmpty()) {
                continue;
            }
            Location loc = locOpt.get();
            if (StringUtils.isNotEmpty(loc.getName())) {
                nameById.put(pid, loc.getName().trim());
            }
        }
        for (StocktakeListRowDto row : rows) {
            if (row == null || row.getPurposeLocationId() == null) {
                continue;
            }
            row.setPurposeLocationName(nameById.get(row.getPurposeLocationId()));
        }
    }

    /** 列表行回填供应状态中文展示。 */
    private void enrichStocktakeListStatusEnumText(List<StocktakeListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (StocktakeListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getStatusEnum())) {
                continue;
            }
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
            if (st != null) {
                // 枚举展示名供前端直接渲染
                row.setStatusEnumText(st.getDisplay());
            }
        }
    }

    /** 列表行回填申请人、审批人姓名（按 busNo 取最新申请行作表头）。 */
    private void enrichStocktakeListDisplayNames(List<StocktakeListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty() || detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, Long> busNoToApproverId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = new LinkedHashMap<>();
        // 同 busNo 取 applyTime 最新行作表头，对齐列表聚合口径
        for (SupplyRequestModel line : detailLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                continue;
            }
            String bn = line.getBusNo().trim();
            SupplyRequestModel existing = headByBusNo.get(bn);
            if (existing == null) {
                headByBusNo.put(bn, line);
            } else {
                LocalDateTime existingApply = existing.getApplyTime();
                LocalDateTime lineApply = line.getApplyTime();
                if (lineApply != null) {
                    if (existingApply == null || lineApply.isAfter(existingApply)) {
                        headByBusNo.put(bn, line);
                    }
                }
            }
        }
        for (Map.Entry<String, SupplyRequestModel> e : headByBusNo.entrySet()) {
            SupplyRequestModel head = e.getValue();
            if (head == null) {
                continue;
            }
            String bn = e.getKey();
            if (head.getApplicantId() != null) {
                busNoToApplicantId.put(bn, head.getApplicantId());
            }
            if (head.getApproverId() != null) {
                busNoToApproverId.put(bn, head.getApproverId());
            }
        }
        Set<Long> practitionerIds = new HashSet<>();
        practitionerIds.addAll(busNoToApplicantId.values());
        practitionerIds.addAll(busNoToApproverId.values());
        Map<Long, String> practitionerNameById = new HashMap<>();
        for (Long pid : practitionerIds) {
            if (pid == null) {
                continue;
            }
            Practitioner pr = this.practitionerQueryService.findById(pid);
            if (pr != null && StringUtils.isNotEmpty(pr.getName())) {
                practitionerNameById.put(pid, pr.getName());
            }
        }
        for (StocktakeListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            Long aid = busNoToApplicantId.get(bn);
            if (aid != null) {
                String name = practitionerNameById.get(aid);
                if (name != null) {
                    row.setApplicantName(name);
                }
            }
            Long approverId = busNoToApproverId.get(bn);
            if (approverId != null) {
                String name = practitionerNameById.get(approverId);
                if (name != null) {
                    row.setApproverName(name);
                }
            }
        }
    }

    /** 详情明细回填产品名、规格、账面量（同批号同库位汇总）。 */
    private void enrichStocktakeDetailLines(
        List<StocktakeDetailLineDto> detailLines,
        List<SupplyRequestModel> supplyLines,
        Map<String, ProductDefinitionDisplayDto> products) {
        if (detailLines == null || detailLines.isEmpty() || supplyLines == null) {
            return;
        }
        Map<Long, SupplyRequestModel> lineById = new HashMap<>();
        for (SupplyRequestModel line : supplyLines) {
            if (line != null && line.getId() != null) {
                lineById.put(line.getId(), line);
            }
        }
        Long purposeLocationId = null;
        if (!supplyLines.isEmpty() && supplyLines.get(0) != null) {
            purposeLocationId = supplyLines.get(0).getPurposeLocationId();
        }
        List<Long> locationIds = new ArrayList<>();
        if (purposeLocationId != null) {
            locationIds.add(purposeLocationId);
        }
        for (StocktakeDetailLineDto dto : detailLines) {
            if (dto == null || dto.getLineId() == null) {
                continue;
            }
            SupplyRequestModel src = lineById.get(dto.getLineId());
            if (src == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(src.getItemTable(), src.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr != null) {
                dto.setProductName(pr.getProductName());
                dto.setSpecification(pr.getTotalVolume());
                dto.setManufacturer(pr.getManufacturer());
            }
            if (purposeLocationId == null || StringUtils.isEmpty(src.getItemTable()) || src.getItemId() == null) {
                // 缺库位或物品键时账面量无法计算，展示零
                dto.setBookQuantity(BigDecimal.ZERO);
                continue;
            }
            String itemTable = src.getItemTable().trim();
            List<Long> itemIds = List.of(src.getItemId());
            List<InventoryCatalogAvailabilityRowModel> availRows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(itemTable, itemIds, locationIds);
            String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(src.getLotNumber());
            BigDecimal bookQty = BigDecimal.ZERO;
            for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                if (ar == null || ar.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(ar.getLocationId(), purposeLocationId)) {
                    continue;
                }
                if (!Objects.equals(ar.getItemId(), src.getItemId())) {
                    continue;
                }
                String arLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(ar.getLotNumber());
                if (!normLot.equals(arLot)) {
                    continue;
                }
                bookQty = bookQty.add(ar.getQuantity());
            }
            dto.setBookQuantity(bookQty);
        }
    }

    /** 详情明细目录价带与持久化行价对齐（BR-PRICE-SRC-001：仅用 supplyLine.price）。 */
    private void enrichStocktakeDetailLinesCatalogPricing(List<StocktakeDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (StocktakeDetailLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat == null) {
                continue;
            }
            ProductDefinitionCatalogUnitBandsAssembler.enrichWarehouseDetailCatalogFromPersistedSupplyLinePrice(
                cat, dto.getPrice(), dto.getUnitCode(), null);
        }
    }

    /** 校验盘点科室药房在管辖 Gate 内且为启用中 {@link LocationForm#DEPARTMENT_PHARMACY} 形态。 */
    private void assertStocktakePurposeLocationGate(Long purposeLocationId) {
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(purposeLocationId, allowedDepartmentPharmacies);
    }

    /** 当前用户可见的启用中科室药房位置 id 集合。 */
    private Set<Long> resolveGatedDepartmentPharmacyLocationIdsForGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveDepartmentPharmacies());
    }

    /** 断言库位在允许集合内且形态/状态为启用中科室药房。 */
    private void assertDepartmentPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEP_ST_002, "科室药房不在当前用户可选范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_002, "科室药房不在当前用户可选范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.DEPARTMENT_PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            // 非启用中科室药房禁止作为盘点位置
            throw new BusinessException(ErrorCodeConstants.DEP_ST_002, "盘点位置须为启用中的科室药房");
        }
    }

    /**
     * 解析制单人员档案主键（提交/新建须绑定医务人员）。
     *
     * @param notLoggedInErrorCode 未登录错误码
     * @param notLoggedInMessage   未登录提示（保留签名与药房盘点同构）
     * @param noPractitionerMessage 无档案提示
     * @return 医务人员主键
     */
    private Long requireStocktakeApplicantPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

    /** 校验盘点明细必填字段与物品表白名单。 */
    private void assertDeptStocktakeLinesFields(List<StocktakeLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (StocktakeLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "盘点明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "盘点明细单位不能为空");
            }
            if (line.getProfitLossQuantity() == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "盈亏数量不能为空");
            }
            if (line.getActualQuantity() != null && line.getActualQuantity().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "实盘数量不能为负");
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "盘点明细制单时间不能为空");
            }
            String table = line.getItemTable().trim();
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
                && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
                throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "物品表非法");
            }
        }
    }

    /** busNo 非空门禁。 */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "busNo 不能为空");
        }
    }

    /** 断言供应请求行均为科室药房盘点类型。 */
    private void assertDeptStocktake(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_STOCKTAKE.getCode().equals(line.getTypeEnum())) {
                // 类型不匹配时拒绝继续编排，防止串单
                throw new BusinessException(ErrorCodeConstants.DEP_ST_103, "单据类型非科室药房盘点单");
            }
        }
    }

    /** 读取表头供应状态枚举；单号不存在时返回 null。 */
    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        String statusCode = lines.get(0).getStatusEnum();
        if (StringUtils.isEmpty(statusCode)) {
            return null;
        }
        return EnumUtil.fromCode(SupplyStatus.class, statusCode.trim());
    }

    /** 选品物品表须为药品或器械定义表。 */
    private void assertProductTableNameWhitelistForStocktakeSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_001, "productTableName 非法");
        }
    }

    /** 库存匹配用批号规范化（空批号统一为空串）。 */
    private String resolveLotNumberForStocktakeInventory(String lotNumber) {
        if (StringUtils.isEmpty(lotNumber)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNumber.trim();
    }

    /** 从批量产品读模型解析行展示名称。 */
    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isEmpty(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "产品定义缺失，无法解析展示名称");
        }
        return pr.getProductName().trim();
    }

    /** 库存动账拆零单位：优先行单位，否则取产品定义拆零单位。 */
    private String resolveSplitUnitCode(SupplyRequestModel line, ProductDefinitionDisplayDto product) {
        if (product == null || StringUtils.isEmpty(product.getSplitUnitCode())) {
            throw new BusinessException(ErrorCodeConstants.DEP_ST_104, "产品定义缺失或缺少拆零单位编码");
        }
        if (line != null && StringUtils.isNotEmpty(line.getUnitCode())) {
            return line.getUnitCode().trim();
        }
        return product.getSplitUnitCode().trim();
    }

    /** 将领域 SUPPLY_REQUEST_INVALID_ARGUMENT 映射为科室盘点业务错误码。 */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /** 同意编排异常映射：重复同意 → DEP_ST_104，其余参数类 → DEP_ST_003。 */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.DEP_ST_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEP_ST_003, msg);
        }
        return ex;
    }

    /** 建单/改单库位类领域异常映射为 DEP_ST_002 或 DEP_ST_001。 */
    private BusinessException mapStocktakeLocationException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("库房") || msg.contains("位置") || msg.contains("药房") || msg.contains("科室"))) {
                return new BusinessException(ErrorCodeConstants.DEP_ST_002, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEP_ST_001, msg);
        }
        return ex;
    }
}
