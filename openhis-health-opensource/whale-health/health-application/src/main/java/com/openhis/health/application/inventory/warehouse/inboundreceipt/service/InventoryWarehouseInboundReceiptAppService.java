/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.service;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptDetailLineDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.TraceIdentityResolveFailItemDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.TraceIdentityResolveItemDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptLineDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptListRowDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderListRowDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.application.inventory.warehouse.purchaseorder.mapper.InventoryWarehousePurchaseOrderMapper;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.*;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.response.*;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.command.DeactivateActivePriceLinesCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SavePriceLineCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.support.ChargePricingTypeCodeResolver;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberIdentityQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.application.shared.tracenumber.TraceNumberBatchIdentityResolver;
import com.openhis.health.application.shared.tracenumber.TraceNumberSupplyAgreeTraceOrchestrator;
import com.openhis.health.application.shared.tracenumber.RecordTraceFromSupplyLineSpec;
import com.openhis.health.application.shared.tracenumber.TraceScanCountSupport;
import com.openhis.health.application.shared.practitioner.PractitionerRoleScopeLoader;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.inventory.warehouse.support.ProductDefinitionPurchasePageLoader;
import com.openhis.health.application.shared.pricing.CatalogReferencePriceSyncSupport;
import com.openhis.health.application.shared.pricing.LotNumberPurchasePriceConsistencyGate;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.domain.share.enums.SupplierStatus;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.application.shared.supplyrequest.SupplyRequestPricedWriteOrchestrator;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterInboundExtensionIn;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 药库进货单（{@link SupplyType#CABINET_INBOUND_RECEIPT}）应用编排。
 *
 * <p>审批链维护供应请求；<strong>同意</strong>后依次：领域落已同意态 → 入库发放（SupplyDelivery）→ 库存入账（InventoryItem），
 * 并可补批号维进价/售价、追溯码。撤回/驳回不涉及库存侧写。</p>
 * <p>列表/门禁以 {@code purpose_location_id}（目的药库）为锚点；类型/状态等枚举入参使用 {@code getCode()} 下推领域。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehouseInboundReceiptAppService {

    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestPricedWriteOrchestrator supplyRequestPricedWriteOrchestrator;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final SupplyDeliveryDomainService supplyDeliveryDomainService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionPurchasePageLoader productDefinitionPurchasePageLoader;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final InventoryWarehousePurchaseOrderMapper purchaseOrderMapper;
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final CatalogReferencePriceSyncSupport catalogReferencePriceSyncSupport;
    private final LotNumberPurchasePriceConsistencyGate lotNumberPurchasePriceConsistencyGate;
    private final TraceNumberSupplyAgreeTraceOrchestrator traceNumberSupplyAgreeTraceOrchestrator;
    private final TraceNumberQueryService traceNumberQueryService;
    /**
     * 加载库房单据页主数据（管辖药库、枚举选项、制单人员等）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 下拉选项与字典项
     */
    public LoadInboundReceiptFormInitResponse loadInboundReceiptFormInit(LoadInboundReceiptFormInitRequest request) {
        // —— 经手人/库管等仍按会话机构收窄；药房与药库位置经 CurrentManagedLocationIdsResolver 收窄（empty 时不收窄）。——
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        try {
            // Gate：机构会话上下文
            // Pre：装载管辖范围内启用位置与角色人员
            // Asm：Mapper 映射页初始化响应
            // —— 门控：管辖库位非空时与启用位置求交；管辖 empty 时不拼 location_id 谓词。——
            List<Location> pharmacies = locationQueryService.listAllActivePharmacies();
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(pharmacies);
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<SupplierListItemQueryModel> suppliers =
                supplierQueryService.listSuppliersByTypeAndActive(
                    com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode());
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
                WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
            return inboundReceiptMapper.toLoadInboundReceiptFormInitResponse(
                inboundReceiptMapper.toOptionDtoListFromLocations(gatedPharmacies),
                inboundReceiptMapper.toOptionDtoListFromLocations(gatedWarehouses),
                inboundReceiptMapper.toOptionDtoListFromSuppliers(suppliers),
                inboundReceiptMapper.toOptionDtoListFromPractitioners(pharmacists),
                inboundReceiptMapper.toOptionDtoListFromPractitioners(warehouseKeepers),
                inboundReceiptMapper.toOptionDtoListInboundItemTableTypes(),
                inboundReceiptMapper.toOptionDtoListSupplyStatuses());
        } catch (RuntimeException ex) {
            log.warn("loadInboundReceiptFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_IN_102, "进货页主数据加载失败");
        }
    }

    /**
     * 分页查询药库进货单列表（按 busNo 聚合展示）。
     *
     * @param request 查询条件；须含 {@code sourceLocationId}、{@code pageNo}、{@code pageSize}；可选 {@code statusEnum}、{@code typeEnum}（须为药库进货 code）、时段与关键字
     * @return 分页结果（{@code pageNo} 1-based，行内状态/类型等为持久化 code，B.10.11 / B.10.8.1）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 分页/位置参数非法、药库越权、{@code typeEnum} 非进货类型；
     *                            {@link ErrorCodeConstants#WHS_IN_101} 机构上下文缺失；{@link ErrorCodeConstants#WHS_IN_102} 列表聚合查询失败
     * @throws IllegalArgumentException {@code statusEnum} 非空但非法持久化 code 时由 {@link EnumUtil#fromCode} 抛出
     */
    public PageInboundReceiptsResponse pageInboundReceipts(PageInboundReceiptsRequest request) {
        // —— 门面入参硬校验：避免 null 穿透到领域查询构造，产生难以定位的 NPE 或空结果误判。——
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        // Pre：组装列表查询与库房门禁
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "sourceLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_IN_001, "分页参数不能为空");
        // —— 可选类型筛选项须解析为药库进货单，防止借列表接口窥探其它供应类型。——
        this.validateOptionalInboundReceiptTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.CABINET_INBOUND_RECEIPT.getCode());
        // Pre：sourceLocationId=0 表示当前用户可见的全部药库；否则单库且须在管辖门控内
        if (Long.valueOf(InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID)
            .equals(request.getSourceLocationId())) {
            if (allowed.isEmpty()) {
                // 无可见药库：占位 id 收窄为空页，避免无约束全表扫描
                listQuery.setSourceLocationIds(
                    List.of(InventoryWarehouseConstants.LIST_FILTER_NO_VISIBLE_WAREHOUSE_PLACEHOLDER_ID));
            } else {
                // 多药库 IN 查询：下推当前用户可见 id 集合
                listQuery.setSourceLocationIds(new ArrayList<>(allowed));
            }
        } else {
            // 单药库筛选（防横向越权）
            this.assertLocationAllowed(request.getSourceLocationId(), allowed);
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (request.getPurposeLocationId() != null) {
            // Pre：可选按目的库再收窄列表，须与源库同属当前用户可见药库集合
            if (Long.valueOf(InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID)
                .equals(request.getPurposeLocationId())) {
                if (allowed.isEmpty()) {
                    // 无可见药库：占位 id 收窄为空页
                    listQuery.setPurposeLocationIds(
                        List.of(InventoryWarehouseConstants.LIST_FILTER_NO_VISIBLE_WAREHOUSE_PLACEHOLDER_ID));
                } else {
                    // 多药库 IN 查询：下推当前用户可见 id 集合
                    listQuery.setPurposeLocationIds(new ArrayList<>(allowed));
                }
            } else {
                // 单目的库筛选（防横向越权）
                this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
                listQuery.setPurposeLocationId(request.getPurposeLocationId());
            }
        }
        // —— 状态筛选为可选：仅当传入非空白 code 时才写入查询对象，否则列表不按状态谓词收窄。——
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            // —— 入参与库表一致：SupplyStatus 持久化 getCode()，禁止排序号中转（B.10.8.1）。——
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        // —— 申请时刻窗：兼容历史请求字段 applyTimeFrom/To 与 AbstractPageQuery 的 startDate/endDate（B.10.11）。——
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
            List<InboundReceiptListRowDto> records = this.inboundReceiptMapper.toInboundReceiptListAggregated(busNos, lines);
            // —— 展示名依赖主数据查询，放在应用层批量解析，避免 Mapper 注入领域服务（B.2 / B.6.1）。——
            this.enrichInboundReceiptListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合 busNo 行并映射分页响应
            return this.inboundReceiptMapper.toPageInboundReceiptsAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageInboundReceipts failed (wkf_supply_request aggregate path)", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_IN_102, "进货单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读进货单详情（含明细行与展示增强）。
     *
     * @param request 请求；{@code busNo} 非空（首尾空白会被去除）
     * @return 详情；头行中 {@code typeEnum}/{@code statusEnum} 等为持久化 {@code getCode()} 字符串（B.10.8.1）；产品名由读模型批量解析，失败时降级为空名
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 单号缺失；
     *                            {@link ErrorCodeConstants#WHS_IN_103} 单据不存在或类型非药库进货单
     */
    public GetInboundReceiptDetailResponse getInboundReceiptDetail(GetInboundReceiptDetailRequest request) {
        // Gate：busNo 非空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
        // Pre：按 busNo 回读供应请求行
        // —— 按业务单号拉取同号全部供应请求行（写模型）：详情页与列表共用 wkf_supply_request 聚合视图。——
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "进货单不存在");
        }
        this.assertCabinetInboundReceipt(lines);

        // Pre：批量装载产品读模型
        // —— 按物品表+主键去重后一次性批量拉取产品定义，避免详情页对每行单独访问读模型（与 A.9.1「集合化查询」同向收敛）。——
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
            // —— 读模型偶发失败时降级为空映射：详情仍返回写模型字段，展示名/规格等由前端自行处理缺失态。——
            log.warn("batchGetProductByIds degraded for inbound receipt detail", ex);
            products = new HashMap<>();
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
        GetInboundReceiptDetailResponse res = inboundReceiptMapper.toGetInboundReceiptDetailResponse(lines, products);
        this.enrichInboundReceiptDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichInboundReceiptDetailLines(res.getLines());
        // Asm：Mapper 映射详情并增强展示字段
        return res;
    }

    /**
     * 进货详情：补齐行单位展示名与 catalog 单位槽 unitName（耗材等须走 UNIT_CODE 术语，B.10.8.1）。
     *
     * @param lines           详情明细行
     * @param products        批量产品读模型
     * @param terminologyMaps 单位/术语映射
     */
    private void enrichInboundReceiptDetailProductDisplay(
        List<InboundReceiptDetailLineDto> lines,
        Map<String, ProductDefinitionDisplayDto> products,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        if (lines == null || lines.isEmpty() || products == null || products.isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        for (InboundReceiptDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
            }
            dto.setUnitCodeDictText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, dto.getUnitCode(), pr));
            ProductDefinitionCatalogEditorHintsDto catalog = dto.getProductCatalog();
            if (catalog == null) {
                catalog = ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
                dto.setProductCatalog(catalog);
            } else if (catalog.getUnitBands() != null && !catalog.getUnitBands().isEmpty()) {
                this.productDefinitionDisplayTerminologyAssembler.applyInboundUnitBandDisplayNames(
                    catalog.getUnitBands(), pr, terminologyMaps);
            }
        }
    }

    /**
     * 详情行槽价：仅锚 {@code SupplyRequest} 持久化 {@code price}/{@code unitCode}，禁止查定价表当前价（**BR-PRICE-SRC-001**）。
     *
     * @param lines 详情明细列表，可为 null
     */
    private void enrichInboundReceiptDetailLines(List<InboundReceiptDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (InboundReceiptDetailLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat == null) {
                continue;
            }
            // —— 历史单据：槽价与 purchasePrice 均来自行持久化价，不回落目录。——
            ProductDefinitionCatalogUnitBandsAssembler.enrichWarehouseDetailCatalogFromPersistedSupplyLinePrice(
                cat, dto.getPrice(), dto.getUnitCode(), null);
        }
    }

    /**
     * 新建药库进货单草稿并落库供应请求。
     *
     * @param request 请求体；须含非空明细、源/目的位置；{@code originalBusNo} 可空（手工直建），非空须为已同意订货单；位置类型由后端按库位推导
     * @return 新单号与创建后状态（{@code statusEnum} 为 code，B.10.8.1）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 参数/位置/供应商不合法；{@link ErrorCodeConstants#WHS_IN_002} 来源订货单不可引用；
     *                            {@link ErrorCodeConstants#WHS_IN_101} 未登录或机构上下文缺失；Mapper 装配抛出 {@link IllegalArgumentException} 时包装为 {@code WHS_IN_001}
     */

    public CreateInboundReceiptResponse createInboundReceipt(CreateInboundReceiptRequest request) {
        // —— 入参形状：头字段 + 至少一行明细；源/目的库位必填（类型由 Mapper 按位置推导）。——
        // Gate：请求体、明细与库房/供应商门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        this.assertLocationAllowed(request.getSourceLocationId(), allowed);
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);

        // —— 业务门禁：供应商启用态；追溯码仅拦长度上限。个数/跨行重复改到提审（允许中途半扫保存）。——
        this.validateSupplierOptional(request.getSupplierId());
        this.assertInboundReceiptTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.WHS_IN_001);
        // Gate：同表同品同批号不得与已有批号进价冲突（与供应商无关）
        this.assertInboundLotPurchasePriceConsistency(request.getLines());

        // —— 订货引用可选：有 originalBusNo 则校验已同意订货单；空白允许手工直建选品。——
        if (StringUtils.isNotEmpty(request.getOriginalBusNo())) {
            this.validateOriginalPurchaseOrderForInboundReceipt(request.getOriginalBusNo().trim());
        }

        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "经手人不能为空");
        }
        this.assertInboundReceiptLinesOccurrencePresent(request.getLines());

        // Pre：解析操作人与制单人，装配领域 Create 命令
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");
        // —— 制单人（申请人/applicant_id）与会话用户绑定医务人员对齐，禁止沿用请求体代传（与列表按医务人员解析一致）。——
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_IN_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：组装 Create 命令并调用领域写服务
            com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand cmd =
                inboundReceiptMapper.toCreateInboundReceiptCommand(request, operatorId, applicantId);
            String busNo = supplyRequestPricedWriteOrchestrator.createInboundReceipt(cmd);
            // Asm：返回 busNo
            return inboundReceiptMapper.toCreateInboundReceiptResponse(busNo);
        } catch (IllegalArgumentException ex) {
            // —— Mapper/枚举解析失败：统一包装为参数非法，避免把 IllegalArgumentException 原样透出 HTTP 层。——
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 更新可编辑态进货单（全量明细覆盖，待审核态）。
     *
     * @param request 请求体；须含 {@code busNo}、{@code rowVersion}、非空明细及与新建相同的头字段约束
     * @return 单号与修订后状态（{@code statusEnum} 为 code）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 参数非法或位置越权；{@link ErrorCodeConstants#WHS_IN_103} 单据不存在或类型不符；
     *                            {@link ErrorCodeConstants#WHS_IN_004} 当前状态不可编辑；{@link ErrorCodeConstants#WHS_IN_101} 未登录或机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_IN_105} 并发冲突（乐观锁）；Mapper 装配 {@link IllegalArgumentException} 包装为 {@code WHS_IN_001}
     */

    public UpdateInboundReceiptResponse updateInboundReceipt(UpdateInboundReceiptRequest request) {
        // —— 与新建相同的头/明细非空约束，并额外要求 busNo + 乐观锁 rowVersion（待审核态全量替换语义）。——
        // Gate：busNo、明细与可编辑态校验
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "源位置与目标位置不能为空");
        }
        if (request.getRowVersion() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "rowVersion 不能为空");
        }

        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        this.assertLocationAllowed(request.getSourceLocationId(), allowed);
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);

        // —— 目标单据须存在、类型为药库进货，且 verifyAbleEdit 为真（非待审核等状态直接拒绝）。——
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "进货单不存在");
        }
        this.assertCabinetInboundReceipt(existing);

        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        SupplyRequestModel head = existing.get(0);
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            // —— 领域返回不可编辑：常见为已提交审批或已同意，禁止客户端强行覆盖明细。——
            throw new BusinessException(ErrorCodeConstants.WHS_IN_004, "当前状态不允许编辑进货单");
        }

        this.validateSupplierOptional(request.getSupplierId());
        // —— 修订同样只拦追溯码长度；半扫可保存，完整 Gate 在提审。——
        this.assertInboundReceiptTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.WHS_IN_004);
        // Gate：同表同品同批号不得与已有批号进价冲突（与供应商无关）
        this.assertInboundLotPurchasePriceConsistency(request.getLines());

        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "经手人不能为空");
        }
        this.assertInboundReceiptLinesOccurrencePresent(request.getLines());

        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_IN_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        // Pre：已有订货引用须保留且不可改；手工直建（快照空）继续无源保存
        String originalSnapshot = head.getOriginalBusNo();
        String originalRefForRevision = null;
        if (StringUtils.isNotEmpty(originalSnapshot)) {
            originalRefForRevision = originalSnapshot.trim();
            String requestOriginal = request.getOriginalBusNo();
            if (StringUtils.isNotEmpty(requestOriginal)
                && !originalRefForRevision.equals(requestOriginal.trim())) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "进货单来源订货单不可变更");
            }
            this.validateOriginalPurchaseOrderForInboundReceipt(originalRefForRevision, request.getBusNo());
        }
        try {
            // Cmd：同号全量对齐修订（领域仍走 createInboundReceipt 写路径）
            com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand cmd =
                inboundReceiptMapper.toCreateInboundReceiptCommandForUpdate(
                    request, operatorId, applicantId, originalRefForRevision);
            String busNo = supplyRequestPricedWriteOrchestrator.createInboundReceipt(cmd);
            // Asm：返回 busNo
            return inboundReceiptMapper.toUpdateInboundReceiptResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            // —— JPA 乐观锁冲突：提示用户刷新后重试，避免静默丢更新。——
            throw new BusinessException(ErrorCodeConstants.WHS_IN_105, "进货单已被并发修改，请刷新后重试", ex);
        }
    }

    /**
     * 提交进货单进入待审批态。
     *
     * <p>提审前对已落库明细做追溯码跨行去重与应扫个数 Gate（草稿保存允许半扫）。</p>
     *
     * @param request 请求；{@code busNo} 经 {@link #gateBusNo(String)} 校验
     * @return 是否成功及提交后头行状态 code（可能为空，见 Mapper 装配）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 请求或单号非法；{@link ErrorCodeConstants#WHS_IN_103} 单据不存在；
     *                            {@link ErrorCodeConstants#WHS_IN_003} 追溯码未扫齐/跨行重复或领域状态机拒绝；
     *                            {@link ErrorCodeConstants#WHS_IN_101} 未登录
     */

    public SubmitInboundReceiptApprovalResponse submitInboundReceiptApproval(SubmitInboundReceiptApprovalRequest request) {
        // Gate：busNo 与制单人员档案
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_IN_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        // —— 提审前追溯 Gate：半扫可先保存，提审时才要求有码行个数齐、跨行不重复。——
        SupplyByBusNoQuery submitTraceQuery = new SupplyByBusNoQuery();
        submitTraceQuery.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> submitLines = supplyRequestQueryService.getSupplyByBusNo(submitTraceQuery);
        if (submitLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "进货单不存在");
        }
        this.assertCabinetInboundReceipt(submitLines);
        this.assertInboundReceiptSubmitTraceGates(submitLines, ErrorCodeConstants.WHS_IN_003);
        // —— 提交审批仅驱动供应请求状态机，不涉及发放与库存；失败时映射为 WHS_IN_003 便于前端统一提示。——
        try {
            // Cmd：领域提交审批
            supplyRequestDomainService.submitApproval(
                inboundReceiptMapper.toSubmitApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_IN_003);
        }
        // Asm：映射提交响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return inboundReceiptMapper.toSubmitInboundReceiptApprovalResponse(st);
    }


    /**
     * 撤回审批（回到可编辑草稿态）。
     *
     * @param request 含业务单号 busNo
     * @return 撤回后的展示状态
     */
    public WithdrawInboundReceiptApprovalResponse withdrawInboundReceiptApproval(WithdrawInboundReceiptApprovalRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批
            supplyRequestDomainService.withdrawApproval(
                inboundReceiptMapper.toWithdrawApprovalCommand(request, currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_IN_003);
        }
        // Asm：映射撤回响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return inboundReceiptMapper.toWithdrawInboundReceiptApprovalResponse(st);
    }

    /**
     * 药库进货单同意编排：领域落已同意态 → 发放 → 批号维定价 → 库存入账 → 追溯 → 可选医保上报。
     *
     * <p>重复 agree、发放已完成等场景抛 {@link ErrorCodeConstants#WHS_IN_104}。</p>
     *
     * @param request 含 busNo；须未重复同意
     * @return 同意后 success 标识
     */
    public AgreeInboundReceiptResponse agreeInboundReceipt(AgreeInboundReceiptRequest request) {
        // Gate：busNo 与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");

        // Pre：同意前读校验——单据须存在、类型须为药库进货；读侧防重复同意（双保险，避免双入账）
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "进货单不存在");
        }
        this.assertCabinetInboundReceipt(beforeLines);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货单已同意，禁止重复同意");
        }

        // Cmd：领域状态机同意进货单（save 先于事件，B.14）
        try {
            supplyRequestDomainService.agreeRequest(inboundReceiptMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_105, "进货单已被并发修改，请刷新后重试", ex);
        }

        // —— 同持久化单元内 flush：保证后续「同意态」回读与发放/库存编排读到与 agree 一致的数据库快照。——
        this.supplyRequestDomainService.flushPendingChanges();

        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertCabinetInboundReceipt(agreedLines);
        // Gate：同意入账前再拦同批号异价（防绕过草稿保存）
        this.assertSupplyLinesLotPurchasePriceConsistency(agreedLines, ErrorCodeConstants.WHS_IN_104);
        // —— 二次校验：防止读侧延迟或异常导致在错误状态下继续入库侧写。——
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "同意后回读状态异常，中止入库编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货明细缺少持久化主键，无法完成入库");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货明细数量无效，无法入账");
            }
        }

        // —— 收集本单全部供应请求行主键，批量查询是否已有完成态发放（禁止循环内逐条 exists）。——
        List<Long> requestIdsForDeliveryGate = new ArrayList<>();
        for (SupplyRequestModel row : agreedLines) {
            if (row != null && row.getId() != null) {
                requestIdsForDeliveryGate.add(row.getId());
            }
        }
        // —— 批量查询完成态发放，防重复同意入账。——
        Set<Long> duplicateCompletedRequestIds =
            supplyDeliveryQueryService.findRequestIdsHavingCompletedDelivery(requestIdsForDeliveryGate);
        if (!duplicateCompletedRequestIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "供应请求行已存在完成态发放，禁止重复同意入账");
        }

        // —— 4. 入库发放：记录与供应请求行关联的入库事实，须在库存增减之前落库（编排顺序）。——
        try {
            // Cmd：入库发放落库
            com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand deliveryCmd =
                inboundReceiptMapper.toCreateSupplyDeliveryCommandForInbound(agreedLines, operatorId);
            if (deliveryCmd.resolveItems().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "入库发放明细为空，无法入账");
            }
            supplyDeliveryDomainService.createSupplyDelivery(deliveryCmd);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "入库发放落库失败：" + ex.getMessage());
        }

        // —— 5. 产品定义批量读：批号维定价换算与后续库存入账共用同一快照。——
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
                log.warn("batchGetProductByIds failed during inbound lot pricing assemble", ex);
            }
        }

        // —— 6. 批号维进价/售价补录 + 目录参考价回写：同批号进价同值跳过批号写；异价（门禁已过）先停用再写；
        // —— 无论批号是否跳过，均以本行包装进价回写 PURCHASE/SALE（下次选品默认；中草药 Herbal AOP 加价后再抬 SALE）。——
        SupplyRequestModel head = agreedLines.get(0);
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            if (StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
                continue;
            }
            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());
            ProductDefinitionDisplayDto product = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable().trim(), line.getItemId()));
            BigDecimal priceVal = ProductDefinitionCatalogUnitBandsAssembler.resolveLotNumberPackageUnitPriceForInboundAgree(
                line.getPrice(), line.getUnitCode(), product);
            BigDecimal existingPurchase = chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                line.getItemTable().trim(), line.getItemId(), resolvedLot);
            String pricingTypeCode = ChargePricingTypeCodeResolver.resolveTypeCodeForLotPriceLine(
                line.getItemTable().trim(),
                line.getItemId(),
                product,
                chargeItemDefinitionQueryService);
            if (StringUtils.isEmpty(pricingTypeCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                    "批号维定价缺少财务类型 type_code，itemTable="
                        + line.getItemTable()
                        + ", itemId="
                        + line.getItemId());
            }
            // 同批号进价已相同：跳过批号写价，仍回写目录参考价（避免参考价落后于历史进价）
            boolean skipLotWrite = existingPurchase != null && existingPurchase.compareTo(priceVal) == 0;
            if (!skipLotWrite) {
                ChargeItemDefinitionQueryCommand priceDedupQuery = new ChargeItemDefinitionQueryCommand();
                priceDedupQuery.setInstanceTable(line.getItemTable().trim());
                priceDedupQuery.setInstanceId(line.getItemId());
                priceDedupQuery.setLotNo(resolvedLot);
                // 异价或仅有售价残留：先停用旧 ACTIVE LOT_NUMBER_COST / LOT_NUMBER_PRICE
                if (chargeItemDefinitionQueryService.existsLotNumberCostOrLotNumberPriceForInstanceAndLot(priceDedupQuery)) {
                    DeactivateActivePriceLinesCommand deactivate = new DeactivateActivePriceLinesCommand();
                    deactivate.setOperatorId(operatorId);
                    List<DeactivateActivePriceLinesCommand.Item> deactivateItems = new ArrayList<>();
                    DeactivateActivePriceLinesCommand.Item costDeact = new DeactivateActivePriceLinesCommand.Item();
                    costDeact.setInstanceTable(line.getItemTable().trim());
                    costDeact.setInstanceId(line.getItemId());
                    costDeact.setPriceTypeCode(PriceType.LOT_NUMBER_COST.getCode());
                    costDeact.setLotNo(resolvedLot);
                    deactivateItems.add(costDeact);
                    DeactivateActivePriceLinesCommand.Item saleDeact = new DeactivateActivePriceLinesCommand.Item();
                    saleDeact.setInstanceTable(line.getItemTable().trim());
                    saleDeact.setInstanceId(line.getItemId());
                    saleDeact.setPriceTypeCode(PriceType.LOT_NUMBER_PRICE.getCode());
                    saleDeact.setLotNo(resolvedLot);
                    deactivateItems.add(saleDeact);
                    deactivate.setItems(deactivateItems);
                    chargeItemDefinitionDomainService.deactivateActivePriceLinesByCommand(deactivate);
                }
                try {
                    SavePriceLineCommand costCmd = new SavePriceLineCommand();
                    costCmd.setOperatorId(operatorId);
                    SavePriceLineCommand.Item costItem = new SavePriceLineCommand.Item();
                    costItem.setInstanceTable(line.getItemTable().trim());
                    costItem.setInstanceId(line.getItemId());
                    costItem.setTypeCode(pricingTypeCode);
                    costItem.setPriceTypeCode(PriceType.LOT_NUMBER_COST.getCode());
                    costItem.setLotNo(resolvedLot);
                    costItem.setPriceValue(priceVal);
                    costCmd.setItem(costItem);
                    chargeItemDefinitionDomainService.addLotPurchasePrice(costCmd);

                    // —— LOT_NUMBER_PRICE 与进价同值落库，供门诊/收费下游；库房读路径不消费。——
                    SavePriceLineCommand saleCmd = new SavePriceLineCommand();
                    saleCmd.setOperatorId(operatorId);
                    SavePriceLineCommand.Item saleItem = new SavePriceLineCommand.Item();
                    saleItem.setInstanceTable(line.getItemTable().trim());
                    saleItem.setInstanceId(line.getItemId());
                    saleItem.setTypeCode(pricingTypeCode);
                    saleItem.setPriceTypeCode(PriceType.LOT_NUMBER_PRICE.getCode());
                    saleItem.setLotNo(resolvedLot);
                    saleItem.setPriceValue(priceVal);
                    saleCmd.setItem(saleItem);
                    chargeItemDefinitionDomainService.addLotSalePrice(saleCmd);
                } catch (BusinessException ex) {
                    throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "批号维定价补录失败：" + ex.getMessage());
                }
            }
            try {
                // Cmd：目录参考价跟本行包装进价（核心进售同值；中草药 Herbal AOP after 再抬 SALE）
                this.catalogReferencePriceSyncSupport.syncPurchaseAndSaleIfChanged(
                    line.getItemTable().trim(),
                    line.getItemId(),
                    pricingTypeCode,
                    priceVal,
                    priceVal,
                    operatorId);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "目录参考价回写失败：" + ex.getMessage());
            }
        }

        // —— 7. 逐行库存入账并拼装追溯写编排命令：同一循环内完成「账面变更 + 追溯项收集」，末尾再一次批量写追溯。——
        String busNoTrim = request.getBusNo().trim();
        this.assertInboundReceiptTraceCodesNotDuplicatedAcrossLines(agreedLines);
        List<RecordTraceFromSupplyLineSpec> traceCommands = new ArrayList<>();
        // Cmd：逐行库存入账并收集追溯写编排命令
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long purposeLocationId = line.getPurposeLocationId();
            Long supplierId = line.getSupplierId();
            if (supplierId == null) {
                supplierId = head.getSupplierId();
            }
            if (supplierId == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "入库编排需要供应商信息，请先补全进货单头供应商");
            }
            line.setSupplierId(supplierId);

            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());
            String productName = this.resolveProductName(line, productByKey);
            ProductDefinitionDisplayDto product = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId()));
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.WHS_IN_104, "进货供应行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isEmpty(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();

            InventoryMatchKey key = new InventoryMatchKey();
            key.setSupplierId(supplierId);
            key.setLotNumber(resolvedLot);
            key.setLocationId(purposeLocationId);
            key.setItemTable(line.getItemTable());
            key.setItemId(line.getItemId());

            Long existingInventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(key).orElse(null);
            try {
                if (existingInventoryId != null) {
                    com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand incCmd =
                        inboundReceiptMapper.toIncreaseQuantityCommand(existingInventoryId, splitQty, operatorId);
                    inventoryItemDomainService.increaseQuantity(incCmd);
                } else {
                    com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand createCmd =
                        inboundReceiptMapper.toCreateInventoryItemCommand(
                            line, purposeLocationId, productName, resolvedLot, splitQty, splitUnitCode, operatorId, product);
                    inventoryItemDomainService.createInventoryItem(createCmd);
                }
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "库存入账失败：" + ex.getMessage());
            }

            if (StringUtils.isNotEmpty(line.getTraceNo())) {
                String lt = this.resolvePurposeLocationTypeCode(line, head);
                String traceUnitCode = this.resolveInboundTraceUnitCode(product);
                // 有码必须能解析贴码单位，禁止静默跳过个数 Gate
                if (StringUtils.isEmpty(traceUnitCode)) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_IN_104,
                        "有追溯码的明细须在药品/耗材目录维护追溯码单位"
                    );
                }
                MedicationProductUnitConversionSource conversionSource =
                    ProductDefinitionDisplaySupport.toUnitConversionSource(product);
                this.assertInboundTraceCodeCountMatchesSplitQuantity(
                    line.getTraceNo(),
                    splitQty,
                    traceUnitCode,
                    conversionSource,
                    ErrorCodeConstants.WHS_IN_104
                );
                RecordTraceFromSupplyLineSpec traceCmd = new RecordTraceFromSupplyLineSpec();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setSupplyTypeCode(SupplyType.CABINET_INBOUND_RECEIPT.getCode());
                traceCmd.setBusNo(busNoTrim);
                traceCmd.setItemTable(line.getItemTable().trim());
                traceCmd.setItemId(line.getItemId());
                traceCmd.setLotNumber(resolvedLot);
                traceCmd.setTraceNos(line.getTraceNo());
                traceCmd.setTraceUnitCode(traceUnitCode);
                traceCmd.setQuantity(splitQty);
                traceCmd.setUnitCode(splitUnitCode);
                traceCmd.setUnitConversionSource(conversionSource);
                traceCmd.setLocationId(purposeLocationId);
                traceCmd.setLocationTypeEnum(lt);
                traceCmd.setRecordDirectionEnum(TraceDirection.IN.getCode());
                traceCommands.add(traceCmd);
            }
        }

        // —— 8. 追溯码批量落库：仅当本请求至少有一条非空 traceNo 时才调用编排 Support，避免空命令噪声。——
        if (!traceCommands.isEmpty()) {
            try {
                traceNumberSupplyAgreeTraceOrchestrator.recordSingleEndLines(traceCommands);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "追溯码入库记录失败：" + ex.getMessage());
            }
        }
        tryUploadMedicareInventoryAfterInboundAgree(request, agreedLines);
        // Asm：映射同意响应
        return inboundReceiptMapper.toAgreeInboundReceiptResponse(true);
    }

    /**
     * 批量扫码身份反查（IF-TRC-21）：主表只读预览，不落库。
     *
     * @param request 追溯码列表（单次 ≤ 50）
     * @return 成功项与失败项
     * @throws BusinessException 空列表、超上限或请求内重复码
     */
    public BatchResolveTraceIdentityResponse batchResolveTraceIdentity(BatchResolveTraceIdentityRequest request) {
        if (request == null || request.getTraceNos() == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码列表不能为空");
        }
        TraceNumberBatchIdentityResolver.assertBatchSizeWithinLimit(request.getTraceNos());
        List<String> normalized = TraceNumberBatchIdentityResolver.normalizeTraceNos(request.getTraceNos());
        TraceNumberBatchIdentityResolver.assertNoDuplicateInRequest(normalized);
        List<TraceNumberIdentityQueryModel> models =
            traceNumberQueryService.batchResolveTraceIdentityByTraceNos(normalized);
        TraceNumberBatchIdentityResolver.ResolvedBatch batch =
            TraceNumberBatchIdentityResolver.splitResolvedItems(models);
        BatchResolveTraceIdentityResponse response = new BatchResolveTraceIdentityResponse();
        List<TraceIdentityResolveItemDto> items = new ArrayList<>();
        if (batch.getItems() != null) {
            for (TraceNumberIdentityQueryModel model : batch.getItems()) {
                if (model == null) {
                    continue;
                }
                TraceIdentityResolveItemDto dto = new TraceIdentityResolveItemDto();
                dto.setTraceNo(model.getTraceNo());
                dto.setResolveStatusEnum(model.getResolveStatusEnum());
                dto.setItemTable(model.getItemTable());
                dto.setItemId(model.getItemId());
                dto.setLotNumber(model.getLotNumber());
                dto.setTraceUnitCode(model.getTraceUnitCode());
                dto.setRemainingSplitQuantity(model.getRemainingSplitQuantity());
                dto.setInHospitalStatusEnum(model.getInHospitalStatusEnum());
                items.add(dto);
            }
        }
        response.setItems(items);
        List<TraceIdentityResolveFailItemDto> failItems = new ArrayList<>();
        if (batch.getFailItems() != null) {
            for (TraceNumberBatchIdentityResolver.FailItem failItem : batch.getFailItems()) {
                if (failItem == null) {
                    continue;
                }
                TraceIdentityResolveFailItemDto dto = new TraceIdentityResolveFailItemDto();
                dto.setTraceNo(failItem.getTraceNo());
                dto.setResolveStatusEnum(failItem.getResolveStatusEnum());
                failItems.add(dto);
            }
        }
        response.setFailItems(failItems);
        return response;
    }

    /**
     * 同意进货后尝试上报医保进销存（集成层降级，失败不阻断主流程）。
     *
     * @param request     同意请求（含 busNo、合同号）
     * @param agreedLines 已同意进货明细行
     */
    private void tryUploadMedicareInventoryAfterInboundAgree(
        AgreeInboundReceiptRequest request,
        List<SupplyRequestModel> agreedLines
    ) {
        List<Long> lineIds = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line != null && line.getId() != null) {
                lineIds.add(line.getId());
            }
        }
        if (lineIds.isEmpty()) {
            // 无持久化行则跳过上传，避免空载荷调用 CHS
            return;
        }
        inventoryUploadExtension.runUploadIfEnabled(() -> {
            InventoryUploadAfterInboundExtensionIn command = new InventoryUploadAfterInboundExtensionIn()
                .setInboundBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setContractBusNo(request.getContractBusNo());
            inventoryUploadExtension.uploadAfterInboundAgree(command);
        });
    }

    /**
     * 驳回进货单并记录原因（不写发放与库存）。
     *
     * @param request 请求；{@code busNo}、{@code rejectReason} 均须非空白；{@code approverId} 可空，空时回落为当前操作人
     * @return 驳回落库成功时 {@code success=true}
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 参数非法；{@link ErrorCodeConstants#WHS_IN_101} 未登录；
     *                            领域拒绝时 {@code WHS_IN_003} 映射
     */

    public RejectInboundReceiptResponse rejectInboundReceipt(RejectInboundReceiptRequest request) {
        // Gate：busNo、驳回原因与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getRejectReason())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "驳回原因不能为空");
        }
        // —— 驳回前仍须断言目标为药库进货单，避免对其它供应类型误走本入口。——

        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");

        // —— 领域驳回：仅供应请求状态与原因，不写发放与库存；领域码 SUPPLY_REQUEST_INVALID_ARGUMENT 经 mapSupplyWorkflowException 收口为 WHS_IN_003。——
        try {
            // Cmd：领域驳回请求（审批人取当前操作人）
            supplyRequestDomainService.rejectRequest(inboundReceiptMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_IN_003);
        }
        // Asm：映射驳回响应
        return inboundReceiptMapper.toRejectInboundReceiptResponse(true);
    }

    /**
     * 删除进货单（仅允许草稿/可删态，按 busNo 软删）。
     *
     * @param request 请求；{@code busNo} 非空
     * @return 删除成功时 {@code success=true}
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 单号非法；{@link ErrorCodeConstants#WHS_IN_101} 未登录；
     *                            领域拒绝时 {@code WHS_IN_005} 映射
     */

    public RemoveInboundReceiptResponse removeInboundReceipt(RemoveInboundReceiptRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删
            supplyRequestDomainService.removeByBusNo(
                inboundReceiptMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_IN_005);
        }
        // Asm：映射删除响应
        return inboundReceiptMapper.toRemoveInboundReceiptResponse(true);
    }

    /**
     * 分页查询进货选品（库存目录，不按库位汇总可用量）。
     *
     * @param request 请求；须含 {@code pageNo}、{@code pageSize}（B.10.11）、{@code purposeLocationId}（目的药库：与建单 {@code purpose_location_id} 对齐之药库门禁）；
     *                可选 {@code productTableName}（非空时须为药品/耗材物理表名白名单）；其余筛选见 {@link PageProductsForInboundReceiptRequest}
     * @return 选品分页结果（1-based 页码元数据；行内 {@code purchasePrice} 为定价表 {@link PriceType#PURCHASE} 实例维采购进价；{@code availableQuantity} 不装配，恒为 {@code null}）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 分页或门禁参数非法、{@code productTableName} 非法；
     *                            {@link ErrorCodeConstants#WHS_IN_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_IN_102} 读模型查询失败
     */
    public PageProductsForInboundReceiptResponse pageProductsForInboundReceipt(PageProductsForInboundReceiptRequest request) {
        // Gate：分页窗口、机构与库房门禁
        // —— 选品分页：除 B.10.11 分页三要素外，目的库必填（药库白名单门禁与进货头目的库语义一致）；不在本路径按库位汇总可用库存。——
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_IN_001, "分页参数不能为空");
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForInboundSelection(request.getProductTableName());
        // Pre：组装库存目录分页查询
        // —— 其余筛选字段由 Mapper 透传至读模型分页查询（keyword/category 等由读模型解释）。——
        ProductDefinitionPageQuery query = inboundReceiptMapper.toProductDefinitionPageQuery(request);
        try {
            // Cmd：查询采购目录分页
            Page<ProductDefinitionDisplayDto> page = productDefinitionPurchasePageLoader.pageForPurchase(query);
            PageProductsForInboundReceiptResponse res = inboundReceiptMapper.toPageProductsForInboundReceiptResponse(page);
            // —— B.10.11：PagedResult 使用 getContent() 获取列表数据。——
            this.enrichInboundProductSelectionRows(res.getContent());
            // Asm：映射选品分页并增强价格
            return res;
        } catch (RuntimeException ex) {
            log.warn("pageProductForPurchase failed for inbound receipt", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_IN_102, "采购选品查询失败");
        }
    }

    /**
     * 进货选品分页行二次装配：定价表 {@code PURCHASE} 进价、单位四槽与采购拆零比展示文案；
     * 并保持 {@code availableQuantity} 为 {@code null}（进货选品不关联库存可用量）。
     *
     * @param rows 当前页 DTO 列表（已由 Mapper 将 {@code availableQuantity} 置空）
     */
    private void enrichInboundProductSelectionRows(List<ProductDefinitionItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ProductDefinitionItemDto row : rows) {
            if (row != null && StringUtils.isNotEmpty(row.getProductTableName()) && row.getProductDefinitionId() != null) {
                // —— 仅合法主键参与 batch：避免空表名污染读模型 IN 查询。——
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductDefinitionId(row.getProductDefinitionId());
                lookup.setProductTableName(row.getProductTableName().trim());
                lookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> products = new HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                // —— 一次拉本页全部产品定义：失败时降级空映射，各行仍可写 PURCHASE 价与空槽。——
                products = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed for inbound product selection rows", ex);
            }
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
        // —— 进货选品不展示目的库可用量：避免逐行打库存读路径；采购进价仍走定价表 PURCHASE。——
        for (ProductDefinitionItemDto row : rows) {
            if (row == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(row.getProductTableName()) && row.getProductDefinitionId() != null) {
                String table = row.getProductTableName().trim();
                Long defId = row.getProductDefinitionId();
                BigDecimal purchasePerPackage = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(table, defId);
                row.setPurchasePrice(purchasePerPackage);
                String key = ProductDefinitionKeyUtil.buildCompositeKey(table, defId);
                ProductDefinitionDisplayDto pr = products.get(key);
                // —— 定价表 PURCHASE 为每包装进价：以此为锚写各槽 unitPrice（采购路径不取 SALE）。——
                List<ProductDefinitionCatalogUnitBandDto> bands =
                    ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBandsWithTerminology(
                        pr, terminologyMaps);
                ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                    bands, pr, purchasePerPackage);
                row.setUnitBands(bands);
                // Asm：采购↔拆零比例文案（与库存明细 packageSplitRatioText 同族算法，单位取采购/拆零槽）
                row.setPurchaseSplitRatioText(this.buildPurchaseSplitRatioText(bands));
                if (pr != null) {
                    row.setCategoryCodeDictText(pr.getCategoryCodeDictText());
                    row.setPartAttributeEnum(pr.getPartAttributeEnum());
                    row.setPartAttributeEnumDictText(pr.getPartAttributeEnumDictText());
                }
            } else {
                // —— 缺主键行：清空采购价，避免分页残留旧值误导。——
                row.setPurchasePrice(null);
                row.setPurchaseSplitRatioText(null);
            }
            // —— 与「不关联库存」契约一致：字段保留于 DTO 以兼容旧客户端，值恒为空。——
            row.setAvailableQuantity(null);
        }
    }

    /**
     * 由进货选品四槽拼装「采购最小单位换算比」展示文案（形如 {@code 1箱/3桶}）。
     *
     * <p>倍数取采购槽 {@code ratioToSplit}，大单位名取采购槽、小单位名取拆零槽；
     * 与 {@link MedicationPackageSplitQuantityDisplayAssembler#buildInventoryPackageSplitRatioText}
     * 库房展示算法同族。缺采购倍数时返回 {@code null}。</p>
     *
     * @param bands 已装配术语的进货单位槽（可 null）
     * @return 展示文案；无法拼装时 {@code null}
     */
    private String buildPurchaseSplitRatioText(List<ProductDefinitionCatalogUnitBandDto> bands) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }
        BigDecimal purchaseRatio = null;
        String purchaseUnitName = null;
        String splitUnitName = null;
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || StringUtils.isEmpty(band.getBandKind())) {
                continue;
            }
            // 采购槽：1 采购单位 = ratio 拆零
            if (ProductDefinitionCatalogUnitBandConstants.BAND_PURCHASE.equals(band.getBandKind())) {
                purchaseRatio = band.getRatioToSplit();
                if (StringUtils.isNotEmpty(band.getUnitName())) {
                    purchaseUnitName = band.getUnitName().trim();
                }
                continue;
            }
            // 拆零槽：最小单位展示名
            if (ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT.equals(band.getBandKind())) {
                if (StringUtils.isNotEmpty(band.getUnitName())) {
                    splitUnitName = band.getUnitName().trim();
                }
            }
        }
        return MedicationPackageSplitQuantityDisplayAssembler.buildInventoryPackageSplitRatioText(
            purchaseRatio,
            purchaseUnitName,
            splitUnitName);
    }

    /**
     * 选品物品表须为药品或器械定义表。
     *
     * @param raw 请求中的 productTableName；blank 时跳过
     */
    private void assertProductTableNameWhitelistForInboundSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return;
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "productTableName 非法");
        }
    }

    /**
     *
     * @param request 请求；须含 {@code purposeLocationId}、{@code pageNo}、{@code pageSize}；目的位置须属当前机构药库白名单
     * @return 订货候选分页（行内类型/状态等为持久化 code，B.10.8.1）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 参数缺失或位置越权；{@link ErrorCodeConstants#WHS_IN_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_IN_102} 读模型查询失败
     */
    public PageLinkablePurchaseOrdersResponse pageLinkablePurchaseOrders(PageLinkablePurchaseOrdersRequest request) {
        // Gate：目的位置与分页窗口
        if (request == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_IN_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();

        // Pre：组装可关联订货单查询（按 busNo 聚合，一行一单）
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.CABINET_PURCHASE_ORDER.getCode());
        listQuery.setStatusEnum(SupplyStatus.AGREE.getCode());
        // purposeLocationId=0 表示当前用户可见的全部药库；否则单库且须在管辖门控内
        if (Long.valueOf(InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID)
            .equals(request.getPurposeLocationId())) {
            if (allowed.isEmpty()) {
                listQuery.setPurposeLocationIds(
                    List.of(InventoryWarehouseConstants.LIST_FILTER_NO_VISIBLE_WAREHOUSE_PLACEHOLDER_ID));
            } else {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowed));
            }
        } else {
            this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        PageQueryBinder.bind(request, listQuery);
        try {
            // Cmd：按 busNo 去重分页查询已同意订货单
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<PurchaseOrderListRowDto> aggregated =
                this.purchaseOrderMapper.toPurchaseOrderListAggregated(busNos, lines);
            this.enrichLinkablePurchaseOrderDisplayNames(aggregated, lines);
            List<WarehouseDocumentDetailRowDto> records =
                this.inboundReceiptMapper.toLinkablePurchaseOrderRows(aggregated, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：映射可关联订货单分页响应（一行一单）
            PageLinkablePurchaseOrdersResponse response =
                this.inboundReceiptMapper.toPageLinkablePurchaseOrdersAggregatedResponse(
                    mergedTotal, pageNo, pageSize, records);
            // —— 排除已被未删除进货单引用的订货单（与 IF-WHS-SO-12 请领排除语义对齐）。——
            this.filterAlreadyReferencedPurchaseOrdersFromLinkablePage(response);
            return response;
        } catch (RuntimeException ex) {
            log.warn("pageLinkablePurchaseOrders failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_IN_102, "可关联订货单查询失败");
        }
    }

    /**
     * 按订货单号拉取进货草稿预填数据（选择单号后填充表单，IF-WHS-IN-13）。
     *
     * @param request 须含 {@code busNo}；可选 {@code purposeLocationId} 与订货目的库一致
     * @return 进货新建表单预填（含 {@code originalBusNo} 与明细行拷贝）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 参数非法；
     *                            {@link ErrorCodeConstants#WHS_IN_002} 订货单不可引用
     */
    public GetLinkablePurchaseOrderDetailResponse getLinkablePurchaseOrderDetail(
        GetLinkablePurchaseOrderDetailRequest request) {
        // Gate：busNo 与机构/库房门禁
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        String busNoTrim = request.getBusNo().trim();
        // Pre：校验订货单可引用并回读明细行
        this.validateOriginalPurchaseOrderForInboundReceipt(busNoTrim);

        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNoTrim);
        List<SupplyRequestModel> poLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (poLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "来源订货单不存在");
        }
        SupplyRequestModel head = poLines.get(0);
        Long poPurposeLocationId = head.getPurposeLocationId();
        if (poPurposeLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "订货单缺少目的药库，无法生成进货预填");
        }
        this.assertLocationAllowed(poPurposeLocationId, allowed);
        if (request.getPurposeLocationId() != null) {
            // —— 前端传入目的库时须与订货单一致，防止跨库错引。——
            if (!request.getPurposeLocationId().equals(poPurposeLocationId)) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "订货单目的药库与请求不一致");
            }
            this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
        }
        // Pre：批量装载产品读模型（与 getInboundReceiptDetail 同口径，供网格展示项目名/规格/单位）
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : poLines) {
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
            log.warn("batchGetProductByIds degraded for linkable purchase order detail", ex);
            products = new HashMap<>();
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
        // Asm：映射进货预填响应并富化产品展示（名称/规格/单位带；行价保留订货落库价）
        GetLinkablePurchaseOrderDetailResponse res =
            inboundReceiptMapper.toGetLinkablePurchaseOrderDetailResponse(poLines, products);
        this.enrichInboundReceiptDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichInboundReceiptDetailLines(res.getLines());
        return res;
    }

    /** busNo 非空门禁。 */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
    }

    /**
     * 按 busNo 回读并断言为药库进货类型。
     *
     * @param busNo 业务单号
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_103} 单号不存在或类型不符
     */
    private void preAssertCabinetInboundReceipt(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "进货单不存在");
        }
        this.assertCabinetInboundReceipt(lines);
    }

    /**
     * 断言供应请求行均为药库进货类型。
     *
     * @param lines 同 busNo 供应请求明细
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_103} 类型不符
     */
    private void assertCabinetInboundReceipt(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(line.getTypeEnum())) {
                // 防止按 busNo 误操作其它供应类型单据
                throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "单据类型非药库进货单");
            }
        }
    }

    /**
     * {@link CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive(java.util.List)} 与管辖库位同源收窄）。
     *
     * @return 允许作为进货源/目的/选品目的库、可关联订货目的库等门禁的药库 {@link Location} 主键集合；主数据未配置药库时可返回空集
     */
    private Set<Long> resolveGatedWarehouseLocationIdsForInboundGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    /**
     * 断言库位落在当前用户管辖药库集合内。
     *
     * @param locationId 待校验库位
     * @param allowed    可见药库 id 集合
     */
    private void assertLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_IN_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null || !allowed.contains(locationId)) {
            // 二次断言防止 Resolver 与集合不一致时漏拦
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "药库位置不在当前用户可选药库范围");
        }
    }

    /**
     * 可选 typeEnum 须为药库进货单类型 code。
     *
     * @param typeEnumCode 供应类型 code；blank 时跳过
     */
    private void validateOptionalInboundReceiptTypeEnum(String typeEnumCode) {
        if (StringUtils.isEmpty(typeEnumCode)) {
            return;
        }
        SupplyType declared = EnumUtil.fromCode(SupplyType.class, typeEnumCode.trim());
        if (declared == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "typeEnum 非法");
        }
        if (declared != SupplyType.CABINET_INBOUND_RECEIPT) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "typeEnum 必须为药库进货单类型");
        }
    }

    /**
     * 可选供应商须存在且为启用态。
     *
     * @param supplierId 供应商主键；null 时跳过
     */
    private void validateSupplierOptional(Long supplierId) {
        if (supplierId == null) {
            return;
        }
        try {
            SupplierDetailQueryModel detail = supplierQueryService.findSupplierById(supplierId);
            if (!SupplierStatus.ACTIVE.getCode().equals(detail.getStatusEnum())) {
                // —— 主数据存在但未启用：进货单头不允许引用停用供应商，避免后续财务/对账口径混乱。——
                throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "供应商不可用");
            }
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.SUPPLIER_NOT_FOUND.equals(ex.getErrorCode())) {
                // —— 将「未找到」统一收口为进货场景参数错误码，避免前端区分多套 NOT_FOUND。——
                throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "供应商不存在或不可用");
            }
            // —— 其它业务异常（如跨租户）原样上抛，由全局处理器记录。——
            throw ex;
        }
    }

    /**
     * 校验 {@code originalBusNo} 指向的订货单可被进货引用（须已同意且类型为药库订货单）。
     *
     * @param originalBusNo 来源订货单号
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_002} 订货单不存在、未同意或类型不符
     */
    private void validateOriginalPurchaseOrderForInboundReceipt(String originalBusNo) {
        this.validateOriginalPurchaseOrderForInboundReceipt(originalBusNo, null);
    }

    /**
     * 校验 {@code originalBusNo} 指向的订货单可被进货引用（须已同意、类型为药库订货单，且未被其它进货单占用）。
     *
     * @param originalBusNo            来源订货单号
     * @param excludeInboundReceiptBusNo 改单时排除的本进货单号，可 blank
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_002} 订货单不存在、未同意、类型不符或已被引用
     */
    private void validateOriginalPurchaseOrderForInboundReceipt(
        String originalBusNo,
        String excludeInboundReceiptBusNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> poLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (poLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "来源订货单不存在");
        }
        // —— 订货单为多行聚合：任一行类型/状态不满足即拒绝引用，防止引用「部分同意」或混类型历史数据。——
        for (SupplyRequestModel line : poLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_PURCHASE_ORDER.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "来源单据类型不是药库订货单");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                // —— 未整单同意前不允许生成进货关联，避免数量/交期与真实订货事实不一致。——
                throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "订货单未同意，禁止引用生成进货单");
            }
        }
        if (this.isPurchaseOrderReferencedByUndeletedInboundReceipt(originalBusNo, excludeInboundReceiptBusNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "该订货单已被其他进货单引用");
        }
    }

    /**
     * 可关联订货列表：剔除已被未删除药库进货单引用的订货单号（当前页内存过滤）。
     *
     * @param response 分页响应，就地改写 {@code content}
     */
    private void filterAlreadyReferencedPurchaseOrdersFromLinkablePage(PageLinkablePurchaseOrdersResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return;
        }
        Set<String> skippedBusNos = new HashSet<>();
        List<WarehouseDocumentDetailRowDto> kept = new ArrayList<>();
        for (WarehouseDocumentDetailRowDto row : response.getContent()) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String busNo = row.getBusNo().trim();
            if (skippedBusNos.contains(busNo)) {
                continue;
            }
            if (this.isPurchaseOrderReferencedByUndeletedInboundReceipt(busNo, null)) {
                skippedBusNos.add(busNo);
                continue;
            }
            kept.add(row);
        }
        response.setContent(kept);
    }

    /**
     * 判断订货单是否已被未删除的 {@link SupplyType#CABINET_INBOUND_RECEIPT} 以 {@code original_bus_no} 引用。
     *
     * @param purchaseOrderBusNo         订货业务单号
     * @param excludeInboundReceiptBusNo 改单时排除的本进货单号，可 blank
     * @return 已被其它进货单引用时 {@code true}
     */
    private boolean isPurchaseOrderReferencedByUndeletedInboundReceipt(
        String purchaseOrderBusNo,
        String excludeInboundReceiptBusNo) {
        if (StringUtils.isEmpty(purchaseOrderBusNo)) {
            return false;
        }
        return supplyRequestQueryService.existsUndeletedSupplyByOriginalBusNo(
            purchaseOrderBusNo.trim(),
            SupplyType.CABINET_INBOUND_RECEIPT.getCode(),
            excludeInboundReceiptBusNo);
    }

    /**
     * 读取表头供应状态枚举；单号不存在时返回 null。
     *
     * @param busNo 业务单号
     * @return 表头状态；不存在时 null
     */
    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        SupplyRequestModel head = lines.get(0);
        return EnumUtil.fromCode(SupplyStatus.class, head.getStatusEnum());
    }

    /**
     * 库存入账与批号维定价共用批号：须非空白，返回 {@code trim} 后字符串（与同意编排整单事务口径一致）。
     *
     * @param lotNumber 进货行批号
     * @return 去首尾空白后的批号
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_104} 批号缺失无法入账
     */
    private String resolveLotNumberForInventory(String lotNumber) {
        if (StringUtils.isEmpty(lotNumber)) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "批号不能为空，无法完成库存入账");
        }
        return lotNumber.trim();
    }

    /**
     * 从批量产品读模型解析行展示名称（同意编排库存入账必填）。
     *
     * @param line          供应请求行
     * @param productByKey  批量产品读模型映射
     * @return 去空白后的产品名称
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_104} 物品标识或产品定义缺失
     */
    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || line.getItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货明细缺少物品标识，无法解析产品名称");
        }
        if (StringUtils.isEmpty(line.getItemTable())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货明细缺少物品表名，无法解析产品名称");
        }
        if (productByKey == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "产品主数据未加载，无法解析产品名称");
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isEmpty(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "产品定义中缺少名称，无法完成库存入账");
        }
        return pr.getProductName();
    }

    /**
     * 皆空时按行、头顺序取 {@code purposeLocationId}，经 {@link LocationQueryService#requireSupplyRequestTypeForLocationId(Long)} 推导；
     * 仍不可得时回落药库（{@link SupplyRequestType#CABINET}，B.10.8.1），兼容历史缺字段数据。
     *
     * @param line 当前进货明细行，可为 null
     * @param head 同号单据头行，可为 null
     * @return 非空、已 {@code trim} 的 {@link SupplyRequestType#getCode()} 字符串
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
        return SupplyRequestType.CABINET.getCode();
    }

    /**
     * 库房进货单制单人（{@code wkf_supply_request.applicant_id}）：取当前登录用户绑定医务人员主键，与会话真源一致；
     * 与操作人（命令 {@code operatorId}，用户主键）区分，亦不经请求体代传。
     *
     * @param notLoggedInErrorCode    未登录或缺用户主键时错误码
     * @param notLoggedInMessage      未登录提示
     * @param noPractitionerMessage   已登录但未绑定医务人员时的提示
     * @return 非空医务人员主键
     * @throws BusinessException 无登录用户或未绑定医务人员
     */
    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

    /**
     * 进货明细行须携带制单时间 {@code occurrence_time}（由前端传入）；草稿阶段不写申请时间。
     */
    private void assertInboundReceiptLinesOccurrencePresent(List<InboundReceiptLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (InboundReceiptLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货明细制单时间不能为空");
            }
        }
    }

    /**
     * 将领域供应工作流异常映射为本场景错误码（提交/撤回/驳回/删除）。
     *
     * @param ex          领域抛出的业务异常
     * @param defaultCode 本场景默认错误码
     * @return 供调用方 {@code throw} 的异常实例
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * {@link ErrorCodeConstants#WHS_IN_104}，否则映射为 {@link ErrorCodeConstants#WHS_IN_003}；其它错误码原样返回。
     *
     * @param ex {@link SupplyRequestDomainService#agreeRequest} 等抛出的业务异常
     * @return 供调用方 {@code throw} 的异常实例
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.WHS_IN_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_IN_003, msg);
        }
        return ex;
    }

    /**
     * 为可关联订货单列表行批量解析供应商/制单人/目的药库展示名。
     *
     * @param rows         当前页聚合行
     * @param detailLines  同页供应请求明细
     */
    private void enrichLinkablePurchaseOrderDisplayNames(
        List<PurchaseOrderListRowDto> rows,
        List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty() || detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToSupplierId = new HashMap<>();
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = this.buildInboundReceiptHeadLineByBusNo(detailLines);
        for (Map.Entry<String, SupplyRequestModel> entry : headByBusNo.entrySet()) {
            SupplyRequestModel head = entry.getValue();
            if (head == null) {
                continue;
            }
            String bn = entry.getKey();
            if (head.getSupplierId() != null) {
                busNoToSupplierId.put(bn, head.getSupplierId());
            }
            if (head.getApplicantId() != null) {
                busNoToApplicantId.put(bn, head.getApplicantId());
            }
        }
        Map<Long, String> supplierNameById = new HashMap<>();
        for (Long supplierId : new HashSet<>(busNoToSupplierId.values())) {
            if (supplierId == null) {
                continue;
            }
            Optional<String> nameOpt = this.supplierQueryService.findSupplierNameIfPresent(supplierId);
            nameOpt.ifPresent(name -> supplierNameById.put(supplierId, name));
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        for (Long applicantId : new HashSet<>(busNoToApplicantId.values())) {
            if (applicantId == null) {
                continue;
            }
            Practitioner pr = this.practitionerQueryService.findById(applicantId);
            if (pr != null && StringUtils.isNotEmpty(pr.getName())) {
                practitionerNameById.put(applicantId, pr.getName());
            }
        }
        Set<Long> purposeLocationIds = new HashSet<>();
        for (PurchaseOrderListRowDto row : rows) {
            if (row != null && row.getPurposeLocationId() != null) {
                purposeLocationIds.add(row.getPurposeLocationId());
            }
        }
        Map<Long, String> purposeLocationNameById = new HashMap<>();
        for (Long locationId : purposeLocationIds) {
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
            locOpt.ifPresent(loc -> {
                if (StringUtils.isNotEmpty(loc.getName())) {
                    purposeLocationNameById.put(locationId, loc.getName().trim());
                }
            });
        }
        for (PurchaseOrderListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            Long supplierId = busNoToSupplierId.get(bn);
            if (supplierId != null) {
                String supplierName = supplierNameById.get(supplierId);
                if (supplierName != null) {
                    row.setSupplierName(supplierName);
                }
            }
            Long applicantId = busNoToApplicantId.get(bn);
            if (applicantId != null) {
                String creatorName = practitionerNameById.get(applicantId);
                if (creatorName != null) {
                    row.setCreatorName(creatorName);
                }
            }
            Long purposeLocationId = row.getPurposeLocationId();
            if (purposeLocationId != null) {
                String purposeLocationName = purposeLocationNameById.get(purposeLocationId);
                if (purposeLocationName != null) {
                    row.setPurposeLocationName(purposeLocationName);
                }
            }
        }
    }

    /**
     * 进货单列表：按 busNo 关联明细头行，批量回填供应商/申请人/审批人/目的库位展示名。
     *
     * @param rows         聚合列表行
     * @param detailLines  同页 supply_request 明细（含头行 supplier/applicant/approver）
     */
    private void enrichInboundReceiptListDisplayNames(List<InboundReceiptListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToSupplierId = new HashMap<>();
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, Long> busNoToApproverId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = this.buildInboundReceiptHeadLineByBusNo(detailLines);
        for (Map.Entry<String, SupplyRequestModel> e : headByBusNo.entrySet()) {
            SupplyRequestModel head = e.getValue();
            if (head == null) {
                continue;
            }
            String bn = e.getKey();
            if (head.getSupplierId() != null) {
                busNoToSupplierId.put(bn, head.getSupplierId());
            }
            if (head.getApplicantId() != null) {
                busNoToApplicantId.put(bn, head.getApplicantId());
            }
            if (head.getApproverId() != null) {
                busNoToApproverId.put(bn, head.getApproverId());
            }
        }
        // —— 供应商名称按主键去重后逐条解析；页大小有上界，此处 N+1 在可接受范围内（B.10.11 页大小上限）。——
        Map<Long, String> supplierNameById = new HashMap<>();
        for (Long supplierId : new HashSet<>(busNoToSupplierId.values())) {
            if (supplierId == null) {
                continue;
            }
            Optional<String> nameOpt = this.supplierQueryService.findSupplierNameIfPresent(supplierId);
            if (nameOpt.isPresent()) {
                supplierNameById.put(supplierId, nameOpt.get());
            }
        }
        Set<Long> practitionerIds = new HashSet<>();
        practitionerIds.addAll(busNoToApplicantId.values());
        practitionerIds.addAll(busNoToApproverId.values());
        // —— 申请人/审批人可能指向同一医务人员，先合并主键再查，减少重复 IO。——
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
        Set<Long> purposeLocationIds = new HashSet<>();
        for (InboundReceiptListRowDto row : rows) {
            if (row != null && row.getPurposeLocationId() != null) {
                purposeLocationIds.add(row.getPurposeLocationId());
            }
        }
        Map<Long, String> purposeLocationNameById = new HashMap<>();
        for (Long locationId : purposeLocationIds) {
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
            locOpt.ifPresent(loc -> {
                if (StringUtils.isNotEmpty(loc.getName())) {
                    purposeLocationNameById.put(locationId, loc.getName().trim());
                }
            });
        }
        // —— 将解析到的展示名写回聚合行；未命中主数据时保持 null，由前端自行降级展示。——
        for (InboundReceiptListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            Long sid = busNoToSupplierId.get(bn);
            if (sid != null) {
                String sn = supplierNameById.get(sid);
                if (sn != null) {
                    row.setSupplierName(sn);
                }
            }
            Long aid = busNoToApplicantId.get(bn);
            if (aid != null) {
                String an = practitionerNameById.get(aid);
                if (an != null) {
                    row.setCreatorName(an);
                }
            }
            Long apid = busNoToApproverId.get(bn);
            if (apid != null) {
                String apn = practitionerNameById.get(apid);
                if (apn != null) {
                    row.setApproverName(apn);
                }
            }
            Long plid = row.getPurposeLocationId();
            if (plid != null) {
                String pln = purposeLocationNameById.get(plid);
                if (pln != null) {
                    row.setPurposeLocationName(pln);
                }
            }
            if (StringUtils.isNotEmpty(row.getStatusEnum())) {
                SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
                if (st != null) {
                    row.setStatusEnumText(st.getDisplay());
                }
            }
        }
    }

    /**
     * 按 busNo 聚合选取表头行（申请时间较新者优先）。
     *
     * @param detailLines 当前页全部供应请求明细
     * @return busNo → 表头行
     */
    private Map<String, SupplyRequestModel> buildInboundReceiptHeadLineByBusNo(List<SupplyRequestModel> detailLines) {
        Map<String, SupplyRequestModel> best = new HashMap<>();
        if (detailLines == null) {
            return best;
        }
        for (SupplyRequestModel line : detailLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                continue;
            }
            String bn = line.getBusNo().trim();
            SupplyRequestModel cur = best.get(bn);
            SupplyRequestModel chosen = this.pickInboundReceiptHeadPreferNewerApply(line, cur);
            best.put(bn, chosen);
        }
        return best;
    }

    /**
     * 表头行择优：申请时间较新者优先；缺时间时保留既有行。
     *
     * @param candidate  候选行
     * @param incumbent  当前表头行
     * @return 择优后的表头行
     */
    private SupplyRequestModel pickInboundReceiptHeadPreferNewerApply(SupplyRequestModel candidate, SupplyRequestModel incumbent) {
        if (incumbent == null) {
            return candidate;
        }
        LocalDateTime tc = candidate.getApplyTime();
        LocalDateTime ti = incumbent.getApplyTime();
        if (tc == null && ti == null) {
            return incumbent;
        }
        if (tc == null) {
            return incumbent;
        }
        if (ti == null) {
            return candidate;
        }
        if (tc.isAfter(ti)) {
            return candidate;
        }
        return incumbent;
    }

    /**
     * 解析进货追溯码贴码单位：仅取药典 {@code traceUnitCode}（供应行无此列）。
     *
     * @param product 产品定义展示（须含目录追溯单位）
     * @return 追溯码单位 code；药典未维护时 null
     */
    private String resolveInboundTraceUnitCode(ProductDefinitionDisplayDto product) {
        if (product != null && StringUtils.isNotEmpty(product.getTraceUnitCode())) {
            return product.getTraceUnitCode().trim();
        }
        return null;
    }

    /**
     * 提审：有码行须通过跨行去重与应扫个数 Gate（与同意编排同口径；草稿保存不拦半扫）。
     *
     * @param lines     已落库供应明细
     * @param errorCode 失败业务码（提审 {@code WHS_IN_003}）
     */
    private void assertInboundReceiptSubmitTraceGates(List<SupplyRequestModel> lines, String errorCode) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<InboundReceiptLineDto> gateLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            InboundReceiptLineDto gateLine = new InboundReceiptLineDto();
            gateLine.setItemTable(line.getItemTable());
            gateLine.setItemId(line.getItemId());
            gateLine.setItemQuantity(line.getItemQuantity());
            gateLine.setTraceNo(line.getTraceNo());
            gateLines.add(gateLine);
        }
        this.assertInboundReceiptTraceGates(gateLines, errorCode);
    }

    /**
     * 有码行跨行去重 + 应扫个数 Gate（提审 / 同意前同口径）。
     *
     * @param lines     写模型明细（可由落库行投影）
     * @param errorCode 失败业务码
     */
    private void assertInboundReceiptTraceGates(List<InboundReceiptLineDto> lines, String errorCode) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // Gate：本单多行追溯码不得重复
        this.assertInboundReceiptDraftTraceCodesNotDuplicatedAcrossLines(lines, errorCode);

        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> lookupKeys = new HashSet<>();
        for (InboundReceiptLineDto line : lines) {
            if (line == null || StringUtils.isEmpty(line.getTraceNo())) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable().trim(), line.getItemId());
            if (!lookupKeys.add(key)) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable().trim());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return;
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        for (InboundReceiptLineDto line : lines) {
            if (line == null || StringUtils.isEmpty(line.getTraceNo())) {
                continue;
            }
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                line.getItemTable(),
                line.getItemQuantity(),
                errorCode,
                "进货明细数量须为拆零正数量"
            );
            ProductDefinitionDisplayDto product = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable().trim(), line.getItemId()));
            String traceUnitCode = this.resolveInboundTraceUnitCode(product);
            if (StringUtils.isEmpty(traceUnitCode)) {
                throw new BusinessException(errorCode, "有追溯码的明细须在药品/耗材目录维护追溯码单位");
            }
            MedicationProductUnitConversionSource conversionSource =
                ProductDefinitionDisplaySupport.toUnitConversionSource(product);
            this.assertInboundTraceCodeCountMatchesSplitQuantity(
                line.getTraceNo(),
                splitQty,
                traceUnitCode,
                conversionSource,
                errorCode
            );
        }
    }

    /**
     * 草稿明细：本单多行追溯码不得重复（同一码不可出现在多行 comma 串中）。
     *
     * @param lines     写模型明细
     * @param errorCode 失败业务码
     */
    private void assertInboundReceiptDraftTraceCodesNotDuplicatedAcrossLines(
        List<InboundReceiptLineDto> lines,
        String errorCode
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (InboundReceiptLineDto line : lines) {
            if (line == null || StringUtils.isEmpty(line.getTraceNo())) {
                continue;
            }
            for (String code : this.splitInboundTraceCodes(line.getTraceNo())) {
                if (!seen.add(code)) {
                    throw new BusinessException(
                        errorCode,
                        "追溯码" + code + "在本进货单多行重复，请合并到一行或删除重复码"
                    );
                }
            }
        }
    }

    /**
     * 本单多行追溯码不得重复（同一码不可出现在多行 comma 串中）。
     *
     * @param lines 已同意明细
     */
    private void assertInboundReceiptTraceCodesNotDuplicatedAcrossLines(List<SupplyRequestModel> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (SupplyRequestModel line : lines) {
            if (line == null || StringUtils.isEmpty(line.getTraceNo())) {
                continue;
            }
            for (String code : this.splitInboundTraceCodes(line.getTraceNo())) {
                if (!seen.add(code)) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_IN_104,
                        "追溯码" + code + "在本进货单多行重复，请合并到一行或删除重复码"
                    );
                }
            }
        }
    }

    /**
     * 入库追溯：码个数须等于 ceil(本行入库拆零 / 每码拆零)（半盒允许，与出库同口径）。
     *
     * @param traceNosRaw      逗号追溯码
     * @param lineSplitQty     本行入库拆零总量
     * @param traceUnitCode    追溯码贴码单位（非固定包装单位）
     * @param conversionSource 单位换算源
     * @param errorCode        失败业务码
     */
    private void assertInboundTraceCodeCountMatchesSplitQuantity(
        String traceNosRaw,
        BigDecimal lineSplitQty,
        String traceUnitCode,
        MedicationProductUnitConversionSource conversionSource,
        String errorCode
    ) {
        List<String> codes = this.splitInboundTraceCodes(traceNosRaw);
        if (codes.isEmpty() || StringUtils.isEmpty(traceUnitCode) || lineSplitQty == null) {
            return;
        }
        // Gate：应扫数 = ceil(入库拆零 / 每码拆零)，与实扫个数须一致（半盒允许）
        int expected = TraceScanCountSupport.requireExactInboundCodeCount(
            conversionSource,
            traceUnitCode.trim(),
            lineSplitQty
        );
        if (codes.size() != expected) {
            throw new BusinessException(
                errorCode,
                "追溯码个数与入库数量不一致：应扫"
                    + expected
                    + "个码（每码="
                    + traceUnitCode.trim()
                    + "），本行实扫"
                    + codes.size()
                    + "个"
            );
        }
    }

    /**
     * 逗号串拆分为追溯码列表。
     *
     * @param traceNosRaw 逗号串
     * @return 非空码列表
     */
    private List<String> splitInboundTraceCodes(String traceNosRaw) {
        List<String> result = new ArrayList<>();
        if (StringUtils.isEmpty(traceNosRaw)) {
            return result;
        }
        for (String part : traceNosRaw.split(GeneralSharedConstants.COMMA)) {
            String normalized = StringUtils.trimToNull(part);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    /**
     * 进货明细追溯码 Gate：整串与逗号分段均不得超出 {@link GeneralSharedConstants#TRACE_NO_WIDE_MAX_LENGTH}。
     *
     * @param lines     待校验明细行
     * @param errorCode 超长时业务码
     * @throws BusinessException 任一追溯码超长
     */
    private void assertInboundReceiptTraceNosWithinWideLimit(List<InboundReceiptLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (InboundReceiptLineDto line : lines) {
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
     * 草稿进货行：同表同品同批号进价须与已有 {@code LOT_NUMBER_COST} 一致。
     *
     * @param lines 进货明细写模型
     */
    private void assertInboundLotPurchasePriceConsistency(List<InboundReceiptLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine> checkLines = new ArrayList<>();
        for (InboundReceiptLineDto line : lines) {
            if (line == null) {
                continue;
            }
            LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine check =
                new LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine();
            check.setItemTable(line.getItemTable());
            check.setItemId(line.getItemId());
            check.setLotNumber(line.getLotNumber());
            check.setPrice(line.getPrice());
            check.setUnitCode(line.getUnitCode());
            checkLines.add(check);
        }
        this.lotNumberPurchasePriceConsistencyGate.assertIncomingLotPurchasePricesConsistent(
            checkLines, ErrorCodeConstants.WHS_IN_001);
    }

    /**
     * 供应请求行：同表同品同批号进价一致性（同意路径）。
     *
     * @param lines     供应行
     * @param errorCode 拒绝错误码
     */
    private void assertSupplyLinesLotPurchasePriceConsistency(List<SupplyRequestModel> lines, String errorCode) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine> checkLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine check =
                new LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine();
            check.setItemTable(line.getItemTable());
            check.setItemId(line.getItemId());
            check.setLotNumber(line.getLotNumber());
            check.setPrice(line.getPrice());
            check.setUnitCode(line.getUnitCode());
            checkLines.add(check);
        }
        this.lotNumberPurchasePriceConsistencyGate.assertIncomingLotPurchasePricesConsistent(checkLines, errorCode);
    }
}
