/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.requisition.service;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.inventory.InventoryListPractitionerNameResolver;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.inventory.department.requisition.dto.DepartmentRequisitionDetailLineDto;
import com.openhis.health.application.inventory.department.requisition.dto.DepartmentRequisitionLineDto;
import com.openhis.health.application.inventory.department.requisition.dto.DepartmentRequisitionListRowDto;
import com.openhis.health.application.inventory.department.requisition.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.requisition.mapper.InventoryDepartmentRequisitionMapper;
import com.openhis.health.application.inventory.department.requisition.request.*;
import com.openhis.health.application.inventory.department.requisition.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
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
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByOriginalBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.application.inventory.support.SupplyRequestPricedWriteOrchestrator;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
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
import java.util.*;

/**
 * 科室药房请领单（{@link SupplyType#DEPT_REQUISITION}）应用编排。
 *
 * <p>物流双端点：{@code source_location_id}=药库（扣减侧）、{@code purpose_location_id}=科室药房（接收侧，
 * 形态 {@link LocationForm#DEPARTMENT_PHARMACY}）。列表按 {@code bus_no} 聚合。<b>同意（IF-DEP-QL-08）</b>仅调用领域
 * {@code agreeRequest} 置 {@link SupplyStatus#AGREE}，不触发库存变动、发放或下游执行单（对齐订货单模式）。</p>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} → {@code POST /api/app/inventoryDepartmentRequisition/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDepartmentRequisitionAppService {

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestPricedWriteOrchestrator supplyRequestPricedWriteOrchestrator;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final InventoryDepartmentRequisitionMapper requisitionMapper;
    private final InventoryListPractitionerNameResolver inventoryListPractitionerNameResolver;

    /**
     * 科室药房请领页初始化（IF-DEP-QL-01）：目的科室药房、来源药库、入库项目类型、单据状态与人员下拉。
     *
     * @param request 可为空；机构上下文由服务端 Resolver 解析
     * @return 表单初始化选项集合
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_101} 机构上下文缺失或主数据加载失败
     */
    public LoadDepartmentRequisitionFormInitResponse loadDepartmentRequisitionFormInit(LoadDepartmentRequisitionFormInitRequest request) {
        // Gate：会话机构上下文须存在，否则无法按管辖收窄库位与人员
        // Pre：拉取启用科室药房/药库并经 CurrentManagedLocationIdsResolver 收窄；按平台角色装载药师/库管
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEP_QL_101, "机构上下文缺失");
        try {
            List<Location> departmentPharmacies = locationQueryService.listAllActiveDepartmentPharmacies();
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedDepartmentPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(departmentPharmacies);
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
                WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
            // Asm：下拉选项写入初始化响应
            return requisitionMapper.toLoadDepartmentRequisitionFormInitResponse(
                requisitionMapper.toOptionDtoListFromLocations(gatedDepartmentPharmacies),
                requisitionMapper.toOptionDtoListFromLocations(gatedWarehouses),
                requisitionMapper.toOptionDtoListInboundItemTableTypes(),
                requisitionMapper.toOptionDtoListSupplyStatuses(),
                requisitionMapper.toOptionDtoListFromPractitioners(pharmacists),
                requisitionMapper.toOptionDtoListFromPractitioners(warehouseKeepers));
        } catch (RuntimeException ex) {
            log.warn("loadDepartmentRequisitionFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_QL_101, "科室药房请领页主数据加载失败");
        }
    }

    /**
     * 分页查询请领单列表（IF-DEP-QL-02）。
     *
     * @param request 含库位筛选、状态与分页窗口
     * @return 分页列表结果
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} 参数非法；{@link ErrorCodeConstants#DEP_QL_101} 机构上下文缺失；{@link ErrorCodeConstants#DEP_QL_102} 列表查询失败
     */
    public PageDepartmentRequisitionsResponse pageDepartmentRequisitions(PageDepartmentRequisitionsRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        // Pre：组装供应单据列表查询条件
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请求不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_QL_001, "分页参数不能为空");
        this.validateOptionalDepartmentRequisitionTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_QL_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_REQUISITION.getCode());
        // purposeLocationId=0：当前用户可见的全部科室药房；否则单科室药房且须在管辖门控内
        if (Long.valueOf(InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID)
            .equals(request.getPurposeLocationId())) {
            if (allowedDepartmentPharmacies.isEmpty()) {
                listQuery.setPurposeLocationIds(
                    List.of(InventoryDepartmentConstants.LIST_FILTER_NO_VISIBLE_DEPARTMENT_PHARMACY_PLACEHOLDER_ID));
            } else {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowedDepartmentPharmacies));
            }
        } else {
            // 单科室药房筛选（防横向越权）
            this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        if (request.getSourceLocationId() != null) {
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "statusEnum 非法");
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
            // Cmd：按 bus_no 去重分页查询供应请求行
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<DepartmentRequisitionListRowDto> records = requisitionMapper.toDepartmentRequisitionListAggregated(busNos, lines);
            this.enrichDepartmentRequisitionListSourceLocationNames(records);
            this.enrichDepartmentRequisitionListStatusEnumText(records);
            this.enrichDepartmentRequisitionListDisplayNames(records);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合行映射为分页响应
            return requisitionMapper.toPageDepartmentRequisitionsResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageDepartmentRequisitions failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_QL_102, "科室药房请领单列表查询失败");
        }
    }

    /**
     * 按业务单号查询请领单详情（IF-DEP-QL-03）。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} busNo 为空；{@link ErrorCodeConstants#DEP_QL_103} 单据不存在或类型不匹配
     */
    public GetDepartmentRequisitionDetailResponse getDepartmentRequisitionDetail(GetDepartmentRequisitionDetailRequest request) {
        // Gate：busNo 不可为空
        // Pre：按单号回读供应请求行并批量预取产品展示信息
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_103, "科室药房请领单不存在");
        }
        this.assertDepartmentRequisition(lines);
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
            log.warn("batchGetProductByIds degraded for department requisition detail", ex);
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
        GetDepartmentRequisitionDetailResponse res = requisitionMapper.toGetDepartmentRequisitionDetailResponse(lines, hints);
        // Asm：与 IF-DEP-QL-03 同构，从产品读模型回填品名/规格/厂家与多单位目录提示
        this.enrichDepartmentRequisitionDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichDepartmentRequisitionDetailLines(res.getLines());
        return res;
    }

    /**
     * 新建请领单（IF-DEP-QL-04）。
     *
     * @param request 含库位、明细行等业务字段
     * @return 新建业务单号
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} 参数或可用量校验失败；{@link ErrorCodeConstants#DEP_QL_101} 机构或操作人上下文缺失
     */
    public CreateDepartmentRequisitionResponse createDepartmentRequisition(CreateDepartmentRequisitionRequest request) {
        // Gate：请求体、库位与明细不可为空
        // Pre：解析操作人与制单人（医务人员档案）
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请领明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "来源药库与目的科室药房不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_QL_101, "机构上下文缺失");
        this.assertDepartmentRequisitionLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
        this.assertDepartmentRequisitionTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.DEP_QL_001);
        this.assertAvailableQuantitiesAgainstSourceWarehouse(request.getLines(), request.getSourceLocationId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "经手人不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_QL_101, "用户未登录");
        Long applicantId = this.requireDepartmentRequisitionApplicantPractitionerId(
            ErrorCodeConstants.DEP_QL_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        LocalDateTime applyTime = LocalDateTime.now();
        try {
            // Cmd：领域 createRequisition（type=DEPT_REQUISITION）落供应写模型
            String busNo = supplyRequestPricedWriteOrchestrator.createRequisition(
                requisitionMapper.toCreateRequisitionCommand(request, operatorId, applicantId, applyTime));
            // Asm：映射新建响应
            return requisitionMapper.toCreateDepartmentRequisitionResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "科室药房请领参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 编辑请领单（IF-DEP-QL-05）：可编辑态下全量替换明细。
     *
     * @param request 含 busNo、库位与明细
     * @return 业务单号
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} 参数非法；{@link ErrorCodeConstants#DEP_QL_004} 当前状态不可编辑；{@link ErrorCodeConstants#DEP_QL_105} 并发冲突
     */
    public UpdateDepartmentRequisitionResponse updateDepartmentRequisition(UpdateDepartmentRequisitionRequest request) {
        // Gate：busNo 与明细、库位不可为空
        // Pre：回读现有单并校验可编辑态
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请领明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "来源药库与目的科室药房不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_QL_101, "机构上下文缺失");
        this.assertDepartmentRequisitionLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_103, "科室药房请领单不存在");
        }
        SupplyRequestModel head = existing.get(0);
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_004, "当前状态不允许编辑科室药房请领单");
        }
        this.assertDepartmentRequisitionTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.DEP_QL_001);
        this.assertAvailableQuantitiesAgainstSourceWarehouse(request.getLines(), request.getSourceLocationId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "经手人不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_QL_101, "用户未登录");
        Long applicantId = this.requireDepartmentRequisitionApplicantPractitionerId(
            ErrorCodeConstants.DEP_QL_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        LocalDateTime applyTime = LocalDateTime.now();
        try {
            String busNo = supplyRequestPricedWriteOrchestrator.createRequisition(
                requisitionMapper.toCreateRequisitionCommand(request, operatorId, applicantId, applyTime));
            return requisitionMapper.toUpdateDepartmentRequisitionResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "科室药房请领参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_105, "科室药房请领单已被并发修改，请刷新后重试", ex);
        }
    }

    /**
     * 提交请领单审批（IF-DEP-QL-06）。
     *
     * @param request 须含 busNo
     * @return 空成功体
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} busNo 为空；{@link ErrorCodeConstants#DEP_QL_003} 状态不允许提交；{@link ErrorCodeConstants#DEP_QL_101} 操作人未绑定医务人员
     */
    public SubmitDepartmentRequisitionApprovalResponse submitDepartmentRequisitionApproval(SubmitDepartmentRequisitionApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireDepartmentRequisitionApplicantPractitionerId(
            ErrorCodeConstants.DEP_QL_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批，推进至 APPROVAL
            supplyRequestDomainService.submitApproval(
                requisitionMapper.toSubmitApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_QL_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            // 领域非法参数映射为应用层审批类错误码
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_QL_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        // Asm：映射提交后状态
        return requisitionMapper.toSubmitDepartmentRequisitionApprovalResponse(st);
    }

    /**
     * 撤回请领单审批（IF-DEP-QL-07）。
     *
     * @param request 须含 busNo
     * @return 空成功体
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} busNo 为空；{@link ErrorCodeConstants#DEP_QL_003} 状态不允许撤回
     */
    public WithdrawDepartmentRequisitionApprovalResponse withdrawDepartmentRequisitionApproval(WithdrawDepartmentRequisitionApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批，回退至 WITHDRAW
            supplyRequestDomainService.withdrawApproval(
                requisitionMapper.toWithdrawApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_QL_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_QL_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        // Asm：映射撤回后状态
        return requisitionMapper.toWithdrawDepartmentRequisitionApprovalResponse(st);
    }

    /**
     * 科室药房请领单审批同意（IF-DEP-QL-08）：仅置 {@link SupplyStatus#AGREE}，不改库存。
     *
     * @param request 须含 busNo
     * @return 同意后单号与状态
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} busNo 为空；{@link ErrorCodeConstants#DEP_QL_103} 单据不存在；{@link ErrorCodeConstants#DEP_QL_104} 重复同意；{@link ErrorCodeConstants#DEP_QL_105} 并发冲突
     */
    public AgreeDepartmentRequisitionResponse agreeDepartmentRequisition(AgreeDepartmentRequisitionRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "busNo 不能为空");
        }
        String busNoTrim = request.getBusNo().trim();
        // Pre：类型守卫 + 读侧校验未重复同意
        this.preAssertDepartmentRequisitionForAgree(busNoTrim);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(busNoTrim);
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_104, "科室药房请领单已同意，禁止重复同意");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_QL_101, "用户未登录");
        try {
            // Cmd：领域 agreeRequest——请领同意终点，禁止扩展库存/出库编排
            supplyRequestDomainService.agreeRequest(requisitionMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_105, "科室药房请领单已被并发修改，请刷新后重试", ex);
        }
        // Asm：映射同意响应
        return requisitionMapper.toAgreeDepartmentRequisitionResponse(busNoTrim, SupplyStatus.AGREE.getCode());
    }

    /**
     * 同意前类型守卫：请领单须存在且为 {@link SupplyType#DEPT_REQUISITION}。
     *
     * @param busNo 业务单号
     */
    private void preAssertDepartmentRequisitionForAgree(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_103, "科室药房请领单不存在");
        }
        this.assertDepartmentRequisition(lines);
    }

    /**
     * 驳回请领单（IF-DEP-QL-09）。
     *
     * @param request 须含 busNo（驳回原因码/说明可选）
     * @return 驳回成功标志
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} busNo 为空；{@link ErrorCodeConstants#DEP_QL_003} 状态不允许驳回
     */
    public RejectDepartmentRequisitionResponse rejectDepartmentRequisition(RejectDepartmentRequisitionRequest request) {
        // Gate：busNo 不可为空（驳回原因码/说明可选，由 Mapper 合并写入领域命令）
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "busNo 不能为空");
        }
        // 驳回原因码/说明均可空；有值时由 Mapper 合并写入领域命令。
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_QL_101, "用户未登录");
        try {
            // Cmd：领域驳回，置 REJECT 并写入原因
            supplyRequestDomainService.rejectRequest(requisitionMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_QL_003);
        }
        return requisitionMapper.toRejectDepartmentRequisitionResponse(true);
    }

    /**
     * 删除请领单（IF-DEP-QL-10）：须无下游出库单引用。
     *
     * @param request 须含 busNo
     * @return 删除成功标志
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} busNo 为空；{@link ErrorCodeConstants#DEP_QL_005} 已被出库单引用或状态不允许删除
     */
    public RemoveDepartmentRequisitionResponse removeDepartmentRequisition(RemoveDepartmentRequisitionRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        this.assertDepartmentRequisitionNotReferencedByDownstreamStockOut(request.getBusNo().trim());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_QL_101, "用户未登录");
        try {
            // Cmd：领域逻辑删除同号全部明细行
            supplyRequestDomainService.removeByBusNo(requisitionMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_QL_005);
        }
        return requisitionMapper.toRemoveDepartmentRequisitionResponse(true);
    }

    /**
     * 科室请领选品分页（IF-DEP-QL-11）：来源药库库存批号行 + ProductDefinition，与药房请领选品同构。
     *
     * @param request 分页窗口与选品筛选条件
     * @return 产品定义展示行分页
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} 分页或库位参数非法；{@link ErrorCodeConstants#DEP_QL_102} 选品查询失败
     */
    public PageProductsForDepartmentRequisitionResponse pageProductsForDepartmentRequisition(PageProductsForDepartmentRequisitionRequest request) {
        // Gate：分页窗口与机构上下文
        // Pre：组装产品定义分页查询
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_QL_001, "分页参数不能为空");
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "sourceLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_QL_101, "机构上下文缺失");
        Set<Long> allowedWarehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(request.getSourceLocationId(), allowedWarehouses);
        this.assertProductTableNameWhitelistForDepartmentRequisitionSelection(request.getProductTableName());
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getSourceLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        // —— 前台可选：true 仅返回选品库位账面量>0 的批号行；false/未传不过滤数量。——
        invQuery.setPositiveQuantityOnly(request.getPositiveQuantityOnly());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
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
                    log.warn("batchGetProductByIds failed for department requisition selection", ex);
                }
            }
            List<Long> pageInventoryItemIds = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv != null && inv.getId() != null) {
                    pageInventoryItemIds.add(inv.getId());
                }
            }
            // —— 选品为批号行级展开（inventoryItemId 锚点）：可用量按 wkf_inventory_item.id 回填（剔除锁定/过期/停供等不可用口径）。——
            Map<Long, BigDecimal> availableQtyByInventoryItemId = new HashMap<>();
            if (!pageInventoryItemIds.isEmpty()) {
                List<InventoryCatalogAvailabilityRowModel> availRows =
                    this.inventoryItemQueryService.findAvailableCatalogRowsForInventoryItemIds(
                        pageInventoryItemIds, List.of(request.getSourceLocationId()));
                for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                    if (ar == null || ar.getId() == null || ar.getQuantity() == null) {
                        continue;
                    }
                    if (!Objects.equals(ar.getLocationId(), request.getSourceLocationId())) {
                        continue;
                    }
                    availableQtyByInventoryItemId.put(ar.getId(), ar.getQuantity());
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
                ProductDefinitionItemDto dto = requisitionMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
                if (inv.getId() == null) {
                    continue;
                }
                BigDecimal bookQty = inv.getQuantity();
                if (bookQty == null || bookQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                // —— 展示量：优先可用口径（剔除锁定/停供/过期）；未命中时回退账面量，与库存明细「有库存」列表对齐。——
                BigDecimal avail = availableQtyByInventoryItemId.get(inv.getId());
                if (avail == null) {
                    avail = bookQty;
                }
                dto.setAvailableQuantity(avail);
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
            // Asm：选品行富化后写入分页响应
            return PagedResultBuilder.build(new PageProductsForDepartmentRequisitionResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForDepartmentRequisition failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_QL_102, "科室药房请领选品查询失败");
        }
    }

    /**
     * 科室请领列表：批量回填来源库位展示名（本页 id 去重后一次查库）。
     *
     * @param rows 列表行
     */
    private void enrichDepartmentRequisitionListSourceLocationNames(List<DepartmentRequisitionListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // —— 先收集本页全部来源库位 id，再批量查名称，避免列表行数次的 location 点查。——
        Set<Long> sourceIds = new HashSet<>();
        for (DepartmentRequisitionListRowDto row : rows) {
            if (row != null && row.getSourceLocationId() != null) {
                sourceIds.add(row.getSourceLocationId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        for (Long sid : sourceIds) {
            locationQueryService.findOptionalById(sid).ifPresent(loc -> {
                if (StringUtils.isNotEmpty(loc.getName())) {
                    nameById.put(sid, loc.getName().trim());
                }
            });
        }
        // —— 回填展示列：未命中名称时保持 null，由前端决定是否展示占位。——
        for (DepartmentRequisitionListRowDto row : rows) {
            if (row == null || row.getSourceLocationId() == null) {
                continue;
            }
            row.setSourceLocationName(nameById.get(row.getSourceLocationId()));
        }
    }

    /**
     * 科室请领列表：将持久化 {@code statusEnum} code 解析为中文展示文案。
     *
     * @param rows 列表行
     */
    private void enrichDepartmentRequisitionListStatusEnumText(List<DepartmentRequisitionListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (DepartmentRequisitionListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getStatusEnum())) {
                continue;
            }
            // —— 列表行 statusEnum 为持久化 code：仅解析成功时写中文，非法码保持 text 为空。——
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
            if (st != null) {
                row.setStatusEnumText(st.getDisplay());
            }
        }
    }

    /**
     * 科室请领列表：批量解析制单人/审批人展示名。
     *
     * @param rows 列表行
     */
    private void enrichDepartmentRequisitionListDisplayNames(List<DepartmentRequisitionListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = new HashSet<>();
        for (DepartmentRequisitionListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getApplicantId() != null) {
                practitionerIds.add(row.getApplicantId());
            }
            if (row.getApproverId() != null) {
                practitionerIds.add(row.getApproverId());
            }
        }
        Map<Long, String> nameById = this.inventoryListPractitionerNameResolver.resolveNamesByIds(practitionerIds);
        for (DepartmentRequisitionListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getApplicantId() != null) {
                String name = nameById.get(row.getApplicantId());
                if (name != null) {
                    row.setCreatorName(name);
                }
            }
            if (row.getApproverId() != null) {
                String name = nameById.get(row.getApproverId());
                if (name != null) {
                    row.setApproverName(name);
                }
            }
        }
    }

    /**
     * 科室药房请领详情：补齐项目名称、规格、厂家与行单位展示名（与药库出库 IF-WHS-SO-03 同构）。
     *
     * @param lines            详情明细行
     * @param products         产品读模型映射（表名+主键复合键）
     * @param terminologyMaps  选品术语（单位展示名等）
     */
    private void enrichDepartmentRequisitionDetailProductDisplay(
        List<DepartmentRequisitionDetailLineDto> lines,
        Map<String, ProductDefinitionDisplayDto> products,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (products == null || products.isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        for (DepartmentRequisitionDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
            }
            // 详情表格「项目名称/规格/厂家」读这三列，不能只依赖 productCatalog
            dto.setProductName(pr.getProductName());
            dto.setSpecification(pr.getTotalVolume());
            dto.setManufacturer(pr.getManufacturer());
            dto.setUnitCodeDictText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, dto.getUnitCode(), pr));
            ProductDefinitionCatalogEditorHintsDto catalog = dto.getProductCatalog();
            if (catalog != null && catalog.getUnitBands() != null && !catalog.getUnitBands().isEmpty()) {
                this.productDefinitionDisplayTerminologyAssembler.applyInboundUnitBandDisplayNames(
                    catalog.getUnitBands(), pr, terminologyMaps);
            }
        }
    }

    /**
     * 详情行槽价：仅锚 {@code SupplyRequest} 持久化行价（**BR-PRICE-SRC-001**）。
     *
     * @param lines 详情行列表，可为 null 或空
     */
    private void enrichDepartmentRequisitionDetailLines(List<DepartmentRequisitionDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (DepartmentRequisitionDetailLineDto dto : lines) {
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

    /**
     * 新建/编辑入口的位置校验：来源须为岗位可见药库，目的须为岗位可见科室药房。
     *
     * @param sourceLocationId  来源药库主键
     * @param purposeLocationId 目的科室药房主键
     */
    private void assertDepartmentRequisitionLocationGate(Long sourceLocationId, Long purposeLocationId) {
        Set<Long> warehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(sourceLocationId, warehouses);
        Set<Long> departmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(purposeLocationId, departmentPharmacies);
    }

    /**
     * 岗位可见且启用中的药库位置 id 集合（请领来源 Gate）。
     *
     * @return 经 ManagedLocation Gate 过滤后的药库 id
     */
    private Set<Long> resolveGatedWarehouseLocationIdsForWarehouseGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    /**
     * 岗位可见且启用中的科室药房位置 id 集合（请领目的 Gate）。
     *
     * @return 经 ManagedLocation Gate 过滤后的科室药房 id
     */
    private Set<Long> resolveGatedDepartmentPharmacyLocationIdsForGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveDepartmentPharmacies());
    }

    /**
     * 断言给定药库主键在允许集合内且为启用中的仓库形态。
     *
     * @param locationId 位置主键
     * @param allowed      岗位可见药库 id 集合
     */
    private void assertWarehouseLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEP_QL_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "药库位置不在当前用户可选药库范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WAREHOUSE.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "来源位置须为启用中的药库");
        }
    }

    /**
     * 断言给定科室药房主键在允许集合内且为启用中的科室药房形态。
     *
     * @param locationId 位置主键
     * @param allowed    岗位可见科室药房 id 集合
     */
    private void assertDepartmentPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEP_QL_001, "目的科室药房不在当前用户可选科室药房范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "目的科室药房不在当前用户可选科室药房范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.DEPARTMENT_PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "目的位置须为启用中的科室药房");
        }
    }

    /**
     * 业务单号非空校验；空串时抛 {@link ErrorCodeConstants#DEP_QL_001}，阻止无单号写路径误操作。
     */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "busNo 不能为空");
        }
    }

    /**
     * 科室药房请领单申请人（{@code wkf_supply_request.applicant_id}）：取当前登录用户绑定医务人员主键；
     *
     * @param notLoggedInErrorCode   未登录或缺用户主键时错误码
     * @param notLoggedInMessage     未登录提示
     * @param noPractitionerMessage  已登录但未绑定医务人员时的提示
     * @return 非空医务人员主键
     * @throws BusinessException 无登录用户或未绑定医务人员
     */
    private Long requireDepartmentRequisitionApplicantPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

    /**
     * 删除前校验：请领单未被药库出库单引用。
     *
     * @param requisitionBusNo 请领业务单号
     */
    private void assertDepartmentRequisitionNotReferencedByDownstreamStockOut(String requisitionBusNo) {
        SupplyByOriginalBusNoQuery q = new SupplyByOriginalBusNoQuery();
        q.setOriginalBusNo(requisitionBusNo);
        List<SupplyRequestModel> downstream = supplyRequestQueryService.getSupplyByOriginalBusNo(q);
        for (SupplyRequestModel line : downstream) {
            if (line == null) {
                continue;
            }
            if (SupplyType.CABINET_STOCK_OUT.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_QL_005, "请领单已被出库单引用，禁止删除");
            }
        }
    }

    /**
     * 断言多行共享同一 {@code DEPT_REQUISITION} 类型（详情/列表读侧一致性）。
     *
     * @param lines 供应请求行
     */
    private void assertDepartmentRequisition(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_REQUISITION.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_QL_103, "单据类型非科室药房请领单");
            }
        }
    }

    /**
     * 列表可选 type 收窄：若传入则须为科室药房请领类型持久化码。
     *
     * @param typeEnumCode 请求 typeEnum，可 blank
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} 非空白但无法解析或不是科室药房请领类型
     */
    private void validateOptionalDepartmentRequisitionTypeEnum(String typeEnumCode) {
        if (StringUtils.isEmpty(typeEnumCode)) {
            return;
        }
        SupplyType declared = EnumUtil.fromCode(SupplyType.class, typeEnumCode.trim());
        if (declared == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "typeEnum 非法");
        }
        if (declared != SupplyType.DEPT_REQUISITION) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "typeEnum 必须为科室药房请领单类型");
        }
    }

    /**
     * 保存草稿前，断言「来源药库」上待入数量不超过<strong>可用库存</strong>。
     *
     * <p><strong>不包含的业务</strong>：药库进货等「只看单品总和」的路径仍走
     * {@link InventoryItemQueryService#findAvailableInventories} 等契约，本方法不负责。</p>
     *
     * <p><strong>数量口径</strong>：{@code item_quantity} 与 {@code wkf_inventory_item.quantity} 均为拆零（最小）单位；
     * {@code unit_code} 仅作文书记录，不参与与库存的比较（见 {@link SupplyPersistedSplitQuantity}）。</p>
     *
     * <p><strong>两阶段可用量校验（与药库出库同构）</strong>：</p>
     * <ul>
     *   <li><strong>阶段 A</strong>：行上带 {@code inventoryItemId} 时，按库存主键批量拉可用行，逐键比对请求量与行上可用量，
     *   并核对库位、物品表、物品 id；若明细填了批号，还须与该行归一化批号一致（防前端串行）。</li>
     *   <li><strong>阶段 B</strong>：未带库存主键时，按物品表分组，在内存用「物品 + 归一化批号」聚合同品同批的多行可用量后再比；
     *   若明细未填批号（归一化后空串），则与该物品在来源库<strong>全部可用批</strong>的数量总和比较。</li>
     * </ul>
     *
     * @param lines             HTTP 明细；null/空由调用方保证已前置校验时可短路
     * @param sourceLocationId  用户选定的来源药库
     * @throws BusinessException {@link ErrorCodeConstants#DEP_QL_001} 字段缺失、表类型非法、数量非正，或可用量不足
     */
    private void assertAvailableQuantitiesAgainstSourceWarehouse(List<DepartmentRequisitionLineDto> lines, Long sourceLocationId) {
        if (sourceLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> locationIds = List.of(sourceLocationId);

        // —— 阶段 A：带 inventoryItemId 的行 —— 按 wkf_inventory_item.id 精确门禁，避免仅靠批号字符串聚合时算错可用量。
        Map<Long, BigDecimal> requestedByInventoryItemId = new LinkedHashMap<>();
        Map<Long, DepartmentRequisitionLineDto> sampleLineByInventoryItemId = new LinkedHashMap<>();
        for (DepartmentRequisitionLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getInventoryItemId() == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请领明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请领明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.DEP_QL_001, "明细数量须为正数");
            Long invId = line.getInventoryItemId();
            // —— 同一库存主键在明细多行出现时：持久化前按拆零数量求和，再与单行可用量比。——
            requestedByInventoryItemId.merge(invId, splitQty, BigDecimal::add);
            if (!sampleLineByInventoryItemId.containsKey(invId)) {
                sampleLineByInventoryItemId.put(invId, line);
            }
        }
        if (!requestedByInventoryItemId.isEmpty()) {
            List<Long> invIds = new ArrayList<>(requestedByInventoryItemId.keySet());
            List<InventoryCatalogAvailabilityRowModel> rowsById =
                this.inventoryItemQueryService.findAvailableCatalogRowsForInventoryItemIds(invIds, locationIds);
            Map<Long, InventoryCatalogAvailabilityRowModel> rowByInventoryItemId = new HashMap<>();
            for (InventoryCatalogAvailabilityRowModel row : rowsById) {
                if (row == null || row.getId() == null) {
                    continue;
                }
                rowByInventoryItemId.put(row.getId(), row);
            }
            for (Map.Entry<Long, BigDecimal> want : requestedByInventoryItemId.entrySet()) {
                Long invId = want.getKey();
                BigDecimal requested = want.getValue();
                // —— 可用投影按 id 命中：查不到表示该行已非可用口径（锁定/过期/状态等），与前端选品行脱节。——
                InventoryCatalogAvailabilityRowModel row = rowByInventoryItemId.get(invId);
                DepartmentRequisitionLineDto sample = sampleLineByInventoryItemId.get(invId);
                if (row == null || sample == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_QL_001,
                        "所选库存行当前不可用、已锁定或已过期，请刷新选品后再试");
                }
                if (!Objects.equals(row.getLocationId(), sourceLocationId)) {
                    throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "所选库存行与当前来源药库不一致");
                }
                String rowTable = "";
                if (row.getItemTable() != null) {
                    rowTable = row.getItemTable().trim();
                }
                if (!rowTable.equals(sample.getItemTable().trim())) {
                    throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "所选库存行物品表与明细不一致");
                }
                if (!Objects.equals(row.getItemId(), sample.getItemId())) {
                    throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "所选库存行物品主键与明细不一致");
                }
                String lineLotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(sample.getLotNumber());
                if (!lineLotKey.isEmpty()) {
                    // —— 明细声明了批号：必须与所选库存行归一化批号一致，防止串批提交。——
                    String rowLotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(row.getLotNumber());
                    if (!lineLotKey.equals(rowLotKey)) {
                        throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "明细批号与所选库存行批号不一致");
                    }
                }
                BigDecimal available = row.getQuantity();
                if (available == null) {
                    available = BigDecimal.ZERO;
                }
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_QL_001,
                        "来源药库该库存行可用量不足，请减少请领数量或刷新库存后再试");
                }
            }
        }

        // —— 阶段 B：未带 inventoryItemId 的行 —— 在单库位 + 单物品表下按「物品 id → 归一化批号」二维 Map 聚合，与 DM 四列唯一（库位+表+品+批）一致。
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (DepartmentRequisitionLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getInventoryItemId() != null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请领明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "请领明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.DEP_QL_001, "明细数量须为正数");
            String lotKeyPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
            if (lotKeyPart.isEmpty()) {
                Map<Long, BigDecimal> perItem =
                    requestedWithoutLotByTable.computeIfAbsent(table, t -> new HashMap<>());
                perItem.merge(itemId, splitQty, BigDecimal::add);
            } else {
                requestedByLotByTable
                    .computeIfAbsent(table, t -> new HashMap<>())
                    .computeIfAbsent(itemId, i -> new HashMap<>())
                    .merge(lotKeyPart, splitQty, BigDecimal::add);
            }
        }
        Set<String> itemTables = new LinkedHashSet<>();
        itemTables.addAll(requestedByLotByTable.keySet());
        itemTables.addAll(requestedWithoutLotByTable.keySet());
        for (String itemTable : itemTables) {
            // 每张物品表单独一批 IN 查询：表名是库存行的物理分面，不可混在单次谓词里跨表 OR。
            Map<Long, Map<String, BigDecimal>> wantedByItemLot =
                requestedByLotByTable.getOrDefault(itemTable, Collections.emptyMap());
            Map<Long, BigDecimal> wantedWithoutLot = requestedWithoutLotByTable.getOrDefault(itemTable, Collections.emptyMap());
            Set<Long> itemIds = new LinkedHashSet<>();
            itemIds.addAll(wantedByItemLot.keySet());
            itemIds.addAll(wantedWithoutLot.keySet());
            if (itemIds.isEmpty()) {
                continue;
            }
            List<InventoryCatalogAvailabilityRowModel> catalogRows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    itemTable, new ArrayList<>(itemIds), locationIds);
            Map<Long, Map<String, BigDecimal>> availableByItemLot = new HashMap<>();
            Map<Long, BigDecimal> availableByItemId = new HashMap<>();
            for (InventoryCatalogAvailabilityRowModel crow : catalogRows) {
                if (crow == null || crow.getItemId() == null || crow.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(crow.getLocationId(), sourceLocationId)) {
                    continue;
                }
                Long crowItemId = crow.getItemId();
                String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(crow.getLotNumber());
                availableByItemLot
                    .computeIfAbsent(crowItemId, k -> new HashMap<>())
                    .merge(normLot, crow.getQuantity(), BigDecimal::add);
                // 无批号维的请求只关心「该品在库内全部可用行的总和」，故并行维护按 itemId 的纵向累计。
                availableByItemId.merge(crowItemId, crow.getQuantity(), BigDecimal::add);
            }
            // —— 有归一化批号键的请求：按「品 + 批」与可用目录逐键比较。——
            for (Map.Entry<Long, Map<String, BigDecimal>> wantItem : wantedByItemLot.entrySet()) {
                Long itemId = wantItem.getKey();
                Map<String, BigDecimal> wantedLots = wantItem.getValue();
                Map<String, BigDecimal> availLots = availableByItemLot.getOrDefault(itemId, Collections.emptyMap());
                for (Map.Entry<String, BigDecimal> wantLot : wantedLots.entrySet()) {
                    BigDecimal requested = wantLot.getValue();
                    BigDecimal available = availLots.getOrDefault(wantLot.getKey(), BigDecimal.ZERO);
                    if (requested.compareTo(available) > 0) {
                        throw new BusinessException(
                            ErrorCodeConstants.DEP_QL_001,
                            "明细数量超过来源药库该批号可用量，请减少数量或刷新库存后再试");
                    }
                }
            }
            // —— 无批号键：只与「该品在来源库全部可用批」纵向和比较（多批可混扣）。——
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_QL_001,
                        "明细数量超过来源药库可用量，请减少数量或刷新库存后再试");
                }
            }
        }
    }

    /**
     * 追溯码宽字符上限校验（逗号分隔多码），与进货/出库同口径。
     *
     * @param lines      明细行
     * @param errorCode  失败时使用的错误码常量
     */
    private void assertDepartmentRequisitionTraceNosWithinWideLimit(List<DepartmentRequisitionLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (DepartmentRequisitionLineDto line : lines) {
            if (line == null || StringUtils.isEmpty(line.getTraceNo())) {
                continue;
            }
            // —— 整段串上限 + 逗号分片每段上限：与进货/出库追溯码宽表一致。——
            String raw = line.getTraceNo();
            if (raw.length() > maxLen) {
                throw new BusinessException(errorCode, "追溯码长度超过允许上限（" + maxLen + "）");
            }
            for (String part : raw.split(GeneralSharedConstants.COMMA)) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.length() > maxLen) {
                    throw new BusinessException(errorCode, "追溯码片段长度超过允许上限（" + maxLen + "）");
                }
            }
        }
    }

    /**
     * 选品接口物品表白名单门控（药品定义表 / 设备定义表）。
     *
     * @param raw 请求 productTableName
     */
    private void assertProductTableNameWhitelistForDepartmentRequisitionSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.DEP_QL_001, "productTableName 非法");
        }
    }

    /**
     * 读头行状态枚举（提交/撤回等接口出参用）。
     *
     * @param busNo 业务单号
     * @return 头行状态；单据不存在时 null
     */
    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        return EnumUtil.fromCode(SupplyStatus.class, lines.get(0).getStatusEnum());
    }

    /**
     * 将领域「非法参数」口径映射为应用层列表/审批类默认错误码。
     *
     * @param ex          领域业务异常
     * @param defaultCode 映射目标错误码
     * @return 映射后的异常或原样返回
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * 同意路径专用：非法参数中「已同意」语义提升为 DEP_QL_104，其余回落 DEP_QL_003。
     *
     * @param ex 领域业务异常
     * @return 映射后的异常或原样返回
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.DEP_QL_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEP_QL_003, msg);
        }
        return ex;
    }
}
