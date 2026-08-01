/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.transferin.service;

import com.openhis.health.application.shared.inventory.InventoryListPractitionerNameResolver;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.inventory.department.transferin.dto.TransferInDetailLineDto;
import com.openhis.health.application.inventory.department.transferin.dto.TransferInListRowDto;
import com.openhis.health.application.inventory.department.transferin.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.transferin.mapper.InventoryDepartmentTransferInMapper;
import com.openhis.health.application.inventory.department.transferin.request.*;
import com.openhis.health.application.inventory.department.transferin.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoBatchQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 科室调入单（{@link SupplyType#DEPT_TRANSFER_IN}）应用编排（只读台账侧 · IF-DEPT-TI）。
 *
 * <p>典型来源：科室药房平级 {@link SupplyType#DEPT_TRANSFER_OUT} agree 后自动生成壳单
 * （{@code originalBusNo} 指向调出单，初态 {@code AGREE}）。
 * 本服务<strong>不支持</strong>手工新建/改单/审批落账（{@link ErrorCodeConstants#DEPT_TI_002}）。
 * 库存变动仅在调出 agree 时完成一次（DM §6.1.8 · {@code createTransfer}）。</p>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} → {@code POST /api/app/inventoryDepartmentTransferIn/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDepartmentTransferInAppService {

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final InventoryDepartmentTransferInMapper transferInMapper;
    private final InventoryListPractitionerNameResolver inventoryListPractitionerNameResolver;

    /**
     * 加载科室调入页主数据（管辖药房/药库、枚举选项、药师等）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 下拉选项与字典项（目的科室药房、来源药库）
     */
    public LoadTransferInFormInitResponse loadTransferInFormInit(LoadTransferInFormInitRequest request) {
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEPT_TI_101, "机构上下文缺失");
        try {
            // Gate：机构会话上下文
            // Pre：装载管辖范围内启用药房/药库与药师角色人员
            // Asm：Mapper 映射页初始化响应
            List<Location> departmentPharmacies = locationQueryService.listAllActiveDepartmentPharmacies();
            List<Location> gatedDepartmentPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(departmentPharmacies);
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
                WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
            return transferInMapper.toLoadTransferInFormInitResponse(
                transferInMapper.toOptionDtoListFromLocations(gatedDepartmentPharmacies),
                transferInMapper.toOptionDtoListFromLocations(gatedWarehouses),
                transferInMapper.toOptionDtoListInboundItemTableTypes(),
                transferInMapper.toOptionDtoListSupplyStatuses(),
                transferInMapper.toOptionDtoListFromPractitioners(pharmacists),
                transferInMapper.toOptionDtoListFromPractitioners(warehouseKeepers));
        } catch (RuntimeException ex) {
            log.warn("loadTransferInFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_101, "科室调入页主数据加载失败");
        }
    }

    /**
     * 分页查询科室调入单列表（按 busNo 聚合；筛选目的科室药房）。
     *
     * @param request 含分页窗口、目的科室药房与状态筛选
     * @return 聚合后的列表行与分页元数据
     */
    public PageTransferInsResponse pageTransferIns(PageTransferInsRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "请求不能为空");
        }
        // Pre：组装列表查询与目的科室药房门禁
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEPT_TI_001, "分页参数不能为空");
        this.validateOptionalTransferInTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEPT_TI_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForPurposeGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_TRANSFER_IN.getCode());
        // —— purposeLocationId=0：多科室药房 IN；无可见科室药房时用占位 id 收窄为空页。——
        if (Long.valueOf(0L).equals(request.getPurposeLocationId())) {
            if (allowedDepartmentPharmacies.isEmpty()) {
                // 岗位下无可见科室药房：用占位 id 保证列表为空页而非全机构泄露
                listQuery.setPurposeLocationIds(List.of(-1L));
            } else {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowedDepartmentPharmacies));
            }
        } else {
            this.assertPurposeDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        if (request.getSourceLocationId() != null) {
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                // 非法状态码：拒绝查询，避免静默忽略筛选条件
                throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "statusEnum 非法");
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
            // Cmd：领域分页查询供应请求
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<TransferInListRowDto> records = this.transferInMapper.toTransferInListAggregated(busNos, lines);
            this.enrichTransferInListSourceLocationNames(records);
            this.enrichTransferInListStatusEnumText(records);
            // Pre：历史壳单可能未落 approverId，按 originalBusNo 回填调出审批人
            this.fillMissingApproverFromOriginalTransferOut(records);
            this.enrichTransferInListDisplayNames(records, lines);
            int pageNo = request.getPageNo();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合 busNo 行并映射分页响应
            return this.transferInMapper.toPageTransferInsResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageTransferIns failed (wkf_supply_request aggregate path)", ex);
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_102, "科室调入单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读单据详情（含明细行与展示增强）。
     *
     * @param request 含业务单号 busNo
     * @return 单头与明细
     */
    public GetTransferInDetailResponse getTransferInDetail(GetTransferInDetailRequest request) {
        // Gate：busNo 非空，否则无法定位供应请求聚合
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "busNo 不能为空");
        }
        // Pre：按 busNo 回读供应请求行
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            // 同号无行：按不存在处理，避免空壳响应误导前端
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_103, "科室调入单不存在");
        }
        this.assertDepartmentTransferIn(lines);
        // Pre：批量装载产品读模型
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
            log.warn("batchGetProductByIds degraded for transfer in detail", ex);
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
        GetTransferInDetailResponse res = transferInMapper.toGetTransferInDetailResponse(lines, hints);
        // Asm：补齐产品展示名与历史落库槽价（BR-PRICE-SRC-001）
        this.enrichTransferInDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichTransferInDetailLines(res.getLines());
        this.enrichTransferInDetailAvailableQuantity(res.getLines(), res.getSourceLocationId());
        this.enrichTransferInDetailPractitionerName(res);
        return res;
    }

    /**
     * 不支持手工新建调入执行单（须由调出 agree 生成壳单）。
     *
     * @param request 请求体（写路径统一拒绝，参数仅作契约占位）
     * @throws BusinessException {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#DEPT_TI_002}
     */
    public CreateTransferInResponse createTransferIn(CreateTransferInRequest request) {
        // Gate：调入壳单只读，拒绝一切手工新建
        return this.rejectManualTransferInWrite("新建");
    }

    /**
     * 不支持手工修订调入执行单。
     *
     * @param request 含 busNo 与明细
     * @throws BusinessException {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#DEPT_TI_002}
     */
    public UpdateTransferInResponse updateTransferIn(UpdateTransferInRequest request) {
        // Gate：调入壳单只读，拒绝手工修订
        return this.rejectManualTransferInWrite("修订");
    }

    /**
     * 调入壳单已终态同意，禁止提交审批流。
     *
     * @param request 含 busNo
     * @throws BusinessException {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#DEPT_TI_002}
     */
    public SubmitTransferInApprovalResponse submitTransferInApproval(SubmitTransferInApprovalRequest request) {
        // Gate：壳单已 AGREE，无独立审批链
        return this.rejectManualTransferInWrite("提交审批");
    }

    /**
     * 调入壳单已终态同意，禁止撤回。
     *
     * @param request 含 busNo
     * @throws BusinessException {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#DEPT_TI_002}
     */
    public WithdrawTransferInApprovalResponse withdrawTransferInApproval(WithdrawTransferInApprovalRequest request) {
        // Gate：壳单已 AGREE，禁止撤回
        return this.rejectManualTransferInWrite("撤回审批");
    }

    /**
     * 调入壳单库存已在调出 agree 时落账，本接口禁止再次审批。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#DEPT_TI_002}
     */
    public AgreeTransferInResponse agreeTransferIn(AgreeTransferInRequest request) {
        // Gate：库存已在调出 agree 落账，禁止二次同意
        return this.rejectManualTransferInWrite("审批同意");
    }

    /**
     * 系统调入壳单不允许驳回（无独立审批链）。
     *
     * @param request 含 busNo
     * @throws BusinessException {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#DEPT_TI_002}
     */
    public RejectTransferInResponse rejectTransferIn(RejectTransferInRequest request) {
        // Gate：系统壳单无驳回路径
        return this.rejectManualTransferInWrite("驳回");
    }

    /**
     * 系统调入壳单不允许删除（追溯绑定调出单）。
     *
     * @param request 含 busNo
     * @throws BusinessException {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#DEPT_TI_002}
     */
    public RemoveTransferInResponse removeTransferIn(RemoveTransferInRequest request) {
        // Gate：壳单须保留追溯链，禁止删除
        return this.rejectManualTransferInWrite("删除");
    }

    /**
     * 分页查询调入壳单选品（来源药库库存目录；只读台账侧展示用）。
     *
     * @param request 含来源药库、物品表与分页参数
     * @return 选品行与分页元数据
     */
    public PageProductsForTransferInResponse pageProductsForTransferIn(PageProductsForTransferInRequest request) {
        // Gate：分页窗口、机构与来源药库门禁
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEPT_TI_001, "分页参数不能为空");
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "sourceLocationId 不能为空");
        }
        // Pre：组装库存目录分页查询
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEPT_TI_101, "机构上下文缺失");
        Set<Long> allowedWarehouses = this.resolveGatedWarehouseLocationIdsForSourceGate();
        this.assertWarehouseSourceLocationAllowed(request.getSourceLocationId(), allowedWarehouses);
        this.assertProductTableNameWhitelistForTransferInSelection(request.getProductTableName());
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getSourceLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        // —— 前台可选：true 仅返回选品库位账面量>0 的批号行；false/未传不过滤数量。——
        invQuery.setPositiveQuantityOnly(request.getPositiveQuantityOnly());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        try {
            // Cmd：查询可用库存行
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
                    log.warn("batchGetProductByIds failed for transfer in selection", ex);
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
                        itemTable, pageItemIds, List.of(request.getSourceLocationId()));
                for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                    if (ar == null || ar.getItemId() == null || ar.getQuantity() == null) {
                        continue;
                    }
                    if (!Objects.equals(ar.getLocationId(), request.getSourceLocationId())) {
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
                ProductDefinitionItemDto dto = transferInMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
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
                    // 选品预览：批号成本价走当前 ACTIVE 目录（新开/选品场景，非历史单据槽价）
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
                        // 无批号价时回落采购包装锚，供单位槽展示换算
                        packageAnchor = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                            inv.getItemTable().trim(), inv.getItemId());
                    }
                    ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                        bands, pr, packageAnchor);
                }
                records.add(dto);
            }
            // Asm：映射选品分页响应
            return PagedResultBuilder.build(new PageProductsForTransferInResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForTransferIn failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_102, "科室调入选品查询失败");
        }
    }

    /**
     * 列表行补全来源药库展示名（批量查 location，避免 N+1）。
     *
     * @param rows 列表聚合行
     */
    private void enrichTransferInListSourceLocationNames(List<TransferInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // —— 先收集本页全部来源库位 id，再批量查名称，避免列表行数次的 location 点查。——
        Set<Long> sourceIds = new HashSet<>();
        for (TransferInListRowDto row : rows) {
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
        for (TransferInListRowDto row : rows) {
            if (row == null || row.getSourceLocationId() == null) {
                continue;
            }
            row.setSourceLocationName(nameById.get(row.getSourceLocationId()));
        }
    }

    /**
     * 列表行补全状态中文展示（由持久化 {@code statusEnum} code 解析）。
     *
     * @param rows 列表聚合行
     */
    private void enrichTransferInListStatusEnumText(List<TransferInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (TransferInListRowDto row : rows) {
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
     * 历史调入壳单可能未落 {@code approverId}：按 {@code originalBusNo}（调出单）头行审批人回填侧栏展示。
     *
     * <p>仅影响本页列表出参，不写库；新生成壳单已在调出 agree 编排写入审批人。</p>
     *
     * @param rows 当前页聚合列表行
     */
    private void fillMissingApproverFromOriginalTransferOut(List<TransferInListRowDto> rows) {
        // Gate：空列表无需回填
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // Pre：收集缺审批人且带关联调出单号的壳单
        List<String> originalBusNos = new ArrayList<>();
        for (TransferInListRowDto row : rows) {
            if (row == null || row.getApproverId() != null) {
                // 已有审批人或空行：跳过
                continue;
            }
            if (StringUtils.isEmpty(row.getOriginalBusNo())) {
                // 无关联调出单号：无法回填
                continue;
            }
            originalBusNos.add(row.getOriginalBusNo().trim());
        }
        if (originalBusNos.isEmpty()) {
            return;
        }
        // Pre：批量回读调出单（B.6.4：经本聚合 SupplyRequestQueryService）
        SupplyByBusNoBatchQuery batchQuery = new SupplyByBusNoBatchQuery();
        batchQuery.setBusNoList(originalBusNos);
        List<SupplyRequestModel> originalLines;
        try {
            originalLines = this.supplyRequestQueryService.getSupplyByBusNoBatch(batchQuery);
        } catch (RuntimeException ex) {
            // 读库批读失败：降级为空审批人，不阻断列表
            log.warn("fillMissingApproverFromOriginalTransferOut degraded", ex);
            return;
        }
        // Pre：按调出单号取首行审批人（同号多行共享 approverId）
        Map<String, Long> approverByOriginalBusNo = new HashMap<>();
        for (SupplyRequestModel line : originalLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo()) || line.getApproverId() == null) {
                continue;
            }
            String busNo = line.getBusNo().trim();
            if (!approverByOriginalBusNo.containsKey(busNo)) {
                // 同号仅取首个非空审批人，避免后行覆盖
                approverByOriginalBusNo.put(busNo, line.getApproverId());
            }
        }
        // Asm：写回列表行 approverId，供后续 practitioner 名富化
        for (TransferInListRowDto row : rows) {
            if (row == null || row.getApproverId() != null || StringUtils.isEmpty(row.getOriginalBusNo())) {
                continue;
            }
            Long approverId = approverByOriginalBusNo.get(row.getOriginalBusNo().trim());
            if (approverId != null) {
                row.setApproverId(approverId);
            }
        }
    }

    /**
     * 科室调入列表行 enrich：批量解析制单人/审批人姓名（IF-DEPT-TI-02 侧栏卡片展示）。
     *
     * @param rows        当前页聚合列表行
     * @param detailLines 当前页供应请求明细（非空门禁；主键以 rows 为准）
     */
    private void enrichTransferInListDisplayNames(List<TransferInListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = new HashSet<>();
        for (TransferInListRowDto row : rows) {
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
        Map<Long, String> practitionerNameById = this.inventoryListPractitionerNameResolver.resolveNamesByIds(practitionerIds);
        for (TransferInListRowDto row : rows) {
            if (row == null) {
                continue;
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

    /**
     * 科室调入详情：补齐项目名称、规格、厂家与行单位展示名（与药库调出 IF-DEPT-TI-03 同构）。
     *
     * @param lines            详情明细行
     * @param products         产品读模型映射（表名+主键复合键）
     * @param terminologyMaps  选品术语（单位展示名等）
     */
    private void enrichTransferInDetailProductDisplay(
        List<TransferInDetailLineDto> lines,
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
        for (TransferInDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
            }
            // —— 壳单/手工单共用：前端表格「项目名称/规格/厂家」读这三列，不能只依赖 productCatalog。——
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
     * 详情行槽价：目录 {@code PURCHASE}、批号 {@code LOT_NUMBER_COST} 原样落槽；{@code STOCK}/{@code SPLIT} 仅包装锚换算。
     *
     * @param lines 详情行列表，可为 null 或空
     */
    private void enrichTransferInDetailLines(List<TransferInDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (TransferInDetailLineDto dto : lines) {
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
     * 详情回填源库可用量（拆零），与选品 {@code availableQuantity} 同口径。
     *
     * @param lines            详情明细行
     * @param sourceLocationId 调出源库位
     */
    private void enrichTransferInDetailAvailableQuantity(
        List<TransferInDetailLineDto> lines, Long sourceLocationId) {
        if (lines == null || lines.isEmpty() || sourceLocationId == null) {
            return;
        }
        // —— 按物品表分组批量取可用行，避免按明细行 N 次查库。——
        Map<String, List<Long>> itemIdsByTable = new HashMap<>();
        for (TransferInDetailLineDto line : lines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String table = line.getItemTable().trim();
            itemIdsByTable.computeIfAbsent(table, k -> new ArrayList<>()).add(line.getItemId());
        }
        Map<String, Map<Long, Map<String, BigDecimal>>> availableByTableItemLot = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : itemIdsByTable.entrySet()) {
            String table = entry.getKey();
            List<Long> itemIds = entry.getValue();
            if (itemIds.isEmpty()) {
                continue;
            }
            List<InventoryCatalogAvailabilityRowModel> availRows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    table, itemIds, List.of(sourceLocationId));
            Map<Long, Map<String, BigDecimal>> byItem =
                availableByTableItemLot.computeIfAbsent(table, k -> new HashMap<>());
            for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                if (ar == null || ar.getItemId() == null || ar.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(ar.getLocationId(), sourceLocationId)) {
                    continue;
                }
                String normLot =
                    InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(ar.getLotNumber());
                byItem.computeIfAbsent(ar.getItemId(), k -> new HashMap<>())
                    .merge(normLot, ar.getQuantity(), BigDecimal::add);
            }
        }
        for (TransferInDetailLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                line.setAvailableQuantity(BigDecimal.ZERO);
                continue;
            }
            String table = line.getItemTable().trim();
            Map<Long, Map<String, BigDecimal>> byItem = availableByTableItemLot.get(table);
            BigDecimal avail = BigDecimal.ZERO;
            if (byItem != null) {
                Map<String, BigDecimal> byLot = byItem.get(line.getItemId());
                if (byLot != null) {
                    String normLot =
                        InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
                    BigDecimal v = byLot.get(normLot);
                    if (v != null) {
                        avail = v;
                    }
                }
            }
            line.setAvailableQuantity(avail);
        }
    }

    /**
     * 详情头回填经手人姓名，供前端下拉 pin 展示。
     *
     * @param res 详情响应
     */
    private void enrichTransferInDetailPractitionerName(GetTransferInDetailResponse res) {
        if (res == null || res.getPractitionerId() == null) {
            return;
        }
        Map<Long, String> nameById =
            this.inventoryListPractitionerNameResolver.resolveNamesByIds(List.of(res.getPractitionerId()));
        String name = nameById.get(res.getPractitionerId());
        if (name != null) {
            res.setPractitionerName(name);
        }
    }

    /**
     * 解析岗位可见科室药房 id 集合（调入列表目的侧门控）。
     *
     * @return 启用中科室药房主键集合（已与管辖范围求交）
     */
    private Set<Long> resolveGatedDepartmentPharmacyLocationIdsForPurposeGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveDepartmentPharmacies());
    }

    /**
     * 解析岗位可见药库 id 集合（调入选品来源侧门控）。
     *
     * @return 启用中药库主键集合（已与管辖范围求交）
     */
    private Set<Long> resolveGatedWarehouseLocationIdsForSourceGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    /**
     * 断言给定药库主键在允许集合内且为启用中的药库形态（调入壳单来源侧，与药库调出对偶）。
     *
     * @param locationId 位置主键
     * @param allowed    岗位可见药房 id 集合
     */
    private void assertWarehouseSourceLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEPT_TI_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "药库位置不在当前用户可选药库范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WAREHOUSE.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            // 选品来源须为启用中的药库形态库位
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "来源位置须为启用中的药库");
        }
    }

    /**
     * 断言给定科室药房主键在允许集合内且为启用中的科室药房形态（调入目的库位）。
     *
     * @param locationId 位置主键
     * @param allowed    岗位可见科室药房 id 集合
     */
    private void assertPurposeDepartmentPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEPT_TI_001, "目的科室药房不在当前用户可选范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "目的科室药房不在当前用户可选范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.DEPARTMENT_PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            // 列表/详情目的侧须为启用中的科室药房形态库位
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "目的位置须为启用中的科室药房");
        }
    }

    /**
     * 科室调入执行单仅允许由调出 agree 生成壳单；拒绝一切手工写路径。
     *
     * <p>泛型返回仅为满足调用方 {@code return} 语法；方法体始终抛出 {@link BusinessException}，永不正常返回。</p>
     *
     * @param actionLabel 操作名称（如「新建」「审批同意」），用于提示文案
     * @param <T>         调用方期望的 Response 类型（占位，不会实际实例化）
     * @return 不返回；编译器占位
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TI_002}
     */
    private <T> T rejectManualTransferInWrite(String actionLabel) {
        String label = actionLabel;
        if (label == null) {
            label = "操作";
        }
        throw new BusinessException(ErrorCodeConstants.DEPT_TI_002,
            "科室调入执行单由调出单同意后系统自动生成，不支持手工" + label);
    }

    /**
     * 断言多行共享同一 {@code DEPT_TRANSFER_IN} 类型（详情/列表读侧一致性）。
     *
     * @param lines 供应请求行
     */
    private void assertDepartmentTransferIn(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_TRANSFER_IN.getCode().equals(line.getTypeEnum())) {
                // 防止按 busNo 误读其它供应类型
                throw new BusinessException(ErrorCodeConstants.DEPT_TI_103, "单据类型非科室调入单");
            }
        }
    }

    /**
     * 列表可选 type 收窄：若传入则须为科室调入类型持久化码。
     *
     * @param typeEnumCode 请求 typeEnum，可 blank
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TI_001} 非空白但无法解析或不是科室调入类型
     */
    private void validateOptionalTransferInTypeEnum(String typeEnumCode) {
        if (StringUtils.isEmpty(typeEnumCode)) {
            return;
        }
        SupplyType declared = EnumUtil.fromCode(SupplyType.class, typeEnumCode.trim());
        if (declared == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "typeEnum 非法");
        }
        if (declared != SupplyType.DEPT_TRANSFER_IN) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "typeEnum 必须为科室调入单类型");
        }
    }

    /**
     * 选品接口物品表白名单门控（药品定义表 / 设备定义表）。
     *
     * @param raw 请求 productTableName
     */
    private void assertProductTableNameWhitelistForTransferInSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TI_001, "productTableName 非法");
        }
    }
}
