/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.service;

import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.LinkablePharmacyStockInListRowDto;
import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.ReturnToWarehouseLineDto;
import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.ReturnToWarehouseListRowDto;
import com.openhis.health.application.inventory.pharmacy.returntowarehouse.mapper.InventoryPharmacyReturnToWarehouseMapper;
import com.openhis.health.application.inventory.pharmacy.returntowarehouse.request.*;
import com.openhis.health.application.inventory.pharmacy.returntowarehouse.response.*;
import com.openhis.health.application.inventory.pharmacy.returntowarehouse.support.ReturnToWarehouseStockInLineHelper;
import com.openhis.health.application.shared.inventory.InventoryItemProductSelectionPageLoader;
import com.openhis.health.application.shared.supplyrequest.SupplyRequestPricedWriteOrchestrator;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.application.shared.inventory.InventoryListLocationNameResolver;
import com.openhis.health.application.shared.inventory.InventoryListPractitionerNameResolver;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.tracenumber.RecordTraceTransferPairSpec;
import com.openhis.health.application.shared.tracenumber.TraceNumberSupplyAgreeTraceOrchestrator;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryPharmacyConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.enums.SupplierStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByOriginalBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 药房退库单（{@link SupplyType#PHARMACY_RETURN_TO_WAREHOUSE}，库存 P-02 · {@code returntowarehouse}）应用编排。
 *
 * <p>明细可选关联已同意药房入库单（{@code original_bus_no}）拉取可退余量，也可按源药房库存自由选品（IF-PHA-RTW-11/12/13）。
 * 同意后：领域落已同意态 → 退库发放 → 源药房扣减、目的药库增库存 → 追溯码出库 → 自动生成退库入库壳单（type 5）；不上报医保进销存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPharmacyReturnToWarehouseAppService {

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
    private final InventoryItemProductSelectionPageLoader inventoryItemProductSelectionPageLoader;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final LocationQueryService locationQueryService;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryPharmacyReturnToWarehouseMapper returnToWarehouseMapper;
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final TraceNumberSupplyAgreeTraceOrchestrator traceNumberSupplyAgreeTraceOrchestrator;
    private final InventoryListPractitionerNameResolver listPractitionerNameResolver;
    private final InventoryListLocationNameResolver listLocationNameResolver;

    /**
     * 加载页主数据（库位、人员、状态等下拉）。
     *
     * @param request 可为空；当前无额外筛选字段
     * @return 表单初始化选项集合
     */
    public LoadReturnToWarehouseFormInitResponse loadReturnToWarehouseFormInit(LoadReturnToWarehouseFormInitRequest request) {
        // Gate：会话机构上下文须存在，否则无法按管辖收窄库位与人员
        // Pre：拉取启用库位/人员并经管辖 Gate 收窄
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RTW_101, "机构上下文缺失");
        try {
            List<Location> pharmacies = this.locationQueryService.listAllActivePharmacies();
            List<Location> gatedPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(pharmacies);
            List<Location> warehouses = this.locationQueryService.listAllActiveWarehouses();
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel> suppliers =
                this.supplierQueryService.listSuppliersByTypeAndActive(
                    com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode());
            // Asm：映射页主数据下拉选项
            return this.returnToWarehouseMapper.toLoadReturnToWarehouseFormInitResponse(
                this.returnToWarehouseMapper.toWarehouseOptionDtoList(gatedPharmacies),
                this.returnToWarehouseMapper.toWarehouseOptionDtoList(gatedWarehouses),
                this.returnToWarehouseMapper.toSupplierOptionDtoList(suppliers));
        } catch (RuntimeException ex) {
            log.warn("loadReturnToWarehouseFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_102, "退库页主数据加载失败");
        }
    }

    /**
     * 分页查询单据列表。
     *
     * @param request 含库位筛选、状态与分页窗口
     * @return 分页列表结果
     */
    public PageReturnToWarehousesResponse pageReturnToWarehouses(PageReturnToWarehousesRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        // Pre：组装供应单据列表查询条件
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "请求不能为空");
        }
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "sourceLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.PHA_RTW_001, "分页参数不能为空");
        this.validateOptionalReturnTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RTW_101, "机构上下文缺失");
        Set<Long> allowedPharmacies = this.resolveGatedPharmacyLocationIds();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.PHARMACY_RETURN_TO_WAREHOUSE.getCode());
        // Pre：sourceLocationId=0 表示当前用户可见的全部药房；否则单库且须在管辖门控内
        if (Long.valueOf(InventoryPharmacyConstants.ALL_PHARMACIES_LIST_FILTER_LOCATION_ID)
            .equals(request.getSourceLocationId())) {
            if (allowedPharmacies.isEmpty()) {
                // 无可见药房：占位 id 收窄为空页，避免无约束全表扫描
                listQuery.setSourceLocationIds(
                    List.of(InventoryPharmacyConstants.LIST_FILTER_NO_VISIBLE_PHARMACY_PLACEHOLDER_ID));
            } else {
                // 多药房 IN 查询：下推当前用户可见 id 集合
                listQuery.setSourceLocationIds(new ArrayList<>(allowedPharmacies));
            }
        } else {
            // 单药房筛选（防横向越权）
            this.assertPharmacyLocationAllowed(request.getSourceLocationId(), allowedPharmacies);
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (request.getStatusEnum() != null) {
            // 列表筛选：前端传 sortOrder，下推持久化 status code
            SupplyStatus st = EnumUtil.requireFromSortOrder(SupplyStatus.class, request.getStatusEnum().longValue());
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        listQuery.setStartDate(request.getApplyTimeFrom());
        listQuery.setEndDate(request.getApplyTimeTo());
        try {
            // Cmd：按 bus_no 聚合分页查询药房退库单
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<ReturnToWarehouseListRowDto> records =
                this.returnToWarehouseMapper.toReturnToWarehouseListAggregated(busNos, lines);
            // Pre：侧栏卡片制单人/审核人/供应商展示名批量富化
            this.enrichReturnToWarehouseListDisplayNames(records);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：映射聚合分页列表响应
            return this.returnToWarehouseMapper.toPageReturnToWarehousesAggregatedResponse(
                mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageReturnToWarehouses failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_102, "退库单列表查询失败");
        }
    }

    /**
     * 按业务单号查询单据详情。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     */
    public GetReturnToWarehouseDetailResponse getReturnToWarehouseDetail(GetReturnToWarehouseDetailRequest request) {
        // Gate：busNo 不可为空
        // Pre：按单号回读供应请求行并批量预取产品展示信息
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_103, "退库单不存在");
        }
        // Gate：单据类型须为药房退库，防止跨类型误读
        this.assertPharmacyReturnToWarehouse(lines);

        // Pre：批量预取产品展示名称
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
            products = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("batchGetProductByIds degraded for pharmacy return to warehouse detail", ex);
            products = new HashMap<>();
        }
        // Asm：映射详情头与明细行
        return this.returnToWarehouseMapper.toGetReturnToWarehouseDetailResponse(lines, products);
    }

    /**
     * 新建单据（草稿/待提交）。
     *
     * @param request 含库位、明细行等业务字段
     * @return 新建业务单号
     */
    public CreateReturnToWarehouseResponse createReturnToWarehouse(CreateReturnToWarehouseRequest request) {
        // Gate：请求体、库位与明细不可为空
        // Pre：解析操作人与制单人（医务人员档案）
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "退库明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RTW_101, "机构上下文缺失");
        this.assertReturnToWarehouseLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
        this.validateSupplierOptional(request.getSupplierId());
        this.assertReturnToWarehouseTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.PHA_RTW_001);
        // Pre：有关联入库单时校验可退余量；无关联时允许自由选品，仅靠源药房可用库存门禁
        String createOriginalBusNo = request.getOriginalBusNo();
        if (!StringUtils.isEmpty(createOriginalBusNo)) {
            createOriginalBusNo = createOriginalBusNo.trim();
        } else {
            createOriginalBusNo = null;
        }
        this.assertReturnLinesBoundToStockInAndWithinReturnable(
            createOriginalBusNo, request.getSourceLocationId(),
            this.toDraftReturnLinesFromReturnLineDtos(request.getLines()), null);
        this.assertReturnToWarehouseDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertReturnToWarehouseLinesOccurrencePresent(request.getLines());

        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RTW_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.PHA_RTW_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：领域 createReturn 落草稿/待提交
            CreateReturnCommand cmd = this.returnToWarehouseMapper.toCreateReturnToWarehouseCommand(
                request, operatorId, applicantId, null);
            String busNo = this.supplyRequestPricedWriteOrchestrator.createReturn(cmd);
            // Asm：映射新建成功响应
            return this.returnToWarehouseMapper.toCreateReturnToWarehouseResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "退库参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 更新单据（全量替换明细，仅可编辑态允许）。
     *
     * @param request 含 busNo、库位与明细
     * @return 业务单号
     */
    public UpdateReturnToWarehouseResponse updateReturnToWarehouse(UpdateReturnToWarehouseRequest request) {
        // Gate：busNo 与明细、库位不可为空
        // Pre：回读现有单并校验可编辑态
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "busNo 不能为空");
        }
        if (request.getRowVersion() != null) {
            // 退库修订走软删重插，不支持 rowVersion 乐观锁
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_105, "退库单不支持 rowVersion 乐观锁");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "退库明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RTW_101, "机构上下文缺失");
        this.assertReturnToWarehouseLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());

        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_103, "退库单不存在");
        }
        this.assertPharmacyReturnToWarehouse(existing);
        SupplyRequestModel head = existing.get(0);
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        editQuery.setItemId(head.getItemId());
        Boolean editable = this.supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            // 非可编辑态禁止全量替换明细
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_004, "当前状态不允许编辑退库单");
        }

        this.validateSupplierOptional(request.getSupplierId());
        this.assertReturnToWarehouseTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.PHA_RTW_004);
        String originalSnapshot = head.getOriginalBusNo();
        this.assertReturnLinesBoundToStockInAndWithinReturnable(
            originalSnapshot, request.getSourceLocationId(),
            this.toDraftReturnLinesFromReturnLineDtos(request.getLines()), request.getBusNo().trim());
        this.assertReturnToWarehouseDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertReturnToWarehouseLinesOccurrencePresent(request.getLines());

        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RTW_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.PHA_RTW_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：软删重插全量对齐明细
            CreateReturnCommand cmd = this.returnToWarehouseMapper.toCreateReturnToWarehouseCommandForUpdate(
                request, operatorId, applicantId, originalSnapshot);
            String busNo = this.supplyRequestPricedWriteOrchestrator.createReturn(cmd);
            // Asm：映射修订成功响应
            return this.returnToWarehouseMapper.toUpdateReturnToWarehouseResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "退库参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_105, "退库单已被并发修改，请刷新后重试", ex);
        }
    }

    /**
     * 提交单据审批。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public SubmitReturnToWarehouseApprovalResponse submitReturnToWarehouseApproval(
        SubmitReturnToWarehouseApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.PHA_RTW_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批，状态迁移至审中
            this.supplyRequestDomainService.submitApproval(
                this.returnToWarehouseMapper.toSubmitApprovalCommand(
                    request,
                    this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RTW_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_RTW_003);
        }
        // Asm：映射提交成功响应
        return this.returnToWarehouseMapper.toSubmitReturnToWarehouseApprovalResponse();
    }

    /**
     * 撤回单据审批（回到可编辑态）。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public WithdrawReturnToWarehouseApprovalResponse withdrawReturnToWarehouseApproval(
        WithdrawReturnToWarehouseApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批，回到可编辑态
            this.supplyRequestDomainService.withdrawApproval(
                this.returnToWarehouseMapper.toWithdrawApprovalCommand(
                    request,
                    this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RTW_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_RTW_003);
        }
        // Asm：映射撤回成功响应
        return this.returnToWarehouseMapper.toWithdrawReturnToWarehouseApprovalResponse();
    }

    /**
     * 药房退库单同意编排：领域落已同意态 → 退库发放 → 源药房扣减、目的药库增库存 → 追溯码出库。
     *
     * @param request 须含 busNo
     * @return 同意成功标志
     */
    public AgreeReturnToWarehouseResponse agreeReturnToWarehouse(AgreeReturnToWarehouseRequest request) {
        // Gate：busNo 与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "busNo 不能为空");
        }
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RTW_101, "用户未登录");

        // Pre：回读退库单并校验未重复同意
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_103, "退库单不存在");
        }
        this.assertPharmacyReturnToWarehouse(beforeLines);
        SupplyRequestValidationModel validation = this.supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            // 已同意单禁止重复 agree，避免双侧库存重复扣增
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "退库单已同意，禁止重复同意");
        }
        SupplyRequestModel agreeHead = beforeLines.get(0);
        this.assertReturnLinesBoundToStockInAndWithinReturnable(
            agreeHead.getOriginalBusNo(),
            agreeHead.getSourceLocationId(),
            this.toDraftReturnLinesFromSupplyModels(beforeLines),
            request.getBusNo().trim());
        this.assertReturnToWarehouseDraftLinesWithinSourceAvailableInventory(
            agreeHead.getSourceLocationId(),
            this.toReturnLineDtosFromSupplyModels(beforeLines));

        try {
            // Cmd：领域 agree 落已同意态
            this.supplyRequestDomainService.agreeRequest(
                this.returnToWarehouseMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_105, "退库单已被并发修改，请刷新后重试", ex);
        }

        // Pre：强制读回 AGREE 行，保证发放/库存编排输入合法
        this.supplyRequestDomainService.flushPendingChanges();

        List<SupplyRequestModel> agreedLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertPharmacyReturnToWarehouse(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                // flush 后须全部为 AGREE，否则库存编排输入不可信
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "同意后回读状态异常，中止退库编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "退库明细缺少持久化主键，无法完成编排");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "退库明细数量无效，无法完成编排");
            }
        }

        List<Long> requestIdsForDeliveryGate = new ArrayList<>();
        for (SupplyRequestModel row : agreedLines) {
            if (row != null && row.getId() != null) {
                requestIdsForDeliveryGate.add(row.getId());
            }
        }
        Set<Long> duplicateCompletedRequestIds =
            this.supplyDeliveryQueryService.findRequestIdsHavingCompletedDelivery(requestIdsForDeliveryGate);
        if (!duplicateCompletedRequestIds.isEmpty()) {
            // 发放幂等：同一供应请求行不可重复完成态发放
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "供应请求行已存在完成态发放，禁止重复同意退库");
        }

        try {
            // Cmd：退库发放落库
            this.supplyDeliveryDomainService.createSupplyDelivery(
                this.returnToWarehouseMapper.toCreateSupplyDeliveryCommandForReturnToWarehouse(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "退库发放落库失败：" + ex.getMessage());
        }

        // Cmd：批量产品读模型，供拆零量与目的侧建库存行使用
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
                productByKey = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed during pharmacy return to warehouse inventory assemble", ex);
            }
        }

        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        RecordTraceTransferPairSpec transferPairCmd = new RecordTraceTransferPairSpec();
        transferPairCmd.setOperatorId(operatorId);
        transferPairCmd.setSupplyTypeCode(SupplyType.PHARMACY_RETURN_TO_WAREHOUSE.getCode());
        transferPairCmd.setBusNo(busNoTrim);
        transferPairCmd.setOutLocationId(head.getSourceLocationId());
        transferPairCmd.setOutLocationTypeEnum(this.resolveSourceLocationTypeCode(head, head));
        transferPairCmd.setInLocationId(head.getPurposeLocationId());
        transferPairCmd.setInLocationTypeEnum(this.resolvePurposeLocationTypeCode(head, head));
        List<RecordTraceTransferPairSpec.TransferLine> transferLines = new ArrayList<>();
        Map<Long, BigDecimal> decreaseMerged = new HashMap<>();
        List<ReturnToWarehouseDestIncr> destIncrs = new ArrayList<>();

        // Cmd：组装源侧扣减、目的侧入账计划与追溯出库项
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long sourceLocationId = line.getSourceLocationId();
            Long purposeLocationId = line.getPurposeLocationId();
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.PHA_RTW_104, "供应请求行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isEmpty(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();
            String productName = this.resolveProductName(line, productByKey);
            // 批号空串与库存可用量匹配口径一致（空白归一为空串）
            String resolvedLot = GeneralSharedConstants.EMPTY_STRING;
            if (!StringUtils.isEmpty(line.getLotNumber())) {
                resolvedLot = line.getLotNumber().trim();
            }

            InventoryMatchKey srcKey = new InventoryMatchKey();
            srcKey.setSupplierId(line.getSupplierId());
            srcKey.setLotNumber(resolvedLot);
            srcKey.setLocationId(sourceLocationId);
            srcKey.setItemTable(line.getItemTable());
            srcKey.setItemId(line.getItemId());
            Long sourceInventoryId = this.inventoryItemQueryService.findFirstIdByInventoryMatchKey(srcKey).orElse(null);
            if (sourceInventoryId == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "源药房未找到匹配库存行，无法扣减");
            }
            decreaseMerged.merge(sourceInventoryId, splitQty, BigDecimal::add);

            ReturnToWarehouseDestIncr op = new ReturnToWarehouseDestIncr();
            op.line = line;
            op.purposeLocationId = purposeLocationId;
            op.splitQty = splitQty;
            op.resolvedLot = resolvedLot;
            op.productName = productName;
            op.splitUnitCode = splitUnitCode;
            op.productDefinition = product;
            InventoryItemModel srcInv = this.inventoryItemQueryService.getInventoryItemById(sourceInventoryId);
            op.supplierId = line.getSupplierId();
            op.sourceInventoryProductionDate = null;
            op.sourceInventoryExpirationDate = null;
            if (srcInv != null) {
                if (op.supplierId == null) {
                    op.supplierId = srcInv.getSupplierId();
                }
                op.sourceInventoryProductionDate = srcInv.getProductionDate();
                op.sourceInventoryExpirationDate = srcInv.getExpirationDate();
            }
            if (op.supplierId == null && head != null) {
                op.supplierId = head.getSupplierId();
            }
            destIncrs.add(op);

            if (StringUtils.isNotEmpty(line.getTraceNo())) {
                transferLines.addAll(traceNumberSupplyAgreeTraceOrchestrator.buildTransferLinesFromTraceNoString(
                    line.getTraceNo(),
                    line.getItemTable().trim(),
                    line.getItemId(),
                    resolvedLot,
                    splitQty,
                    splitUnitCode,
                    ProductDefinitionDisplaySupport.toUnitConversionSource(product)));
            }
        }

        // —— 源药房按库存行 id 合并扣减（先扣后加，避免先加后扣窗口）。——
        try {
            this.inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, decreaseMerged);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "源药房库存扣减失败：" + ex.getMessage());
        }

        // —— 目的药库逐行入账；命中既有行则加量，否则按四元组新建新库存项。——
        for (ReturnToWarehouseDestIncr op : destIncrs) {
            SupplyRequestModel line = op.line;
            if (op.supplierId == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "源药房库存行缺少供应商标识，无法在目的药库新建账面");
            }
            line.setSupplierId(op.supplierId);
            InventoryMatchKey dstKey = new InventoryMatchKey();
            dstKey.setSupplierId(op.supplierId);
            dstKey.setLotNumber(op.resolvedLot);
            dstKey.setLocationId(op.purposeLocationId);
            dstKey.setItemTable(line.getItemTable());
            dstKey.setItemId(line.getItemId());
            Long destInventoryId = this.inventoryItemQueryService.findFirstIdByInventoryMatchKey(dstKey).orElse(null);
            try {
                if (destInventoryId != null) {
                    // 目的药库已有同四元组库存行：加量
                    IncreaseQuantityCommand incCmd =
                        this.inboundReceiptMapper.toIncreaseQuantityCommand(destInventoryId, op.splitQty, operatorId);
                    this.inventoryItemDomainService.increaseQuantity(incCmd);
                } else {
                    // 目的药库无匹配行：按源行快照新建库存项
                    CreateInventoryItemCommand createCmd = this.inboundReceiptMapper.toCreateInventoryItemCommand(
                        line,
                        op.purposeLocationId,
                        op.productName,
                        op.resolvedLot,
                        op.splitQty,
                        op.splitUnitCode,
                        operatorId,
                        op.productDefinition);
                    if (createCmd.getItem() != null) {
                        if (createCmd.getItem().getProductionDate() == null
                            && op.sourceInventoryProductionDate != null) {
                            createCmd.getItem().setProductionDate(op.sourceInventoryProductionDate);
                        }
                        if (createCmd.getItem().getExpirationDate() == null
                            && op.sourceInventoryExpirationDate != null) {
                            createCmd.getItem().setExpirationDate(op.sourceInventoryExpirationDate);
                        }
                    }
                    this.inventoryItemDomainService.createInventoryItem(createCmd);
                }
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "目的药库库存增加失败：" + ex.getMessage());
            }
        }

        transferPairCmd.setLines(transferLines);
        if (transferLines != null && !transferLines.isEmpty()) {
            try {
                this.traceNumberSupplyAgreeTraceOrchestrator.recordTransferPair(transferPairCmd);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "追溯码出库记录失败：" + ex.getMessage());
            }
        }

        // —— 对侧退库入库壳单须落库，失败则整单回滚（库存已在上方源减/目增完成）。——
        Long shellApplicantId = head.getApplicantId();
        if (shellApplicantId == null) {
            shellApplicantId = this.requireWarehousePreparerPractitionerId(
                ErrorCodeConstants.PHA_RTW_101,
                "用户未登录",
                "当前用户未绑定医务人员档案，无法生成退库入库壳单制单人");
        }
        String generatedInboundBusNo;
        try {
            CreateReturnCommand shellCmd = this.returnToWarehouseMapper.toCreateReturnCommandForShellCounterpartyReturnInbound(
                agreedLines, busNoTrim, operatorId, shellApplicantId);
            generatedInboundBusNo = this.supplyRequestDomainService.createReturn(shellCmd);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "同步生成退库入库壳单失败：" + ex.getMessage());
        }

        // Asm：映射同意成功响应（含壳单号）
        return this.returnToWarehouseMapper.toAgreeReturnToWarehouseResponse(
            busNoTrim, SupplyStatus.AGREE.getCode(), generatedInboundBusNo);
    }

    /**
     * 驳回单据。
     *
     * @param request 须含 busNo 与驳回原因
     * @return 驳回成功标志
     */
    public RejectReturnToWarehouseResponse rejectReturnToWarehouse(RejectReturnToWarehouseRequest request) {
        // Gate：busNo 与驳回原因不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getRejectReason())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "驳回原因不能为空");
        }
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RTW_101, "用户未登录");
        try {
            // Cmd：领域驳回，状态终态为 REJECT
            this.supplyRequestDomainService.rejectRequest(
                this.returnToWarehouseMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_RTW_003);
        }
        // Asm：映射驳回成功响应
        return this.returnToWarehouseMapper.toRejectReturnToWarehouseResponse(true);
    }

    /**
     * 删除单据（仅草稿/可删态）。
     *
     * @param request 须含 busNo
     * @return 删除成功标志
     */
    public RemoveReturnToWarehouseResponse removeReturnToWarehouse(RemoveReturnToWarehouseRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RTW_101, "用户未登录");
        try {
            // Cmd：领域软删整单（仅草稿/可删态）
            this.supplyRequestDomainService.removeByBusNo(
                this.returnToWarehouseMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_RTW_005);
        }
        // Asm：映射删除成功响应
        return this.returnToWarehouseMapper.toRemoveReturnToWarehouseResponse(true);
    }

    /**
     * 分页查询药房退库选品（源药房库存批号级，IF-PHA-RTW-11）。
     *
     * @param request 含源药房、物品表与分页参数
     * @return 选品行与分页元数据
     */
    public PageProductsForReturnToWarehouseResponse pageProductsForReturnToWarehouse(
        PageProductsForReturnToWarehouseRequest request) {
        // Gate：分页窗口、机构与源药房门禁
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.PHA_RTW_001, "分页参数不能为空");
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "sourceLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RTW_101, "机构上下文缺失");
        Set<Long> allowedPharmacies = this.resolveGatedPharmacyLocationIds();
        this.assertPharmacyLocationAllowed(request.getSourceLocationId(), allowedPharmacies);
        this.assertProductTableNameWhitelistForReturnToWarehouseSelection(request.getProductTableName());
        // Pre：组装源药房库存分页查询（物品表白名单），再批量拼产品定义、批号维进价与目录槽
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getSourceLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        // —— 前台可选：true 仅返回选品库位账面量>0 的批号行；false/未传不过滤数量。——
        invQuery.setPositiveQuantityOnly(request.getPositiveQuantityOnly());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        try {
            // Cmd：查询可用库存行
            Page<InventoryItemModel> page = this.inventoryItemProductSelectionPageLoader.page(invQuery, pageable);
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
                    products = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
                } catch (RuntimeException ex) {
                    log.warn("batchGetProductByIds failed for return-to-warehouse selection", ex);
                }
            }
            String itemTable = request.getProductTableName().trim();
            LinkedHashSet<Long> pageItemIds = new LinkedHashSet<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv != null && inv.getItemId() != null) {
                    pageItemIds.add(inv.getItemId());
                }
            }
            // —— 选品为批号行级展开：可用量按 inventoryItemId 回填，与 assert 行级键一致。——
            Map<Long, BigDecimal> availableQtyByInventoryItemId = new HashMap<>();
            if (!pageItemIds.isEmpty()) {
                List<InventoryCatalogAvailabilityRowModel> availRows =
                    this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                        itemTable, new ArrayList<>(pageItemIds), List.of(request.getSourceLocationId()));
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
                ProductDefinitionItemDto dto =
                    this.returnToWarehouseMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
                if (inv.getId() != null) {
                    BigDecimal avail = availableQtyByInventoryItemId.get(inv.getId());
                    // 无可用量行时展示 0，避免 null 误导前端可退库
                    if (avail == null) {
                        avail = BigDecimal.ZERO;
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
                    // —— 库存流转选品槽锚仅 LOT_NUMBER_COST；无批号价不灌目录 PURCHASE。——
                    ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                        bands, pr, lotCostUnit);
                }
                records.add(dto);
            }
            // Asm：映射选品分页（B.10.11：分页元数据取自同一 Spring Page）
            return PagedResultBuilder.build(new PageProductsForReturnToWarehouseResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForReturnToWarehouse failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_102, "退库选品查询失败");
        }
    }

    /**
     * 分页查询可关联药房入库单（已同意且仍有可退余量，IF-PHA-RTW-12）。
     *
     * @param request 须含 {@code sourceLocationId} 与分页参数
     * @return 按 {@code bus_no} 聚合的入库单候选行
     */
    public PageLinkableReturnSourceDocumentsResponse pageLinkableReturnSourceDocuments(
        PageLinkableReturnSourceDocumentsRequest request) {
        // Gate：来源药房与分页窗口
        if (request == null || request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "sourceLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.PHA_RTW_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RTW_101, "机构上下文缺失");
        Set<Long> allowedPharmacies = this.resolveGatedPharmacyLocationIds();

        // Pre：组装已同意入库单聚合列表查询
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.PHARMACY_STOCK_IN.getCode());
        listQuery.setStatusEnum(SupplyStatus.AGREE.getCode());
        // Pre：sourceLocationId=0 表示当前用户可见的全部药房（与 pageReturnToWarehouses / 退货关联进货单同构）
        if (Long.valueOf(InventoryPharmacyConstants.ALL_PHARMACIES_LIST_FILTER_LOCATION_ID)
            .equals(request.getSourceLocationId())) {
            if (allowedPharmacies.isEmpty()) {
                listQuery.setPurposeLocationIds(
                    List.of(InventoryPharmacyConstants.LIST_FILTER_NO_VISIBLE_PHARMACY_PLACEHOLDER_ID));
            } else {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowedPharmacies));
            }
        } else {
            this.assertPharmacyLocationAllowed(request.getSourceLocationId(), allowedPharmacies);
            listQuery.setPurposeLocationId(request.getSourceLocationId());
        }
        PageQueryBinder.bind(request, listQuery);
        try {
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<LinkablePharmacyStockInListRowDto> filtered = new ArrayList<>();
            // Pre：按 busNo 聚合并筛掉无可退余量的入库单
            for (String busNo : busNos) {
                if (StringUtils.isEmpty(busNo)) {
                    continue;
                }
                LinkablePharmacyStockInListRowDto row = this.buildLinkablePharmacyStockInListRow(
                    busNo.trim(), lines, request.getSourceLocationId());
                if (row == null) {
                    continue;
                }
                if (!Boolean.TRUE.equals(row.getReturnable())) {
                    // 可退余量已耗尽的单据不出现在候选列表
                    continue;
                }
                filtered.add(row);
            }
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Pre：批量解析制单人/药房/供应商展示名（对偶 IF-WHS-RT-12）
            this.enrichLinkablePharmacyStockInDisplayNames(filtered);
            // Asm：映射分页响应
            return this.returnToWarehouseMapper.toPageLinkablePharmacyStockInsAggregatedResponse(
                mergedTotal, pageNo, pageSize, filtered);
        } catch (RuntimeException ex) {
            log.warn("pageLinkableReturnSourceDocuments failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_102, "可关联入库单查询失败");
        }
    }

    /**
     * 按药房入库单号拉取退库草稿预填（IF-PHA-RTW-13，对偶 IF-WHS-RT-13）。
     *
     * @param request 须含 {@code busNo}；可选 {@code sourceLocationId}；改单时可传 {@code excludeReturnBusNo}
     * @return 退库表单预填（含可退余量与源药房可用量）
     */
    public GetLinkablePharmacyStockInDetailResponse getLinkablePharmacyStockInDetail(
        GetLinkablePharmacyStockInDetailRequest request) {
        // Gate：busNo 与机构/药房门禁
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "busNo 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RTW_101, "机构上下文缺失");
        Set<Long> allowedPharmacies = this.resolveGatedPharmacyLocationIds();
        String busNoTrim = request.getBusNo().trim();
        this.validateOriginalPharmacyStockInForReturnToWarehouse(busNoTrim, request.getSourceLocationId());

        List<SupplyRequestModel> stockInLines = this.loadStockInLinesByBusNo(busNoTrim);
        if (stockInLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_002, "来源入库单不存在");
        }
        SupplyRequestModel head = stockInLines.get(0);
        Long stockInPharmacyId = head.getPurposeLocationId();
        if (stockInPharmacyId == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_002, "入库单缺少目的药房，无法生成退库预填");
        }
        this.assertPharmacyLocationAllowed(stockInPharmacyId, allowedPharmacies);
        if (request.getSourceLocationId() != null) {
            if (!request.getSourceLocationId().equals(stockInPharmacyId)) {
                // 请求源药房与入库单目的药房不一致，禁止跨药房引用
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_002, "入库单目的药房与请求不一致");
            }
            this.assertPharmacyLocationAllowed(request.getSourceLocationId(), allowedPharmacies);
        }
        List<SupplyRequestModel> returnsReferencingStockIn = this.loadReturnsReferencingStockIn(busNoTrim);
        String excludeReturnBusNo = request.getExcludeReturnBusNo();
        // Pre：按行键计算可退余量、已退占用与源药房可用量
        Map<String, BigDecimal> returnableByKey = ReturnToWarehouseStockInLineHelper.computeReturnableQuantityByLineKey(
            stockInLines, returnsReferencingStockIn, excludeReturnBusNo);
        Map<String, BigDecimal> stockInByKey =
            ReturnToWarehouseStockInLineHelper.sumSplitQuantitiesByLineKey(stockInLines);
        Map<String, BigDecimal> returnedByKey = new HashMap<>();
        for (Map.Entry<String, BigDecimal> stockInEntry : stockInByKey.entrySet()) {
            BigDecimal returnable = returnableByKey.getOrDefault(stockInEntry.getKey(), BigDecimal.ZERO);
            BigDecimal returned = stockInEntry.getValue().subtract(returnable);
            if (returned.compareTo(BigDecimal.ZERO) < 0) {
                returned = BigDecimal.ZERO;
            }
            returnedByKey.put(stockInEntry.getKey(), returned);
        }
        Map<String, BigDecimal> availableByKey =
            this.loadSourceAvailableQuantityByLineKey(stockInPharmacyId, stockInLines);

        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : stockInLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> products = new HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                products = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds degraded for linkable pharmacy stock in detail", ex);
            }
        }
        // Asm：映射退库预填响应
        return this.returnToWarehouseMapper.toGetLinkablePharmacyStockInDetailResponse(
            stockInLines, returnableByKey, returnedByKey, availableByKey, products);
    }

    /**
     * 校验 original_bus_no 指向已同意的药房入库单且与源药房 location 匹配（PHA_RTW_002）。
     *
     * @param originalBusNo   来源入库 bus_no
     * @param sourcePharmacyId 源药房 location
     * @throws BusinessException 类型/状态/location 不匹配
     */
    private void validateOriginalPharmacyStockInForReturnToWarehouse(String originalBusNo, Long sourcePharmacyId) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> inboundLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (inboundLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_002, "来源入库单不存在");
        }
        boolean matchedPharmacy = false;
        for (SupplyRequestModel line : inboundLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_STOCK_IN.getCode().equals(line.getTypeEnum())) {
                // original_bus_no 须指向药房入库单，禁止挂接其它供应类型
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_002, "来源单据类型不是药房入库单");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                // 未同意入库单不可作为退库来源
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_002, "来源入库单未同意，不可引用");
            }
            if (Objects.equals(sourcePharmacyId, line.getPurposeLocationId())) {
                matchedPharmacy = true;
            }
        }
        if (!matchedPharmacy && sourcePharmacyId != null) {
            // 显式传入源药房时须与入库单目的药房匹配
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_002, "来源入库单与当前源药房不匹配");
        }
    }

    /**
     * 按 bus_no 加载供应请求行（入库单明细）。
     *
     * @param stockInBusNo 入库单 bus_no
     * @return 供应行列表
     */
    private List<SupplyRequestModel> loadStockInLinesByBusNo(String stockInBusNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(stockInBusNo);
        return this.supplyRequestQueryService.getSupplyByBusNo(q);
    }

    /**
     * 加载引用同一 original_bus_no 的退库/关联供应行。
     *
     * @param stockInBusNo 来源入库 bus_no
     * @return 引用行列表
     */
    private List<SupplyRequestModel> loadReturnsReferencingStockIn(String stockInBusNo) {
        SupplyByOriginalBusNoQuery q = new SupplyByOriginalBusNoQuery();
        q.setOriginalBusNo(stockInBusNo);
        return this.supplyRequestQueryService.getSupplyByOriginalBusNo(q);
    }

    /**
     * 关联入库单时：草稿退库行须绑定有效入库单且不超过可退数量（含 excludeReturnBusNo）。
     *
     * <p>未传 {@code originalBusNo} 时直接放行，由源药房可用库存门禁约束自由选品明细。</p>
     *
     * @param originalBusNo       来源入库 bus_no；空表示自由选品
     * @param sourceLocationId    源 location
     * @param draftLines          草稿行
     * @param excludeReturnBusNo  排除的退库单号（编辑时）
     * @throws BusinessException PHA_RTW_001/002
     */
    private void assertReturnLinesBoundToStockInAndWithinReturnable(
        String originalBusNo,
        Long sourceLocationId,
        List<ReturnToWarehouseStockInLineHelper.DraftReturnLine> draftLines,
        String excludeReturnBusNo) {
        // —— 未关联入库单：允许自由选品，跳过入库可退余量绑定。——
        if (StringUtils.isEmpty(originalBusNo)) {
            return;
        }
        this.validateOriginalPharmacyStockInForReturnToWarehouse(originalBusNo.trim(), sourceLocationId);
        List<SupplyRequestModel> stockInLines = this.loadStockInLinesByBusNo(originalBusNo.trim());
        List<SupplyRequestModel> returnsReferencingStockIn = this.loadReturnsReferencingStockIn(originalBusNo.trim());
        ReturnToWarehouseStockInLineHelper.assertDraftLinesWithinStockInReturnable(
            stockInLines,
            returnsReferencingStockIn,
            draftLines,
            excludeReturnBusNo,
            ErrorCodeConstants.PHA_RTW_001);
    }

    /**
     * 退库选品 {@code productTableName} 白名单：仅药品/耗材定义表。
     *
     * @param raw 请求表名
     * @throws BusinessException 空或非法表名
     */
    private void assertProductTableNameWhitelistForReturnToWarehouseSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "productTableName 非法");
        }
    }

    /**
     * 退库明细 DTO → 可退余量校验用 DraftReturnLine。
     *
     * @param lines 退库明细 DTO
     * @return DraftReturnLine 列表
     */
    private List<ReturnToWarehouseStockInLineHelper.DraftReturnLine> toDraftReturnLinesFromReturnLineDtos(
        List<ReturnToWarehouseLineDto> lines) {
        List<ReturnToWarehouseStockInLineHelper.DraftReturnLine> draftLines = new ArrayList<>();
        if (lines == null) {
            return draftLines;
        }
        for (ReturnToWarehouseLineDto line : lines) {
            if (line == null) {
                continue;
            }
            draftLines.add(new ReturnToWarehouseStockInLineHelper.DraftReturnLine(
                line.getItemTable(),
                line.getItemId(),
                line.getItemQuantity(),
                line.getUnitCode(),
                line.getLotNumber()));
        }
        return draftLines;
    }

    /**
     * 供应行 → 可退余量校验用 DraftReturnLine。
     *
     * @param lines 供应行
     * @return DraftReturnLine 列表
     */
    private List<ReturnToWarehouseStockInLineHelper.DraftReturnLine> toDraftReturnLinesFromSupplyModels(
        List<SupplyRequestModel> lines) {
        List<ReturnToWarehouseStockInLineHelper.DraftReturnLine> draftLines = new ArrayList<>();
        if (lines == null) {
            return draftLines;
        }
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            draftLines.add(new ReturnToWarehouseStockInLineHelper.DraftReturnLine(
                line.getItemTable(),
                line.getItemId(),
                line.getItemQuantity(),
                line.getUnitCode(),
                line.getLotNumber()));
        }
        return draftLines;
    }

    /**
     * 供应行 → 退库明细 DTO 列表（展示/预填用）。
     *
     * @param lines 供应行
     * @return 退库行 DTO
     */
    private List<ReturnToWarehouseLineDto> toReturnLineDtosFromSupplyModels(List<SupplyRequestModel> lines) {
        List<ReturnToWarehouseLineDto> dtos = new ArrayList<>();
        if (lines == null) {
            return dtos;
        }
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            ReturnToWarehouseLineDto dto = new ReturnToWarehouseLineDto();
            dto.setItemTable(line.getItemTable());
            dto.setItemId(line.getItemId());
            dto.setItemQuantity(line.getItemQuantity());
            dto.setUnitCode(line.getUnitCode());
            dto.setLotNumber(line.getLotNumber());
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * 按入库行 composite key 批量查询源药房账面可用量。
     *
     * @param sourceLocationId 源药房 location
     * @param stockInLines     入库明细行
     * @return lineKey → 可用拆零量
     */
    private Map<String, BigDecimal> loadSourceAvailableQuantityByLineKey(
        Long sourceLocationId,
        List<SupplyRequestModel> stockInLines) {
        Map<String, BigDecimal> availableByKey = new HashMap<>();
        if (sourceLocationId == null || stockInLines == null || stockInLines.isEmpty()) {
            return availableByKey;
        }
        Map<String, Map<Long, Map<String, BigDecimal>>> wantedByLotByTable = new HashMap<>();
        for (SupplyRequestModel line : stockInLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            String lotKeyPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
            wantedByLotByTable
                .computeIfAbsent(table, t -> new HashMap<>())
                .computeIfAbsent(itemId, i -> new HashMap<>())
                .merge(lotKeyPart, BigDecimal.ONE, BigDecimal::add);
        }
        for (Map.Entry<String, Map<Long, Map<String, BigDecimal>>> tableEntry : wantedByLotByTable.entrySet()) {
            String itemTable = tableEntry.getKey();
            Set<Long> itemIds = tableEntry.getValue().keySet();
            if (itemIds.isEmpty()) {
                continue;
            }
            List<InventoryCatalogAvailabilityRowModel> rows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    itemTable, new ArrayList<>(itemIds), List.of(sourceLocationId));
            Map<Long, Map<String, BigDecimal>> availableByItemLot = new HashMap<>();
            for (InventoryCatalogAvailabilityRowModel row : rows) {
                if (row == null || row.getItemId() == null || row.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(row.getLocationId(), sourceLocationId)) {
                    continue;
                }
                String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(row.getLotNumber());
                availableByItemLot
                    .computeIfAbsent(row.getItemId(), k -> new HashMap<>())
                    .merge(normLot, row.getQuantity(), BigDecimal::add);
            }
            for (SupplyRequestModel line : stockInLines) {
                if (line == null || !itemTable.equals(StringUtils.trimToEmpty(line.getItemTable()))) {
                    continue;
                }
                String key = ReturnToWarehouseStockInLineHelper.buildStockInLineKey(
                    line.getItemTable(), line.getItemId(), line.getLotNumber());
                if (key.isEmpty()) {
                    continue;
                }
                Map<String, BigDecimal> lots = availableByItemLot.getOrDefault(line.getItemId(), Collections.emptyMap());
                String lotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
                availableByKey.put(key, lots.getOrDefault(lotKey, BigDecimal.ZERO));
            }
        }
        return availableByKey;
    }

    /**
     * 组装可关联药房入库单列表行（含是否仍有可退数量与合计金额）。
     *
     * @param stockInBusNo     入库 bus_no
     * @param pageLines        分页原始行
     * @param sourcePharmacyId 源药房（可选过滤）
     * @return 列表行；无有效入库行时 null
     */
    private LinkablePharmacyStockInListRowDto buildLinkablePharmacyStockInListRow(
        String stockInBusNo,
        List<SupplyRequestModel> pageLines,
        Long sourcePharmacyId) {
        List<SupplyRequestModel> stockInLines = new ArrayList<>();
        for (SupplyRequestModel line : pageLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                continue;
            }
            if (!stockInBusNo.equals(line.getBusNo().trim())) {
                continue;
            }
            stockInLines.add(line);
        }
        if (stockInLines.isEmpty()) {
            return null;
        }
        List<SupplyRequestModel> returnsReferencingStockIn = this.loadReturnsReferencingStockIn(stockInBusNo);
        Map<String, BigDecimal> returnableByKey = ReturnToWarehouseStockInLineHelper.computeReturnableQuantityByLineKey(
            stockInLines, returnsReferencingStockIn, null);
        boolean returnable = ReturnToWarehouseStockInLineHelper.stockInHasAnyReturnableLine(returnableByKey);
        SupplyRequestModel head = stockInLines.get(0);
        LinkablePharmacyStockInListRowDto row = new LinkablePharmacyStockInListRowDto();
        row.setBusNo(stockInBusNo);
        row.setPurposeLocationId(head.getPurposeLocationId());
        row.setSupplierId(head.getSupplierId());
        // —— 制单人取入库头行申请人，展示名由 enrichLinkablePharmacyStockInDisplayNames 回填。——
        row.setApplicantId(head.getApplicantId());
        row.setApprovalTime(head.getApprovalTime());
        // —— 选单「制单时间」优先业务发生时刻，其次申请时刻，最后回落审批时刻。——
        LocalDateTime applyTime = head.getOccurrenceTime();
        if (applyTime == null) {
            applyTime = head.getApplyTime();
        }
        if (applyTime == null) {
            applyTime = head.getApprovalTime();
        }
        row.setApplyTime(applyTime);
        row.setReturnable(Boolean.valueOf(returnable));
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SupplyRequestModel line : stockInLines) {
            if (line != null && line.getTotalPrice() != null) {
                totalAmount = totalAmount.add(line.getTotalPrice());
            }
        }
        row.setTotalAmount(totalAmount);
        return row;
    }

    /**
     * 为可关联药房入库单列表行批量填充制单人名、药房名、供应商名（IF-PHA-RTW-12）。
     *
     * @param rows 列表行
     */
    private void enrichLinkablePharmacyStockInDisplayNames(List<LinkablePharmacyStockInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> applicantIds = new HashSet<>();
        Set<Long> purposeLocationIds = new HashSet<>();
        Set<Long> supplierIds = new HashSet<>();
        for (LinkablePharmacyStockInListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            // 收集本页需解析的主键，避免逐行查主数据
            if (row.getApplicantId() != null) {
                applicantIds.add(row.getApplicantId());
            }
            if (row.getPurposeLocationId() != null) {
                purposeLocationIds.add(row.getPurposeLocationId());
            }
            if (row.getSupplierId() != null) {
                supplierIds.add(row.getSupplierId());
            }
        }
        Map<Long, String> practitionerNameById = this.listPractitionerNameResolver.resolveNamesByIds(applicantIds);
        Map<Long, String> purposeLocationNameById = this.listLocationNameResolver.resolveNamesByIds(purposeLocationIds);
        Map<Long, String> supplierNameById = new HashMap<>();
        for (Long supplierId : supplierIds) {
            // B.6.4：跨聚合读 SupplierQueryService，仅取展示名
            this.supplierQueryService.findSupplierNameIfPresent(supplierId)
                .ifPresent(name -> supplierNameById.put(supplierId, name));
        }
        for (LinkablePharmacyStockInListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getApplicantId() != null) {
                String creatorName = practitionerNameById.get(row.getApplicantId());
                if (creatorName != null) {
                    row.setCreatorName(creatorName);
                }
            }
            if (row.getPurposeLocationId() != null) {
                String purposeLocationName = purposeLocationNameById.get(row.getPurposeLocationId());
                if (purposeLocationName != null) {
                    row.setPurposeLocationName(purposeLocationName);
                }
            }
            if (row.getSupplierId() != null) {
                String supplierName = supplierNameById.get(row.getSupplierId());
                if (supplierName != null) {
                    row.setSupplierName(supplierName);
                }
            }
        }
    }

    /**
     * 草稿退库明细不得超过源药房库存可用量（按批号/无批号分别校验）。
     *
     * @param sourceLocationId 源药房 location
     * @param lines            退库明细
     * @throws BusinessException PHA_RTW_001 可用量不足
     */
    private void assertReturnToWarehouseDraftLinesWithinSourceAvailableInventory(
        Long sourceLocationId,
        List<ReturnToWarehouseLineDto> lines) {
        if (sourceLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> locationIds = List.of(sourceLocationId);
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (ReturnToWarehouseLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "退库明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "退库明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.PHA_RTW_001, "退库明细数量须为正数");
            String lotKeyPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
            if (lotKeyPart.isEmpty()) {
                // 无批号行：按物品汇总与源药房总可用量比较
                Map<Long, BigDecimal> perItem =
                    requestedWithoutLotByTable.computeIfAbsent(table, t -> new HashMap<>());
                perItem.merge(itemId, splitQty, BigDecimal::add);
            } else {
                // 有批号行：按归一化批号与对应可用量比较
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
            Map<Long, Map<String, BigDecimal>> wantedByItemLot =
                requestedByLotByTable.getOrDefault(itemTable, Collections.emptyMap());
            Map<Long, BigDecimal> wantedWithoutLot = requestedWithoutLotByTable.getOrDefault(itemTable, Collections.emptyMap());
            Set<Long> itemIds = new LinkedHashSet<>();
            itemIds.addAll(wantedByItemLot.keySet());
            itemIds.addAll(wantedWithoutLot.keySet());
            if (itemIds.isEmpty()) {
                continue;
            }
            List<InventoryCatalogAvailabilityRowModel> rows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    itemTable, new ArrayList<>(itemIds), locationIds);
            Map<Long, Map<String, BigDecimal>> availableByItemLot = new HashMap<>();
            Map<Long, BigDecimal> availableByItemId = new HashMap<>();
            for (InventoryCatalogAvailabilityRowModel row : rows) {
                if (row == null || row.getItemId() == null || row.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(row.getLocationId(), sourceLocationId)) {
                    continue;
                }
                Long itemId = row.getItemId();
                String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(row.getLotNumber());
                availableByItemLot
                    .computeIfAbsent(itemId, k -> new HashMap<>())
                    .merge(normLot, row.getQuantity(), BigDecimal::add);
                availableByItemId.merge(itemId, row.getQuantity(), BigDecimal::add);
            }
            for (Map.Entry<Long, Map<String, BigDecimal>> wantItem : wantedByItemLot.entrySet()) {
                Long itemId = wantItem.getKey();
                Map<String, BigDecimal> wantedLots = wantItem.getValue();
                Map<String, BigDecimal> availLots = availableByItemLot.getOrDefault(itemId, Collections.emptyMap());
                for (Map.Entry<String, BigDecimal> wantLot : wantedLots.entrySet()) {
                    BigDecimal requested = wantLot.getValue();
                    BigDecimal available = availLots.getOrDefault(wantLot.getKey(), BigDecimal.ZERO);
                    if (requested.compareTo(available) > 0) {
                        // 同批号请求量超过源药房账面可用量
                        throw new BusinessException(
                            ErrorCodeConstants.PHA_RTW_001,
                            "源药房该批号可用量不足，请减少退库数量或刷新库存后再试");
                    }
                }
            }
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    // 无批号汇总请求量超过源药房该物品总可用量
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_RTW_001,
                        "源药房可用量不足，请减少退库数量或刷新库存后再试");
                }
            }
        }
    }

    /**
     * 当前用户可操作的药房 location 门禁集合。
     *
     * @return gated location id 集合
     */
    private Set<Long> resolveGatedPharmacyLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActivePharmacies());
    }

    /**
     * 当前用户可操作的药库 location 门禁集合。
     *
     * @return gated 药库 location id 集合
     */
    private Set<Long> resolveGatedWarehouseLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    /**
     * 退库单源/目的 location 双端门禁（药房 + 药库）。
     *
     * @param sourceLocationId  源药房 location
     * @param purposeLocationId 目的药库 location
     * @throws BusinessException 不在管辖或形态/状态不符
     */
    private void assertReturnToWarehouseLocationGate(Long sourceLocationId, Long purposeLocationId) {
        Set<Long> pharmacies = this.resolveGatedPharmacyLocationIds();
        this.assertPharmacyLocationAllowed(sourceLocationId, pharmacies);
        Set<Long> warehouses = this.resolveGatedWarehouseLocationIds();
        this.assertWarehouseLocationAllowed(purposeLocationId, warehouses);
    }

    /**
     * 断言 location 在当前用户药房管辖且为启用药房形态。
     *
     * @param locationId 库位 id
     * @param allowed    门禁集合
     * @throws BusinessException PHA_RTW_001
     */
    private void assertPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.PHA_RTW_001, "药房位置不在当前用户可选药房范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "药房位置不在当前用户可选药房范围");
        }
        Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            // 源位置须为启用中的药房形态库位
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "源位置须为启用中的药房");
        }
    }

    /**
     * 断言 location 在当前用户药库管辖且为启用药库形态。
     *
     * @param locationId 库位 id
     * @param allowed    门禁集合
     * @throws BusinessException PHA_RTW_001
     */
    private void assertWarehouseLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.PHA_RTW_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "药库位置不在当前用户可选药库范围");
        }
        Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WAREHOUSE.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            // 目的位置须为启用中的药库形态库位
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "目的位置须为启用中的药库");
        }
    }

    /**
     * 从批量产品读模型解析供应行展示名称。
     *
     * @param line          供应行
     * @param productByKey  compositeKey → 产品读模型
     * @return 产品名称
     * @throws BusinessException PHA_RTW_104 产品缺失
     */
    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isEmpty(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_104, "产品定义缺失，无法解析展示名称");
        }
        return pr.getProductName().trim();
    }

    /**
     * 可选校验退库单 typeEnum 须为药房退库（PHARMACY_RETURN_TO_WAREHOUSE）。
     *
     * @param typeEnum 供应类型 sort
     * @throws BusinessException typeEnum 不匹配
     */
    private void validateOptionalReturnTypeEnum(Integer typeEnum) {
        if (typeEnum == null) {
            return;
        }
        int expectedSort = SupplyType.PHARMACY_RETURN_TO_WAREHOUSE.getSortOrder().intValue();
        if (typeEnum.intValue() != expectedSort) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "typeEnum 必须为药房退库单类型");
        }
    }

    /**
     * 可选校验供应商存在且为 ACTIVE。
     *
     * @param supplierId 供应商 id；null 跳过
     * @throws BusinessException 不存在或不可用
     */
    private void validateSupplierOptional(Long supplierId) {
        if (supplierId == null) {
            return;
        }
        try {
            SupplierDetailQueryModel detail = this.supplierQueryService.findSupplierById(supplierId);
            if (!SupplierStatus.ACTIVE.getCode().equals(detail.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "供应商不可用");
            }
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.SUPPLIER_NOT_FOUND.equals(ex.getErrorCode())) {
                // 供应商不存在时统一映射为退库参数错误
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "供应商不存在或不可用");
            }
            throw ex;
        }
    }

    /**
     * 断言供应行均为药房退库单类型（防 busNo 误操作其它单据）。
     *
     * @param lines 供应行
     * @throws BusinessException PHA_RTW_103
     */
    private void assertPharmacyReturnToWarehouse(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_RETURN_TO_WAREHOUSE.getCode().equals(line.getTypeEnum())) {
                // 防止按 busNo 误操作其它供应类型单据
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_103, "单据类型非药房退库单");
            }
        }
    }

    /**
     * busNo 非空 Gate。
     *
     * @param busNo 供应单号
     * @throws BusinessException busNo 为空
     */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "busNo 不能为空");
        }
    }

    /**
     * 退库明细各行 occurrenceTime 非空校验。
     *
     * @param lines 退库明细
     * @throws BusinessException 制单时间为空
     */
    private void assertReturnToWarehouseLinesOccurrencePresent(List<ReturnToWarehouseLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (ReturnToWarehouseLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_RTW_001, "退库明细制单时间不能为空");
            }
        }
    }

    /**
     * 退库明细追溯码长度宽限校验（含逗号分隔片段）。
     *
     * @param lines     退库明细
     * @param errorCode 错误码
     * @throws BusinessException 超长
     */
    private void assertReturnToWarehouseTraceNosWithinWideLimit(List<ReturnToWarehouseLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (ReturnToWarehouseLineDto line : lines) {
            if (line == null || StringUtils.isEmpty(line.getTraceNo())) {
                continue;
            }
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
     * 解析供应行源 location 类型 code（行 → 单头 → CABINET 默认）。
     *
     * @param line 明细行
     * @param head 单头
     * @return sourceTypeEnum code
     */
    private String resolveSourceLocationTypeCode(SupplyRequestModel line, SupplyRequestModel head) {
        if (line != null && StringUtils.isNotEmpty(line.getSourceTypeEnum())) {
            return line.getSourceTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotEmpty(head.getSourceTypeEnum())) {
            return head.getSourceTypeEnum().trim();
        }
        return com.openhis.health.domain.share.enums.SupplyRequestType.CABINET.getCode();
    }

    /**
     * 解析供应行目的 location 类型 code（行 → 单头 → 按 purposeLocationId 反查 → CABINET）。
     *
     * @param line 明细行
     * @param head 单头
     * @return purposeTypeEnum code
     */
    private String resolvePurposeLocationTypeCode(SupplyRequestModel line, SupplyRequestModel head) {
        if (line != null && StringUtils.isNotEmpty(line.getPurposeTypeEnum())) {
            return line.getPurposeTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotEmpty(head.getPurposeTypeEnum())) {
            return head.getPurposeTypeEnum().trim();
        }
        Long purposeLocationId = null;
        if (line != null && line.getPurposeLocationId() != null) {
            purposeLocationId = line.getPurposeLocationId();
        } else if (head != null && head.getPurposeLocationId() != null) {
            purposeLocationId = head.getPurposeLocationId();
        }
        if (purposeLocationId != null) {
            return this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
        }
        return com.openhis.health.domain.share.enums.SupplyRequestType.CABINET.getCode();
    }

    /**
     * 解析当前登录用户对应医务人员 id（制单人 Gate）。
     *
     * @param notLoggedInErrorCode  未登录错误码
     * @param notLoggedInMessage    未登录文案（保留签名兼容）
     * @param noPractitionerMessage 无医务人员文案
     * @return practitionerId
     * @throws BusinessException 未登录或无医务人员
     */
    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

    /**
     * 供应工作流领域异常 → 本场景统一错误码（参数类映射 defaultCode）。
     *
     * @param ex          原异常
     * @param defaultCode 目标错误码
     * @return 映射后异常；其它原样返回
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            // 领域参数错误映射为本场景统一错误码
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * agree 阶段领域异常 → PHA_RTW_104（重复 agree）或 PHA_RTW_003。
     *
     * @param ex 原异常
     * @return 映射后异常
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                // 重复 agree 与库存编排失败共用 PHA_RTW_104
                return new BusinessException(ErrorCodeConstants.PHA_RTW_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.PHA_RTW_003, msg);
        }
        return ex;
    }

    /**
     * 为药房退库单列表行批量填充供应商名、制单人名、审批人名（IF-PHA-RTW-02 侧栏卡片展示）。
     *
     * @param rows 当前页聚合列表行
     */
    private void enrichReturnToWarehouseListDisplayNames(List<ReturnToWarehouseListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> practitionerIds = new HashSet<>();
        for (ReturnToWarehouseListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getSupplierId() != null) {
                supplierIds.add(row.getSupplierId());
            }
            if (row.getApplicantId() != null) {
                practitionerIds.add(row.getApplicantId());
            }
            if (row.getApproverId() != null) {
                practitionerIds.add(row.getApproverId());
            }
        }
        Map<Long, String> supplierNameById = new HashMap<>();
        for (Long supplierId : supplierIds) {
            // B.6.4：跨聚合读 SupplierQueryService，仅取展示名
            this.supplierQueryService.findSupplierNameIfPresent(supplierId)
                .ifPresent(name -> supplierNameById.put(supplierId, name));
        }
        Map<Long, String> practitionerNameById =
            this.listPractitionerNameResolver.resolveNamesByIds(practitionerIds);
        for (ReturnToWarehouseListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getSupplierId() != null) {
                String supplierName = supplierNameById.get(row.getSupplierId());
                if (supplierName != null) {
                    row.setSupplierName(supplierName);
                }
            }
            if (row.getApplicantId() != null) {
                String creatorName = practitionerNameById.get(row.getApplicantId());
                if (creatorName != null) {
                    row.setCreatorName(creatorName);
                }
            }
            if (row.getApproverId() != null) {
                String approverName = practitionerNameById.get(row.getApproverId());
                if (approverName != null) {
                    row.setApproverName(approverName);
                }
            }
        }
    }

    /** 同意编排中目的药库入账计划的行内上下文（非对外 DTO）。 */
    private static final class ReturnToWarehouseDestIncr {
        /** 已同意的供应请求明细行。 */
        private SupplyRequestModel line;
        /** 目的药库库位主键。 */
        private Long purposeLocationId;
        /** 拆零退库数量。 */
        private BigDecimal splitQty;
        /** 归一化后写入库存的批号。 */
        private String resolvedLot;
        /** 产品展示名称（新建库存行用）。 */
        private String productName;
        /** 拆零单位编码。 */
        private String splitUnitCode;
        /** 产品读模型（含包装换算）。 */
        private ProductDefinitionDisplayDto productDefinition;
        /** 供应商标识（优先行、源库存、单头）。 */
        private Long supplierId;
        /** 源库存行生产日期（新建目的行时回填）。 */
        private java.time.LocalDateTime sourceInventoryProductionDate;
        /** 源库存行效期（新建目的行时回填）。 */
        private java.time.LocalDateTime sourceInventoryExpirationDate;
    }
}
